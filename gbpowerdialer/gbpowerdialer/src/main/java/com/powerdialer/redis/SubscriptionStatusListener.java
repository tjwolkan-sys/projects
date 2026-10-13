package com.powerdialer.redis;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.redisson.api.listener.StatusListener;

/**
 * Listen and log subscription status
 */
public class SubscriptionStatusListener implements StatusListener {

  private static final Log log = LogFactory.getLog(SubscriptionStatusListener.class);

  private static final SubscriptionStatusListener instance = new SubscriptionStatusListener();

  private SubscriptionStatusListener() {}

  public static SubscriptionStatusListener getInstance() {
    return instance;
  }

  @Override
  public void onSubscribe(String channel) {
    log.info("Start subscription to channel: '" + channel + "'");
  }

  @Override
  public void onUnsubscribe(String channel) {
    log.info("Unsubscribe from channel: '" + channel + "'");
  }

}
