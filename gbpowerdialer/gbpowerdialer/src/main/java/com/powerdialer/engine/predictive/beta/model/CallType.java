package com.powerdialer.engine.predictive.beta.model;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * Enum representing different types of call states and their associated count extractors.
 */
public enum CallType {

    RINGING(PeriodicCallData::getRingingCallCount),
    ACTIVE(PeriodicCallData::getActiveCallCount),
    IN_CALL(PeriodicCallData::getInCallCount),
    ABANDONED(PeriodicCallData::getAbandonedCallCount),
    NO_CONNECTION(PeriodicCallData::getNoConnectionCallCount),
    BAD_CONNECTION(PeriodicCallData::getBadConnectionCallCount),
    OFFERED(PeriodicCallData::getOfferedCallCount);

    private final Function<PeriodicCallData, AtomicInteger> countExtractor;

    CallType(Function<PeriodicCallData, AtomicInteger> countExtractor) {
        this.countExtractor = countExtractor;
    }

    public AtomicInteger extract(PeriodicCallData data) {
        return countExtractor.apply(data);
    }
}