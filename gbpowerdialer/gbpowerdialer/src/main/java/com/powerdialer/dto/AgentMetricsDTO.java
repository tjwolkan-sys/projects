package com.powerdialer.dto;

public class AgentMetricsDTO {
    private final double physicalTotalAgents;
    private final double totalAgents;
    private final double incallAgents;
    private final double availAgents;
    private final double acwAgents;

    public AgentMetricsDTO(double physicalTotalAgents, double totalAgents, double incallAgents, double availAgents, double acwAgents) {
        this.physicalTotalAgents = physicalTotalAgents;
        this.totalAgents = totalAgents;
        this.incallAgents = incallAgents;
        this.availAgents = availAgents;
        this.acwAgents = acwAgents;
    }

    public AgentMetricsDTO(double totalAgents, double incallAgents, double availAgents, double acwAgents) {
        this(totalAgents, totalAgents, incallAgents, availAgents, acwAgents);
    }

    public static AgentMetricsDTO zero() {
        return new AgentMetricsDTO(0.0, 0.0, 0.0, 0.0, 0.0);
    }

    public double getPhysicalTotalAgents() {
        return physicalTotalAgents;
    }

    public double getTotalAgents() {
        return totalAgents;
    }

    public double getIncallAgents() {
        return incallAgents;
    }

    public double getAvailAgents() {
        return availAgents;
    }

    public double getAcwAgents() {
        return acwAgents;
    }

    @Override
    public String toString() {
        return "AgentMetricsDTO{" +
                "physicalTotalAgents=" + physicalTotalAgents +
                ", totalAgents=" + totalAgents +
                ", incallAgents=" + incallAgents +
                ", availAgents=" + availAgents +
                ", acwAgents=" + acwAgents +
                '}';
    }
}
