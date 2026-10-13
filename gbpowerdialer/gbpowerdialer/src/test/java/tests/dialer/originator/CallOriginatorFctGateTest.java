package tests.dialer.originator;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import com.globalbilgi.gbqueueselectionservice.grpc.RouteDetailEntry;
import com.powerdialer.engine.CallCleaner;
import com.powerdialer.engine.CallUpToDateKeeper;
import com.powerdialer.engine.asterisk.CallerIdManager;
import com.powerdialer.engine.asterisk.ami.ManagerConnectionComposition;
import com.powerdialer.engine.asterisk.ami.ManagerConnectionDecorator;
import com.powerdialer.engine.dialer.CallAttemptPolicy;
import com.powerdialer.engine.dialer.CallConsumer;
import com.powerdialer.engine.dialer.CallOriginator;
import com.powerdialer.engine.dialer.CampaignContext;
import com.powerdialer.engine.dialer.DialerFacade;
import com.powerdialer.engine.dialer.routing.FctChannelGate;
import com.powerdialer.engine.dialer.routing.FctChannelRegistry;
import com.powerdialer.engine.dialer.routing.RouteDetailsCursor;
import com.powerdialer.model.CDR;
import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.Server;
import com.powerdialer.redis.CallEventPublisher;
import com.powerdialer.service.CampaignResultService;
import com.powerdialer.util.Constants;
import com.powerdialer.util.LoggerDecorator;

/**
 * FCT kapasite gate'inin originate akışındaki davranışı: rezervasyon {@code processOrigination}
 * başında alınır; uygun kanal yoksa çağrı iptal EDİLMEZ, kampanya pause defer'iyle aynı primitive
 * üzerinden attempt geri alınıp kuyruğun başına döner.
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class CallOriginatorFctGateTest {

  private static final Long CAMPAIGN_ID = 71L;
  private static final String FCT_A = "05550000201";
  private static final String FCT_B = "05550000202";

  @Mock
  private LoggerDecorator loggerDecorator;

  @Mock
  private ManagerConnectionDecorator connectionDecorator;

  @Mock
  private ManagerConnectionComposition connectionComposition;

  @Mock
  private DialerFacade dialerFacade;

  @Mock
  private CallerIdManager callerIdManager;

  @Mock
  private CallCleaner callCleaner;

  @Mock
  private CampaignResultService campaignResultService;

  @Mock
  private CallEventPublisher callEventPublisher;

  @Mock
  private CallUpToDateKeeper callUpToDateKeeper;

  @Mock
  private CampaignContext campaignContext;

  @Mock
  private CallConsumer callConsumer;

  private BlockingDeque<Call> campaignCallQueue;
  private CallOriginator callOriginator;
  private FctChannelRegistry registry;
  private FctChannelGate gate;

  @Before
  public void setUp() {
    campaignCallQueue = new LinkedBlockingDeque<>();
    registry = newRegistry();
    gate = new FctChannelGate();
    gate.setRegistry(registry);
    gate.setDeferLimit(10);

    when(connectionDecorator.getConnectionComposition()).thenReturn(connectionComposition);
    when(connectionDecorator.getServer()).thenReturn(server());
    when(connectionComposition.getLoggerDecorator()).thenReturn(loggerDecorator);
    when(connectionComposition.getCallUpToDateKeeper()).thenReturn(callUpToDateKeeper);
    when(connectionComposition.getFctChannelGate()).thenReturn(gate);
    when(loggerDecorator.getLogger(CallOriginator.class)).thenReturn(loggerDecorator);
    when(dialerFacade.getCallEventPublisher()).thenReturn(callEventPublisher);

    Map<Long, CampaignContext> contexts = new HashMap<>();
    contexts.put(CAMPAIGN_ID, campaignContext);
    when(dialerFacade.getCampaignContexts()).thenReturn(contexts);
    when(campaignContext.getCallConsumer()).thenReturn(callConsumer);
    when(callConsumer.getCallQueue()).thenReturn(campaignCallQueue);

    callOriginator = new CallOriginator(connectionDecorator, dialerFacade, callerIdManager, callCleaner, campaignResultService);
  }

  @Test
  public void shouldDeferOriginationWithoutBurningAttemptWhenEveryFctIsFull() {
    // Listedeki iki FCT'nin kapasitesi 1 ve ikisi de başka çağrılar tarafından dolduruldu.
    registry.tryAcquire(FCT_A, "MOBILE", "1", 1, "baska-call-a", 9L);
    registry.tryAcquire(FCT_B, "MOBILE", "1", 1, "baska-call-b", 9L);
    Call call = fctCall();
    CallAttemptPolicy.incrementForOrigination(call);
    assertThat("originate öncesi attempt artmıştı", call.getAttempts(), is(1));

    ReflectionTestUtils.invokeMethod(callOriginator, "processOrigination", "bulk-fct", call);

    verify(connectionDecorator, never()).originateCall(any(Call.class));
    assertThat("çağrı kuyruğun başına geri konur", campaignCallQueue.peekFirst(), is(call));
    assertThat("gate reddi attempt yakmaz", call.getAttempts(), is(0));
    assertThat("defer sayacı artar", call.getFctChannelDeferCount(), is(1));
    verify(callUpToDateKeeper).unregisterCall(call);
    assertThat("hiçbir kanal rezerve edilmez", registry.occupancy(FCT_A), is(1));
    assertThat(registry.occupancy(FCT_B), is(1));
  }

  @Test
  public void shouldReserveChannelAndOriginateWhenCapacityIsAvailable() {
    Call call = fctCall();
    CallAttemptPolicy.incrementForOrigination(call);
    call.incrementFctChannelDeferCount();

    ReflectionTestUtils.invokeMethod(callOriginator, "processOrigination", "bulk-fct", call);

    verify(connectionDecorator).originateCall(call);
    assertThat("rezervasyon alınır", registry.occupancy(FCT_A), is(1));
    assertThat("caller ID rezerve edilen FCT olur", call.getCallerId(), is(FCT_A));
    assertThat("başarılı rezervasyonda defer sayacı sıfırlanır", call.getFctChannelDeferCount(), is(0));
    assertThat("çağrı kuyruğa geri konmaz", campaignCallQueue.isEmpty(), is(true));
  }

  @Test
  public void shouldSkipFullFctAndReserveNextBeforeOriginate() {
    registry.tryAcquire(FCT_A, "MOBILE", "1", 1, "baska-call-a", 9L);
    Call call = fctCall();

    ReflectionTestUtils.invokeMethod(callOriginator, "processOrigination", "bulk-fct", call);

    verify(connectionDecorator).originateCall(call);
    assertThat("dolu FCT atlanıp sıradaki rezerve edilir", call.getCallerId(), is(FCT_B));
    assertThat(registry.occupancy(FCT_B), is(1));
  }

  private Call fctCall() {
    Campaign campaign = new Campaign();
    campaign.setId(CAMPAIGN_ID);
    campaign.setState(Constants.CAMPAIGN_STATE.RUNNING);
    campaign.setType(Constants.CAMPAIGN_TYPE.PROGRESSIVE_RELEASED);
    campaign.setCallerIdBehaviour(Constants.CALLER_ID_BEHAVIOUR.POOL);
    campaign.setFctRoutingEnabled(true);
    campaign.setColResultAttempts("attempts");
    campaign.setPhoneNumberColumns(Collections.<com.powerdialer.model.PhoneNumberColumn>emptyList());

    Call call = new Call();
    call.setId("dl-fct-gate-call");
    call.setCampaign(campaign);
    call.setCalledNumber("5551000");
    call.setRawData(new JSONObject().put("attempts", 0));
    call.setCallOriginator(callOriginator);
    call.setRouteDetailsCursor(new RouteDetailsCursor(Arrays.asList(entry(FCT_A), entry(FCT_B))));
    call.setCallerId(FCT_A);

    CDR cdr = new CDR();
    cdr.setInteractionId(call.getId());
    cdr.setCallStartTime(new Date());
    call.setCdr(cdr);
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

  private Server server() {
    Server server = new Server();
    server.setIp("127.0.0.1");
    server.setName("asterisk-test");
    return server;
  }

  private FctChannelRegistry newRegistry() {
    FctChannelRegistry created = new FctChannelRegistry();
    ReflectionTestUtils.setField(created, "gateEnabled", true);
    ReflectionTestUtils.setField(created, "ttlMs", 3900000L);
    ReflectionTestUtils.setField(created, "staleWarnMs", 600000L);
    ReflectionTestUtils.setField(created, "cooldownMs", 60000L);
    ReflectionTestUtils.setField(created, "headroomPercent", 0);
    ReflectionTestUtils.setField(created, "metaRetentionMs", 1800000L);
    ReflectionTestUtils.setField(created, "eventLogSize", 200);
    return created;
  }
}
