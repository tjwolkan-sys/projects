package com.powerdialer.validators;

import com.powerdialer.exceptions.DuplicatedNameException;
import com.powerdialer.model.SortFieldValue;

public class SortFieldValueValidator implements Validator<SortFieldValue> {

  @Override
  public ValidationResult validate(SortFieldValue t) throws DuplicatedNameException {
    return new ValidationResult(true, new String[0]);
  }

}
