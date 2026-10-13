package com.powerdialer.validators;

import com.powerdialer.exceptions.DuplicatedNameException;
import com.powerdialer.model.SortField;

public class SortFieldValidator implements Validator<SortField> {

  @Override
  public ValidationResult validate(SortField t) throws DuplicatedNameException {
    return new ValidationResult(true, new String[0]);
  }

}
