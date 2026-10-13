package com.powerdialer.engine.asterisk.actions;

import org.asteriskjava.manager.action.OriginateAction;

import com.powerdialer.model.Call;

/**
 * OriginateAction for previewStraight campaign type
 */
public class PreviewStraightAction extends PreviewFreeAction {

  private static final long serialVersionUID = -5852706797777631463L;

  public PreviewStraightAction(Call call) {
    super(call);
  }

  /**
   * Campaign type specific manipulations
   */
  @Override
  public OriginateAction build(String inboundContext) {
    super.build(inboundContext);
    return this;
  }
}
