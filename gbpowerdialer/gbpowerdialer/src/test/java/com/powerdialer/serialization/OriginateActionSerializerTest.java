package com.powerdialer.serialization;

import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assert.*;

import java.util.Collections;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.asteriskjava.manager.action.OriginateAction;
import org.json.JSONObject;
import org.junit.Test;

import com.powerdialer.engine.asterisk.actions.AbstractOriginateAction;
import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.util.Constants;

/**
 * Test for OriginateActionSerializer class
 */
public class OriginateActionSerializerTest {

  /**
   * Test that resulting JSON contains required fieldsk
   * @throws Exception
   */
  @Test
  public void testSerialize() throws Exception {
    ObjectMapper objectMapper = new ObjectMapper();

    Campaign campaign = new Campaign();
    campaign.setId(1L);
    campaign.setColResultRetryNumber("colRetryNumber");
    campaign.setCallerIdBehaviour(Constants.CALLER_ID_BEHAVIOUR.FIXED);
    campaign.setAttachedDatas(Collections.emptyList());
    campaign.setAmdEnabled(false);
    campaign.setRingTimeout(0);

    Call call = new Call();
    call.setRawData(new JSONObject());
    call.setCampaign(campaign);

    OriginateAction customOriginateAction = new AbstractOriginateAction(call) {
      @Override
      public OriginateAction build(String inboundContext) {
        return this;
      }
    };
    String json = objectMapper.writeValueAsString(customOriginateAction);

    assertThat(json, containsString("channel"));
    assertThat(json, containsString("priority"));
    assertThat(json, containsString("async"));
    assertThat(json, containsString("timeout"));
    assertThat(json, containsString("actionId"));
    assertThat(json, containsString("account"));
    assertThat(json, containsString("exten"));
    assertThat(json, containsString("variables"));
  }
}
