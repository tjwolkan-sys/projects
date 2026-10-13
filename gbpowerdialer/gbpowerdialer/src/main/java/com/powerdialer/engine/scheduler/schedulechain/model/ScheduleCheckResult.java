package com.powerdialer.engine.scheduler.schedulechain.model;

public class ScheduleCheckResult {
    private boolean mustBeStarted = false;
    private boolean mustBeStopped = false;
    private boolean mustBePaused = false;
    private boolean resetEnabled = false;
    public boolean isMustBeStarted() { return mustBeStarted; }
    public boolean isMustBeStopped() { return mustBeStopped; }
    public boolean isMustBePaused() { return mustBePaused; }
    public boolean isResetEnabled() { return resetEnabled; }

    public void setMustBeStarted(boolean mustBeStarted) { this.mustBeStarted = mustBeStarted; }
    public void setMustBeStopped(boolean mustBeStopped) { this.mustBeStopped = mustBeStopped; }
    public void setMustBePaused(boolean mustBePaused) { this.mustBePaused = mustBePaused; }
    public void setResetEnabled(boolean resetEnabled) { this.resetEnabled = resetEnabled; }

}
