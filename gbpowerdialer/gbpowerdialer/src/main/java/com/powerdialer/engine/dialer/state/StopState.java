package com.powerdialer.engine.dialer.state;

import java.util.Date;
import java.util.List;
import java.util.function.Supplier;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

import com.powerdialer.engine.dialer.CampaignContext;
import com.powerdialer.engine.dialer.CampaignStopJob;
import com.powerdialer.engine.dialer.SharedResultTableSiblings;
import com.powerdialer.exceptions.CampaignIllegalStateException;
import com.powerdialer.exceptions.DateMismatchException;
import com.powerdialer.exceptions.StopConditionReachedException;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.CampaignMemento;
import com.powerdialer.model.CampaignJobLog;
import com.powerdialer.model.RoutingGroup;
import com.powerdialer.model.Schedule;
import com.powerdialer.model.User;
import com.powerdialer.util.Constants;
import com.powerdialer.util.Constants.CAMPAIGN_ACTION;
import com.powerdialer.util.Constants.DAY_OF_WEEK;
import com.powerdialer.util.Constants.LOG_MODULE;
import com.powerdialer.util.LoggerDecorator;

/**
 * State for campaign, instance of this class is used when campaign is in stop
 * state
 */
public class StopState extends CampaignStateTemplate {

  private final LoggerDecorator LOG;

  /** FIX-333599: canlıya geçiş anının ve startup cleanup kesmesinin zaman kaynağı. */
  private final Supplier<Date> clock;

  public StopState(CampaignContext campaignContext) {
    this(campaignContext, Date::new);
  }

  /**
   * Zaman kaynağını dışarıdan alan constructor; testler kesme anını sabitlemek için kullanır.
   * Üretimde {@link #StopState(CampaignContext)} sistem saatini verir.
   */
  public StopState(CampaignContext campaignContext, Supplier<Date> clock) {
    super(campaignContext);
    this.LOG = campaignContext.getDialerFacade().getLoggerDecorator().getLogger(StopState.class);
    this.clock = clock;
  }

  @Override
  public void start() {
    checkIfCampaignIsEnabled();
    checkNowIsBetweenCampaignStartAndEndDate();
    checkNowIsBetweenScheduleFromAndTill();
    checkIfCallerIdPoolEnabled();
    restoreCampaignFromMemento();
    checkStopCondition();
    checkForUnfinishedCalls();
    calculateOnlineAgentsSum();
    addCallQueueToAsteriskCallQueues();
    checkIfExpireJobShouldContinue();
    registerAsLive();
    super.start();
  }

  /**
   * Check if campaign is enabled before start
   */
  private void checkIfCampaignIsEnabled() {
    Campaign campaign = campaignContext.getCampaign();
    if (!campaign.isEnabled())
      throw new CampaignIllegalStateException("campaign.start.illegal.state", campaign.getName());
  }


  private void checkIfExpireJobShouldContinue() {
    Campaign campaign = campaignContext.getCampaign();
    CampaignJobLog campaignJobLog = campaignContext.getDialerFacade().getCampaignResultJobService().getCampaigJobLogByCampaignId(campaign.getId());
    if (campaignJobLog != null ) {
      throw new CampaignIllegalStateException("campaign.start.illegal.state.runningJob", campaign.getName());

    }
  }

  /**
   * Check if campaign's startDate is before now and endDate is after now
   * @return
   */
  private void checkNowIsBetweenCampaignStartAndEndDate() {
    Date now = new Date();
    Campaign campaign = campaignContext.getCampaign();
    if (now.before(campaign.getStartDate()) || now.after(campaign.getEndDate()))
      throw new DateMismatchException("campaign.date.range.error", campaign.getName());
  }

  /**
   * Check if from one of campaign's schedule is before now and till is after
   * now
   */
  private void checkNowIsBetweenScheduleFromAndTill() {
    Campaign campaign = campaignContext.getCampaign();
    if (!campaign.getScheduleEnabled())
      return;
    LocalDateTime now = LocalDateTime.now();
    int hoursOfNow = now.getHour();
    int minutesOfNow = now.getMinute();
    int day = now.getDayOfWeek().getValue();
    DAY_OF_WEEK dayOfWeek = DAY_OF_WEEK.intToDayOfWeek(day);
    List<Schedule> schedules = campaign.getEnabledSchedules();
    for (Schedule schedule : schedules) {
      if (schedule.getDay() == dayOfWeek) {
        LocalTime from = schedule.getFrom().toInstant().atZone(ZoneId.systemDefault()).toLocalTime();
        LocalTime till = schedule.getTill().toInstant().atZone(ZoneId.systemDefault()).toLocalTime();
        if (((from.getHour() < hoursOfNow) || ((from.getHour() == hoursOfNow) && (from.getMinute() <= minutesOfNow)))
                && ((till.getHour() > hoursOfNow) || ((till.getHour() == hoursOfNow) && (till.getMinute() > minutesOfNow)))) {
          return;
        }
      }
    }
    throw new DateMismatchException("campaign.schedule.range.error", campaign.getName());
  }

  /**
   * Check if callerId pool is still active (for pool callerId behaviour)
   */
  private void checkIfCallerIdPoolEnabled() {
    Campaign campaign = campaignContext.getCampaign();
    if (campaign.getCallerIdBehaviour() == Constants.CALLER_ID_BEHAVIOUR.POOL) {
      if (campaign.getCallerIdPool() != null) {
        RoutingGroup routingGroup = campaignContext.getDialerFacade().getRoutingGroupService().getRoutingGroup(campaign.getCallerIdPool().getId());
        if (routingGroup != null && routingGroup.isEnabled() && !routingGroup.isDeleted()) {
          return;
        }
      }
      throw new CampaignIllegalStateException("campaign.start.callerIdPool.disabled");
    }
  }

  /**
   * Restore campaign statistics from memento
   */
  private void restoreCampaignFromMemento() {
    Campaign campaign = campaignContext.getCampaign();
    CampaignMemento campaignMemento = campaignContext.getDialerFacade().getCampaignMementoService().getCampaignMementoByCampaignId(campaign.getId());
    if (campaignMemento != null) {
      campaignMemento.restore(campaign);
    }
  }

  private void checkStopCondition() {
    Campaign campaign = campaignContext.getCampaign();
    if (campaignContext.getDialerFacade().getStopConditionChecker().isStopConditionReached(campaign))
      throw new StopConditionReachedException("campaign.stopCondition.error", campaign.getName());
  }

  /**
   * Calculate quantity of dedicated online agents
   */
  private void calculateIdleAgentsSum() {
    Campaign campaign = campaignContext.getCampaign();
    List<User> campaignUsers = campaignContext.getDialerFacade().getAcdEngine().getCampaignUsers(campaign);
    campaign.setAgentsSize(campaignUsers.size());
  }


  /**
   * Calculate quantity of dedicated online agents
   */
  private void calculateOnlineAgentsSum() {
    Campaign campaign = campaignContext.getCampaign();
    int online = campaignContext.getDialerFacade().getAcdEngine().countCampaignAllActiveUsers(campaign);
    campaign.setAgentsSize(online);
  }

  /**
   * FIX-333599: STOP→START anı, context kardeş kampanyaların göreceği haritaya girmeden ÖNCE
   * işaretlenir. Bu an, kardeşlerin startup cleanup'ında kesme olarak kullanılır; bu kampanyanın
   * bu andan sonra yaptığı rezervasyonlar kardeş cleanup'ından korunur. {@code startedDate} bu iş
   * için kullanılmaz: unpause'ta da güncellendiği için pause öncesi canlı rezervasyonları bayat
   * gösterirdi.
   */
  private void registerAsLive() {
    campaignContext.markLive(clock.get());
    campaignContext.getDialerFacade().getCampaignContexts().put(campaignContext.getCampaign().getId(), campaignContext);
  }

  /**
   * Startup DB check: yarım kalmış rezervasyonları temizler. Cleanup tablo geneline uygulandığı için
   * yalnız kesmeden ÖNCE yapılmış rezervasyonlar temizlenir:
   * kesme = min(cleanup'ın başladığı an, aynı result tablosunu kullanan canlı kardeşlerin en erken
   * canlıya geçiş anı).
   * <ul>
   *   <li>Kardeş anı (FIX-333599, vaka 02.10.2026: camp 17'nin cleanup'ı camp 6'nın ilk batch
   *       rezervasyonlarını sildi): kardeşin canlıya geçişinden sonraki rezervasyonları korur.</li>
   *   <li>Cleanup anı (05.10.2026): FIX-333607 ile farklı kampanyaların start'ları paralel çalışır;
   *       kardeş bu taramadan sonra canlıya geçebilir ve taramada görünmez. Bu kampanya bu koşuda henüz
   *       rezervasyon yapmadığı için (producer {@code super.start()} ile başlar) cleanup anından sonraki
   *       hiçbir rezervasyon bayat olamaz. An taramadan ÖNCE alınır: önce canlıya geçen kardeş taramada
   *       görünür ve kendi anıyla korunur, sonra geçenin rezervasyonları bu andan sonradır.</li>
   * </ul>
   */
  private void checkForUnfinishedCalls() {
    if (Boolean.TRUE.equals(campaignContext.getCampaign().getStartupDbCheck()))
      try {
        Date cleanupStartedAt = clock.get();
        Date siblingLiveSince = SharedResultTableSiblings.earliestLiveSince(campaignContext,
            campaignContext.getDialerFacade().getCampaignContexts().values());
        boolean cutBySibling = siblingLiveSince != null && siblingLiveSince.before(cleanupStartedAt);
        Date fetchedBefore = cutBySibling ? siblingLiveSince : cleanupStartedAt;
        LOG.info(campaignContext.getCampaign() + ": Cleaning-up the fetched flag for unfinished calls..."
            + " Only reservations made before " + fetchedBefore + " are released"
            + (cutBySibling ? " (live campaign(s) share the result table)." : "."), LOG_MODULE.CAMPAIGN, campaignContext.getCampaign());
        campaignContext.getDialerFacade().getCampaignDbOperations().removeFetchedDataFlagFromUnfinishedCalls(campaignContext.getCampaign(), fetchedBefore);
      } catch (Exception e) {
        LOG.error(campaignContext.getCampaign() + ": Could not reset fetched data.", e, LOG_MODULE.CAMPAIGN, campaignContext.getCampaign());
      }
  }

  @Override
  public void pause() {
    LOG.info(campaignContext.getCampaign() + " is stopped!", LOG_MODULE.CAMPAIGN, campaignContext.getCampaign(), getAuthenticatedUser());
  }

  @Override
  public void stop(CAMPAIGN_ACTION action) {
    CampaignStopJob campaignStopJob = campaignContext.getCampaign().getCampaignStopJob();
    if (action == CAMPAIGN_ACTION.STOP_FORCEFUL) {
      if (campaignStopJob != null) {
        synchronized (campaignStopJob) {
          campaignStopJob.setAction(CAMPAIGN_ACTION.STOP_FORCEFUL);
          campaignStopJob.notify();
        }
        LOG.info(campaignContext.getCampaign() + " has already stopped!", LOG_MODULE.CAMPAIGN, campaignContext.getCampaign(), getAuthenticatedUser());
      } else {
        super.stop(action);
      }
    }
  }

  @Override
  public void reload() {
    LOG.info(campaignContext.getCampaign() + " cannot be reloaded when stopped!", LOG_MODULE.CAMPAIGN, campaignContext.getCampaign(), getAuthenticatedUser());
  }
}
