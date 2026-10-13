package com.powerdialer.engine.asterisk.actions;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.apache.commons.lang3.StringUtils;
import org.asteriskjava.manager.action.OriginateAction;

import com.powerdialer.model.Call;
import com.powerdialer.serialization.OriginateActionSerializer;
import com.powerdialer.util.Constants.MANAGER_ACTION_VARIABLE;
import com.powerdialer.util.DevProperties;

/**
 * Abstract parent for all originate actions
 */
@JsonSerialize(using = OriginateActionSerializer.class)
public abstract class AbstractOriginateAction extends OriginateAction {

  private static final long serialVersionUID = -4396074066733693717L;
  private Call call;

  public AbstractOriginateAction(Call call) {
    this.call = call;
    setInstanceVariables();
  }

  private void setInstanceVariables() {
    setVariable(MANAGER_ACTION_VARIABLE.ORIGIN_ID.getParam(), call.getId());
    setVariable(MANAGER_ACTION_VARIABLE.PARENT_ID.getParam(), call.getId());
    setVariable(MANAGER_ACTION_VARIABLE.CALLER_ID.getParam(), call.getCallerId());
    setVariable(MANAGER_ACTION_VARIABLE.CONTACT_ID.getParam(), call.getUniqueKey());
    setVariable(MANAGER_ACTION_VARIABLE.CAMPAIGN_ID.getParam(), call.getCampaign().getId().toString());
    setVariable(MANAGER_ACTION_VARIABLE.CAMPAIGN_NAME.getParam(), call.getCampaign().getName());
    setVariable(MANAGER_ACTION_VARIABLE.CAMPAIGN_TYPE.getParam(), String.valueOf(call.getCampaign().getType()));
    setVariable(MANAGER_ACTION_VARIABLE.DIALED_NUMBER.getParam(), call.getCalledNumber());
    setVariable(MANAGER_ACTION_VARIABLE.ALLOW_CALLBACK.getParam(), String.valueOf(call.getCampaign().getAllowCallback()));
    setVariable(MANAGER_ACTION_VARIABLE.ALLOW_CUSTOM_CALLBACK.getParam(), String.valueOf(call.getCampaign().getAllowCallbackCustom()));
    if (call.getCampaign().getResultCodeTimerStatus() != null) {
      setVariable(MANAGER_ACTION_VARIABLE.RESULT_CODE_TIMER_STATUS.getParam(), String.valueOf(call.getCampaign().getResultCodeTimerStatus()));
    }
    setVariable(MANAGER_ACTION_VARIABLE.RESULT_CODE_TIMER.getParam(), String.valueOf(call.getCampaign().getResultCodeTimer()));

    if (!StringUtils.isEmpty(DevProperties.AGI_IP))
      setVariable(MANAGER_ACTION_VARIABLE.AGI_SERVER_IP.getParam(), DevProperties.AGI_IP);

    if (Boolean.TRUE.equals(call.getCampaign().getAmdEnabled()))
      setVariable(MANAGER_ACTION_VARIABLE.AMD_ENABLED.getParam(), "1");

    if (call.getUser() != null) {
      setVariable(MANAGER_ACTION_VARIABLE.AGENT_ID.getParam(), call.getUser().getId().toString());
      setVariable(MANAGER_ACTION_VARIABLE.AGENT_CURRENT_GROUP_ID.getParam(), call.getUser().getGroup().getId().toString());
      setVariable(MANAGER_ACTION_VARIABLE.AGENT_SESSION_ID.getParam(), call.getUser().getSessionId().toString());
      setVariable(MANAGER_ACTION_VARIABLE.AGENT_TENANT_ID.getParam(), call.getUser().getTenant().getId().toString());
    }

    setChannel(String.format("local/%s@" + call.getCampaign().getContext() + "/n", call.getCalledNumber()));
    setPriority(1);
    setAsync(true);
    setTimeout((long) (call.getCampaign().getRingTimeout()) * 1000);
    setVariables(getVariables());
    setActionId(call.getId());
    setAccount(call.getId());
  }

  /**
   * Build originateAction object
   * @return OriginateAction
   */
  public abstract OriginateAction build(String inboundContext);

  /**
   * FIX-GLB90060295: getter BİLEREK public değildir. asterisk-java action'ları sabit alan listesiyle
   * değil refleksiyonla serileştirir: {@code ReflectionUtil.getGetters} yalnız <b>public</b>,
   * parametresiz, adı {@code get}/{@code is} ile başlayan metotları toplar ve
   * {@code ActionBuilderImpl.appendGetters} bunlardan {@code {class, action, actionid, attributes}}
   * dışında kalan her birini AMI başlığı yapar. Public kaldığında tüm {@code Call} nesnesi
   * {@code call: Call [ … retryHistory=[…] … ]} satırı olarak Asterisk'e gönderiliyor, satır
   * uzunluğu retry geçmişiyle büyüyor ve manager'ın 1024 baytlık satır tamponunu aşınca Asterisk
   * originate'i {@code 'Failed to parse message: line too long'} ile reddediyordu (TRC 907).
   * Asterisk bu başlığı zaten hiç okumaz; dialplan/AGI yalnız {@code Variable:} değerlerini görür.
   * Erişim paket içinde kalır; tüm çağıranlar aynı paketteki action alt sınıflarıdır.
   */
  protected Call getCall() {
    return call;
  }

  protected void setCall(Call call) {
    this.call = call;
  }

}
