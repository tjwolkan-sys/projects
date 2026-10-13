package tests.ami;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import com.globalbilgi.gbqueueselectionservice.grpc.RouteDetailEntry;
import com.google.common.cache.Cache;
import com.powerdialer.engine.CallCleaner;
import com.powerdialer.engine.CallUpToDateKeeper;
import com.powerdialer.engine.StopConditionChecker;
import com.powerdialer.engine.asterisk.ami.ManagerConnectionComposition;
import com.powerdialer.engine.asterisk.ami.ManagerConnectionDecorator;
import com.powerdialer.engine.asterisk.events.DialerHangupEvent;
import com.powerdialer.engine.db.CampaignDbOperations;
import com.powerdialer.engine.dialer.CallOriginator;
import com.powerdialer.engine.dialer.CampaignRealTimeRingingManager;
import com.powerdialer.engine.dialer.DialerFacade;
import com.powerdialer.engine.dialer.routing.RouteDetailsCursor;
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

/**
 * FIX: {@code OriginateResponse(Reason=0)} kurulmuş çağrının gerçek sonucunu eziyor.
 *
 * <p><b>Üretim kanıtı (02.09.2026, gbWebphone_NonTurkcell kampanya 55):</b> RC 79 yazılan 372
 * çağrının tamamında {@code Originate successfully queued} (Response=Success) alınmış, kanal
 * kurulmuş ({@code channel} + {@code startDate} dolu, failure'dan 7-40 sn ÖNCE set edilmiş),
 * çağrı çalmış ve operatörden gerçek cause dönmüştür. Terminal SBC bacağı cause dağılımı:
 * 19 (165), 16 (130), 17 (72), 18 (2), 21 (2), 1 (1). Buna karşın {@code pd_cdr.telephony_rc}
 * = 79 (code 907, "Originate action error") yazılmıştır.</p>
 *
 * <p><b>Neden:</b> Asterisk {@code OriginateResponse} olayının {@code Reason} alanı, çalma
 * sonrası cevapsız/meşgul/iptal durumlarında da {@code 0} gelir; canlıda gözlenen TEK değer
 * budur (02.09 turkcell index: reason '0' 202.575 kez, reason 1/3/4/5/8 sıfır kez). Yani
 * {@code ORIGINATE_RESPONSE_SUCCESSFUL_REASONS = {1,3,4,5,8}} whitelist'i pratikte ölü koddur
 * ve her {@code OriginateResponse} failure'ı origination hatası sayılır.</p>
 *
 * <p><b>Zamanlama:</b> Gerçek sonucu taşıyan hangup, Asterisk içinde geç yayılır. Ölçüm
 * (SBC terminal bacak -> Local {@code ;1} bacak): RC 79 alan çağrılarda p50 0,359s / p90 1,935s
 * / max 4,632s; doğru RC alan kontrol grubunda p50 0,035s / p90 0,081s. Çok bacaklı
 * (FCT -> 503 -> sabit numara) çağrılarda gecikme 10 kat artar ve {@code reason 0} bu boşlukta
 * işlenip 907 yazar.</p>
 *
 * <p>Testler dört kolu kilitler: (1) kanal kurulmuşsa 907 yazılmaz ve çağrı cache'te kalır,
 * (2) kanal hiç kurulmamışsa 907 yazılmaya DEVAM eder (FIX-GLB90060295 line-too-long
 * regresyon koruması), (3) 907 hangup tarafından düzeltilebilir, (4) hangup hiç gelmezse
 * grace timer eski davranışa döner ve çağrı asılı kalmaz.</p>
 */
@RunWith(MockitoJUnitRunner.class)
public class OriginateResponseFailureEstablishedCallRegressionTest {

  private static final String CALL_ID = "dl-origresp-reason0-1";
  private static final String CHANNEL = "Local/05551000@out-dialer-attempt-tahsilat-amd-0001d360;1";

  /** Ölçülen en kötü hangup yayılma gecikmesi 4,632s; grace timer bunun üstünde olmalı. */
  private static final long OBSERVED_WORST_HANGUP_PROPAGATION_MS = 4632L;

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

  /**
   * Test 1: kanal kurulmuş çağrıda {@code reason 0} failure'ı 907 yazmamalı ve çağrı
   * {@code calls} cache'inden DÜŞMEMELİ. Cache'te kalması kritiktir: geciken hangup
   * {@code getCallForHangup} ile çağrıyı bulabilsin ve gerçek sonucu yazabilsin.
   */
  @Test
  public void shouldNotWriteOriginateActionErrorWhenChannelIsAlreadyEstablished() {
    Call call = establishedRingingCall();
    putIntoCallsCache(call);

    ReflectionTestUtils.invokeMethod(decorator, "handleOriginationFail", CALL_ID, new Date(20000L));

    assertThat("kanal kurulmuşken RC 907 yazılmamalı; gerçek sonuç hangup'tan gelecek",
        call.getTelephonyResultCode(), is(nullValue()));
    assertThat("çağrı cache'te kalmalı, aksi halde geciken hangup onu bulamaz",
        callsCache().getIfPresent(CALL_ID), is(notNullValue()));
    verify(dbOperations, never()).insertCallResultToDB(call);
  }

  /**
   * Test 2 (FIX-GLB90060295 regresyon koruması): kanal HİÇ kurulmamışsa bu gerçek bir
   * originate reddidir (ör. Asterisk 'Failed to parse message: line too long' -> Response=Error;
   * kanal açılmaz, VarSetEvent gelmez) ve 907 yazılmaya devam etmelidir. Guard fazla geniş
   * yazılırsa Ağustos 2026'da çözülen arıza sessizce geri döner.
   */
  @Test
  public void shouldStillWriteOriginateActionErrorWhenChannelWasNeverEstablished() {
    Call call = neverOriginatedCall();
    putIntoCallsCache(call);
    when(dialerFacade.getCampaignContexts()).thenReturn(Collections.emptyMap());

    ReflectionTestUtils.invokeMethod(decorator, "handleOriginationFail", CALL_ID, new Date(20000L));

    assertThat("kanal kurulmamışsa gerçek originate hatasıdır, RC 907 yazılmalı",
        call.getTelephonyResultCode(), is(notNullValue()));
    assertThat(call.getTelephonyResultCode().getCode(), is(Constants.TELEPHONY_RC_ORIGINATE_ACTION_ERROR));
  }

  /**
   * Test 3: {@code shouldFinalizeOnHangup} RC 907 taşıyan çağrıda {@code true} dönmeli.
   * Mevcut kodda {@code hasErrorCode} yalnız {@code ASTERISK_ERROR_CODES}'a bakıyor; o küme
   * ITU cause'larını (0..127) tutar ve 907 içinde YOKTUR, dolayısıyla hangup RC 79'u
   * düzeltemez. 905/906/908 de aynı durumdadır.
   */
  @Test
  public void shouldAllowHangupToFinalizeCallThatCarriesDialerInternalErrorCode() {
    Call call = establishedRingingCall();
    TelephonyResultCode originateActionError =
        telephonyResultCode(79L, Constants.TELEPHONY_RC_ORIGINATE_ACTION_ERROR);
    call.setTelephonyResultCode(originateActionError);
    // KRİTİK: TRC'yi CDR'a da yazıyoruz. Aksi halde shouldFinalizeOnHangup'ın İKİNCİ koşulu
    // (FIX-320173 skip-guard: cdr.getTelephonyRC() == null) true döner ve test, hasErrorCode
    // düzeltilmese bile yeşil kalır — yani asıl invariant'ı ölçmez.
    call.getCdr().setTelephonyRC(originateActionError);

    Boolean shouldFinalize = ReflectionTestUtils.invokeMethod(decorator, "shouldFinalizeOnHangup", call);

    assertThat("RC 907 dialer iç hata kodudur; CDR'a persist edilmiş olsa bile hangup "
            + "gerçek cause ile finalize edebilmeli (hasErrorCode 907'yi tanımalı)",
        shouldFinalize, is(Boolean.TRUE));
  }

  /**
   * Test 4: uçtan uca. 907 yazılmış çağrıya cause 17 (User busy, servis sağlayıcıdan gelen
   * SIP 486 Busy Here) hangup'ı geldiğinde nihai sonuç RC 11 olmalıdır.
   */
  @Test
  public void shouldOverwriteOriginateActionErrorWithRealHangupCause() {
    Call call = establishedRingingCall();
    TelephonyResultCode originateActionError =
        telephonyResultCode(79L, Constants.TELEPHONY_RC_ORIGINATE_ACTION_ERROR);
    call.setTelephonyResultCode(originateActionError);
    // 907 CDR'a da persist edilmiş olmalı: üretimde processCallResult bunu yazar. Aksi halde
    // shouldFinalizeOnHangup'ın FIX-320173 skip-guard'ı testi hasErrorCode'dan bağımsız
    // yeşil tutar ve uçtan uca invariant ölçülmemiş olur.
    call.getCdr().setTelephonyRC(originateActionError);
    call.setCallOriginator(mock(CallOriginator.class));
    putIntoCallsCache(call);
    when(dialerFacade.getCampaignContexts()).thenReturn(Collections.emptyMap());

    ReflectionTestUtils.invokeMethod(decorator, "handleDialerHangup", hangupEvent("17"));

    assertThat("gerçek cause 17 (486 Busy) nihai sonuç olmalı, 907 değil",
        call.getTelephonyResultCode().getCode(), is(17));
    assertThat(call.getTelephonyResultCode().getId(), is(11L));
  }

  /**
   * Test 5: hangup HİÇ gelmezse çağrı asılı kalmamalı. Grace timer süresi sonunda 907 ile
   * finalize edilir (eski davranış). Mevcut emniyet ağı {@code CALL_EXPIRE_TIMEOUT} = 1 saattir
   * ve pratikte tetiklenmez (02.09 nonturkcell: RC 901 sıfır kayıt), bu yüzden çıplak
   * {@code return} slot ve sayaç sızdırırdı.
   */
  @Test
  public void shouldFinalizeWithOriginateActionErrorWhenHangupNeverArrives() {
    Call call = establishedRingingCall();
    call.setCallOriginator(mock(CallOriginator.class));
    putIntoCallsCache(call);
    when(dialerFacade.getCampaignContexts()).thenReturn(Collections.emptyMap());

    ReflectionTestUtils.invokeMethod(decorator, "handleOriginationFail", CALL_ID, new Date(20000L));
    assertThat("grace timer dolmadan sonuç yazılmamalı", call.getTelephonyResultCode(), is(nullValue()));

    // Grace timer'ın gövdesi: süre sonunda çağrı hâlâ finalize edilmemişse 907 yazılır.
    ReflectionTestUtils.invokeMethod(decorator, "finalizeOriginationFailFallback", call, new Date(30000L));

    assertThat("hangup gelmediyse eski davranışa dönülmeli",
        call.getTelephonyResultCode(), is(notNullValue()));
    assertThat(call.getTelephonyResultCode().getCode(), is(Constants.TELEPHONY_RC_ORIGINATE_ACTION_ERROR));
    verify(dbOperations).insertCallResultToDB(call);
  }

  /**
   * Savunma kapısı: dialplan başarılı çağrılarda cause 16 gönderir, dolayısıyla
   * {@code answerDate == null} iken cause 16 beklenen bir durum değildir. Böyle bir vakada
   * mevcut RC 907 KORUNUR; RC 10 ("Normal call clearing") yazmak hiç açılmamış, ses kaydı
   * olmayan çağrıyı başarılı gösterirdi ve RC 10 retry/attempt/Gamma zincirlerinde
   * "cevaplandı" sinyali olarak okunurdu.
   */
  @Test
  public void shouldKeepOriginateActionErrorWhenNormalClearingArrivesForUnansweredCall() {
    Call call = establishedRingingCall();
    call.setAnswerDate(null);
    TelephonyResultCode originateActionError =
        telephonyResultCode(79L, Constants.TELEPHONY_RC_ORIGINATE_ACTION_ERROR);
    call.setTelephonyResultCode(originateActionError);
    call.getCdr().setTelephonyRC(originateActionError);
    call.setCallOriginator(mock(CallOriginator.class));
    putIntoCallsCache(call);
    when(dialerFacade.getCampaignContexts()).thenReturn(Collections.emptyMap());

    ReflectionTestUtils.invokeMethod(decorator, "handleDialerHangup", hangupEvent("16"));

    assertThat("cevaplanmamış çağrıda cause 16 gelirse RC 907 korunmalı, RC 10 yazılmamalı",
        call.getTelephonyResultCode().getCode(), is(Constants.TELEPHONY_RC_ORIGINATE_ACTION_ERROR));
    // Çağrı yine finalize edilmeli: erken return slot ve sayaç sızdırırdı.
    verify(dbOperations).insertCallResultToDB(call);
  }

  /**
   * Kapsam kontrolü: savunma kapısı YALNIZ mevcut RC 907 iken devreye girmeli. Hiç sonucu
   * olmayan normal bir çağrıda cause 16 geldiğinde hangup kendi sonucunu (RC 10) yazmaya
   * devam etmelidir; aksi halde meşru "bağlandı" sonuçları bastırılırdı.
   */
  @Test
  public void shouldStillApplyNormalClearingWhenCallHasNoPreviousErrorResult() {
    Call call = establishedRingingCall();
    call.setAnswerDate(null);
    call.setTelephonyResultCode(null);
    call.setCallOriginator(mock(CallOriginator.class));
    putIntoCallsCache(call);
    when(dialerFacade.getCampaignContexts()).thenReturn(Collections.emptyMap());

    ReflectionTestUtils.invokeMethod(decorator, "handleDialerHangup", hangupEvent("16"));

    assertThat("mevcut sonucu olmayan çağrıda hangup kendi sonucunu yazmalı",
        call.getTelephonyResultCode(), is(notNullValue()));
    assertThat(call.getTelephonyResultCode().getId(), is(10L));
  }

  /**
   * FCT list-retry çarpışması: {@code tryFctRetryWithNextCallerId} AYNI {@code Call} nesnesini
   * yeniden originate eder ve bunu {@code originateCall}'ı DOĞRUDAN çağırarak yapar (dolayısıyla
   * {@code CallOriginator} üzerinden geçen originationDate güncellemesi olmaz). Bekleyen eski
   * fallback iptal edilmezse YENİ denemeye RC 907 yazıp canlı çağrıyı öldürürdü — üstelik tüm
   * durum guard'ları geçerdi: finalize latch'i kapanmamış, awaiting değil, çağrı cache'te MEVCUT.
   * Bu yüzden {@code originateCall} bekleyen fallback'i iptal etmek ZORUNDADIR.
   */
  @Test
  public void shouldCancelPendingFallbackWhenCallIsReOriginated() {
    Call call = establishedRingingCall();
    RecordingFuture pending = new RecordingFuture();
    call.setOriginationFailFallbackFuture(pending);

    // FCT list-retry yolu: originateCall doğrudan yeniden çağrılır.
    call.setTelephonyResultCode(null);
    try {
      decorator.originateCall(call);
    } catch (RuntimeException ignored) {
      // AMI bağlantısı test ortamında kurulu değil; bizi ilgilendiren tek şey iptal çağrısı.
    }

    assertThat("yeniden originate edilirken bekleyen fallback iptal edilmeli",
        pending.cancelled, is(true));
  }

  /** İptal çağrısını gözlemlemek için minimal Future. */
  private static final class RecordingFuture implements java.util.concurrent.Future<Object> {
    private boolean cancelled;

    @Override public boolean cancel(boolean mayInterruptIfRunning) {
      cancelled = true;
      return true;
    }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public boolean isDone() { return cancelled; }
    @Override public Object get() { return null; }
    @Override public Object get(long timeout, java.util.concurrent.TimeUnit unit) { return null; }
  }

  /**
   * Preview çift-serbest bırakma koruması: {@code clearCall} preview kampanyada {@code calls}
   * cache'ini BİLEREK invalidate etmez (userResultCode yoksa), dolayısıyla cache kontrolü bu
   * vakayı yakalamaz. {@code handlePreviewTimeout} gibi bir yol çağrıyı {@code processCallResult}
   * çağırmadan söktüyse finalize latch'i de kapanmamıştır. Bu durumda fallback devam ederse
   * {@code returnUserToACDEngine} ve kampanya sayaçları İKİNCİ kez uygulanır.
   */
  @Test
  public void shouldSkipFallbackWhenTelephonySlotWasAlreadyReleased() {
    Call call = establishedRingingCall();
    call.setCallOriginator(mock(CallOriginator.class));
    putIntoCallsCache(call);
    // Çağrı başka bir yoldan söküldü: slot bırakıldı ama processCallResult çalışmadı.
    call.markTelephonySlotReleased();

    ReflectionTestUtils.invokeMethod(decorator, "finalizeOriginationFailFallback", call, new Date(30000L));

    assertThat("slot zaten bırakılmışsa fallback sonuç yazmamalı",
        call.getTelephonyResultCode(), is(nullValue()));
    verify(dbOperations, never()).insertCallResultToDB(call);
  }

  /**
   * En kritik senaryo: çağrı grace period İÇİNDE cevaplanır (müşteri açar, agent bağlanır) ve
   * gerçek hangup henüz gelmemiştir. Fallback bu durumda RC 907 yazarsa canlı ve konuşulan
   * çağrı finalize edilir; dahası agent'ın result code'u kalıcı olarak kaybolur, çünkü 907'nin
   * CDR id'si (79) ne {@code WEBPHONE_RESULT_CODE_RECOVERABLE_TRC_IDS} ({75,78,84}) ne de
   * durable fallback'in başarı kümesinde ({10,24}) yer alır — disposition sessizce düşer.
   */
  @Test
  public void shouldSkipFallbackWhenCallIsConnectedToAgent() {
    Call call = establishedRingingCall();
    call.setCallOriginator(mock(CallOriginator.class));
    putIntoCallsCache(call);
    // Grace period içinde müşteri açtı; hangup henüz gelmedi.
    call.setAnsweredByUser(true);   // agent bacagi cevapladi

    ReflectionTestUtils.invokeMethod(decorator, "finalizeOriginationFailFallback", call, new Date(30000L));

    assertThat("agent bagli cagriya fallback RC 907 yazmamali",
        call.getTelephonyResultCode(), is(nullValue()));
    verify(dbOperations, never()).insertCallResultToDB(call);
  }

  /**
   * BOSLUK KAPATMA: zamanlayici kablolamasi. Diger testler finalizeOriginationFailFallback'i
   * dogrudan cagiriyor; bu, handleOriginationFail -> scheduleService.schedule ->
   * runOriginationFailFallback -> eventWorker zincirinin TAMAMINI test disi birakiyordu.
   * Dogrulandi: zamanlayici govdesi tamamen silindiginde diger 11 test yesil kaliyordu, yani
   * ozellik olu olsa fark edilmezdi. Bu test (a) fallback'in ARM edildigini, (b) gecikmenin
   * sabitten okundugunu, (c) future'in Call'a yazildigini, (d) atesleyince 907 yazildigini kilitler.
   */
  @Test
  public void shouldArmFallbackWithConfiguredDelayWhenChannelIsEstablished() {
    CapturingScheduler scheduler = new CapturingScheduler();
    ReflectionTestUtils.setField(decorator, "scheduleService", scheduler);
    Call call = establishedRingingCall();
    call.setCallOriginator(mock(CallOriginator.class));
    putIntoCallsCache(call);

    ReflectionTestUtils.invokeMethod(decorator, "handleOriginationFail", CALL_ID, new Date(20000L));

    assertThat("fallback zamanlanmali", scheduler.scheduledCommand, is(notNullValue()));
    assertThat("gecikme sabitten okunmali", scheduler.scheduledDelay, is(10000L));
    assertThat(scheduler.scheduledUnit, is(TimeUnit.MILLISECONDS));
    assertThat("future iptal edilebilmek icin Call'a yazilmali",
        ReflectionTestUtils.getField(call, "originationFailFallbackFuture"), is(notNullValue()));

    when(dialerFacade.getCampaignContexts()).thenReturn(Collections.emptyMap());
    scheduler.fire();

    assertThat(call.getTelephonyResultCode(), is(notNullValue()));
    assertThat(call.getTelephonyResultCode().getCode(), is(Constants.TELEPHONY_RC_ORIGINATE_ACTION_ERROR));
  }

  /**
   * BOSLUK KAPATMA: eventWorker kapatilmissa is SESSIZCE dusmemeli.
   * blockingPutRejectedHandler kapali executor'da gorevi sessizce atiyor; bu tam olarak
   * reconnect/logoff drain aninda olur ve origination hatalari da orada yogunlasir. Inline
   * kacis yolu olmadan fallback hic calismaz ve cagri 1 saatlik expiry'ye kalirdi.
   */
  @Test
  public void shouldRunFallbackInlineWhenEventWorkerIsShutdown() {
    // Uretimdeki blockingPutRejectedHandler kapali executor'da gorevi SESSIZCE atar (exception
    // atmaz). O davranisi birebir taklit ediyoruz: boylece test yalnizca isShutdown() kolunu
    // olcer, RejectedExecutionException catch'ine dusup yanlis sebeple gecemez.
    ReflectionTestUtils.setField(decorator, "eventWorker", new SilentlyDroppingExecutor());
    Call call = establishedRingingCall();
    call.setCallOriginator(mock(CallOriginator.class));
    putIntoCallsCache(call);
    when(dialerFacade.getCampaignContexts()).thenReturn(Collections.emptyMap());

    ReflectionTestUtils.invokeMethod(decorator, "runOriginationFailFallback", call, new Date(30000L));

    assertThat("worker kapaliysa is inline kosmali, sessizce dusmemeli",
        call.getTelephonyResultCode(), is(notNullValue()));
    assertThat(call.getTelephonyResultCode().getCode(), is(Constants.TELEPHONY_RC_ORIGINATE_ACTION_ERROR));
  }

  /**
   * BOSLUK KAPATMA: kapsam kilidi. HANGUP_OVERWRITABLE_DIALER_ERROR_CODES yalniz 907 icermelidir.
   * 905/908 originate'ten ONCE cancelCall ile set edilir (kanal yok, hangup gelmez); 906 hangup'in
   * KENDISI tarafindan isAbandoned() daliyla uretilir, onu overwritable saymak abandoned sonucunu
   * kendi uzerine yazdirirdi. Bu kume ayni zamanda shouldProcessCallResultOnHangup'i besliyor,
   * yani genisletmek agent-bagli cagrilarin yonlendirmesini de degistirir. Gerekce yorumda
   * yaziliydi ama yurutulebilir kilidi yoktu.
   */
  @Test
  public void hangupOverwritableCodesShouldContainOnlyOriginateActionError() {
    assertThat(Constants.HANGUP_OVERWRITABLE_DIALER_ERROR_CODES,
        is(Collections.singleton(Constants.TELEPHONY_RC_ORIGINATE_ACTION_ERROR)));
    assertThat("905 originate oncesi set edilir, hangup gelmez",
        Constants.HANGUP_OVERWRITABLE_DIALER_ERROR_CODES.contains(Constants.TELEPHONY_RC_BLOCKED_BY_LSC), is(false));
    assertThat("906 hangup'in kendisi uretir",
        Constants.HANGUP_OVERWRITABLE_DIALER_ERROR_CODES.contains(Constants.TELEPHONY_RC_ABANDONED_TIMEOUT), is(false));
    assertThat("908 originate oncesi set edilir, hangup gelmez",
        Constants.HANGUP_OVERWRITABLE_DIALER_ERROR_CODES.contains(Constants.TELEPHONY_RC_LSC_TIMEOUT), is(false));
  }

  /**
   * BOSLUK KAPATMA: isChannelEstablished bir OR'dur; fixture iki alani da doldurdugu icin hicbir
   * test iki kolu ayirt etmiyordu ve OR -> AND degisimi sessizce gecerdi. Kanal kurulmus ama
   * startDate henuz yazilmamis (veya tersi) yaris penceresi gercektir.
   */
  @Test
  public void shouldTreatChannelOnlyCallAsEstablished() {
    Call call = establishedRingingCall();
    call.setStartDate(null);
    putIntoCallsCache(call);

    ReflectionTestUtils.invokeMethod(decorator, "handleOriginationFail", CALL_ID, new Date(20000L));

    assertThat("yalniz channel dolu olsa da kanal kurulmus sayilmali",
        call.getTelephonyResultCode(), is(nullValue()));
  }

  @Test
  public void shouldTreatStartDateOnlyCallAsEstablished() {
    Call call = establishedRingingCall();
    call.setChannel(null);
    putIntoCallsCache(call);

    ReflectionTestUtils.invokeMethod(decorator, "handleOriginationFail", CALL_ID, new Date(20000L));

    assertThat("yalniz startDate dolu olsa da kanal kurulmus sayilmali",
        call.getTelephonyResultCode(), is(nullValue()));
  }

  /** Bilinmeyen/temizlenmis cagri icin hangup geldiginde hicbir yan etki olusmamali. */
  @Test
  public void shouldNotProduceSideEffectsWhenHangupArrivesForUnknownCall() {
    ReflectionTestUtils.invokeMethod(decorator, "handleDialerHangup", hangupEvent("17"));

    verify(dbOperations, never()).insertCallResultToDB(any(Call.class));
  }

  /** Kapali executor'i taklit eder: isShutdown() true, execute() gorevi sessizce atar. */
  private static final class SilentlyDroppingExecutor extends java.util.concurrent.AbstractExecutorService {
    @Override public void shutdown() { }
    @Override public java.util.List<Runnable> shutdownNow() { return Collections.emptyList(); }
    @Override public boolean isShutdown() { return true; }
    @Override public boolean isTerminated() { return true; }
    @Override public boolean awaitTermination(long timeout, TimeUnit unit) { return true; }
    @Override public void execute(Runnable command) { /* sessizce dusur */ }
  }

  /**
   * Zamanlayiciyi yakalayan minimal ScheduledExecutorService: gercek gecikmeyi beklemek yerine
   * komutu ve parametrelerini saklar, fire() ile elle calistirir.
   */
  private static final class CapturingScheduler extends java.util.concurrent.AbstractExecutorService
      implements java.util.concurrent.ScheduledExecutorService {
    private Runnable scheduledCommand;
    private long scheduledDelay;
    private TimeUnit scheduledUnit;

    void fire() {
      if (scheduledCommand != null) {
        scheduledCommand.run();
      }
    }

    @Override
    public java.util.concurrent.ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
      this.scheduledCommand = command;
      this.scheduledDelay = delay;
      this.scheduledUnit = unit;
      return new StubScheduledFuture();
    }

    @Override
    public <V> java.util.concurrent.ScheduledFuture<V> schedule(java.util.concurrent.Callable<V> callable, long delay,
        TimeUnit unit) {
      throw new UnsupportedOperationException();
    }

    @Override
    public java.util.concurrent.ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period,
        TimeUnit unit) {
      return new StubScheduledFuture();
    }

    @Override
    public java.util.concurrent.ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay,
        long delay, TimeUnit unit) {
      return new StubScheduledFuture();
    }

    @Override public void shutdown() { }
    @Override public java.util.List<Runnable> shutdownNow() { return Collections.emptyList(); }
    @Override public boolean isShutdown() { return false; }
    @Override public boolean isTerminated() { return false; }
    @Override public boolean awaitTermination(long timeout, TimeUnit unit) { return true; }
    @Override public void execute(Runnable command) { command.run(); }
  }

  private static final class StubScheduledFuture implements java.util.concurrent.ScheduledFuture<Object> {
    @Override public long getDelay(TimeUnit unit) { return 0L; }
    @Override public int compareTo(java.util.concurrent.Delayed o) { return 0; }
    @Override public boolean cancel(boolean mayInterruptIfRunning) { return true; }
    @Override public boolean isCancelled() { return false; }
    @Override public boolean isDone() { return false; }
    @Override public Object get() { return null; }
    @Override public Object get(long timeout, TimeUnit unit) { return null; }
  }

  /** Grace timer süresi ölçülen en kötü yayılma gecikmesini karşılamalı. */
  @Test
  public void graceTimerShouldCoverObservedWorstCaseHangupPropagationDelay() {
    Long graceMs = (Long) ReflectionTestUtils.getField(ManagerConnectionDecorator.class,
        "ORIGINATION_FAIL_GRACE_PERIOD_MS");

    assertThat(graceMs, is(notNullValue()));
    assertThat("grace timer ölçülen max gecikmeyi (4,632s) karşılamalı",
        graceMs > OBSERVED_WORST_HANGUP_PROPAGATION_MS, is(true));
    assertThat("grace timer 1 saatlik CALL_EXPIRE_TIMEOUT'tan çok kısa olmalı",
        graceMs < Constants.CALL_EXPIRE_TIMEOUT, is(true));
  }

  @SuppressWarnings("unchecked")
  private Cache<String, Call> callsCache() {
    return (Cache<String, Call>) ReflectionTestUtils.getField(decorator, "calls");
  }

  private void putIntoCallsCache(Call call) {
    callsCache().put(call.getId(), call);
  }

  /**
   * Test 21 (code-review CONFIRMED): FCT list-retry aynı Call nesnesini yeniden originate eder;
   * {@code startDate} sıfırlanıyordu ama {@code channel} sıfırlanmıyordu (repo genelinde
   * {@code setChannel(null)} hiç yoktu). {@code isChannelEstablished} bu iki alanın OR'u olduğu
   * için 2. denemenin GERÇEK originate reddi bayat channel yüzünden "kanal kurulmuş" sayılıp
   * gereksiz yere 10 saniyelik grace period'a giriyordu.
   */
  @Test
  public void shouldClearChannelOnFctRetrySoNextGenuineFailureIsNotTreatedAsEstablished() {
    Call call = establishedRingingCall();
    call.setRouteDetailsCursor(fctCursorWithNext());
    call.setCallOriginator(mock(CallOriginator.class));
    putIntoCallsCache(call);

    ReflectionTestUtils.invokeMethod(decorator, "tryFctRetryWithNextCallerId", call);

    assertThat("FCT retry sonrası channel sıfırlanmalı", call.getChannel(), is(nullValue()));
    assertThat("startDate zaten sıfırlanıyordu", call.getStartDate(), is(nullValue()));
    Boolean established = ReflectionTestUtils.invokeMethod(decorator, "isChannelEstablished", call);
    assertThat("retry sonrası kanal kurulmuş sayılmamalı; aksi halde 2. denemenin gerçek "
            + "originate reddi 10sn gecikirdi", established, is(Boolean.FALSE));
  }

  /**
   * Test 22 (code-review CONFIRMED): üretimdeki {@code blockingPutRejectedHandler} kapalı
   * executor'da görevi SESSİZCE düşürür ve {@code RejectedExecutionException} ATMAZ. Eski kodun
   * {@code isShutdown()} ön kontrolü TOCTOU olduğu için, kontrolden SONRA kapanan worker'da iş
   * sessizce kayboluyor ve çağrı 1 saatlik cache expiry'sine kalıyordu. Gerçek
   * {@code ThreadPoolExecutor} + gerçek handler ile kapalı worker senaryosu.
   */
  @Test
  public void shouldRunFallbackInlineWhenRealWorkerIsShutdownAndSilentlyDropsTask() throws Exception {
    ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
        new java.util.concurrent.LinkedBlockingQueue<Runnable>(10),
        invokeBlockingPutRejectedHandler());
    worker.shutdown();
    ReflectionTestUtils.setField(decorator, "eventWorker", worker);
    Call call = establishedRingingCall();
    call.setCallOriginator(mock(CallOriginator.class));
    putIntoCallsCache(call);
    when(dialerFacade.getCampaignContexts()).thenReturn(Collections.emptyMap());

    ReflectionTestUtils.invokeMethod(decorator, "runOriginationFailFallback", call, new Date(30000L));

    assertThat("kapalı worker görevi sessizce düşürür; fallback inline koşmalı",
        call.getTelephonyResultCode(), is(notNullValue()));
    assertThat(call.getTelephonyResultCode().getCode(), is(Constants.TELEPHONY_RC_ORIGINATE_ACTION_ERROR));
  }

  /**
   * Test 23 (code-review CONFIRMED): kuyruk DOLU ve worker AÇIK iken eski kod
   * {@code worker.execute} çağırıyordu; {@code blockingPutRejectedHandler} bu durumda
   * {@code getQueue().put()} ile submitter'ı BLOKLAR. Submitter tek thread'li
   * {@code scheduleService} olduğu için bu, tüm preview timeout'larını kilitlerdi. Yeni kod
   * bloklamayan {@code offer} kullanır ve kabul edilmezse inline koşar.
   */
  @Test
  public void shouldNotBlockSchedulerThreadWhenEventWorkerQueueIsFull() throws Exception {
    // Kapasite 1; worker'ın tek thread'ini meşgul edip kuyruğu da doldurarak offer'ı reddettiriyoruz.
    ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
        new java.util.concurrent.LinkedBlockingQueue<Runnable>(1),
        invokeBlockingPutRejectedHandler());
    final java.util.concurrent.CountDownLatch block = new java.util.concurrent.CountDownLatch(1);
    worker.execute(() -> {
      try {
        block.await(5, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    });
    worker.execute(() -> { /* kuyruğu doldurur */ });
    ReflectionTestUtils.setField(decorator, "eventWorker", worker);
    Call call = establishedRingingCall();
    call.setCallOriginator(mock(CallOriginator.class));
    putIntoCallsCache(call);
    when(dialerFacade.getCampaignContexts()).thenReturn(Collections.emptyMap());

    try {
      // Bloklanırsa bu çağrı 5 saniye asılı kalırdı; inline yol anında döner.
      long startedAt = System.currentTimeMillis();
      ReflectionTestUtils.invokeMethod(decorator, "runOriginationFailFallback", call, new Date(30000L));
      long elapsed = System.currentTimeMillis() - startedAt;

      assertThat("kuyruk doluyken submitter thread'i bloklanmamalı", elapsed < 2000L, is(Boolean.TRUE));
      assertThat("iş düşmemeli; inline koşmalı",
          call.getTelephonyResultCode(), is(notNullValue()));
      assertThat(call.getTelephonyResultCode().getCode(), is(Constants.TELEPHONY_RC_ORIGINATE_ACTION_ERROR));
    } finally {
      block.countDown();
      worker.shutdownNow();
    }
  }

  /**
   * Test 24 (kendi düzeltmemizin regresyonu): fallback HİÇ iş almamış bir worker'a verildiğinde de
   * gerçekten ÇALIŞMALIDIR.
   *
   * <p>{@code ThreadPoolExecutor} core thread'i yalnız {@code execute()} içinde (lazy) yaratır ve
   * üretimde {@code prestartCoreThread} çağrılmıyor. Bu yüzden görevi {@code getQueue().offer(...)}
   * ile doğrudan kuyruğa koymak, havuz henüz hiç iş almamışsa (poolSize=0) işi ÇALIŞTIRMADAN
   * kuyrukta bırakır: çağrı finalize edilmez ve 1 saatlik cache expiry'sine kalır. Diğer blocking
   * testi worker'ı önceden {@code execute} ile ısıttığı için bu kusuru göremez; burada havuz
   * bilerek SOĞUK bırakılmıştır.</p>
   */
  @Test
  public void shouldRunFallbackWhenEventWorkerHasNeverExecutedAnyTaskBefore() throws Exception {
    ThreadPoolExecutor coldWorker = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
        new java.util.concurrent.LinkedBlockingQueue<Runnable>(10),
        invokeBlockingPutRejectedHandler());
    assertThat("havuz soğuk olmalı: hiç thread yaratılmamış", coldWorker.getPoolSize(), is(0));
    ReflectionTestUtils.setField(decorator, "eventWorker", coldWorker);
    Call call = establishedRingingCall();
    call.setCallOriginator(mock(CallOriginator.class));
    putIntoCallsCache(call);
    when(dialerFacade.getCampaignContexts()).thenReturn(Collections.emptyMap());

    try {
      ReflectionTestUtils.invokeMethod(decorator, "runOriginationFailFallback", call, new Date(30000L));

      // Worker thread'inde koşabileceği için sonucu bekleyerek doğruluyoruz.
      long deadline = System.currentTimeMillis() + 3000L;
      while (call.getTelephonyResultCode() == null && System.currentTimeMillis() < deadline) {
        Thread.sleep(20L);
      }
      assertThat("soğuk havuzda da fallback çalışmalı; kuyrukta asılı kalmamalı",
          call.getTelephonyResultCode(), is(notNullValue()));
      assertThat(call.getTelephonyResultCode().getCode(), is(Constants.TELEPHONY_RC_ORIGINATE_ACTION_ERROR));
    } finally {
      coldWorker.shutdownNow();
    }
  }

  /**
   * Test 25 (davranış dokümantasyonu): grace period DOLDUKTAN SONRA gelen hangup RC'yi ARTIK
   * GÜNCELLEMEZ.
   *
   * <p>Senaryo: 10. saniyede fallback RC 907 yazıp {@code processCallResult} ile finalize eder;
   * bu da {@code releaseTelephonySlotIfNeeded -> clearCall} zinciriyle non-preview çağrıyı
   * {@code calls} cache'inden düşürür. 20. saniyede gerçek hangup geldiğinde
   * {@code getCallForHangup} dört cache'in hiçbirinde çağrıyı bulamaz ve
   * {@code handleDialerHangup} "unknown or already cleared call" WARN'ı ile erken döner.</p>
   *
   * <p>Yani grace period bir <b>kesin sınırdır</b>: içinde gelen hangup gerçek sonucu yazar,
   * dışında kalan hangup 907'yi düzeltemez. Bu bilinçli bir tasarım tercihidir — finalize
   * edilmiş, CDR'ı persist edilmiş, slotu ve agent'ı serbest bırakılmış bir çağrıyı sonradan
   * yeniden yazmak retry/attempt/kampanya sayaçlarını tutarsız hale getirirdi. Bu test o sınırı
   * belgeler; ölçüm p90 1,9s / max 4,6s olduğundan pratikte 10sn dışına düşen vaka beklenmez.</p>
   */
  @Test
  public void shouldNotUpdateResultWhenRealHangupArrivesAfterGracePeriodExpired() {
    Call call = establishedRingingCall();
    call.setCallOriginator(mock(CallOriginator.class));
    putIntoCallsCache(call);
    when(dialerFacade.getCampaignContexts()).thenReturn(Collections.emptyMap());

    // t=0: originate-fail geldi, kanal kurulu -> 907 yazılmadı, grace timer kuruldu.
    ReflectionTestUtils.invokeMethod(decorator, "handleOriginationFail", CALL_ID, new Date(20000L));
    assertThat(call.getTelephonyResultCode(), is(nullValue()));

    // t=10s: hangup hâlâ gelmedi -> fallback 907 yazıp finalize etti.
    ReflectionTestUtils.invokeMethod(decorator, "finalizeOriginationFailFallback", call, new Date(30000L));
    assertThat(call.getTelephonyResultCode().getCode(), is(Constants.TELEPHONY_RC_ORIGINATE_ACTION_ERROR));
    assertThat("finalize sonrası non-preview çağrı calls cache'inden düşmeli",
        callsCache().getIfPresent(CALL_ID), is(nullValue()));

    // t=20s: gerçek hangup (cause 17 = User busy) NİHAYET geliyor.
    ReflectionTestUtils.invokeMethod(decorator, "handleDialerHangup", hangupEvent("17"));

    assertThat("grace period dışında gelen hangup RC'yi güncellemez; 907 kalır",
        call.getTelephonyResultCode().getCode(), is(Constants.TELEPHONY_RC_ORIGINATE_ACTION_ERROR));
    // Finalize bir kez oldu: geç hangup ikinci bir finalize/CDR yazımı tetiklemedi.
    verify(dbOperations).insertCallResultToDB(call);
  }

  /**
   * Test 26 (code-review BUG 1): aynı callId için İKİNCİ bir origination-fail geldiğinde birinci
   * timer İPTAL EDİLMELİDİR.
   *
   * <p>`Call` tek bir Future alanı tutar; iki ayrı fail girişi vardır
   * ({@code handleOriginateResponse} ve {@code runOriginationFailTask}) ve grace dalı çağrıyı
   * {@code calls} cache'inden düşürmediği için ikincisi de aynı çağrıya ulaşabilir. Eski timer
   * iptal edilmezse referansı üzerine yazılır ve ARTIK İPTAL EDİLEMEZ; FCT list-retry sonrası
   * ateşleyip canlı 2. denemeye RC 907 yazabilirdi.</p>
   */
  @Test
  public void shouldCancelPreviousFallbackWhenSecondOriginationFailArrivesForSameCall() {
    Call call = establishedRingingCall();
    putIntoCallsCache(call);
    RecordingFuture first = new RecordingFuture();
    call.setOriginationFailFallbackFuture(first);

    // İkinci fail girişi: grace dalına tekrar girer ve yeni timer kurar.
    ReflectionTestUtils.invokeMethod(decorator, "handleOriginationFail", CALL_ID, new Date(21000L));

    assertThat("ikinci fail birinci timer'i iptal etmeli; aksi halde o timer izlenemez hale gelir",
        first.cancelled, is(Boolean.TRUE));
  }

  /**
   * Test 27 (code-review BUG 2): {@code completeCall}'dan geçen sökme yolları fallback'i
   * durdurmalıdır.
   *
   * <p>{@code CampaignStopJob.clearCallAndSaveResultToDb} (forceful stop) TRC 902 yazıp CDR'i
   * persist eder ama {@code processCallResult} KULLANMAZ — ne finalize latch'i ne de telephony
   * slot bayrağı set edilir. {@code clearCall} PREVIEW kampanyada {@code calls} cache'ini
   * bilerek invalidate etmediği için son cache kapısı da açık kalır. Bu durumda fallback 902'yi
   * RC 907 ile ezip CDR'i ve kampanya sayaçlarını İKİNCİ kez işlerdi. Kapı
   * {@code getCompleteDate() != null} olup diğer iki finalize yoluyla hizalıdır.</p>
   */
  @Test
  public void shouldSkipFallbackWhenCallWasAlreadyCompletedByForcefulCampaignStop() {
    Call call = establishedRingingCall();
    call.getCampaign().setType(Constants.CAMPAIGN_TYPE.PREVIEW_STRAIGHT);
    call.setCallOriginator(mock(CallOriginator.class));
    putIntoCallsCache(call);
    // Forceful stop taklidi: TRC 902 + completeDate, latch'ler set EDİLMEZ, preview olduğu için
    // clearCall `calls`'i invalidate etmez -> çağrı cache'te kalır.
    TelephonyResultCode campaignStopped = telephonyResultCode(74L, 902);
    call.setTelephonyResultCode(campaignStopped);
    call.setCompleteDate(46264.6D);

    ReflectionTestUtils.invokeMethod(decorator, "finalizeOriginationFailFallback", call, new Date(30000L));

    assertThat("tamamlanmis cagriya RC 907 yazilmamali; kampanya stop sonucu korunmali",
        call.getTelephonyResultCode().getCode(), is(902));
    verify(dbOperations, never()).insertCallResultToDB(call);
  }

  /**
   * Test 28 (code-review BUG 3): submit bloklamayan yolla yapılmalı.
   *
   * <p>Üretim worker'ı {@code blockingPutRejectedHandler} kullanır; bu handler REDDETMEZ, kuyruk
   * doluysa submitter'ı BLOKLAR. Submitter tek thread'li {@code scheduleService} olduğundan
   * bloklanırsa tüm preview timeout'ları ve cache expiry job'lari durur. "Önce kapasiteyi
   * kontrol et sonra execute et" TOCTOU'dur; burada kuyruk DOLU ve worker AÇIK iken submit'in
   * bloklanmadığı ve işin düşmediği doğrulanır.</p>
   */
  @Test
  public void shouldNotBlockWhenWorkerQueueIsFullAndWorkerIsRunning() throws Exception {
    ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
        new java.util.concurrent.LinkedBlockingQueue<Runnable>(1),
        invokeBlockingPutRejectedHandler());
    final java.util.concurrent.CountDownLatch block = new java.util.concurrent.CountDownLatch(1);
    worker.execute(() -> {
      try {
        block.await(5, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    });
    worker.execute(() -> { /* kuyrugu doldurur */ });
    ReflectionTestUtils.setField(decorator, "eventWorker", worker);
    Call call = establishedRingingCall();
    call.setCallOriginator(mock(CallOriginator.class));
    putIntoCallsCache(call);
    when(dialerFacade.getCampaignContexts()).thenReturn(Collections.emptyMap());

    try {
      long startedAt = System.currentTimeMillis();
      ReflectionTestUtils.invokeMethod(decorator, "runOriginationFailFallback", call, new Date(30000L));
      long elapsed = System.currentTimeMillis() - startedAt;

      assertThat("kuyruk doluyken submitter thread'i bloklanmamali", elapsed < 2000L, is(Boolean.TRUE));
      assertThat("is dusmemeli; inline kosmali",
          call.getTelephonyResultCode(), is(notNullValue()));
      assertThat(call.getTelephonyResultCode().getCode(), is(Constants.TELEPHONY_RC_ORIGINATE_ACTION_ERROR));
    } finally {
      block.countDown();
      worker.shutdownNow();
    }
  }

  /** Static package-private factory'yi reflection ile çağırır (tests/ami farklı pakette). */
  private static java.util.concurrent.RejectedExecutionHandler invokeBlockingPutRejectedHandler() throws Exception {
    java.lang.reflect.Method method =
        ManagerConnectionDecorator.class.getDeclaredMethod("blockingPutRejectedHandler");
    method.setAccessible(true);
    return (java.util.concurrent.RejectedExecutionHandler) method.invoke(null);
  }

  /** FCT retry'nin ilerleyebilmesi için: cursor'da bir SONRAKİ entry bulunmalı (hasNext). */
  private RouteDetailsCursor fctCursorWithNext() {
    return new RouteDetailsCursor(java.util.Arrays.asList(
        // NOT: QSS sözleşmesi gereği `attempts` proto'da int32 -> string oldu (virgülle birleşik
        // çok değerli gelebiliyor, AT_ attribute'u yoksa 0..99 fallback'i döner). Bu test develop'ta
        // eski int32 imzasıyla yazılmıştı; merge sırasında string imzasına uyarlandı.
        RouteDetailEntry.newBuilder().setPhoneNumber("05551000").setType("FCT").setAttempts("0").build(),
        RouteDetailEntry.newBuilder().setPhoneNumber("05559999").setType("FCT").setAttempts("0").build()));
  }

  /**
   * Test 18 (code-review CONFIRMED-HIGH): 907 kontrolü {@code hasErrorCode} İÇİNE konursa
   * {@code shouldProcessCallResultOnHangup}'ın 3. koşulu ({@code hasErrorCode && !preview}) da
   * true olur; preview OLMAYAN, cevaplanmış, agent'ı atanmış ve user result code BEKLEYEN çağrı
   * awaiting-user-result kolundan çıkıp doğrudan finalize edilir — agent'ın disposition'ı sessizce
   * kaybolur. 907 kontrolü yalnız {@code shouldFinalizeOnHangup}'ta olmalı.
   */
  @Test
  public void shouldStillWaitForUserResultCodeWhenAnsweredCallCarriesOriginateActionError() {
    Call call = establishedRingingCall();
    call.setAnswerDate(46264.505820590275D);
    User user = new User();
    user.setId(7L);
    Group group = new Group();
    group.setResultCodes(Collections.singletonList(new ResultCode()));
    user.setGroup(group);
    call.setUser(user);
    call.setUserResultCode(null);
    TelephonyResultCode originateActionError =
        telephonyResultCode(79L, Constants.TELEPHONY_RC_ORIGINATE_ACTION_ERROR);
    call.setTelephonyResultCode(originateActionError);

    Boolean shouldProcess = ReflectionTestUtils.invokeMethod(decorator,
        "shouldProcessCallResultOnHangup", call, originateActionError);

    assertThat("RC 907 taşıyan cevaplanmış çağrı hâlâ agent'ın result code'unu beklemeli; "
            + "907'yi hasErrorCode'a eklemek bu kolu atlayıp disposition'ı kaybettirirdi",
        shouldProcess, is(Boolean.FALSE));
  }

  /**
   * Test 19 (code-review CONFIRMED-HIGH): fallback'in TRC yazımı ile finalize kontrolü atomik
   * olmalıdır. Hangup thread'i {@code processCallResult} içinde latch'i almış ama daha
   * {@code persistFinalCdr} TRC'yi okumamışken fallback araya girip RC 907 yazabiliyordu — yani
   * bu branch'in düzeltmeye çalıştığı "907 gerçek sonucu eziyor" hatası daha dar bir pencerede
   * geri geliyordu. CAS bunu kapatır.
   */
  @Test
  public void shouldNotOverwriteRealResultWhenCallGetsFinalizedDuringGracePeriod() {
    TelephonyResultCode realResult = telephonyResultCode(11L, 17);
    // Yarışı gerçek sıralamasıyla kur: fallback'in giriş kapıları GEÇİLDİKTEN SONRA, ama TRC
    // yazımından ÖNCE hangup thread'i finalize latch'ini alır. Kapılar geçildiği için erken
    // return devreye giremez; çağrıyı yalnız CAS koruyabilir. Latch'i getCallForHangup'ın
    // cache okumasına bağlayarak tam o araya enjekte ediyoruz.
    Call call = new Call() {
      @Override
      public String getId() {
        // finalizeOriginationFailFallback'in son kapısı getCallForHangup(call.getId()).
        if (isTelephonySlotReleased() || isBusinessResultFinalized()) {
          return super.getId();
        }
        setTelephonyResultCode(realResult);
        markBusinessResultFinalized();
        return super.getId();
      }
    };
    seedCall(call);
    call.setChannel(CHANNEL);
    call.setStartDate(46264.505820590275D);
    call.setOriginationDate(46264.505820590275D);
    decorator.getCalls().put(CALL_ID, call);

    ReflectionTestUtils.invokeMethod(decorator, "finalizeOriginationFailFallback", call, new Date(30000L));

    assertThat("hangup kapılardan sonra finalize etti; fallback RC 907 yazmamalı",
        call.getTelephonyResultCode().getCode(), is(17));
    assertThat(call.getTelephonyResultCode().getId(), is(11L));
    verify(cdrService, never()).saveOrUpdate(any(CDR.class));
  }

  /**
   * Test 20: CAS'in ters yönü — çağrı finalize edilmemişse fallback TRC'yi yazabilmeli.
   * Aksi halde test 19'un düzeltmesi fallback'i tamamen devre dışı bırakmış olurdu.
   */
  @Test
  public void shouldAllowFallbackToWriteResultWhenCallIsNotFinalized() {
    Call call = establishedRingingCall();

    boolean written = call.setTelephonyResultCodeIfNotFinalized(
        telephonyResultCode(79L, Constants.TELEPHONY_RC_ORIGINATE_ACTION_ERROR));

    assertThat(written, is(Boolean.TRUE));
    assertThat(call.getTelephonyResultCode().getCode(), is(Constants.TELEPHONY_RC_ORIGINATE_ACTION_ERROR));
  }

  private DialerHangupEvent hangupEvent(String cause) {
    DialerHangupEvent event = new DialerHangupEvent(this);
    event.setOriginId(CALL_ID);
    event.setHangupCause(cause);
    event.setDateReceived(new Date(25000L));
    return event;
  }

  /** Kanalı kurulmuş, çalan, henüz cevaplanmamış çağrı: üretimdeki RC 79 profilinin birebiri. */
  private Call establishedRingingCall() {
    Call call = baseCall();
    call.setChannel(CHANNEL);
    call.setStartDate(46264.505820590275D);
    call.setOriginationDate(46264.505820590275D);
    return call;
  }

  /** Originate hiç kurulamamış çağrı: kanal yok, startDate yok (gerçek originate hatası). */
  private Call neverOriginatedCall() {
    Call call = baseCall();
    call.setChannel(null);
    call.setStartDate(null);
    call.setOriginationDate(null);
    return call;
  }

  private Call baseCall() {
    return seedCall(new Call());
  }

  /** {@link #baseCall()}'un gövdesi; verilen örneği doldurur (alt sınıf örnekleri için ayrıldı). */
  private Call seedCall(Call call) {
    Campaign campaign = new Campaign();
    campaign.setId(55L);
    campaign.setType(Constants.CAMPAIGN_TYPE.PREDICTIVE);
    campaign.setColResultRcSequence("rc_sequence");
    campaign.setColResultUserRc("user_rc");
    campaign.setColResultTelephonyRc("telephony_rc");
    campaign.setColResultRetryHistory("retry_history");
    campaign.setRetryScheduleEnabled(false);
    PhoneNumberColumn phoneNumberColumn = new PhoneNumberColumn();
    phoneNumberColumn.setColNumber("phone1");
    campaign.getPhoneNumberColumns().add(phoneNumberColumn);

    call.setId(CALL_ID);
    call.setCampaign(campaign);
    call.setCalledNumber("05551000");
    call.setRawData(new JSONObject().put("phone1", "05551000"));
    call.setResultCodeSequence(0);

    CDR cdr = new CDR();
    cdr.setId(2002L);
    cdr.setInteractionId(CALL_ID);
    cdr.setCallDialTime(new Date(10000L));
    call.setCdr(cdr);
    return call;
  }

  private Server server() {
    Server server = new Server();
    server.setId(1L);
    server.setIp("127.0.0.1");
    return server;
  }

  /** pd_telephony_rc canlı eşlemesinin test alt kümesi (id / code). */
  private Map<Integer, TelephonyResultCode> telephonyResultCodes() {
    Map<Integer, TelephonyResultCode> relations = new HashMap<>();
    relations.put(1, telephonyResultCode(1L, 1));
    relations.put(16, telephonyResultCode(10L, 16));
    relations.put(17, telephonyResultCode(11L, 17));
    relations.put(18, telephonyResultCode(12L, 18));
    relations.put(19, telephonyResultCode(13L, 19));
    relations.put(21, telephonyResultCode(15L, 21));
    relations.put(34, telephonyResultCode(25L, 34));
    relations.put(Constants.ERROR_RESULT_CODES.CALL_EXPIRED_CLEARING.getCode(), telephonyResultCode(901L, 901));
    relations.put(Constants.TELEPHONY_RC_ORIGINATE_ACTION_ERROR,
        telephonyResultCode(79L, Constants.TELEPHONY_RC_ORIGINATE_ACTION_ERROR));
    return relations;
  }

  private TelephonyResultCode telephonyResultCode(Long id, int code) {
    TelephonyResultCode telephonyResultCode = new TelephonyResultCode();
    telephonyResultCode.setId(id);
    telephonyResultCode.setCode(code);
    return telephonyResultCode;
  }
}
