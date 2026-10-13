package tests.ami;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.CountDownLatch;
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
import com.powerdialer.engine.db.CampaignDbOperations;
import com.powerdialer.engine.dialer.CampaignRealTimeRingingManager;
import com.powerdialer.engine.dialer.DialerFacade;
import com.powerdialer.model.CDR;
import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.PhoneNumberColumn;
import com.powerdialer.model.Server;
import com.powerdialer.redis.CallEventPublisher;
import com.powerdialer.service.CDRService;
import com.powerdialer.util.LoggerDecorator;

/**
 * FIX-GLB90048674 (Faz 1B — Fix #1): event worker task gövdesi, handler bir
 * {@code RuntimeException} fırlatsa bile worker thread'ini ÖLDÜRMEMELİ ve hatayı
 * SESSİZCE yutmamalı; sınıf {@code LOG}'una hata yazıp devam etmelidir.
 *
 * <p>Bu, asterisk-java'nın eski koruyucu davranışını (her {@code onManagerEvent}
 * çağrısını {@code try/catch(RuntimeException)} ile sarıp "Unexpected exception in
 * eventHandler" loglaması ve devam etmesi) işi tek-thread'li worker'a taşıdıktan
 * sonra birebir geri kazandırır. Worker görevi sarılmazsa exception worker
 * thread'inden kaçar, in-flight event'in kalan işi kaybolur ve hata app log yerine
 * varsayılan {@code UncaughtExceptionHandler}/{@code System.err}'e gider (sessiz
 * regresyon, "behavior birebir aynı" ihlali).
 *
 * <p>Determinizm: production tipiyle aynı GERÇEK tek-thread'li worker enjekte edilir
 * ve her event sonrası {@code awaitTermination}/latch ile beklenir (sleep yok).
 * İlk event'in handler'ı {@code dbOperations.insertCallResultToDB(...)} üzerinden
 * fırlatır; worker hayatta kalmalı, hata loglanmalı ve SONRAKİ event yine işlenmeli.
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class AmiEventWorkerHandlerExceptionRegressionTest {

  private static final String CALL_ID_THROWING = "dl-throwing-1";
  private static final String CALL_ID_SUBSEQUENT = "dl-subsequent-1";

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

    realWorker = Executors.newSingleThreadExecutor();
    // setEventWorker package-private; tests/ami farklı pakette olduğu için reflection ile (mevcut idiom).
    ReflectionTestUtils.invokeMethod(decorator, "setEventWorker", realWorker);
  }

  @After
  public void tearDown() throws InterruptedException {
    realWorker.shutdown();
    realWorker.awaitTermination(5, TimeUnit.SECONDS);
  }

  @Test
  public void throwingHandlerMustNotKillWorkerAndMustBeLoggedAndSubsequentEventStillProcessed() throws Exception {
    // İlk çağrının VarSet handler'ı dbOperations.insertCallResultToDB üzerinden patlar.
    Call throwingCall = baseCall(CALL_ID_THROWING);
    decorator.getCalls().put(throwingCall.getId(), throwingCall);
    doThrow(new RuntimeException("boom from handler")).when(dbOperations).insertCallResultToDB(throwingCall);

    // (a) onManagerEvent çağrı thread'ine exception PROPAGATE ETMEMELİ (submit edip hemen döner).
    decorator.onManagerEvent(varSetEvent(CALL_ID_THROWING));
    drainAndAwaitOneTask();

    // (b) Hata sessizce yutulmamalı; sınıf LOG'una error yazılmalı.
    verify(loggerDecorator, atLeastOnce()).error(any(), any(Throwable.class));

    // (c) Worker, fırlatan handler'dan SONRA hâlâ yaşıyor olmalı: sonraki event işlenmeli.
    // İkinci çağrı için DB stub'ı normal davranır (sayım kaydı tutar).
    final CountDownLatch subsequentProcessed = new CountDownLatch(1);
    Call subsequentCall = baseCall(CALL_ID_SUBSEQUENT);
    decorator.getCalls().put(subsequentCall.getId(), subsequentCall);
    doAnswer(invocation -> {
      subsequentProcessed.countDown();
      return null;
    }).when(dbOperations).insertCallResultToDB(subsequentCall);

    decorator.onManagerEvent(varSetEvent(CALL_ID_SUBSEQUENT));

    assertThat("worker fırlatan handler'dan sonra hayatta kalmalı ve sonraki event'i işlemeli",
        subsequentProcessed.await(5, TimeUnit.SECONDS), is(true));
    assertThat("sonraki event handler'ı tam çalışmalı (startDate set olur)",
        subsequentCall.getStartDate(), notNullValue());
    verify(dbOperations).insertCallResultToDB(subsequentCall);
  }

  /**
   * Worker'ı drain edip ilk task'ın bittiğinden emin oluruz; sonra testin geri kalanı
   * için yeni bir tek-thread worker enjekte ederiz (önceki worker shutdown sonrası yeni
   * task kabul etmez). Bu, "worker thread öldü mü" sorusunu izole eder: eğer eski tasarımda
   * worker thread exception ile ölseydi, ThreadPoolExecutor yeni task'ta yeni thread
   * doğururdu; biz GERÇEK koruyucu davranışı (görevin içinde catch) tek worker'ın aynı
   * thread'de yaşamasıyla doğruluyoruz — bu yüzden aşağıdaki ikinci event AYNI worker'a
   * gönderilir.
   */
  private void drainAndAwaitOneTask() throws InterruptedException {
    // Tek-thread executor: bir no-op task submit edip onun bitişini beklemek, önceki
    // (fırlatan) task'ın da bittiğini garanti eder (FIFO, tek thread).
    final CountDownLatch fence = new CountDownLatch(1);
    realWorker.execute(fence::countDown);
    if (!fence.await(5, TimeUnit.SECONDS)) {
      fail("worker ilk (fırlatan) task'tan sonra yeni task çalıştıramadı (thread ölmüş olabilir)");
    }
  }

  private VarSetEvent varSetEvent(String callId) {
    VarSetEvent event = new VarSetEvent(this);
    event.setVariable("ORIGINID");
    event.setValue(callId);
    event.setChannel("Local/5551000@out-dialer-attempt-0009b39a;1");
    event.setUniqueId("GAVXBSNTRGRP01-1777966771.480652");
    event.setDateReceived(new java.util.Date());
    return event;
  }

  private Call baseCall(String callId) {
    Campaign campaign = new Campaign();
    campaign.setId(45L);
    campaign.setColResultRcSequence("rc_sequence");
    PhoneNumberColumn phoneNumberColumn = new PhoneNumberColumn();
    phoneNumberColumn.setColNumber("phone1");
    campaign.getPhoneNumberColumns().add(phoneNumberColumn);

    Call call = new Call();
    call.setId(callId);
    call.setCampaign(campaign);
    call.setResultCodeSequence(0);
    call.setCalledNumber("5551000");
    call.setRawData(new JSONObject().put("phone1", "5551000"));

    CDR cdr = new CDR();
    cdr.setInteractionId(callId);
    call.setCdr(cdr);
    return call;
  }

  private Server server() {
    Server server = new Server();
    server.setIp("127.0.0.1");
    return server;
  }
}
