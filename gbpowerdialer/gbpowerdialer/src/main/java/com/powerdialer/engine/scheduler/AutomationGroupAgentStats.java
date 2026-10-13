package com.powerdialer.engine.scheduler;

public class AutomationGroupAgentStats {

    private final int availableAgentsCount;
    private final int loginAgentsCount;

    public AutomationGroupAgentStats(int availableAgentsCount, int loginAgentsCount) {
        this.availableAgentsCount = availableAgentsCount;
        this.loginAgentsCount = loginAgentsCount;
    }

    public int getAvailableAgentsCount() {
        return availableAgentsCount;
    }

    public int getLoginAgentsCount() {
        return loginAgentsCount;
    }

    @Override
    public String toString() {
        return "AutomationGroupAgentStats{" +
                "availableAgentsCount=" + availableAgentsCount +
                ", loginAgentsCount=" + loginAgentsCount +
                '}';
    }
}
