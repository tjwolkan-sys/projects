package com.powerdialer.exceptions;

import com.powerdialer.model.Campaign;

public class ModifiedWhilePrioritiesRecalculatingException extends RuntimeException {

  public ModifiedWhilePrioritiesRecalculatingException(Campaign campaign) {
    super ("Campaign could not be modified due to active priority recalculation process: " + campaign);
  }

}
