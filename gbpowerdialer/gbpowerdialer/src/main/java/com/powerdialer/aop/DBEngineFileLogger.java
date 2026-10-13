package com.powerdialer.aop;

import java.util.Arrays;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import com.powerdialer.engine.dialer.CampaignContext;
import com.powerdialer.engine.dialer.DialerFacade;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.ParametrizedQuery;
import com.powerdialer.service.CampaignService;
import com.powerdialer.util.Constants;
import com.powerdialer.util.LoggerDecorator;

/**
 * File logger for manual queries in {@link com.powerdialer.engine.db.DBEngine}
 * class
 */
@Component
@Aspect
public class DBEngineFileLogger {

  private static final String LOG_FILES_AMOUNT_PROPERTY = "loggingFilesAmount";
  private static final int DEFAULT_LOG_FILES_AMOUNT = 3;
  private static final String LOG_FILE_EXTENSION = ".log";
  private DialerFacade dialerFacade;
  private CampaignService campaignService;
  private final LoggerDecorator log;
  private final LoggerContext loggerContext;
  private Integer logFilesAmount;

  @Value("${campaign.appender.file.path}")
  private String filePath;

  @Value("${campaign.appender.file.suffix}")
  private String fileSuffix;

  @Value("${campaign.appender.file.layout.ConversionPattern}")
  private String layoutPattern;

  @Autowired
  public DBEngineFileLogger(LoggerDecorator loggerDecorator,
                            DialerFacade dialerFacade,
                            CampaignService campaignService) {
    this.dialerFacade = dialerFacade;
    this.campaignService = campaignService;
    log = loggerDecorator.getLogger(DBEngineFileLogger.class);
    loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
    logFilesAmount = getLogFilesAmount();
  }

  private Integer getLogFilesAmount() {
    String systemPropertyValue = System.getProperty(LOG_FILES_AMOUNT_PROPERTY);
    if (!StringUtils.isBlank(systemPropertyValue))
      try {
        Integer amount = Integer.parseInt(systemPropertyValue);
        log.info("Got configured value for logging files amount: " + amount);
        return amount;
      } catch (NumberFormatException e) {
        log.error("Could not parse system property " + LOG_FILES_AMOUNT_PROPERTY + ", got value: " + systemPropertyValue);
      }
    return DEFAULT_LOG_FILES_AMOUNT;
  }

  @Around(value = "(execution(* com.powerdialer.engine.db.DBEngine.executeParametrizedQuery(..)))")
  public Object createLogForExecuteQuery(ProceedingJoinPoint joinPoint) throws Throwable {
    // Task 327143: executeParametrizedQuery'nin streaming overload'u boolean
    // dondurur; JSONArray cast'i o yolda ClassCastException uretirdi. Advice,
    // proceed sonucunu tipinden bagimsiz aynen gecirir.
    Long campaignId = getCampaignId(joinPoint);
    ParametrizedQuery parametrizedQuery = getParametrizedQuery(joinPoint);
    return createLog(joinPoint, campaignId, parametrizedQuery);
  }

  @Around(value = "(execution(* com.powerdialer.engine.db.DBEngine.executeParametrizedUpdate(..)))")
  public int createLogForExecuteUpdate(ProceedingJoinPoint joinPoint) throws Throwable {
    Long campaignId = getCampaignId(joinPoint);
    ParametrizedQuery parametrizedQuery = getParametrizedQuery(joinPoint);
    Object data = createLog(joinPoint, campaignId, parametrizedQuery);
    return data != null ? (int) data : -1;
  }

  private Long getCampaignId(ProceedingJoinPoint joinPoint) {
    Object[] args = joinPoint.getArgs();
    if (args.length >= 2) {
      Object campaignIdObject = args[1];
      if (campaignIdObject instanceof Long) {
        return (Long) campaignIdObject;
      }
    }
    log.warn("Can't get campaign id from joinPoint: " + joinPoint);
    return null;
  }

  private ParametrizedQuery getParametrizedQuery(ProceedingJoinPoint joinPoint) {
    Object[] args = joinPoint.getArgs();
    if (args.length >= 1) {
      Object data = args[0];
      if (data instanceof ParametrizedQuery) {
        return (ParametrizedQuery) data;
      }
    }
    log.warn("Can't get query from joinPoint: " + joinPoint);
    return null;
  }

  private Object createLog(ProceedingJoinPoint joinPoint, Long campaignId, ParametrizedQuery parametrizedQuery) throws Throwable {
    Throwable joinPointError = null;
    Object data = null;
    try {
      data = joinPoint.proceed();
    } catch (Throwable e) {
      joinPointError = e;
    }
    try {
      if (campaignId == null) {
        return data;
      }
      Logger campaignLogger = getLogger(campaignId);
      if (CollectionUtils.isEmpty(parametrizedQuery.getParameters())) {
        campaignLogger.info(parametrizedQuery.getQuery());
      } else {
        campaignLogger.info(parametrizedQuery.getQuery() + " parameters: " + Arrays.toString(parametrizedQuery.getParameters().toArray()));
      }
      if (joinPointError != null) {
        campaignLogger.error("Query execution failed: " + parametrizedQuery.getQuery());
        campaignLogger.error(joinPointError.getMessage());
      }
    } catch (Exception e) {
      log.error("An error occurred during creating log for " + joinPoint, e, Constants.LOG_MODULE.CAMPAIGN);
    } finally {
      if (joinPointError != null)
        throw joinPointError;
    }
    return data;
  }

  private Logger getLogger(Long campaignId) {
    Logger logger = loggerContext.exists(campaignId.toString());
    if (logger == null) {
      synchronized (this) {
        logger = loggerContext.exists(campaignId.toString());
        if (logger == null) {
          logger = configureLogger(campaignId, getCampaignName(campaignId));
        }
      }
    }
    return logger;
  }

  private String getCampaignName(Long campaignId) {
    if (campaignId == null)
      return null;
    CampaignContext campaignContext = dialerFacade.getCampaignContexts().get(campaignId);
    Campaign campaign = (campaignContext != null) ? campaignContext.getCampaign() : campaignService.getEntity(campaignId);
    return campaign.getName();
  }

  private Logger configureLogger(Long campaignId, String campaignName) {
    Logger logger = loggerContext.getLogger(campaignId.toString());
    logger.detachAndStopAllAppenders();
    String filename = campaignName.replaceAll("[^a-zA-Z0-9.-]", "_");
    setUpAppender(logger, filename);
    logger.setAdditive(false);
    logger.setLevel(Level.INFO);
    return logger;
  }

  private void setUpAppender(Logger logger, String filename) {
    RollingFileAppender<ILoggingEvent> logFileAppender = new RollingFileAppender<>();
    logFileAppender.setContext(loggerContext);
    logFileAppender.setName(filename);
    logFileAppender.setAppend(true);
    // Task 325469 (A4-2): her query'de senkron flush yerine OS buffer'ina yazilir;
    // dosya icerigi/formati ayni kalir, query basina syscall maliyeti kalkar.
    logFileAppender.setImmediateFlush(false);
    logFileAppender.setFile(filePath + filename + LOG_FILE_EXTENSION);
    setUpLayoutEncoder(logFileAppender);
    setUpRollingPolicy(filename, logFileAppender);
    logFileAppender.start();
    logger.addAppender(logFileAppender);
  }

  private void setUpLayoutEncoder(RollingFileAppender<ILoggingEvent> logFileAppender) {
    PatternLayoutEncoder logEncoder = new PatternLayoutEncoder();
    logEncoder.setContext(loggerContext);
    logEncoder.setPattern(layoutPattern);
    logEncoder.start();
    logFileAppender.setEncoder(logEncoder);
  }

  private void setUpRollingPolicy(String filename, RollingFileAppender<ILoggingEvent> logFileAppender) {
    TimeBasedRollingPolicy logFilePolicy = new TimeBasedRollingPolicy();
    logFilePolicy.setContext(loggerContext);
    logFilePolicy.setParent(logFileAppender);
    logFilePolicy.setFileNamePattern(filePath + filename + fileSuffix);
    logFilePolicy.setMaxHistory(logFilesAmount);
    logFilePolicy.start();
    logFileAppender.setRollingPolicy(logFilePolicy);
  }

  /**
   * Reconfigures the campaign logger with a new filename.
   */
  synchronized void resetLogger(Campaign campaign) {
      log.info("Resetting an existent logger for campaign " + campaign.getId() + " to the new name '" + campaign.getName() + "'");
      configureLogger(campaign.getId(), campaign.getName());
  }

}
