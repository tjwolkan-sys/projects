package tests.ami;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.powerdialer.engine.CallCleaner;
import com.powerdialer.engine.CallUpToDateKeeper;
import com.powerdialer.engine.StopConditionChecker;
import com.powerdialer.engine.asterisk.ami.ManagerConnectionComposition;
import com.powerdialer.engine.asterisk.ami.ManagerConnectionDecorator;
import com.powerdialer.engine.db.CampaignDbOperations;
import com.powerdialer.engine.dialer.CallOriginator;
import com.powerdialer.engine.dialer.CampaignRealTimeRingingManager;
import com.powerdialer.engine.dialer.DialerFacade;
import com.powerdialer.model.CDR;
import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.PhoneNumberColumn;
import com.powerdialer.model.Server;
import com.powerdialer.model.TelephonyResultCode;
import com.powerdialer.redis.CallEventPublisher;
import com.powerdialer.service.CDRService;
import com.powerdialer.util.Constants;
import com.powerdialer.util.LoggerDecorator;
import com.powerdialer.util.Utils;

/**
 * FIX-320173 persist-first regression'ları: pd_cdr finalize persist'i yan etkilerden bağımsız
 * çalışır; tek bir yan etki hatası persist'i yutamaz ve answered+null-TRC kombinasyonu finalize'ı
 * NPE ile kesemez.
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class ProcessCallResultPersistFirstRegressionTest {

  @Mock
  private LoggerDecorator loggerDecorator;

  @Mock
  private ManagerConnectionComposition connectionComposition;

  @Mock
  private CDRService cdrService;

  @Mock
  private DialerFacade dialerFacade;

  @Mock
  private CallEventPublisher callEventPublisher;

  @Mock
  private CampaignDbOperations dbOperations;

  @Mock
  private CampaignRealTimeRingingManager campaignRealTimeRingingManager;

  @Mock
  private StopConditionChecker stopConditionChecker;

  @Mock
  private CallCleaner callCleaner;

  @Mock
  private CallUpToDateKeeper callUpToDateKeeper;

  private ManagerConnectionDecorator decorator;
  private TelephonyResultCode normalClearing;

  @Before
  public void setUp() {
    when(loggerDecorator.getLogger(ManagerConnectionDecorator.class)).thenReturn(loggerDecorator);
    when(connectionComposition.getLoggerDecorator()).thenReturn(loggerDecorator);
    when(connectionComposition.getDialerFacade()).thenReturn(dialerFacade);
    when(connectionComposition.getStopConditionChecker()).thenReturn(stopConditionChecker);
    when(connectionComposition.getCallCleaner()).thenReturn(callCleaner);
    when(connectionComposition.getCallUpToDateKeeper()).thenReturn(callUpToDateKeeper);
    when(dialerFacade.getCallEventPublisher()).thenReturn(callEventPublisher);
    when(dialerFacade.getCampaignRealTimeRingingManager()).thenReturn(campaignRealTimeRingingManager);

    decorator = new ManagerConnectionDecorator("127.0.0.1", 5038, "user", "password", connectionComposition);
    decorator.setCdrService(cdrService);
    decorator.setServer(server());
    decorator.setDbOperations(dbOperations);
    decorator.setItuToTelephonyResultCodeRelations(telephonyResultCodes());
  }

  @Test
  public void shouldPersistFinalCdrWhenRetrySideEffectFails() {
    Call call = answeredCall("dl-persist-first-retry-1");
    Campaign campaign = call.getCampaign();
    // Retry yan etkisini doğal yoldan patlatır: strateji createRetry içinde
    // originator.getCallCleaner() null döndüğü için NPE atar.
    campaign.setRetryScheduleEnabled(true);
    campaign.setRetryStrategy(Constants.RETRY_STRATEGY.REACH_RETRY_LIMIT_FOR_SINGLE_NUMBER);
    campaign.addCallInProgress(call);
    campaign.addToOriginatedCalls(call);
    campaign.setActiveCalls(1);
    decorator.getCalls().put(call.getId(), call);
    Date completeDate = new Date(15000L);

    decorator.processCallResult(call, completeDate);

    ArgumentCaptor<CDR> cdrCaptor = ArgumentCaptor.forClass(CDR.class);
    verify(cdrService).saveOrUpdate(cdrCaptor.capture());
    assertThat(cdrCaptor.getValue().getTelephonyRC().getCode(), is(Constants.TELEPHONY_RC_NORMAL_CLEARING));
    assertThat(cdrCaptor.getValue().getCallEndTime(), is(completeDate));
    assertThat(call.isBusinessResultFinalized(), is(true));
    // Retry grubu patlasa bile slot/sayaç grubu ve kampanya result insert'i çalışmaya devam eder.
    assertThat(campaign.getCallsInProgressCount(), is(0));
    assertThat(campaign.getActiveCalls().get(), is(0));
    verify(dbOperations).insertCallResultToDB(call);
  }

  @Test
  public void shouldIsolateStopConditionFailureFromPersistAndOtherSideEffects() {
    Call call = answeredCall("dl-persist-first-stop-1");
    Campaign campaign = call.getCampaign();
    campaign.setRetryScheduleEnabled(false);
    campaign.addCallInProgress(call);
    campaign.addToOriginatedCalls(call);
    campaign.setActiveCalls(1);
    decorator.getCalls().put(call.getId(), call);
    doThrow(new RuntimeException("stop condition failure")).when(stopConditionChecker).isStopConditionReached(any(Campaign.class));

    decorator.processCallResult(call, new Date(15000L));

    verify(cdrService).saveOrUpdate(any(CDR.class));
    verify(dbOperations).insertCallResultToDB(call);
    assertThat(call.isBusinessResultFinalized(), is(true));
    assertThat(campaign.getCallsInProgressCount(), is(0));
  }

  @Test
  public void shouldNotThrowWhenAnsweredCallHasNullTelephonyResultCode() {
    Call call = answeredCall("dl-persist-first-null-trc-1");
    call.setTelephonyResultCode(null);
    Campaign campaign = call.getCampaign();
    campaign.setRetryScheduleEnabled(false);
    campaign.addCallInProgress(call);
    campaign.addToOriginatedCalls(call);
    campaign.setActiveCalls(1);
    decorator.getCalls().put(call.getId(), call);

    decorator.processCallResult(call, new Date(15000L));

    ArgumentCaptor<CDR> cdrCaptor = ArgumentCaptor.forClass(CDR.class);
    verify(cdrService).saveOrUpdate(cdrCaptor.capture());
    assertThat(cdrCaptor.getValue().getTelephonyRC(), nullValue());
    assertThat(call.isBusinessResultFinalized(), is(true));
    assertThat(campaign.getCallsInProgressCount(), is(0));
  }

  private Call answeredCall(String callId) {
    Campaign campaign = new Campaign();
    campaign.setId(81L);
    campaign.setType(Constants.CAMPAIGN_TYPE.PROGRESSIVE_RELEASED);
    campaign.setColResultRcSequence("rc_sequence");
    campaign.setColResultUserRc("user_rc");
    campaign.setColResultTelephonyRc("telephony_rc");
    campaign.setColResultRetryHistory("retry_history");
    PhoneNumberColumn phoneNumberColumn = new PhoneNumberColumn();
    phoneNumberColumn.setColNumber("phone1");
    campaign.getPhoneNumberColumns().add(phoneNumberColumn);

    Call call = new Call();
    call.setId(callId);
    call.setCampaign(campaign);
    call.setCalledNumber("5551000");
    call.setRawData(new JSONObject().put("phone1", "5551000"));
    call.setResultCodeSequence(0);
    call.setStartDate(Utils.convertDateToDouble(new Date(1000L)));
    call.setAnswerDate(Utils.convertDateToDouble(new Date(2000L)));
    call.setTelephonyResultCode(normalClearing);
    call.setCallOriginator(mock(CallOriginator.class));

    CDR cdr = new CDR();
    cdr.setId(2002L);
    cdr.setInteractionId(callId);
    cdr.setCallDialTime(new Date(1500L));
    cdr.setCallEndTime(new Date(1600L));
    cdr.setCallAnswerTime(new Date(2000L));
    cdr.setCallHandlingStartTime(new Date(2100L));
    call.setCdr(cdr);
    return call;
  }

  private Map<Integer, TelephonyResultCode> telephonyResultCodes() {
    normalClearing = telephonyResultCode(10L, Constants.TELEPHONY_RC_NORMAL_CLEARING);
    Map<Integer, TelephonyResultCode> resultCodes = new HashMap<>();
    resultCodes.put(Constants.TELEPHONY_RC_NORMAL_CLEARING, normalClearing);
    resultCodes.put(Constants.ERROR_RESULT_CODES.CALL_EXPIRED_CLEARING.getCode(),
        telephonyResultCode(73L, Constants.ERROR_RESULT_CODES.CALL_EXPIRED_CLEARING.getCode()));
    return resultCodes;
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
