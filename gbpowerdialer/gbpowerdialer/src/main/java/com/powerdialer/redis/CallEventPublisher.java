package com.powerdialer.redis;

import java.util.Date;

import javax.annotation.PostConstruct;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.powerdialer.redis.event.CallEvent;
import com.powerdialer.redis.event.dialercall.DialerCallAbandonIVREvent;
import com.powerdialer.redis.event.dialercall.DialerCallAgentAnswerEvent;
import com.powerdialer.redis.event.dialercall.DialerCallCustomerAnswerEvent;
import com.powerdialer.redis.event.dialercall.DialerCallDialEvent;
import com.powerdialer.redis.event.dialercall.DialerCallEndEvent;
import com.powerdialer.redis.event.dialercall.DialerCallPreviewAcceptEvent;
import com.powerdialer.redis.event.dialercall.DialerCallPreviewEvent;
import com.powerdialer.util.ApplicationUtil;
import com.powerdialer.util.Constants;

@Component
public class CallEventPublisher {

  private static final Logger log = LoggerFactory.getLogger(CallEventPublisher.class);
  @Autowired
  private ObjectMapper objectMapper;

  private String publisher;

  private final RedisUtil redisUtil;
  private final ApplicationUtil applicationUtil;

  public CallEventPublisher(RedisUtil redisUtil, ApplicationUtil applicationUtil) {
    this.redisUtil = redisUtil;
    this.applicationUtil = applicationUtil;
  }

  @PostConstruct
  public void init() {
    publisher = applicationUtil.getThisApplicationLocalIpAddress();
    log.info("Got publisher info : " + publisher);
  }

  public void publishCallDialEvent(String interactionId, Long campaignId, Date dialDate, String dnis, Long ivrId, String serverName, String callerId,  Date fetchDate) {
    publish(new DialerCallDialEvent(publisher, interactionId, campaignId, dialDate, dnis, ivrId, serverName, callerId, fetchDate));
  }

  public void publishCallCustomerAnswerEvent(String interactionId, Date dialDate) {
    publish(new DialerCallCustomerAnswerEvent(publisher, interactionId, dialDate));
  }

  public void publishCallAgentAnswerEvent(String interactionId, Date dialDate, String username) {
    publish(new DialerCallAgentAnswerEvent(publisher, interactionId, dialDate, username));
  }

  public void publishCallPreviewEvent(String interactionId, Long campaignId, String dnis, String username, String serverName, String callerId, Date fetchDate) {
    publish(new DialerCallPreviewEvent(publisher, interactionId, campaignId, dnis, username, serverName, callerId, fetchDate));
  }

  public void publishCallEndEvent(String interactionId, Date endDate) {
    publish(new DialerCallEndEvent(publisher, interactionId, endDate));
  }

  public void publishCallAbandonIVREvent(String interactionId) {
    publish(new DialerCallAbandonIVREvent(publisher, interactionId));
  }

  public void publishCallPreviewAcceptEvent(String interactionId, Date acceptDate) {
    publish(new DialerCallPreviewAcceptEvent(publisher, interactionId, acceptDate));
  }

  private void publish(CallEvent event) {
    try {
      redisUtil.publish(Constants.REDIS_CHANNEL.channelCallEvent.toString(), objectMapper.writeValueAsString(event));
    } catch (Exception e) {
      log.error("Could not publish: {}", event, e);
    }
    log.info("Call event published: {}", event);
  }


}
