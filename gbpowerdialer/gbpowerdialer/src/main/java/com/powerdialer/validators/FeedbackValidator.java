package com.powerdialer.validators;

import com.powerdialer.model.Feedback;

public class FeedbackValidator implements Validator<Feedback>{

  public ValidationResult validate(Feedback t) {
    return new ValidationResult(true, new String[0]);
  }

}
