package tests.dialer.routing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.globalbilgi.gbqueueselectionservice.grpc.RouteDetailEntry;
import com.powerdialer.engine.dialer.routing.FctChannelGate;
import com.powerdialer.engine.dialer.routing.FctChannelRegistry;
import com.powerdialer.engine.dialer.routing.RouteDetailsCursor;
import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.util.Constants;

/**
 * Gate'in karar mantığı: QSS sıralaması korunarak dolu/cooldown FCT atlanır, uygun kanal bulunamazsa
 * originate reddedilir, cause 34'te numara cooldown'a alınıp sıradaki uygun FCT rezerve edilir.
 */
public class FctChannelGateTest {

  private static final String FCT_A = "05550000101";
  private static final String FCT_B = "05550000102";
  private static final String FCT_C = "05550000103";

  private FctChannelRegistry registry;
  private FctChannelGate gate;
  private AtomicLong now;

  @Before
  public void setUp() {
    registry = new FctChannelRegistry();
    now = new AtomicLong(2000000L);
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

    gate = new FctChannelGate();
    gate.setRegistry(registry);
    gate.setDeferLimit(10);
  }

  @Test
  public void shouldReserveFirstEntryWhenCapacityIsAvailable() {
    Call call = fctCall("call-1", Constants.CAMPAIGN_TYPE.PROGRESSIVE_RELEASED);

    assertTrue(gate.reserveForOrigination(call));

    assertEquals("QSS sıralamasındaki ilk uygun FCT kullanılır", FCT_A, call.getCallerId());
    assertEquals(0, call.getRouteDetailsCursor().position());
    assertEquals(1, registry.occupancy(FCT_A));
  }

  @Test
  public void shouldSkipFullEntryAndReserveNextInQssOrder() {
    // FCT_A kapasitesi 1 ve başka bir çağrı tarafından dolduruldu.
    registry.tryAcquire(FCT_A, "MOBILE", "1", 1, "baska-call", 9L);
    Call call = fctCall("call-1", Constants.CAMPAIGN_TYPE.PROGRESSIVE_RELEASED);

    assertTrue(gate.reserveForOrigination(call));

    assertEquals("dolu FCT atlanır, sıradaki denenir", FCT_B, call.getCallerId());
    assertEquals(1, call.getRouteDetailsCursor().position());
    assertEquals(1, registry.occupancy(FCT_B));
  }

  @Test
  public void shouldSkipCooldownEntry() {
    registry.markFull(FCT_A);
    Call call = fctCall("call-1", Constants.CAMPAIGN_TYPE.PROGRESSIVE_RELEASED);

    assertTrue(gate.reserveForOrigination(call));

    assertEquals("cooldown'daki FCT atlanır", FCT_B, call.getCallerId());
  }

  @Test
  public void shouldRejectOriginationWhenEveryEntryIsUnavailable() {
    registry.tryAcquire(FCT_A, "MOBILE", "1", 1, "dolu-a", 9L);
    registry.tryAcquire(FCT_B, "MOBILE", "1", 1, "dolu-b", 9L);
    registry.markFull(FCT_C);
    Call call = fctCall("call-1", Constants.CAMPAIGN_TYPE.PROGRESSIVE_RELEASED);

    assertFalse("hiçbir FCT uygun değilse originate edilmez", gate.reserveForOrigination(call));
    assertEquals("çağrı hiçbir kanal tutmaz", 0, registry.occupancy(FCT_C));
  }

  @Test
  public void shouldNotApplyGateToPreviewCampaign() {
    Call call = fctCall("call-1", Constants.CAMPAIGN_TYPE.PREVIEW_STRAIGHT);
    registry.tryAcquire(FCT_A, "MOBILE", "1", 1, "dolu-a", 9L);
    registry.tryAcquire(FCT_B, "MOBILE", "1", 1, "dolu-b", 9L);
    registry.tryAcquire(FCT_C, "MOBILE", "1", 1, "dolu-c", 9L);

    assertFalse(gate.isGatePath(call));
    assertTrue("preview kampanyada originate'i webphone attığı için rezervasyon tutulmaz",
        gate.reserveForOrigination(call));
    assertTrue("preview akışında FCT routing (liste iterasyonu) korunur", gate.isFctRoutingPath(call));
  }

  @Test
  public void shouldNotApplyGateWhenCampaignIsNotFctRouting() {
    Call call = fctCall("call-1", Constants.CAMPAIGN_TYPE.PROGRESSIVE_RELEASED);
    call.getCampaign().setFctRoutingEnabled(false);

    assertFalse(gate.isGatePath(call));
    assertTrue(gate.reserveForOrigination(call));
    assertEquals(0, registry.occupancy(FCT_A));
  }

  @Test
  public void shouldReleaseAndCooldownFailedNumberThenReserveNextOnCause34() {
    Call call = fctCall("call-1", Constants.CAMPAIGN_TYPE.PROGRESSIVE_RELEASED);
    assertTrue(gate.reserveForOrigination(call));
    assertEquals(FCT_A, call.getCallerId());

    assertTrue(gate.reserveNextAfterNoCircuit(call));

    assertEquals("cause 34 veren numaranın rezervasyonu bırakılır", 0, registry.occupancy(FCT_A));
    assertTrue("cause 34 veren numara cooldown'a alınır", registry.isInCooldown(FCT_A));
    assertEquals("sıradaki FCT rezerve edilir", FCT_B, call.getCallerId());
    assertEquals(1, registry.occupancy(FCT_B));
  }

  @Test
  public void shouldReturnFalseWhenCursorExhaustedButStillCooldownFailedNumber() {
    Call call = fctCall("call-1", Constants.CAMPAIGN_TYPE.PROGRESSIVE_RELEASED);
    RouteDetailsCursor cursor = call.getRouteDetailsCursor();
    cursor.next();
    cursor.next();
    call.setCallerId(cursor.current().getPhoneNumber());
    assertTrue(registry.tryAcquire(FCT_C, "MOBILE", "1", 1, call.getId(), 1L));

    assertFalse("liste tükendiğinde standart sonuç akışına düşülür", gate.reserveNextAfterNoCircuit(call));

    assertEquals("son numaranın rezervasyonu bırakılır", 0, registry.occupancy(FCT_C));
    assertTrue("liste tükense de dolu FCT cooldown'a alınır", registry.isInCooldown(FCT_C));
  }

  @Test
  public void shouldReleaseReservationForCall() {
    Call call = fctCall("call-1", Constants.CAMPAIGN_TYPE.PROGRESSIVE_RELEASED);
    gate.reserveForOrigination(call);

    gate.release(call);
    gate.release(call);

    assertEquals("release idempotenttir", 0, registry.occupancy(FCT_A));
  }

  private Call fctCall(String callId, Constants.CAMPAIGN_TYPE type) {
    Campaign campaign = new Campaign();
    campaign.setId(55L);
    campaign.setType(type);
    campaign.setCallerIdBehaviour(Constants.CALLER_ID_BEHAVIOUR.POOL);
    campaign.setFctRoutingEnabled(true);

    Call call = new Call();
    call.setId(callId);
    call.setCampaign(campaign);
    call.setCalledNumber("5551000");
    call.setRouteDetailsCursor(new RouteDetailsCursor(Arrays.asList(
        entry(FCT_A), entry(FCT_B), entry(FCT_C))));
    return call;
  }

  private RouteDetailEntry entry(String phoneNumber) {
    return RouteDetailEntry.newBuilder()
        .setPhoneNumber(phoneNumber)
        .setType("MOBILE")
        .setAttempts("1")
        .setCapacity(1)
        .build();
  }
}
