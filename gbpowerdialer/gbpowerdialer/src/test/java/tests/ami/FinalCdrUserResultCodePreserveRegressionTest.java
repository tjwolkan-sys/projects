package tests.ami;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
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
import com.powerdialer.model.Group;
import com.powerdialer.model.PhoneNumberColumn;
import com.powerdialer.model.ResultCode;
import com.powerdialer.model.Server;
import com.powerdialer.model.TelephonyResultCode;
import com.powerdialer.model.User;
import com.powerdialer.redis.CallEventPublisher;
import com.powerdialer.service.CDRService;
import com.powerdialer.util.Constants;
import com.powerdialer.util.LoggerDecorator;
import com.powerdialer.util.Utils;

/**
 * `persistFinalCdr`'ın in-memory CDR üzerinde zaten dolu olan `user_result_code_id`
 * değerini, `call.userResultCode` null olduğu için NULL'a düşürmediğini doğrular.
 *
 * <p>Üretim vakası (04.08.2026): webphone durable fallback pd_cdr'a URC yazdıktan 139 ms
 * sonra awaiting-timeout finalize'ı aynı satıra NULL yazdı. Bu sınıf, finalize persist
 * sözleşmesinin "bilgim yok" durumunu "değer yok" diye yazmamasını IO eklemeden kilitler.</p>
 *
 * <p>`UserPopulationEvaluator`'ın başarısız çağrılarda (TRC 84 gibi) URC'yi bastırma
 * yetkisinin korunduğu da karakterizasyon senaryosuyla kilitlenir.</p>
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class FinalCdrUserResultCodePreserveRegressionTest {

  private static final String CALL_ID = "dl-final-cdr-urc-preserve-1";
  private static final Long PERSISTED_RESULT_CODE_ID = 171L;

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
  private TelephonyResultCode answeredButNoMtConnection;

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
  public void shouldKeepInMemoryCdrUserResultCodeWhenCallResultCodeIsCleared() {
    Call call = successfulCall(normalClearing);
    call.getCdr().setUserResultCodeId(PERSISTED_RESULT_CODE_ID);
    call.setUserResultCode(null);

    boolean persisted = decorator.persistFinalCdr(call, new Date(9000L), false);

    assertTrue("Finalize persist başarılı olmalı.", persisted);
    assertThat("in-memory CDR'da dolu olan user_result_code_id NULL'a düşürülmemeli.",
        savedCdr().getUserResultCodeId(), is(PERSISTED_RESULT_CODE_ID));
  }

  @Test
  public void shouldSuppressUserResultCodeWhenEvaluatorRejectsEvenIfCdrHasValue() {
    Call call = successfulCall(answeredButNoMtConnection);
    call.getCdr().setCallHandlingStartTime(null);
    call.getCdr().setUserResultCodeId(PERSISTED_RESULT_CODE_ID);
    call.setUserResultCode(null);

    decorator.persistFinalCdr(call, new Date(9000L), false);

    assertThat("TRC 84'te URC bastırma kasıtlıdır; UserPopulationEvaluator yetkisi korunmalı.",
        savedCdr().getUserResultCodeId(), is((Long) null));
  }

  @Test
  public void shouldPreferCallUserResultCodeOverInMemoryCdrValue() {
    Call call = successfulCall(normalClearing);
    call.getCdr().setUserResultCodeId(PERSISTED_RESULT_CODE_ID);
    ResultCode agentResultCode = new ResultCode();
    agentResultCode.setId(200L);
    call.setUserResultCode(agentResultCode);

    decorator.persistFinalCdr(call, new Date(9000L), false);

    assertThat("Agent'ın canlı result code'u in-memory CDR değerinden önceliklidir.",
        savedCdr().getUserResultCodeId(), is(200L));
  }

  private CDR savedCdr() {
    ArgumentCaptor<CDR> cdrCaptor = ArgumentCaptor.forClass(CDR.class);
    verify(cdrService).saveOrUpdate(cdrCaptor.capture());
    return cdrCaptor.getValue();
  }

  private Call successfulCall(TelephonyResultCode telephonyResultCode) {
    Campaign campaign = new Campaign();
    campaign.setId(64L);
    campaign.setType(Constants.CAMPAIGN_TYPE.PROGRESSIVE_RELEASED);
    campaign.setColResultUserRc("user_rc");
    campaign.setRetryScheduleEnabled(false);
    PhoneNumberColumn phoneNumberColumn = new PhoneNumberColumn();
    phoneNumberColumn.setColNumber("phone1");
    campaign.getPhoneNumberColumns().add(phoneNumberColumn);

    Call call = new Call();
    call.setId(CALL_ID);
    call.setCampaign(campaign);
    call.setCalledNumber("5551000");
    call.setRawData(new JSONObject().put("phone1", "5551000"));
    call.setResultCodeSequence(1);
    call.setStartDate(Utils.convertDateToDouble(new Date(1000L)));
    call.setAnswerDate(Utils.convertDateToDouble(new Date(2000L)));
    call.setAnsweredByUser(true);
    call.setUser(agent());
    call.setTelephonyResultCode(telephonyResultCode);
    call.setCallOriginator(mock(CallOriginator.class));

    CDR cdr = new CDR();
    cdr.setId(7007L);
    cdr.setInteractionId(CALL_ID);
    cdr.setCallDialTime(new Date(1500L));
    cdr.setCallAnswerTime(new Date(2000L));
    cdr.setCallHandlingStartTime(new Date(2500L));
    cdr.setTelephonyRC(telephonyResultCode);
    call.setCdr(cdr);
    return call;
  }

  private User agent() {
    Group group = new Group();
    group.setResultCodes(Collections.singletonList(new ResultCode()));
    User user = new User();
    user.setId(9930L);
    user.setGroup(group);
    return user;
  }

  private Map<Integer, TelephonyResultCode> telephonyResultCodes() {
    normalClearing = telephonyResultCode(10L, Constants.TELEPHONY_RC_NORMAL_CLEARING);
    answeredButNoMtConnection = telephonyResultCode(
        (long) Constants.TELEPHONY_RC_ANSWERED_BUT_NO_MT_CONNECTION, Constants.TELEPHONY_RC_CUSTOMER_HANGUP_BEFORE_AGENT);
    Map<Integer, TelephonyResultCode> resultCodes = new HashMap<>();
    resultCodes.put(Constants.TELEPHONY_RC_NORMAL_CLEARING, normalClearing);
    resultCodes.put(Constants.TELEPHONY_RC_CUSTOMER_HANGUP_BEFORE_AGENT, answeredButNoMtConnection);
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
