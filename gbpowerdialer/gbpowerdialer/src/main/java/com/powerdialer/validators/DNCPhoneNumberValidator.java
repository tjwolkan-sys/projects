package com.powerdialer.validators;

import com.powerdialer.exceptions.DuplicatedNameException;
import com.powerdialer.model.DNCPhoneNumber;

public class DNCPhoneNumberValidator implements Validator<DNCPhoneNumber> {
  
  @Override
  public ValidationResult validate(DNCPhoneNumber t) throws DuplicatedNameException {
    return new ValidationResult(true, new String[0]);
  }

}
