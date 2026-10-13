package com.powerdialer.redis;

import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.redisson.api.listener.MessageListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import com.powerdialer.engine.dialer.CampaignContext;
import com.powerdialer.engine.dialer.DialerFacade;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.RoutingGroup;
import com.powerdialer.redis.message.RdsConfigurationMessage;
import com.powerdialer.service.CampaignService;
import com.powerdialer.service.RoutingGroupService;
import com.powerdialer.util.Constants;
import com.powerdialer.util.LoggerDecorator;

@Component
@Lazy(false)
public class ResourceDistributionServiceSubscriber implements MessageListener<String> {

  private final ObjectMapper objectMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  private LoggerDecorator LOG;

  @Autowired
  private LoggerDecorator loggerDecorator;

  @Autowired
  private RedisUtil redisUtil;

  @Autowired
  private DialerFacade dialerFacade;

  @Autowired
  private RoutingGroupService routingGroupService;

  @Autowired
  private CampaignService campaignService;

  @PostConstruct
  private void init() {
    redisUtil.subscribe(Constants.REDIS_CHANNEL.channelQssConfiguration.toString(), this);
    LOG = loggerDecorator.getLogger(ResourceDistributionServiceSubscriber.class);
  }

  @Override
  public void onMessage(CharSequence channel, String rawMessage) {
    try {
      LOG.info("Message: " + rawMessage.substring(0, Math.min(200, rawMessage.length())) + "... received");
      RdsConfigurationMessage message = objectMapper.readValue(rawMessage, RdsConfigurationMessage.class);
      List<Campaign> relatedCampaigns = dialerFacade.getCampaignContexts().values().stream()
          .map(CampaignContext::getCampaign)
          .filter(c -> c.getCallerIdBehaviour() == Constants.CALLER_ID_BEHAVIOUR.POOL
              && c.getCallerIdPool() != null
              && c.getCallerIdPool().getId().equals(message.getRoutingGroupId()))
          .collect(Collectors.toList());
      if (!CollectionUtils.isEmpty(relatedCampaigns) && isRoutingGroupInactive(message)) {
        relatedCampaigns.forEach(this::stopCampaign);
      }
    } catch (Exception e) {
      LOG.error("Could not handle message " + rawMessage, e);
    }
  }

  private boolean isRoutingGroupInactive(RdsConfigurationMessage message) {
    if (message.getActionType() == Constants.RDS_MESSAGE_ACTION_TYPE.REMOVE) {
      return true;
    } else if (message.getActionType() == Constants.RDS_MESSAGE_ACTION_TYPE.RELOAD) {
      RoutingGroup routingGroup = routingGroupService.getRoutingGroup(message.getRoutingGroupId());
      return routingGroup == null || routingGroup.isDeleted() || !routingGroup.isEnabled();
    } else {
      return false;
    }
  }

  private void stopCampaign(Campaign campaign) {
    LOG.warn("CallerId pool is no active anymore for campaign " + campaign + ": Stopping...", Constants.LOG_MODULE.CAMPAIGN, campaign);
    campaignService.actionAsync(campaign.getId(), Constants.CAMPAIGN_ACTION.STOP_REGULAR, Constants.CAMPAIGN_STOP_TYPE.AUTOMATIC);
  }

}
