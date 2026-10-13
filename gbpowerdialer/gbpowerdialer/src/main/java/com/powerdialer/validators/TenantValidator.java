package com.powerdialer.validators;

import com.powerdialer.model.Tenant;

public class TenantValidator  implements Validator<Tenant> {

  public ValidationResult validate(Tenant t) {
    return new ValidationResult(true, new String[0]);
  }
}
