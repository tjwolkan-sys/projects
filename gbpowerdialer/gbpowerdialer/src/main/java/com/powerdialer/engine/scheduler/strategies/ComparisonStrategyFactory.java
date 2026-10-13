package com.powerdialer.engine.scheduler.strategies;

import static com.powerdialer.util.Constants.COMPARISON_STRATEGY_TYPE;

import com.powerdialer.engine.scheduler.ExpressionEngine;
import com.powerdialer.engine.scheduler.StatsRestEngine;
import com.powerdialer.model.ScheduleAutomation;

public class ComparisonStrategyFactory {

  private final ExpressionEngine expressionEngine;
  private final StatsRestEngine restEngine;

  public ComparisonStrategyFactory(ExpressionEngine expressionEngine, StatsRestEngine restEngine) {
    this.expressionEngine = expressionEngine;
    this.restEngine = restEngine;
  }

  public ComparisonStrategy buildStrategy(ScheduleAutomation automation) {
    if (COMPARISON_STRATEGY_TYPE.AVERAGE_VALUE == automation.getComparisonType()) {
      return new AverageValueStrategy(expressionEngine, restEngine, createParams(automation));
    } else if (COMPARISON_STRATEGY_TYPE.ONE_BY_ONE == automation.getComparisonType()) {
      return new OneByOneStrategy(expressionEngine, restEngine, createParams(automation));
    }
    return new EmptyStrategy();
  }

  private StrategyParams createParams(ScheduleAutomation automation) {
    return  new StrategyParams(automation.getQueues(), automation.getGroups(), automation.getFormula());
  }
}
