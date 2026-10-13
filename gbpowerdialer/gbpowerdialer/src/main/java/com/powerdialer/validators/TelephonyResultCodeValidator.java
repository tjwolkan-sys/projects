package com.powerdialer.validators;

import com.powerdialer.exceptions.DuplicatedNameException;
import com.powerdialer.model.TelephonyResultCode;

public class TelephonyResultCodeValidator implements Validator<TelephonyResultCode> {

  @Override
  public ValidationResult validate(TelephonyResultCode t) throws DuplicatedNameException {
    return new ValidationResult(true, new String[0]);
  }
}
