package com.powerdialer.validators;

import com.powerdialer.model.AdminLogDetails;

public class AdminLogDetailsValidator implements Validator<AdminLogDetails> {
  
  @Override
  public ValidationResult validate(AdminLogDetails t) {
    return new ValidationResult(true, new String[0]);
  }

}
