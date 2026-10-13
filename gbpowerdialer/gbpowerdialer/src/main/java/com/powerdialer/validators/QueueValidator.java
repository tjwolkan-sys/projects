package com.powerdialer.validators;

import com.powerdialer.exceptions.DuplicatedNameException;
import com.powerdialer.model.Queue;

public class QueueValidator implements Validator<Queue> {
  @Override
  public ValidationResult validate(Queue queue) throws DuplicatedNameException {
    return new ValidationResult(true, new String[0]);
  }
}
