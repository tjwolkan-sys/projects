package com.powerdialer.engine.dialer.state;

import com.powerdialer.util.Constants.CAMPAIGN_ACTION;

/**
 * Interface providing methods for campaign states
 */
public interface CampaignState {

  void start();

  void pause();

  void stop(CAMPAIGN_ACTION action);

  void reload();

  void reset();

}
