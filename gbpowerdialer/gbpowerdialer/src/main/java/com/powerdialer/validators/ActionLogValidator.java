package com.powerdialer.validators;

import com.powerdialer.exceptions.DuplicatedNameException;
import com.powerdialer.model.ActionLog;

public class ActionLogValidator implements Validator<ActionLog> {

  public ValidationResult validate(ActionLog t) throws DuplicatedNameException {
    return new ValidationResult(true, new String[0]);
  }

}
