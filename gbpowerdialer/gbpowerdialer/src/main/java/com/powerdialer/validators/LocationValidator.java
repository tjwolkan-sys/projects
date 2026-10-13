package com.powerdialer.validators;

import com.powerdialer.exceptions.DuplicatedNameException;
import com.powerdialer.model.Location;

public class LocationValidator implements Validator<Location> {

  @Override
  public ValidationResult validate(Location t) throws DuplicatedNameException {
    return new ValidationResult(true, new String[0]);
  }

}
