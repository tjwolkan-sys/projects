package com.powerdialer.engine.predictive.beta.model;

public class PredictiveCallParameters {
    private float previousAlpha1;
    private float previousBeta1;
    private float previousBeta2;
    private float previousGamma1;
    private float previousGamma2;
    private float previousC1;

    private float previousWI;
    private float previousRW;

    private float currentAlpha1;
    private float currentBeta1;
    private float currentBeta2;
    private float currentGamma1;
    private float currentGamma2;
    private float currentC1;

    private float xn;

    public float getPreviousAlpha1() {
        return previousAlpha1;
    }
    public void setPreviousAlpha1(float previousAlpha1) {
        this.previousAlpha1 = previousAlpha1;
    }

    public float getPreviousBeta1() { return previousBeta1; }
    public void setPreviousBeta1(float previousBeta1) {
        this.previousBeta1 = previousBeta1;
    }

    public float getPreviousBeta2() {
        return previousBeta2;
    }
    public void setPreviousBeta2(float previousBeta2) {
        this.previousBeta2 = previousBeta2;
    }

    public float getPreviousGamma1() {
        return previousGamma1;
    }
    public void setPreviousGamma1(float previousGamma1) {
        this.previousGamma1 = previousGamma1;
    }

    public float getPreviousGamma2() { return previousGamma2; }
    public void setPreviousGamma2(float previousGamma2) {
        this.previousGamma2 = previousGamma2;
    }

    public float getPreviousC1() {
        return previousC1;
    }
    public void setPreviousC1(float previousC1) {
        this.previousC1 = previousC1;
    }


    public float getCurrentAlpha1() {
        return currentAlpha1;
    }
    public void setCurrentAlpha1(float currentAlpha1) {
        this.currentAlpha1 = currentAlpha1;
    }

    public float getCurrentBeta1() {
        return currentBeta1;
    }
    public void setCurrentBeta1(float currentBeta1) {
        this.currentBeta1 = currentBeta1;
    }

    public float getCurrentBeta2() {
        return currentBeta2;
    }
    public void setCurrentBeta2(float currentBeta2) {
        this.currentBeta2 = currentBeta2;
    }

    public float getCurrentGamma1() {
        return currentGamma1;
    }
    public void setCurrentGamma1(float currentGamma1) {
        this.currentGamma1 = currentGamma1;
    }

    public float getCurrentGamma2() {
        return currentGamma2;
    }
    public void setCurrentGamma2(float currentGamma2) {
        this.currentGamma2 = currentGamma2;
    }

    public float getCurrentC1() {
        return currentC1;
    }
    public void setCurrentC1(float currentC1) {
        this.currentC1 = currentC1;
    }


    public float getPreviousWI() { return previousWI; }
    public void setPreviousWI(float previousWI) { this.previousWI = previousWI; }

    public float getPreviousRW() { return previousRW; }
    public void setPreviousRW(float previousRW) { this.previousRW = previousRW; }


    public float getXn() { return xn; }
    public void setXn(float xn) {
        this.xn = xn;
    }

    @Override
    public String toString() {
        return "PredictiveCallParameters{" +
                "previousAlpha1=" + previousAlpha1 +
                ", previousBeta1=" + previousBeta1 +
                ", previousBeta2=" + previousBeta2 +
                ", previousGamma1=" + previousGamma1 +
                ", previousGamma2=" + previousGamma2 +
                ", previousC1=" + previousC1 +
                ", previousWI=" + previousWI +
                ", previousRW=" + previousRW +
                ", currentAlpha1=" + currentAlpha1 +
                ", currentBeta1=" + currentBeta1 +
                ", currentBeta2=" + currentBeta2 +
                ", currentGamma1=" + currentGamma1 +
                ", currentGamma2=" + currentGamma2 +
                ", currentC1=" + currentC1 +
                ", xn=" + xn +
                '}';
    }
}

