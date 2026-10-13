package com.powerdialer.validators;

import com.powerdialer.exceptions.DuplicatedNameException;
import com.powerdialer.model.CampaignGroup;

public class CampaignGroupValidator implements Validator<CampaignGroup> {

  @Override
  public ValidationResult validate(CampaignGroup t) throws DuplicatedNameException {
    return new ValidationResult(true, new String[0]);
  }

}
