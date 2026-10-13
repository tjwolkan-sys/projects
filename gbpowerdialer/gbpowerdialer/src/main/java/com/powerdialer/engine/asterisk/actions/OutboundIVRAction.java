package com.powerdialer.engine.asterisk.actions;

import org.asteriskjava.manager.action.OriginateAction;

import com.powerdialer.model.Call;

/**
 * OriginateAction for outboundIVR campaign type
 */
public class OutboundIVRAction extends AbstractOriginateAction {

  private static final long serialVersionUID = -540198401555772922L;

  public OutboundIVRAction(Call call) {
    super(call);
  }

  /**
   * Campaign type specific manipulations
   */
  @Override
  public OriginateAction build(String inboundContext) {
    setContext(inboundContext);
    setExten(String.format("q%s", getCall().getCampaign().getIvrApplicationNumber().getDigits()));
    return this;
  }
}
