package com.powerdialer.validators;

import com.powerdialer.exceptions.DuplicatedNameException;
import com.powerdialer.model.PhoneNumberColumn;

public class PhoneNumberColumnValidator implements Validator<PhoneNumberColumn> {

  @Override
  public ValidationResult validate(PhoneNumberColumn t) throws DuplicatedNameException {
    return new ValidationResult(true, new String[0]);
  }

}
