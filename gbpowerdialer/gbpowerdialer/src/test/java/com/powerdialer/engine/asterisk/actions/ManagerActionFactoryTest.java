package com.powerdialer.engine.asterisk.actions;


import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;

import java.util.Properties;

import org.asteriskjava.manager.action.OriginateAction;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.PropertyPlaceholderConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.Group;
import com.powerdialer.model.Number;
import com.powerdialer.model.Tenant;
import com.powerdialer.model.User;
import com.powerdialer.util.Constants;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration
public class ManagerActionFactoryTest {

  private static final String OUTBOUND_IVR_EXTENSION_PREFIX ="q";
  private static final String OUTBOUND_IVR_INBOUND_CONTEXT="outboundIvrContext";
  private static final String PROGRESSIVE_INBOUND_CONTEXT="progressiveContext";
  private static final String PREVIEW_INBOUND_CONTEXT="previewContext";

  private Campaign campaign = new Campaign();
  private Call call = new Call();
  private User user = new User();
  private String expectedChannelToDialRemoteParty;
  private String expectedChannelToDialAgent;

  @Configuration
  static class ContextConfiguration {

    @Bean
    public PropertyPlaceholderConfigurer propertyConfigurer() {
      PropertyPlaceholderConfigurer propertiesConfigurer = new PropertyPlaceholderConfigurer();
      Properties properties = new Properties();
      properties.setProperty("pbx.context.outboundIvr", OUTBOUND_IVR_INBOUND_CONTEXT);
      properties.setProperty("pbx.context.progressive", PROGRESSIVE_INBOUND_CONTEXT);
      properties.setProperty("pbx.context.preview", PREVIEW_INBOUND_CONTEXT);
      propertiesConfigurer.setProperties(properties);
      return propertiesConfigurer;
    }

    @Bean
    public ManagerActionFactory managerActionFactory() { return new ManagerActionFactory(); };
  }

  @Autowired
  ManagerActionFactory managerActionFactory;

  @Before
  public void setUp() {
    call.setCampaign(campaign);
    call.setCalledNumber("0123456789");
    campaign.setId(111L);
    campaign.setContext("destinationContext");
    campaign.setCallerIdBehaviour(Constants.CALLER_ID_BEHAVIOUR.FIXED);
    user.setId(444L);
    user.setUsername("81013");
    user.setGroup(new Group());
    user.getGroup().setId(222L);
    user.setTenant(new Tenant());
    user.getTenant().setId(333L);
    user.setSessionId(555L);
    campaign.setAmdEnabled(false);
    campaign.setRingTimeout(0);

    expectedChannelToDialRemoteParty = "local/" + call.getCalledNumber() + "@" + campaign.getContext() + "/n";
    expectedChannelToDialAgent = "local/" + user.getUsername() + "@" + PREVIEW_INBOUND_CONTEXT + "/n";
  }

  @Test
  public void outboundIvrOriginateActionTest() {
    campaign.setType(Constants.CAMPAIGN_TYPE.OUTBOUND_IVR);
    Number number = new Number();
    number.setDigits("112233");
    campaign.setIvrApplicationNumber(number);

    OriginateAction originateAction = managerActionFactory.generateCall(call);

    assertThat(originateAction.getChannel(), is(expectedChannelToDialRemoteParty));
    assertThat(originateAction.getContext(), is(OUTBOUND_IVR_INBOUND_CONTEXT));
    assertThat(originateAction.getExten(), is(OUTBOUND_IVR_EXTENSION_PREFIX + number.getDigits()));
  }

  @Test
  public void progressiveReleasedOriginateActionTest() {
    campaign.setType(Constants.CAMPAIGN_TYPE.PROGRESSIVE_RELEASED);
    call.setUser(user);

    OriginateAction originateAction = managerActionFactory.generateCall(call);

    assertThat(originateAction.getChannel(), is(expectedChannelToDialRemoteParty));
    assertThat(originateAction.getContext(), is(PROGRESSIVE_INBOUND_CONTEXT));
    assertThat(originateAction.getExten(), is(user.getUsername()));
  }

  @Test
  public void progressiveReservedOriginateActionTest() {
    campaign.setType(Constants.CAMPAIGN_TYPE.PROGRESSIVE_RESERVED);
    call.setUser(user);

    OriginateAction originateAction = managerActionFactory.generateCall(call);

    assertThat(originateAction.getChannel(), is(expectedChannelToDialRemoteParty));
    assertThat(originateAction.getContext(), is(PROGRESSIVE_INBOUND_CONTEXT));
    assertThat(originateAction.getExten(), is(user.getUsername()));
  }

  @Test
  public void previewFreeOriginateActionTest() {
    campaign.setType(Constants.CAMPAIGN_TYPE.PREVIEW_FREE);
    call.setUser(user);

    OriginateAction originateAction = managerActionFactory.generateCall(call);

    assertThat(originateAction.getChannel(), is(expectedChannelToDialAgent));
    assertThat(originateAction.getContext(), is(campaign.getContext()));
    assertThat(originateAction.getExten(), is(call.getCalledNumber()));
  }

  @Test
  public void previewStraightOriginateActionTest() {
    campaign.setType(Constants.CAMPAIGN_TYPE.PREVIEW_STRAIGHT);
    call.setUser(user);

    OriginateAction originateAction = managerActionFactory.generateCall(call);

    assertThat(originateAction.getChannel(), is(expectedChannelToDialAgent));
    assertThat(originateAction.getContext(), is(campaign.getContext()));
    assertThat(originateAction.getExten(), is(call.getCalledNumber()));
  }

}