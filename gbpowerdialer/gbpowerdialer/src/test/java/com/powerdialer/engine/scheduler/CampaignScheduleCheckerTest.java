package com.powerdialer.engine.scheduler;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.List;

import java.time.LocalDateTime;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Spy;
import org.powermock.reflect.Whitebox;
import org.mockito.junit.MockitoJUnitRunner;

import com.powerdialer.model.Campaign;
import com.powerdialer.model.Schedule;
import com.powerdialer.util.Constants;
import com.powerdialer.util.Constants.DAY_OF_WEEK;
import com.powerdialer.util.LoggerDecorator;

/**
 * Tests for CampaignScheduleChecker class
 */
@RunWith(MockitoJUnitRunner.class)
public class CampaignScheduleCheckerTest {

  @Spy
  private Campaign campaign;

  private CampaignScheduleChecker checker;
  private LocalDateTime now;
  private Schedule schedule;
  private List<Schedule> schedules;

  @Before
  public void init() throws IllegalArgumentException, SecurityException {
    schedule = new Schedule();
    schedules = new ArrayList<>();
    now = LocalDateTime.of(2016, 6, 15, 12, 30); // Wednesday
    checker = spy(new CampaignScheduleChecker(new LoggerDecorator()));

    Whitebox.setInternalState(checker, "now", now);
  }

  @Test
  public void campaignMustBeStoppedByEndDate() {
    when(campaign.getStartDate()).thenReturn(new GregorianCalendar(2016, 5, 12, 12, 00).getTime());
    when(campaign.getEndDate()).thenReturn(new GregorianCalendar(2016, 5, 15, 12, 00).getTime());
    checker.checkCampaign(campaign);
    assertTrue(checker.isMustBeStopped());
  }

  @Test
  public void campaignMustBeStartedByStartDate() {
    when(campaign.getStartDate()).thenReturn(new GregorianCalendar(2016, 5, 15, 12, 15).getTime());
    when(campaign.getEndDate()).thenReturn(new GregorianCalendar(2016, 5, 30, 12, 15).getTime());
    when(campaign.getScheduleEnabled()).thenReturn(false);
    checker.checkCampaign(campaign);
    assertTrue(checker.isMustBeStarted());
  }

  @Test
  public void campaignMustBeStartedBySchedule() {
    when(campaign.getStartDate()).thenReturn(new GregorianCalendar(2016, 5, 1, 12, 00).getTime());
    when(campaign.getEndDate()).thenReturn(new GregorianCalendar(2016, 5, 30, 12, 00).getTime());
    when(campaign.getScheduleEnabled()).thenReturn(true);
    schedule.setDay(DAY_OF_WEEK.WED);
    schedule.setFrom(new GregorianCalendar(2016, 5, 15, 12, 15).getTime());
    schedule.setTill(new GregorianCalendar(2016, 5, 15, 13, 15).getTime());
    schedules.add(schedule);
    when(campaign.getEnabledSchedules()).thenReturn(schedules);
    checker.checkCampaign(campaign);
    assertTrue(checker.isMustBeStarted());
  }

  @Test
  public void campaignMustBeStoppedBySchedule() {
    when(campaign.getStartDate()).thenReturn(new GregorianCalendar(2016, 5, 1, 12, 00).getTime());
    when(campaign.getEndDate()).thenReturn(new GregorianCalendar(2016, 5, 30, 12, 00).getTime());
    when(campaign.getScheduleEnabled()).thenReturn(true);
    schedule.setDay(DAY_OF_WEEK.WED);
    schedule.setFrom(new GregorianCalendar(2016, 5, 15, 11, 00).getTime());
    schedule.setTill(new GregorianCalendar(2016, 5, 15, 12, 30).getTime());
    schedules.add(schedule);
    when(campaign.getEnabledSchedules()).thenReturn(schedules);
    checker.checkCampaign(campaign);
    assertTrue(checker.isMustBeStopped());
  }

  @Test
  public void campaignMustBeStartedByScheduleAfterManualStopAtFirstRun() {
    when(campaign.getStartDate()).thenReturn(new GregorianCalendar(2016, 5, 1, 12, 00).getTime());
    when(campaign.getEndDate()).thenReturn(new GregorianCalendar(2016, 5, 30, 12, 00).getTime());
    when(campaign.getScheduleEnabled()).thenReturn(true);
    schedule.setDay(DAY_OF_WEEK.WED);
    schedule.setFrom(new GregorianCalendar(2016, 5, 15, 12, 15).getTime());
    schedule.setTill(new GregorianCalendar(2016, 5, 15, 13, 15).getTime());
    schedules.add(schedule);
    when(campaign.getEnabledSchedules()).thenReturn(schedules);
    when(campaign.getStopType()).thenReturn(Constants.CAMPAIGN_STOP_TYPE.MANUAL);
    checker.checkCampaign(campaign);
    assertTrue(checker.isMustBeStarted());
  }

  @Test
  public void campaignMustNotStartedByScheduleAfterManualStopIfNotFirstRun() {
    lenient().when(campaign.getStartDate()).thenReturn(new GregorianCalendar(2016, 5, 1, 12, 00).getTime());
    lenient().when(campaign.getEndDate()).thenReturn(new GregorianCalendar(2016, 5, 30, 12, 00).getTime());
    lenient().when(campaign.getScheduleEnabled()).thenReturn(true);
    schedule.setDay(DAY_OF_WEEK.WED);
    schedule.setFrom(new GregorianCalendar(2016, 5, 15, 12, 15).getTime());
    schedule.setTill(new GregorianCalendar(2016, 5, 15, 13, 15).getTime());
    schedules.add(schedule);
    lenient().when(campaign.getEnabledSchedules()).thenReturn(schedules);
    when(campaign.getStopType()).thenReturn(Constants.CAMPAIGN_STOP_TYPE.MANUAL);
    checker.setFirstCheckToFalse();
    checker.checkCampaign(campaign);
    assertFalse(checker.isMustBeStarted());
  }
}
