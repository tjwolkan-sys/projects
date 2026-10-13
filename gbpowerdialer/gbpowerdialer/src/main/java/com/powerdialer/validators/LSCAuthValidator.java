package com.powerdialer.validators;

import com.powerdialer.exceptions.DuplicatedNameException;
import com.powerdialer.model.LSCAuthorization;

public class LSCAuthValidator implements Validator<LSCAuthorization> {

  @Override
  public ValidationResult validate(LSCAuthorization t) throws DuplicatedNameException {
    return new ValidationResult(true, new String[0]);
  }

}
