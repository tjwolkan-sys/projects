package com.powerdialer.engine.synchronization;

import com.powerdialer.model.Call;

/**
 * Counter for campaign active calls
 */
public class ActiveCallsCounter extends Counter {

  @Override
  public void checkAndCount(Call call) {
    if(call.isActive()) {
      counters.compute(call.getCampaign().getId(), (key, oldValue) -> {
        if(oldValue != null) {
          return oldValue + 1;
        } else {
          return 1;
        }
      });
    }
  }

}
