package com.powerdialer.engine.dialer;

import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.powerdialer.engine.dialer.endlistbehaviour.EndListBehaviour;
import com.powerdialer.engine.dialer.endlistbehaviour.EndListBehaviourFactory;
import com.powerdialer.engine.dialer.state.CampaignState;
import com.powerdialer.engine.dialer.state.PauseState;
import com.powerdialer.engine.dialer.state.StartState;
import com.powerdialer.engine.dialer.state.StopState;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.User;
import com.powerdialer.util.CampaignPropertyCopier;
import com.powerdialer.util.Constants.CAMPAIGN_ACTION;
import com.powerdialer.util.Constants.LOG_MODULE;
import com.powerdialer.util.LoggerDecorator;

/**
 * Context class that holds information and actions for campaign
 */
public class CampaignContext {

  private static final long STOP_EXECUTOR_IDLE_SECONDS = 60L;

  private final LoggerDecorator LOG;
  private Campaign campaign;
  private DialerFacade dialerFacade;
  private CampaignState startState;
  private CampaignState pauseState;
  private CampaignState stopState;
  private CampaignState campaignState;
  private CallProducer callProducer;
  private CallConsumer callConsumer;
  private EndListBehaviour endListBehaviour;
  private final ExecutorService campaignStopExecutor;
  private volatile Date liveSince;

  public CampaignContext(Campaign campaign,
                         DialerFacade dialerFacade,
                         CampaignFilterStatsCalculator campaignFilterStatsCalculator) {
    this.campaign = campaign;
    this.dialerFacade = dialerFacade;
    this.startState = new StartState(this);
    this.pauseState = new PauseState(this);
    this.stopState = new StopState(this);
    this.callProducer = new CallProducer(this, campaignFilterStatsCalculator);
    this.callConsumer = new CallConsumer(this);
    this.campaignState = stopState;
    this.endListBehaviour = EndListBehaviourFactory.create(this);
    this.campaignStopExecutor = createCampaignStopExecutor();
    this.LOG = dialerFacade.getLoggerDecorator().getLogger(CampaignContext.class);
  }

  /**
   * FIX-333607: tek thread'li, FIFO stop executor. Eskiden {@code Executors.newSingleThreadExecutor}
   * kullanılıyordu ve executor hiç kapatılmadığı için her stop/restart'ta bir daemon thread sızıyordu.
   * Executor kapatılmaz (restart akışında aynı context'e sonradan stop işi gelebilir); bunun yerine
   * boşta kalan thread kısa süre sonra kendiliğinden sonlanır.
   */
  public static ExecutorService createCampaignStopExecutor() {
    ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, STOP_EXECUTOR_IDLE_SECONDS, TimeUnit.SECONDS,
        new LinkedBlockingQueue<>(),
        new ThreadFactoryBuilder().setDaemon(true).setNameFormat("campaign-stop-executor-%d").build());
    executor.allowCoreThreadTimeOut(true);
    return executor;
  }

  public void setCampaignState(CampaignState campaignState) {
    this.campaignState = campaignState;
  }

  public Date getLiveSince() {
    return liveSince;
  }

  public void markLive(Date liveSince) {
    this.liveSince = liveSince;
  }

  public Campaign getCampaign() {
    return campaign;
  }

  public void updateCampaign(Campaign campaign) {
    CampaignPropertyCopier.copyProperties(campaign, this.campaign, CampaignPropertyCopier.TRANSIENT_FIELD_FILTER);;
    CampaignPropertyCopier.copyProperties(campaign, this.campaign, CampaignPropertyCopier.PERSISTENT_FIELD_FILTER);
    CampaignPropertyCopier.copyEntityReferences(
            campaign,
            this.campaign,
            dialerFacade::ref,
            new HashSet<>(Arrays.asList(
                    com.powerdialer.model.Number.class,
                    com.powerdialer.model.IVRApplication.class,
                    com.powerdialer.model.RoutingGroup.class
            ))
    );
    CampaignPropertyCopier.copyShallowRelationships(campaign, this.campaign);
    callConsumer.updateCampaignData();
    callConsumer.calculateOnlineAgentsSum();
    this.endListBehaviour = EndListBehaviourFactory.create(this);
  }




  public CampaignState getStartState() {
    return startState;
  }

  public CampaignState getPauseState() {
    return pauseState;
  }

  public CampaignState getStopState() {
    return stopState;
  }

  public CallProducer getCallProducer() {
    return callProducer;
  }

  public CallConsumer getCallConsumer() {
    return callConsumer;
  }

  public EndListBehaviour getEndListBehaviour() {
    return endListBehaviour;
  }

  public DialerFacade getDialerFacade() {
    return dialerFacade;
  }

  public void start() {
    campaignState.start();
  }

  public void pause() {
    campaignState.pause();
  }

  public void stop(CAMPAIGN_ACTION action) {
    campaignState.stop(action);
  }

  public void reload() {
    campaignState.reload();
  }

  public void reset() {
    campaignState.reset();
  }

  public void startCampaignStopJob(CAMPAIGN_ACTION action) {
    CampaignStopJob campaignStopJob = new CampaignStopJob(this, action);
    campaign.setCampaignStopJob(campaignStopJob);
    CompletableFuture.runAsync(campaignStopJob, campaignStopExecutor).exceptionally(this::handleCampaignTerminationError);
  }

  private Void handleCampaignTerminationError(Throwable e) {
    LOG.error(e + " during " + campaign + " termination", e, LOG_MODULE.CAMPAIGN, campaign);
    campaign.setCampaignStopJob(null);
    return null;
  }

}
