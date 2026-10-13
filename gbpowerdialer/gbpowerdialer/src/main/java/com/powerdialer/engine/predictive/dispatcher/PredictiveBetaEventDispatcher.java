package com.powerdialer.engine.predictive.dispatcher;

import com.powerdialer.redis.PredictivePublisher;
import com.powerdialer.redis.event.predictive.PredictiveBetaEvent;
import org.springframework.stereotype.Component;

@Component
public class PredictiveBetaEventDispatcher extends PredictiveEventDispatcher<PredictiveBetaEvent> {
    private final PredictivePublisher predictivePublisher;
    public PredictiveBetaEventDispatcher(PredictivePublisher predictivePublisher) { this.predictivePublisher = predictivePublisher; }
    @Override protected void publishEvent(PredictiveBetaEvent event) { predictivePublisher.publish(event); }
    @Override protected String getEventType() { return "BETA"; }
}