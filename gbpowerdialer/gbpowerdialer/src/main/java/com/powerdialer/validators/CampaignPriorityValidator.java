package com.powerdialer.validators;

import com.powerdialer.exceptions.DuplicatedNameException;
import com.powerdialer.model.CampaignPriority;
import com.powerdialer.model.LSC;

public class CampaignPriorityValidator implements Validator<CampaignPriority> {

  @Override
  public ValidationResult validate(CampaignPriority t) throws DuplicatedNameException {
    return new ValidationResult(true, new String[0]);
  }

}
