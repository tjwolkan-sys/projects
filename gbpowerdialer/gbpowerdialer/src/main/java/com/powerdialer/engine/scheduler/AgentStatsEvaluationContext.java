package com.powerdialer.engine.scheduler;

public class AgentStatsEvaluationContext {

    private final long groupId;
    private final String groupName;
    private final int loginCount;
    private final int availableCount;

    public AgentStatsEvaluationContext(long groupId, String groupName, int loginCount, int availableCount) {
        this.groupId = groupId;
        this.groupName = groupName;
        this.loginCount = loginCount;
        this.availableCount = availableCount;
    }

    public int getLoginCount() {
        return loginCount;
    }

    public int getAvailableCount() {
        return availableCount;
    }

    public int getLoginAgentsCount() {
        return loginCount;
    }

    public int getAvailableAgentsCount() {
        return availableCount;
    }

    public long getGroupId() {
        return groupId;
    }

    public String getGroupName() {
        return groupName;
    }

    @Override
    public String toString() {
        return "AgentStatsEvaluationContext{groupId=" + groupId
                + ", groupName='" + groupName + '\''
                + ", loginCount=" + loginCount
                + ", availableCount=" + availableCount
                + '}';
    }
}
