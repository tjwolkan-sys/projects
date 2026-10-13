package com.powerdialer.engine.synchronization;

import java.util.Collection;
import java.util.Map;

import javax.annotation.PostConstruct;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;

import com.google.common.cache.Cache;

import com.powerdialer.engine.asterisk.ami.ManagerConnectionComposition;
import com.powerdialer.engine.asterisk.ami.ManagerConnectionDecorator;
import com.powerdialer.engine.dialer.DialerFacade;

/**
 * Class that synchronize campaigns statistics with actual data
 */
// @Component
// @Lazy(false)
public class StatisticSynchronizer {

  private static final Log LOG = LogFactory.getLog(DialerFacade.class);

  @Autowired
  private DialerFacade dialerFacade;

  @Autowired
  private ManagerConnectionComposition connectionComposition;

  @PostConstruct
  public void init() {
    System.out.println("init StatisticSynchronizer");
  }

  @Scheduled(fixedRate = 60000, initialDelay = 60000)
  public void synchronizeActiveCalls() {
    ActiveCallsCounter activeCallsCounter = new ActiveCallsCounter();
    connectionComposition.getOpenedConnections().values().stream()
        .map(ManagerConnectionDecorator::getCalls)
        .map(Cache::asMap)
        .map(Map::values)
        .flatMap(Collection::stream)
        .forEach(activeCallsCounter::checkAndCount);
    activeCallsCounter.getCounters().forEach((campaignId, activeCalls) -> {
      int currentActiveCalls = dialerFacade.getCampaignContexts().get(campaignId).getCampaign().getActiveCalls().getAndSet(activeCalls);
      if (currentActiveCalls != activeCalls) {
        LOG.info("Active calls changer from: " + currentActiveCalls + " to: " + activeCalls + " for campaignId: " + campaignId);
      }
    });
  }

}
