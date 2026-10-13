package com.powerdialer.dto;

public class RingingMetricsDTO {
    private int totalRinging = 0;
    private int cbRinging = 0;
    private int retryRinging = 0;
    private int freshRinging = 0;
    private int pendingTotalRinging = 0;
    private long pendingMaxAgeMs = 0L;

    // --- Gamma Plus (MADDE 2B) yaş ağırlıklı ringing toplamları ---
    // Bayrak kapalıyken DOLDURULMAZ (0 kalır) ve okunmaz; mevcut sayaçlar tek satır değişmez.
    // w = GammaRingAgeWeight.weight(yaş). Sigma toplamı Poisson-binom varyansı için gerekir:
    //   Var = Σ p_i(1−p_i) = AR·Σw − AR²·Σw²
    private double cbRingingWeight = 0.0;
    private double retryRingingWeight = 0.0;
    private double freshRingingWeight = 0.0;
    private double cbRingingWeightSq = 0.0;
    private double retryRingingWeightSq = 0.0;
    private double freshRingingWeightSq = 0.0;

    public int getTotalRinging() {
        return totalRinging;
    }

    public void setTotalRinging(int totalRinging) {
        this.totalRinging = totalRinging;
    }

    public int getCbRinging() {
        return cbRinging;
    }

    public void setCbRinging(int cbRinging) {
        this.cbRinging = cbRinging;
    }

    public int getRetryRinging() {
        return retryRinging;
    }

    public void setRetryRinging(int retryRinging) {
        this.retryRinging = retryRinging;
    }

    public int getFreshRinging() {
        return freshRinging;
    }

    public void setFreshRinging(int freshRinging) {
        this.freshRinging = freshRinging;
    }

    public int getPendingTotalRinging() {
        return pendingTotalRinging;
    }

    public void setPendingTotalRinging(int pendingTotalRinging) {
        this.pendingTotalRinging = pendingTotalRinging;
    }

    public long getPendingMaxAgeMs() {
        return pendingMaxAgeMs;
    }

    public void setPendingMaxAgeMs(long pendingMaxAgeMs) {
        this.pendingMaxAgeMs = pendingMaxAgeMs;
    }

    public double getCbRingingWeight() {
        return cbRingingWeight;
    }

    public void setCbRingingWeight(double cbRingingWeight) {
        this.cbRingingWeight = cbRingingWeight;
    }

    public double getRetryRingingWeight() {
        return retryRingingWeight;
    }

    public void setRetryRingingWeight(double retryRingingWeight) {
        this.retryRingingWeight = retryRingingWeight;
    }

    public double getFreshRingingWeight() {
        return freshRingingWeight;
    }

    public void setFreshRingingWeight(double freshRingingWeight) {
        this.freshRingingWeight = freshRingingWeight;
    }

    public double getCbRingingWeightSq() {
        return cbRingingWeightSq;
    }

    public void setCbRingingWeightSq(double cbRingingWeightSq) {
        this.cbRingingWeightSq = cbRingingWeightSq;
    }

    public double getRetryRingingWeightSq() {
        return retryRingingWeightSq;
    }

    public void setRetryRingingWeightSq(double retryRingingWeightSq) {
        this.retryRingingWeightSq = retryRingingWeightSq;
    }

    public double getFreshRingingWeightSq() {
        return freshRingingWeightSq;
    }

    public void setFreshRingingWeightSq(double freshRingingWeightSq) {
        this.freshRingingWeightSq = freshRingingWeightSq;
    }

    @Override
    public String toString() {
        return "RingingMetricsDTO{" +
                "totalRinging=" + totalRinging +
                ", cbRinging=" + cbRinging +
                ", retryRinging=" + retryRinging +
                ", freshRinging=" + freshRinging +
                ", pendingTotalRinging=" + pendingTotalRinging +
                ", pendingMaxAgeMs=" + pendingMaxAgeMs +
                ", cbRingingWeight=" + cbRingingWeight +
                ", retryRingingWeight=" + retryRingingWeight +
                ", freshRingingWeight=" + freshRingingWeight +
                ", cbRingingWeightSq=" + cbRingingWeightSq +
                ", retryRingingWeightSq=" + retryRingingWeightSq +
                ", freshRingingWeightSq=" + freshRingingWeightSq +
                '}';
    }
}
