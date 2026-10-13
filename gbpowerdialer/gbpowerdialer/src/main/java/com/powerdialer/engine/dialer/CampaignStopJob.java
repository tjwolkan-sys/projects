package com.powerdialer.engine.dialer;

import java.util.Collection;
import java.util.Date;

import com.powerdialer.engine.asterisk.ami.ManagerConnectionComposition;
import com.powerdialer.engine.asterisk.ami.ManagerConnectionDecorator;
import com.powerdialer.engine.dialer.routing.FctChannelGate;
import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.CampaignMemento;
import com.powerdialer.publish.CampaignChangedEvent;
import com.powerdialer.util.Constants;
import com.powerdialer.util.Constants.CAMPAIGN_ACTION;
import com.powerdialer.util.Constants.CAMPAIGN_STATE;
import com.powerdialer.util.Constants.LOG_MODULE;
import com.powerdialer.util.LoggerDecorator;
import com.powerdialer.util.Utils;

/**
 * Class that checks whether there are active calls in campaign and waits for
 * their completion to change the status of campaign and memento to db
 */
public class CampaignStopJob implements Runnable {

  private final LoggerDecorator LOG;
  private final int WAIT_TIMEOUT = 3600000;
  private final int TERMINATION_DELAY_PERIOD = 100;
  private CampaignContext campaignContext;
  private CAMPAIGN_ACTION action;

  public CampaignStopJob(CampaignContext campaignContext, CAMPAIGN_ACTION action) {
    this.campaignContext = campaignContext;
    this.action = action;
    this.LOG = campaignContext.getDialerFacade().getLoggerDecorator().getLogger(CampaignStopJob.class);
  }

  public void setAction(CAMPAIGN_ACTION action) {
    this.action = action;
  }

  @Override
  public void run() {
    processCampaignStopByAction();
    lastInvokeForCallProducerCalculation();
    lastCleanUpChecks();
    changeStateAndRemoveFromCampaignContexts();
    saveCampaignMemento();
    publishCampaignChangeEvent();
    LOG.info((action == CAMPAIGN_ACTION.STOP_FORCEFUL ? "Forceful stop" : "Regular stop") + " is processed successfully for " + campaignContext.getCampaign(),
             LOG_MODULE.CAMPAIGN,
             campaignContext.getCampaign());
  }

  /**
   * Make decision about how to stop campaign
   */
  private void processCampaignStopByAction() {
    switch (action) {
      case STOP_REGULAR:
      case RESTART:
        waitCallsInProgressCompletion();
        break;
      case STOP_FORCEFUL:
        terminateCallsInProgress();
        break;
      default:
        break;
    }
  }

  /**
   * Stop execution if there are calls in progress, it will be notified by
   * campaign
   */
  private void waitCallsInProgressCompletion() {
    try {
      synchronized (this) {
        if (campaignContext.getCampaign().hasCallsInProgress()) {
          wait(WAIT_TIMEOUT);
          LOG.info(this + " has finished awaiting completion of active calls");
        }
      }
    } catch (InterruptedException e) {
      LOG.error(e, e, LOG_MODULE.CAMPAIGN, campaignContext.getCampaign());
    }
  }

  private void terminateCallsInProgress() {
    Campaign campaign = campaignContext.getCampaign();
    Collection<Call> calls = campaign.getOriginatedCalls().asMap().values();
    LOG.info("Cleaning-up active calls for " + campaign + " due-to forceful stop, Size=" + calls.size(), LOG_MODULE.CAMPAIGN, campaign);
    calls.forEach(this::terminateCall);
  }

  private void terminateCall(Call call) {
    clearCallAndSaveResultToDb(call);
    sendCallHangupCommandToAsterisk(call);
    try {
      Thread.sleep(TERMINATION_DELAY_PERIOD);
    } catch (InterruptedException e) {
      LOG.error(e, e, LOG_MODULE.CAMPAIGN);
    }
  }

  /**
   * Clear call from caches, save result to db
   * @param call
   */
  private void clearCallAndSaveResultToDb(Call call) {
    ManagerConnectionDecorator connectionDecorator = call.getCallOriginator().getConnectionDecorator();
    connectionDecorator.clearCall(call);
    call.setTelephonyResultCode(connectionDecorator.getItuToTelephonyResultCodeRelations().get(Constants.ERROR_RESULT_CODES.CAMPAIGN_STOPPED_FORCEFULLY.getCode()));
    call.getCallOriginator().getCallCleaner().completeCall(call);
    campaignContext.getDialerFacade().getCampaignDbOperations().insertCallResultToDB(call);
    call.getCallOriginator().saveCDR(call);
    LOG.warn("Cleaning-up due-to forceful campaign stop for " + call);
  }

  /**
   * Sending asterisk command to hangup call
   * @param call
   */
  private void sendCallHangupCommandToAsterisk(Call call) {
    LOG.info("Sending hangup command to Asterisk due-to forceful campaign stop for " + call);
    call.getCallOriginator().getConnectionDecorator().hangupCall(call);
  }

  /**
   * Receive the latest status of parameters that are being calculated in
   * producer
   */
  private void lastInvokeForCallProducerCalculation() {
    campaignContext.getDialerFacade().getCampaignContexts().get(campaignContext.getCampaign().getId()).getCallProducer().calculateCampaignParameters();
  }

  /**
   * Last clean-up for campaign, in case calls hang-up and lock of this object
   * is released due to timeout campaign fields must be reset
   */
  private void lastCleanUpChecks() {
    Campaign campaign = campaignContext.getCampaign();
    campaign.setRingingCalls(0);
    campaign.clearCallsInProgress();
    campaign.setActiveCalls(0);
    releaseFctChannelsForCampaign(campaign);
  }

  /**
   * FCT kanal rezervasyonları için safety net: normal akışta release çağrı bazında yapılır, burada
   * yalnız stop sonrası artık kalmadığı garanti edilir. Diğer kampanyaların aynı FCT üzerindeki
   * rezervasyonlarına dokunulmaz (release kampanya kimliğine göre filtrelenir).
   */
  private void releaseFctChannelsForCampaign(Campaign campaign) {
    if (campaign == null || campaign.getId() == null) {
      return;
    }
    ManagerConnectionComposition composition = campaignContext.getDialerFacade() == null
        ? null : campaignContext.getDialerFacade().getConnectionComposition();
    FctChannelGate gate = composition == null ? null : composition.getFctChannelGate();
    if (gate != null) {
      gate.releaseAllForCampaign(campaign.getId());
    }
  }

  /**
   * Change campaign state to stopped and remove {@link CampaignContext} from
   * {@link DialerFacade#campaignContexts} if {@link CampaignStopJob#action} is
   * NOT {@link CAMPAIGN_ACTION#RESTART} because on restart we update
   * {@link CampaignContext} in {@link DialerFacade#campaignContexts} and there
   * is no need to remove it.
   */
  private void changeStateAndRemoveFromCampaignContexts() {
    if (action != CAMPAIGN_ACTION.RESTART) {
      campaignContext.getCampaign().setState(CAMPAIGN_STATE.STOPPED);
      campaignContext.getDialerFacade().removeCampaignContext(campaignContext);
      lastCleanUpChecks();
    }
  }

  /**
   * Save campaignMemento
   */
  private void saveCampaignMemento() {
    CampaignMemento campaignMemento = campaignContext.getDialerFacade().getCampaignMementoService().getCampaignMementoByCampaignId(campaignContext.getCampaign().getId());
    if (campaignMemento == null)
      campaignMemento = new CampaignMemento();
    campaignContext.getDialerFacade().getCampaignMementoService().saveCampaignMementoByCampaign(campaignMemento, campaignContext.getCampaign());
  }

  /**
   * Publish CampaignChangedEvent only for restart action. This event should be
   * published just before CampaignStopJob completes processing. Publishing this
   * event will start campaign
   */
  private void publishCampaignChangeEvent() {
    if (action == CAMPAIGN_ACTION.RESTART) {
      campaignContext.getDialerFacade().getPublisher().publishEvent(new CampaignChangedEvent(this, campaignContext.getCampaign().getId()));
    }
  }

  @Override
  public String toString() {
    return "CampaignStopJob [campaign=" + campaignContext.getCampaign() + "]";
  }

}
