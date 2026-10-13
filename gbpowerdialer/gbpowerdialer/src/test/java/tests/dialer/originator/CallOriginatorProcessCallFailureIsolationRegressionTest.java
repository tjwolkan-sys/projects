package tests.dialer.originator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;

import org.hibernate.LazyInitializationException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;
import org.powermock.reflect.Whitebox;

import com.powerdialer.engine.acd.ACDEngine;
import com.powerdialer.engine.asterisk.ami.ManagerConnectionDecorator;
import com.powerdialer.engine.dialer.CallOriginator;
import com.powerdialer.engine.dialer.DialerFacade;
import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.util.Constants;

/**
 * 10.08.2026 uretim kaydi: tek bir cagrinin ACD islenmesi LazyInitializationException ile
 * patladiginda hata {@code processCall -> getCallFromCallQueues -> pollCallsFromConsumerAndOriginate}
 * zincirinden {@code run()} icindeki genel catch'e kadar cikiyordu. Sonuc:
 *
 * <ul>
 *   <li>O tick'in TUM originate butcesi iptal oluyordu (server genelinde arama cikmiyor).</li>
 *   <li>Kuyruktan {@code poll()} edilmis cagri ne kuyruga ne DB'ye dondugu icin KAYBOLUYORDU.</li>
 * </ul>
 *
 * <p>Kilitlenen contract: cagri basina hata izole edilir, cagri KAYBEDILMEZ ve tick devam eder.
 * Cagri kuyrugun SONUNA konur; basa konsa deterministik olarak patlayan bir cagri ayni tick icinde
 * originate butcesini tek basina tuketirdi.</p>
 *
 * <p>Mock kurulumu {@code CallOriginatorAppointedCallbackResetTest} deseniyle aynidir:
 * {@code @InjectMocks @Spy CallOriginator} + deep-stub {@code ManagerConnectionDecorator};
 * {@code getCallFromCallQueues} private oldugundan Whitebox ile cagrilir.</p>
 */
@RunWith(MockitoJUnitRunner.class)
public class CallOriginatorProcessCallFailureIsolationRegressionTest {

  @InjectMocks
  @Spy
  private CallOriginator callOriginator;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private ManagerConnectionDecorator managerConnectionDecorator;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private DialerFacade dialerFacade;

  private ACDEngine acdMock;
  private Campaign campaign;
  private Call failingCall;
  private Call nextCall;
  private BlockingDeque<Call> callQueue;

  @Before
  public void setUp() {
    acdMock = managerConnectionDecorator.getConnectionComposition().getAcdEngine();

    campaign = new Campaign();
    campaign.setId(1L);
    campaign.setType(Constants.CAMPAIGN_TYPE.PREDICTIVE);
    campaign.setTimezoneType(Constants.TIMEZONE_TYPE.OFF);
    campaign.setConcurrentLines(10);

    failingCall = call("c1");
    nextCall = call("c2");

    callQueue = new LinkedBlockingDeque<>();
    callQueue.add(failingCall);
    callQueue.add(nextCall);
    List<BlockingDeque<Call>> callQueues = Whitebox.getInternalState(callOriginator, "callQueues");
    callQueues.add(callQueue);

    // Uretimdeki kirilma noktasi: ACD agent secimi olu Hibernate proxy'si nedeniyle patliyor.
    doThrow(new LazyInitializationException("could not initialize proxy [Group#10082] - no Session"))
        .when(acdMock).processACD(any(Call.class));
  }

  @Test
  public void failingCallDoesNotPropagateOutOfCallQueuePolling() throws Exception {
    assertNull(invokeGetCallFromCallQueues());
  }

  @Test
  public void failingCallIsKeptInItsQueueInsteadOfBeingLost() throws Exception {
    invokeGetCallFromCallQueues();

    assertEquals(2, callQueue.size());
    assertTrue(callQueue.contains(failingCall));
  }

  /**
   * Patlayan cagri kuyrugun SONUNA gider; boylece ayni tick icinde originate butcesini tek basina
   * tuketmez ve sirada bekleyen sagliklikli cagrilar denenebilir.
   */
  @Test
  public void failingCallIsMovedBehindTheRemainingCalls() throws Exception {
    invokeGetCallFromCallQueues();

    assertSame(nextCall, callQueue.peek());
    assertSame(failingCall, callQueue.peekLast());
  }

  private Call call(String id) {
    Call call = new Call();
    call.setId(id);
    call.setCampaign(campaign);
    return call;
  }

  private Object invokeGetCallFromCallQueues() throws Exception {
    Method getCallFromCallQueues = Whitebox.getMethod(CallOriginator.class, "getCallFromCallQueues");
    return getCallFromCallQueues.invoke(callOriginator);
  }
}
