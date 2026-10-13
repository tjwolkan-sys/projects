package com.powerdialer.engine.dialer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.common.cache.Cache;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.powerdialer.model.CDR;
import com.powerdialer.model.Call;
import com.powerdialer.model.User;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.PhoneNumberColumn;
import com.powerdialer.model.Server;
import com.powerdialer.model.TelephonyResultCode;
import com.powerdialer.util.UserPopulationEvaluator;
import org.asteriskjava.manager.ManagerConnectionState;
import org.springframework.util.CollectionUtils;

import com.powerdialer.engine.CallCleaner;
import com.powerdialer.engine.acd.ACDEngine;
import com.powerdialer.engine.asterisk.CallerIdManager;
import com.powerdialer.engine.asterisk.ami.ManagerConnectionComposition;
import com.powerdialer.engine.asterisk.ami.ManagerConnectionDecorator;
import com.powerdialer.engine.dialer.retry.RetryStrategy;
import com.powerdialer.engine.dialer.retry.RetryStrategyFactory;
import com.powerdialer.engine.dialer.routing.FctChannelGate;
import com.powerdialer.engine.dialer.routing.RouteDetailsCursor;
import com.powerdialer.exceptions.NoAvailablePoolCallerIdException;
import com.powerdialer.exceptions.RouteDetailsUnavailableException;
import com.powerdialer.service.CampaignResultService;
import com.powerdialer.util.Constants;
import com.powerdialer.util.Constants.CAMPAIGN_TYPE;
import com.powerdialer.util.Constants.CAMPAIGN_STATE;
import com.powerdialer.util.Constants.ERROR_RESULT_CODES;
import com.powerdialer.util.Constants.LOG_MODULE;
import com.powerdialer.util.LoggerDecorator;
import com.powerdialer.util.Utils;

/**
 * Polls calls from given callQueues and originates them to asterisk server
 */
public class CallOriginator implements RunnableAction {

  private ExecutorService lscExecutor;
  private final LoggerDecorator LOG;
  private final int CPS_PERIOD = 1000;
  /** Kampanya basina "agent bulunamadi" WARN'lari arasindaki en kisa sure. */
  private static final long NO_AGENT_WARN_THROTTLE_MS = 10_000L;
  private final CopyOnWriteArrayList<BlockingDeque<Call>> callQueues = new CopyOnWriteArrayList<>();
  private BlockingQueue<Call> callQueue;
  private ScheduledExecutorService cpsService;
  private ScheduledFuture<?> cpsServiceFuture;
  private ManagerConnectionDecorator connectionDecorator;
  private final DialerFacade dialerFacade;
  private int nextCallQueueIndex = 0;
  /**
   * Tick başına çağrı işleme hatası sayacı; log amplifikasyonunu sınırlamak için kullanılır
   * (bkz. {@link #processCallSafely}). {@code nextCallQueueIndex} ile aynı gerekçeyle korumasızdır:
   * originate tick'i tek thread'li {@code cpsService} üzerinde çalışır.
   */
  private int failedCallCountInCurrentTick = 0;
  private final AtomicInteger callsCountUnderLSCProcessing = new AtomicInteger(0);
  private final RetryStrategyFactory strategyFactory = new RetryStrategyFactory();
  private boolean amiConnectionEstablished;
  private final CallerIdManager callerIdManager;
  private final CallCleaner callCleaner;
  private final CampaignResultService campaignResultService;
  /**
   * FIX-GLB90060295: "ACD agent bulamadi -> cagri kuyruga geri konuldu" WARN'inin kampanya basina
   * en erken tekrar zamani (epoch ms). Bu yol saniyede onlarca kez calisabildigi icin throttle
   * zorunludur. Instance alanidir (yeni static mutable state eklenmez); girdi sayisi bu originator'in
   * gordugu kampanya sayisiyla sinirlidir.
   */
  private final ConcurrentMap<Long, Long> noAgentWarnNextAtByCampaignId = new ConcurrentHashMap<>();

  public CallOriginator(ManagerConnectionDecorator connectionDecorator,
                        DialerFacade dialerFacade,
                        CallerIdManager callerIdManager,
                        CallCleaner callCleaner,
                        CampaignResultService campaignResultService) {
    this.connectionDecorator = connectionDecorator;
    this.dialerFacade = dialerFacade;
    this.callerIdManager = callerIdManager;
    this.callCleaner = callCleaner;
    this.callQueue = new LinkedBlockingQueue<>();
    this.campaignResultService = campaignResultService;
    this.LOG = connectionDecorator.getConnectionComposition().getLoggerDecorator().getLogger(CallOriginator.class);
  }

  public ManagerConnectionDecorator getConnectionDecorator() {
    return connectionDecorator;
  }

  public CallCleaner getCallCleaner() {
    return callCleaner;
  }

  public void start() {
    Thread thread = new Thread(this);
    thread.setDaemon(true);
    thread.start();
    LOG.info("Started [" + this + "]");
  }

  public void pause() {
    cpsServiceFuture.cancel(false);
    LOG.info("Paused [" + this + "]");
  }

  public void stop() {
    cpsService.shutdown();
    lscExecutor.shutdown();
    LOG.info("Stopped [" + this + "]");
  }

  /**
   * Checks the connection state. If it is up then proceeds call origination otherwise
   * verify actuality of current AMI connection. On triggering connection state appropriate log messages
   * have to be stored. To avoid constantly logging about of lost connection we use the
   * <code>amiConnectionStateFlag</code> which is updated on connection state change.
   */
  @Override
  public void run() {
    createExecutors();
    cpsServiceFuture = cpsService.scheduleWithFixedDelay(() -> {
      try {
        if (connectionDecorator.getManagerConnectionState() == ManagerConnectionState.CONNECTED) {
          updateAmiConnectionStateFlag(true);
          pollCallsFromConsumerAndOriginate();
        } else {
          updateAmiConnectionStateFlag(false);
          handleConnectionLoss();
        }
      } catch (Exception e) {
        LOG.error("Got exception in " + this, e);
      }
    }, 0, CPS_PERIOD, TimeUnit.MILLISECONDS);
  }

  private void createExecutors() {
    ThreadFactory callOriginatorThreadFactory = new ThreadFactoryBuilder().setDaemon(true)
                                                    .setNameFormat("call-originator-" + connectionDecorator.getServer().getIp() + "-executor-%d")
                                                    .build();
    this.cpsService = Executors.newSingleThreadScheduledExecutor(callOriginatorThreadFactory);
    this.lscExecutor = createLscExecutor(connectionDecorator.getServer().getIp(),
                                         connectionDecorator.getServer().getMaxLines());
  }

  /**
   * Task 325469: lsc-executor sinirsiz cached pool yerine ayni semantikle
   * (core 0, 60 s idle, SynchronousQueue) ama server.maxLines ust sinirli
   * kurulur. availableOriginateSlots in-flight LSC'yi butceden dustugu icin
   * normal akista doygunluk beklenmez; doygunlukta CallerRunsPolicy originate
   * tick thread'inde kosarak dogal geri basinc uygular (cagri kaybolmaz).
   */
  public static ExecutorService createLscExecutor(String serverIp, Integer maxLines) {
    ThreadFactory lscThreadFactory = new ThreadFactoryBuilder().setDaemon(true)
                                         .setNameFormat("lsc-executor-" + serverIp + "-%d")
                                         .build();
    int maxThreads = Math.max(1, maxLines == null ? 0 : maxLines.intValue());
    return new ThreadPoolExecutor(0, maxThreads, 60L, TimeUnit.SECONDS,
        new SynchronousQueue<>(), lscThreadFactory, new ThreadPoolExecutor.CallerRunsPolicy());
  }

  private void updateAmiConnectionStateFlag(boolean established) {
    if (established && !amiConnectionEstablished) {
      amiConnectionEstablished = true;
      LOG.info("AMI connection established. Ready to originate calls: " + this);
    } else if (!established && amiConnectionEstablished) {
      amiConnectionEstablished = false;
      LOG.warn("AMI connection lost. Unable to originate calls: " + this);
    }
  }

  /**
   * Poll a call from callQueues and originate to asterisk server
   */
  private void pollCallsFromConsumerAndOriginate() {
    Server server = connectionDecorator.getServer();
    int originateElementsSize = availableOriginateSlots(server);
    String bulkOriginationId = "bulk-" + UUID.randomUUID();
    int originatedCount = 0;
    failedCallCountInCurrentTick = 0;
    for (int i = 0; i < originateElementsSize; i++) {
      Call call = getCallFromCallQueues();
      if (call != null) {
        if (!call.isGammaSymbolicCarrierAssignment()) {
          connectionDecorator.getConnectionComposition().getCallUpToDateKeeper().registerCall(call);
        }
        int callerIdAttempt = callerIdAttemptForOrigination(call);
        incrementAttemptsForOrigination(call);
        call.setCallOriginator(this);
        try {
          if (isFctRoutingPath(call)) {
            RouteDetailsCursor cursor = callerIdManager.obtainRouteDetailsCursor(call);
            call.setRouteDetailsCursor(cursor);
            call.setCallerId(cursor.current().getPhoneNumber());
          } else {
            call.setCallerId(callerIdManager.obtainCallerId(call));
          }
          if (call.getCampaign().getLscEnabled()) {
            applyLSCAndOriginate(bulkOriginationId, call);
          } else {
            processOrigination(bulkOriginationId, call);
          }
        } catch (NoAvailablePoolCallerIdException e) {
          handleNoAvailablePoolCallerId(call);
        } catch (RouteDetailsUnavailableException e) {
          handleRouteDetailsUnavailable(call, e);
        }
        delayOrigination(originateElementsSize);
        originatedCount++;
      }
    }
    logSuppressedCallFailuresIfNeeded(server);
    logOriginateStarvationIfNeeded(server, originateElementsSize, originatedCount);
  }

  /**
   * Aynı tick içinde birden fazla çağrı işlenemediyse yalnız ilki stack trace ile loglanır
   * ({@link #processCallSafely}); kalanı burada tek satırda sayı olarak raporlanır.
   *
   * <p>Neden bastırma: bozuk runtime state paylaşımlı olabilir (10.08.2026 kaydında ölü Hibernate
   * proxy'si {@code CallUpToDateKeeper}'da olduğu için TÜM çağrıların ACD'si patlıyordu). Çağrı başına
   * tam stack trace basmak, tick başına originate bütçesi kadar (ör. 25) × server sayısı kadar log
   * üretir ve olay anında log/IO'yu kendi başına bir arıza kaynağına çevirir.</p>
   */
  private void logSuppressedCallFailuresIfNeeded(Server server) {
    if (failedCallCountInCurrentTick > 1) {
      LOG.warn("Suppressed " + (failedCallCountInCurrentTick - 1) + " further call processing failure(s) on "
          + server.getIp() + " in this originate tick (see the first error above for the cause)");
    }
  }

  /**
   * Tick başına originate bütçesini hesaplar: min(maxLines − in-flight callQueue, cpsLimit)
   * − uçuştaki LSC. Davranış eskisiyle birebir aynıdır (yalnız inline hesaptan extract edildi).
   * Teşhis: bütçe ≤ 0 ise hangi bileşenin (in-flight callQueue / cpsLimit / LSC) kıstığı WARN'lanır;
   * origination'ın server seviyesinde tıkanıp tıkanmadığını ve sebebini canlıda görmek için.
   * Per-tick (~1/sn/server) ve yalnız tıkanmada loglar → hot-path maliyeti ihmal edilebilir, spam yok.
   */
  int availableOriginateSlots(Server server) {
    int inFlightCallQueue = callQueue.size();
    int underLsc = callsCountUnderLSCProcessing.get();
    int slots = Math.min(server.getMaxLines() - inFlightCallQueue, server.getCpsLimit()) - underLsc;
    if (slots <= 0) {
      LOG.warn("Originate throttled on " + server.getIp() + ": slots=" + slots
          + " (maxLines=" + server.getMaxLines() + ", inFlightCallQueue=" + inFlightCallQueue
          + ", cpsLimit=" + server.getCpsLimit() + ", underLSC=" + underLsc + ")");
    }
    return slots;
  }

  /**
   * Bütçe varken (slots > 0) hiç çağrı originate edilmediyse ve kampanya kuyruklarında
   * bekleyen çağrı varsa, "recycle/agent-yok" desenini WARN'lar: throttle DEĞİL ama çağrılar
   * ajana atanamayıp originate olmuyor demektir. Per-tick ve yalnız bu anomalide loglar;
   * callQueues taraması yalnız anomali koşulunda yapılır (sağlıklı akışta maliyet yok).
   */
  private void logOriginateStarvationIfNeeded(Server server, int slots, int originatedCount) {
    if (slots <= 0 || originatedCount > 0) {
      return;
    }
    int queuedAcrossCampaigns = 0;
    Call sample = null;
    // CopyOnWriteArrayList: iterasyon snapshot üzerinden çalışır; eşzamanlı
    // add/remove ile CME oluşmaz, ayrı lock'a gerek yoktur.
    for (BlockingDeque<Call> queue : callQueues) {
      queuedAcrossCampaigns += queue.size();
      if (sample == null) {
        sample = queue.peek();
      }
    }
    if (queuedAcrossCampaigns > 0) {
      // Bekleyen örnek bir çağrının kampanyasındaki idle ajan sayısını ekle: idle=0 ise havuz
      // boştu (availability/pool), idle>0 ise ajan vardı ama atanamadı (ACD eşleşme). Yalnız
      // anomali yolunda hesaplanır; deep call zinciri try/catch ile korunur (log teşhis amaçlı).
      String idleInfo = "";
      if (sample != null && sample.getCampaign() != null) {
        try {
          int idleAgents = connectionDecorator.getConnectionComposition().getAcdEngine()
              .getCampaignIdleUsersCount(sample.getCampaign());
          idleInfo = ", sampleCampaign=" + sample.getCampaign().getId() + " idleAgents=" + idleAgents;
        } catch (Exception ignore) {
          // teşhis logu; idle sayısı alınamazsa sessiz geç
        }
      }
      LOG.warn("Originate starvation on " + server.getIp() + ": slots=" + slots
          + " but originated=0 with queuedAcrossCampaigns=" + queuedAcrossCampaigns + idleInfo
          + " (calls waiting but none assignable/originated — recycle/no-agent)");
    }
  }

  int callerIdAttemptForOrigination(Call call) {
    // RDS maps the received value back with attempt - 1, so pass the next logical attempt.
    return call.getAttempts() + 2;
  }

  void incrementAttemptsForOrigination(Call call) {
    CallAttemptPolicy.incrementForOrigination(call);
  }

  /**
   * Poll a call from a call consumer callQueue if campaign hasn't reached the
   * limit of concurrentLines
   */
  private Call getCallFromCallQueues() {
    try {
      if (!CollectionUtils.isEmpty(callQueues)) {
        BlockingDeque<Call> currentCallQueue = callQueues.get(nextCallQueueIndex);
        nextCallQueueIndex = (nextCallQueueIndex < callQueues.size() - 1) ? ++nextCallQueueIndex : 0;
        Call call = currentCallQueue.poll();
        if (call != null)
          if (call.getCampaign().canOriginateMoreCalls())
            return processCallSafely(currentCallQueue, call);
          else
            currentCallQueue.addFirst(call);
      }
    } catch (IndexOutOfBoundsException e) {
      nextCallQueueIndex = 0;
    }
    return null;
  }

  /**
   * Tek bir çağrının işlenmesi beklenmedik şekilde patlarsa hata o çağrıyla sınırlı kalır.
   *
   * <p>Eski hali: exception {@code processCall} -> {@link #getCallFromCallQueues()} ->
   * {@link #pollCallsFromConsumerAndOriginate()} zincirinden {@link #run()} içindeki genel catch'e
   * kadar çıkıyordu. Sonuç iki katmanlı hasardı: (1) o tick'in TÜM originate bütçesi iptal olduğu
   * için server'da hiç çağrı çıkmıyor, (2) kuyruktan {@code poll()} edilmiş çağrı ne kuyruğa ne
   * DB'ye döndüğü için KAYBOLUYORDU. 10.08.2026 kaydında tek bozuk runtime state (ölü Hibernate
   * proxy'si) bu yolla originate'i dakikalarca durdurdu.</p>
   *
   * <p>Yeni hali: hata ERROR olarak loglanır (sessiz yutma yok), çağrı KAYBEDİLMEZ ve tick sonraki
   * kuyrukla devam eder. Çağrı kuyruğun SONUNA konur: başa konsa deterministik olarak patlayan bir
   * çağrı aynı tick içinde originate bütçesini tek başına tüketirdi. Kuyruk yapısı unbounded
   * {@code LinkedBlockingDeque} olduğu için {@code addLast} kapasite hatası üretmez.</p>
   *
   * <p>Log maliyeti iki yönden sınırlıdır: tick başına yalnız ilk hata stack trace ile basılır
   * (kalanı {@link #logSuppressedCallFailuresIfNeeded} ile tek satırda sayılır) ve log çağrısı
   * bilinçli olarak varargs'sız tutulur; aksi halde {@code LoggerDecorator} her hatada ActionLog DB
   * insert'i yapardı.</p>
   */
  private Call processCallSafely(BlockingDeque<Call> currentCallQueue, Call call) {
    try {
      return processCall(currentCallQueue, call);
    } catch (RuntimeException e) {
      currentCallQueue.addLast(call);
      if (failedCallCountInCurrentTick++ == 0) {
        LOG.error("Could not process call for origination, call is returned to the end of its queue. " + call, e);
      }
      return null;
    }
  }

  private boolean canOriginateCall(Call call) {
    Campaign campaign = call.getCampaign();
    return campaign != null
        && CAMPAIGN_STATE.RUNNING.equals(campaign.getState())
        && campaign.canOriginateMoreCalls();
  }

  /**
   * Process call by applying ACD if campaign type is not OUTBOUND_IVR to find
   * idle user to handle the call
   * @param currentCallQueue
   * @param call
   * @return call ready for origination or null
   */
  private Call processCall(BlockingDeque<Call> currentCallQueue, Call call) {
    if (!call.isNowFitCallTimeRange()) {
      LOG.info("Call is out of call time range. Will be returned to db. " + call, LOG_MODULE.CAMPAIGN, call.getCampaign());
      returnCallToDb(call);
      return null;
    }
    ACDEngine acdEngine = connectionDecorator.getConnectionComposition().getAcdEngine();
    if (call.getCampaign().getType() != CAMPAIGN_TYPE.OUTBOUND_IVR)
      acdEngine.processACD(call);

    if (call.getUser() != null || call.getCampaign().getType() == CAMPAIGN_TYPE.OUTBOUND_IVR) {
      // Gercek atama basarili: olasi eski tasiyici isareti temizlenir. Ayni Call nesnesi deferred
      // origination / kuyruga-geri yollarindan tekrar islenebilir; isaret bayat kalirsa gercek
      // atamali cagri keeper'a kaydedilmezdi.
      call.setGammaSymbolicCarrierAssignment(false);
      return call;
    }

    // user_id NULL ise callback havuza devredilmiştir (ATTEMPT_COUNT convert) → artık sahibe ait değil; appointed
    // gibi davranıp reschedule/attempt++ YAPILMAMALI, normal campaign-wide callback olarak akmalı (ACD pool ataması
    // boş döndüyse skill ise returnToDb, değilse kuyruğa geri).
    boolean isAssignedToSameUser = Constants.CALLBACKTYPE.ASSIGN_TO_SAME_USER.equals(call.getCallbackType())
            && Boolean.TRUE.equals(call.getCampaign().getAllowOverwriteByWebphone())
            && call.getUserId() != null
            && !call.isAppointerTimedOut();

    boolean isAppointedToUser = call.getUserIdAppointedToCallback() != null;

    // SORUN 2 / 1B (GAMMA): avail agent yok diye cagri DURDURULMAZ. Kac cagri cikacagini GAMMA
    // pacing (Xn) belirler ve "yakinda bosalacak" hesabini zaten yapmistir; on atama GAMMA'da
    // semboliktir (gercek secim musteri actiginda answer fazinda yapilir). Temsili tasiyici
    // (online herhangi bir kampanya agent'i, avail olmasi gerekmez) atanir ki originate exten'i
    // gecerli kalsin — agentsiz originate Asterisk'te 'Extension does not exist' ile reddediliyor
    // (30.06 kaydi). Appointed/same-user callback'ler HARIC: onlar sahibini beklemeli. Online
    // agent hic yoksa mevcut akisa duser (bos odaya arama cikarilmaz; Xn de zaten ~0 ister).
    if (call.getCampaign().isGammaPredictiveCampaign() && !isAssignedToSameUser && !isAppointedToUser) {
      User symbolicCarrier = acdEngine.findGammaSymbolicCarrier(call.getCampaign());
      if (symbolicCarrier != null) {
        call.setUser(symbolicCarrier);
        // Tasiyici gercek atama DEGILDIR: cagri keeper'a kaydedilmez (bkz. Call'daki bayrak
        // aciklamasi) ki mesgul tasiyici bosaldiginda idle'a donusu hayalet cagriyla engellenmesin.
        call.setGammaSymbolicCarrierAssignment(true);
        LOG.info("[GAMMA-CARRIER] avail agent yok; temsili tasiyici " + symbolicCarrier.getUsername()
            + " ile originate'e devam (gercek secim answer'da; keeper'a kayit YOK) " + call,
            LOG_MODULE.CAMPAIGN, call.getCampaign());
        return call;
      }
    }

    if (isAssignedToSameUser || isAppointedToUser) {
      // Bu noktada call.getUser() her zaman null'dur (line 314 atanan durumu döner): ACD sahibi
      // AVAILABLE bulamadı (online-meşgul VEYA offline farketmez), dolayısıyla atayamadı.
      moveDownAndRotateAppointedCallbacks(currentCallQueue);
      Campaign campaign = call.getCampaign();
      if (campaign.isAppointerResetByAttempt()) {
        // Task 321997: sahip o an AVAILABLE değil (online-meşgul veya offline farketmez) → arayamadığımız
        // için deneme sayacı +1 ve reschedule; sayaç eşiğe (agentCbConvertAfterAttempts) ulaşınca callback
        // campaign-wide'a (user_id=NULL) devredilir. Karar/sayım DB tarafında (rescheduleAppointedCallback).
        handleAppointedOwnerUnavailable(call);
        return null;
      }
      // Legacy TIMER stratejisi: davranış değişmez — DB'ye geri sek (eski offline/online dallarının
      // ikisi de returnCallToDb yapıyordu).
      returnCallToDb(call);
      return null;
    }
    // FIX-GLB90060295: buraya dusmek "ACD hic agent bulamadi" demektir ve cagri sessizce DB'ye veya
    // kuyrugun basina geri konur. Eskiden bu yolda tek satir log yoktu; uretimde kampanya saatlerce
    // yavaslasa bile log uzerinden gorunmuyordu (10.08.2026 incelemesi). Throttle'li WARN teshis
    // borcunu kapatir; hot path oldugu icin kampanya basina en fazla 10 sn'de bir satir basilir.
    logNoAvailableAgentThrottled(call);
    if (call.getCampaign().getSkillEnabled()) {
      returnCallToDb(call);
      return null;
    }
    currentCallQueue.addFirst(call);
    return null;
  }

  /**
   * ACD hicbir agent atayamadiginda kampanya basina throttle'li WARN basar.
   *
   * <p>Throttle kontrolu bilincli olarak kilitsizdir: iki thread ayni anda gecerse en fazla bir
   * fazla satir olusur, kayip veya bozulma olmaz.</p>
   *
   * @param call agent atanamayan cagri
   */
  private void logNoAvailableAgentThrottled(Call call) {
    Campaign campaign = call.getCampaign();
    if (campaign == null || campaign.getId() == null) {
      return;
    }
    long now = System.currentTimeMillis();
    Long nextAt = noAgentWarnNextAtByCampaignId.get(campaign.getId());
    if (nextAt != null && now < nextAt) {
      return;
    }
    noAgentWarnNextAtByCampaignId.put(campaign.getId(), now + NO_AGENT_WARN_THROTTLE_MS);
    // Argumansiz form bilincli: LoggerDecorator argumanli cagrilarda senkron ActionLog DB insert
    // yapar (bkz. LoggerDecorator.warn), bu yol ise cagri hot path'indedir.
    LOG.warn("[ACD-NO-AGENT] No agent could be assigned by ACD; call is deferred. campaignIdleUsers="
        + connectionDecorator.getConnectionComposition().getAcdEngine().getCampaignIdleUsersCount(campaign)
        + ", " + campaign + ", " + call);
  }

  /**
   * Move down and rotate call with callbacks in queue so that call without
   * callback appear at the top of the queue. For example: c - call with
   * callback, w - call without callback [c1,c2,c3,w1,w2] -> [w1,c2,c3,c1,w2]
   * @param callQueue
   */
  public void moveDownAndRotateAppointedCallbacks(BlockingDeque<Call> callQueue) {
    Call next = callQueue.poll();
    List<Call> callsWithCallbacks = new ArrayList<>();
    while (next != null && next.getUserIdAppointedToCallback() != null) {
      callsWithCallbacks.add(0, next);
      next = callQueue.poll();
    }
    Collections.rotate(callsWithCallbacks, 1);
    for (Call callWithCallback : callsWithCallbacks)
      if (!callQueue.offerFirst(callWithCallback))
        returnCallToDb(callWithCallback);

    if (next != null && !callQueue.offerFirst(next))
      returnCallToDb(next);
  }

  /**
   * Clear fetched flag for call
   * @param call
   */
  private void returnCallToDb(Call call) {
    connectionDecorator.getConnectionComposition().getCampaignDbOperations().removeFetchedDataFlagFromCallsAsync(call.getCampaign(), Collections.singletonList(call))
        .exceptionally(e -> {
          LOG.error(e, e, LOG_MODULE.DB, call.getCampaign());
          return null;
        });
  }

  /**
   * Task 321997 (ATTEMPT_COUNT): appointed callback sahibine atanamadığında (sahip AVAILABLE değil —
   * online-meşgul VEYA offline), plain return-to-DB churn yerine tek atomik reschedule/deneme-say
   * UPDATE'i tetikler: deneme sayacı +1 ve callback_date ötelenir; sayaç eşiğe ulaşınca callback
   * campaign-wide'a (user_id=NULL) devredilir (karar/sayım DB tarafında).
   * removeFetchedDataFlagFromCallsAsync ile aynı async/exceptionally desenini izler.
   */
  private void handleAppointedOwnerUnavailable(Call call) {
    connectionDecorator.getConnectionComposition().getCampaignDbOperations()
        .rescheduleAppointedCallbackAsync(call.getCampaign(), call)
        .exceptionally(e -> {
          LOG.error(e, e, LOG_MODULE.DB, call.getCampaign());
          return null;
        });
  }

  /**
   * Apply LSCs to call and send originate action if checks were successful
   * @param bulkOriginationId
   * @param call
   */
  private void applyLSCAndOriginate(String bulkOriginationId, Call call) {
    callsCountUnderLSCProcessing.incrementAndGet();
    lscExecutor.execute(() -> {
      try {
        switch (dialerFacade.getLscEngine().applyLscsToCall(call)) {
        case OK:
          processOrigination(bulkOriginationId, call);
          break;
        case FAIL:
          boolean allowRetries = allowRetriesForCallFailedLSCCheck(call);
          cancelCall(call, Constants.TELEPHONY_RC_BLOCKED_BY_LSC, allowRetries);
          break;
        case TIMEOUT:
          cancelCall(call, Constants.TELEPHONY_RC_LSC_TIMEOUT, true);
          break;
        }
      } finally {
        callsCountUnderLSCProcessing.decrementAndGet();
      }
    });
  }

  private boolean allowRetriesForCallFailedLSCCheck(Call call) {
    Campaign campaign = call.getCampaign();
    Boolean fullCheckEnabled = campaign != null ? campaign.getFullContactListCheckEnabled() : null;

    if (Boolean.TRUE.equals(fullCheckEnabled)) {
      List<String> numbersInRawData = call.getNumbersInRawData();
      String calledNumber = call.getCalledNumber();

      if (numbersInRawData != null && calledNumber != null) {
        Set<String> allNumbers = new HashSet<>(numbersInRawData);
        allNumbers.remove(calledNumber);

        if (!allNumbers.isEmpty()) {
          List<String> retriedNumbers = call.getRetryHistory();
          if (retriedNumbers != null) {
            return allNumbers.stream().anyMatch(number -> !retriedNumbers.contains(number));
          }
        }
      }
    }
    return false;
  }

  private void processOrigination(String bulkOriginationId, Call call) {
    if (isCampaignPaused(call)) {
      deferOriginationUntilCampaignResumes(call);
      return;
    }

    // FCT kapasite gate'i: rezervasyon originate'e EN YAKIN noktada alınır (LSC sonrası, AMI send
    // öncesi) — böylece LSC async işlemi veya timeout süresince FCT kanalı boşa tutulmaz.
    // Listedeki hiçbir FCT uygun değilse çağrı YAKILMAZ: attempt geri alınıp kuyruğun başına döner.
    if (!reserveFctChannel(call)) {
      deferOriginationUntilFctChannelAvailable(call);
      return;
    }
    call.resetFctChannelDeferCount();
    // Sabit hat fallback bütçesi her yeni origination denemesinde sıfırlanır: bütçe çağrının tüm
    // ömrü boyunca değil, TEK originate zinciri (N FCT + M sabit hat) boyunca geçerlidir.
    call.resetFixedLineAttemptCount();

    ManagerConnectionDecorator currentConnectionDecorator = connectionDecorator;
    Date originationDate = new Date();
    call.setOriginationDate(Utils.convertDateToDouble(originationDate));
    call.getCdr().setCallDialTime(originationDate);
    call.getCampaign().addToOriginatedCalls(call);

    callQueue.add(call);
    boolean originated = currentConnectionDecorator.originateCall(call);
    if (originated) {
      saveInitialCDR(call, currentConnectionDecorator);
    }

    publishDialerCallDialEvent(call, originationDate, currentConnectionDecorator);
  }

  private boolean isCampaignPaused(Call call) {
    Campaign campaign = call.getCampaign();
    return campaign != null && (CAMPAIGN_STATE.PAUSING.equals(campaign.getState()) || CAMPAIGN_STATE.PAUSED.equals(campaign.getState()));
  }

  private void deferOriginationUntilCampaignResumes(Call call) {
    cleanupCallBeforeDeferredOrigination(call);
    if (!returnCallToCampaignQueue(call)) {
      returnCallToDb(call);
    }
    LOG.info("Origination deferred because " + call.getCampaign() + " is paused. " + call, LOG_MODULE.CAMPAIGN, call.getCampaign());
  }

  /**
   * FCT kapasite rezervasyonunu dener. Gate bileşeni yoksa (Spring dışında kurulan testler) veya
   * çağrı gate kapsamında değilse originate akışı bugünkü gibi devam eder.
   */
  private boolean reserveFctChannel(Call call) {
    FctChannelGate gate = fctChannelGate();
    return gate == null || gate.reserveForOrigination(call);
  }

  /**
   * Listedeki tüm FCT'ler dolu veya cooldown'da: çağrı iptal EDİLMEZ, kampanya pause defer'iyle
   * aynı primitive kullanılarak attempt geri alınır, agent idle havuzuna döner ve çağrı kuyruğun
   * başına konur; bir sonraki tick'te yeniden denenir. Defer limiti aşılırsa mevcut
   * "callerId bulunamadı" akışına düşülür ki çağrı sonsuza kadar kuyrukta dönmesin.
   */
  private void deferOriginationUntilFctChannelAvailable(Call call) {
    int deferCount = call.incrementFctChannelDeferCount();
    int deferLimit = fctChannelDeferLimit();
    if (deferCount > deferLimit) {
      LOG.warn("FCT kapasite gate: defer limiti aşıldı (deferCount=" + deferCount + ", limit=" + deferLimit
          + "), çağrı no-callerId akışına düşürülüyor. " + call, LOG_MODULE.CAMPAIGN, call.getCampaign());
      call.resetFctChannelDeferCount();
      cancelCall(call, Constants.TELEPHONE_RC_NO_CALLERID_AVAILABLE, false);
      return;
    }
    cleanupCallBeforeDeferredOrigination(call);
    if (!returnCallToCampaignQueue(call)) {
      returnCallToDb(call);
    }
    LOG.warn("FCT kapasite gate: uygun FCT kanalı yok, origination ertelendi (deferCount=" + deferCount
        + "/" + deferLimit + "). " + call, LOG_MODULE.CAMPAIGN, call.getCampaign());
  }

  private int fctChannelDeferLimit() {
    FctChannelGate gate = fctChannelGate();
    return gate == null ? 0 : gate.getDeferLimit();
  }

  private FctChannelGate fctChannelGate() {
    ManagerConnectionComposition composition = connectionDecorator.getConnectionComposition();
    return composition == null ? null : composition.getFctChannelGate();
  }

  private void cleanupCallBeforeDeferredOrigination(Call call) {
    ManagerConnectionComposition managerConnectionComposition = connectionDecorator.getConnectionComposition();
    if (managerConnectionComposition.getCallUpToDateKeeper() != null) {
      managerConnectionComposition.getCallUpToDateKeeper().unregisterCall(call);
    }
    CallAttemptPolicy.restoreForDeferredOriginationIfNeeded(call);
    if (call.getUser() != null && managerConnectionComposition.getAcdEngine() != null) {
      managerConnectionComposition.getAcdEngine().addToIdleUsers(call.getUser());
      call.setUser(null);
    }
    call.setCallOriginator(null);
  }

  /**
   * Çağrıyı kampanyanın kuyruğunun başına geri koyar. Hem kampanya pause defer'i hem FCT kapasite
   * defer'i bu primitive'i kullanır.
   */
  private boolean returnCallToCampaignQueue(Call call) {
    Campaign campaign = call.getCampaign();
    CampaignContext campaignContext = campaign != null ? dialerFacade.getCampaignContexts().get(campaign.getId()) : null;
    if (campaignContext == null || campaignContext.getCallConsumer() == null || campaignContext.getCallConsumer().getCallQueue() == null) {
      return false;
    }
    return campaignContext.getCallConsumer().getCallQueue().offerFirst(call);
  }

  private void publishDialerCallDialEvent(Call call, Date dialDate, ManagerConnectionDecorator currentConnectionDecorator) {
    Campaign campaign = call.getCampaign();
    Long ivrId = campaign.isOutboundIvrCampaign() ? campaign.getIvrApplication().getId() : null;
    dialerFacade.getCallEventPublisher().publishCallDialEvent(call.getId(), campaign.getId(), dialDate, call.getCalledNumber(),
                                                              ivrId, currentConnectionDecorator.getServer().getName(),
                                                              call.getCallerId(), call.getCdr().getCallStartTime());
  }

  private boolean isFctRoutingPath(Call call) {
    Campaign campaign = call.getCampaign();
    return campaign != null
        && campaign.isFctRoutingEnabled()
        && Constants.CALLER_ID_BEHAVIOUR.POOL.equals(campaign.getCallerIdBehaviour());
  }

  private void handleNoAvailablePoolCallerId(Call call) {
    LOG.warn("Got no callerId for " + call + ". Terminating...");
    cancelCall(call, Constants.TELEPHONE_RC_NO_CALLERID_AVAILABLE, false);
  }

  /**
   * QSS'e ULAŞILAMADI (gRPC channel yok veya çağrı hata attı). Bu, "grup/route yok" durumundan
   * farklı olarak GEÇİCİ bir sorundur; bu yüzden çağrı retry'a izin verilerek iptal edilir.
   * Aksi hâlde kısa bir QSS/Eureka kesintisinde o an originate edilen tüm çağrılar kalıcı olarak
   * kaybedilirdi (eski davranış: hata da boş liste döndüğü için retry'sız iptal).
   *
   * <p>Log tek noktada burada basılır (facade artık loglamıyor) — kesinti sırasında originate
   * başına çift satır oluşmaz.
   */
  private void handleRouteDetailsUnavailable(Call call, RouteDetailsUnavailableException e) {
    LOG.error("QSS route details unavailable, call will be retried: " + call, e, LOG_MODULE.CAMPAIGN, call.getCampaign());
    cancelCall(call, Constants.TELEPHONE_RC_NO_CALLERID_AVAILABLE, true);
  }

  private void checkStopCondition(Campaign campaign) {
    ManagerConnectionComposition managerConnectionComposition = connectionDecorator.getConnectionComposition();
    if (managerConnectionComposition.getStopConditionChecker().isStopConditionReached(campaign)) {
      LOG.info(campaign + " has reached stop condition", LOG_MODULE.CAMPAIGN, campaign);
      managerConnectionComposition.getCampaignService().actionAsync(campaign.getId(), Constants.CAMPAIGN_ACTION.STOP_REGULAR, Constants.CAMPAIGN_STOP_TYPE.STOP_CONDITION);
    }
  }

  private void cancelCall(Call call, int telephonyResultCode, boolean allowRetries) {
    ManagerConnectionComposition managerConnectionComposition = connectionDecorator.getConnectionComposition();
    Campaign campaign = call.getCampaign();
    managerConnectionComposition.getCallUpToDateKeeper().unregisterCall(call);
    call.setTelephonyResultCode(getTelephonyResultCode(telephonyResultCode));
    CallAttemptPolicy.restoreForNonAttemptResultIfNeeded(call);
    call.incrementResultCodeSequence();
    campaign.incrementTelephonyResultCode(call.getTelephonyResultCode().getId());
    checkStopCondition(campaign);
    if (!allowRetries || !campaign.getRetryScheduleEnabled() || campaign.getRetryStrategy() == null) {
      callCleaner.completeCall(call);
    } else {
      applyRetryStrategy(call);
    }
    if (call.getUser() != null)
      managerConnectionComposition.getAcdEngine().addToIdleUsers(call.getUser());

    dialerFacade.getCampaignDbOperations().insertCallResultToDB(call);
    // Apply all CDR user ownership fields in a single operation
    UserPopulationEvaluator.populateCdrUserOwnership(call);
    saveCDR(call);
  }

  public void saveCDR(Call call) {
    saveCDR(call, new Date());
  }

  public void saveCDR(Call call, Date callEndTime) {
    CDR cdr = call.getCdr();
    cdr.setCallEndTime(callEndTime);
    cdr.setTelephonyRC(call.getTelephonyResultCode());
    cdr.setRcSequence(call.getResultCodeSequence());
    cdr.setFilter(call.getFilter());
    cdr.setUserResultCodeId(call.getUserResultCode() != null ? call.getUserResultCode().getId() : null);
    cdr.setAbandoned(call.isAbandoned());
    cdr.setServer(connectionDecorator.getServer());
    PhoneNumberColumn actualPhoneNumberColumn = call.getActualPhoneNumberColumn();
    int phoneNumberColumnOrder = call.getCampaign().getPhoneNumberColumnOrder(actualPhoneNumberColumn);
    cdr.setContactNumberOrder(phoneNumberColumnOrder);
    cdr.setContactNumberColumn(actualPhoneNumberColumn.getColNumber());
    if(call.getTelephonyResultCode().getCode() == Constants.TELEPHONE_RC_CAMPAIGN_IS_STOPPED_FORCEFULLY){
      try {
        cdr.setUserResultCodeId(Constants.USER_RC_CAMPAIGN_IS_STOPPED_FORCEFULLY_ID);
        dialerFacade.getCallEventPublisher().publishCallEndEvent(Objects.isNull(call.getId()) ? cdr.getInteractionId() : call.getId(), cdr.getCallEndTime());
        campaignResultService.updateUserResultCode(cdr, Constants.USER_RC_CAMPAIGN_IS_STOPPED_FORCEFULLY_ID);
        LOG.info("User result code updated for CDR: " + cdr.getId());
      } catch (Exception e) {
        LOG.error("Failed to update user result code for CDR: " + cdr.getId(), e);
      }
    }
    // Apply all CDR user ownership fields in a single operation
      UserPopulationEvaluator.populateCdrUserOwnership(call);

   // dialerFacade.getInteractionNotifierPublisher().publishDialerCallReport(call);

    this.dialerFacade.getCdrService().saveOrUpdate(cdr);
  }

  @SuppressWarnings("unchecked")
  private TelephonyResultCode getTelephonyResultCode(int telephonyResultCode) {
    Map<Integer, TelephonyResultCode> telephonyResultCodes = (Map<Integer, TelephonyResultCode>) dialerFacade.getServletContext()
        .getAttribute(Constants.TELEPHONY_RESULT_CODES);
    return telephonyResultCodes.get(telephonyResultCode);
  }

  public void applyRetryStrategy(Call call) {
    RetryStrategy retryStrategy = strategyFactory.createStrategy(call.getCampaign().getRetryStrategy());
    retryStrategy.createRetry(call);
  }

  /**
   * Delay call origination action within CPS_PERIOD
   * @param originateElementsSize
   */
  private void delayOrigination(int originateElementsSize) {
    if (originateElementsSize > 1) {
      int originationDelay = CPS_PERIOD / originateElementsSize;
      try {
        Thread.sleep(originationDelay);
      } catch (InterruptedException e) {
        LOG.error(e, e, LOG_MODULE.CAMPAIGN);
      }
    }
  }

  private void handleConnectionLoss() {
    cleanUpCallsOnConnectionLoss();
    setNewConnectionDecoratorIfExists();
  }

  /**
   * Clean-up calls from memory on connections loss
   */
  private void cleanUpCallsOnConnectionLoss() {
    Cache<String, Call> calls = connectionDecorator.getCalls();
    if (calls != null) {
      for (Call call : new ArrayList<>(connectionDecorator.getCalls().asMap().values())) {
        try {
          if (shouldRecoverAfterConnectionLoss(call)) {
            connectionDecorator.deferCallCleanupOnConnectionLoss(call);
            LOG.warn("Deferred clean-up due to connection loss for bridged " + call + " on " + connectionDecorator.getServer(),
                LOG_MODULE.ASTERISK, call.getCampaign());
            continue;
          }
          call.setTelephonyResultCode(connectionDecorator.getItuToTelephonyResultCodeRelations().get(ERROR_RESULT_CODES.AMI_CONNECTION_LOSS.getCode()));
          Campaign campaign = call.getCampaign();
          if (Boolean.TRUE.equals(campaign.getRetryScheduleEnabled()) && campaign.getRetryStrategy() != null) {
            applyRetryStrategy(call);
          } else {
            callCleaner.completeCall(call);
          }
          CallAttemptPolicy.restoreForNonAttemptResultIfNeeded(call);

          campaign.removeCallInProgress(call);

          if (call.getStartDate() != null && call.getAnswerDate() == null)
            campaign.decrementRingingCalls(call);

          if (call.getStartDate() != null && call.getAnswerDate() != null)
            campaign.decrementActiveCalls(call);

          if(call.isAnsweredByUser())
            campaign.decrementInCallsSize(call);

          connectionDecorator.clearCall(call);
          connectionDecorator.getDbOperations().insertCallResultToDB(call);

          saveCDR(call);

          ManagerConnectionComposition managerConnectionComposition = connectionDecorator.getConnectionComposition();

          if (call.getUser() != null)
            managerConnectionComposition.getAcdEngine().addToIdleUsers(call.getUser());

          LOG.warn("Clean-up due to connection loss for " + call + " on " + connectionDecorator.getServer() + " is processed", LOG_MODULE.ASTERISK, call.getCampaign());
        } catch (Exception e) {
          LOG.error("Exception occurred during clean-up of " + call, e);
        }
      }
    }
  }

  private boolean shouldRecoverAfterConnectionLoss(Call call) {
    return call != null
        && call.getUser() != null
        && call.getAnswerDate() != null
        && (call.isAnsweredByUser() || hasCallHandlingStarted(call));
  }

  private boolean hasCallHandlingStarted(Call call) {
    return call.getCdr() != null && call.getCdr().getCallHandlingStartTime() != null;
  }



  private void saveInitialCDR(Call call, ManagerConnectionDecorator currentConnectionDecorator) {
    try {
      CDR cdr = call.getCdr();
      if (cdr != null) {
        cdr.setServer(currentConnectionDecorator.getServer());
        cdr.setCallEndTime(new Date());
        cdr.setRcSequence(call.getResultCodeSequence());

        // Capture assigned user at dial time. In predictive mode the agent may be
        // reassigned to another call before hangup arrives, leaving call.getUser()
        // null at processCallResult time. Persisting here ensures the CDR retains
        // the original assignment even if the call-level user reference is cleared.
        User user = call.getUser();
        if (user != null) {
          cdr.setAssignedUser(user);
        }

        PhoneNumberColumn actualPhoneNumberColumn = call.getActualPhoneNumberColumn();
        if (actualPhoneNumberColumn == null) {
          LOG.warn("Skipping initial CDR save, actualPhoneNumberColumn is null for call: " + call);
          return;
        }
        int phoneNumberColumnOrder = call.getCampaign().getPhoneNumberColumnOrder(actualPhoneNumberColumn);
        cdr.setContactNumberOrder(phoneNumberColumnOrder);
        cdr.setContactNumberColumn(actualPhoneNumberColumn.getColNumber());

        this.dialerFacade.getCdrService().saveOrUpdate(cdr);
      }
    } catch (Exception e) {
      LOG.error("Failed to save initial CDR for call: " + call, e);
    }
  }

  /**
   * Checks is there a new instance of {@link ManagerConnectionDecorator} in the composition for given server.
   * If yes, the current connection decorator has to be replaced with the new one from the composition.
   */
  private void setNewConnectionDecoratorIfExists() {
    Server server = connectionDecorator.getServer();
    ManagerConnectionComposition connectionComposition = connectionDecorator.getConnectionComposition();
    ManagerConnectionDecorator newConnectionDecorator = connectionComposition.getOpenedConnections().get(server.getIp());
    if (newConnectionDecorator != null && connectionDecorator != newConnectionDecorator) {
      transferRecoverableConnectionLossCalls(connectionDecorator, newConnectionDecorator);
      LOG.info("AMI connections in the call originator and connection composition are differs. Updating the current one for " + this);
      // FIX-GLB90048674 (Faz 1B, invariant #2): eski decorator referansı bırakılmadan önce onun
      // event worker'ını drain edip kapat; aksi halde daemon worker thread'i sızar ve kuyrukta
      // bekleyen DB/CDR yazımları terk edilirdi (kayıpsızlık ihlali). DRAIN-only: AMI logoff
      // ÇAĞRILMAZ; bu hot/kırılgan recovery yolunun recovery semantiği değişmez.
      connectionDecorator.drainAndShutdownEventWorker();
      connectionDecorator = newConnectionDecorator;
      callQueue = new LinkedBlockingQueue<>();
    }
  }

  private void transferRecoverableConnectionLossCalls(ManagerConnectionDecorator sourceConnectionDecorator,
                                                       ManagerConnectionDecorator targetConnectionDecorator) {
    for (Call call : new ArrayList<>(sourceConnectionDecorator.getRecoverableConnectionLossCalls().asMap().values())) {
      targetConnectionDecorator.deferCallCleanupOnConnectionLoss(call);
      sourceConnectionDecorator.getRecoverableConnectionLossCalls().invalidate(call.getId());
    }
  }

  /**
   * Add given callQueue to list of callQueues
   * @param callQueue
   * @return true if added
   */
  public boolean addCallQueue(BlockingDeque<Call> callQueue) {
    return callQueues.addIfAbsent(callQueue);
  }

  /**
   * Remove callQueue from list of callQueues
   * @param callQueue
   * @return true if removed
   */
  public boolean removeCallQueue(BlockingDeque<Call> callQueue) {
    return callQueues.remove(callQueue);
  }

  /**
   * Remove call from callQueue
   * @param call
   */
  public void clearCall(Call call) {
    callQueue.remove(call);
  }

  public BlockingQueue<Call> getCallQueue() {
    return callQueue;
  }

  @Override
  public String toString() {
    return "CallOriginator [server=" + connectionDecorator.getServer() + ", callQueue=" + callQueue.size() + "]";
  }

}
