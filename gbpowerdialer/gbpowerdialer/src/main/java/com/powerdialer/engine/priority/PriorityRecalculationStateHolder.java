package com.powerdialer.engine.priority;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.powerdialer.util.Constants;

@Component
public class PriorityRecalculationStateHolder {

  private final Map<Long, Constants.PRIORITY_RECALCULATION_STATE> recalculations = new ConcurrentHashMap<>();

  public boolean isRecalculationInProgress(Long campaignId) {
    return recalculations.containsKey(campaignId);
  }

  Constants.PRIORITY_RECALCULATION_STATE get(Long campaignId) {
    return recalculations.get(campaignId);
  }

  void put(Long campaignId, Constants.PRIORITY_RECALCULATION_STATE state) {
    recalculations.put(campaignId, state);
  }

  void remove (Long campaignId) {
    recalculations.remove(campaignId);
  }

  int size() {
    return recalculations.size();
  }

}
