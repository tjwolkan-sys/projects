package com.powerdialer.engine.predictive.dispatcher;

import com.powerdialer.redis.PredictivePublisher;
import com.powerdialer.redis.event.predictive.PredictiveGammaEvent;
import org.springframework.stereotype.Component;

@Component
public class PredictiveGammaEventDispatcher extends PredictiveEventDispatcher<PredictiveGammaEvent> {
    private final PredictivePublisher predictivePublisher;
    public PredictiveGammaEventDispatcher(PredictivePublisher predictivePublisher) { this.predictivePublisher = predictivePublisher; }
    @Override protected void publishEvent(PredictiveGammaEvent event) { predictivePublisher.publish(event); }
    @Override protected String getEventType() { return "GAMMA"; }
}