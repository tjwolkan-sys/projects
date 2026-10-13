package com.powerdialer.validators;

import java.util.Collection;

/**
 * Generic validator
 * @author Pasha Omelchenko
 * @param <T>
 */
public interface Validator<T> {

  /**
   * Validate
   * @param t entity to validate
   * @return validation result
   */
  ValidationResult validate(T t) throws RuntimeException;

  /**
   * @param <T> the type of elements in this collection
   * @param t collection to validate
   * @return validation result
   * @author Taras Gerasymchuk
   */
  default ValidationResult validate(Collection<T> t) {
    return new ValidationResult(true, new String[] {});
  }

}
