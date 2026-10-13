package com.powerdialer.engine.dialer.endlistbehaviour;

import com.powerdialer.engine.dialer.CampaignContext;

/**
 * Factory for creating end list behaviour for campaign context
 */
public class EndListBehaviourFactory {

  private EndListBehaviourFactory() {}

  public static EndListBehaviour create(CampaignContext campaignContext) {
    EndListBehaviour behaviour = null;
    if (campaignContext.getCampaign() != null) {
      switch (campaignContext.getCampaign().getEndListBehaviour()) {
      case CONTINUE:
        behaviour = () -> {};
        break;
      case STOP_DISCARDING_RETRIES_AND_CALLBACKS:
        behaviour = new StopDiscardingBehaviour(campaignContext);
        break;
      case STOP_ACCELERATING_RETRIES_AND_CALLBACKS:
        behaviour = new StopAcceleratingBehaviour(campaignContext);
        break;
      }
    }
    return behaviour;
  }

}
