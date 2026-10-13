package com.powerdialer.engine.boost;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.powerdialer.model.Call;

@Component
public class DynamicBoostTestCallsEmulator {

  private static final Logger log = LoggerFactory.getLogger(DynamicBoostTestCallsEmulator.class);
  private static final int EMULATION_INTERVAL_MS = 1000;

  private final Map<Long, TestCallsEmulationParams> emulationParams = new ConcurrentHashMap<>();

  @Autowired
  private DynamicBoostManager dynamicBoostManager;

  public void applyEmulationParams(Long campaignId, TestCallsEmulationParams params) {
    if (params.getAnsweredCallsMax() == 0 && params.getFailedCallsMax() == 0
        && params.getAbandonedCallsMax() == 0 && params.getHandledCallsMax() == 0) {
      log.info("Got zero max calls to emulate, cancelling emulation");
      stopEmulation(campaignId);
    }
    log.info("Starting emulation for campaign with id {}: {}", campaignId, params);
    emulationParams.put(campaignId, params);
  }

  public void stopEmulation(Long campaignId) {
    log.info("Stopping emulation for campaign with id {}", campaignId);
    emulationParams.remove(campaignId);
  }

  public TestCallsEmulationParams getEmulationParams(Long campaignId) {
    return emulationParams.get(campaignId);
  }

  @Scheduled(fixedRate = EMULATION_INTERVAL_MS)
  private void emulateCalls() {
    try {
      emulationParams.forEach(this::emulateCallsForCampaign);
    } catch (Exception e) {
      log.error("Could not emulate calls", e);
    }
  }

  private void emulateCallsForCampaign(Long campaignId, TestCallsEmulationParams params) {
    try {
      log.trace("Emulating calls for campaign with id {} according to {}", campaignId, params);
      Call call = DynamicBoostManager.createFakeCall(campaignId);
      emulateCallsForCampaign(call, params.getAnsweredCallsMin(), params.getAnsweredCallsMax(), dynamicBoostManager::onCallAnsweredByClient);
      emulateCallsForCampaign(call, params.getFailedCallsMin(), params.getFailedCallsMax(), dynamicBoostManager::onCallFailed);
      emulateCallsForCampaign(call, params.getAbandonedCallsMin(), params.getAbandonedCallsMax(), dynamicBoostManager::onCallAbandoned);
      emulateCallsForCampaign(call, params.getHandledCallsMin(), params.getHandledCallsMax(), dynamicBoostManager::onCallAnsweredByAgent);
    } catch (Exception e) {
      log.error("Could not emulate calls for campaign " + campaignId + " regarding the " + params, e);
    }
  }

  private void emulateCallsForCampaign(Call call, int min, int max, Consumer<Call> method) {
    if (min > max) {
      log.warn("Minimum value can not be greater then maximum value");
    } else if (max > 0) {
      int upperBoundary = getRandomNumber(min, max);
      for (int i = 1; i <= upperBoundary; i++) {
        method.accept(call);
      }
    }
  }

  public int getRandomNumber(int min, int max) {
    Random random = new Random();
    if (max == min) {
      return min;
    } else if (max - min == 1) {
      return Math.round(random.nextFloat()) + min;
    }
    return random.nextInt(max - min) + min;
  }

}
