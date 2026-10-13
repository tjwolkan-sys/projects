package com.powerdialer.engine.asterisk.actions;

import static com.powerdialer.util.Constants.MANAGER_ACTION_VARIABLE.CLIENT_DIAL_TIMEOUT;
import static com.powerdialer.util.Constants.MANAGER_ACTION_VARIABLE.DIALED_NUMBER;

import org.asteriskjava.manager.action.OriginateAction;

import com.powerdialer.model.Call;

/**
 * OriginateAction for previewFree campaign type
 */
public class PreviewFreeAction extends AbstractOriginateAction {

  private static final long serialVersionUID = -5852706797777631463L;

  public PreviewFreeAction(Call call) {
    super(call);
  }

  /**
   * Campaign type specific manipulations
   */
  @Override
  public OriginateAction build(String inboundContext) {
    setChannel(String.format("local/%s@" + inboundContext + "/n", getCall().getUser().getUsername()));
    setTimeout((Long) null);
    setVariable(DIALED_NUMBER.getParam(), getCall().getCalledNumber());
    setVariable(CLIENT_DIAL_TIMEOUT.getParam(), (getCall().getCampaign().getRingTimeout()) * 1000 + "");
    setContext(getCall().getCampaign().getContext());
    setExten(getCall().getCalledNumber());
    return this;
  }
}
