package com.powerdialer.engine.predictive.beta.model;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

public class PeriodicCallData {

    private final Date calculationIntervalStartTime = Date.from(ZonedDateTime.now(ZoneId.of("Europe/Istanbul")).toInstant());
    private final AtomicInteger ringingCallCount = new AtomicInteger();
    private final AtomicInteger activeCallCount = new AtomicInteger();
    private final AtomicInteger inCallCount = new AtomicInteger();
    private final AtomicInteger abandonedCallCount = new AtomicInteger();
    private final AtomicInteger noConnectionCallCount = new AtomicInteger();
    private final AtomicInteger badConnectionCallCount = new AtomicInteger();
    private final AtomicInteger offeredCallCount = new AtomicInteger();
    private float totalNumberOfCalls;

    public PeriodicCallData(float totalNumberOfCalls) {
        this.totalNumberOfCalls = totalNumberOfCalls;
    }

    public Date getCalculationIntervalStartTime() {
        return calculationIntervalStartTime;
    }

    public AtomicInteger getRingingCallCount() {
        return ringingCallCount;
    }

    public AtomicInteger getActiveCallCount() {
        return activeCallCount;
    }

    public AtomicInteger getInCallCount() {
        return inCallCount;
    }

    public AtomicInteger getAbandonedCallCount() {
        return abandonedCallCount;
    }

    public AtomicInteger getNoConnectionCallCount() {
        return noConnectionCallCount;
    }

    public AtomicInteger getBadConnectionCallCount() {
        return badConnectionCallCount;
    }

    public AtomicInteger getOfferedCallCount() {
        return offeredCallCount;
    }

    public float getTotalNumberOfCalls() {
        return totalNumberOfCalls;
    }

    public void setTotalNumberOfCalls(float totalNumberOfCalls) {
        this.totalNumberOfCalls = totalNumberOfCalls;
    }

    @Override
    public String toString() {
        return "PeriodicCallData{" +
                "calculationIntervalStartTime=" + calculationIntervalStartTime +
                ", ringCallCount=" + ringingCallCount +
                ", activeCallCount=" + activeCallCount +
                ", inCallCount=" + inCallCount +
                ", abandonedCallCount=" + abandonedCallCount +
                ", noConnectionCallCount=" + noConnectionCallCount +
                ", badConnectionCallCount=" + badConnectionCallCount +
                ", offeredCallCount=" + offeredCallCount +
                ", totalNumberOfCalls=" + totalNumberOfCalls +
                '}';
    }


}

