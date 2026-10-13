package com.powerdialer.engine.scheduler;

import java.math.BigDecimal;

public class EvaluationResultContext {

  private final BigDecimal evaluationResult;
  private final String expressionForLog;
  private String errorLogMessage;

  public EvaluationResultContext(BigDecimal evaluationResult, String expressionForLog) {
    this.evaluationResult = evaluationResult;
    this.expressionForLog = expressionForLog;
  }

  public boolean getResultBoolean() {
    return evaluationResult.intValue() == 1;
  }

  public String getExpressionForLog() {
    return expressionForLog;
  }

  public String getErrorLogMessage() {
    return errorLogMessage;
  }

  public void setErrorLogMessage(String errorLogMessage) {
    this.errorLogMessage = errorLogMessage;
  }
}
