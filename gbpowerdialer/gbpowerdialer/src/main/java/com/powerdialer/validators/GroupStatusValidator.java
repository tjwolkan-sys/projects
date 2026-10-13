package com.powerdialer.validators;

import com.powerdialer.model.GroupStatus;

public class GroupStatusValidator implements Validator<GroupStatus> {

  public ValidationResult validate(GroupStatus t) {
    return new ValidationResult(true, new String[0]);
  }
}
