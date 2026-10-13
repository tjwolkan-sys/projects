package com.powerdialer.engine.scheduler;

import java.math.BigDecimal;

public class EvaluationResultUtils {

  private static final EvaluationResultContext falseBooleanResult  =  new EvaluationResultContext(new BigDecimal(0), null);

  private EvaluationResultUtils() {

  }

  public static EvaluationResultContext falseBooleanResult(){
    return falseBooleanResult;
  }
}
