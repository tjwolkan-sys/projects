package com.powerdialer.validators;

import com.powerdialer.exceptions.DuplicatedNameException;
import com.powerdialer.model.Filter;

public class FilterValidator implements Validator<Filter> {

  @Override
  public ValidationResult validate(Filter t) throws DuplicatedNameException {
    return new ValidationResult(true, new String[0]);
  }

}
