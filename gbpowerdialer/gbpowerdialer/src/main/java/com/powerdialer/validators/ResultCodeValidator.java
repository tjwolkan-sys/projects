package com.powerdialer.validators;

import com.powerdialer.exceptions.DuplicatedNameException;
import com.powerdialer.model.ResultCode;

public class ResultCodeValidator implements Validator<ResultCode> {

  @Override
  public ValidationResult validate(ResultCode t) throws DuplicatedNameException {
    return new ValidationResult(true, new String[0]);
  }

}
