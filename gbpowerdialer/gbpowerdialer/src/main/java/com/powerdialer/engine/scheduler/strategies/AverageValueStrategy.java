package com.powerdialer.engine.scheduler.strategies;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.powerdialer.engine.scheduler.EvaluationResultContext;
import com.powerdialer.engine.scheduler.ExpressionEngine;
import com.powerdialer.engine.scheduler.StatsEvaluationContext;
import com.powerdialer.engine.scheduler.StatsRestEngine;
import com.powerdialer.model.Group;
import com.powerdialer.model.GroupStats;
import com.powerdialer.model.Queue;
import com.powerdialer.model.QueueStats;
import com.powerdialer.model.base.BaseEntityTenant;

/**
 * Strategy gets data only for one group/queue -> make comparison -> if false than take data for next group/queue -> etc
 */
@Deprecated
public class AverageValueStrategy implements ComparisonStrategy {

  private final ExpressionEngine expressionEngine;
  private final StatsRestEngine restEngine;
  private final List<Queue> queues;
  private final List<Group> groups;
  private final String expression;

  public AverageValueStrategy(ExpressionEngine expressionEngine, StatsRestEngine restEngine, StrategyParams params) {
    this.expressionEngine = expressionEngine;
    this.restEngine = restEngine;
    this.queues = params.getQueues();
    this.groups = params.getGroups();
    this.expression = params.getExpression();
  }

  @Override
  public EvaluationResultContext apply() {
    String queueIds = prepareIds(queues, q -> ((Queue) q).getId());
    String groupIds = prepareIds(groups, g -> ((Group) g).getId());
    QueueStats queueStats = queueIds.isEmpty() ? null : restEngine.prepareQueueStats(queueIds);
    GroupStats groupStats = groupIds.isEmpty() ? null : restEngine.prepareGroupStats(groupIds);
    return expressionEngine.evaluateBoolean(expression, new StatsEvaluationContext(queueStats, groupStats));
  }

  private String prepareIds(List<? extends BaseEntityTenant> data, Function<? super BaseEntityTenant, Long> function) {
    return data.stream()
        .map(function)
        .map(String::valueOf)
        .collect(Collectors.joining(","));
  }
}
