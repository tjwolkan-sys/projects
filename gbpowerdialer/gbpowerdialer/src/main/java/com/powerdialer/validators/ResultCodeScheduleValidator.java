package com.powerdialer.validators;

import com.powerdialer.exceptions.DuplicatedNameException;
import com.powerdialer.model.ResultCodeSchedule;

public class ResultCodeScheduleValidator implements Validator<ResultCodeSchedule> {

  @Override
  public ValidationResult validate(ResultCodeSchedule t) throws DuplicatedNameException {
    return new ValidationResult(true, new String[0]);
  }

}
