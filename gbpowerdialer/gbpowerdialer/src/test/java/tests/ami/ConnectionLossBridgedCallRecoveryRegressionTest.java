package tests.ami;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.powerdialer.engine.CallCleaner;
import com.powerdialer.engine.acd.ACDEngine;
import com.powerdialer.engine.asterisk.CallerIdManager;
import com.powerdialer.engine.asterisk.ami.ManagerConnectionComposition;
import com.powerdialer.engine.asterisk.ami.ManagerConnectionDecorator;
import com.powerdialer.engine.db.CampaignDbOperations;
import com.powerdialer.engine.dialer.CallAttemptPolicy;
import com.powerdialer.engine.dialer.CallOriginator;
import com.powerdialer.engine.dialer.DialerFacade;
import com.powerdialer.model.CDR;
import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.PhoneNumberColumn;
import com.powerdialer.model.Server;
import com.powerdialer.model.TelephonyResultCode;
import com.powerdialer.model.User;
import com.powerdialer.service.CampaignResultService;
import com.powerdialer.util.Constants;
import com.powerdialer.util.LoggerDecorator;
import com.powerdialer.util.Utils;

@RunWith(MockitoJUnitRunner.class)
public class ConnectionLossBridgedCallRecoveryRegressionTest {

  private static final String CALL_ID = "dl-connection-loss-bridge-1";

  @Mock
  private ManagerConnectionDecorator connectionDecorator;

  @Mock
  private ManagerConnectionComposition connectionComposition;

  @Mock
  private LoggerDecorator loggerDecorator;

  @Mock
  private DialerFacade dialerFacade;

  @Mock
  private CallerIdManager callerIdManager;

  @Mock
  private CallCleaner callCleaner;

  @Mock
  private CampaignResultService campaignResultService;

  @Mock
  private CampaignDbOperations dbOperations;

  @Mock
  private ACDEngine acdEngine;

  private Cache<String, Call> activeCalls;
  private Cache<String, Call> recoverableConnectionLossCalls;
  private CallOriginator callOriginator;
  private TelephonyResultCode amiConnectionLoss;

  @Before
  public void setUp() {
    activeCalls = CacheBuilder.newBuilder().build();
    recoverableConnectionLossCalls = CacheBuilder.newBuilder().build();
    amiConnectionLoss = telephonyResultCode(903L, Constants.ERROR_RESULT_CODES.AMI_CONNECTION_LOSS.getCode());

    when(connectionDecorator.getConnectionComposition()).thenReturn(connectionComposition);
    when(connectionDecorator.getCalls()).thenReturn(activeCalls);
    when(connectionDecorator.getDbOperations()).thenReturn(dbOperations);
    when(connectionDecorator.getServer()).thenReturn(server());
    when(connectionDecorator.getItuToTelephonyResultCodeRelations()).thenReturn(Collections.singletonMap(
        Constants.ERROR_RESULT_CODES.AMI_CONNECTION_LOSS.getCode(), amiConnectionLoss));
    when(connectionComposition.getLoggerDecorator()).thenReturn(loggerDecorator);
    when(loggerDecorator.getLogger(CallOriginator.class)).thenReturn(loggerDecorator);

    doAnswer(invocation -> {
      Call call = invocation.getArgument(0);
      activeCalls.invalidate(call.getId());
      recoverableConnectionLossCalls.put(call.getId(), call);
      return null;
    }).when(connectionDecorator).deferCallCleanupOnConnectionLoss(any(Call.class));

    callOriginator = spy(new CallOriginator(connectionDecorator, dialerFacade, callerIdManager, callCleaner, campaignResultService));
  }

  @Test
  public void shouldDeferBridgedCallOnConnectionLossWithoutFinalCleanup() {
    Call call = bridgedCall();
    activeCalls.put(call.getId(), call);
    call.getCampaign().addCallInProgress(call);
    call.getCampaign().addToOriginatedCalls(call);
    call.getCampaign().setActiveCalls(1);
    call.getCampaign().incrementInCallsSize(call);

    ReflectionTestUtils.invokeMethod(callOriginator, "cleanUpCallsOnConnectionLoss");

    assertThat(activeCalls.getIfPresent(call.getId()), nullValue());
    assertThat(recoverableConnectionLossCalls.getIfPresent(call.getId()), sameInstance(call));
    assertThat(call.getTelephonyResultCode(), nullValue());
    assertThat(call.getCompleteDate(), nullValue());
    assertThat(call.getCampaign().getCallsInProgressCount(), is(1));
    assertThat(call.getCampaign().hasCallsInProgress(), is(true));
    assertThat(call.getCampaign().getActiveCalls().get(), is(1));

    verify(callCleaner, never()).completeCall(call);
    verify(connectionDecorator, never()).clearCall(call);
    verify(dbOperations, never()).insertCallResultToDB(call);
    verify(callOriginator, never()).saveCDR(call);
    verify(acdEngine, never()).addToIdleUsers(call.getUser());
  }

  @Test
  public void shouldKeepConnectionLossCleanupForCallsWithoutBridgeEvidence() {
    Call call = ringingCall();
    activeCalls.put(call.getId(), call);
    call.getCampaign().addCallInProgress(call);
    doNothing().when(callOriginator).saveCDR(call);

    ReflectionTestUtils.invokeMethod(callOriginator, "cleanUpCallsOnConnectionLoss");

    assertThat(recoverableConnectionLossCalls.getIfPresent(call.getId()), nullValue());
    assertThat(call.getTelephonyResultCode(), sameInstance(amiConnectionLoss));
    assertThat(call.getCampaign().getCallsInProgressCount(), is(0));

    verify(callCleaner).completeCall(call);
    verify(connectionDecorator).clearCall(call);
    verify(dbOperations).insertCallResultToDB(call);
    verify(callOriginator).saveCDR(call);
  }

  @Test
  public void shouldCountAttemptsWhenRegistrarDisconnectFinalizesAmiConnectionLoss() {
    TelephonyResultCode trc75AmiConnectionLoss = telephonyResultCode(75L, Constants.ERROR_RESULT_CODES.AMI_CONNECTION_LOSS.getCode());
    when(connectionDecorator.getItuToTelephonyResultCodeRelations()).thenReturn(Collections.singletonMap(
        Constants.ERROR_RESULT_CODES.AMI_CONNECTION_LOSS.getCode(), trc75AmiConnectionLoss));
    Call call = ringingCall();
    call.getCampaign().setColResultAttempts("attempts");
    call.setRawData(call.getRawData().put("attempts", 2));
    CallAttemptPolicy.incrementForOrigination(call);
    activeCalls.put(call.getId(), call);
    call.getCampaign().addCallInProgress(call);
    doNothing().when(callOriginator).saveCDR(call);

    assertThat(call.getAttempts(), is(3));

    ReflectionTestUtils.invokeMethod(callOriginator, "cleanUpCallsOnConnectionLoss");

    assertThat(call.getTelephonyResultCode(), sameInstance(trc75AmiConnectionLoss));
    assertThat(call.getAttempts(), is(3));
    verify(dbOperations).insertCallResultToDB(call);
  }

  @Test
  public void shouldTransferRecoverableCallWhenConnectionDecoratorIsReplaced() {
    Call call = bridgedCall();
    Cache<String, Call> newRecoverableConnectionLossCalls = CacheBuilder.newBuilder().build();
    ManagerConnectionDecorator newConnectionDecorator = mock(ManagerConnectionDecorator.class);
    Map<String, ManagerConnectionDecorator> openedConnections = new HashMap<>();
    openedConnections.put("127.0.0.1", newConnectionDecorator);

    recoverableConnectionLossCalls.put(call.getId(), call);
    when(connectionDecorator.getRecoverableConnectionLossCalls()).thenReturn(recoverableConnectionLossCalls);
    when(connectionComposition.getOpenedConnections()).thenReturn(openedConnections);
    doAnswer(invocation -> {
      Call deferredCall = invocation.getArgument(0);
      newRecoverableConnectionLossCalls.put(deferredCall.getId(), deferredCall);
      return null;
    }).when(newConnectionDecorator).deferCallCleanupOnConnectionLoss(any(Call.class));

    ReflectionTestUtils.invokeMethod(callOriginator, "setNewConnectionDecoratorIfExists");

    assertThat(recoverableConnectionLossCalls.getIfPresent(call.getId()), nullValue());
    assertThat(newRecoverableConnectionLossCalls.getIfPresent(call.getId()), sameInstance(call));
  }

  private Call bridgedCall() {
    Call call = baseCall();
    call.setUser(user(10L));
    call.setStartDate(Utils.convertDateToDouble(new java.util.Date(1000L)));
    call.setAnswerDate(Utils.convertDateToDouble(new java.util.Date(2000L)));
    call.setAnsweredByUser(true);
    call.getCdr().setCallHandlingStartTime(new java.util.Date(2500L));
    return call;
  }

  private Call ringingCall() {
    Call call = baseCall();
    call.setStartDate(Utils.convertDateToDouble(new java.util.Date(1000L)));
    return call;
  }

  private Call baseCall() {
    Campaign campaign = new Campaign();
    campaign.setId(85L);
    campaign.setColResultRcSequence("rc_sequence");
    PhoneNumberColumn phoneNumberColumn = new PhoneNumberColumn();
    phoneNumberColumn.setColNumber("phone1");
    campaign.getPhoneNumberColumns().add(phoneNumberColumn);

    Call call = new Call();
    call.setId(CALL_ID);
    call.setCampaign(campaign);
    call.setCallOriginator(callOriginator);
    call.setResultCodeSequence(0);
    call.setCalledNumber("5551000");
    call.setRawData(new org.json.JSONObject());

    CDR cdr = new CDR();
    cdr.setInteractionId(CALL_ID);
    cdr.setCallDialTime(new java.util.Date(500L));
    call.setCdr(cdr);
    return call;
  }

  private User user(Long id) {
    User user = new User();
    user.setId(id);
    return user;
  }

  private TelephonyResultCode telephonyResultCode(Long id, int code) {
    TelephonyResultCode telephonyResultCode = new TelephonyResultCode();
    telephonyResultCode.setId(id);
    telephonyResultCode.setCode(code);
    telephonyResultCode.setName("TRC-" + code);
    return telephonyResultCode;
  }

  private Server server() {
    Server server = new Server();
    server.setIp("127.0.0.1");
    return server;
  }
}
