package com.powerdialer.validators;

import java.util.Collection;

import com.powerdialer.exceptions.DuplicatedNameException;
import com.powerdialer.model.ScheduleAutomation;

public class ScheduleAutomationValidator implements Validator<ScheduleAutomation> {
  @Override
  public ValidationResult validate(Collection<ScheduleAutomation> schedules) {
    return new ValidationResult(true, new String[0]);
  }

  @Override
  public ValidationResult validate(ScheduleAutomation scheduleAutomation) throws DuplicatedNameException {
    return new ValidationResult(true, new String[0]);
  }
}
