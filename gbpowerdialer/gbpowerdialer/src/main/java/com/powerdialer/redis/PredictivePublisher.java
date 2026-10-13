package com.powerdialer.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powerdialer.redis.event.PredictiveEvent;
import com.powerdialer.redis.event.predictive.PredictiveAlphaEvent;
import com.powerdialer.redis.event.predictive.PredictiveBetaEvent;
import com.powerdialer.redis.event.predictive.PredictiveGammaEvent;
import com.powerdialer.util.ApplicationUtil;
import com.powerdialer.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Component
public class PredictivePublisher {

    private static final Logger log = LoggerFactory.getLogger(PredictivePublisher.class);
    @Autowired
    private ObjectMapper objectMapper;

    private String publisher;

    private final RedisUtil redisUtil;
    private final ApplicationUtil applicationUtil;

    public PredictivePublisher(RedisUtil redisUtil, ApplicationUtil applicationUtil) {
        this.redisUtil = redisUtil;
        this.applicationUtil = applicationUtil;
    }

    @PostConstruct
    public void init() {
        publisher = applicationUtil.getThisApplicationLocalIpAddress();
    }

    private void publish(PredictiveEvent event) {
        try {
            redisUtil.publish(Constants.REDIS_CHANNEL.channelPredictiveEvent.toString(), objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            log.error("Could not publish: {}", event, e);
        }
    }

    public void publish(PredictiveAlphaEvent event) { publish((PredictiveEvent) event); }
    public void publish(PredictiveBetaEvent  event) { publish((PredictiveEvent) event); }
    public void publish(PredictiveGammaEvent event) { publish((PredictiveEvent) event); }
}
