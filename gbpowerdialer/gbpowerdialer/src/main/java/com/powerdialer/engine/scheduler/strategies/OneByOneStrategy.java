package com.powerdialer.engine.scheduler.strategies;

import java.util.List;
import java.util.Objects;

import com.powerdialer.engine.scheduler.EvaluationResultContext;
import com.powerdialer.engine.scheduler.EvaluationResultUtils;
import com.powerdialer.engine.scheduler.ExpressionEngine;
import com.powerdialer.engine.scheduler.StatsEvaluationContext;
import com.powerdialer.engine.scheduler.StatsRestEngine;
import com.powerdialer.model.Group;
import com.powerdialer.model.Queue;

/**
 * Strategy gets all data from all queues or groups and make calculations
 */
public class OneByOneStrategy implements ComparisonStrategy {

  private final ExpressionEngine expressionEngine;
  private final StatsRestEngine restEngine;
  private final List<Queue> queues;
  private final List<Group> groups;
  private final String expression;
  private final EvaluationResultContext falseBooleanResult = EvaluationResultUtils.falseBooleanResult();

  OneByOneStrategy(ExpressionEngine expressionEngine, StatsRestEngine restEngine, StrategyParams params) {
    this.expressionEngine = expressionEngine;
    this.restEngine = restEngine;
    this.queues = params.getQueues();
    this.groups = params.getGroups();
    this.expression = params.getExpression();
  }

  @Override
  public EvaluationResultContext apply() {
    if (!queues.isEmpty()) {
      return queues.stream()
          .map(queue -> restEngine.prepareQueueStats(queue.getId().toString()))
          .filter(Objects::nonNull)
          .map(queueStats -> expressionEngine.evaluateBoolean(expression, new StatsEvaluationContext(queueStats, null)))
          .findFirst().orElse(falseBooleanResult);
    } else if (!groups.isEmpty()) {
      return groups.stream()
          .map(group -> restEngine.prepareGroupStats(group.getId().toString()))
          .filter(Objects::nonNull)
          .map(groupStats -> expressionEngine.evaluateBoolean(expression, new StatsEvaluationContext(null, groupStats)))
          .findFirst().orElse(falseBooleanResult);
    }
    return falseBooleanResult;
  }

}
