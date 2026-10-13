package com.powerdialer.validators;

import com.powerdialer.model.User;

public class UserValidator implements Validator<User> {

  public ValidationResult validate(User t) {
    return new ValidationResult(true, new String[0]);
  }

}
