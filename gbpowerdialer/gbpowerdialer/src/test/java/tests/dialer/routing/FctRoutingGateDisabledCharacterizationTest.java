package tests.dialer.routing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

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
 * Karakterizasyon: kapasite gate'i pasifken (kill switch kapalı VEYA QSS {@code capacity}
 * göndermiyor) 04.05.2026 FCT routing davranışı birebir korunur — liste sırasındaki ilk entry
 * kullanılır, hiçbir originate engellenmez ve cause 34'te sıradaki entry'ye geçilir.
 *
 * <p>Bu davranış geri dönüş yolunun (rollback) da testidir: üretimde sorun çıkarsa
 * {@code fct.channel.gate.enabled=false} ile eski akışa dönülür.
 */
public class FctRoutingGateDisabledCharacterizationTest {

  private static final String FCT_A = "05550000401";
  private static final String FCT_B = "05550000402";

  @Test
  public void shouldKeepLegacyBehaviourWhenKillSwitchIsOff() {
    FctChannelRegistry registry = newRegistry(false);
    FctChannelGate gate = newGate(registry);
    Call call = fctCall(1);

    // Kapasite 1 ve numara başka bir çağrı tarafından dolu olsa bile gate kapalıyken engellenmez.
    registry.tryAcquire(FCT_A, "MOBILE", "1", 1, "baska-call", 9L);

    assertTrue(gate.reserveForOrigination(call));
    assertEquals("liste sırasındaki ilk entry kullanılır", FCT_A, call.getCallerId());
    assertEquals("cursor ilerletilmez", 0, call.getRouteDetailsCursor().position());
  }

  @Test
  public void shouldKeepLegacyBehaviourWhenQssSendsNoCapacity() {
    FctChannelRegistry registry = newRegistry(true);
    FctChannelGate gate = newGate(registry);
    Call call = fctCall(0);

    for (int i = 0; i < 5; i++) {
      registry.tryAcquire(FCT_A, "MOBILE", "1", 0, "baska-call-" + i, 9L);
    }

    assertTrue("capacity alanı gelmiyorsa gate uygulanmaz (fail-open)", gate.reserveForOrigination(call));
    assertEquals(FCT_A, call.getCallerId());
    assertEquals(0, call.getRouteDetailsCursor().position());
  }

  @Test
  public void shouldStillAdvanceToNextEntryOnCause34WhenGateIsOff() {
    FctChannelRegistry registry = newRegistry(false);
    FctChannelGate gate = newGate(registry);
    Call call = fctCall(1);
    gate.reserveForOrigination(call);

    assertTrue("cause 34'te sıradaki entry'ye geçiş korunur", gate.reserveNextAfterNoCircuit(call));
    assertEquals(FCT_B, call.getCallerId());
    assertEquals(1, call.getRouteDetailsCursor().position());
    assertFalse("gate kapalıyken cooldown akışı kısmaz", registry.isInCooldown(FCT_A));
  }

  private Call fctCall(int capacity) {
    Campaign campaign = new Campaign();
    campaign.setId(99L);
    campaign.setType(Constants.CAMPAIGN_TYPE.PROGRESSIVE_RELEASED);
    campaign.setCallerIdBehaviour(Constants.CALLER_ID_BEHAVIOUR.POOL);
    campaign.setFctRoutingEnabled(true);

    Call call = new Call();
    call.setId("dl-legacy-fct");
    call.setCampaign(campaign);
    call.setCalledNumber("5551000");
    call.setRouteDetailsCursor(new RouteDetailsCursor(Arrays.asList(entry(FCT_A, capacity), entry(FCT_B, capacity))));
    return call;
  }

  private RouteDetailEntry entry(String phoneNumber, int capacity) {
    return RouteDetailEntry.newBuilder()
        .setPhoneNumber(phoneNumber)
        .setType("MOBILE")
        .setAttempts("1")
        .setCapacity(capacity)
        .build();
  }

  private FctChannelGate newGate(FctChannelRegistry registry) {
    FctChannelGate gate = new FctChannelGate();
    gate.setRegistry(registry);
    gate.setDeferLimit(10);
    return gate;
  }

  private FctChannelRegistry newRegistry(boolean gateEnabled) {
    FctChannelRegistry created = new FctChannelRegistry();
    ReflectionTestUtils.setField(created, "gateEnabled", gateEnabled);
    ReflectionTestUtils.setField(created, "ttlMs", 3900000L);
    ReflectionTestUtils.setField(created, "staleWarnMs", 600000L);
    ReflectionTestUtils.setField(created, "cooldownMs", 60000L);
    ReflectionTestUtils.setField(created, "headroomPercent", 0);
    ReflectionTestUtils.setField(created, "metaRetentionMs", 1800000L);
    ReflectionTestUtils.setField(created, "eventLogSize", 200);
    return created;
  }
}
