package com.powerdialer.validators;

import com.powerdialer.exceptions.DuplicatedNameException;
import com.powerdialer.model.CampaignPriority;
import com.powerdialer.model.CampaignPriorityFilter;

public class CampaignPriorityFilterValidator implements Validator<CampaignPriorityFilter> {

  @Override
  public ValidationResult validate(CampaignPriorityFilter t) throws DuplicatedNameException {
    return new ValidationResult(true, new String[0]);
  }

}
