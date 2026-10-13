package com.powerdialer.engine.asterisk.actions;

import org.asteriskjava.manager.action.OriginateAction;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.util.Constants.CAMPAIGN_TYPE;

/**
 * Factory for Asterisk manager actions
 */
@Component
public class ManagerActionFactory {

  @Value("${pbx.context.progressive}")
  private String progressiveInboundContext;

  @Value("${pbx.context.outboundIvr}")
  private String ivrInboundContext;

  @Value("${pbx.context.preview}")
  private String previewInboundContext;

  /**
   * Creates originateAction for call
   * @param call
   * @return originateAction to make a call
   */
  public OriginateAction generateCall(Call call) {

    Campaign campaign = call.getCampaign();
    OriginateAction action = new OriginateAction();

    if (campaign.getType() == CAMPAIGN_TYPE.OUTBOUND_IVR) {

      action = new OutboundIVRAction(call).build(ivrInboundContext);

    } else if (campaign.getType() == CAMPAIGN_TYPE.PREVIEW_FREE) {

      action = new PreviewFreeAction(call).build(previewInboundContext);

    } else if (campaign.getType() == CAMPAIGN_TYPE.PREVIEW_STRAIGHT) {

      action = new PreviewStraightAction(call).build(previewInboundContext);

    } else if (campaign.getType() == CAMPAIGN_TYPE.PROGRESSIVE_RELEASED || campaign.getType() == CAMPAIGN_TYPE.PREDICTIVE) {

      action = new ProgressiveReleasedAction(call).build(progressiveInboundContext);

    } else if (campaign.getType() == CAMPAIGN_TYPE.PROGRESSIVE_RESERVED) {

      action = new ProgressiveReservedAction(call).build(progressiveInboundContext);

    }

    return action;

  }

}
