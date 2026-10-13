package com.powerdialer.engine.asterisk.actions;

import org.asteriskjava.manager.action.OriginateAction;

import com.powerdialer.model.Call;

/**
 * OriginateAction for progressiveReleased campaign type
 */
public class ProgressiveReleasedAction extends AbstractOriginateAction {

  private static final long serialVersionUID = -5852706797777631463L;

  public ProgressiveReleasedAction(Call call) {
    super(call);
  }

  /**
   * Campaign type specific manipulations
   */
  @Override
  public OriginateAction build(String inboundContext) {
    setContext(inboundContext);
    setExten(getCall().getUser().getUsername());
    return this;
  }
}
