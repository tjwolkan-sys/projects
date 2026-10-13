package com.powerdialer.validators;

import com.powerdialer.exceptions.DuplicatedNameException;
import com.powerdialer.model.Number;

public class NumberValidator implements Validator<Number> {

  @Override
  public ValidationResult validate(Number t) throws DuplicatedNameException {
    return new ValidationResult(true, new String[0]);
  }

}
