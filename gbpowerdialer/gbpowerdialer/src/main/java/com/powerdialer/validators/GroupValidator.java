package com.powerdialer.validators;

import com.powerdialer.exceptions.DuplicatedNameException;
import com.powerdialer.model.Group;

public class GroupValidator implements Validator<Group> {

  @Override
  public ValidationResult validate(Group t) throws DuplicatedNameException {
    return new ValidationResult(true, new String[0]);
  }

}
