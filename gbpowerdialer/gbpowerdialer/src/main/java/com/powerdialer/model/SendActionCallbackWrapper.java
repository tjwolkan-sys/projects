package com.powerdialer.model;

import java.time.LocalTime;

import java.util.Date;
import java.util.function.BiConsumer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.asteriskjava.manager.SendActionCallback;
import org.asteriskjava.manager.response.ManagerError;
import org.asteriskjava.manager.response.ManagerResponse;

import com.powerdialer.engine.asterisk.ami.ManagerConnectionDecorator;
import com.powerdialer.engine.dialer.DialerFacade;
import com.powerdialer.jmx.MetricManager;

/**
 * A simple response handler that creates log for received response
 */
public class SendActionCallbackWrapper implements SendActionCallback {

  private static final Log LOG = LogFactory.getLog(DialerFacade.class);
  private final BiConsumer<String, Date> failHandler;
  private boolean isInUse = true;
  private LocalTime createDate;
  private long sendActionNanos;
  private final ManagerConnectionDecorator managerConnectionDecorator;

  public SendActionCallbackWrapper(ManagerConnectionDecorator managerConnectionDecorator,
                                   BiConsumer<String, Date> failHandler) {
    this.failHandler = failHandler;
    createDate = LocalTime.now();
    this.managerConnectionDecorator = managerConnectionDecorator;
  }

  @Override
  public void onResponse(ManagerResponse response) {
    MetricManager.getInstance().saveOriginationTimeForServer(managerConnectionDecorator.getServer().getIp(), (System.nanoTime() - sendActionNanos) / 1000000);
    Call call = managerConnectionDecorator.getCalls().getIfPresent(response.getActionId());
    LOG.info(response + " received for " + call);
    if (response instanceof ManagerError) {
      try {
        failHandler.accept(response.getActionId(), response.getDateReceived());
      } catch (Exception e) {
        // swallow
      }
    }
    setInUse(false);
  }

  public boolean isInUse() {
    return isInUse;
  }

  public void setInUse(boolean inUse) {
    isInUse = inUse;
  }

  public LocalTime getCreateDate() {
    return createDate;
  }

  public void setSendActionNanos(long sendActionNanos) {
    this.sendActionNanos = sendActionNanos;
  }
}