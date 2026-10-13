package com.powerdialer.engine.predictive.dispatcher;

import com.powerdialer.redis.event.PredictiveEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;

/**
 * Base class for asynchronous dispatch of predictive events.
 *
 * <p>Concrete dispatchers (e.g., Alpha/Beta/Gamma) extend this class and implement
 * {@link #publishEvent(PredictiveEvent)} to route the event to the underlying transport
 * (e.g., Redis via a publisher). This base guarantees non-blocking dispatch with a
 * consistent logging/exception policy: failures are logged at WARN and do not interrupt
 * caller flow.</p>
 *
 * @param <T> concrete predictive event type
 */
public abstract class PredictiveEventDispatcher<T extends PredictiveEvent> {

    /** Instance-level logger so subclass names appear correctly in logs. */
    protected final Logger log = LoggerFactory.getLogger(getClass());

    /**
     * Dispatches the given predictive event asynchronously.
     *
     * <p>Any exception thrown by the underlying publisher is caught and logged at WARN level,
     * so the calling thread is never disrupted.</p>
     *
     * @param event the predictive event to publish (must not be {@code null})
     */
    @Async
    public void dispatchAsync(T event) {
        try {
            if (event == null) {
                log.warn("Ignored null predictive event for type {}", getEventType());
                return;
            }
            log.info("Publishing predictive {} event (publisher={}, class={}, created={})", getEventType(), event.getPublisher(), event.getEventClass(), event.getCreateDate());
            publishEvent(event);
        } catch (Exception e) {
            log.warn("Failed to publish predictive {} event. Continuing without interruption.", getEventType(), e);
        }
    }

    /**
     * Concrete transport logic (e.g., call the appropriate method on a PredictivePublisher).
     */
    protected abstract void publishEvent(T event);

    /**
     * A short label for log context (e.g., "ALPHA", "BETA", "GAMMA").
     */
    protected abstract String getEventType();
}
