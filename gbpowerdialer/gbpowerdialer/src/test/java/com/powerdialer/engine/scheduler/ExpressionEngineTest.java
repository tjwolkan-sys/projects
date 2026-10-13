package com.powerdialer.engine.scheduler;

import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.mockito.BDDMockito.given;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.powerdialer.model.QueueStats;
import com.powerdialer.util.LoggerDecorator;

@RunWith(MockitoJUnitRunner.class)
public class ExpressionEngineTest {

  private static final String EXPRESSION = "{{queueStats.handledCallsCount}} > 5";

  @InjectMocks
  private ExpressionEngine expressionEngine;

  @Mock
  private QueueStats queueStats;

  @Mock
  private StatsEvaluationContext context;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private LoggerDecorator loggerDecorator;

  @Before
  public void init() {
    given(context.getQueueStats()).willReturn(queueStats);
  }

  @Test
  public void givenExpressionWhenEvaluateThenReturnResult() {
    given(queueStats.getHandledCallsCount()).willReturn(10);

    EvaluationResultContext result = expressionEngine.evaluateBoolean(EXPRESSION, context);
    assertTrue(result.getResultBoolean());
  }

  @Test
  public void givenEmptyExpressionWhenEvaluateThenReturnContextWithFalseResult() {
    EvaluationResultContext result = expressionEngine.evaluateBoolean("", context);
    assertFalse(result.getResultBoolean());
  }

  @Test
  public void givenExpressionIsNullWhenEvaluateThenReturnContextWithFalseResult() {
    EvaluationResultContext result = expressionEngine.evaluateBoolean(null, context);
    assertFalse(result.getResultBoolean());
  }

  @Test
  public void givenEvaluationContextIsNullWhenEvaluateThenReturnContextWithFalseResult() {
    EvaluationResultContext result = expressionEngine.evaluateBoolean(EXPRESSION, null);
    assertFalse(result.getResultBoolean());
  }

  @Test
  public void givenExpressionWithPathToNonExistingPropertyWhenEvaluateThenReturnContextWithFalseResult() {
    EvaluationResultContext result = expressionEngine.evaluateBoolean("{{nonExistingProperty}}", context);
    assertFalse(result.getResultBoolean());
  }


  @Test
  public void givenEvaluationContextWithQueueStatsIsNullWhenEvaluateThenReturnContextWithFalseResult() {
    given(context.getQueueStats()).willReturn(null);
    EvaluationResultContext result = expressionEngine.evaluateBoolean(EXPRESSION, context);
    assertFalse(result.getResultBoolean());
  }

}
