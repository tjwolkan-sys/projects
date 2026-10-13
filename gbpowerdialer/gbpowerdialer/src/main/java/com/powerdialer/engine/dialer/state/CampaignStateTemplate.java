package com.powerdialer.engine.dialer.state;

import java.util.Date;
import java.util.List;
import java.util.concurrent.BlockingDeque;

import com.powerdialer.util.Constants;
import com.powerdialer.engine.dialer.CallOriginator;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.powerdialer.engine.dialer.CampaignContext;
import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.Location;
import com.powerdialer.model.User;
import com.powerdialer.util.Constants.CAMPAIGN_ACTION;
import com.powerdialer.util.Constants.CAMPAIGN_STATE;
import com.powerdialer.util.Constants.LOG_MODULE;
import com.powerdialer.util.LoggerDecorator;

/**
 * Template class for campaign states
 */
public abstract class CampaignStateTemplate implements CampaignState {

  protected CampaignContext campaignContext;
  private final LoggerDecorator LOG;

  public CampaignStateTemplate(CampaignContext campaignContext) {
    this.campaignContext = campaignContext;
    this.LOG = campaignContext.getDialerFacade().getLoggerDecorator().getLogger(CampaignStateTemplate.class);
  }

  /**
   * Start campaign
   */
  public void start() {
    campaignContext.setCampaignState(campaignContext.getStartState());
    // FIX-333607: campaign, predictive init (GAMMA startup sorguları dâhil) bitene kadar STARTING
    // görünür; producer/consumer'dan önce RUNNING olur. Init hata verse bile son durum eskisi gibi
    // RUNNING kalır ki regular stop (RUNNING/PAUSED bekler) çalışmaya devam etsin.
    try {
      campaignContext.getDialerFacade().getDncEngine().addRunningCampaign(campaignContext.getCampaign());
      campaignContext.getDialerFacade().getLscEngine().addRunningCampaign(campaignContext.getCampaign());
      campaignContext.getDialerFacade().getCampaignRealTimeRingingManager().onCampaignStart(campaignContext.getCampaign());

      if (Constants.CAMPAIGN_TYPE.PREDICTIVE.equals(campaignContext.getCampaign().getType())) {
        Constants.PREDICTIVE_STRATEGY strategy = campaignContext.getCampaign().getPredictiveStrategy();
        switch (strategy) {
          case ALPHA:
            campaignContext.getDialerFacade().getDynamicBoostManager().onCampaignStart(campaignContext.getCampaign());
            break;

          case BETA:
            campaignContext.getDialerFacade().getPredictiveBetaCallManager().onCampaignStart(campaignContext.getCampaign());
            break;

          case GAMMA:
            campaignContext.getDialerFacade().getPredictiveGammaCallManager().onCampaignStart(campaignContext.getCampaign());
            break;

          default:
            LOG.warn("Unknown predictive strategy: " + strategy, LOG_MODULE.CAMPAIGN, campaignContext.getCampaign(), getAuthenticatedUser());
            break;
        }
      }
    } finally {
      campaignContext.getCampaign().setState(CAMPAIGN_STATE.RUNNING);
    }

    saveLiveCallsAndStartedDate();
    campaignContext.getCallProducer().start();
    campaignContext.getCallConsumer().start();
    LOG.info(campaignContext.getCampaign() + " is started!", LOG_MODULE.CAMPAIGN, campaignContext.getCampaign(), getAuthenticatedUser());
  }

  /**
   * Save data about amount of live calls and date when campaign has been
   * started to correctly calculate runoutEstimation
   */
  private void saveLiveCallsAndStartedDate() {
    if (campaignContext.getCampaign() != null && campaignContext.getCampaign().getLiveCalls() != null) {
      campaignContext.getCampaign().setLiveCallsFromLastStarting(campaignContext.getCampaign().getLiveCalls().get());
      campaignContext.getCampaign().setStartedDate(new Date());
    }
  }

  /**
   * Pause campaign
   */
  public void pause() {
    campaignContext.getCallProducer().pause();
    campaignContext.getCallConsumer().pause();
    campaignContext.getCampaign().setState(CAMPAIGN_STATE.PAUSED);
    removeCallQueueFromAsteriskCallQueues();
    campaignContext.setCampaignState(campaignContext.getPauseState());
    LOG.info(campaignContext.getCampaign() + " is paused!", LOG_MODULE.CAMPAIGN, campaignContext.getCampaign(), getAuthenticatedUser());
  }

  /**
   * Stop campaign
   */
  public void stop(CAMPAIGN_ACTION action) {
    Campaign campaign = campaignContext.getCampaign();
    int activeCallsSize = campaign.getOriginatedCalls().asMap().values().size();

    LOG.info(campaign + " is notified to stop "
            + (action == CAMPAIGN_ACTION.STOP_FORCEFUL ? "forcefully, active calls are going to be terminated" : "regularly, waiting for active calls to finish") + ", Size="
            + activeCallsSize, LOG_MODULE.CAMPAIGN, campaign, getAuthenticatedUser());

    campaignContext.getCallProducer().stop();
    campaignContext.getCallConsumer().stop();
    campaignContext.startCampaignStopJob(action);
    campaignContext.setCampaignState(campaignContext.getStopState());
    campaignContext.getDialerFacade().getDncEngine().removeRunningCampaign(campaign);
    campaignContext.getDialerFacade().getLscEngine().removeRunningCampaign(campaign);
    campaignContext.getDialerFacade().getCampaignRealTimeRingingManager().onCampaignStop(campaign);

    if (Constants.CAMPAIGN_TYPE.PREDICTIVE.equals(campaign.getType())) {
      Constants.PREDICTIVE_STRATEGY strategy = campaign.getPredictiveStrategy();
      switch (strategy) {
        case ALPHA:
          campaignContext.getDialerFacade().getDynamicBoostManager().onCampaignStop(campaign);
          break;

        case BETA:
          campaignContext.getDialerFacade().getPredictiveBetaCallManager().onCampaignStop(campaign);
          break;

        case GAMMA:
          campaignContext.getDialerFacade().getPredictiveGammaCallManager().onCampaignStop(campaign);
          break;

        default:
          LOG.warn("Unknown predictive strategy: " + strategy, LOG_MODULE.CAMPAIGN, campaign, getAuthenticatedUser());
          break;
      }
    }

    removeCallQueueFromAsteriskCallQueues();
  }

  /**
   * Remove callQueue of a campaign from Asterisk callQueues
   */
  protected void removeCallQueueFromAsteriskCallQueues() {
    BlockingDeque<Call> callQueue = campaignContext.getCallConsumer().getCallQueue();
    Campaign campaign = campaignContext.getCampaign();
    campaign.getLocations().stream()
            .map(Location::getServers)
            .flatMap(List::stream)
            .filter(server -> campaignContext.getDialerFacade().getAsteriskCallOriginators().containsKey(server.getId()))
            .map(server -> campaignContext.getDialerFacade().getAsteriskCallOriginators().get(server.getId()))
            .forEach(callOriginator -> {
              if (callOriginator.removeCallQueue(callQueue))
                LOG.info("Call queue of " + campaign + " is removed from " + callOriginator);
              else
                LOG.warn("Cannot remove call queue of " + campaign + " from " + callOriginator);
            });
  }

  /**
   * Add callQueue of consumer to Asterisk callQueues
   */
  protected void addCallQueueToAsteriskCallQueues() {
    Campaign campaign = campaignContext.getCampaign();
    boolean anyServerAdded = false;
    for (Location location : campaign.getLocations()) {
      for (com.powerdialer.model.Server server : location.getServers()) {
        CallOriginator callOriginator = campaignContext.getDialerFacade().getAsteriskCallOriginators().get(server.getId());
        if (callOriginator != null) {
          LOG.info("Adding call queue to " + callOriginator + ": " + campaign);
          callOriginator.addCallQueue(campaignContext.getCallConsumer().getCallQueue());
          anyServerAdded = true;
        }
      }
    }
    if (!anyServerAdded) {
      LOG.warn("No servers available for " + campaign + " Check 'inUse' and 'enabled' flags for expected servers", LOG_MODULE.CAMPAIGN, campaign);
    }
  }

  /**
   * Reload data from db, remove fetched data flag for calls that are in memory
   * but not originated yet
   */
  public void reload() {
    campaignContext.getCallProducer().setRowFetchedFlagsToNull();
    campaignContext.getCallConsumer().setRowFetchedFlagsToNull();
    LOG.info(campaignContext.getCampaign() + " is reloaded!", LOG_MODULE.CAMPAIGN, campaignContext.getCampaign(), getAuthenticatedUser());
  }

  /**
   * Reset information about campaign statistics
   */
  public void reset() {
    campaignContext.getCampaign().reset();
    campaignContext.getDialerFacade().getCampaignMementoService().resetCampaignMemento(campaignContext.getCampaign().getId());
    LOG.info(campaignContext.getCampaign() + " is reset!", LOG_MODULE.CAMPAIGN, campaignContext.getCampaign(), getAuthenticatedUser());
  }

  /**
   * Get authenticated User
   * @return User
   */
  public User getAuthenticatedUser() {
    Authentication authenticate = SecurityContextHolder.getContext().getAuthentication();
    return authenticate == null ? null : (User) authenticate.getPrincipal();
  }

}
