package com.powerdialer.engine.predictive.beta.model;

import com.powerdialer.model.Campaign;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class PredictiveCampaignCallData {

    private final Lock lock = new ReentrantLock(true);

    private ScheduledFuture<?> recalculationFuture;
    private PeriodicCallData currentData;
    private PeriodicCallData previousData;
    private PredictiveCallParameters predictiveCallParameters;
    private Campaign campaign;

    // ===== Recursive Beta State =====
    private float previousAlpha1;
    private float previousBeta1;
    private float previousBeta2;
    private float previousGamma1;
    private float previousGamma2;
    private float previousC1;

    public PredictiveCampaignCallData(Campaign campaign) {
        this.campaign = campaign;
    }

    public Campaign getCampaign() {
        return campaign;
    }

    public void setCampaign(Campaign campaign) {
        this.campaign = campaign;
    }

    public ScheduledFuture<?> getRecalculationFuture() {
        return recalculationFuture;
    }

    public void setRecalculationFuture(ScheduledFuture<?> scheduledFuture) {
        this.recalculationFuture = scheduledFuture;
    }

    public PeriodicCallData getCurrentData() {
        return currentData;
    }

    public void setCurrentData(PeriodicCallData currentData) {
        this.currentData = currentData;
    }

    public PeriodicCallData getPreviousData() {
        return previousData;
    }

    public void setPreviousData(PeriodicCallData previousData) {
        this.previousData = previousData;
    }

    public void restartCalculation(float totalNumberOfCalls) {
        if (currentData != null) {
            previousData = currentData;
        }
        currentData = new PeriodicCallData(totalNumberOfCalls);
    }

    // ===== Recursive State Getters =====
    public float getPreviousAlpha1() { return previousAlpha1; }
    public void setPreviousAlpha1(float v) { this.previousAlpha1 = v; }

    public float getPreviousBeta1() { return previousBeta1; }
    public void setPreviousBeta1(float v) { this.previousBeta1 = v; }

    public float getPreviousBeta2() { return previousBeta2; }
    public void setPreviousBeta2(float v) { this.previousBeta2 = v; }

    public float getPreviousGamma1() { return previousGamma1; }
    public void setPreviousGamma1(float v) { this.previousGamma1 = v; }

    public float getPreviousGamma2() { return previousGamma2; }
    public void setPreviousGamma2(float v) { this.previousGamma2 = v; }

    public float getPreviousC1() { return previousC1; }
    public void setPreviousC1(float v) { this.previousC1 = v; }

    public void lock() {
        lock.lock();
    }

    public void unlock() {
        lock.unlock();
    }

    @Override
    public String toString() {
        return "PredictiveCampaignCallData{" +
                "currentData=" + currentData +
                ", previousData=" + previousData +
                '}';
    }

    public PredictiveCallParameters getPredictiveCallParameters() {
        return predictiveCallParameters;
    }

    public void setPredictiveCallParameters(PredictiveCallParameters predictiveCallParameters) {
        this.predictiveCallParameters = predictiveCallParameters;
    }
}