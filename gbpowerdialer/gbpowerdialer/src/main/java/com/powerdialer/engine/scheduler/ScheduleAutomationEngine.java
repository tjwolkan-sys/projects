package com.powerdialer.engine.scheduler;

import com.powerdialer.model.Campaign;
import com.powerdialer.model.Group;
import com.powerdialer.model.ScheduleAutomation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * Component responsible for operations with schedule automation
 */
@Component("scheduleAutomationEngine")
@Lazy(false)
public class ScheduleAutomationEngine {

  @Autowired
  private ExpressionEngine expressionEngine;

  @Autowired
  private AutomationGroupAgentStatsProvider agentStatsProvider;

  public EvaluationResultContext processScheduleAutomation(ScheduleAutomation automation) {
    if (automation == null) {
      return EvaluationResultUtils.falseBooleanResult();
    }

    Campaign campaign = automation.getCampaign();
    if (campaign == null) {
      return EvaluationResultUtils.falseBooleanResult();
    }

    String expression = automation.getFormula();
    if (expression == null || expression.isEmpty()) {
      return EvaluationResultUtils.falseBooleanResult();
    }

    List<Group> groups = automation.getGroups();
    if (groups == null || groups.isEmpty()) {
      return EvaluationResultUtils.falseBooleanResult();
    }

    for (Group group : groups) {
      if (group == null) continue;

      AgentStatsEvaluationContext ctx = agentStatsProvider.buildContext(campaign, group);
      EvaluationResultContext eval = expressionEngine.evaluateBoolean(expression, ctx);

      if (eval != null && eval.getResultBoolean()) {
        return eval;
      }
    }

    return EvaluationResultUtils.falseBooleanResult();
  }
}
