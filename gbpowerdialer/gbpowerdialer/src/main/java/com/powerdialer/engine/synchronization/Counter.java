package com.powerdialer.engine.synchronization;

import java.util.HashMap;
import java.util.Map;

import com.powerdialer.model.Call;

/**
 * Class for holding campaigns statistic counters
 */
public abstract class Counter {

  /**
   * Here kes - campaign id, value - specific counter
   */
  protected final Map<Long, Integer> counters = new HashMap<>();

  /**
   * Check and increment counter for specific campaign
   * @param call
   */
  abstract void checkAndCount(Call call);

  public Map<Long, Integer> getCounters() {
    return counters;
  }

}
