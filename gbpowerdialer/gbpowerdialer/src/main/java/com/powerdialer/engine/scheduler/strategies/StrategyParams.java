package com.powerdialer.engine.scheduler.strategies;

import java.util.List;

import com.powerdialer.model.Group;
import com.powerdialer.model.Queue;

public class StrategyParams {
  private final List<Queue> queues;
  private final List<Group> groups;
  private final String expression;

  public StrategyParams(List<Queue> queues, List<Group> groups, String expression) {
    this.queues = queues;
    this.groups = groups;
    this.expression = expression;
  }

  public List<Queue> getQueues() {
    return queues;
  }

  public List<Group> getGroups() {
    return groups;
  }

  public String getExpression() {
    return expression;
  }
}
