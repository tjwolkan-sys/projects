package com.powerdialer.validators;

import com.powerdialer.exceptions.DuplicatedNameException;
import com.powerdialer.model.ResultCodeScheduleAdvanced;

public class ResultCodeScheduleAdvancedValidator implements Validator<ResultCodeScheduleAdvanced> {

  @Override
  public ValidationResult validate(ResultCodeScheduleAdvanced t) throws DuplicatedNameException {
    return new ValidationResult(true, new String[0]);
  }

}
