package com.powerdialer.validators;

import com.powerdialer.exceptions.DuplicatedNameException;
import com.powerdialer.model.AttachedData;

public class AttachedDataValidator implements Validator<AttachedData> {

  @Override
  public ValidationResult validate(AttachedData t) throws DuplicatedNameException {
    return new ValidationResult(true, new String[0]);
  }

}
