package com.powerdialer.engine.scheduler.strategies;

import com.powerdialer.engine.scheduler.EvaluationResultContext;
import com.powerdialer.engine.scheduler.EvaluationResultUtils;

/**
 * Strategy to cover unsupported type cases. No data calculation. Always return false;
 */
public class EmptyStrategy implements ComparisonStrategy {
  @Override
  public EvaluationResultContext apply() {
    return EvaluationResultUtils.falseBooleanResult();
  }
}
