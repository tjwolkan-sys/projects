package com.powerdialer.engine.scheduler.strategies;

import com.powerdialer.engine.scheduler.EvaluationResultContext;

public interface ComparisonStrategy {
  EvaluationResultContext apply();
}
