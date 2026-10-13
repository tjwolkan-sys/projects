package com.powerdialer.engine.dialer.endlistbehaviour;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import com.powerdialer.engine.dialer.CallProducer;
import com.powerdialer.engine.dialer.CampaignContext;
import com.powerdialer.model.Campaign;
import com.powerdialer.util.LoggerDecorator;
import com.powerdialer.util.Constants.DB_TYPE;

/**
 * Abstract class that holds common objects/methods for end list behaviour
 * implementations
 */
public abstract class EndListBehaviourTemplate implements EndListBehaviour {

  protected final LoggerDecorator LOG;
  protected final CampaignContext campaignContext;
  protected final ScheduledExecutorService cpsService;

  public EndListBehaviourTemplate(CampaignContext campaignContext) {
    ThreadFactory threadFactory = new ThreadFactoryBuilder().setDaemon(true)
                                                            .setNameFormat("end-list-executor-%d")
                                                            .build();
    cpsService = Executors.newSingleThreadScheduledExecutor(threadFactory);
    this.campaignContext = campaignContext;
    LOG = campaignContext.getDialerFacade().getLoggerDecorator().getLogger(CallProducer.class);
  }

  public String processColumnName(Campaign campaign, String columnName) {
    return shouldEscapeColumnNames(campaign) ? escapeColumnName(columnName) : columnName;
  }

  private boolean shouldEscapeColumnNames(Campaign campaign) {
    DB_TYPE dbType = DB_TYPE.getByConnectionString(campaign.getDbConnectionString());
    return DB_TYPE.ORACLE == dbType || DB_TYPE.POSTGRE_SQL == dbType;
  }

  private String escapeColumnName(String fieldName) {
    return "\"" + fieldName + "\"";
  }

}
