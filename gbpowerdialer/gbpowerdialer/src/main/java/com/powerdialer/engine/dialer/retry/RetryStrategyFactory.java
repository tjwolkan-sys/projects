package com.powerdialer.engine.dialer.retry;

import com.powerdialer.util.Constants.RETRY_STRATEGY;

/**
 * Factory to create retry strategy
 */
public class RetryStrategyFactory {

  public RetryStrategy createStrategy(RETRY_STRATEGY strategy) {

    if (strategy.equals(RETRY_STRATEGY.REACH_RETRY_LIMIT_FOR_SINGLE_NUMBER))
      return new ReachRetrySingleNumber();
    if (strategy.equals(RETRY_STRATEGY.REACH_RETRY_LIMIT_BEFORE_NEXT_NUMBER))
      return new ReachRetryPerNumber();
    if (strategy.equals(RETRY_STRATEGY.RETRY_RULES_MANAGES_CYCLE))
      return new RetryRulesCycle();
    if (strategy.equals(RETRY_STRATEGY.ROUND_ROBIN_DIAL_WITH_OVERALL_RETRY_LIMIT_SCHEDULE_NEXT_CYCLE))
      return new RoundRobinNextCycle();
    if (strategy.equals(RETRY_STRATEGY.ROUND_ROBIN_DIAL_WITH_OVERALL_RETRY_LIMIT_SCHEDULE_NEXT_NUMBER))
      return new RoundRobinNextNumber();
    if (strategy.equals(RETRY_STRATEGY.ROUND_ROBIN_DIAL_WITH_PER_CYCLE_RETRY_LIMIT_SCHEDULE_NEXT_NUMBER))
      return new RoundRobinPerCycle();

    return null;

  }
}
