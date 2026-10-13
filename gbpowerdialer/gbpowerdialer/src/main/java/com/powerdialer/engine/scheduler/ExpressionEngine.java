package com.powerdialer.engine.scheduler;

import static com.powerdialer.util.Constants.DOUBLE_BRACES_PATTERN;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;

import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;

import com.udojava.evalex.Expression;
import org.apache.commons.beanutils.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.powerdialer.util.LoggerDecorator;

/**
 * Component responsible for evaluating expressions
 */
@Component
public class ExpressionEngine {

  private final LoggerDecorator LOG;

  @Autowired
  public ExpressionEngine(LoggerDecorator loggerDecorator) {
    this.LOG = loggerDecorator.getLogger(ExpressionEngine.class);
  }

  public EvaluationResultContext evaluateBoolean(String expression, Object evaluationContext) {
    if (Objects.isNull(expression) || expression.isEmpty()) {
      LOG.error("Evaluated expression  must not be null or empty for object: " + evaluationContext);
      return EvaluationResultUtils.falseBooleanResult();
    }
    if (Objects.isNull(evaluationContext)) {
      LOG.error("Evaluation context must not be null");
      return EvaluationResultUtils.falseBooleanResult();
    }

    return evaluate(expression, evaluationContext);
  }

  /**
   * Evaluate expression applying it to object. Expression might contain any mathematical operations.
   * @param expression
   * @param evaluationContext
   * @return   evaluate result {@link BigDecimal}
   * @see <a href="https://github.com/uklimaschewski/EvalEx">
   * All available opertions in expression
   * </a>
   */
  private EvaluationResultContext evaluate(String expression, Object evaluationContext) {
    String[] calculations = extractCalculations(expression);
    Map<String, Double> calculationToEvaluatedValue;
    try {
      calculationToEvaluatedValue = evaluateNumericCalculations(calculations, evaluationContext);
    } catch (Exception e) {
      return handleCalculationError(e);
    }
    String preparedExpression = replaceAllCalculationInExpression(expression,  calculationToEvaluatedValue);
    try {
      BigDecimal result = new Expression(preparedExpression).eval();
      LOG.info("Evaluated expression: " + expression + " for object: " + evaluationContext + " equals to " + result);
      String expressionForLog = result.intValue() == 0 ? preparedExpression : annotateExpressionForLog(expression,  calculationToEvaluatedValue);
      return new EvaluationResultContext(result, expressionForLog);
    } catch (Exception e) {
      LOG.error("Failed to evaluate expression " + preparedExpression);
      return new EvaluationResultContext(new BigDecimal(0),
          ": invalid prepared expression" + preparedExpression);
    }
  }

  private EvaluationResultContext handleCalculationError(Exception e) {
    EvaluationResultContext resultContext = new EvaluationResultContext(new BigDecimal(0),
       null);
    resultContext.setErrorLogMessage(e.getMessage());
    return resultContext;
  }

  private String[] extractCalculations(String expression) {
    Set<String> calculations = new HashSet<>();
    Matcher matcher = DOUBLE_BRACES_PATTERN.matcher(expression);
    while (matcher.find()) {
      String calculation = matcher.group(0);
      calculations.add(calculation);
    }
    return calculations.toArray(new String[]{});
  }

  /**
   * Create map where key is calculation and value is evaluated expression value
   * @param calculations
   * @param context
   * @throws UnsupportedOperationException if any of calculation evaluated to non numeric value
   */
  private Map<String, Double> evaluateNumericCalculations(String[] calculations, Object context) throws UnsupportedOperationException {
    Map<String, Double> expressionToEvaluatedResult = new HashMap<>(calculations.length);
    for (String calculation : calculations) {
      String propertyPath = calculation.substring(2, calculation.length() - 2);
      try {
        String property = BeanUtils.getProperty(context, propertyPath);
        if (property != null)
          expressionToEvaluatedResult.put(calculation, Double.parseDouble(property));
        else
          expressionToEvaluatedResult.put(calculation, 0D);
      } catch (NumberFormatException e) {
        handleException("Property: " + propertyPath + " of object: " + context + " can't be " +
            "parsed as number", e);
      } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException | IllegalArgumentException e) {
        handleException("Error during evaluating calculation property: " + calculation + ". " +  e.getMessage(), e);
      }
    }
    return expressionToEvaluatedResult;
  }

  private String replaceAllCalculationInExpression(String expression, Map<String, Double> calculationToEvaluatedValue) {
    for (Map.Entry<String, Double> entry : calculationToEvaluatedValue.entrySet()) {
      String calculation = entry.getKey();
      Double replacement = entry.getValue();
      expression = expression.replace(calculation, replacement.toString());
    }
    return expression;
  }

  private void handleException(String message, Exception e) throws UnsupportedOperationException {
    LOG.error(message, e);
    throw new UnsupportedOperationException(message);
  }

  private String annotateExpressionForLog(String expression, Map<String, Double> calculationToEvaluatedValue) {
    for (Map.Entry<String, Double> entry : calculationToEvaluatedValue.entrySet()) {
      String calculation = entry.getKey();
      Double replacement = entry.getValue();
      expression = expression.replace(calculation, addAnnotationToReplacement(calculation, replacement));
    }
    return expression;
  }

  private String addAnnotationToReplacement(String calculation, Double replacement) {
    String preparedCalculation = calculation.replaceAll("\\{+", "").replaceAll("}+","");
    return replacement.toString() + "(*" + preparedCalculation +")";
  }

}
