package com.powerdialer.validators;

import com.powerdialer.exceptions.DuplicatedNameException;
import com.powerdialer.model.Skill;

public class SkillValidator implements Validator<Skill> {

  @Override
  public ValidationResult validate(Skill t) throws DuplicatedNameException {
    return new ValidationResult(true, new String[0]);
  }

}
