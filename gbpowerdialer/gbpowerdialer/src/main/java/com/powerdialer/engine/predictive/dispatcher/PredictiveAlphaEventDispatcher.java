package com.powerdialer.engine.predictive.dispatcher;

import com.powerdialer.redis.PredictivePublisher;
import com.powerdialer.redis.event.predictive.PredictiveAlphaEvent;
import org.springframework.stereotype.Component;

@Component
public class PredictiveAlphaEventDispatcher extends PredictiveEventDispatcher<PredictiveAlphaEvent> {

    private final PredictivePublisher predictivePublisher;

    public PredictiveAlphaEventDispatcher(PredictivePublisher predictivePublisher) {
        this.predictivePublisher = predictivePublisher;
    }

    @Override
    protected void publishEvent(PredictiveAlphaEvent event) {
        // requires PredictivePublisher.publish(PredictiveAlphaEvent) overload
        predictivePublisher.publish(event);
    }

    @Override
    protected String getEventType() {
        return "ALPHA";
    }
}
