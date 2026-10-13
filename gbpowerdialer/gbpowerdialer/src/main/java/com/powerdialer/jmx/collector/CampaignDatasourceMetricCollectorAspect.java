package com.powerdialer.jmx.collector;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import com.powerdialer.aop.DBEngineFileLogger;
import com.powerdialer.jmx.MetricManager;
import com.powerdialer.util.LoggerDecorator;

/**
 * Aspect for collection metrics from campaign datasource
 */
@Aspect
@Component
@Lazy(false)
public class CampaignDatasourceMetricCollectorAspect {

  private final LoggerDecorator LOG;

  @Autowired
  public CampaignDatasourceMetricCollectorAspect(LoggerDecorator loggerDecorator) {
    LOG = loggerDecorator.getLogger(DBEngineFileLogger.class);
  }

  @Around(value = "(execution(* com.powerdialer.engine.db.DBEngine.executeParametrizedQuery(..))) || (execution(* com.powerdialer.engine.db.DBEngine.executeParametrizedUpdate(..)))")
  public Object measureCampaignQueryTime(ProceedingJoinPoint joinPoint) throws Throwable {
    long startTime = System.nanoTime();
    Object data = joinPoint.proceed();
    long timeElapsed = System.nanoTime() - startTime;
    MetricManager.getInstance().saveCampaignDatasourceQueryTime(getCampaignId(joinPoint), timeElapsed / 1000000);
    return data;
  }

  private Long getCampaignId(ProceedingJoinPoint joinPoint) {
    Object[] args = joinPoint.getArgs();
    if (args.length >= 2) {
      Object campaignIdObject = args[1];
      if (campaignIdObject instanceof Long) {
        return (Long) campaignIdObject;
      }
    }
    LOG.warn("Can't get campaign name from joinPoint: " + joinPoint);
    return null;
  }

}
