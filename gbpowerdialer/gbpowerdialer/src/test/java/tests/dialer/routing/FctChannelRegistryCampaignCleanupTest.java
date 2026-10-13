package tests.dialer.routing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.powerdialer.engine.dialer.routing.FctChannelRegistry;

/**
 * Kampanya forceful stop sonrası safety net: yalnız o kampanyanın rezervasyonları düşer, aynı FCT'yi
 * kullanan diğer kampanyaların canlı çağrılarına dokunulmaz. Kampanya start/reset akışları
 * registry'ye hiç dokunmadığı için burada test edilen tek lifecycle etkisi budur.
 */
public class FctChannelRegistryCampaignCleanupTest {

  private static final String NUMBER_A = "05550000011";
  private static final String NUMBER_B = "05550000012";
  private static final Long CAMPAIGN_STOPPED = 101L;
  private static final Long CAMPAIGN_RUNNING = 202L;

  private FctChannelRegistry registry;
  private AtomicLong now;

  @Before
  public void setUp() {
    registry = new FctChannelRegistry();
    now = new AtomicLong(500000L);
    LongSupplier clock = new LongSupplier() {
      @Override
      public long getAsLong() {
        return now.get();
      }
    };
    ReflectionTestUtils.setField(registry, "gateEnabled", true);
    ReflectionTestUtils.setField(registry, "ttlMs", 3900000L);
    ReflectionTestUtils.setField(registry, "staleWarnMs", 600000L);
    ReflectionTestUtils.setField(registry, "cooldownMs", 60000L);
    ReflectionTestUtils.setField(registry, "headroomPercent", 0);
    ReflectionTestUtils.setField(registry, "metaRetentionMs", 1800000L);
    ReflectionTestUtils.setField(registry, "eventLogSize", 200);
    ReflectionTestUtils.setField(registry, "clockMs", clock);
  }

  @Test
  public void shouldReleaseOnlyStoppedCampaignReservations() {
    registry.tryAcquire(NUMBER_A, "MOBILE", "1", 4, "stopped-1", CAMPAIGN_STOPPED);
    registry.tryAcquire(NUMBER_A, "MOBILE", "1", 4, "running-1", CAMPAIGN_RUNNING);
    registry.tryAcquire(NUMBER_B, "MOBILE", "1", 4, "stopped-2", CAMPAIGN_STOPPED);

    registry.releaseAllForCampaign(CAMPAIGN_STOPPED);

    assertEquals("aynı FCT'deki diğer kampanya rezervasyonu korunur", 1, registry.occupancy(NUMBER_A));
    assertEquals("stop edilen kampanyanın diğer FCT rezervasyonu da düşer", 0, registry.occupancy(NUMBER_B));
  }

  @Test
  public void shouldIgnoreNullCampaignId() {
    registry.tryAcquire(NUMBER_A, "MOBILE", "1", 4, "call-1", CAMPAIGN_RUNNING);

    registry.releaseAllForCampaign(null);

    assertEquals(1, registry.occupancy(NUMBER_A));
  }

  @Test
  public void shouldDropIdleNumberMetadataAfterRetention() {
    registry.tryAcquire(NUMBER_A, "MOBILE", "1", 4, "call-1", CAMPAIGN_RUNNING);
    registry.release(NUMBER_A, "call-1");

    assertEquals("boşta duran numara bir süre IDLE olarak listede kalır", 1, registry.snapshot().getEntries().size());

    now.addAndGet(1800000L + 1L);

    assertTrue("metadata retention sonrası numara listeden düşer", registry.snapshot().getEntries().isEmpty());
  }

  @Test
  public void shouldKeepNumberWithLiveReservationBeyondRetention() {
    registry.tryAcquire(NUMBER_A, "MOBILE", "1", 4, "uzun-call", CAMPAIGN_RUNNING);

    now.addAndGet(1800000L + 1L);

    assertEquals("canlı rezervasyonu olan numara retention nedeniyle düşürülmez",
        1, registry.snapshot().getEntries().size());
  }
}
