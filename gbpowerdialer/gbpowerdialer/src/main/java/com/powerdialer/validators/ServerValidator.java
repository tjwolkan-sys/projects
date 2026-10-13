package com.powerdialer.validators;

import com.powerdialer.exceptions.DuplicatedNameException;
import com.powerdialer.model.Server;

public class ServerValidator implements Validator<Server> {

  @Override
  public ValidationResult validate(Server t) throws DuplicatedNameException {
    return new ValidationResult(true, new String[0]);
  }

}
