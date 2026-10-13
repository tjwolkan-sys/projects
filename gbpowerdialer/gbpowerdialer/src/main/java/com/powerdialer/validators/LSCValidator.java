package com.powerdialer.validators;

import com.powerdialer.exceptions.DuplicatedNameException;
import com.powerdialer.model.LSC;

public class LSCValidator implements Validator<LSC> {

  @Override
  public ValidationResult validate(LSC t) throws DuplicatedNameException {
    return new ValidationResult(true, new String[0]);
  }

}
