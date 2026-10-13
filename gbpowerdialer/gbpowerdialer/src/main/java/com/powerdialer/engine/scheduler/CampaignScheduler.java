package com.powerdialer.engine.scheduler;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import java.time.LocalTime;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.powerdialer.util.DevProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Class that starts checking methods of campaigns
 */
@Component
@Lazy(false)
public class CampaignScheduler implements InitializingBean {

  private static final Logger LOG = LoggerFactory.getLogger(CampaignScheduler.class);

  @Autowired
  private AutomaticCampaignExecutor automaticCampaignExecutor;

  @Autowired
  private ScheduledCampaignRunner  scheduledCampaignRunner;

  @Value("${campaign.schedule.delay}")
  private int scheduleRestartDelay;

  @Override
  public void afterPropertiesSet() {
    if (Boolean.TRUE.equals(DevProperties.DISABLE_CAMPAIGN_SCHEDULER)) {
      LOG.warn("Campaign scheduler is disabled");
    } else {
      startScheduler();
    }
  }

  /**
   * Starts checking schedules of campaigns every n milliseconds
   */
  private void startScheduler() {
    LOG.info("Scheduling check for each " + scheduleRestartDelay + "ms");
    ThreadFactory threadFactory = new ThreadFactoryBuilder().setDaemon(true)
        .setNameFormat("campaign-scheduler-%d")
        .build();
    ScheduledExecutorService scheduleService = Executors.newSingleThreadScheduledExecutor(threadFactory);
    scheduleService.scheduleWithFixedDelay(() -> scheduledCampaignRunner.processScheduledCampaigns(), countMillisecondsToFullMinute(), scheduleRestartDelay, TimeUnit.MILLISECONDS);
  }

  private int countMillisecondsToFullMinute() {
    LocalTime now = LocalTime.now();
    return (60 - now.getSecond()) * 1000;
  }

}
