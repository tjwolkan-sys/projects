package com.powerdialer.engine.asterisk.ami;

import static com.powerdialer.util.Constants.ASTERISK_ERROR_CODES;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import static com.powerdialer.util.Constants.TELEPHONY_RC_ORIGINATE_ACTION_ERROR;

import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import java.io.IOException;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalTime;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalCause;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.powerdialer.engine.boost.DynamicBoostManager;
import com.powerdialer.engine.predictive.beta.model.CallType;
import com.powerdialer.engine.predictive.beta.processor.PredictiveBetaCallManager;
import com.powerdialer.util.UserPopulationEvaluator;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.asteriskjava.manager.DefaultManagerConnection;
import org.asteriskjava.manager.ManagerConnection;
import org.asteriskjava.manager.ManagerConnectionFactory;
import org.asteriskjava.manager.ManagerConnectionState;
import org.asteriskjava.manager.ManagerEventListener;
import org.asteriskjava.manager.SendActionCallback;
import org.asteriskjava.manager.action.HangupAction;
import org.asteriskjava.manager.action.OriginateAction;
import org.asteriskjava.manager.event.ManagerEvent;
import org.asteriskjava.manager.event.OriginateResponseEvent;
import org.asteriskjava.manager.event.VarSetEvent;
import org.asteriskjava.manager.internal.ManagerConnectionImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

import com.powerdialer.engine.asterisk.actions.OriginateActionDataContainer;
import com.powerdialer.engine.asterisk.events.AgentCalledByDialerEvent;
import com.powerdialer.engine.asterisk.events.AgentPredictiveAnswerEvent;
import com.powerdialer.engine.asterisk.events.AniDefinitionEvent;
import com.powerdialer.engine.asterisk.events.ClientPredictiveAnswerEvent;
import com.powerdialer.engine.asterisk.events.DialerHangupEvent;
import com.powerdialer.engine.asterisk.events.DisconnectEvent;
import com.powerdialer.engine.asterisk.events.HardphoneAnswerEvent;
import com.powerdialer.engine.asterisk.events.NewTransferChannelEvent;
import com.powerdialer.engine.asterisk.events.VariablesEvent;
import com.powerdialer.engine.db.CampaignDbOperations;
import com.powerdialer.engine.dialer.CallAttemptPolicy;
import com.powerdialer.engine.dialer.CampaignContext;
import com.powerdialer.engine.dialer.CampaignRealTimeRingingManager;
import com.powerdialer.engine.dialer.retry.RetryStrategy;
import com.powerdialer.engine.dialer.retry.RetryStrategyFactory;
import com.powerdialer.engine.dialer.routing.FctChannelGate;
import com.powerdialer.engine.dialer.routing.RouteDetailsCursor;
import com.globalbilgi.gbqueueselectionservice.grpc.RouteDetailEntry;
import com.powerdialer.model.AttachedData;
import com.powerdialer.model.AttachedDataItem;
import com.powerdialer.model.CDR;
import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.Group;
import com.powerdialer.model.PhoneNumberColumn;
import com.powerdialer.model.ResultCode;
import com.powerdialer.model.SendActionCallbackWrapper;
import com.powerdialer.model.Server;
import com.powerdialer.model.Status;
import com.powerdialer.model.TelephonyResultCode;
import com.powerdialer.model.User;
import com.powerdialer.redis.RedisUtil;
import com.powerdialer.redis.message.RedisMessage;
import com.powerdialer.redis.message.UserPauseMessage;
import com.powerdialer.service.CDRService;
import com.powerdialer.util.Constants;
import com.powerdialer.util.Constants.CAMPAIGN_STATE;
import com.powerdialer.util.Constants.LOG_MODULE;
import com.powerdialer.util.LoggerDecorator;
import com.powerdialer.util.Utils;

/**
 * Opens and holds connection to asterisk server
 */
public class ManagerConnectionDecorator extends ManagerConnectionFactory implements ManagerEventListener {

  private static final String CALL_ID_DIALPLAN_VARIABLE = "ORIGINID";
  private static final String TRANSFER_PARENT_ID_CHAN_VARIABLE = "PARENTID";
  private static final Integer[] ORIGINATE_RESPONSE_SUCCESSFUL_REASONS = { 1, 3, 4, 5, 8 };
  /**
   * Kanalı kurulmuş çağrıda {@code OriginateResponse(Reason=0)} sonrası gerçek hangup'ın
   * beklendiği süre. Üretim ölçümü (SBC terminal bacak -> Local {@code ;1} bacak yayılma
   * gecikmesi): p50 0,359s, p90 1,935s, max 4,632s. 10 saniye bu dağılımın tamamını karşılar
   * ve 1 saatlik {@code CALL_EXPIRE_TIMEOUT}'tan çok kısadır.
   */
  private static final long ORIGINATION_FAIL_GRACE_PERIOD_MS = 10000L;
  // FIX-GLB90048674 (Faz 1): event işleme süresi bu eşiği geçerse WARN (gbwebphone
  // ManagerConnectionWrapper.logExecutionTime modeli). Worker kuyruğu bu derinliği aşınca da WARN.
  /**
   * Asterisk {@code [outbound-predictive-fct]} {@code h} extension'ının okuduğu değişken adı.
   * Sözleşme adıdır; geçiş notu olmadan değişmez (dialplan tarafıyla eşleşir).
   */
  private static final String HAS_NEXT_VARIABLE = "HAS_NEXT";

  /**
   * Seçili FCT numarasının QSS'te tanımlı trunk'ı. Dialplan boş/eksik değeri varsayılan trunk
   * sayar; sözleşme adıdır, geçiş notu olmadan değişmez.
   */
  private static final String DIALER_TRUNK_VARIABLE = "DIALER_TRUNK";

  private static final long EVENT_HANDLING_TIME_THRESHOLD_MS = 200;
  private static final int EVENT_WORKER_QUEUE_WARN_THRESHOLD = 5000;
  // FIX: "slow event" WARN'u throttle'lanır. Log baskısı altında her event eşiği aşınca event
  // başına WARN basılıyor, aynı log kilidini besleyip gecikmeyi büyüten kendini-besleyen bir
  // döngü yaratıyordu. Sunucu başına en fazla bir WARN / SLOW_EVENT_WARN_THROTTLE_MS; pencere
  // içinde bastırılan uyarı sayısı bir sonraki WARN'a iliştirilir.
  private static final long SLOW_EVENT_WARN_THROTTLE_MS = 1000;
  // Drain-on-shutdown (preDestroy/logoff): bekleyen DB yazımlarının bitmesi beklenir; shutdownNow
  // KULLANILMAZ. 30s shutdown/restart için uygundur (canlı trafik yok, kayıpsızlık önceliklidir).
  private static final long EVENT_WORKER_SHUTDOWN_TIMEOUT_SECONDS = 30;
  // FIX-GLB90048674 (Faz 1B, Fix #2): decorator-replace/reconnect recovery yolu CPS thread'inde
  // (sıcak/kırılgan) koşar; orada 30s blok kabul edilemez. Bu yolda daha KISA, ayrı bir drain
  // timeout'u kullanılır. Timeout aşılırsa mevcut WARN korunur (yalnızca bu nadir durumda kuyrukta
  // task kalabilir — kabul edilebilir takas; açıkça loglanır).
  private static final long EVENT_WORKER_REPLACE_DRAIN_TIMEOUT_SECONDS = 5;
  private final LoggerDecorator LOG;
  private final Logger logger = LoggerFactory.getLogger(ManagerConnectionDecorator.class);
  // Slow-event WARN throttle durumu (thread-safe; worker tek-thread'li olsa da güvenli tarafta kalınır).
  private final java.util.concurrent.atomic.AtomicLong lastSlowEventWarnAtMs = new java.util.concurrent.atomic.AtomicLong(0);
  private final java.util.concurrent.atomic.AtomicLong suppressedSlowEventCount = new java.util.concurrent.atomic.AtomicLong(0);
  // Test seam: throttle penceresi için zaman kaynağı (deterministik test; sleep kullanılmaz).
  private java.util.function.LongSupplier slowEventClockMs = System::currentTimeMillis;
  // FIX-GLB90048674 (Faz 2): gbwebphone modelinde olduğu gibi event ve command yolu AYRI iki
  // AMI bağlantısına bölünür (ikisi de AYNI Asterisk'e — yeni config/credential yok).
  // eventConnection: bugünkü bağlantının birebir aynısı — listener bu, registerUserEventClasses
  //   burada, eventMask bugünküyle aynı (createConnection null geçer). state/recovery için
  //   AUTHORITATIVE bağlantı bu kalır (getManagerConnectionState bunu döndürür) → connection-loss/
  //   recovery STATE makinesi DEĞİŞMEZ (kırılgan alan korunur).
  // commandConnection: eventMask="off" (reader boşta kalır, ping/komut cevaplarını anında okur),
  //   listener YOK. Tüm Originate/Hangup buradan gider; response (ve callback) boşta reader'da
  //   anında döner. Bağlantı kapalıysa sendAction exception fırlatır ve MEVCUT originate hata
  //   yolu (handleExceptionDuringOrigination / originateCall try-catch) devreye girer — yeni state yolu yok.
  private ManagerConnection eventConnection;
  private ManagerConnection commandConnection;
  private final ManagerConnectionComposition connectionComposition;
  private Server server;
  private CampaignDbOperations dbOperations;
  private Map<Integer, TelephonyResultCode> ituToTelephonyResultCodeRelations = new ConcurrentHashMap<>();
  private final RetryStrategyFactory strategyFactory = new RetryStrategyFactory();
  private final Cache<String, Call> calls = CacheBuilder.newBuilder()
      .expireAfterWrite(Constants.CALL_EXPIRE_TIMEOUT, TimeUnit.MILLISECONDS)
      .removalListener(new CallRemovalListener())
      .build();
  private final Cache<String, Call> orphanedCalls = CacheBuilder.newBuilder()
      .expireAfterWrite(Constants.ORPHANED_CALL_EXPIRE_TIMEOUT, TimeUnit.MILLISECONDS)
      .build();
  private final Cache<String, Call> recoverableConnectionLossCalls = CacheBuilder.newBuilder()
      .expireAfterWrite(Constants.ORPHANED_CALL_EXPIRE_TIMEOUT, TimeUnit.MILLISECONDS)
      .removalListener(new RecoverableConnectionLossCallRemovalListener())
      .build();
  private final Cache<String, Call> awaitingUserResultCodeCalls = CacheBuilder.newBuilder()
      .expireAfterWrite(Constants.AWAITING_USER_RESULT_EXPIRE_TIMEOUT, TimeUnit.MILLISECONDS)
      .removalListener(new AwaitingUserResultCodeCallRemovalListener())
      .build();
  private final Cache<String, SendActionCallbackWrapper> actionCallbacksWrappers = CacheBuilder.newBuilder()
      .expireAfterWrite(Constants.CALLBACK_EXPIRE_TIMEOUT, TimeUnit.MILLISECONDS)
      .build();
  private final ScheduledExecutorService scheduleService;
  /**
   * FIX-GLB90048674 (Faz 1): AMI reader thread'inden devralınan event/callback işini işleyen worker.
   * Varsayılan INLINE ({@link #directExecutor}): testler decorator'ı {@code createConnection} olmadan
   * doğrudan kurar ve senkron yan etki bekler; bu varsayılan mevcut karakterizasyon testlerini
   * AYNEN yeşil tutar. Üretimde {@code createConnection} bunu gerçek tek-thread'li bounded worker ile
   * değiştirir (sıra korunur + blocking-put + drain-on-shutdown).
   */
  private volatile ExecutorService eventWorker = directExecutor;
  private CDRService cdrService;
  private DynamicBoostManager dynamicBoostManager;
  private PredictiveBetaCallManager predictiveBetaCallManager;

  public ManagerConnectionDecorator(String hostname, int port, String username, String password, ManagerConnectionComposition connectionComposition) {
    super(hostname, port, username, password);
    this.connectionComposition = connectionComposition;
    this.LOG = connectionComposition.getLoggerDecorator().getLogger(ManagerConnectionDecorator.class);
    ThreadFactory threadFactory = new ThreadFactoryBuilder().setDaemon(true)
        .setNameFormat("expired-calls-executor-%d")
        .build();
    scheduleService = Executors.newSingleThreadScheduledExecutor(threadFactory);
    scheduleService.scheduleAtFixedRate(this::clearExpiredCallbacksFromListeners, Constants.CALLBACK_EXPIRE_TIMEOUT,
        Constants.CALLBACK_EXPIRE_TIMEOUT, TimeUnit.MILLISECONDS);
    scheduleService.scheduleAtFixedRate(this::clearExpiredRecoverableConnectionLossCalls, Constants.ORPHANED_CALL_EXPIRE_TIMEOUT,
        Constants.CALLBACK_EXPIRE_TIMEOUT, TimeUnit.MILLISECONDS);
    scheduleService.scheduleAtFixedRate(this::clearExpiredAwaitingUserResultCodeCalls, Constants.AWAITING_USER_RESULT_EXPIRE_TIMEOUT,
        Constants.CALLBACK_EXPIRE_TIMEOUT, TimeUnit.MILLISECONDS);
  }

  /**
   * INLINE executor: komutu çağıran thread'de çalıştırır. {@link #eventWorker} varsayılanı budur;
   * yeni bağımlılık eklemeden senkron davranışı korur (testler ve {@code createConnection} öncesi).
   * Durumsuz olduğu için tek bir paylaşılan örnek güvenlidir (yeni mutable state değil).
   */
  private static final ExecutorService directExecutor = new AbstractExecutorService() {
    @Override public void execute(Runnable command) { command.run(); }
    @Override public void shutdown() { /* no-op: inline executor'ın yönetilen thread'i yok */ }
    @Override public List<Runnable> shutdownNow() { return Collections.emptyList(); }
    @Override public boolean isShutdown() { return false; }
    @Override public boolean isTerminated() { return false; }
    @Override public boolean awaitTermination(long timeout, TimeUnit unit) { return true; }
  };

  /**
   * Test seam: deterministik/senkron veya capturing executor enjekte etmek için (package-private).
   * Üretimde {@code createConnection} gerçek worker'ı enjekte eder.
   */
  void setEventWorker(ExecutorService eventWorker) {
    this.eventWorker = eventWorker;
  }

  /**
   * Blocking-put rejected handler (invariant #2 — kayıpsızlık): kuyruk dolduğunda task'ı DÜŞÜRMEZ
   * ve exception ATMAZ; submitter'ı kuyrukta yer açılana kadar bloklar ({@code getQueue().put}).
   * CallerRuns DEĞİL (o, reader'ı worker'la eşzamanlı çalıştırıp sırayı/atomikliği bozardı).
   * Interrupt'ta interrupt bayrağı geri yüklenir.
   */
  static RejectedExecutionHandler blockingPutRejectedHandler() {
    return (r, executor) -> {
      if (executor.isShutdown()) {
        return;
      }
      try {
        executor.getQueue().put(r);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    };
  }

  private static ThreadFactory daemonNamedThreadFactory(String nameFormat) {
    return new ThreadFactoryBuilder().setDaemon(true).setNameFormat(nameFormat).build();
  }

  public void setDynamicBoostManager(DynamicBoostManager dynamicBoostManager) {
    this.dynamicBoostManager = dynamicBoostManager;
  }

  public void setPredictiveBetaCallManager(PredictiveBetaCallManager predictiveBetaCallManager) {
    this.predictiveBetaCallManager = predictiveBetaCallManager;
  }

  public Map<Integer, TelephonyResultCode> getItuToTelephonyResultCodeRelations() {
    return ituToTelephonyResultCodeRelations;
  }

  public void setItuToTelephonyResultCodeRelations(Map<Integer, TelephonyResultCode> ituToTelephonyResultCodeRelations) {
    this.ituToTelephonyResultCodeRelations = ituToTelephonyResultCodeRelations;
  }

  public CampaignDbOperations getDbOperations() {
    return dbOperations;
  }

  public void setDbOperations(CampaignDbOperations dbOperations) {
    this.dbOperations = dbOperations;
  }

  public Server getServer() {
    return server;
  }

  public void setServer(Server server) {
    this.server = server;
  }

  public Cache<String, Call> getCalls() {
    return calls;
  }

  public Cache<String, Call> getOrphanedCalls() {
    return orphanedCalls;
  }

  public Cache<String, Call> getRecoverableConnectionLossCalls() {
    return recoverableConnectionLossCalls;
  }

  /**
   * FIX-333599: bu bağlantıda görüşmesi süren (originate edilmiş, finalize edilmemiş) çağrıların
   * uniqueKey'lerini {@code target}'a ekler. Dört cache de kapsanır: çalıyor/görüşmede
   * ({@code calls}), wrap-up'ta URC bekleniyor, bağlantı kaybı kurtarma ve orphan. Producer'ın
   * mükerrer fetch guard'ı bu set ile görüşmedeki satırı ikinci kez Call'a dönüştürmez. Dört
   * cache'in de expiry'si olduğu için takılan bir çağrı satırı kalıcı olarak kilitlemez.
   *
   * <p>Maliyet: producer tick'i başına bağlantıdaki canlı çağrı sayısı kadar tek geçiş; Guava
   * cache görünümleri weakly consistent'tır, kilit almaz. Call/event hot path'inde çağrılmaz.</p>
   */
  public void collectInFlightUniqueKeys(Long campaignId, Set<String> target) {
    if (campaignId == null || target == null) {
      return;
    }
    collectInFlightUniqueKeys(calls, campaignId, target);
    collectInFlightUniqueKeys(awaitingUserResultCodeCalls, campaignId, target);
    collectInFlightUniqueKeys(recoverableConnectionLossCalls, campaignId, target);
    collectInFlightUniqueKeys(orphanedCalls, campaignId, target);
  }

  private static void collectInFlightUniqueKeys(Cache<String, Call> cache, Long campaignId, Set<String> target) {
    for (Call call : cache.asMap().values()) {
      if (call.getUniqueKey() != null && call.getCampaign() != null && campaignId.equals(call.getCampaign().getId())) {
        target.add(call.getUniqueKey());
      }
    }
  }

  public Cache<String, Call> getAwaitingUserResultCodeCalls() {
    return awaitingUserResultCodeCalls;
  }

  public void deferCallCleanupOnConnectionLoss(Call call) {
    if (call == null || StringUtils.isEmpty(call.getId())) {
      return;
    }
    calls.invalidate(call.getId());
    recoverableConnectionLossCalls.put(call.getId(), call);
  }

  /**
   * Primary (event) bağlantısını döndürür — dış çağıranlar/testler için davranış korunur
   * (FIX-GLB90048674 Faz 2: eski tek {@code managerConnection}'ın yerine event bağlantısı geçer).
   */
  public ManagerConnection getManagerConnection() {
    return eventConnection;
  }

  /**
   * FIX-GLB90048674 (Faz 2): Originate/Hangup'ın gittiği event'siz command bağlantısı (dahili kullanım).
   */
  ManagerConnection getCommandConnection() {
    return commandConnection;
  }

  /**
   * FIX-GLB90048674 (Faz 2): state/recovery için AUTHORITATIVE bağlantı EVENT bağlantısıdır.
   * {@code CallOriginator}'ın CPS poll'u, {@code handleConnectionLoss} ve recoverable-call mantığı
   * bu değeri kullandığı için event bağlantısı döndürülerek connection-loss/recovery semantiği
   * Faz 1'deki gibi DEĞİŞMEDEN korunur.
   */
  public ManagerConnectionState getManagerConnectionState() {
    return eventConnection != null ? eventConnection.getState() : null;
  }

  public ManagerConnectionComposition getConnectionComposition() {
    return connectionComposition;
  }

  public void setCdrService(CDRService cdrService) {
    this.cdrService = cdrService;
  }

  /**
   * Create manager connection and login to asterisk server
   */
  void createConnection(int maxLoginAttempts, int maxPingAttempts, long pingTimeout, long stateCheckInterval, int connectionTimeout) {
    try {
      // FIX-GLB90048674 (Faz 1): INLINE varsayılanı gerçek tek-thread'li bounded worker ile değiştir.
      // Tek thread => event/callback sırası korunur; bounded + blocking-put => task düşmez; daemon =>
      // JVM kapanışını engellemez. Reader thread artık DB/CDR işini bu worker'a devredip serbest kalır.
      // Faz 1 invariant'ı: worker login'den ÖNCE kurulur (ilk event'ler gelmeden worker hazır olsun).
      eventWorker = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
          new LinkedBlockingQueue<>(Constants.AMI_EVENT_WORKER_QUEUE_CAPACITY),
          daemonNamedThreadFactory("ami-event-worker-" + server.getIp() + "-%d"),
          blockingPutRejectedHandler());
      // FIX-GLB90048674 (Faz 2): event ve command bağlantısı ayrılır (gbwebphone modeli).
      // eventConnection: bugünkü bağlantının birebir aynısı (eventMask = null, listener + user event class'ları burada).
      eventConnection = createAutonomousManagerConnection(stateCheckInterval, maxLoginAttempts, maxPingAttempts, pingTimeout, null);
      eventConnection.addEventListener(this);
      eventConnection.setSocketTimeout(connectionTimeout);
      registerUserEventClasses();
      // commandConnection: eventMask = "off" => reader boşta kalır, originate/hangup response'unu anında okur; listener YOK.
      commandConnection = createAutonomousManagerConnection(stateCheckInterval, maxLoginAttempts, maxPingAttempts, pingTimeout, "off");
      commandConnection.setSocketTimeout(connectionTimeout);
      eventConnection.login();
      commandConnection.login();
    } catch (Exception e) {
      LOG.error("Got exception on AMI connection initializing: ", e);
    }
  }

  /**
   * Register custom user events on the EVENT connection (the listener); command connection
   * is event'siz (eventMask="off") so it does not register/receive events.
   */
  private void registerUserEventClasses() {
    eventConnection.registerUserEventClass(ClientPredictiveAnswerEvent.class);
    eventConnection.registerUserEventClass(AgentPredictiveAnswerEvent.class);
    eventConnection.registerUserEventClass(DialerHangupEvent.class);
    eventConnection.registerUserEventClass(DisconnectEvent.class);
    eventConnection.registerUserEventClass(HardphoneAnswerEvent.class);
    eventConnection.registerUserEventClass(VariablesEvent.class);
    eventConnection.registerUserEventClass(AgentCalledByDialerEvent.class);
    eventConnection.registerUserEventClass(AniDefinitionEvent.class);
    eventConnection.registerUserEventClass(NewTransferChannelEvent.class);
  }

  /**
   * FIX-GLB90048674 (Faz 2) test seam: event ve command bağlantısını enjekte etmek için
   * (package-private, {@link #setEventWorker} seam'iyle aynı stil). Üretimde
   * {@code createConnection} bunları içeride kurar; testler {@code createConnection} olmadan
   * mock bağlantı enjekte eder.
   */
  void setConnections(ManagerConnection eventConnection, ManagerConnection commandConnection) {
    this.eventConnection = eventConnection;
    this.commandConnection = commandConnection;
  }

  /**
   * Create and send originateAction to asterisk server
   * @param call the call to originate
   */
  public boolean originateCall(Call call) {
    try {
      // Aynı Call nesnesi yeniden originate edilebilir (FCT list-retry
      // tryFctRetryWithNextCallerId bu metodu doğrudan tekrar çağırır). Önceki denemenin
      // bekleyen origination-fail fallback'i iptal edilmezse YENİ denemeye RC 907 yazıp
      // canlı çağrıyı öldürebilir.
      call.cancelOriginationFailFallbackFuture();
      calls.put(call.getId(), call);
      OriginateAction originateAction = connectionComposition.getManagerActionFactory().generateCall(call);
      applyHasNextVariable(originateAction, call);
      applyTrunkVariable(originateAction, call);
      sendAttachedDataToRedis(call);
      if (Boolean.TRUE.equals(call.getCampaign().getAttachedDataLongTermSave())) {
        saveAttachedDataLongTerm(call);
      }
      if (call.getCampaign().isPreviewCampaign()) {
        publishOriginateMessageToRedis(originateAction, call);
        publishCallPreviewEvent(call);
      } else {
        sendOriginateActionToAsterisk(originateAction);
        // Originate edildi ancak Asterisk VarSetEvent'i henüz dönmedi; Gamma predictive
        // bu uçuştaki çağrıyı ringingLoad'a dahil edebilsin diye pending cache'e yazıyoruz.
        // addRingingCall (VarSet handler'ı) pending'i otomatik invalidate eder; cache TTL'i
        // AMI hiç dönmezse self-healing temizler.
        connectionComposition.getDialerFacade().getCampaignRealTimeRingingManager().addPendingRingingCall(call);
      }
      call.getCampaign().addCallInProgress(call);
      return true;
    } catch (Exception e) {
      handleExceptionDuringOrigination(call);
      LOG.error(call + " is returned to db due to " + e + " on " + server, e, LOG_MODULE.ASTERISK, call.getCampaign());
      return false;
    }
  }

  /**
   * FCT gate yolundaki her Originate'e {@code HAS_NEXT} değişkenini ekler.
   *
   * <p>Asterisk {@code [outbound-predictive-fct]} context'inin {@code h} extension'ı bu değişkeni
   * okur: {@code HAS_NEXT=1} ve DIALSTATUS CONGESTION/CHANUNAVAIL ise yalnız {@code DialerHangup}
   * UserEvent'i gönderilir, Disconnect/ODBC/hangup_data/EXEC/qos adımları atlanır. Boş veya
   * {@code 0} ise eski davranış korunur.
   *
   * <p>Gate yoksa, çağrı gate yolunda değilse veya kill switch kapalıysa değişken HİÇ EKLENMEZ;
   * Asterisk tarafında boş değer 04.05.2026 davranışına denk gelir. Preview kampanyalar
   * {@code isGatePath} gereği zaten kapsam dışıdır, bu yüzden Redis'e publish edilen preview
   * payload'ına da girmez.
   */
  private void applyHasNextVariable(OriginateAction originateAction, Call call) {
    FctChannelGate gate = fctChannelGate();
    if (gate == null || !gate.isHasNextContractActive(call)) {
      return;
    }
    originateAction.setVariable(HAS_NEXT_VARIABLE, gate.hasNextCallerId(call) ? "1" : "0");
  }

  /**
   * Seçili FCT numarasının trunk'ını {@code Variable: DIALER_TRUNK} olarak ekler. Değer QSS'ten
   * gelir ({@code RouteDetailEntry.trunk}), PD yorumlamaz.
   *
   * <p>{@code HAS_NEXT}'ten FARKLI olarak yalnız {@link FctChannelGate#isGatePath} koşuluna
   * bağlıdır, kill switch'e DEĞİL. Gerekçe: {@code fct.channel.gate.enabled=false} kapasite
   * saymayı durdurur ama caller ID yine QSS entry'sinden seçilir — trunk'ı o anda göndermeyi
   * kesmek, kill switch'in bütün FCT çağrılarını varsayılan trunk'a kaydırması demek olurdu.
   * Kill switch kapasite gate'ini geri alır, çağrının doğru trunk'tan çıkmasını değil.
   *
   * <p>Boş/null trunk'ta değişken HİÇ eklenmez: dialplan tarafında set edilmemiş değişken zaten
   * boş string'e denktir ve AMI satırı gereksiz uzamaz.
   */
  private void applyTrunkVariable(OriginateAction originateAction, Call call) {
    FctChannelGate gate = fctChannelGate();
    if (gate == null || !gate.isGatePath(call)) {
      return;
    }
    String trunk = call.getFctTrunk();
    if (StringUtils.isNotBlank(trunk)) {
      originateAction.setVariable(DIALER_TRUNK_VARIABLE, trunk);
    }
  }

  private void publishCallPreviewEvent(Call call) {
    connectionComposition.getDialerFacade().getCallEventPublisher().publishCallPreviewEvent(call.getId(), call.getCampaign().getId(),
                                                                                            call.getCalledNumber(), call.getUser().getUsername(),
                                                                                            server.getName(), call.getCallerId(),
                                                                                            call.getCdr().getCallStartTime());
  }

  private void publishOriginateMessageToRedis(OriginateAction originateAction, Call call) {
    RedisMessage originateMsg = new RedisMessage(Constants.MSG_ACTION_TYPE.ORIGINATE_CALL,
                                                 Constants.MSG_ENTITY_TYPE.ORIGINATE_ACTION_WRAPPER,
                                                 new OriginateActionDataContainer(originateAction, call, call.getCampaign(), server));
    connectionComposition.getRedisUtil().publishMessage(Constants.REDIS_CHANNEL.channelGBPowerDialer.toString(), originateMsg);
    call.setPreviewFuture(scheduleService.schedule(() -> this.handlePreviewTimeout(call), getPreviewFutureDelay(call), TimeUnit.SECONDS));
  }

  private void handlePreviewTimeout(Call call) {
    LOG.warn("Handling preview timeout action for " + call);
    if (call.getUser() != null)
      returnUserToACDEngine(call);
    call.getCampaign().removeCallInProgress(call);
    clearCall(call);
    releaseFctChannel(call);
    try {
      dbOperations.removeFetchedDataFlagFromCall(call);
    } catch (SQLException e) {
      LOG.error(e + " on " + server, e, LOG_MODULE.DB);
    }
  }

  /**
   * Obtains a delay for preview timeout execution. Besides the campaign preview timeout option,
   * we add a few seconds more in order to cover network costs.
   * @param call the call to obtain timeout value from
   * @return preview timeout delay
   */
  private int getPreviewFutureDelay(Call call) {
    return call.getCampaign().getPreviewTimeout() + connectionComposition.getPreviewTimeoutOriginationDelaySec();
  }

  /**
   * Send {@link OriginateAction} to the Asterisk server with
   * {@link SendActionCallbackWrapper} which is new one or from cache - first
   * one which not in use
   * @param originateAction the action to send to the Asterisk server
   * @throws IOException if the network connection is disrupted.
   */
  private void sendOriginateActionToAsterisk(OriginateAction originateAction) throws IOException {
    SendActionCallbackWrapper callbackWrapper = actionCallbacksWrappers.asMap().values().stream()
        .filter(wrapper -> !wrapper.isInUse())
        .findAny()
        .orElseGet(() -> createAndCacheNewCallback(originateAction));
    sendActionUsingCallback(originateAction, callbackWrapper);
  }

  /**
   * Create callback for originate action and put it to cache
   * @param originateAction the action to send to the Asterisk server
   */
  private SendActionCallbackWrapper createAndCacheNewCallback(OriginateAction originateAction) {
    // FIX-GLB90048674 (Faz 1): originate response callback de AMI reader/dispatch thread'inde
    // çalışıyor. DB-ağır fail handler'ı (handleOriginationFail -> processCallResult) aynı eventWorker'a
    // devrederek reader'ı serbest tutarız; tek worker event<->callback sırasını bugünkü gibi serileştirir.
    // onResponse'ın ucuz kısmı (metrics, setInUse(false)) response thread'inde kalır.
    SendActionCallbackWrapper sendActionCallbackWrapper = new SendActionCallbackWrapper(this,
        (callId, failDate) -> eventWorker.execute(() -> runOriginationFailTask(callId, failDate)));
    actionCallbacksWrappers.put(originateAction.toString(), sendActionCallbackWrapper);
    return sendActionCallbackWrapper;
  }

  /**
   * Send action using {@link SendActionCallbackWrapper} preliminarily set it as
   * in use and register send action time
   * @param originateAction the action to send to the Asterisk server
   * @param sendActionCallbackWrapper the action response handler
   * @throws IOException if the network connection is disrupted.
   */
  private void sendActionUsingCallback(OriginateAction originateAction, SendActionCallbackWrapper sendActionCallbackWrapper) throws IOException {
    sendActionCallbackWrapper.setInUse(true);
    sendActionCallbackWrapper.setSendActionNanos(System.nanoTime());
    // FIX-GLB90048674 (Faz 2): Originate event'siz command bağlantısından gönderilir; response
    // (ve onResponse callback'i) boşta command reader'ında anında döner. Callback'in DB-ağır fail
    // handler'ı zaten eventWorker'a devrediliyor (Faz 1). Bağlantı kapalıysa sendAction exception
    // fırlatır ve originateCall'daki mevcut try-catch (handleExceptionDuringOrigination) devreye girer.
    commandConnection.sendAction(originateAction, sendActionCallbackWrapper);
  }

  private void sendAttachedDataToRedis(Call call) {
    RedisUtil redisUtil = connectionComposition.getRedisUtil();
    redisUtil.saveObject(call.getId(), call.getAttachedData());
  }

  private void saveAttachedDataLongTerm(Call call) {
    if (call.getAttachedData() == null || call.getCampaign().getAttachedDatas() == null) {
      return;
    }
    List<AttachedDataItem> attachedDataForSave = new ArrayList<>();
    Set<String> longTermSaveAttachedDataNames = call
        .getCampaign()
        .getAttachedDatas()
        .stream()
        .filter(attachedData -> attachedData.getSaveLongTerm() && attachedData.isEnabled() && !attachedData.isDeleted())
        .map(AttachedData::getFieldName)
        .collect(Collectors.toSet());
    String encodedB64JsonAttachedData = call.getAttachedData();
    String jsonAttachedData = new String(Base64.getDecoder().decode(encodedB64JsonAttachedData));
    ObjectMapper objectMapper = new ObjectMapper();
    ObjectReader objectReader = objectMapper.readerFor(new TypeReference<Map<String, JsonNode>>() {});
    try {
      Map<String, JsonNode> attachedDataNameToParams = objectReader.readValue(jsonAttachedData);
      for (String attachedDataName : longTermSaveAttachedDataNames) {
        String attachedDataValue;
        if (attachedDataNameToParams.containsKey(attachedDataName) && attachedDataNameToParams.get(attachedDataName) != null) {
          attachedDataValue = attachedDataNameToParams.get(attachedDataName)
              .get("value")
              .asText();
        } else {
          attachedDataValue = null;
        }
        AttachedDataItem attachedDataItem = new AttachedDataItem();
        attachedDataItem.setFieldName(attachedDataName);
        attachedDataItem.setFieldValue(attachedDataValue);
        attachedDataItem.setParentCallId(call.getId());
        attachedDataForSave.add(attachedDataItem);
      }
    } catch (IOException e) {
      String errorMessage = "Error when deserializing JSON format of attached data. B64 encoded data: " + encodedB64JsonAttachedData;
      LOG.error(errorMessage);
      throw new IllegalArgumentException(errorMessage);
    }
    connectionComposition.getAttachedDataItemService().save(attachedDataForSave);
  }

  /**
   * Logoff from asterisk server
   */
  void logoff() {
    // FIX-GLB90048674 (Faz 2): event ve command bağlantısının İKİSİ de kapatılır (null-guard'lı;
    // bağlantı hiç kurulmamışsa veya test enjekte etmediyse güvenli). preDestroy bu metodu çağırır.
    if (eventConnection != null) {
      eventConnection.logoff();
    }
    if (commandConnection != null) {
      commandConnection.logoff();
    }
    // preDestroy/shutdown yolu: uzun (shutdown) drain timeout'u — kayıpsızlık önceliklidir.
    shutdownEventWorkerWithDrain(EVENT_WORKER_SHUTDOWN_TIMEOUT_SECONDS);
  }

  /**
   * FIX-GLB90048674 (Faz 1B, invariant #2): decorator-replace (reconnect) yolu için yalnızca
   * event worker'ı DRAIN edip kapatır ({@code shutdown()} + {@code awaitTermination}); ESKİ
   * decorator referansı bırakılmadan önce çağrılır, böylece daemon worker thread'i sızmaz ve
   * kuyrukta bekleyen DB/CDR yazımları terk edilmez. {@code logoff()}'tan farklı olarak
   * {@code managerConnection.logoff()} ÇAĞRILMAZ: bu yol AMI bağlantısını yönetmez ve istenmeyen
   * AMI yan etkisinden kaçınır. {@code shutdownNow()} KULLANILMAZ (drain garantisi korunur).
   *
   * <p>FIX-GLB90048674 (Faz 1B, Fix #2): bu yol CPS thread'inde sıcak recovery sırasında çağrıldığı
   * için canlı/recovery yolunu 30s bloklamamak adına KISA {@link #EVENT_WORKER_REPLACE_DRAIN_TIMEOUT_SECONDS}
   * timeout'u kullanılır; shutdown drain'i ise uzun {@link #EVENT_WORKER_SHUTDOWN_TIMEOUT_SECONDS}'de kalır.
   */
  public void drainAndShutdownEventWorker() {
    shutdownEventWorkerWithDrain(EVENT_WORKER_REPLACE_DRAIN_TIMEOUT_SECONDS);
  }

  /**
   * FIX-GLB90048674 (Faz 1, invariant #2): worker'ı DRAIN ederek kapatır — {@code shutdown()} +
   * {@code awaitTermination}. {@code shutdownNow()} KULLANILMAZ; o kuyrukta bekleyen DB/CDR
   * yazımlarını düşürürdü. INLINE varsayılan worker yönetilen thread tutmadığı için dokunulmaz.
   *
   * @param awaitTerminationTimeoutSeconds drain için beklenecek süre (saniye). preDestroy/logoff uzun
   *        (shutdown) timeout'u; decorator-replace/recovery yolu ise kısa timeout'u verir (Fix #2).
   *        Timeout aşılırsa WARN loglanır ve yalnızca bu durumda kuyrukta task kalabilir.
   */
  private void shutdownEventWorkerWithDrain(long awaitTerminationTimeoutSeconds) {
    ExecutorService worker = this.eventWorker;
    if (worker == directExecutor) {
      return;
    }
    worker.shutdown();
    try {
      if (!worker.awaitTermination(awaitTerminationTimeoutSeconds, TimeUnit.SECONDS)) {
        LOG.warn("(" + (server != null ? server.getIp() : "?") + ") AMI event worker did not drain within "
            + awaitTerminationTimeoutSeconds + "s; queued tasks may remain (abandoned only in this timeout case)");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /**
   * Events sent from asterisk are handled by this method.
   * @param event the event to handle
   */
  @Override
  public final void onManagerEvent(final ManagerEvent event) {
    // FIX-GLB90048674 (Faz 1): reader thread'i bloklamamak için tüm event işleme (event-received
    // logu dahil) worker'a devredilir. eventWorker tek thread olduğundan event'ler geldiği sırada
    // (FIFO) işlenir; bu metot submit edip hemen döner. Varsayılan INLINE worker'da (testler /
    // createConnection öncesi) davranış senkron kalır.
    eventWorker.execute(() -> processEvent(event));
  }

  /**
   * Worker thread'inde tek bir event'i route eder ve işleme süresini ölçer (gbwebphone
   * {@code logExecutionTime} modeli). {@code instanceof} dağıtımı ve yan etkiler değişmedi.
   */
  private void processEvent(final ManagerEvent event) {
    // Ham event izi INFO'dan DEBUG'a indirildi. Maliyet log satirinin kendisi degil
    // ManagerEvent.toString(): reflection tabanli calisir (ReflectionUtil.getGetters her cagride
    // cache'siz yeni HashMap + Class.getMethods() dizi kopyasi kurar, ardindan ~40 alan icin
    // Method.invoke). isDebugEnabled guard'i ZORUNLU: Java argumani cagriden once hesapladigi
    // icin guard olmadan seviye DEBUG'a inse bile toString maliyeti her event'te odenir.
    // LoggerDecorator'da debug() yok; ham slf4j alani kullanilir (logger adi LOG ile ayni).
    // Iz gerektiginde bu sinifin logger'i DEBUG yapilarak geri acilir (logback scan kapali,
    // restart gerekir). Karar/akis degismez: dispatchEvent, catch ve logEventHandlingTime aynen.
    if (logger.isDebugEnabled()) {
      logger.debug("(" + server.getIp() + ") Event received: " + event);
    }
    // FIX: kronometre event izi logundan SONRA başlar. Aksi halde (eski davranış) log yazma
    // gecikmesi -özellikle log baskısı altında- ölçülen süreye dahil oluyor ve gerçek işi yavaş
    // göstermeyen olaylarda yanlış "slow event" WARN'u tetikliyordu. İz DEBUG'a indiği için bu
    // maliyet üretimde artık oluşmaz; guard açıldığında ölçüm bozulmasın diye konum korunur.
    long startTime = System.currentTimeMillis();
    try {
      dispatchEvent(event);
    } catch (Exception e) {
      // FIX-GLB90048674 (Faz 1B, Fix #1 — "behavior birebir aynı"): bu iş artık AMI reader/dispatch
      // thread'i yerine tek-thread'li worker'da koşuyor. Eskiden asterisk-java dispatch'i her
      // onManagerEvent'i try/catch(RuntimeException) ile sarıp "Unexpected exception in eventHandler"
      // loglar ve devam ederdi. Görevi sarmazsak exception worker thread'inden kaçar; thread ölür
      // (ThreadPoolExecutor sonraki submit'te yeni thread doğursa da) in-flight event'in kalan işi
      // kaybolur ve hata app log yerine varsayılan UncaughtExceptionHandler/System.err'e gider
      // (sessiz regresyon). catch(Exception) ile eski koruyucu davranışı birebir geri kazanırız:
      // SESSİZCE yutmadan LOG'a yazıp worker thread'ini yaşatırız.
      LOG.error("(" + server.getIp() + ") Unexpected exception in eventHandler: " + event, e);
    } finally {
      logEventHandlingTime(startTime, event);
    }
  }

  private void dispatchEvent(final ManagerEvent event) {
    if (event instanceof VarSetEvent) {
      handleCallCreation((VarSetEvent) event);
    } else if (event instanceof ClientPredictiveAnswerEvent) {
      handleCallAnswer((ClientPredictiveAnswerEvent) event);
    } else if (event instanceof AgentPredictiveAnswerEvent) {
      handleAgentAnswer((AgentPredictiveAnswerEvent) event);
    } else if (event instanceof DialerHangupEvent) {
      handleDialerHangup((DialerHangupEvent) event);
    } else if (event instanceof OriginateResponseEvent) {
      handleOriginateResponse((OriginateResponseEvent) event);
    } else if(event instanceof AniDefinitionEvent) {
      handleAniDefinitionEvent((AniDefinitionEvent) event);
    } else if(event instanceof AgentCalledByDialerEvent) {
      handleAgentCalledByDialerEvent((AgentCalledByDialerEvent) event);
    } else if (event instanceof NewTransferChannelEvent) {
      handleNewTransferChannel((NewTransferChannelEvent) event);
    }
  }

  /**
   * Worker içinde event işleme süresini loglar: eşik aşılırsa WARN, yoksa sessiz kalır
   * (verbose üretim logunu artırmamak için). Kuyruk derinliği uyarı eşiğini aşarsa backlog WARN'u.
   */
  private void logEventHandlingTime(long startTime, ManagerEvent event) {
    long executionTime = System.currentTimeMillis() - startTime;
    if (executionTime > EVENT_HANDLING_TIME_THRESHOLD_MS) {
      maybeWarnSlowEvent(event, executionTime);
    }
    if (eventWorker instanceof ThreadPoolExecutor) {
      int queueSize = ((ThreadPoolExecutor) eventWorker).getQueue().size();
      if (queueSize > EVENT_WORKER_QUEUE_WARN_THRESHOLD) {
        LOG.warn("(" + server.getIp() + ") AMI event worker backlog is high: queueSize=" + queueSize);
      }
    }
  }

  /**
   * FIX: slow-event WARN'unu throttle'lar. Log baskısı altında her event eşiği aştığında event
   * başına WARN basılması, aynı log kilidini besleyip gecikmeyi büyüten kendini-besleyen bir
   * döngü yaratıyordu. Sunucu başına en fazla bir WARN / {@link #SLOW_EVENT_WARN_THROTTLE_MS};
   * pencere içinde bastırılan uyarılar sayılıp bir sonraki WARN'a iliştirilir. Eşik değişmedi.
   */
  private void maybeWarnSlowEvent(ManagerEvent event, long executionTime) {
    long now = slowEventClockMs.getAsLong();
    long last = lastSlowEventWarnAtMs.get();
    if (now - last >= SLOW_EVENT_WARN_THROTTLE_MS && lastSlowEventWarnAtMs.compareAndSet(last, now)) {
      long suppressed = suppressedSlowEventCount.getAndSet(0);
      LOG.warn("(" + server.getIp() + ") " + event.getClass().getSimpleName() + " handled in " + executionTime + "ms"
          + (suppressed > 0 ? " (+" + suppressed + " benzer uyarı bastırıldı)" : ""));
    } else {
      suppressedSlowEventCount.incrementAndGet();
    }
  }

  /**
   * Event being thrown when call is created
   * @param event the event to handle
   */
  private void handleCallCreation(VarSetEvent event) {
    if (StringUtils.isEmpty(event.getValue()) || !CALL_ID_DIALPLAN_VARIABLE.equals(event.getVariable()))
      return;

    Call call = calls.getIfPresent(event.getValue());

    if (call != null && call.getStartDate() == null) {
      call.setStartDate(Utils.convertDateToDouble(event.getDateReceived()));

      call.setChannel(event.getChannel());
      // Asterisk LINKEDID = bu Local kanalın UNIQUEID'si; çağrı boyunca tüm bridge'lenen
      // bacaklar bu değeri miras alır. NewTransferChannel.LinkedChannel ile eşleştirme
      // için saklanır. JVM-only transient; DB/CDR'a yazılmaz.
      call.setLinkedid(event.getUniqueId());
      call.getCampaign().incrementRingingCalls(call);

      //RINGING Call
      if(call.isBetaPredictive()) {
        predictiveBetaCallManager.updateCallState(call, CallType.RINGING, true);
      }

      connectionComposition.getDialerFacade().getCampaignRealTimeRingingManager().addRingingCall(call);

      dbOperations.insertCallResultToDB(call);

      if (call.getCampaign().isPreviewCampaign()) {
        connectionComposition.getDialerFacade().getCallEventPublisher().publishCallPreviewAcceptEvent(call.getId(), event.getDateReceived());
      }
      LOG.info("VarSetEvent for: " + call + ", " + event);
    }
  }

  /**
   * Event being thrown when client answered
   * @param event the event to handle
   */
  private void handleCallAnswer(ClientPredictiveAnswerEvent event) {
    if (StringUtils.isEmpty(event.getParentId()))
      return;

    Call call = calls.getIfPresent(event.getParentId());

    if (call != null && call.getAnswerDate() == null) {
      call.setChannel(event.getClientchannel());
      call.setAnswerDate(Utils.convertDateToDouble(event.getDateReceived()));
      call.getCdr().setCallAnswerTime(event.getDateReceived());
      if (call.getCampaign().isOutboundIvrCampaign() || call.getCampaign().isPreviewCampaign()) {
        call.getCdr().setCallHandlingStartTime(event.getDateReceived());
      }
      call.getCampaign().incrementTouchedContacts();
      // FIX-333607: campaign stop edilip context kaldırıldıktan sonra gelen cevapta context sayaçları
      // atlanır; answer işleme (CDR, sonuç yazımı, customer answer event'i) devam eder.
      Campaign runtimeCampaign = runtimeCampaignOf(call);
      if (runtimeCampaign != null) {
        runtimeCampaign.incrementActiveCalls(call);
      }

      //Active Call
      if(call.isBetaPredictive()) {
        predictiveBetaCallManager.updateCallState(call,CallType.ACTIVE,true);
      }
      
      connectionComposition.getDialerFacade().getCampaignRealTimeRingingManager().removeRingingCall(call);

      if (call.getStartDate() != null) {
        if (runtimeCampaign != null) {
          runtimeCampaign.decrementRingingCalls(call);
        }
        if(call.isBetaPredictive()) {
          predictiveBetaCallManager.updateCallState(call,CallType.RINGING,false);
        }
      }

      dbOperations.insertCallResultToDB(call);

      saveCdrOnAnswer(call, event.getDateReceived(), "customer");

      notifyGammaOnCallAnswered(call);

      LOG.info("ClientPredictiveAnswer for: " + call + ", " + event);
      checkStopCondition(call.getCampaign());
      publishCustomerAnswerEvent(call, event.getDateReceived());
    }
  }


  private Campaign runtimeCampaignOf(Call call) {
    CampaignContext campaignContext = connectionComposition.getDialerFacade().getCampaignContexts().get(call.getCampaign().getId());
    return campaignContext != null ? campaignContext.getCampaign() : null;
  }

  private void publishCustomerAnswerEvent(Call call, Date answerDate) {
    connectionComposition.getDialerFacade().getCallEventPublisher().publishCallCustomerAnswerEvent(call.getId(), answerDate);
  }

  /**
   * Event being thrown when agent answered
   * @param event the event to handle
   */
  private void handleAgentAnswer(AgentPredictiveAnswerEvent event) {
    if (StringUtils.isEmpty(event.getParentId()) || StringUtils.isEmpty(event.getUsername()))
      return;
    Call call = calls.getIfPresent(event.getParentId());
    if (call == null) {
      LOG.warn("There is no such call in the cache for " + event);
    } else if (!call.setAnsweredByUser(event.getUsername())) {
      LOG.warn("Could not set answered by user flag for " + call + " and user " + event.getUsername());
    } else {
      call.getCdr().setCallHandlingStartTime(event.getDateReceived());
      call.getCampaign().incrementInCallsSize(call);

      if(call.getCampaign().isPredictiveCampaign() && call.getCampaign().getPredictiveStrategy() == Constants.PREDICTIVE_STRATEGY.ALPHA)
        dynamicBoostManager.onCallAnsweredByAgent(call);

        //IN_CALL STATE
      if(call.isBetaPredictive()) {
        predictiveBetaCallManager.updateCallState(call,CallType.IN_CALL,true);
        predictiveBetaCallManager.updateCallState(call,CallType.ACTIVE,false);
      }

      saveCdrOnAnswer(call, event.getDateReceived(), "agent");

      publishCallAgentAnswerEvent(event, call);
      LOG.info("Call is answered by agent: " + call);
    }
  }

  private void saveCdrOnAnswer(Call call, Date eventDate, String answerType) {
    try {
      CDR cdr = prepareCdrForAnswerSave(call, eventDate);
      if (cdr != null) {
        cdrService.saveOrUpdate(cdr);
      }
    } catch (Exception e) {
      LOG.error("Cannot update CDR on " + answerType + " answer for call: " + call, e);
    }
  }

  private CDR prepareCdrForAnswerSave(Call call, Date eventDate) {
    CDR cdr = call.getCdr();
    if (cdr == null || cdrService == null) {
      return null;
    }
    if (StringUtils.isEmpty(cdr.getInteractionId())) {
      cdr.setInteractionId(call.getId());
    }
    if (cdr.getId() == null) {
      CDR persistedCdr = findPersistedCdr(call, cdr);
      if (persistedCdr != null) {
        copyAnswerTimestamps(persistedCdr, cdr);
        call.setCdr(persistedCdr);
        return persistedCdr;
      }
      populateInitialCdrFieldsForAnswer(call, cdr, eventDate);
    }
    return cdr;
  }

  /**
   * FIX-GLB90048674 (Faz 3, Scope A): persisted-CDR lookup'ı (getByInteractionId SELECT'i) çağrı
   * yaşam döngüsü başına EN FAZLA BİR kez yapılır. CDR id bilindiğinde çağıran zaten bu metoda
   * girmez (id!=null dalı SELECT'i atlar). Burada ek olarak {@code call} üzerinde tek-atış bayrağı
   * tutulur: ilk lookup BOŞ dönse (ve {@code saveOrUpdate} id atamasa) bile sonraki answer
   * event'lerinde SELECT TEKRARLANMAZ. Pür round-trip azaltımı — yazım sırası/kayıpsızlığı
   * etkilenmez (yalnız bir READ elenir). Decorator-local; paylaşılan DAO/CDRService sözleşmesi
   * değişmez.
   */
  private CDR findPersistedCdr(Call call, CDR cdr) {
    if (StringUtils.isEmpty(cdr.getInteractionId())) {
      return null;
    }
    if (!call.markCdrPersistLookupAttempted()) {
      // Lookup bu çağrı için zaten yapıldı (ve id hâlâ null); redundant SELECT'i atla.
      return null;
    }
    return cdrService.getByInteractionId(cdr.getInteractionId());
  }

  private void copyAnswerTimestamps(CDR target, CDR source) {
    if (source.getCallAnswerTime() != null) {
      target.setCallAnswerTime(source.getCallAnswerTime());
    }
    if (source.getCallHandlingStartTime() != null) {
      target.setCallHandlingStartTime(source.getCallHandlingStartTime());
    }
    if (target.getCallDialTime() == null && source.getCallDialTime() != null) {
      target.setCallDialTime(source.getCallDialTime());
    }
    if (target.getCallStartTime() == null && source.getCallStartTime() != null) {
      target.setCallStartTime(source.getCallStartTime());
    }
  }

  private void populateInitialCdrFieldsForAnswer(Call call, CDR cdr, Date eventDate) {
    cdr.setServer(this.server);
    if (cdr.getCallEndTime() == null) {
      cdr.setCallEndTime(resolveInitialCdrEndTime(cdr, eventDate));
    }
    cdr.setRcSequence(call.getResultCodeSequence());
    populateCdrPhoneNumberFields(call, cdr);
  }

  private Date resolveInitialCdrEndTime(CDR cdr, Date eventDate) {
    if (cdr.getCallDialTime() != null) {
      return cdr.getCallDialTime();
    }
    return eventDate != null ? eventDate : new Date();
  }

  private void populateCdrPhoneNumberFields(Call call, CDR cdr) {
    try {
      PhoneNumberColumn actualPhoneNumberColumn = call.getActualPhoneNumberColumn();
      if (actualPhoneNumberColumn == null) {
        LOG.warn("Cannot populate initial answer CDR phone fields, actualPhoneNumberColumn is null for call: " + call);
        return;
      }
      int phoneNumberColumnOrder = call.getCampaign().getPhoneNumberColumnOrder(actualPhoneNumberColumn);
      cdr.setContactNumberOrder(phoneNumberColumnOrder);
      cdr.setContactNumberColumn(actualPhoneNumberColumn.getColNumber());
    } catch (Exception e) {
      LOG.error("Cannot populate initial answer CDR phone fields for call: " + call, e);
    }
  }

  private void publishCallAgentAnswerEvent(AgentPredictiveAnswerEvent event, Call call) {
    String username = call.getUser() != null ? call.getUser().getUsername() : null;
    connectionComposition.getDialerFacade().getCallEventPublisher().publishCallAgentAnswerEvent(call.getId(),event.getDateReceived(),  username);
  }


  /**
   * Event being thrown when call finished
   * @param event the AMI event to handle
   */
  private void handleDialerHangup(DialerHangupEvent event) {
    if (StringUtils.isEmpty(event.getOriginId()))
      return;

    Call call = getCallForHangup(event.getOriginId());

    if (call == null) {
      // Bu dal eskiden tamamen sessizdi: hangup'ın hiç gelmediği durum ile gelip de çağrıyı
      // cache'te bulamadığı durum loglardan ayırt edilemiyordu. Teşhis için gereklidir.
      LOG.warn("Hangup event arrived for unknown or already cleared call " + event.getOriginId()
          + " (cause=" + event.getHangupCause() + ")");
      return;
    }

    if (call.getCampaign().isPreviewCampaign() && call.getUser() != null && call.getAnswerDate() != null) {
      connectionComposition.getAcdEngine().updateUserLastCallEndDate(call.getUser(), new Date());
    }
    if (Constants.CAMPAIGN_TYPE.PROGRESSIVE_RESERVED.equals(call.getCampaign().getType())) {
      Optional.of(call)
          .map(Call::getUser)
          .map(User::getStatus)
          .map(Status::getId)
          .filter(Constants.SYSTEM_STATUS.AVAILABLE.getId()::equals)
          .ifPresent((statusId) -> connectionComposition.getRedisUtil().publishUserPauseMessage(new UserPauseMessage(call.getUser().getId(),
              false)));
    }
    if (shouldFinalizeOnHangup(call)) {
      int hangupCause = Integer.parseInt(event.getHangupCause());
      TelephonyResultCode telephonyResultCode = resolveHangupTelephonyResultCode(call, event);
      telephonyResultCode = applyCustomerHangupBeforeAgentConnectionOverride(call, telephonyResultCode);
      // Cause 16 (Normal Clearing) hiç cevaplanmamış bir çağrıda "bağlandı" anlamına gelmez;
      // mevcut RC 907'yi RC 10 ile değiştirmek çağrıyı başarılı göstermiş olurdu. Böyle bir
      // vakada 907 KORUNUR ve durum WARN'lanır. Çağrı finalize edilmeye devam eder (erken
      // return slot ve sayaç sızdırırdı); yalnız TRC yazımı atlanır.
      if (!isUnansweredNormalClearingOverOriginateError(call, hangupCause, telephonyResultCode)) {
        call.setTelephonyResultCode(telephonyResultCode);
      }

      if (hangupCause == Constants.HANGUP_CAUSE_NO_CIRCUIT_AVAILABLE && tryFctRetryWithNextCallerId(call)) {
        LOG.info("FCT routing list retry continued, skipping standard hangup result for: " + call);
        return;
      }

      boolean shouldProcessCallResult = shouldProcessCallResultOnHangup(call, telephonyResultCode);
      if (shouldProcessCallResult) {
        processCallResult(call, event.getDateReceived());
      } else {
        persistHangupSnapshotForDelayedResult(call, event.getDateReceived());
        // Atomic transfer: webphone subscriber the call'ı bulabilsin diye `calls`
        // cache'ten invalidate olmadan ÖNCE awaiting cache'e konur. clearCall,
        // call.isAwaitingUserResultCode() iken awaiting cache'i invalidate etmeyerek
        // aradaki yarış penceresini kapatır.
        call.markAwaitingUserResultCode();
        awaitingUserResultCodeCalls.put(call.getId(), call);
        scheduleAwaitingUserResultCodeFinalize(call);
        releaseTelephonySlotIfNeeded(call, "hangup-waiting-user-result");
      }
      connectionComposition.getDialerFacade().getCallEventPublisher().publishCallEndEvent(call.getId(), event.getDateReceived());
     // connectionComposition.getDialerFacade().getInteractionNotifierPublisher().publishDialerCallReport(call);
      LOG.info("Hangup for: " + call + ", " + event);
    }
  }

  private Call getCallForHangup(String originId) {
    return ObjectUtils.firstNonNull(calls.getIfPresent(originId), orphanedCalls.getIfPresent(originId),
        recoverableConnectionLossCalls.getIfPresent(originId), awaitingUserResultCodeCalls.getIfPresent(originId));
  }

  /**
   * Hangup'ın finalize bloğuna girip girmeyeceğine karar verir.
   *
   * <p>FIX-320173 skip-guard'ı: telephonyResultCode in-memory set edilmiş ama pd_cdr'a hiç
   * persist edilmemişse (örn. bir finalize yolu persist edemeden kesildiyse veya persist
   * etmeyi unutan bir akış varsa) hangup son şanstır; sessizce atlamak satırı kalıcı
   * telephony_rc=NULL bırakır. Kontrol yalnız in-memory CDR alanı üzerindedir; hot path'e
   * DB erişimi eklenmez.</p>
   *
   * <p>907 kontrolü bilinçli olarak {@code hasErrorCode} İÇİNDE DEĞİL burada yapılır:
   * {@code hasErrorCode}'un ikinci çağıranı {@link #shouldProcessCallResultOnHangup} olup, orada
   * 907'yi "hata" saymak preview olmayan + cevaplanmış bir çağrıyı awaiting-user-result kolundan
   * çıkarıp doğrudan finalize eder ve agent'ın disposition'ını sessizce kaybettirirdi. Kapsam bu
   * yüzden yalnız bu metotla sınırlıdır (gerekçe: {@link Constants#HANGUP_OVERWRITABLE_DIALER_ERROR_CODES}).</p>
   */
  private boolean shouldFinalizeOnHangup(Call call) {
    TelephonyResultCode current = call.getTelephonyResultCode();
    if (current == null || hasErrorCode(call)
        || Constants.HANGUP_OVERWRITABLE_DIALER_ERROR_CODES.contains(current.getCode())) {
      return true;
    }
    if (call.getCdr() != null && call.getCdr().getTelephonyRC() == null) {
      LOG.warn("Hangup found terminal result code that was never persisted to CDR; finalizing now for " + call);
      return true;
    }
    return false;
  }

  /**
   * Hangup cause'unu telephony result code'a çözer (FIX-320173 savunma katmanı):
   * parse edilemeyen veya pd_telephony_rc'de eşleşmesi olmayan cause'larda finalize'ı null TRC
   * ile zehirlemek yerine {@code CALL_EXPIRED_CLEARING} (901) fallback'i kullanılır ve cause
   * değeri WARN ile raporlanır.
   */
  private TelephonyResultCode resolveHangupTelephonyResultCode(Call call, DialerHangupEvent event) {
    Integer hangupCause = parseHangupCause(event);
    Integer causeKey = call.isAbandoned() ? Integer.valueOf(Constants.TELEPHONY_RC_ABANDONED_TIMEOUT) : hangupCause;
    TelephonyResultCode resolved = causeKey != null ? ituToTelephonyResultCodeRelations.get(causeKey) : null;
    if (resolved == null) {
      resolved = ituToTelephonyResultCodeRelations.get(Constants.ERROR_RESULT_CODES.CALL_EXPIRED_CLEARING.getCode());
      LOG.warn("Hangup cause '" + event.getHangupCause() + "' is not mapped to a telephony result code; falling back to code "
          + Constants.ERROR_RESULT_CODES.CALL_EXPIRED_CLEARING.getCode() + " for " + call);
    }
    return resolved;
  }

  /**
   * Savunma kapısı: {@code cause 16} (Normal Clearing) + hiç cevaplanmamış çağrı + mevcut RC 907.
   *
   * <p>Dialplan başarılı (bağlanmış) çağrılarda cause 16 gönderir; {@code answerDate == null}
   * iken 16 gelmesi beklenen bir durum DEĞİLDİR. Böyle bir vakada RC 907'yi cause 16'nın
   * eşleniği olan RC 10 ("Normal call clearing") ile değiştirmek, hiç açılmamış ve ses kaydı
   * olmayan bir çağrıyı <b>başarılı</b> gösterirdi; ayrıca RC 10 retry/attempt/Gamma
   * zincirlerinde "cevaplandı" sinyali olarak okunur
   * ({@code RetryStrategy.isCallAnswered}, {@code CallAttemptPolicy}, Gamma {@code == 10}).
   * Bu yüzden mevcut 907 korunur.</p>
   *
   * <p>Kapsam bilinçli olarak dardır: yalnız mevcut RC 907 ise devreye girer. Aksi halde
   * ({@code telephonyResultCode == null} olan normal bir çağrı gibi) hangup'ın kendi sonucunu
   * yazması engellenmiş olurdu.</p>
   */
  private boolean isUnansweredNormalClearingOverOriginateError(Call call, int hangupCause,
      TelephonyResultCode resolvedFromCause) {
    if (hangupCause != Constants.TELEPHONY_RC_NORMAL_CLEARING || call.getAnswerDate() != null) {
      return false;
    }
    TelephonyResultCode current = call.getTelephonyResultCode();
    if (current == null || current.getCode() != Constants.TELEPHONY_RC_ORIGINATE_ACTION_ERROR) {
      return false;
    }
    LOG.warn("Hangup cause 16 (normal clearing) arrived for a call that was never answered; keeping"
        + " originate action error result instead of reporting it as answered. Resolved-from-cause was "
        + (resolvedFromCause != null ? resolvedFromCause.getId() : null) + " for " + call);
    return true;
  }

  private Integer parseHangupCause(DialerHangupEvent event) {
    try {
      return Integer.valueOf(event.getHangupCause());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private void awaitUserResultCodeAfterHangup(Call call) {
    if (call == null || StringUtils.isEmpty(call.getId())) {
      return;
    }
    calls.invalidate(call.getId());
    recoverableConnectionLossCalls.invalidate(call.getId());
    awaitingUserResultCodeCalls.put(call.getId(), call);
  }

  /**
   * FCT routing aktif kampanyalarda cause 34 (No circuit/channel available) hangup'ı geldiğinde
   * cursor'da bir sonraki caller ID varsa aynı Call için yeni Originate atar. Standart
   * processCallResult akışı ve retry strategy çağrılmaz; counter'lar hangup tarafında
   * decrement, originateCall tarafında re-increment ile dengelenir.
   *
   * @return true: list-retry başlatıldı (caller akışı durdurulmalı). false: cursor yok veya exhaust.
   */
  private boolean tryFctRetryWithNextCallerId(Call call) {
    RouteDetailsCursor cursor = call.getRouteDetailsCursor();
    if (cursor == null) {
      return false;
    }
    FctChannelGate gate = fctChannelGate();
    // In-call retry sınırı (fct.channel.list.retry.max). Sınır aşılsa da "bu FCT dolu" bilgisi diğer
    // çağrılar için değerli olduğundan release + cooldown yine yapılır; ardından FCT listesi
    // tükenmiş sayılır ve sabit hat fallback'i denenir.
    if (gate != null && !gate.isListRetryAllowed(call.getFctListRetryCount())) {
      gate.releaseAndMarkFull(call);
      return tryFixedLineFallback(call, gate);
    }
    // Kapasite gate'i açıkken: cause 34 veren numaranın rezervasyonu bırakılır, numara cooldown'a
    // alınır (kapasite sayımının göremediği trafiği de telafi eder) ve sıradaki UYGUN entry rezerve
    // edilir. Gate yoksa davranış eskisi gibi sıradaki entry'ye geçmektir.
    if (!advanceToNextFctChannel(call, cursor)) {
      return tryFixedLineFallback(call, gate);
    }
    call.incrementFctListRetryCount();
    if (gate != null && gate.getRegistry() != null) {
      gate.getRegistry().recordReoriginate(call.getCallerId(), call.getId());
    }

    prepareCallForReOrigination(call);

    // Kısa ve tek satır: teşhis için callId, liste konumu ve yeni caller ID yeterli. QSS'in
    // type/attempts alanları virgüllü çok değerli geldiği (attempts fallback'i 0..99) ve bu satır
    // her cause 34 retry'ında basıldığı için payload log'a alınmaz.
    LOG.info("FCT routing list retry: " + call.getId() + " " + cursor.position() + "/" + cursor.size()
        + " " + call.getCallerId());

    return originateCall(call);
  }

  /**
   * FCT listesi tükendiğinde (veya in-call retry sınırı dolduğunda) kampanyanın sabit hat caller
   * ID'siyle son bir kez originate dener.
   *
   * <p>Sabit hat FCT kapasitesiyle ilgili olmadığı için registry'de rezervasyon TUTULMAZ ve bütçesi
   * {@code fct.fixed.line.retry.max} ile ayrıdır; {@code fct.channel.list.retry.max = 0} iken bile
   * çalışır. Kaynak {@code Campaign.callerId}'dir; boşsa fallback kapalıdır ve mevcut TRC 25 yoluna
   * dönülür.
   *
   * @return true: sabit hatla yeniden originate edildi. false: fallback yok/tükendi — çağıran
   *         standart {@code processCallResult} zincirine düşmelidir.
   */
  private boolean tryFixedLineFallback(Call call, FctChannelGate gate) {
    if (gate == null) {
      return false;
    }
    String fixedLine = gate.nextFixedLineCallerId(call);
    if (fixedLine == null) {
      return false;
    }
    call.setCallerId(fixedLine);
    // Sabit hat QSS'te tanımlı değil, dolayısıyla trunk'ı da yok. Önceki FCT'den kalan değer
    // taşınırsa çağrı yanlış trunk'tan çıkar (channel'ı sıfırlamayı unutunca yaşadığımız
    // bayat-değer hatasının aynısı). Temizlenince dialplan varsayılan trunk'a düşer.
    call.setFctTrunk(null);
    int attempt = call.incrementFixedLineAttemptCount();
    if (gate.getRegistry() != null) {
      gate.getRegistry().recordFixedLineFallback(fixedLine, call.getId());
    }
    prepareCallForReOrigination(call);

    LOG.info("FCT exhausted, fixed line fallback: " + call.getId() + " " + attempt + "/"
        + gate.getFixedLineRetryMax() + " " + fixedLine);

    return originateCall(call);
  }

  /**
   * Aynı {@code Call} nesnesi yeniden originate edilmeden önceki ortak temizlik. Hem FCT list-retry
   * hem sabit hat fallback bu yolu kullanır; counter'lar hangup tarafında decrement, originateCall
   * tarafında re-increment ile dengelenir.
   *
   * <p><b>originationDate BİLİNÇLİ OLARAK sıfırlanmaz</b> (Faz 2, 30.09.2026). Alanın tek yazarı
   * {@code CallOriginator.processOrigination}'dır ve re-originate oradan geçmez; eskiden burada
   * null'landığı için bir daha doldurulmuyor, {@code Call.getDurationFromOrigination()} de null
   * {@code originationDate} gördüğünde hiç güncellenmemiş alanı (0) döndürüyordu. Sonuç
   * {@code Campaign.incrementTotalCallDuration(0)} ve interaction-notifier payload'ında
   * {@code durationFromOrigination = 0} idi. İlk bacağın değeri korunarak süre gerçekten
   * ölçülür; N bacak tek attempt olduğu için ölçümün ilk originate'ten başlaması doğru tanımdır.
   */
  private void prepareCallForReOrigination(Call call) {
    Campaign campaign = call.getCampaign();
    campaign.removeCallInProgress(call);
    if (call.getStartDate() != null && call.getAnswerDate() == null) {
      campaign.decrementRingingCalls(call);
    }
    calls.invalidate(call.getId());

    call.setTelephonyResultCode(null);
    call.setStartDate(null);
    // channel de sıfırlanmalı: aynı Call nesnesi yeniden originate ediliyor ve
    // isChannelEstablished (channel != null || startDate != null) originate-fail yolunda
    // KARAR VERİCİDİR. Bayat channel kalırsa 2. denemenin gerçek originate reddi (kanal hiç
    // kurulmamışken) yanlışlıkla "kanal kurulmuş" sayılıp grace period'a girer ve RC 907
    // gereksiz yere 10 saniye gecikir.
    call.setChannel(null);
  }

  /**
   * cause 34 sonrası sıradaki FCT'ye geçer. Gate bileşeni varsa release + cooldown + kapasite
   * rezervasyonu birlikte yapılır; yoksa (Spring dışında kurulan testler) eski cursor davranışı
   * korunur.
   */
  private boolean advanceToNextFctChannel(Call call, RouteDetailsCursor cursor) {
    FctChannelGate gate = fctChannelGate();
    if (gate != null) {
      return gate.reserveNextAfterNoCircuit(call);
    }
    if (!cursor.hasNext()) {
      return false;
    }
    RouteDetailEntry next = cursor.next();
    if (next == null || StringUtils.isEmpty(next.getPhoneNumber())) {
      return false;
    }
    call.setCallerId(next.getPhoneNumber());
    return true;
  }

  private FctChannelGate fctChannelGate() {
    return connectionComposition == null ? null : connectionComposition.getFctChannelGate();
  }

  /** Çağrının tuttuğu FCT kanalını bırakır. Idempotenttir; birden fazla yoldan çağrılabilir. */
  private void releaseFctChannel(Call call) {
    FctChannelGate gate = fctChannelGate();
    if (gate != null) {
      gate.release(call);
    }
  }

  private boolean shouldProcessCallResultOnHangup(Call call, TelephonyResultCode telephonyResultCode) {
    return (call.getAnswerDate() == null && !call.getCampaign().isPreviewCampaign())
        || !callIsWaitingForUserResultCode(call)
        || (hasErrorCode(call) && !call.getCampaign().isPreviewCampaign())
        || call.isAbandoned()
        || call.isOrphaned()
        || isAmdFamilyTelephonyResultCode(telephonyResultCode)
        || isCustomerHangupBeforeAgentConnectionTrc(telephonyResultCode);
  }

  private TelephonyResultCode applyCustomerHangupBeforeAgentConnectionOverride(Call call, TelephonyResultCode resolvedFromCause) {
    if (resolvedFromCause == null || call.getCdr() == null) {
      return resolvedFromCause;
    }
    if (call.isAbandoned()) {
      return resolvedFromCause;
    }
    if (call.getAnswerDate() == null) {
      return resolvedFromCause;
    }
    if (call.getCdr().getCallHandlingStartTime() != null) {
      return resolvedFromCause;
    }
    if (Long.valueOf(Constants.TELEPHONY_RC_ANSWERED_BUT_NO_MT_CONNECTION).equals(resolvedFromCause.getId())) {
      return resolvedFromCause;
    }
    if (isAmdFamilyTelephonyResultCode(resolvedFromCause)) {
      return resolvedFromCause;
    }
    TelephonyResultCode trc84 = ituToTelephonyResultCodeRelations.get(Constants.TELEPHONY_RC_CUSTOMER_HANGUP_BEFORE_AGENT);
    if (trc84 == null) {
      LOG.warn("Telephony result code with code {} is not defined in pd_telephony_rc; leaving mapped cause {}",
          Constants.TELEPHONY_RC_CUSTOMER_HANGUP_BEFORE_AGENT, resolvedFromCause.getId());
      return resolvedFromCause;
    }
    return trc84;
  }

  private void persistHangupSnapshotForDelayedResult(Call call, Date completeDate) {
    CDR cdr = call.getCdr();
    if (cdr == null || cdr.getId() == null || call.getTelephonyResultCode() == null || cdrService == null) {
      // FIX-320173: sessiz skip kalıcı telephony_rc=NULL satır bırakabildiği için artık WARN'lı.
      LOG.warn("Skipping delayed hangup snapshot persist (cdr/id/telephonyRC/service missing) for " + call);
      return;
    }
    try {
      cdr.setUserResultCodeId(call.getUserResultCode() != null ? call.getUserResultCode().getId() : null);
      UserPopulationEvaluator.populateCdrUserOwnership(call);
      cdr.setServer(this.server);
      cdr.setTelephonyRC(call.getTelephonyResultCode());
      cdr.setCallEndTime(completeDate);
      cdr.setRcSequence(call.getResultCodeSequence());
      cdr.setFilter(call.getFilter());
      cdr.setAbandoned(call.isAbandoned());
      cdrService.saveOrUpdate(cdr);
    } catch (Exception e) {
      LOG.error("Cannot save delayed hangup snapshot for " + cdr, e);
    }
  }

  /**
   * Check is call has was finished with telephony error code
   * @param call the call to check
   * @return true if the call was finished with error, otherwise false
   */
  private boolean hasErrorCode(Call call) {
    return call.getTelephonyResultCode() != null && ASTERISK_ERROR_CODES.contains(call.getTelephonyResultCode().getCode());
  }

  /**
   * Check if we have to wait for user result code to process call
   * @param call the call to evaluate
   * @return true if call is waiting for result code, else false
   */
  private boolean callIsWaitingForUserResultCode(Call call) {
    return call.getUserResultCode() == null && call.getUser() != null && call.getUser().getGroup() != null
        && !CollectionUtils.isEmpty(call.getUser().getGroup().getResultCodes());
  }

  private static boolean isCustomerHangupBeforeAgentConnectionTrc(TelephonyResultCode trc) {
    return trc != null && Long.valueOf(Constants.TELEPHONY_RC_ANSWERED_BUT_NO_MT_CONNECTION).equals(trc.getId());
  }

  /**
   * FEAT-325470: AMD ailesi (900 AMD, 113 bad number, 114 not reached, 115 unknown tone) hangup
   * yolunda tek muamele görür: awaiting-URC'ye düşmez, TRC 84 override'ından muaftır ve
   * predictive'de NO_CONNECTION sayılır. Karar {@code pd_telephony_rc.code} ile verilir; eski kod
   * yalnız id 71'i (code 900) tanıyordu ve id ortamlar arası güvenilir değildir.
   */
  private static boolean isAmdFamilyTelephonyResultCode(TelephonyResultCode trc) {
    return trc != null && Constants.isAmdFamilyTelephonyResultCode(trc.getCode());
  }

  /**
   * Process call; manage retries, unary operations on campaign, clear call and
   * insert result to db
   * @param call the call to process
   * @param completeDate the call completion date
   */
  public void processCallResult(Call call, Date completeDate) {
    if (call == null) {
      return;
    }
    if (!call.markBusinessResultFinalized()) {
      LOG.info("Business result already finalized for " + call);
      return;
    }
    // Awaiting transfer kilidi sadece hangup-waiting-user-result yolunda anlamlıdır;
    // call burada finalize edildiği için clearCall awaiting cache'i serbestçe invalidate edebilmeli.
    call.clearAwaitingUserResultCode();
    if (call.getUser() != null)
      returnUserToACDEngine(call);
    call.incrementResultCodeSequence();
    updateCallCampaignIfNeeded(call);

    // FIX-320173 persist-first: pd_cdr finalize persist'i tüm yan etkilerden ÖNCE garanti edilir.
    // Eski sırada persist zincirin en sonundaydı; :820-848 bölgesindeki tek bir exception hem
    // persist'i yutuyor hem de one-shot finalize bayrağı + erken cache temizliği nedeniyle
    // telephony_rc kalıcı NULL kalıyordu. Persist başarısız olursa bayrak geri alınır; çağrı
    // cache'lerden henüz çıkarılmadığı için sonraki hangup bacağı / expiry / RC mesajı yeniden dener.
    if (!persistFinalCdr(call, completeDate, false)) {
      call.rollbackBusinessResultFinalized();
      LOG.error("Final CDR persist failed; finalize rolled back for retry of " + call);
      return;
    }

    runCallResultSideEffects(call, completeDate);
  }

  /**
   * Finalize yan etkileri persist'ten ve birbirinden izole çalışır (FIX-320173): tek bir hata
   * zinciri koparıp kalan adımları sessizce yutamaz. Her grup kendi try/catch'inde, hata
   * call kimliğiyle ERROR loglanır.
   */
  private void runCallResultSideEffects(Call call, Date completeDate) {
    try {
      CallAttemptPolicy.restoreForNonAttemptResultIfNeeded(call);
      CallAttemptPolicy.incrementForSuccessfulCompletionIfNeeded(call);
      clearPreviousRetryDateForOriginatedCall(call);
      if (call.getCallbackDate() == null)
        manageRetries(call, completeDate);
    } catch (Exception e) {
      LOG.error("Retry/attempt finalize side effect failed for " + call, e);
    }

    try {
      releaseTelephonySlotIfNeeded(call, "process-call-result");
      doCampaignIncrementationsOnHangup(call);
    } catch (Exception e) {
      LOG.error("Slot/counter finalize side effect failed for " + call, e);
    }

    dbOperations.insertCallResultToDB(call);

    try {
      checkStopCondition(call.getCampaign());
    } catch (Exception e) {
      LOG.error("Stop condition check failed on finalize for " + call, e);
    }

    try {
      if (call.getAnswerDate() == null
          || isAmdFamilyTelephonyResultCode(call.getTelephonyResultCode())) {
        //No Connection Call
        if(call.isBetaPredictive()) {
          logger.info("Predictive Beta - Call is in NO_CONNECTION state, incrementing no connection size:  callId: {} ", call.getId());
          predictiveBetaCallManager.updateCallState(call, CallType.NO_CONNECTION,true);
        } else if (call.getCampaign().isPredictiveCampaign() && call.getCampaign().getPredictiveStrategy() == Constants.PREDICTIVE_STRATEGY.ALPHA) {
          dynamicBoostManager.onCallFailed(call);
        }
      }
    } catch (Exception e) {
      LOG.error("Predictive finalize side effect failed for " + call, e);
    }

    if(call.isGammaPredictive()){
      notifyGammaOnCallEnded(call, completeDate);
    }
  }

  /**
   * pd_cdr finalize persist sözleşmesi (FIX-320173): telephonyResultCode'u terminal olarak set
   * eden her yol pd_cdr'ı bu metotla persist eder. Persist, finalize yan etkilerinden bağımsız
   * çalışır; başarısızlıkta false döner ve çağıran telafi kararını verir.
   *
   * @param preserveUserResultCode true ise (logout-orphan finalize'ı) in-memory user result
   *        code, {@link UserPopulationEvaluator} kuralları tarafından null'lansa bile korunur;
   *        kampanya result tablosu orphan yolunda URC'yi zaten yazdığı için pd_cdr ile
   *        tutarlılık sağlanır (FIX-320173 Açık Soru #2 kararı).
   */
  public boolean persistFinalCdr(Call call, Date completeDate, boolean preserveUserResultCode) {
    if (call == null) {
      return false;
    }
    CDR cdr = call.getCdr();
    if (cdr == null || cdrService == null) {
      LOG.warn("Cannot persist final CDR; cdr or cdrService is missing for " + call);
      return false;
    }
    if (call.getTelephonyResultCode() == null) {
      LOG.warn("Persisting final CDR with null telephony result code for " + call);
    }
    Long preservedUserResultCodeId = call.getUserResultCode() != null
        ? call.getUserResultCode().getId()
        : cdr.getUserResultCodeId();
    // FIX-GLB90060295: in-memory CDR'da zaten dolu olan URC, call.userResultCode null olduğu için
    // NULL'a düşürülmez. Aksi hâlde "bilgim yok" durumu "değer yok" diye persist edilip başka bir
    // yolun (webphone durable fallback) yazdığı disposition eziliyordu. Başarısız çağrılarda URC'yi
    // bastırma yetkisi aşağıdaki populateCdrUserOwnership çağrısında kalır.
    cdr.setUserResultCodeId(preservedUserResultCodeId);

    // Apply all CDR user ownership fields in a single operation
    UserPopulationEvaluator.populateCdrUserOwnership(call);
    if (preserveUserResultCode && preservedUserResultCodeId != null) {
      cdr.setUserResultCodeId(preservedUserResultCodeId);
    }
    cdr.setServer(this.server);
    cdr.setTelephonyRC(call.getTelephonyResultCode());
    cdr.setCallEndTime(completeDate);
    cdr.setRcSequence(call.getResultCodeSequence());
    cdr.setFilter(call.getFilter());
    cdr.setAbandoned(call.isAbandoned());
    try {
      PhoneNumberColumn actualPhoneNumberColumn = call.getActualPhoneNumberColumn();
      int phoneNumberColumnOrder = call.getCampaign().getPhoneNumberColumnOrder(actualPhoneNumberColumn);
      cdr.setContactNumberOrder(phoneNumberColumnOrder);
      cdr.setContactNumberColumn(actualPhoneNumberColumn.getColNumber());
    } catch (Exception e) {
      // Telefon kolonu alanları initial CDR save'inde zaten persist edildi; çözümleme hatası
      // finalize persist'ini engellememeli (FIX-320173 — eski akışta bu satırlar save'i kesiyordu).
      LOG.warn("Cannot resolve phone column fields on final CDR persist; keeping persisted values for " + call, e);
    }
    try {
      cdrService.saveOrUpdate(cdr);
      return true;
    } catch (Exception e) {
      LOG.error("Cannot save " + cdr, e);
      return false;
    }
  }

  /**
   * MADDE 1 (Gamma Plus) — müşteri cevabı geldiği an AR penceresini besler.
   *
   * <p>Manager tarafı bayrağı kendisi kontrol eder; bayrak kapalıysa metot hiçbir şey yapmaz.
   * {@code notifyGammaOnCallEnded} ile aynı savunma: gamma tarafındaki bir hata AMI answer
   * akışını kesmemeli.
   */
  private void notifyGammaOnCallAnswered(Call call) {
    // notifyGammaOnCallEnded ile aynı kapı: gamma dışı kampanyalar facade'a hiç uğramasın.
    if (!call.isGammaPredictive()) return;
    try {
      getConnectionComposition()
              .getDialerFacade()
              .getPredictiveGammaCallManager()
              .onCallAnswered(call);
    } catch (Exception ex) {
      LOG.error("GAMMA onCallAnswered notify failed for " + call, ex);
    }
  }

  private void notifyGammaOnCallEnded(Call call, Date completeDate) {
    try {
      boolean answered = call.getAnswerDate() != null;

      Date dialTime = (call.getCdr() != null) ? call.getCdr().getCallDialTime() : null;
      Date handlingStartTime = (call.getCdr() != null) ? call.getCdr().getCallHandlingStartTime() : null;
      Date endTime  = (call.getCdr() != null) ? call.getCdr().getCallEndTime()  : completeDate;

      Long dialMs = (dialTime != null) ? dialTime.getTime() : null;
      Long handlingStartMs = (handlingStartTime != null) ? handlingStartTime.getTime() : null;
      Long endMs  = (endTime  != null) ? endTime.getTime()  : null;

      Long answerMs = null;
      if (answered) {
        answerMs = Utils.convertDoubleToLongDate(call.getAnswerDate());
      }

      int talkSec = 0;
      if (answered && endMs != null && handlingStartMs != null) {
        talkSec = (int) Math.max(0L, (endMs - handlingStartMs) / 1000L);
      }

      int ringSec = 0;
      if (dialMs != null) {
        long ringEnd = answered ? answerMs : (endMs != null ? endMs : dialMs);
        ringSec = (int) Math.max(0L, (ringEnd - dialMs) / 1000L);
      }

      getConnectionComposition()
              .getDialerFacade()
              .getPredictiveGammaCallManager()
              .onCallEnded(call, talkSec, ringSec);

    } catch (Exception ex) {
      LOG.error("GAMMA onCallEnded notify failed for " + call, ex);
    }
  }

  public void insertCallResultToDB(Call call) {
    dbOperations.insertCallResultToDB(call);
  }

  private void checkStopCondition(Campaign campaign) {
    if (connectionComposition.getStopConditionChecker().isStopConditionReached(campaign)) {
      LOG.info(campaign + " has reached stop condition", LOG_MODULE.CAMPAIGN, campaign);
      connectionComposition.getCampaignService().actionAsync(campaign.getId(), Constants.CAMPAIGN_ACTION.STOP_REGULAR, Constants.CAMPAIGN_STOP_TYPE.STOP_CONDITION);
    }
  }

  /**
   * Clear retry date in order to not call again if this retry already performed
   * @param call the call to handle
   */
  private void clearPreviousRetryDateForOriginatedCall(Call call) {
    if (StringUtils.isNotEmpty(call.getChannel())) {
      call.setRetryDate(null);
    }
  }

  /**
   * Manage retries for call
   * @param call the call to handle
   * @param completeDate the date of the call completion
   */
  private void manageRetries(Call call, Date completeDate) {
    if (!isRetryApplicable(call) || !isResultCodeAllowsRetries(call)) {
      connectionComposition.getCallCleaner().completeCall(call, completeDate);
      call.getCampaign().incrementCompletedContacts();
      return;
    }

    RetryStrategy retryStrategy = strategyFactory.createStrategy(call.getCampaign().getRetryStrategy());
    retryStrategy.createRetry(call);
  }

  private boolean isRetryApplicable(Call call) {
    return call.getCampaign().getRetryScheduleEnabled() && call.getCampaign().getRetryStrategy() != null;
  }

  private boolean isResultCodeAllowsRetries(Call call) {
    return call.getUserResultCode() == null || !call.getUserResultCode().isFinalStatus() || call.getUserResultCode().isDncIdentifier();
  }

  /**
   * When we update running campaign there is need to update campaign of calls
   * which has been originated and has not been finished at the time of update
   * @param call the call to handle
   */
  private void updateCallCampaignIfNeeded(Call call) {
    Campaign currentCallCampaign = call.getCampaign();
    CampaignContext campaignContext = connectionComposition.getDialerFacade().getCampaignContexts().get(currentCallCampaign.getId());
    if (campaignContext == null)
      return;

    Campaign campaignFromContext = campaignContext.getCampaign();

    if (isCampaignUpdated(currentCallCampaign, campaignFromContext)) {
      call.setCampaign(campaignFromContext);
      LOG.info("Campaign was updated during call, new campaign is set to " + call);
    }
  }

  private boolean isCampaignUpdated(Campaign currentCallCampaign, Campaign campaignFromContext) {
    return !(Objects.equals(currentCallCampaign.getVersion(), campaignFromContext.getVersion()));
  }

  /**
   * Incrementations to be done in campaign when a call finishes
   * @param call the finished call
   */
  private void doCampaignIncrementationsOnHangup(Call call) {
    Campaign campaign = call.getCampaign();
    campaign.incrementCallsMade();
    campaign.incrementTotalCallDuration(call.getDurationFromOrigination());

    if (call.getAnswerDate() == null) {
      campaign.incrementNoConnectionCalls();


      if(call.isBetaPredictive()) {
        //NO_CONNECTION && BAD_CONNECTION Call
        if(call.getTelephonyResultCode() != null && call.getTelephonyResultCode().getId() == TELEPHONY_RC_ORIGINATE_ACTION_ERROR ) {
          logger.info("Predictive Beta - Call is in BAD_CONNECTION state, incrementing bad connection size:  callId: {} ", call.getId());
          predictiveBetaCallManager.updateCallState(call,CallType.BAD_CONNECTION,true);
        } else {
          logger.info("Predictive Beta - Call is in NO_CONNECTION state, incrementing no connection size:  callId: {} ", call.getId());
          predictiveBetaCallManager.updateCallState(call,CallType.NO_CONNECTION,true);
        }
      }

    } else {
      campaign.incrementLiveCalls();
      if (!call.isRetry() && !call.isCallback()) {
        campaign.incrementUniqueLiveCalls();
        //Offered Call
        if(call.isBetaPredictive()
                && call.isAnsweredByUser()) {
          logger.info("Predictive Beta - Call is in OFFERED state, incrementing offered call size:  callId: {} ", call.getId());
          logger.info("Predictive Beta - Call is in OFFERED state, decrementing in call size:  callId: {} ", call.getId());
          predictiveBetaCallManager.updateCallState(call,CallType.OFFERED,true);
          predictiveBetaCallManager.updateCallState(call,CallType.IN_CALL,false);
        }
      }
    }

    if (call.getRetryHistory().size() > 1 || (call.getRetryHistory().size() > 0 && call.getRetryDate() == null))
      campaign.incrementRetriesMade();

    if (call.getUserResultCode() != null && call.getUserResultCode().getId() != null) {
      Long userResultCodeId = call.getUserResultCode().getId();
      campaign.incrementUserResultCode(userResultCodeId);
    }
    if (call.getTelephonyResultCode() != null) {
      Long telephonyResultCodeId = call.getTelephonyResultCode().getId();
      campaign.incrementTelephonyResultCode(telephonyResultCodeId);
    }

    LOG.info("Incrementations processed for " + call);
  }

  /**
   * Decrementations to be done in campaign when a call finishes
   * @param call the finished call
   */
  private void doCampaignDecrementationsOnHangup(Call call) {
    Campaign campaign = call.getCampaign();
    campaign.removeCallInProgress(call);

    if (call.getAnswerDate() != null)
      campaign.decrementActiveCalls(call);
    else if (call.getStartDate() != null)
      campaign.decrementRingingCalls(call);

    if(call.isAnsweredByUser() && !call.isTransferred()) {
      // Transferred calls already decremented inCalls in handleNewTransferChannel when the agent
      // left the bridge; skip here to avoid a double decrement (inCalls counter drift).
      campaign.decrementInCallsSize(call);
    }

    if (campaign.getState() == CAMPAIGN_STATE.STOPPING || campaign.getState() == CAMPAIGN_STATE.PAUSED)
      campaign.decrementListRemaining();

    LOG.info("Decrementations processed for " + call);
  }

  private void releaseTelephonySlotIfNeeded(Call call, String reason) {
    clearCall(call);
    // FCT kanal rezervasyonu telefonluk slotla aynı anda bırakılır. release idempotent olduğu için
    // markTelephonySlotReleased guard'ının önünde durması sorun değildir.
    releaseFctChannel(call);
    if (!call.markTelephonySlotReleased()) {
      LOG.info("Telephony slot already released for " + call + " due to " + reason);
      return;
    }
    doCampaignDecrementationsOnHangup(call);
    LOG.info("Telephony slot released for " + call + " due to " + reason);
  }

  /**
   * Remove call from cache and call queue of server
   * @param call the call to clear
   */
  public void clearCall(Call call) {
    call.getCallOriginator().clearCall(call);
    call.getCampaign().clearCall(call);

    boolean isPreview = call.getCampaign().isPreviewCampaign();
    boolean hasUserResultCode = call.getUserResultCode() != null;

    if (!isPreview || hasUserResultCode) {
      calls.invalidate(call.getId());
    }

    recoverableConnectionLossCalls.invalidate(call.getId());
    if (!call.isAwaitingUserResultCode()) {
      awaitingUserResultCodeCalls.invalidate(call.getId());
    }

    CampaignRealTimeRingingManager ringingManager = connectionComposition.getDialerFacade().getCampaignRealTimeRingingManager();
    ringingManager.removeRingingCall(call);
    // Originate fail, exception veya bağlantı kaybı yollarında pending kaydını da temizle.
    // VarSet hiç gelmediyse pending TTL self-healing yapardı; bu çağrı kapanışı garanti eder.
    ringingManager.removePendingRingingCall(call);
    connectionComposition.getCallUpToDateKeeper().unregisterCall(call);
  }

  /**
   * Send hangup request to the PBX for a given call
   * @param call the call that should be terminated
   */
  public void hangupCall(Call call) {
    try {
      HangupAction hangupAction = new HangupAction();
      hangupAction.setChannel(call.getChannel());
      // FIX-GLB90048674 (Faz 2): Hangup da event'siz command bağlantısından gider (event yükünden izole).
      commandConnection.sendAction(hangupAction, null);
      LOG.info("Forceful hangup command sent to Asterisk for " + call);
    } catch (IllegalArgumentException | IllegalStateException | IOException e) {
      LOG.error("Cannot send hangupAction to asterisk for " + call, e);
    }
  }

  private void handleOriginateResponse(OriginateResponseEvent event) {
    if (!event.isSuccess() && StringUtils.isNotEmpty(event.getActionId())
        && (event.getReason() == null || Arrays.stream(ORIGINATE_RESPONSE_SUCCESSFUL_REASONS).noneMatch(r -> r.equals(event.getReason())))) {
      String callId = event.getActionId();
      Date failDate = event.getDateReceived();
      LOG.warn("Origination has been failed for call " + callId + " with reason code '" + event.getReason() + "'");
      handleOriginationFail(callId, failDate);
    }
  }

  /**
   * FIX-GLB90048674 (Faz 1B, Fix #1 — "behavior birebir aynı"): originate-fail işi de artık
   * AMI response thread'i yerine tek-thread'li worker'da koşuyor. Eskiden
   * {@link SendActionCallbackWrapper#onResponse} {@code failHandler.accept(...)} çağrısını
   * {@code try/catch(Exception)} ile sarıp SESSİZCE yutardı. İşi sarmazsak DB-ağır
   * {@code handleOriginationFail} (-> processCallResult) içinden kaçan bir exception worker
   * thread'ini öldürürdü. catch(Exception) ile eski koruyucu davranışı geri kazanırız; tek fark,
   * artık sessizce yutmak yerine LOG'a yazarız.
   */
  private void runOriginationFailTask(String callId, Date failDate) {
    try {
      handleOriginationFail(callId, failDate);
    } catch (Exception e) {
      LOG.error("(" + (server != null ? server.getIp() : "?") + ") Unexpected exception while handling origination fail for call "
          + callId, e);
    }
  }

  /**
   * Handle failed call origination. Return user to idle users (if present) and
   * set proper result code
   * @param callId the callId of the failed call
   * @param failDate the date when call was failed
   */
  private void handleOriginationFail(String callId, Date failDate) {
    LOG.warn("Origination failed for call " + callId);
    Call call = calls.getIfPresent(callId);
    if (call == null) {
      return;
    }

    // FIX: Asterisk OriginateResponse'un Reason alanı çalma sonrası cevapsız/meşgul/iptal
    // durumlarında da 0 gelir; canlıda gözlenen TEK değer budur. Kanal kurulmuşsa bu bir
    // originate reddi DEĞİLDİR: gerçek sonuç hangup cause'undan gelir ve burada RC 907
    // yazmak onu kalıcı olarak ezer. Kanal hiç kurulmamışsa (gerçek originate hatası,
    // ör. 'line too long' -> Response=Error) eski davranış korunur.
    if (isChannelEstablished(call)) {
      LOG.warn("Originate response failure ignored for established call; awaiting real hangup result for " + call);
      scheduleOriginationFailFallback(call, failDate);
      return;
    }

    TelephonyResultCode telephonyResultCode = getItuToTelephonyResultCodeRelations().get(Constants.TELEPHONY_RC_ORIGINATE_ACTION_ERROR);
    call.setTelephonyResultCode(telephonyResultCode);
    processCallResult(call, failDate);
  }

  /**
   * Kanalın fiilen kurulup kurulmadığı. {@code channel} ve {@code startDate},
   * dialplan'ın {@code ORIGINID} VarSetEvent'i ile {@link #handleCallCreation} içinde set
   * edilir; bu event ancak kanal gerçekten varsa gelir. Üretim ölçümünde her vakada
   * origination-fail'den 7-40 saniye ÖNCE set edilmiş durumdaydı.
   */
  /**
   * Çağrının fiilen bir agent'a bağlanıp bağlanmadığı. {@code answeredByUser} agent bacağının
   * cevapladığını, {@code cdr.callHandlingStartTime} ise handling'in başladığını gösterir;
   * {@code answerDate} tek başına YETERSİZDİR (abandoned/AMD/IVR çağrılarında da doludur).
   */
  private boolean isConnectedToAgent(Call call) {
    if (call.isAnsweredByUser()) {
      return true;
    }
    return call.getUser() != null && call.getCdr() != null && call.getCdr().getCallHandlingStartTime() != null;
  }

  private boolean isChannelEstablished(Call call) {
    return call.getChannel() != null || call.getStartDate() != null;
  }

  /**
   * Gerçek sonucu taşıyan hangup hiç gelmezse çağrının asılı kalmasını engeller.
   * Mevcut emniyet ağı {@code CALL_EXPIRE_TIMEOUT} (1 saat) pratikte tetiklenmiyor, bu yüzden
   * çıplak {@code return} telephony slot'u ve kampanya sayaçlarını sızdırırdı.
   */
  private void scheduleOriginationFailFallback(Call call, Date failDate) {
    // Fallback İPTAL EDİLEBİLİR olmalı (previewFuture deseni): aynı Call nesnesi yeniden
    // originate edilebilir (FCT list-retry) veya çağrı bu arada finalize edilebilir. Future
    // saklanmazsa eski denemenin fallback'i canlı/yeni çağrıya RC 907 yazabilir.
    // Zamanlayıcı yalnız BEKLEME için kullanılır; finalize işi eventWorker'a devredilir, böylece
    // FIX-GLB90048674'ün event sıralama garantisi ve enjekte edilebilir test seam'i korunur.
    // NOT: eventWorker kapatılmışsa blockingPutRejectedHandler işi SESSİZCE düşürür, kuyruk
    // doluysa submitter'ı BLOKLAR. runOriginationFailFallback bu yüzden bloklamayan
    // `getQueue().offer` ile kuyruğa koyar (core thread prestart edilerek) ve kabul edilmezse
    // inline koşturur; aksi halde fallback ya kaybolur ya da tek thread'li scheduleService'i
    // (tüm preview timeout'larını ve cache expiry job'larını) kilitlerdi.
    //
    // ÖNCE ESKİ TIMER İPTAL EDİLİR: `Call` tek bir Future alanı tutar, ama aynı callId için iki
    // ayrı fail girişi vardır — `handleOriginateResponse` (OriginateResponse Reason=0) ve
    // `runOriginationFailTask` (Originate action'ın ManagerError cevabı). Grace dalı çağrıyı
    // `calls` cache'inden DÜŞÜRMEDİĞİ için ikinci giriş de buraya gelebilir. İptal edilmezse
    // birinci timer'ın referansı üzerine yazılır ve o timer ARTIK İPTAL EDİLEMEZ hale gelir;
    // FCT list-retry sonrası ateşleyip canlı 2. denemeye RC 907 yazabilirdi.
    call.cancelOriginationFailFallbackFuture();
    Future<?> future = scheduleService.schedule(() -> runOriginationFailFallback(call, failDate),
        ORIGINATION_FAIL_GRACE_PERIOD_MS, TimeUnit.MILLISECONDS);
    call.setOriginationFailFallbackFuture(future);
  }

  /**
   * Fallback'i eventWorker'da koşturur; worker kapatılmışsa (reconnect/logoff drain) iş sessizce
   * düşmesin diye zamanlayıcı thread'inde inline çalıştırır. Aksi halde
   * {@code blockingPutRejectedHandler} görevi atar ve çağrı 1 saatlik cache expiry'sine kalırdı.
   */
  private void runOriginationFailFallback(Call call, Date failDate) {
    // One-shot guard: görevin İKİ kez çalıştırılması denenebilir (worker'a verilir, kabul
    // edilmediği anlaşılırsa inline koşulur). İş idempotent olmalı ki iki yol yarışırsa
    // finalize iki kez yapılmasın. `processCallResult`'ın kendi latch'i de ayrıca korur.
    AtomicBoolean executed = new AtomicBoolean(false);
    Runnable task = () -> {
      if (!executed.compareAndSet(false, true)) {
        return;
      }
      try {
        finalizeOriginationFailFallback(call, failDate);
      } catch (Exception e) {
        LOG.error("Origination fail fallback failed for " + call, e);
      }
    };

    ExecutorService worker = eventWorker;
    if (worker == null || worker.isShutdown()) {
      LOG.warn("Event worker unavailable; running origination fail fallback inline for " + call);
      task.run();
      return;
    }
    // `blockingPutRejectedHandler` REDDETMEZ: kuyruk doluysa submitter'ı BLOKLAR, worker
    // kapalıysa görevi SESSİZCE DÜŞÜRÜR. Bu yüzden RejectedExecutionException'a güvenilemez
    // (asla atılmaz) ve `isShutdown()` ön kontrolü TOCTOU'dur — kontrolden sonra shutdown
    // olursa iş sessizce kaybolurdu. Ayrıca burası tek thread'li `scheduleService`'tir;
    // blocking-put onu, dolayısıyla TÜM preview timeout'larını kilitleyebilir.
    //
    // Submit, üretim worker'ında `execute()` ile YAPILMAZ: `blockingPutRejectedHandler`
    // reddetmez — kuyruk doluysa `getQueue().put()` ile submitter'ı BLOKLAR. Buradaki submitter
    // tek thread'li `scheduleService`'tir; bloklanırsa TÜM preview timeout'ları ve üç cache
    // expiry job'ı (özellikle awaiting-user-result 15dk kurtarması) durur.
    //
    // "Önce remainingCapacity() kontrol et, sonra execute et" de YETMEZ: kontrol ile execute
    // arasında AMI reader kuyruğu doldurabilir (TOCTOU) ve blocking-put'a düşülür.
    //
    // Bu yüzden bloklamayan `getQueue().offer(...)` kullanılır. `offer`'ın tek tuzağı
    // ThreadPoolExecutor'ın core thread'ini yalnız `execute()` içinde LAZY yaratmasıdır: havuz
    // hiç iş almamışsa poolSize=0 olur ve kuyruğa konan iş HİÇ ÇALIŞMAZ. `prestartCoreThread()`
    // bu tuzağı kapatır (idempotent; thread zaten varsa false döner ve hiçbir şey yapmaz).
    boolean handedToWorker = false;
    if (worker instanceof ThreadPoolExecutor) {
      ThreadPoolExecutor pool = (ThreadPoolExecutor) worker;
      if (!pool.isShutdown()) {
        pool.prestartCoreThread();
        handedToWorker = pool.getQueue().offer(task);
      }
    } else {
      // Test seam'leri (directExecutor / enjekte edilen executor) ThreadPoolExecutor değildir;
      // onlarda execute senkron veya güvenlidir.
      try {
        worker.execute(task);
        handedToWorker = true;
      } catch (RejectedExecutionException e) {
        handedToWorker = false;
      }
    }
    // Worker'a verilemediyse (kuyruk dolu / kapanmış) iş DÜŞMEMELİ: inline koştur. Worker'a
    // verildiyse bile kapanma yarışında görev sessizce düşmüş olabilir; one-shot guard sayesinde
    // aşağıdaki çağrı ya hiç çalışmamış işi kurtarır ya da zaten çalışmışsa hemen döner.
    if (!handedToWorker) {
      LOG.warn("Event worker could not accept origination fail fallback; running inline for " + call);
      task.run();
    } else if (worker.isShutdown()) {
      LOG.warn("Event worker shut down while accepting origination fail fallback; ensuring it ran for " + call);
      task.run();
    }
  }

  /**
   * Grace period gövdesi: çağrı hâlâ finalize edilmemişse eski davranışa (RC 907) döner.
   * Hangup bu arada geldiyse {@code markBusinessResultFinalized} latch'i zaten kapanmıştır ve
   * {@code processCallResult} sessizce çıkar; ayrıca çağrı cache'ten düşmüş olur.
   */
  private void finalizeOriginationFailFallback(Call call, Date failDate) {
    // Finalize edilmiş olmanın TEK doğru göstergesi one-shot latch'tir. "TRC != null" yeterli
    // değildir: hangup TRC'yi set edip awaiting-user-result koluna girebilir (processCallResult
    // çağırmadan), o durumda finalize'ı beklemeye devam etmek gerekir.
    if (call.isBusinessResultFinalized() || call.isAwaitingUserResultCode()) {
      return;
    }
    // `completeCall`'dan geçen TÜM sökme yolları için tek kapı. Bu yollar `processCallResult`
    // KULLANMAZ, dolayısıyla ne finalize latch'ini ne de telephony slot bayrağını set ederler;
    // aşağıdaki kapıların hiçbiri onları yakalamaz:
    //   - CampaignStopJob.clearCallAndSaveResultToDb (forceful stop): TRC 902 yazıp CDR'ı
    //     persist eder. `clearCall` PREVIEW kampanyada `calls` cache'ini bilerek invalidate
    //     etmediği için son cache kapısı da açık kalır ve fallback 902'yi RC 907 ile ezip
    //     CDR'ı + kampanya sayaçlarını İKİNCİ kez işlerdi.
    //   - UserLoginLogoutHandler.finishCall (logout/orphan) ve CallCleaner expiry yolları.
    // Aynı kapı `finalizeRecoverableConnectionLossCall` ve `finalizeAwaitingUserResultCodeCall`
    // içinde de kullanılır; buradaki eksikliği onlarla hizalar.
    if (call.getCompleteDate() != null) {
      LOG.info("Origination fail fallback skipped; call was already completed elsewhere for " + call);
      return;
    }
    // Telephony slot zaten bırakılmışsa çağrı başka bir yoldan (preview timeout, connection-loss
    // finalize, transfer) sökülmüştür. Preview kampanyada clearCall `calls` cache'ini bilerek
    // invalidate etmediği için aşağıdaki cache kontrolü bu vakayı yakalamaz; buradan çıkmazsak
    // returnUserToACDEngine ve kampanya sayaçları İKİNCİ kez uygulanırdı. Yan etkisiz olduğu
    // için agent kontrolünden ÖNCE gelir (aksi halde bu tanı logu maskelenirdi).
    if (call.isTelephonySlotReleased()) {
      LOG.info("Origination fail fallback skipped; telephony slot already released for " + call);
      return;
    }
    // Çağrı grace period İÇİNDE agent'a bağlanmış olabilir ve hangup henüz gelmemiş olabilir.
    // Böyle bir çağrıya RC 907 yazmak canlı, konuşulan çağrıyı finalize edip agent'ın result
    // code'unu kalıcı olarak kaybettirirdi: 907'nin CDR id'si (79) ne
    // WEBPHONE_RESULT_CODE_RECOVERABLE_TRC_IDS ne de durable fallback'in başarı kümesinde
    // ({10, 24}) olduğu için disposition sessizce düşer.
    //
    // ÖLÇÜT `answerDate` DEĞİL, agent bağlantısıdır. `answerDate` yalnız müşterinin açtığını
    // söyler; abandoned, AMD (telesekreter) ve outbound-IVR çağrılarında da doludur ve o
    // vakalarda kaybedilecek bir agent disposition'ı YOKTUR. `answerDate`e bakmak fallback'i
    // o çağrılar için de devre dışı bırakır ve slot/agent 1 saatlik expiry'ye kadar asılı kalır.
    // Kodun kendi "tam bağlantı" tanımı da iki koşulu birlikte ister
    // (UserPopulationEvaluator: answerDate != null && cdr.callHandlingStartTime != null).
    if (isConnectedToAgent(call)) {
      LOG.warn("Origination fail fallback skipped; call is connected to an agent for " + call);
      return;
    }
    // Çağrı dört cache'ten herhangi birinde olabilir; yalnız `calls`e bakmak, çağrı bu arada
    // orphaned/recoverable/awaiting cache'ine taşındıysa onu kalıcı olarak finalize edilmemiş
    // bırakırdı (getCallForHangup ile aynı kapsam kullanılır).
    if (getCallForHangup(call.getId()) == null) {
      return;
    }
    LOG.warn("Real hangup never arrived within grace period; falling back to originate action error for " + call);
    TelephonyResultCode telephonyResultCode = getItuToTelephonyResultCodeRelations().get(Constants.TELEPHONY_RC_ORIGINATE_ACTION_ERROR);
    // Yukarıdaki kapılar ile bu yazım arasında hangup thread'i finalize'a girmiş olabilir; kontrol
    // ile yazımı atomik yapan CAS olmadan RC 907 gerçek sonucun üzerine yazılabilirdi.
    if (!call.setTelephonyResultCodeIfNotFinalized(telephonyResultCode)) {
      LOG.info("Origination fail fallback lost race to real call result for " + call);
      return;
    }
    processCallResult(call, failDate);
  }

  /**
   * Clear call, remove fetched flag and return user to ACD engine if an
   * exception occurs during origination
   * @param call the failed call
   */
  private void handleExceptionDuringOrigination(Call call) {
    if (call.getUser() != null)
      returnUserToACDEngine(call);
    try {
      clearCall(call);
      // Rezervasyon originate öncesi alınmıştı; Originate atılamadıysa kanal hemen geri verilir.
      releaseFctChannel(call);
      dbOperations.removeFetchedDataFlagFromCall(call);
    } catch (SQLException e) {
      LOG.error(e + " on " + server, e, LOG_MODULE.DB);
    }
  }

  /**
   * Return user to idle users queue of ACDEngine if still in available status,
   * used in possible case when client didn't answer and call has a user
   * @param call the call to obtain the user from
   */
  private void returnUserToACDEngine(Call call) {
    try {
      User user = connectionComposition.getRedisUtil().getUserFromRedis(call.getUser().getId());
      if (user != null && user.getStatus() != null && user.getStatus().isAvailableForDialerCalls()) {
        connectionComposition.getAcdEngine().addToIdleUsers(user);
      }
    } catch (Exception e) {
      LOG.error("", e);
    }
  }

  /**
   * Clean-up for calls that have expired from callQueue
   */
  private class CallRemovalListener implements RemovalListener<String, Call> {
    @Override
    public void onRemoval(RemovalNotification<String, Call> notification) {
      if (notification.getCause() == RemovalCause.EXPIRED) {
        Call call = notification.getValue();
        if (call != null) {
          getConnectionComposition().getCallCleaner().clearExpiredCall(call);
          LOG.warn("Clean-up due to expiration for " + call + " on " + server, LOG_MODULE.ASTERISK, call.getCampaign());
        }
      }
    }
  }

  /**
   * Remove expired callbacks from
   * {@link ManagerConnectionImpl#getResponseListeners()}
   */
  private void clearExpiredCallbacksFromListeners() {
    // FIX-GLB90048674 (Faz 2): Originate sendAction artık COMMAND bağlantısından gidiyor; dolayısıyla
    // SendActionCallbackWrapper'lar command bağlantısının responseListeners map'inde tutuluyor.
    // Expired callback temizliği bu yüzden command bağlantısı üzerinden yapılır (null-guard'lı).
    if (commandConnection == null) {
      return;
    }
    Collection<SendActionCallback> sendActionCallbacks = ((DefaultManagerConnection) commandConnection).getResponseListeners().values();
    Iterator<SendActionCallback> iterator = sendActionCallbacks.iterator();
    removeCallbackIfExpired(iterator);
  }

  private void clearExpiredRecoverableConnectionLossCalls() {
    recoverableConnectionLossCalls.cleanUp();
  }

  private void clearExpiredAwaitingUserResultCodeCalls() {
    awaitingUserResultCodeCalls.cleanUp();
  }

  private void finalizeRecoverableConnectionLossCall(Call call) {
    try {
      if (call == null || call.getCompleteDate() != null || !call.markBusinessResultFinalized()) {
        return;
      }
      Date completeDate = getRecoverableConnectionLossCompletionDate(call);
      connectionComposition.getCallCleaner().completeCall(call, completeDate);
      if (call.getTelephonyResultCode() == null) {
        call.setTelephonyResultCode(ituToTelephonyResultCodeRelations.get(Constants.ERROR_RESULT_CODES.AMI_CONNECTION_LOSS.getCode()));
      }
      CallAttemptPolicy.restoreForNonAttemptResultIfNeeded(call);

      releaseTelephonySlotIfNeeded(call, "recoverable-connection-loss-timeout");
      dbOperations.insertCallResultToDB(call);
      call.getCallOriginator().saveCDR(call, completeDate);
      if (call.getUser() != null) {
        returnUserToACDEngine(call);
      }
      LOG.warn("Timed out recoverable connection loss call " + call + " on " + server, LOG_MODULE.ASTERISK, call.getCampaign());
    } catch (Exception e) {
      LOG.error("Exception occurred during recoverable connection loss timeout cleanup of " + call, e);
    }
  }

  private Date getRecoverableConnectionLossCompletionDate(Call call) {
    CDR cdr = call.getCdr();
    if (cdr != null && cdr.getCallEndTime() != null && call.getTelephonyResultCode() != null) {
      return cdr.getCallEndTime();
    }

    LOG.warn("Timed out recoverable connection loss call " + call + " on " + server, LOG_MODULE.ASTERISK, call.getCampaign());
    return new Date();
  }

  /**
   * 327734: Awaiting penceresi agent'ın webphone grubundaki "Wrapup timer" (saniye) değeridir;
   * preview kampanyada kampanyanın result code timer'ı tanımlıysa webphone gibi o değer önceliklidir.
   * Değer yok veya 0 ise (webphone'da 0 = wrap-up beklemesi yok) varsayılan
   * {@link Constants#AWAITING_USER_RESULT_EXPIRE_TIMEOUT} korunur; aynı değer cache ceiling'i
   * olduğu için üst sınırdır.
   */
  private long resolveAwaitingUserResultCodeTimeoutMillis(Call call) {
    Integer wrapupTimerSeconds = resolveWrapupTimerSeconds(call);
    if (wrapupTimerSeconds == null || wrapupTimerSeconds <= 0) {
      return Constants.AWAITING_USER_RESULT_EXPIRE_TIMEOUT;
    }
    return Math.min(TimeUnit.SECONDS.toMillis(wrapupTimerSeconds), Constants.AWAITING_USER_RESULT_EXPIRE_TIMEOUT);
  }

  private Integer resolveWrapupTimerSeconds(Call call) {
    Campaign campaign = call.getCampaign();
    if (campaign != null && campaign.isPreviewCampaign() && campaign.getResultCodeTimer() != null) {
      return campaign.getResultCodeTimer();
    }
    return Optional.ofNullable(call.getUser())
        .map(User::getGroup)
        .map(Group::getWrapupTimerSeconds)
        .orElse(null);
  }

  /**
   * Pencere varsayılandan kısaysa finalize'ı zamanlar; varsayılana eşitse mevcut cache expiry yolu
   * aynen sürer. Task yalnız call id tutar; URC gelip çağrı cache'ten çıktıysa süre sonunda no-op olur.
   */
  private void scheduleAwaitingUserResultCodeFinalize(Call call) {
    long timeoutMillis = resolveAwaitingUserResultCodeTimeoutMillis(call);
    if (timeoutMillis >= Constants.AWAITING_USER_RESULT_EXPIRE_TIMEOUT) {
      return;
    }
    String callId = call.getId();
    scheduleService.schedule(() -> finalizeAwaitingUserResultCodeCallIfDue(callId), timeoutMillis, TimeUnit.MILLISECONDS);
    LOG.info("Awaiting user result code for " + timeoutMillis + " ms (wrapup timer) for " + call);
  }

  /**
   * Cache expiry yoluyla aynı sıra: çağrı önce cache'ten çıkarılır (EXPLICIT removal, listener no-op),
   * sonra mevcut awaiting finalize hattına verilir.
   */
  private void finalizeAwaitingUserResultCodeCallIfDue(String callId) {
    Call call = awaitingUserResultCodeCalls.getIfPresent(callId);
    if (call == null) {
      return;
    }
    awaitingUserResultCodeCalls.invalidate(callId);
    finalizeAwaitingUserResultCodeCall(call);
  }

  private void finalizeAwaitingUserResultCodeCall(Call call) {
    try {
      if (call == null || call.getCompleteDate() != null || call.isBusinessResultFinalized()) {
        return;
      }
      hydrateUserResultCodeFromDurableFallback(call);
      applyAmbiguousTrcForMissingUserResultCode(call);
      Date completeDate = getAwaitingUserResultCodeCompletionDate(call);
      processCallResult(call, completeDate);
      LOG.warn("Timed out awaiting user result code call " + call + " on " + server, LOG_MODULE.ASTERISK, call.getCampaign());
    } catch (Exception e) {
      LOG.error("Exception occurred during awaiting user result code timeout cleanup of " + call, e);
    }
  }

  /**
   * 327734: Awaiting penceresi user result code gelmeden dolan başarılı (normal clearing)
   * çağrının TRC'si Belirsiz Kayıt'a (code 912, id 85) çevrilir; retry strategy çağrıyı
   * kapatmak yerine normal retry sürecini işletir. Kod pd_telephony_rc'de tanımsızsa
   * mevcut davranış korunur.
   */
  private void applyAmbiguousTrcForMissingUserResultCode(Call call) {
    if (call.getUserResultCode() != null) {
      return;
    }
    TelephonyResultCode current = call.getTelephonyResultCode();
    if (current == null || current.getCode() != Constants.TELEPHONY_RC_NORMAL_CLEARING) {
      return;
    }
    TelephonyResultCode ambiguousRecord = ituToTelephonyResultCodeRelations.get(Constants.TELEPHONY_RC_AMBIGUOUS_RECORD);
    if (ambiguousRecord == null) {
      LOG.warn("Telephony result code with code {} is not defined in pd_telephony_rc; keeping successful result for {}",
          Constants.TELEPHONY_RC_AMBIGUOUS_RECORD, call);
      return;
    }
    call.setTelephonyResultCode(ambiguousRecord);
    LOG.warn("No user result code received before awaiting timeout; telephony result code set to ambiguous for " + call,
        LOG_MODULE.ASTERISK, call.getCampaign());
  }

  /**
   * Webphone durable fallback DB CDR'a user result code yazmış olabileceği için, awaiting
   * timeout cleanup'tan önce DB değerini in-memory call'a bağlar. Sonradan
   * `processCallResult`/`insertCallResultToDB` akışı in-memory null'dan dolayı CDR ve
   * campaign result tablosundaki dolu user_result_code alanını ezmez.
   */
  private void hydrateUserResultCodeFromDurableFallback(Call call) {
    if (call == null || call.getUserResultCode() != null || cdrService == null
        || call.getCdr() == null || StringUtils.isEmpty(call.getId())) {
      return;
    }
    try {
      Long resultCodeId = findPersistedUserResultCodeId(call);
      if (resultCodeId == null) {
        resultCodeId = findAuthoritativeWebphoneUserResultCodeId(call);
      }
      if (resultCodeId == null) {
        return;
      }
      call.getCdr().setUserResultCodeId(resultCodeId);
      ResultCode hydrated = new ResultCode();
      hydrated.setId(resultCodeId);
      hydrated.setFinalStatus(true);
      call.setUserResultCode(hydrated);
    } catch (Exception e) {
      LOG.warn("Could not hydrate user result code from durable fallback for " + call, e);
    }
  }

  private Long findPersistedUserResultCodeId(Call call) {
    CDR persistedCdr = cdrService.getByInteractionId(call.getId());
    return persistedCdr != null ? persistedCdr.getUserResultCodeId() : null;
  }

  /**
   * Agent result code'unun authoritative kaynağı webphone tablolarıdır; dialer bu değeri normalde
   * yalnız Redis pub/sub üzerinden öğrenir. Fire-and-forget delivery'de tek bir mesajın düşmesi
   * disposition'ı kalıcı kaybettirdiği için (06.08.2026 üretim vakası) awaiting timeout, çağrıyı
   * kapatmadan önce {@code ast_registrar_cdr} + {@code wbp_call_result_code} üzerinden son bir kez
   * kontrol eder. Aynı okuma, durable fallback ile finalize'ın aynı satırda yarıştığı pencereyi de
   * kapatır: fallback'in yazdığı değer henüz commit olmasa bile webphone kaydı zaten mevcuttur.
   *
   * <p>Sıra bilinçlidir: önce in-memory evaluator gate'i, sonra result code sahibi çözümü, en son
   * DB. İlk iki adım gereksiz sorguyu eler; awaiting timeout başına en fazla bir indeksli
   * {@code TOP 1} sorgusu çalışır ve bu yol hangup hot path'inde değildir.</p>
   */
  private Long findAuthoritativeWebphoneUserResultCodeId(Call call) {
    if (!UserPopulationEvaluator.shouldPopulateCdrUserResultCode(call)) {
      // Evaluator zaten URC'yi bastıracak (örn. TRC 84 no-MT): sorgu yapmanın faydası yok.
      return null;
    }
    Long resultCodeOwnerId = resolveUserResultCodeOwnerId(call);
    if (resultCodeOwnerId == null) {
      // Sahibi çözülemeyen çağrıya başka bir agent'ın disposition'ı yazılmamalı.
      return null;
    }
    Long resultCodeId = cdrService.findWebphoneUserResultCodeId(call.getId(), resultCodeOwnerId);
    if (resultCodeId == null || resultCodeId <= 0L) {
      // Dialer'ın sahibi olmadığı tablodan gelen değer boundary'de doğrulanır; geçersiz id
      // pd_cdr'a yazılırsa raporlamada var olmayan bir result code'a işaret eder.
      return null;
    }
    LOG.warn("Recovered user result code {} from webphone tables on awaiting timeout for userId={} and {}",
        resultCodeId, resultCodeOwnerId, call);
    return resultCodeId;
  }

  /**
   * Result code'u girmiş olması beklenen agent: öncelik in-memory {@code call.user}'dadır.
   * Hunt/churn sonrası temizlenmişse persist edilmiş CDR ownership alanları kullanılır
   * (assigned-first, sonra connected — {@code WebphoneSubscriber} durable fallback guard'ıyla
   * aynı sıra).
   */
  private Long resolveUserResultCodeOwnerId(Call call) {
    if (call.getUser() != null && call.getUser().getId() != null) {
      return call.getUser().getId();
    }
    CDR cdr = call.getCdr();
    if (cdr.getAssignedUser() != null && cdr.getAssignedUser().getId() != null) {
      return cdr.getAssignedUser().getId();
    }
    return cdr.getConnectedUser() != null ? cdr.getConnectedUser().getId() : null;
  }

  private Date getAwaitingUserResultCodeCompletionDate(Call call) {
    CDR cdr = call.getCdr();
    if (cdr != null && cdr.getCallEndTime() != null) {
      return cdr.getCallEndTime();
    }
    return new Date();
  }

  private class RecoverableConnectionLossCallRemovalListener implements RemovalListener<String, Call> {
    @Override
    public void onRemoval(RemovalNotification<String, Call> notification) {
      if (notification.getCause() == RemovalCause.EXPIRED) {
        finalizeRecoverableConnectionLossCall(notification.getValue());
      }
    }
  }

  private class AwaitingUserResultCodeCallRemovalListener implements RemovalListener<String, Call> {
    @Override
    public void onRemoval(RemovalNotification<String, Call> notification) {
      if (notification.getCause() == RemovalCause.EXPIRED) {
        finalizeAwaitingUserResultCodeCall(notification.getValue());
      }
    }
  }

  private void removeCallbackIfExpired(Iterator<SendActionCallback> iterator) {
    LocalTime now = LocalTime.now();
    try {
      while (iterator.hasNext()) {
        SendActionCallbackWrapper sendActionCallbackWrapper = (SendActionCallbackWrapper) iterator.next();
        if (isCallbackExpired(now, sendActionCallbackWrapper))
          iterator.remove();
      }
    } catch (ConcurrentModificationException e) {
      LOG.warn(e);
    }
  }

  private boolean isCallbackExpired(LocalTime now, SendActionCallbackWrapper sendActionCallbackWrapper) {
    return !sendActionCallbackWrapper.isInUse() && Duration.between((sendActionCallbackWrapper).getCreateDate(), now).getSeconds() * 1000 > Constants.CALLBACK_EXPIRE_TIMEOUT;
  }

  private void handleAniDefinitionEvent(AniDefinitionEvent event) {
    Call call = calls.getIfPresent(event.getParentId());
    if (call == null) {
      LOG.warn("There is no such call in the cache for " + event);
    } else {
      call.getCdr().setOutgoingCallId(event.getUniqueId());
    }
  }

  private void handleAgentCalledByDialerEvent(AgentCalledByDialerEvent event) {
    Call call = calls.getIfPresent(event.getParentId());
    if (call == null) {
      LOG.warn("There is no such call in the cache for " + event);
    } else {
      call.getCdr().setInternalCallId(event.getUniqueId());
    }
  }

  /**
   * Asterisk transfer tetiklendiğinde fire eden UserEvent'i handle eder.
   * Agent transfer ettiği an MT'yi dialer'ın idle havuzuna geri koyarak, müşteri
   * transfer hedefiyle konuşmaya devam ederken MT'nin yeni çağrı alabilmesini
   * sağlar.
   *
   * <p>Eşleştirme iki anahtar üzerinden yapılır, sırasıyla:
   * <ol>
   *   <li>{@code chanVariables.PARENTID} → tüm açık decorator'lerin {@code calls}
   *       cache'lerinde aranır (canonical, dialer call ID UUID'si sunucu
   *       namespace'inden bağımsız). Cross-server blind transfer senaryosu:
   *       call origination ile transfer event'i farklı Asterisk sunucularına
   *       düşebiliyor, bu yüzden lookup tek decorator'la sınırlı değil.</li>
   *   <li>{@code event.linkedChannel} ↔ {@code Call.linkedid} (handleCallCreation'da
   *       VarSetEvent.uniqueId ile yakalanır). PARENTID yokken/hiçbir cache'te
   *       bulunmadığında <b>bu decorator scope'unda</b> fallback; linkedid
   *       Asterisk per-server UNIQUEID namespace'i olduğu için cross-decorator
   *       aranmıyor (yanlış call eşleşmesi riski).</li>
   * </ol>
   *
   * <p>Hangup yolundaki çift muhasebeyi engellemek için `call.transferred=true`
   * set edilir; `doCampaignDecrementationsOnHangup` bu bayrağı görür ve
   * `decrementInCallsSize` çağrısını atlar. Flag {@code transient volatile}
   * olduğu için origination decorator'unun hangup thread'i de doğru okur.
   */
  private void handleNewTransferChannel(NewTransferChannelEvent event) {
    String linkedChannel = event.getLinkedChannel();
    String parentId = extractParentIdFromChanVariables(event);
    Call call = resolveTransferCall(parentId, linkedChannel);
    if (call == null) {
      LOG.warn("No call matched transfer event (parentId=" + parentId + ", linkedChannel=" + linkedChannel + "): " + event);
      return;
    }
    if (call.isTransferred()) {
      return;
    }

    User agent = call.getUser();
    if (agent != null) {
      agent.setDialInProcess(false);
      connectionComposition.getCallUpToDateKeeper().unregisterCall(call);
      // The original call's AGI Dial is still blocked (customer is now talking to the transfer
      // target), so its finally/releaseUser will not run until that call ends. Release the dialer
      // dial lock for this agent now so the freed agent can be dialed for a new call. Ownership is
      // checked by call id, so a concurrent new call that already re-locked the agent is unaffected.
      connectionComposition.getAgentDialLockRegistry().release(agent.getId(), call.getId());
      if (agent.getStatus() != null && agent.getStatus().isAvailableForDialerCalls()) {
        connectionComposition.getAcdEngine().addToIdleUsers(agent);
      }
    }

    if (call.isAnsweredByUser() && call.getCampaign() != null) {
      call.getCampaign().decrementInCallsSize(call);
    }

    call.setTransferred(true);
    LOG.info("Agent released early on transfer for " + call);
  }

  private String extractParentIdFromChanVariables(NewTransferChannelEvent event) {
    Map<String, String> chanVariables = event.getChanVariables();
    if (chanVariables == null) {
      return null;
    }
    return chanVariables.get(TRANSFER_PARENT_ID_CHAN_VARIABLE);
  }

  /**
   * Transfer event'i için call'u önce PARENTID (canonical dialer call ID, tüm
   * decorator cache'leri arasında), sonra linkedChannel (LINKEDID fallback,
   * sadece bu decorator scope'unda) üzerinden çözer.
   */
  private Call resolveTransferCall(String parentId, String linkedChannel) {
    if (StringUtils.isNotEmpty(parentId)) {
      Call byParentId = findCallByParentIdAcrossDecorators(parentId);
      if (byParentId != null) {
        return byParentId;
      }
    }
    if (StringUtils.isNotEmpty(linkedChannel)) {
      return findCallByLinkedid(linkedChannel);
    }
    return null;
  }

  /**
   * PARENTID üzerinden call'u önce bu decorator'un cache'inde, ardından açık
   * tüm sibling decorator'lerin cache'lerinde arar. Cross-server blind transfer
   * senaryosunda call origination farklı bir Asterisk sunucusundadır; PARENTID
   * dialer'in UUID'sini taşıdığı için sunucu namespace'inden bağımsız ve
   * cross-decorator lookup için güvenlidir. {@code openedConnections} HashMap'i
   * codebase'in başka yerlerinde de lock'suz iterate ediliyor (composition
   * logoff/cleanup yolları); transfer event frekansı düşük olduğu için aynı
   * pattern uygulandı. CME/NPE'ye karşı try-catch ile WARN + fallback.
   */
  private Call findCallByParentIdAcrossDecorators(String parentId) {
    Call local = calls.getIfPresent(parentId);
    if (local != null) {
      return local;
    }
    try {
      Map<String, ManagerConnectionDecorator> openedConnections = connectionComposition.getOpenedConnections();
      if (openedConnections == null) {
        return null;
      }
      for (ManagerConnectionDecorator other : openedConnections.values()) {
        if (other == this) {
          continue;
        }
        Call call = other.getCalls().getIfPresent(parentId);
        if (call != null) {
          return call;
        }
      }
    } catch (Exception e) {
      LOG.warn("Cross-decorator PARENTID lookup failed for " + parentId + ": " + e);
    }
    return null;
  }

  /**
   * Decorator'un kendi `calls` cache'inde linkedid eşleşmesi yapar. Lineer tarama;
   * N = anlık aktif çağrı sayısı. Transfer event frekansı düşük olduğu için
   * `AGENTS.md` hot path standardı kapsamında zararsız.
   */
  private Call findCallByLinkedid(String linkedChannel) {
    if (StringUtils.isEmpty(linkedChannel)) {
      return null;
    }
    for (Call c : calls.asMap().values()) {
      if (linkedChannel.equals(c.getLinkedid())) {
        return c;
      }
    }
    return null;
  }

}
