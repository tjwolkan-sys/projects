package com.powerdialer.util;

import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.powerdialer.model.ActionLog;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.DNC;
import com.powerdialer.model.LSC;
import com.powerdialer.model.Tenant;
import com.powerdialer.model.User;
import com.powerdialer.service.ActionLogService;
import com.powerdialer.util.Constants.LOG_LEVEL;
import com.powerdialer.util.Constants.LOG_MODULE;

/**
 * Decorator for slf4j Logger. Logs are converted to ActionLog and saved to db.
 */
@Component
public class LoggerDecorator {

  private Logger log;

  @Autowired
  private ActionLogService actionLogService;

  public LoggerDecorator() {}

  public LoggerDecorator(Logger logger,
                         ActionLogService actionLogService) {
    this.log = logger;
    this.actionLogService = actionLogService;
  }

  /**
   * Get LoggerDecorator
   * @return LoggerDecorator
   */
  public LoggerDecorator getLogger() {
    if (this.log == null)
      throw new IllegalArgumentException("No logger class is defined");
    return this;
  }

  /**
   * Create LoggerDecorator for given class
   * @param clazz
   * @return LoggerDecorator
   */
  public LoggerDecorator getLogger(Class<?> clazz) {
    return new LoggerDecorator(LoggerFactory.getLogger(clazz), actionLogService);
  }

  /**
   * Save actionLog and log message as info
   * @param message
   * @param arguments
   */
  public void info(Object message, Object... arguments) {
    if (arguments.length > 0)
      actionLogService.addActionLog(createActionLog(LOG_LEVEL.INFO, message, arguments));
    log.info(message.toString());
  }

  /**
   * Save actionLog and log message as warning
   * @param message
   * @param arguments
   */
  public void warn(Object message, Object... arguments) {
    if (arguments.length > 0)
      actionLogService.addActionLog(createActionLog(LOG_LEVEL.WARNING, message, arguments));
    log.warn(message.toString());
  }

  /**
   * Save actionLog and log message as error
   * @param message
   * @param arguments
   */
  public void error(Object message, Object... arguments) {
    if (arguments.length > 0)
      actionLogService.addActionLog(createActionLog(LOG_LEVEL.ERROR, message, arguments));
    log.error(message.toString());
  }

  /**
   * Save actionLog and log message as error
   * @param message
   * @param throwable
   * @param arguments
   */
  public void error(Object message, Throwable throwable, Object... arguments) {
    if (arguments.length > 0)
      actionLogService.addActionLog(createActionLog(LOG_LEVEL.ERROR, message, arguments));
    log.error(message.toString(), throwable);
  }

  /**
   * Task 325469: Operasyon ActionLog ekranindan LSC akisini izledigi icin
   * ActionLog kaydi info ile birebir ayni sekilde (INFO seviyesiyle) korunur;
   * dosya/console appender'ina ise debug seviyesinde yazilir. Uretimde root
   * logger INFO oldugundan bu satirlar logback appender kilidine hic girmez;
   * gerekirse ilgili logger DEBUG yapilarak dosya logunda da acilir.
   */
  public void audit(Object message, Object... arguments) {
    if (arguments.length > 0)
      actionLogService.addActionLog(createActionLog(LOG_LEVEL.INFO, message, arguments));
    log.debug(message.toString());
  }

  /**
   * Create ActionLog object and save
   * @param message
   * @param arguments
   */
  private ActionLog createActionLog(LOG_LEVEL level, Object message, Object... arguments) {
    ActionLog actionLog = new ActionLog();
    actionLog.setLog(message == null ? null : message.toString());
    actionLog.setCreateDate(new Date());
    actionLog.setLogLevel(level);
    processArguments(actionLog, arguments);
    return actionLog;
  }

  /**
   * Process given arguments to set actionLog variables
   */
  private void processArguments(ActionLog actionLog, Object... arguments) {
    for (Object object : arguments) {
      if (object instanceof Campaign) {
        Campaign campaign = (Campaign) object;
        actionLog.setCampaign(campaign);
        actionLog.setTenant(campaign.getTenant());
      } else if (object instanceof LSC) {
        LSC lsc = (LSC) object;
        actionLog.setLsc(lsc);
        actionLog.setTenant(lsc.getTenant());
      } else if (object instanceof DNC) {
        DNC dnc = (DNC) object;
        actionLog.setDnc(dnc);
        actionLog.setTenant(dnc.getTenant());
      } else if (object instanceof User) {
        User user = (User) object;
        actionLog.setUser(user);
        actionLog.setTenant(user.getTenant());
      } else if (object instanceof LOG_MODULE) {
        actionLog.setLogModule((LOG_MODULE) object);
      } else if (object instanceof Tenant) {
        actionLog.setTenant((Tenant) object);
      }
    }
  }
}
