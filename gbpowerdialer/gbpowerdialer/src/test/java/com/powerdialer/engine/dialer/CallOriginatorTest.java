package com.powerdialer.engine.dialer;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicInteger;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.SQLException;

import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;
import org.powermock.reflect.Whitebox;

import com.powerdialer.engine.acd.ACDEngine;
import com.powerdialer.engine.asterisk.ami.ManagerConnectionDecorator;
import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.PhoneNumberColumn;
import com.powerdialer.model.Server;
import com.powerdialer.model.TelephonyResultCode;
import com.powerdialer.model.User;
import com.powerdialer.util.Constants;
import com.powerdialer.util.Constants.RETRY_STRATEGY;

/**
 * Test for CallOriginator class
 */
@RunWith(MockitoJUnitRunner.class)
public class CallOriginatorTest {

  private Campaign campaign;

  @InjectMocks
  @Spy
  private CallOriginator callOriginator;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private ManagerConnectionDecorator managerConnectionDecorator;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private DialerFacade dialerFacade;

  @Before
  public void setUp() {
    campaign = new Campaign();
    campaign.setAppointerReset(false);
    campaign.setColResultCallbackDate("callback_date");
  }

  private void createCampaign() {
    campaign.setRetryScheduleEnabled(true);
    campaign.setRetryStrategy(RETRY_STRATEGY.REACH_RETRY_LIMIT_FOR_SINGLE_NUMBER);
    campaign.setRetryOveralAttempt(2);
    campaign.setMacroMaxAttempt(2);
  }

  private TelephonyResultCode createLSCTimeoutResultCode() {
    TelephonyResultCode telephonyResultCode = new TelephonyResultCode();
    telephonyResultCode.setCode(Constants.TELEPHONY_RC_LSC_TIMEOUT);
    telephonyResultCode.setName("LSC timeout");
    return telephonyResultCode;
  }

  private Call createCall(String id, Campaign campaign, Long userId) {
    Call call = new Call();
    call.setId(id);
    call.setCampaign(campaign);
    call.setCalledNumber("ANY_NUMBER");
    if (userId != null)
      call.setRawData(new JSONObject("{\"user_id\":" + userId + "}"));
    return call;
  }

  /**
   * Test move down callbacks
   * @throws Exception
   */
  @Test
  public void testMoveDownAndRotateAppointedCallbacks() throws Exception {
    campaign.setColResultUserId("user_id");
    campaign.setAllowCallback(true);
    campaign.setCallbackToAppointer(true);

    Call c1 = createCall("c1", campaign, 1L);
    Call c2 = createCall("c2", campaign, 1L);
    Call c3 = createCall("c3", campaign, 1L);
    Call w1 = createCall("w1", campaign, null);
    Call w2 = createCall("w2", campaign, null);
    BlockingDeque<Call> callQueue = new LinkedBlockingDeque<>();
    callQueue.addAll(Arrays.asList(c1, c2, c3, w1, w2));
    callOriginator.moveDownAndRotateAppointedCallbacks(callQueue);
    Assert.assertArrayEquals(new Call[] { w1, c2, c3, c1, w2 }, callQueue.toArray());
    System.out.println();
  }

  /**
   * Test move down single callback
   * @throws Exception
   */
  @Test
  public void testMoveDownAndRotateAppointedCallbacksWithSingleCallback() throws Exception {
    campaign.setColResultUserId("user_id");
    campaign.setAllowCallback(true);
    campaign.setCallbackToAppointer(true);

    Call c1 = createCall("c1", campaign, 1L);
    Call w1 = createCall("w1", campaign, null);
    Call w2 = createCall("w2", campaign, null);
    BlockingDeque<Call> callQueue = new LinkedBlockingDeque<>();
    callQueue.addAll(Arrays.asList(c1, w1, w2));
    callOriginator.moveDownAndRotateAppointedCallbacks(callQueue);
    Assert.assertArrayEquals(new Call[] { w1, c1, w2 }, callQueue.toArray());
    System.out.println();
  }

  /**
   * Test rotation of callbacks
   * @throws Exception
   */
  @Test
  public void testMoveDownAndRotateAppointedCallbacksWithOnlyCallbacks() throws Exception {
    campaign.setColResultUserId("user_id");
    campaign.setAllowCallback(true);
    campaign.setCallbackToAppointer(true);

    Call c1 = createCall("c1", campaign, 1L);
    Call c2 = createCall("c2", campaign, 1L);
    Call c3 = createCall("c3", campaign, 1L);
    BlockingDeque<Call> callQueue = new LinkedBlockingDeque<>();
    callQueue.addAll(Arrays.asList(c1, c2, c3));
    callOriginator.moveDownAndRotateAppointedCallbacks(callQueue);
    Assert.assertArrayEquals(new Call[] { c2, c3, c1 }, callQueue.toArray());
    System.out.println();
  }

  /**
   * Test for queue without callbacks
   * @throws Exception
   */
  @Test
  public void testMoveDownAndRotateAppointedCallbacksWithoutCallbacks() throws Exception {
    campaign.setColResultUserId("user_id");

    Call w1 = createCall("w1", campaign, null);
    Call w2 = createCall("w2", campaign, null);
    BlockingDeque<Call> callQueue = new LinkedBlockingDeque<>();
    callQueue.addAll(Arrays.asList(w1, w2));
    callOriginator.moveDownAndRotateAppointedCallbacks(callQueue);
    Assert.assertArrayEquals(new Call[] { w1, w2 }, callQueue.toArray());
    System.out.println();
  }

  @Test
  public void givenCallWhenLSCTimeoutThenCreateRetry() throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
    createCampaign();
    Call call = createCall("ANY_ID", campaign, 0L);
    given(dialerFacade.getServletContext().getAttribute(anyString())).willReturn(new HashMap() {
      {
        put(Constants.TELEPHONY_RC_LSC_TIMEOUT, createLSCTimeoutResultCode());
      }
    });
    campaign.getPhoneNumberColumns().add(new PhoneNumberColumn());
    Method processLscTimeout = Whitebox.getMethod(CallOriginator.class, "cancelCall", Call.class, int.class, boolean.class);
    processLscTimeout.invoke(callOriginator, call, Constants.TELEPHONY_RC_LSC_TIMEOUT, true);

    assertThat(call.getRetryDate(), is(not(nullValue())));
  }

  @Test
  public void givenCallQueueWhenCampaignIsOutboundIvrThenReturnCall() throws InvocationTargetException, IllegalAccessException {
    Method processCall = Whitebox.getMethod(CallOriginator.class, "processCall", BlockingDeque.class, Call.class);
    campaign.setType(Constants.CAMPAIGN_TYPE.OUTBOUND_IVR);
    Call call = createCall("c1", campaign, null);
    BlockingDeque<Call> callQueue = new LinkedBlockingDeque<>();
    callQueue.addAll(Arrays.asList(call));

    Call result = (Call) processCall.invoke(callOriginator, callQueue, call);
    assertEquals(call, result);
    assertEquals(1, callQueue.size());
  }

  @Test
  public void givenCallQueueWhenCampaignIsProgressiveThenReturnCallWithUser() throws InvocationTargetException, IllegalAccessException {
    Method processCall = Whitebox.getMethod(CallOriginator.class, "processCall", BlockingDeque.class, Call.class);
    campaign.setType(Constants.CAMPAIGN_TYPE.PREVIEW_STRAIGHT);
    Call call = createCall("c1", campaign, null);
    BlockingDeque<Call> callQueue = new LinkedBlockingDeque<>();
    callQueue.addAll(Arrays.asList(call));

    ACDEngine acdMock = managerConnectionDecorator.getConnectionComposition().getAcdEngine();
    User user = new User();
    user.setId(1L);
    doAnswer(invocation -> {
      ((Call) invocation.getArguments()[0]).setUser(user);
      return null;
    }).when(acdMock).processACD(Matchers.any());

    Call result = (Call) processCall.invoke(callOriginator, callQueue, call);
    assertEquals(call, result);
    assertEquals(1, callQueue.size());
    assertEquals(user, call.getUser());
  }

  @Test
  public void givenCallQueueWhenCampaignIsProgressiveWithEnabledSkillsThenReturnCallToDb() throws InvocationTargetException, IllegalAccessException, SQLException {
    Method processCall = Whitebox.getMethod(CallOriginator.class, "processCall", BlockingDeque.class, Call.class);
    campaign.setType(Constants.CAMPAIGN_TYPE.PREVIEW_STRAIGHT);
    campaign.setSkillEnabled(true);
    Call call = createCall("c1", campaign, null);
    BlockingDeque<Call> callQueue = new LinkedBlockingDeque<>();
    callQueue.addAll(Arrays.asList(call));

    ACDEngine acdMock = managerConnectionDecorator.getConnectionComposition().getAcdEngine();
    doAnswer(invocation -> {
      ((Call) invocation.getArguments()[0]).setUser(null);
      return null;
    }).when(acdMock).processACD(Matchers.any());

    Call result = (Call) processCall.invoke(callOriginator, callQueue, call);
    assertNull(result);
    assertEquals(1, callQueue.size());
    assertNull(call.getUser());
    verify(managerConnectionDecorator.getConnectionComposition().getCampaignDbOperations()).removeFetchedDataFlagFromCallsAsync(eq(campaign), any(List.class));
  }

  @Test
  public void givenCallQueueWhenCampaignIsProgressiveWithEnabledSkillsThenRotateCallbacks() throws Exception {
    Method processCall = Whitebox.getMethod(CallOriginator.class, "processCall", BlockingDeque.class, Call.class);
    campaign.setType(Constants.CAMPAIGN_TYPE.PREVIEW_STRAIGHT);
    campaign.setSkillEnabled(true);
    campaign.setColResultUserId("col_result_user_id");
    Call call = createCall("c1", campaign, null);
    call.setRawData(new JSONObject().put(campaign.getColResultUserId(), "1"));
    BlockingDeque<Call> callQueue = new LinkedBlockingDeque<>();
    callQueue.addAll(Arrays.asList(call));
    campaign.setAllowCallback(true);
    campaign.setCallbackToAppointer(true);

    User user = new User();
    user.setId(1L);
    ACDEngine acdMock = managerConnectionDecorator.getConnectionComposition().getAcdEngine();
    doAnswer(invocation -> {
      ((Call) invocation.getArguments()[0]).setUser(null);
      return null;
    }).when(acdMock).processACD(Matchers.any());

    // Task 321997: strateji null (legacy TIMER) olduğundan processCall artık appointed dalında
    // isCampaignHasOnlineUser çağırmaz (davranış korunur: returnToDb + rotate). Eski stub kaldırıldı.
    Call result = (Call) processCall.invoke(callOriginator, callQueue, call);
    assertNull(result);
    assertEquals(1, callQueue.size());
    assertNull(call.getUser());

    verify(callOriginator).moveDownAndRotateAppointedCallbacks(callQueue);
    verify(managerConnectionDecorator.getConnectionComposition().getCampaignDbOperations()).removeFetchedDataFlagFromCallsAsync(eq(campaign), any(List.class));
  }

  @Test
  public void givenCallQueueWhenCampaignIsProgressiveWithEnabledSkillsThenReturnToDbAndRotateCallbacks() throws Exception {
    Method processCall = Whitebox.getMethod(CallOriginator.class, "processCall", BlockingDeque.class, Call.class);
    campaign.setType(Constants.CAMPAIGN_TYPE.PREVIEW_STRAIGHT);
    campaign.setSkillEnabled(true);
    campaign.setColResultUserId("col_result_user_id");
    Call call = createCall("c1", campaign, null);
    call.setRawData(new JSONObject().put(campaign.getColResultUserId(), "1"));
    BlockingDeque<Call> callQueue = new LinkedBlockingDeque<>();
    callQueue.addAll(Arrays.asList(call));
    campaign.setAllowCallback(true);
    campaign.setCallbackToAppointer(true);

    User user = new User();
    user.setId(1L);
    ACDEngine acdMock = managerConnectionDecorator.getConnectionComposition().getAcdEngine();
    doAnswer(invocation -> {
      ((Call) invocation.getArguments()[0]).setUser(null);
      return null;
    }).when(acdMock).processACD(Matchers.any());

    // Task 321997: strateji null (legacy TIMER) olduğundan processCall artık appointed dalında
    // isCampaignHasOnlineUser çağırmaz (davranış korunur: returnToDb + rotate). Eski stub kaldırıldı.
    Call result = (Call) processCall.invoke(callOriginator, callQueue, call);
    assertNull(result);
    assertEquals(1, callQueue.size());
    assertNull(call.getUser());

    verify(callOriginator).moveDownAndRotateAppointedCallbacks(callQueue);
    verify(managerConnectionDecorator.getConnectionComposition().getCampaignDbOperations(), atLeast(1)).removeFetchedDataFlagFromCallsAsync(eq(campaign), any(List.class));
  }

  // --- SORUN 2 / 1B (GLB90090645): GAMMA'da avail agent yokken cagri durdurulmaz; temsili
  // tasiyici (online agent) atanip originate'e devam edilir. Appointed/same-user callback'ler
  // ve diger stratejiler mevcut akisda kalir.

  @Test
  public void givenGammaPredictiveWhenNoAvailAgentThenOriginatesWithSymbolicCarrier() throws InvocationTargetException, IllegalAccessException {
    Method processCall = Whitebox.getMethod(CallOriginator.class, "processCall", BlockingDeque.class, Call.class);
    campaign.setType(Constants.CAMPAIGN_TYPE.PREDICTIVE);
    campaign.setPredictiveStrategy(Constants.PREDICTIVE_STRATEGY.GAMMA);
    campaign.setSkillEnabled(false);
    Call call = createCall("c1", campaign, null);
    BlockingDeque<Call> callQueue = new LinkedBlockingDeque<>();
    callQueue.add(call);

    ACDEngine acdMock = managerConnectionDecorator.getConnectionComposition().getAcdEngine();
    doAnswer(invocation -> {
      ((Call) invocation.getArguments()[0]).setUser(null); // processACD avail agent bulamadi
      return null;
    }).when(acdMock).processACD(Matchers.any());
    User carrier = new User();
    carrier.setId(9L);
    doReturn(carrier).when(acdMock).findGammaSymbolicCarrier(campaign);

    Call result = (Call) processCall.invoke(callOriginator, callQueue, call);

    assertEquals(call, result);            // cagri DURDURULMADI, originate'e devam ediyor
    assertEquals(carrier, call.getUser()); // temsili tasiyici atandi (exten gecerli kalacak)
    assertEquals(1, callQueue.size());     // kuyruga geri itilmedi
    Assert.assertTrue("tasiyici atamasi isaretlenmeli ki keeper'a hayalet cagri kaydedilmesin",
        call.isGammaSymbolicCarrierAssignment());
  }

  @Test
  public void givenGammaAppointedCallbackWhenOwnerUnavailableThenSymbolicCarrierNotUsed() throws Exception {
    Method processCall = Whitebox.getMethod(CallOriginator.class, "processCall", BlockingDeque.class, Call.class);
    campaign.setType(Constants.CAMPAIGN_TYPE.PREDICTIVE);
    campaign.setPredictiveStrategy(Constants.PREDICTIVE_STRATEGY.GAMMA);
    campaign.setSkillEnabled(false);
    campaign.setColResultUserId("user_id");
    campaign.setAllowCallback(true);
    campaign.setCallbackToAppointer(true);
    Call call = createCall("c1", campaign, 1L); // sahibine atanmis callback
    BlockingDeque<Call> callQueue = new LinkedBlockingDeque<>();
    callQueue.add(call);

    ACDEngine acdMock = managerConnectionDecorator.getConnectionComposition().getAcdEngine();
    doAnswer(invocation -> {
      ((Call) invocation.getArguments()[0]).setUser(null); // sahip su an avail degil
      return null;
    }).when(acdMock).processACD(Matchers.any());

    Call result = (Call) processCall.invoke(callOriginator, callQueue, call);

    assertNull(result);      // appointed callback sahibini bekler, temsiliyle ARANMAZ
    assertNull(call.getUser());
    verify(acdMock, never()).findGammaSymbolicCarrier(Matchers.any());
    verify(callOriginator).moveDownAndRotateAppointedCallbacks(callQueue);
  }

  @Test
  public void givenCallQueueWhenNoUserForCallThenCallRemainsInQueue() throws InvocationTargetException, IllegalAccessException {
    Method processCall = Whitebox.getMethod(CallOriginator.class, "getCallFromCallQueues");
    campaign.setType(Constants.CAMPAIGN_TYPE.PREVIEW_STRAIGHT);
    campaign.setSkillEnabled(false);
    campaign.setConcurrentLines(2);
    campaign.setState(Constants.CAMPAIGN_STATE.RUNNING);
    Call call = createCall("c1", campaign, null);
    BlockingDeque<Call> callQueue = new LinkedBlockingDeque<>();
    callQueue.addAll(Arrays.asList(call));

    callOriginator.addCallQueue(callQueue);

    ACDEngine acdMock = managerConnectionDecorator.getConnectionComposition().getAcdEngine();
    doNothing().when(acdMock).processACD(Matchers.any());

    Call result = (Call) processCall.invoke(callOriginator);
    assertNull(result);

    Assert.assertTrue(callQueue.contains(call));
  }

  @Test
  public void givenNonPerCycleRetryStrategyWhenOriginatingThenAttemptsIncrementForEveryCall() {
    for (RETRY_STRATEGY retryStrategy : Arrays.asList(
        RETRY_STRATEGY.REACH_RETRY_LIMIT_FOR_SINGLE_NUMBER,
        RETRY_STRATEGY.REACH_RETRY_LIMIT_BEFORE_NEXT_NUMBER,
        RETRY_STRATEGY.ROUND_ROBIN_DIAL_WITH_OVERALL_RETRY_LIMIT_SCHEDULE_NEXT_NUMBER,
        RETRY_STRATEGY.ROUND_ROBIN_DIAL_WITH_OVERALL_RETRY_LIMIT_SCHEDULE_NEXT_CYCLE)) {
      Call call = createAttemptsCall(retryStrategy, "number_1", "", 0);

      callOriginator.incrementAttemptsForOrigination(call);

      assertEquals("attempts should increment for " + retryStrategy, 1, call.getAttempts());
    }
  }

  @Test
  public void givenPerCycleStrategyWhenCycleHasRemainingNumbersThenAttemptsDoesNotIncrement() {
    Call call = createAttemptsCall(
        RETRY_STRATEGY.ROUND_ROBIN_DIAL_WITH_PER_CYCLE_RETRY_LIMIT_SCHEDULE_NEXT_NUMBER,
        "number_1",
        "",
        0);

    callOriginator.incrementAttemptsForOrigination(call);

    assertEquals(0, call.getAttempts());
  }

  @Test
  public void givenPerCycleStrategyWhenCycleHasRemainingNumbersThenCallerIdAttemptUsesCurrentCycle() {
    Call call = createAttemptsCall(
        RETRY_STRATEGY.ROUND_ROBIN_DIAL_WITH_PER_CYCLE_RETRY_LIMIT_SCHEDULE_NEXT_NUMBER,
        "number_1",
        "",
        0);

    int callerIdAttempt = callOriginator.callerIdAttemptForOrigination(call);
    callOriginator.incrementAttemptsForOrigination(call);

    assertEquals(2, callerIdAttempt);
    assertEquals(0, call.getAttempts());
  }

  @Test
  public void givenPerCycleStrategyWhenAllUsableNumbersAreDialedThenAttemptsIncrements() {
    Call call = createAttemptsCall(
        RETRY_STRATEGY.ROUND_ROBIN_DIAL_WITH_PER_CYCLE_RETRY_LIMIT_SCHEDULE_NEXT_NUMBER,
        "number_2",
        "number_2",
        0);

    callOriginator.incrementAttemptsForOrigination(call);

    assertEquals(1, call.getAttempts());
  }

  @Test
  public void givenPerCycleStrategyWhenDncLeavesSingleUsableNumberThenAttemptsIncrements() {
    Call call = createAttemptsCall(
        RETRY_STRATEGY.ROUND_ROBIN_DIAL_WITH_PER_CYCLE_RETRY_LIMIT_SCHEDULE_NEXT_NUMBER,
        "number_2",
        "",
        0);
    call.setDNCNumbers(new HashSet<>(Arrays.asList("number_1")));

    callOriginator.incrementAttemptsForOrigination(call);

    assertEquals(1, call.getAttempts());
  }

  @Test
  public void givenPerCycleCallbackWhenOriginatingThenAttemptsIncrements() {
    Call call = createAttemptsCallWithPhoneNumbers(
        RETRY_STRATEGY.ROUND_ROBIN_DIAL_WITH_PER_CYCLE_RETRY_LIMIT_SCHEDULE_NEXT_NUMBER,
        "number_2",
        "number_2",
        1,
        "number_1",
        "number_2",
        "number_3");
    call.getCampaign().setColResultCallbackDate("callback_date");
    call.getCampaign().setColResultCallbackNumber("callback_number");
    call.getRawData()
        .put("callback_date", 45678D)
        .put("callback_number", "number_2");
    call.setCallbackNumber("number_2");

    CallAttemptPolicy.incrementForOrigination(call);

    assertEquals(2, call.getAttempts());
  }

  @Test
  public void givenPerCycleSuccessBeforeCycleEndThenAttemptsIncrementsOnCompletion() {
    Call call = createAttemptsCallWithPhoneNumbers(
        RETRY_STRATEGY.ROUND_ROBIN_DIAL_WITH_PER_CYCLE_RETRY_LIMIT_SCHEDULE_NEXT_NUMBER,
        "number_2",
        "number_2",
        0,
        "number_1",
        "number_2",
        "number_3");
    call.setTelephonyResultCode(telephonyResultCode(10L));

    CallAttemptPolicy.incrementForSuccessfulCompletionIfNeeded(call);

    assertEquals(1, call.getAttempts());
  }

  @Test
  public void givenPerCycleSuccessAtCycleEndThenCompletionDoesNotDoubleIncrementAttempts() {
    Call call = createAttemptsCallWithPhoneNumbers(
        RETRY_STRATEGY.ROUND_ROBIN_DIAL_WITH_PER_CYCLE_RETRY_LIMIT_SCHEDULE_NEXT_NUMBER,
        "number_3",
        "number_2;number_3",
        0,
        "number_1",
        "number_2",
        "number_3");
    call.setTelephonyResultCode(telephonyResultCode(10L));

    CallAttemptPolicy.incrementForOrigination(call);
    CallAttemptPolicy.incrementForSuccessfulCompletionIfNeeded(call);

    assertEquals(1, call.getAttempts());
  }

  @Test
  public void givenPerCycleCallbackSuccessThenCompletionDoesNotDoubleIncrementAttempts() {
    Call call = createAttemptsCallWithPhoneNumbers(
        RETRY_STRATEGY.ROUND_ROBIN_DIAL_WITH_PER_CYCLE_RETRY_LIMIT_SCHEDULE_NEXT_NUMBER,
        "number_2",
        "number_2",
        1,
        "number_1",
        "number_2",
        "number_3");
    call.getCampaign().setColResultCallbackDate("callback_date");
    call.getCampaign().setColResultCallbackNumber("callback_number");
    call.getRawData()
        .put("callback_date", 45678D)
        .put("callback_number", "number_2");
    call.setCallbackNumber("number_2");
    call.setTelephonyResultCode(telephonyResultCode(24L));

    CallAttemptPolicy.incrementForOrigination(call);
    CallAttemptPolicy.incrementForSuccessfulCompletionIfNeeded(call);

    assertEquals(2, call.getAttempts());
  }

  // --- Origination throttle characterization (FIX-GLB90048674 diag) ---
  // Tick bütçesi = min(maxLines − in-flight callQueue, cpsLimit) − uçuştaki LSC.
  // Bu davranış, 18:00 origination tıkanıklığı teşhisinin merkezinde: in-flight
  // callQueue maxLines'a dayanırsa bütçe 0'a düşer ve hiç yeni arama başlatılmaz.

  @Test
  public void availableOriginateSlotsThrottledToZeroWhenInFlightCallQueueReachesMaxLines() {
    Server server = mock(Server.class);
    given(server.getMaxLines()).willReturn(200);
    given(server.getCpsLimit()).willReturn(25);
    given(server.getIp()).willReturn("10.0.0.1");
    for (int i = 0; i < 200; i++) {
      callOriginator.getCallQueue().add(createCall("dl-" + i, campaign, null));
    }

    assertEquals(0, callOriginator.availableOriginateSlots(server));
  }

  @Test
  public void availableOriginateSlotsEqualsCpsLimitWhenInFlightCallQueueEmpty() {
    Server server = mock(Server.class);
    given(server.getMaxLines()).willReturn(200);
    given(server.getCpsLimit()).willReturn(25);

    assertEquals(25, callOriginator.availableOriginateSlots(server));
  }

  @Test
  public void availableOriginateSlotsReducedByInFlightLscProcessing() {
    Server server = mock(Server.class);
    given(server.getMaxLines()).willReturn(200);
    given(server.getCpsLimit()).willReturn(25);
    Whitebox.setInternalState(callOriginator, "callsCountUnderLSCProcessing", new AtomicInteger(10));

    assertEquals(15, callOriginator.availableOriginateSlots(server));
  }

  // --- callQueues registry davranış karakterizasyonu (thread-safety refactoru için) ---
  // addCallQueue idempotent kayıt yapar, removeCallQueue kaydı geri alır. Bu kontrat,
  // callQueues'un alttaki liste implementasyonundan (synchronized ArrayList ya da
  // CopyOnWriteArrayList) bağımsız olarak korunmalıdır.

  @Test
  public void addCallQueueRegistersQueueOnceAndRejectsDuplicate() {
    BlockingDeque<Call> queue = new LinkedBlockingDeque<>();

    Assert.assertTrue(callOriginator.addCallQueue(queue));
    Assert.assertFalse(callOriginator.addCallQueue(queue));
  }

  @Test
  public void addCallQueueAcceptsDistinctQueues() {
    BlockingDeque<Call> first = new LinkedBlockingDeque<>();
    BlockingDeque<Call> second = new LinkedBlockingDeque<>();

    Assert.assertTrue(callOriginator.addCallQueue(first));
    Assert.assertTrue(callOriginator.addCallQueue(second));
  }

  @Test
  public void removeCallQueueDetachesRegisteredQueue() {
    BlockingDeque<Call> queue = new LinkedBlockingDeque<>();
    callOriginator.addCallQueue(queue);

    Assert.assertTrue(callOriginator.removeCallQueue(queue));
    Assert.assertFalse(callOriginator.removeCallQueue(queue));
  }

  private Call createAttemptsCall(RETRY_STRATEGY retryStrategy, String calledNumber, String retryHistory, int attempts) {
    return createAttemptsCallWithPhoneNumbers(retryStrategy, calledNumber, retryHistory, attempts, "number_1", "number_2");
  }

  private Call createAttemptsCallWithPhoneNumbers(RETRY_STRATEGY retryStrategy,
                                                  String calledNumber,
                                                  String retryHistory,
                                                  int attempts,
                                                  String... numbers) {
    Campaign campaign = new Campaign();
    campaign.setRetryStrategy(retryStrategy);
    campaign.setColResultAttempts("attempts");
    campaign.setColResultRetryHistory("retry_history");
    PhoneNumberColumn[] phoneNumberColumns = new PhoneNumberColumn[numbers.length];
    JSONObject rawData = new JSONObject()
        .put("attempts", attempts)
        .put("retry_history", retryHistory);
    for (int i = 0; i < numbers.length; i++) {
      String columnName = "phone_number_" + (i + 1);
      phoneNumberColumns[i] = createPhoneNumberColumn(columnName);
      rawData.put(columnName, numbers[i]);
    }
    campaign.setPhoneNumberColumns(Arrays.asList(phoneNumberColumns));

    Call call = new Call();
    call.setCampaign(campaign);
    call.setCalledNumber(calledNumber);
    call.setRawData(rawData);
    return call;
  }

  private TelephonyResultCode telephonyResultCode(Long id) {
    TelephonyResultCode telephonyResultCode = new TelephonyResultCode();
    telephonyResultCode.setId(id);
    return telephonyResultCode;
  }

  private PhoneNumberColumn createPhoneNumberColumn(String columnName) {
    PhoneNumberColumn phoneNumberColumn = new PhoneNumberColumn();
    phoneNumberColumn.setEnabled(true);
    phoneNumberColumn.setColNumber(columnName);
    return phoneNumberColumn;
  }

}
