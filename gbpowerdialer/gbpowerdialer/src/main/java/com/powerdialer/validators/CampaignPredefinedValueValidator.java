package com.powerdialer.validators;

import com.powerdialer.exceptions.DuplicatedNameException;
import com.powerdialer.model.PredefinedValue;

public class CampaignPredefinedValueValidator implements Validator<PredefinedValue> {
  public ValidationResult validate(PredefinedValue t) throws DuplicatedNameException {
    return new ValidationResult(true, new String[0]);
  }
}
