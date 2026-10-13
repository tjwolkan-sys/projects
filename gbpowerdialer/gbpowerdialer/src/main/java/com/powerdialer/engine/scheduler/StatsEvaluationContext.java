package com.powerdialer.engine.scheduler;

import com.powerdialer.model.GroupStats;
import com.powerdialer.model.QueueStats;

public class StatsEvaluationContext {
  private final QueueStats queueStats;
  private final GroupStats groupStats;

  public StatsEvaluationContext(QueueStats queueStats, GroupStats groupStats) {
    this.queueStats = queueStats;
    this.groupStats = groupStats;
  }

  public QueueStats getQueueStats() {
    return queueStats;
  }

  public GroupStats getGroupStats() {
    return groupStats;
  }
}
