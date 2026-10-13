package com.powerdialer.validators;

import com.powerdialer.exceptions.DuplicatedNameException;
import com.powerdialer.model.IVRApplication;

public class IVRApplicationValidator implements Validator<IVRApplication> {

  @Override
  public ValidationResult validate(IVRApplication t) throws DuplicatedNameException {
    return new ValidationResult(true, new String[0]);
  }

}
