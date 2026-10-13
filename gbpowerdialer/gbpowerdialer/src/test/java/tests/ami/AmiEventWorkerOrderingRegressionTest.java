package tests.ami;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.asteriskjava.manager.event.VarSetEvent;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import com.powerdialer.engine.CallCleaner;
import com.powerdialer.engine.CallUpToDateKeeper;
import com.powerdialer.engine.StopConditionChecker;
import com.powerdialer.engine.asterisk.ami.ManagerConnectionComposition;
import com.powerdialer.engine.asterisk.ami.ManagerConnectionDecorator;
import com.powerdialer.engine.asterisk.events.ClientPredictiveAnswerEvent;
import com.powerdialer.engine.asterisk.events.DialerHangupEvent;
import com.powerdialer.engine.db.CampaignDbOperations;
import com.powerdialer.engine.dialer.CallOriginator;
import com.powerdialer.engine.dialer.CampaignContext;
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
 * Invariant #1 (sıra korunur): gerçek tek-thread'li worker enjekte edildiğinde,
 * aynı çağrı için ardışık gelen event'ler ({@code VarSet} -> {@code ClientAnswer}
 * -> {@code DialerHangup}) submit edildikleri sırada (FIFO) işlenmelidir.
 *
 * <p>Sıra, DB/CDR çağrılarının (insertCallResultToDB / saveOrUpdate) shared
 * recording list'e ekleme anı üzerinden gözlenir. Tek thread => deterministik;
 * sleep yok, {@code awaitTermination} ile drain edilip beklenir.
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class AmiEventWorkerOrderingRegressionTest {

  private static final String CALL_ID = "dl-ordering-1";

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
  private ExecutorService realWorker;
  private final List<String> processingOrder = new CopyOnWriteArrayList<>();

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

    // VarSet ve ClientAnswer handler'ları insertCallResultToDB çağırır; hangup ise
    // persistHangupSnapshotForDelayedResult -> cdrService.saveOrUpdate çağırır.
    doAnswer(invocation -> {
      processingOrder.add("db:" + ((Call) invocation.getArgument(0)).getResultCodeSequence());
      return null;
    }).when(dbOperations).insertCallResultToDB(org.mockito.ArgumentMatchers.any(Call.class));
    doAnswer(invocation -> {
      processingOrder.add("cdr");
      return null;
    }).when(cdrService).saveOrUpdate(org.mockito.ArgumentMatchers.any(CDR.class));

    realWorker = Executors.newSingleThreadExecutor();
    ReflectionTestUtils.invokeMethod(decorator, "setEventWorker", realWorker);
  }

  @After
  public void tearDown() throws InterruptedException {
    realWorker.shutdown();
    realWorker.awaitTermination(5, TimeUnit.SECONDS);
  }

  @Test
  public void shouldProcessEventsForSameCallInSubmissionOrder() throws InterruptedException {
    Call call = ringingCallAwaitingAnswer();
    call.setUser(userWithResultCodes());
    call.setCallOriginator(mock(CallOriginator.class));
    decorator.getCalls().put(call.getId(), call);
    Map<Long, CampaignContext> campaignContexts = campaignContexts(call.getCampaign());
    when(dialerFacade.getCampaignContexts()).thenReturn(campaignContexts);

    VarSetEvent varSet = new VarSetEvent(this);
    varSet.setVariable("ORIGINID");
    varSet.setValue(CALL_ID);
    varSet.setChannel("Local/5551000@out;1");
    varSet.setUniqueId("UID-1");
    varSet.setDateReceived(new java.util.Date(1000L));

    ClientPredictiveAnswerEvent answer = new ClientPredictiveAnswerEvent(this);
    answer.setParentId(CALL_ID);
    answer.setClientchannel("SIP/customer-1");
    answer.setDateReceived(new java.util.Date(11000L));

    DialerHangupEvent hangup = new DialerHangupEvent(this);
    hangup.setOriginId(CALL_ID);
    hangup.setHangupCause(String.valueOf(Constants.TELEPHONY_RC_NORMAL_CLEARING));
    hangup.setDateReceived(new java.util.Date(21000L));

    // startDate null iken VarSet startDate set eder; sonra answer answerDate set eder.
    // CDR id set olduğu için answer/hangup yolu getByInteractionId yapmadan saveOrUpdate'e gider.
    decorator.onManagerEvent(varSet);
    decorator.onManagerEvent(answer);
    decorator.onManagerEvent(hangup);

    realWorker.shutdown();
    assertThat("worker makul sürede drain olmalı", realWorker.awaitTermination(5, TimeUnit.SECONDS), is(true));

    // VarSet -> db (insertCallResultToDB), ClientAnswer -> db + cdr, hangup -> cdr (snapshot).
    // Submission FIFO: VarSet'in db'si, ClientAnswer'ın db'si, sonrasında gelen cdr yazımları.
    assertThat("ilk işlenen VarSet handler'ının DB yazımı olmalı",
        processingOrder.get(0), is("db:0"));
    assertThat("ikinci işlenen ClientAnswer handler'ının DB yazımı olmalı",
        processingOrder.get(1), is("db:0"));
    // Kalan yazımlar CDR (answer save + hangup snapshot); hepsi VarSet/Answer DB'sinden SONRA gelir.
    assertThat("toplam 4 yazım gözlenmeli (VarSet db, Answer db, Answer cdr, hangup cdr)",
        processingOrder.size(), is(4));
    assertThat(processingOrder.get(2), is("cdr"));
    assertThat(processingOrder.get(3), is("cdr"));
  }

  private Call ringingCallAwaitingAnswer() {
    Campaign campaign = new Campaign();
    campaign.setId(45L);
    campaign.setType(Constants.CAMPAIGN_TYPE.PROGRESSIVE_RELEASED);
    campaign.setColResultRcSequence("rc_sequence");
    campaign.setColResultUserRc("user_rc");
    campaign.setColResultTelephonyRc("telephony_rc");
    campaign.setColResultRetryHistory("retry_history");
    campaign.setRetryScheduleEnabled(false);
    PhoneNumberColumn phoneNumberColumn = new PhoneNumberColumn();
    phoneNumberColumn.setColNumber("phone1");
    campaign.getPhoneNumberColumns().add(phoneNumberColumn);

    Call call = new Call();
    call.setId(CALL_ID);
    call.setCampaign(campaign);
    call.setResultCodeSequence(0);
    call.setCalledNumber("5551000");
    call.setRawData(new JSONObject().put("phone1", "5551000"));

    CDR cdr = new CDR();
    cdr.setId(2002L);
    cdr.setInteractionId(CALL_ID);
    cdr.setCallEndTime(new java.util.Date(1L));
    cdr.setCallHandlingStartTime(new java.util.Date());
    call.setCdr(cdr);
    return call;
  }

  private User userWithResultCodes() {
    Group group = new Group();
    group.setResultCodes(Collections.singletonList(new ResultCode()));
    User user = new User();
    user.setGroup(group);
    return user;
  }

  private Map<Long, CampaignContext> campaignContexts(Campaign campaign) {
    CampaignContext campaignContext = mock(CampaignContext.class);
    when(campaignContext.getCampaign()).thenReturn(campaign);
    Map<Long, CampaignContext> campaignContexts = new HashMap<>();
    campaignContexts.put(campaign.getId(), campaignContext);
    return campaignContexts;
  }

  private Map<Integer, TelephonyResultCode> telephonyResultCodes() {
    Map<Integer, TelephonyResultCode> resultCodes = new HashMap<>();
    resultCodes.put(Constants.TELEPHONY_RC_NORMAL_CLEARING,
        telephonyResultCode(16L, Constants.TELEPHONY_RC_NORMAL_CLEARING));
    resultCodes.put(Constants.TELEPHONY_RC_CUSTOMER_HANGUP_BEFORE_AGENT,
        telephonyResultCode(911L, Constants.TELEPHONY_RC_CUSTOMER_HANGUP_BEFORE_AGENT));
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
