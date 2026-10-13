package com.powerdialer.engine.scheduler;

import static com.powerdialer.util.Constants.SCHEDULE_ACTION_TYPE;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.powerdialer.engine.dialer.CampaignContext;
import com.powerdialer.engine.dialer.DialerFacade;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.Schedule;
import com.powerdialer.model.ScheduleAutomation;
import com.powerdialer.util.Constants;
import com.powerdialer.util.Constants.DAY_OF_WEEK;
import com.powerdialer.util.LoggerDecorator;

/**
 * Class that checks schedules of campaign
 */
@Component
public class CampaignScheduleChecker {

  private static final String START_KEY = "Start";
  private static final String STOP_KEY = "Stop";

  @Autowired
  private ScheduleAutomationEngine scheduleAutomationEngine;

  @Autowired
  private DialerFacade dialerFacade;

  private final LoggerDecorator LOG;

  @Autowired
  public CampaignScheduleChecker(LoggerDecorator loggerDecorator) {
    this.LOG = loggerDecorator.getLogger(CampaignScheduleChecker.class);
  }

  private Campaign campaign;
  private LocalDateTime now;
  private LocalTime from;
  private LocalTime till;
  private boolean mustBeStarted;
  private boolean mustBeStopped;
  private boolean firstCheck = true;
  private boolean resetEnabled;

  public void setNow() {
    now = LocalDateTime.now();
  }

  public void checkCampaign(Campaign campaign) {
    initialize(campaign);
    if (shouldStopCheckByStopCondition(campaign)) {
      LOG.info("Campaign " + campaign + " was stopped manually or conditionally. Ignoring schedule check");
      return;
    }
    if (checkNowIsBetweenStartAndEndDate()) {
      if (campaign.getScheduleEnabled())
        checkCampaignMustBeStartedOrStoppedBySchedule();
      else
        mustBeStarted = true;
    } else
      checkCampaignMustBeStoppedByEndDate();
  }

  private boolean shouldStopCheckByStopCondition(Campaign campaign) {
    return shouldStopByManualCondition(campaign)
        || Constants.CAMPAIGN_STOP_TYPE.STOP_CONDITION.equals(campaign.getStopType());
  }

  /**
   * Campaign shouldn't be started after manual stop, excluding very first run when application deployed.
   */
  private boolean shouldStopByManualCondition(Campaign campaign) {
    return !firstCheck && Constants.CAMPAIGN_STOP_TYPE.MANUAL.equals(campaign.getStopType());
  }

  private void initialize(Campaign campaign) {
    this.campaign = campaign;
    mustBeStarted = mustBeStopped = false;
  }

  private boolean checkNowIsBetweenStartAndEndDate() {
    return now.isAfter(LocalDateTime.ofInstant(campaign.getStartDate().toInstant(), ZoneId.systemDefault()))
        && now.isBefore(LocalDateTime.ofInstant(campaign.getEndDate().toInstant(), ZoneId.systemDefault()));
  }

  private void checkCampaignMustBeStartedOrStoppedBySchedule() {
    int day = now.getDayOfWeek().getValue();
    DAY_OF_WEEK dayOfWeek = DAY_OF_WEEK.intToDayOfWeek(day);
    List<Schedule> schedules = campaign.getEnabledSchedules();
    for (Schedule schedule : schedules) {
      if (schedule.getDay().equals(dayOfWeek)) {
        from = schedule.getFrom().toInstant().atZone(ZoneId.systemDefault()).toLocalTime();
        till = schedule.getTill().toInstant().atZone(ZoneId.systemDefault()).toLocalTime();
        resetEnabled = schedule.isResetEnabled();
        if (checkNowIsBetweenFromAndTill()) {
          mustBeStarted = true;
          mustBeStopped = false;
          break;
        }
        if (checkNowIsAfterTill()) {
          mustBeStopped = true;
        }
      }
    }
  }

  private void checkCampaignMustBeStoppedByEndDate() {
    if (now.isAfter(LocalDateTime.ofInstant(campaign.getEndDate().toInstant(), ZoneId.systemDefault())))
      mustBeStopped = true;
  }

  private boolean checkNowIsBetweenFromAndTill() {
    int hoursOfNow = now.getHour();
    int minutesOfNow = now.getMinute();
    return ((from.getHour() < hoursOfNow) || ((from.getHour() == hoursOfNow) && (from.getMinute() <= minutesOfNow)))
        && ((till.getHour() > hoursOfNow) || ((till.getHour() == hoursOfNow) && (till.getMinute() > minutesOfNow)));
  }

  private boolean checkNowIsAfterTill() {
    int hoursOfNow = now.getHour();
    int minutesOfNow = now.getMinute();
    return (till.getHour() < hoursOfNow) || ((till.getHour() == hoursOfNow) && (till.getMinute() <= minutesOfNow));
  }

  public boolean isMustBeStarted() {
    return mustBeStarted;
  }

  public boolean isResetEnabled() {
    return resetEnabled;
  }

  public boolean isMustBeStopped() {
    return mustBeStopped;
  }

  public void setFirstCheckToFalse() {
    if (firstCheck)
      firstCheck = false;
  }

  public void checkCampaignAutomation(Campaign campaign) {
    ScheduleAutomation startAutomation = campaign.getScheduleAutomationByType(SCHEDULE_ACTION_TYPE.START);
    ScheduleAutomation stopAutomation = campaign.getScheduleAutomationByType(SCHEDULE_ACTION_TYPE.STOP);
    if (Objects.nonNull(startAutomation)) {
      processStartAutomation(campaign, startAutomation);
    }

    if (Objects.nonNull(stopAutomation)) {
      processStopAutomation(campaign, stopAutomation);
    }

  }

  private void processStartAutomation(Campaign campaign, ScheduleAutomation startAutomation) {
    if (startThresholdExpired(campaign, startAutomation)) {
      EvaluationResultContext startAutomationContext = scheduleAutomationEngine.processScheduleAutomation(startAutomation);
      mustBeStarted = startAutomationContext.getResultBoolean();
      processScheduleAutomationExpressionLog(campaign, startAutomationContext::getExpressionForLog, START_KEY);
      processExpressionErrorLog(campaign, startAutomationContext::getErrorLogMessage, START_KEY);
    } else {
      mustBeStarted = false;
    }
  }

  private void processStopAutomation(Campaign campaign, ScheduleAutomation stopAutomation) {
    if (stopThresholdExpired(campaign, stopAutomation)) {
      EvaluationResultContext stopAutomationContext = scheduleAutomationEngine.processScheduleAutomation(stopAutomation);
      mustBeStopped = stopAutomationContext.getResultBoolean();
      processScheduleAutomationExpressionLog(campaign, stopAutomationContext::getExpressionForLog, STOP_KEY);
      processExpressionErrorLog(campaign, stopAutomationContext::getErrorLogMessage, STOP_KEY);
    } else {
      mustBeStopped = false;
    }
    processCampaignStopTypeForTheFirstRun();
  }

  private void processScheduleAutomationExpressionLog(Campaign campaign, Supplier<String> resource, String action) {
    String expression = resource.get();
    if (Objects.nonNull(expression) && !expression.isEmpty()) {
      LOG.info(campaign + " " + action + " automation. " + expression, Constants.LOG_MODULE.CAMPAIGN, campaign);
    }
  }

  private void processExpressionErrorLog(Campaign campaign, Supplier<String> resource, String action) {
    String expression = resource.get();
    if (Objects.nonNull(expression) && !expression.isEmpty()) {
      LOG.error(campaign + " " + action + " automation. " + expression, Constants.LOG_MODULE.CAMPAIGN, campaign);
    }
  }

  private void processCampaignStopTypeForTheFirstRun() {
    if (firstCheck) {
      campaign.setStopType(Constants.CAMPAIGN_STOP_TYPE.AUTOMATIC);
    }
  }

  private boolean startThresholdExpired(Campaign campaign, ScheduleAutomation startAutomation) {
    if (Objects.nonNull(campaign.getStoppedDate())) {
      return checkThresholdExpired(campaign.getStoppedDate(), startAutomation.getThreshold());
    }
    return true;
  }

  private boolean stopThresholdExpired(Campaign campaign, ScheduleAutomation stopAutomation) {
    CampaignContext campaignContext = dialerFacade.getCampaignContexts().get(campaign.getId());
    if (Objects.nonNull(campaignContext)) {
      Date startedDate = campaignContext.getCampaign().getStartedDate();
      return Objects.nonNull(startedDate) && checkThresholdExpired(startedDate, stopAutomation.getThreshold());
    }
    return false;
  }

  private boolean checkThresholdExpired(Date date, int threshold) {
    LocalDateTime thresholdDate = getThresholdEndDate(date, threshold);
    return now.isAfter(thresholdDate);
  }

  private LocalDateTime getThresholdEndDate(Date date, int threshold) {
    return LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault()).plusMinutes(threshold);
  }
}
