package com.powerdialer.engine.dialer;

import java.sql.SQLException;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;

import com.powerdialer.engine.predictive.beta.processor.PredictiveBetaCallManager;
import com.powerdialer.engine.predictive.gamma.model.GammaRingingLoadModel;
import com.powerdialer.engine.predictive.gamma.model.PredictiveGammaCallData;
import com.powerdialer.engine.predictive.gamma.processor.PredictiveGammaCallManager;
import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.User;
import com.powerdialer.util.Constants;
import com.powerdialer.util.Constants.CAMPAIGN_STATE;
import com.powerdialer.util.Constants.LOG_MODULE;
import com.powerdialer.util.LoggerDecorator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Consumer that drains data from dataQueue to callQueue by given cps period and
 * callQueue remainder size.
 */
public class CallConsumer extends CallProducerConsumerTemplate {

  private static final int CPS_PERIOD = 1000;
  private static final int GAMMA_CPS_PERIOD = 2000;
  private static final double EPSILON = 0.000001d;


  private final LoggerDecorator LOG;
  private BlockingDeque<Call> callQueue;
  private int callQueueSoftLimit;
  private final PredictiveBetaCallManager predictiveBetaCallManager;

  public CallConsumer(CampaignContext campaignContext) {
    super(campaignContext);
    updateCampaignData();
    this.dataQueue = campaignContext.getCallProducer().getDataQueue();
    this.callQueue = new LinkedBlockingDeque<>();
    this.LOG = campaignContext.getDialerFacade().getLoggerDecorator().getLogger(CallConsumer.class);
    this.predictiveBetaCallManager = campaignContext.getDialerFacade().getPredictiveBetaCallManager();
  }

  public void updateCampaignData() {
    this.callQueueSoftLimit = Math.max(1, campaignContext.getCampaign().getConcurrentLines());
  }

  @Override
  public void run() {
    int schedulePeriod = campaignContext.getCampaign().isPredictiveCampaign() && Constants.PREDICTIVE_STRATEGY.GAMMA.equals(campaignContext.getCampaign().getPredictiveStrategy())
            ? GAMMA_CPS_PERIOD
            : CPS_PERIOD;
    scheduledFuture = scheduledExecutorService.scheduleAtFixedRate(() -> {
      if (CAMPAIGN_STATE.RUNNING.equals(campaignContext.getCampaign().getState())) {
        try {
          pollCallFromProducerToConsumer();
          calculateCampaignParameters();
        } catch (Exception e) {
          LOG.error(e, e, LOG_MODULE.CAMPAIGN, campaignContext.getCampaign());
        }
      }
    }, 0, schedulePeriod, TimeUnit.MILLISECONDS);
  }

  /**
   * Get a call from dataQueue(producer) and add them to callQueue(consumer) if
   * there is a place in callQueue
   */
  private void pollCallFromProducerToConsumer() {
    int remainingSoftCapacity = callQueueSoftLimit - callQueue.size();
    int drainedCallbacks = 0;
    int concurrentLines = getCampaignContext().getCampaign().getConcurrentLines();
    if (campaignContext.getCampaign().isPredictiveCampaign() && Constants.PREDICTIVE_STRATEGY.GAMMA.equals(campaignContext.getCampaign().getPredictiveStrategy())) {
      int cps = campaignContext.getCampaign().getCallsPerSecond();
      int maxCallCount = Math.max(0, Math.min(cps - callQueue.size(), remainingSoftCapacity));
      PredictiveGammaCallManager gammaCallManager = campaignContext.getDialerFacade().getPredictiveGammaCallManager();
      PredictiveGammaCallManager.GammaDialBudget dialBudget = gammaCallManager.calculateDialBudget(
              campaignContext.getCampaign(), cps, remainingSoftCapacity, campaignContext.getDialerFacade(), dataQueue.peek());

      // Backpressure guard — AMI bağlantı kaybı veya CallOriginator yavaşlamasında
      // sınırsız backlog birikmesini engeller. Sağlıklı state'te tick anında
      // callQueue boştur (originator her saniye CPS hızında boşaltır, consumer
      // 2 saniyede bir CPS kadar ekler). callQueue CPS'e dayandıysa originator
      // yetişemiyor demektir; yeni contact eklemeyi atla, mevcut bekleyenler
      // boşalsın. Formül zaten koştu (Holt/window state güncel); drain ve
      // starvation update atlanır. Telemetri: drainedCallCount=0 ile event
      // yayımlanır → analyzer (drained=0 AND queueSize>=cps) deseninden
      // backpressure'ı türetir.
      boolean backpressureActive = callQueue.size() >= cps;
      int drainSize = 0;
      if (!backpressureActive) {
        drainSize = drainWithBudget(dialBudget.getTotalDialBudget(), maxCallCount, concurrentLines, dialBudget.getRingingLoadModel());
        int starvationDrainCount = maxCallCount > 0 ? drainSize : 1;
        gammaCallManager.updateStarvationCredit(campaignContext.getCampaign(), dialBudget.getBaseDialBudget(), dialBudget.getNextCallCost(), starvationDrainCount, campaignContext.getDialerFacade());
      }
      PredictiveGammaCallData callData = dialBudget.getCallData();
      if (callData != null) {
        if (!backpressureActive) {
          callData.setStarvationCreditAfter(gammaCallManager.getStarvationCredit(campaignContext.getCampaign().getId()));
        }
        // MADDE 3A telemetrisi: partiye yazilan toplam guvenlik payi ve taban kuralinin
        // devreye girip girmedigi. Bayrak kapaliyken model null -> alanlar null kalir.
        GammaRingingLoadModel loadModel = dialBudget.getRingingLoadModel();
        if (loadModel != null) {
          callData.setDrainMarginalCost(loadModel.getChargedMarginalCost());
          callData.setDrainFloorApplied(loadModel.isFirstCallFloorApplied());
        }
        callData.setDrainedCallCount(drainSize);
        callData.setMaxCallCount(maxCallCount);
        callData.setRemainingCapacity(remainingSoftCapacity);
        callData.setCacheSize(dataQueue.size());
        callData.setQueueSize(callQueue.size());
        gammaCallManager.dispatchEvent(callData);
      }
      if (drainSize > 0)
        LOG.info("Calls drained to CallConsumer (GAMMA) for " + campaignContext.getCampaign() + ", Size=" + drainSize);
    } else {
      int canDrainElements = calculateCanDrainElements(remainingSoftCapacity);
      int callbacksInCallQueue = (int) callQueue.stream().filter(call -> call.getCallType().equals(Constants.CALL_TYPES.CALLBACK)).count();
      if (callbacksInCallQueue < callQueue.size()) {
        while (dataQueue.peek() != null && dataQueue.peek().getCallType().equals(Constants.CALL_TYPES.CALLBACK)) {
          if (canDrainElements <= 0) {
            canDrainElements += cleanQueue(callQueue, 1, concurrentLines);
          }
          if (callQueue.size() < concurrentLines) {
            Call call = dataQueue.poll();
            if (call != null && callQueue.offerFirst(call)) {
              drainedCallbacks++;
              canDrainElements--;
            }
          }
        }
      }
      int drainSize = dataQueue.drainTo(callQueue, canDrainElements);
      if (drainSize > 0 || drainedCallbacks > 0)
        LOG.info("Calls drained to CallConsumer for " + campaignContext.getCampaign() + ", Size=" + (drainSize + drainedCallbacks));
    }

    if (callQueue.removeIf(Objects::isNull)) {
      LOG.warn("Nullable elements removed from call queue for " + getCampaignContext().getCampaign());
    }
  }

  /**
   * Calculate how many elements we can drain from dataQueue to callQueue
   * @param remainingSoftCapacity - remaining soft capacity of callQueue
   * @return number of elements that we can drain
   */
  private int calculateCanDrainElements(int remainingSoftCapacity) {

    if (campaignContext.getCampaign().isPredictiveCampaign()
            && Constants.PREDICTIVE_STRATEGY.BETA.equals(campaignContext.getCampaign().getPredictiveStrategy())) {


      return Math.min(remainingSoftCapacity,
              Math.min(campaignContext.getCampaign().getCallsPerSecond(),
                      predictiveBetaCallManager.getPredictiveTotalNumberOfCall(campaignContext.getCampaign().getId())));
    }

    // NOT: burada eskiden bir GAMMA dalı vardı. Ulaşılamıyordu — gamma yolu
    // pollCallFromProducerToConsumer içinde if/else ile zaten ayrılıyor ve bu metot
    // yalnız else kolundan çağrılıyor. Ulaşılsaydı bütçeyi ikinci kez hesaplar, AR map'lerini
    // tekrar yazar ve Holt state'ini bozardı. Kaldırıldı.

    return Math.min(remainingSoftCapacity, campaignContext.getCampaign().getCallsPerSecond());
  }

  /**
   * Return calls count is call queue
   */
  public int getCallsCountInCallQueue() {
    return callQueue.size();
  }

  /**
   * Set necessary campaign parameters that we can calculate in this consumer.
   * @implNote in some cases callQueue.size() could exceed callQueueSoftLimit. It depends on CallOriginator.getCallFromCallQueues()
   * realization. Generally it is not a problem but we can hide it.
   */
  @Override
  protected void calculateCampaignParameters() {
    campaignContext.getCampaign().setCacheSize(dataQueue.size());
    campaignContext.getCampaign().setQueueSize(callQueue.size());
    if(dataQueue.size() > campaignContext.getCampaign().getCacheLimit()) {
      LOG.warn("Campaign " + campaignContext.getCampaign().getId() + " cache size set to " + dataQueue.size() + ", cache limit : " + campaignContext.getCampaign().getCacheLimit());
    }

    if(callQueue.size() > campaignContext.getCampaign().getConcurrentLines()) {
      LOG.warn("Campaign " + campaignContext.getCampaign().getId() + " queue size set to " + callQueue.size() + ", concurrentlines : " + campaignContext.getCampaign().getConcurrentLines());
    }

  }

  public void calculateOnlineAgentsSum() {
    Campaign campaign = campaignContext.getCampaign();
    List<User> campaignUsers = campaignContext.getDialerFacade().getAcdEngine().getCampaignAllActiveUsers(campaign);
    campaign.setAgentsSize(campaignUsers.size());

    int availCount = campaignContext.getDialerFacade()
            .getAcdEngine()
            .countCampaignAvailableUsersNow(campaign);
    campaign.setAvailAgentsSize(availCount);
  }


  /**
   * Set fetchedRow flag back to null for callQueue items
   */
  @Override
  public void setRowFetchedFlagsToNull() {
    try {
      campaignContext.getDialerFacade().getCampaignDbOperations().removeFetchedDataFlagFromCalls(campaignContext.getCampaign(), convertFromBlockingQueueToList(callQueue));
    } catch (SQLException e) {
      LOG.error(e, e, LOG_MODULE.DB, campaignContext.getCampaign());
    }
  }

  @Override
  protected LoggerDecorator getLog() {
    return LOG;
  }

  public BlockingDeque<Call> getCallQueue() {
    return callQueue;
  }

  private int drainWithBudget(double dialBudget, int maxCallCount, int concurrentLines) {
    return drainWithBudget(dialBudget, maxCallCount, concurrentLines, null);
  }

  /**
   * @param ringingLoadModel MADDE 3A — Gamma Plus'ta çıkan partiyi marjinal UCB ile fiyatlar.
   *                         {@code null} ise (bayrak kapalı) bugünkü sabit AR maliyeti kullanılır
   *                         ve davranış bit-bit aynı kalır.
   */
  private int drainWithBudget(double dialBudget, int maxCallCount, int concurrentLines, GammaRingingLoadModel ringingLoadModel) {
    double budget = dialBudget;
    int drained = 0;
    long cid = campaignContext.getCampaign().getId();
    PredictiveGammaCallManager gammaCallManager = campaignContext.getDialerFacade().getPredictiveGammaCallManager();

    int callbacksInCallQueue = (int) callQueue.stream().filter(call -> Constants.CALL_TYPES.CALLBACK.equals(call.getCallType())).count();
    if (callbacksInCallQueue < callQueue.size()) {
        while (dataQueue.peek() != null && Constants.CALL_TYPES.CALLBACK.equals(dataQueue.peek().getCallType())) {

        if (drained >= maxCallCount || budget <= 0.0) break;

        if (callQueue.size() >= concurrentLines) {
          int cleaned = cleanQueue(callQueue, 1, concurrentLines);
          if (cleaned == 0) break;
        }

        if (callQueue.size() < concurrentLines) {
          Call candidate = dataQueue.peek();
          if (candidate == null) {
            break;
          }

          double cost = resolveDrainCost(candidate, cid, gammaCallManager, ringingLoadModel);
          if (!isAffordable(budget, cost, drained, candidate, cid, gammaCallManager, ringingLoadModel)) {
            break;
          }

          Call call = dataQueue.poll();
          if (call != null && callQueue.offerFirst(call)) {
            budget -= cost;
            drained++;
            if (ringingLoadModel != null) ringingLoadModel.commit(call.getCallType());
          }
        } else {
          break;
        }
      }
    }

    while (!dataQueue.isEmpty() && drained < maxCallCount && callQueue.size() < concurrentLines && budget > 0.0) {
      Call candidate = dataQueue.peek();
      if (candidate == null) break;

      double cost = resolveDrainCost(candidate, cid, gammaCallManager, ringingLoadModel);
      if (!isAffordable(budget, cost, drained, candidate, cid, gammaCallManager, ringingLoadModel)) break;

      Call call = dataQueue.poll();
      if (call == null) continue;

      callQueue.offer(call);
      budget -= cost;
      drained++;
      if (ringingLoadModel != null) ringingLoadModel.commit(call.getCallType());
    }

    return drained;
  }

  /**
   * MADDE 3A — aynı risk, bulunduğu yere göre farklı fiyatlanmasın.
   *
   * <p>Bugün çıkarılırken {@code cost = AR} (ortalama kadar cevap beklenir, varyans payı YOK),
   * çalarken ise {@code ringingLoad} paylı. Pay üreten tek terim zaten çalanları görüyor;
   * havuz boşalınca pay sıfırlanıyor. Uç hâli kampanya açılışı: 40 agent'a 40 cevap
   * beklentisiyle ~80 numara, sapma için hiç yer yok.
   *
   * <p>Yeni emniyet katmanı eklenmez, var olan tutarlı uygulanır:
   * {@code cost(n) = UCB(R + n + 1) − UCB(R + n)}. Parti için toplanan pay
   * {@code UCB(R+B) − UCB(R)} — bir sonraki tick'in freninin göreceğinin aynısı. Çifte sayım
   * yok, öne alma var.
   */
  private double resolveDrainCost(Call candidate, long cid, PredictiveGammaCallManager gammaCallManager, GammaRingingLoadModel ringingLoadModel) {
    if (ringingLoadModel == null) {
      return gammaCallManager.getAnswerRateCost(candidate, cid);
    }
    return ringingLoadModel.marginalCost(candidate.getCallType());
  }

  /**
   * MADDE 3A kalibrasyonu — UCB'nin normal yaklaşımı N=1'de geçersizdir.
   *
   * <p>{@code p = 0,50} (startup sabiti) iken havuz boşken ilk çağrının marjinal maliyeti
   * {@code 1,32 > 1,0} çıkar. Tek boş agent'lı ve havuzu boş bir kampanya bu yüzden hiçbir tick'te
   * arama yapamaz hale gelirdi — kalıcı kilitlenme; MADDE 5'in kapatmayı hedeflediği küçük takım
   * senaryosunu da geri getirirdi. Havuzda tek bekleyen çağrı en fazla 1 agent'ı meşgul
   * edebileceği için, partinin İLK çağrısında ortalama fiyat tabanı yeterli kabul edilir.
   * Sonraki çağrılar tam marjinal UCB fiyatını öder.
   *
   * <p><b>Taban neden {@code AR_dial}:</b> Gamma Plus tek çağrılık durumda bayrak kapalı hâlden
   * ASLA daha kısıtlayıcı olmamalı. {@code AR_ring >= AR_dial} olduğu için taban {@code AR_ring}
   * seçilseydi, bütçesi {@code [AR_dial, AR_ring)} aralığına düşen kampanyalar bayrak kapalıyken
   * arama yapıp bayrak açıkken yapamaz olurdu — hem gaz maddelerinin amacına ters, hem de
   * starvation makinesi {@code nextCallCost}'u {@code AR_dial} ile ölçtüğü için o kampanyalar
   * kalıcı olarak sıfır aramada takılabilirdi (kredi de {@code freshBudget >= nextCallCost}
   * dalına düşüp sıfırlanır, kurtarma gelmez).
   */
  private boolean isAffordable(double budget, double cost, int drained, Call candidate, long cid,
                               PredictiveGammaCallManager gammaCallManager, GammaRingingLoadModel ringingLoadModel) {
    // !(a < b) ile yazıldı: eski davranışın NaN semantiği birebir korunur (a >= b değil).
    if (!(budget + EPSILON < cost)) return true;
    if (ringingLoadModel == null || drained > 0) return false;
    if (budget + EPSILON < gammaCallManager.getAnswerRateCost(candidate, cid)) return false;
    ringingLoadModel.markFirstCallFloorApplied();
    return true;
  }

  @Override
  public String toString() {
    return "CallConsumer [campaign=" + campaignContext.getCampaign() + ", callQueue=" + callQueue.size() + "]";
  }
}
