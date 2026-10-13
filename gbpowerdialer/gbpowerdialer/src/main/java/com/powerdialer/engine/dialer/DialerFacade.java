package com.powerdialer.engine.dialer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.PreDestroy;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.servlet.ServletContext;

import com.powerdialer.engine.boost.DynamicBoostManager;
import com.powerdialer.engine.predictive.beta.processor.PredictiveBetaCallManager;
import com.powerdialer.engine.predictive.gamma.processor.PredictiveGammaCallManager;
import com.powerdialer.redis.InteractionNotifierPublisher;
import com.powerdialer.service.CampaignResultService;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.powerdialer.engine.CallCleaner;
import com.powerdialer.engine.DNCEngine;
import com.powerdialer.engine.LSCEngine;
import com.powerdialer.engine.StopConditionChecker;
import com.powerdialer.engine.acd.ACDEngine;
import com.powerdialer.engine.asterisk.CallerIdManager;
import com.powerdialer.engine.asterisk.ami.ManagerConnectionComposition;
import com.powerdialer.engine.asterisk.ami.ManagerConnectionDecorator;
import com.powerdialer.engine.db.CampaignDbOperations;
import com.powerdialer.exceptions.CampaignIllegalStateException;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.Location;
import com.powerdialer.model.Server;
import com.powerdialer.redis.CallEventPublisher;
import com.powerdialer.service.CDRService;
import com.powerdialer.service.CampaignDataTypeChecker;
import com.powerdialer.service.CampaignMementoService;
import com.powerdialer.service.ResultCodeScheduleService;
import com.powerdialer.service.RoutingGroupService;
import com.powerdialer.service.CampaignResultJobService;
import com.powerdialer.util.Constants.CAMPAIGN_ACTION;
import com.powerdialer.util.Constants.CAMPAIGN_STATE;
import com.powerdialer.util.Constants.SERVER_TYPE;
import com.powerdialer.util.LoggerDecorator;

/**
 * Core engine to manipulate with campaigns and asterisk servers. It creates
 * instances of classes that fetches data from db, creates calls and originates
 * them to asterisk.
 */
@Component
@Lazy(false)
public class DialerFacade implements ApplicationEventPublisherAware {

  private static final Log LOG = LogFactory.getLog(DialerFacade.class);

  private final Map<Long, CampaignContext> campaignContexts = new ConcurrentHashMap<>();
  private final Map<Long, CallOriginator> asteriskCallOriginators = new ConcurrentHashMap<>();
  private DNCEngine dncEngine;
  private com.powerdialer.engine.JITExpireValidator jitExpireValidator;

  @Autowired
  private ApplicationContext applicationContext;

  @Autowired
  private ServletContext servletContext;

  @Autowired
  private CampaignDbOperations campaignDbOperations;

  @Autowired
  private ManagerConnectionComposition connectionComposition;

  @Autowired
  private CampaignMementoService campaignMementoService;

  @Autowired
  private CampaignResultJobService campaignResultJobService;

  @Autowired
  private LSCEngine lscEngine;

  @Autowired
  private LoggerDecorator loggerDecorator;

  @Autowired
  private CampaignDataTypeChecker campaignDataTypeChecker;

  @Autowired
  private ACDEngine acdEngine;

  @Autowired
  private ApplicationEventPublisher campaignPublisher;

  @Autowired
  private StopConditionChecker stopConditionChecker;

  @Autowired
  private CallerIdManager callerIdManager;

  @Autowired
  private CallCleaner callCleaner;

  @Autowired
  private RoutingGroupService routingGroupService;

  @Autowired
  private CDRService cdrService;

  @Autowired
  private ResultCodeScheduleService resultCodeScheduleService;

  @Autowired
  private CallEventPublisher callEventPublisher;

  @Autowired
  private InteractionNotifierPublisher interactionNotifierPublisher;

  @Autowired
  private CampaignFilterStatsCalculator campaignFilterStatsCalculator;

  @Autowired
  private DynamicBoostManager dynamicBoostManager;

  @Autowired
  private PredictiveBetaCallManager predictiveBetaCallManager;

  @Autowired
  private PredictiveGammaCallManager predictiveGammaCallManager;

  @Autowired
  private CampaignRealTimeRingingManager campaignRealTimeRingingManager;

  @Autowired
  private CampaignResultService campaignResultService;

  @PersistenceContext
  private EntityManager em;

  private Integer maxLines = 0;

  public PredictiveBetaCallManager getPredictiveBetaCallManager() {
    return predictiveBetaCallManager;
  }

  public PredictiveGammaCallManager getPredictiveGammaCallManager() {
    return predictiveGammaCallManager;
  }

  public CampaignRealTimeRingingManager getCampaignRealTimeRingingManager() {
    return campaignRealTimeRingingManager;
  }

  public DynamicBoostManager getDynamicBoostManager() {
    return dynamicBoostManager;
  }

  public Map<Long, CampaignContext> getCampaignContexts() {
    return campaignContexts;
  }

  public Map<Long, CallOriginator> getAsteriskCallOriginators() {
    return asteriskCallOriginators;
  }

  public ServletContext getServletContext() {
    return servletContext;
  }

  public CampaignDbOperations getCampaignDbOperations() {
    return campaignDbOperations;
  }

  public ManagerConnectionComposition getConnectionComposition() {
    return connectionComposition;
  }

  public CampaignMementoService getCampaignMementoService() {
    return campaignMementoService;
  }

  public CampaignResultJobService getCampaignResultJobService() {
    return campaignResultJobService;
  }

  public LoggerDecorator getLoggerDecorator() {
    return loggerDecorator;
  }

  public RoutingGroupService getRoutingGroupService() {
    return routingGroupService;
  }

  public CDRService getCdrService() {
    return cdrService;
  }

  public ResultCodeScheduleService getResultCodeScheduleService() {
    return resultCodeScheduleService;
  }

  public <T> T ref(Class<T> type, Long id) {
    return em.getReference(type, id);
  }

  public DNCEngine getDncEngine() {
    if (dncEngine == null)
      dncEngine = (DNCEngine) applicationContext.getBean("dncEngine");
    return dncEngine;
  }

  public com.powerdialer.engine.JITExpireValidator getJitExpireValidator() {
    if (jitExpireValidator == null)
      jitExpireValidator = (com.powerdialer.engine.JITExpireValidator) applicationContext.getBean("jitExpireValidator");
    return jitExpireValidator;
  }

  public LSCEngine getLscEngine() {
    return lscEngine;
  }

  public ACDEngine getAcdEngine() {
    return acdEngine;
  }

  public CallEventPublisher getCallEventPublisher() {
    return callEventPublisher;
  }

  public InteractionNotifierPublisher getInteractionNotifierPublisher() {
    return interactionNotifierPublisher;
  }

  public ApplicationEventPublisher getPublisher() {
    return campaignPublisher;
  }

  public void setApplicationEventPublisher(ApplicationEventPublisher campaignPublisher) {
    this.campaignPublisher = campaignPublisher;
  }

  public StopConditionChecker getStopConditionChecker() {
    return stopConditionChecker;
  }

  /**
   * Listener method for {@code ApplicationContext}'s initialized and refreshed
   * events
   * @param event
   */
  @EventListener
  public void onContextRefresh(ContextRefreshedEvent event) {
    startCallOriginators();
  }

  /**
   * Create asteriskCallOriginators that will consume calls from
   * callProducerConsumerContexts
   */
  public void startCallOriginators() {
    for (ManagerConnectionDecorator connectionDecorator : connectionComposition.getOpenedConnections().values()) {
      if (connectionDecorator.getServer().isInUse() && !asteriskCallOriginators.containsKey(connectionDecorator.getServer().getId())) {
        CallOriginator callOriginator = new CallOriginator(connectionDecorator, this, callerIdManager, callCleaner, campaignResultService);
        callOriginator.start();
        asteriskCallOriginators.put(connectionDecorator.getServer().getId(), callOriginator);
        maxLines = connectionDecorator.getServer().getMaxLines();
      }
    }
    LOG.info("DialerEngine started. " + asteriskCallOriginators.size() + " servers ready to consume calls.");
  }

  /**
   * Start {@link CallOriginator} for server
   */
  public void startCallOriginatorForServer(Server server) {
    if (server.getType() != SERVER_TYPE.REGISTRAR) {
      LOG.warn("Could not create call originator for non-registrar server: " + server);
      return;
    }
    ManagerConnectionDecorator connectionDecorator = connectionComposition.getOpenedConnections().get(server.getIp());
    if (connectionDecorator != null && !asteriskCallOriginators.containsKey(connectionDecorator.getServer().getId())) {
      CallOriginator callOriginator = new CallOriginator(connectionDecorator, this, callerIdManager, callCleaner, campaignResultService);
      addCallQueuesToCallOriginator(callOriginator);
      callOriginator.start();
      asteriskCallOriginators.put(connectionDecorator.getServer().getId(), callOriginator);
      LOG.info(callOriginator + " started");
    }
  }

  private void addCallQueuesToCallOriginator(CallOriginator callOriginator) {
    for (CampaignContext context : campaignContexts.values()) {
      if (shouldAddQueueToOriginator(context.getCampaign(), callOriginator)) {
        LOG.info("Adding call queue to " + callOriginator + ": " + context.getCampaign());
        callOriginator.addCallQueue(context.getCallConsumer().getCallQueue());
      }
    }
  }

  private boolean shouldAddQueueToOriginator(Campaign campaign, CallOriginator callOriginator) {
    if (campaign.getState() == CAMPAIGN_STATE.STOPPED || campaign.getState() == CAMPAIGN_STATE.STOPPING
        || campaign.getState() == CAMPAIGN_STATE.PAUSED || campaign.getState() == CAMPAIGN_STATE.PAUSING)
      return false;
    for (Location location : campaign.getLocations()) {
      for (Server server : location.getServers()) {
        if (server.getIp().equals(callOriginator.getConnectionDecorator().getServer().getIp()))
          return true;
      }
    }
    return false;
  }

  /**
   * Stop {@link CallOriginator} linked with given server
   */
  public void stopCallOriginatorForServer(Server server) {
    Iterator<Map.Entry<Long, CallOriginator>> iterator = asteriskCallOriginators.entrySet().iterator();
    while (iterator.hasNext()) {
      CallOriginator callOriginator = iterator.next().getValue();
      if (callOriginator.getConnectionDecorator().getServer().equals(server)) {
        callOriginator.stop();
        iterator.remove();
        return;
      }
    }
  }

  /**
   * Create callFactory for given campaign with producer and consumer, pass
   * created callQueue to asterisk servers by location
   * @param campaign
   */
  public void startCampaign(Campaign campaign) {
    checkColumnsAndDBConnection(campaign);
    campaign.setState(CAMPAIGN_STATE.STARTING);
    campaign.setMaxLines(maxLines);
    CampaignContext campaignContext = campaignContexts.get(campaign.getId());
    if (campaignContext == null) {
      createAndStartNewContext(campaign);
    } else {
      if (!CAMPAIGN_STATE.RUNNING.equals(campaignContext.getCampaign().getState()) && !CAMPAIGN_STATE.STARTING.equals(campaignContext.getCampaign().getState())) {
        if (campaignNeedToBeRestarted(campaignContext)) {
          updateCampaignTransientFields(campaign, campaignContext.getCampaign());
          createAndStartNewContext(campaign);
        } else {
          unpauseCampaign(campaign.getId());
        }
      }
    }
  }

  /**
   * Check if campaign need to be restarted
   * @param campaignContext
   * @return true if there is existing context for campaign and state is not
   *         paused
   */
  private boolean campaignNeedToBeRestarted(CampaignContext campaignContext) {
    return campaignContext != null && !CAMPAIGN_STATE.PAUSED.equals(campaignContext.getCampaign().getState());
  }

  /**
   * Updates the campaign context data "on fly", without of campaign restart
   * @param campaign to update
   */
  public void updateContext(Campaign campaign) {
    Optional.ofNullable(campaign)
            .map(Campaign::getId)
            .map(campaignContexts::get)
            .ifPresent(context -> {
              campaign.setState(context.getCampaign().getState());
              context.updateCampaign(campaign);
              dncEngine.loadDNCForCampaign(campaign);
              lscEngine.loadLSCForCampaign(campaign);
              LOG.info("Campaign context for " + campaign + " has updated");
            });
  }

  /**
   * Create new campaign context We have to get dialerFacade via
   * connectionComposition because we need a Spring proxy instead of real
   * instance. It`s required for aspect intercepting.
   * @param campaign
   */
  private void createAndStartNewContext(Campaign campaign) {
    CampaignContext campaignContext = new CampaignContext(campaign, connectionComposition.getDialerFacade(), campaignFilterStatsCalculator);
    campaignContext.start();
  }

  /**
   * Update real time data of campaign with new configuration using data from
   * campaign with old configuration
   * @param campaignWithNewConfiguration campaign with old settings
   * @param campaignWithOldConfiguration campaign with new settings
   */
  private void updateCampaignTransientFields(Campaign campaignWithNewConfiguration, Campaign campaignWithOldConfiguration) {
    campaignWithNewConfiguration.getListRemaining().updateAndGet(n -> n + campaignWithOldConfiguration.getListRemaining().get());
    campaignWithNewConfiguration.getCallsMade().updateAndGet(n -> n + campaignWithOldConfiguration.getCallsMade().get());
    campaignWithNewConfiguration.getActiveCalls().updateAndGet(n -> n + campaignWithOldConfiguration.getActiveCalls().get());
    campaignWithNewConfiguration.getLiveCalls().updateAndGet(n -> n + campaignWithOldConfiguration.getLiveCalls().get());
    campaignWithNewConfiguration.getRingingCalls().updateAndGet(n -> n + campaignWithOldConfiguration.getRingingCalls().get());
    campaignWithNewConfiguration.getNoConnectionCalls().updateAndGet(n -> n + campaignWithOldConfiguration.getNoConnectionCalls().get());
    campaignWithNewConfiguration.getTotalCallDuration().updateAndGet(n -> n + campaignWithOldConfiguration.getTotalCallDuration().get());
    campaignWithNewConfiguration.clearCallsInProgress();
    campaignWithNewConfiguration.getCallsInProgress().addAll(campaignWithOldConfiguration.getCallsInProgress());
    campaignWithNewConfiguration.getRetriesMade().updateAndGet(n -> n + campaignWithOldConfiguration.getRetriesMade().get());
  }

  /**
   * Check is {@link Campaign#dbConnectionString} valid and all used db columns
   * has valid types
   * @param campaign to check
   * @return true if all check passed
   * @throws CampaignIllegalStateException if check failed
   */
  private boolean checkColumnsAndDBConnection(Campaign campaign) throws CampaignIllegalStateException {
    boolean result = campaignDataTypeChecker.checkColumnsTypes(campaign);
    campaignDataTypeChecker.checkUniqueKeyIndex(campaign);
    return result;
  }

  /**
   * Start new producer and consumer and continue to work
   * @param campaignId
   */
  public void unpauseCampaign(Long campaignId) {
    if (campaignContexts.containsKey(campaignId)) {
      Campaign campaign = campaignContexts.get(campaignId).getCampaign();
      CAMPAIGN_STATE campaignState = campaign.getState();
      if (campaignState == CAMPAIGN_STATE.PAUSED) {
        campaign.setManuallyPaused(false);
        campaign.setState(CAMPAIGN_STATE.STARTING);
        campaignContexts.get(campaignId).start();
      }
    }
  }

  /**
   * Stop work of producer and consumer thread for given campaign
   * @param campaignId
   */
  public void shutdownCampaign(Long campaignId, CAMPAIGN_ACTION action) {
    if (campaignContexts.containsKey(campaignId)) {
      Campaign campaign = campaignContexts.get(campaignId).getCampaign();
      CAMPAIGN_STATE campaignState = campaign.getState();
      if (campaignState == CAMPAIGN_STATE.RUNNING || campaignState == CAMPAIGN_STATE.PAUSED || action == CAMPAIGN_ACTION.STOP_FORCEFUL) {
        campaign.setManuallyPaused(false);
        campaign.setState(CAMPAIGN_STATE.STOPPING);
        campaignContexts.get(campaignId).stop(action);
      }
    }
  }

  /**
   * Pause campaign so that producer and consumer cannot take create more calls
   * @param campaignId campaign to pause
   * @param manual true if the pause is triggered by an operator (UI/API); false
   *               if it originates from schedule automation. Manual pause is kept
   *               sticky so the scheduler does not auto-start it while inside the
   *               schedule window.
   */
  public void pauseCampaign(Long campaignId, boolean manual) {
    if (campaignContexts.containsKey(campaignId)) {
      Campaign campaign = campaignContexts.get(campaignId).getCampaign();
      CAMPAIGN_STATE campaignState = campaign.getState();
      if (campaignState == CAMPAIGN_STATE.RUNNING) {
        campaign.setManuallyPaused(manual);
        campaign.setState(CAMPAIGN_STATE.PAUSING);
        campaignContexts.get(campaignId).pause();
      }
    }
  }

  /**
   * Clear data producer and consumer queues and reset row fetched flag
   * @param campaignId
   */
  public void reloadCampaign(Long campaignId) {
    if (campaignContexts.containsKey(campaignId))
      campaignContexts.get(campaignId).reload();
  }

  /**
   * Reset volatile fields of campaign
   * @param campaignId
   */
  public void resetCampaign(Long campaignId) {
    if (campaignContexts.containsKey(campaignId)) {
      campaignContexts.get(campaignId).reset();
    } else {
      campaignMementoService.resetCampaignMemento(campaignId);
    }
  }

  /**
   * Get realtime values of campaign attributes
   * @param campaignIds
   * @return list of campaigns
   */
  public List<Campaign> getCampaignsRealtimeInfo(Long[] campaignIds) {
    List<Campaign> campaigns = new ArrayList<>();
    if (campaignIds == null || campaignIds.length == 0)
      campaignContexts.forEach((id, context) -> campaigns.add(context.getCampaign()));
    else
      for (Long campaignId : campaignIds)
        if (campaignContexts.containsKey(campaignId))
          campaigns.add(campaignContexts.get(campaignId).getCampaign());
    return campaigns;
  }

  /**
   * Remove campaignContext
   * @param context
   */

  public CampaignContext removeCampaignContext(CampaignContext context) {
    return campaignContexts.remove(context.getCampaign().getId());
  }

  /**
   * Stop threads for producers and consumers
   */
  @PreDestroy
  private void cleanUp() {
    for (CampaignContext campaignContext : campaignContexts.values()) {
      campaignContext.stop(CAMPAIGN_ACTION.STOP_FORCEFUL);
    }
    for (CallOriginator asteriskCallOriginator : asteriskCallOriginators.values()) {
      asteriskCallOriginator.stop();
    }
  }

}
