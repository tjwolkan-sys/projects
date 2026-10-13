package com.powerdialer.validators;

import com.powerdialer.model.AdminLog;

public class AdminLogValidator implements Validator<AdminLog> {

  @Override
  public ValidationResult validate(AdminLog t) {
    return new ValidationResult(true, new String[0]);
  }

}
