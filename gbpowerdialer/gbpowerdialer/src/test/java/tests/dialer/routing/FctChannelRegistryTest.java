package tests.dialer.routing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.powerdialer.dto.FctChannelEntryDTO;
import com.powerdialer.dto.FctChannelEventDTO;
import com.powerdialer.dto.FctRegistrySnapshotDTO;
import com.powerdialer.engine.dialer.routing.FctChannelEventKind;
import com.powerdialer.engine.dialer.routing.FctChannelRegistry;

/**
 * FCT kanal registry'sinin çekirdek semantiği: kapasite sınırı, headroom, fail-open, cooldown,
 * idempotent release ve TTL prune.
 */
public class FctChannelRegistryTest {

  private static final String NUMBER = "05550000001";
  private static final long TTL_MS = 3900000L;
  private static final long COOLDOWN_MS = 60000L;

  private FctChannelRegistry registry;
  private AtomicLong now;

  @Before
  public void setUp() {
    registry = newRegistry(true, 0);
  }

  @Test
  public void shouldAcquireUpToCapacityAndRejectBeyond() {
    assertTrue(registry.tryAcquire(NUMBER, "MOBILE", "10", 2, "call-1", 1L));
    assertTrue(registry.tryAcquire(NUMBER, "MOBILE", "10", 2, "call-2", 1L));

    assertFalse("kapasite dolduktan sonra rezervasyon reddedilir",
        registry.tryAcquire(NUMBER, "MOBILE", "10", 2, "call-3", 1L));
    assertEquals(2, registry.occupancy(NUMBER));
    assertEquals(FctChannelEventKind.REJECT.name(), lastEvent().getKind());
  }

  @Test
  public void shouldApplyHeadroomToCapacity() {
    registry = newRegistry(true, 50);

    assertEquals(2, registry.effectiveCapacity(4));
    assertTrue(registry.tryAcquire(NUMBER, "MOBILE", "1", 4, "call-1", 1L));
    assertTrue(registry.tryAcquire(NUMBER, "MOBILE", "1", 4, "call-2", 1L));
    assertFalse("headroom sonrası etkin kapasite 2, üçüncü rezervasyon reddedilir",
        registry.tryAcquire(NUMBER, "MOBILE", "1", 4, "call-3", 1L));
  }

  @Test
  public void shouldKeepAtLeastOneChannelWhenHeadroomWouldZeroCapacity() {
    registry = newRegistry(true, 90);

    assertEquals("headroom kapasiteyi sıfırlayamaz", 1, registry.effectiveCapacity(1));
    assertTrue(registry.tryAcquire(NUMBER, "MOBILE", "1", 1, "call-1", 1L));
    assertFalse(registry.tryAcquire(NUMBER, "MOBILE", "1", 1, "call-2", 1L));
  }

  @Test
  public void shouldFailOpenWhenCapacityIsUnknown() {
    for (int i = 0; i < 5; i++) {
      assertTrue("capacity<=0 ise gate uygulanmaz (QSS alanı gelmiyor olabilir)",
          registry.tryAcquire(NUMBER, "MOBILE", "1", 0, "call-" + i, 1L));
    }
    assertEquals("rezervasyonlar monitor için yine kaydedilir", 5, registry.occupancy(NUMBER));
  }

  @Test
  public void shouldFailOpenWhenGateIsDisabled() {
    registry = newRegistry(false, 0);

    assertTrue(registry.tryAcquire(NUMBER, "MOBILE", "1", 1, "call-1", 1L));
    assertTrue("kill switch kapalıyken kapasite dolu olsa da reddedilmez",
        registry.tryAcquire(NUMBER, "MOBILE", "1", 1, "call-2", 1L));

    registry.markFull(NUMBER);
    assertFalse("gate kapalıyken cooldown akışı kısmaz", registry.isInCooldown(NUMBER));
    assertTrue(registry.tryAcquire(NUMBER, "MOBILE", "1", 1, "call-3", 1L));
  }

  @Test
  public void shouldTreatReleaseAsIdempotentAndOwnerScoped() {
    registry.tryAcquire(NUMBER, "MOBILE", "1", 2, "call-1", 1L);

    registry.release(NUMBER, "call-1");
    registry.release(NUMBER, "call-1");
    registry.release(NUMBER, "yabanci-call");
    registry.release("bilinmeyen-numara", "call-1");

    assertEquals("çift release doluluğu eksiye düşürmez", 0, registry.occupancy(NUMBER));
  }

  @Test
  public void shouldNotDoubleCountSameCallId() {
    assertTrue(registry.tryAcquire(NUMBER, "MOBILE", "1", 1, "call-1", 1L));
    assertTrue("aynı callId tekrar acquire edilirse doluluk şişmez",
        registry.tryAcquire(NUMBER, "MOBILE", "1", 1, "call-1", 1L));

    assertEquals(1, registry.occupancy(NUMBER));
    registry.release(NUMBER, "call-1");
    assertEquals(0, registry.occupancy(NUMBER));
  }

  @Test
  public void shouldSkipNumberWhileInCooldownAndRecoverAfterwards() {
    registry.markFull(NUMBER);

    assertTrue(registry.isInCooldown(NUMBER));
    assertFalse(registry.tryAcquire(NUMBER, "MOBILE", "1", 4, "call-1", 1L));
    assertEquals(FctChannelEventKind.SKIP.name(), lastEvent().getKind());

    now.addAndGet(COOLDOWN_MS + 1L);

    assertFalse(registry.isInCooldown(NUMBER));
    assertTrue("cooldown bitince numara yeniden kullanılabilir",
        registry.tryAcquire(NUMBER, "MOBILE", "1", 4, "call-1", 1L));
  }

  @Test
  public void shouldPruneReservationAfterTtlAndReportPruneEvent() {
    registry.tryAcquire(NUMBER, "MOBILE", "1", 1, "kacan-release", 1L);

    now.addAndGet(TTL_MS + 1L);

    assertEquals("TTL aşan rezervasyon prune edilir", 0, registry.occupancy(NUMBER));
    assertTrue("prune sonrası kanal yeniden rezerve edilebilir",
        registry.tryAcquire(NUMBER, "MOBILE", "1", 1, "yeni-call", 1L));

    FctRegistrySnapshotDTO snapshot = registry.snapshot();
    assertEquals(1, snapshot.getKpi().getPrunedLast5Min());
    assertTrue(containsKind(snapshot, FctChannelEventKind.PRUNE));
  }

  @Test
  public void shouldNotPruneReservationYoungerThanTtl() {
    registry.tryAcquire(NUMBER, "MOBILE", "1", 1, "uzun-konusma", 1L);

    // Gerçek konuşma süresi: TTL'in altında kaldığı sürece kanal serbest bırakılmaz.
    now.addAndGet(TTL_MS - 1L);

    assertEquals(1, registry.occupancy(NUMBER));
    assertFalse(registry.tryAcquire(NUMBER, "MOBILE", "1", 1, "yeni-call", 1L));
  }

  @Test
  public void shouldExposeCapacityAndInFlightInSnapshot() {
    registry.tryAcquire(NUMBER, "FIXED", "1240", 8, "call-1", 77L);

    FctRegistrySnapshotDTO snapshot = registry.snapshot();

    assertEquals(1, snapshot.getEntries().size());
    FctChannelEntryDTO entry = snapshot.getEntries().get(0);
    assertEquals(NUMBER, entry.getPhoneNumber());
    assertEquals("FIXED", entry.getType());
    assertEquals("1240", entry.getAttempts());
    assertEquals(8, entry.getCapacity());
    assertEquals(8, entry.getEffectiveCapacity());
    assertEquals(1, entry.getInFlight().size());
    assertEquals("call-1", entry.getInFlight().get(0).getCallId());
    assertEquals(Long.valueOf(77L), entry.getInFlight().get(0).getCampaignId());
    assertEquals(TTL_MS, snapshot.getTtlMs());
    assertEquals(COOLDOWN_MS, snapshot.getCooldownMs());
    assertTrue(snapshot.isGateEnabled());
  }

  /**
   * Monitor ekranı hangi numaranın hangi trunk'la çıktığını göstermek için snapshot'taki trunk
   * alanını kullanır; aksi hâlde {@code DIALER_TRUNK} değerini görmek için AMI/log debug gerekir.
   */
  @Test
  public void shouldExposeTrunkInSnapshot() {
    registry.tryAcquire(NUMBER, "FIXED", "1240", 8, "SOL", "call-1", 77L);

    assertEquals("SOL", onlyEntry().getTrunk());
  }

  /**
   * QSS trunk göndermediğinde snapshot'ta da trunk boş kalır; ekran bu durumda "varsayılan"
   * rozetini gösterir ve çağrı dialplan varsayılan trunk'ına gider.
   */
  @Test
  public void shouldLeaveTrunkEmptyWhenNotProvided() {
    registry.tryAcquire(NUMBER, "FIXED", "1240", 8, "call-1", 77L);

    assertNull(onlyEntry().getTrunk());
  }

  /**
   * Trunk her rezervasyonda yeniden yazılır: QSS tanımı değişirse monitor son gönderilen değeri
   * gösterir, eski değeri önbelleklemez.
   */
  @Test
  public void shouldRefreshTrunkOnNextAcquire() {
    registry.tryAcquire(NUMBER, "FIXED", "1240", 8, "SOL", "call-1", 77L);
    registry.tryAcquire(NUMBER, "FIXED", "1240", 8, "ININ", "call-2", 77L);

    assertEquals("ININ", onlyEntry().getTrunk());
  }

  private FctChannelEntryDTO onlyEntry() {
    FctRegistrySnapshotDTO snapshot = registry.snapshot();
    assertEquals(1, snapshot.getEntries().size());
    return snapshot.getEntries().get(0);
  }

  private FctChannelEventDTO lastEvent() {
    FctRegistrySnapshotDTO snapshot = registry.snapshot();
    assertFalse("olay kaydı bulunmalı", snapshot.getRecentEvents().isEmpty());
    return snapshot.getRecentEvents().get(0);
  }

  private boolean containsKind(FctRegistrySnapshotDTO snapshot, FctChannelEventKind kind) {
    for (FctChannelEventDTO event : snapshot.getRecentEvents()) {
      if (kind.name().equals(event.getKind())) {
        return true;
      }
    }
    return false;
  }

  private FctChannelRegistry newRegistry(boolean gateEnabled, int headroomPercent) {
    FctChannelRegistry created = new FctChannelRegistry();
    now = new AtomicLong(1000000L);
    LongSupplier clock = new LongSupplier() {
      @Override
      public long getAsLong() {
        return now.get();
      }
    };
    ReflectionTestUtils.setField(created, "gateEnabled", gateEnabled);
    ReflectionTestUtils.setField(created, "ttlMs", TTL_MS);
    ReflectionTestUtils.setField(created, "staleWarnMs", 600000L);
    ReflectionTestUtils.setField(created, "cooldownMs", COOLDOWN_MS);
    ReflectionTestUtils.setField(created, "headroomPercent", headroomPercent);
    ReflectionTestUtils.setField(created, "metaRetentionMs", 1800000L);
    ReflectionTestUtils.setField(created, "eventLogSize", 200);
    ReflectionTestUtils.setField(created, "clockMs", clock);
    return created;
  }
}
