package com.powerdialer.validators;

import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.powerdialer.util.Constants;

public class MapValuesTypesValidatorTest {

  Validator<Map<String, String>> mapValuesTypesValidator;

  @Before
  public void init() {
    Map<String, Constants.DB_FIELD_TYPES> columnsToTypes = new HashMap<>();
    columnsToTypes.put("integer_column", Constants.DB_FIELD_TYPES.INT);
    columnsToTypes.put("decimal_column", Constants.DB_FIELD_TYPES.DECIMAL);
    mapValuesTypesValidator = new MapValuesTypesValidator(columnsToTypes);
  }

  @Test
  public void givenRowAndTypesWhenValidateThenValidationPassed() {
    Map<String, String> row = new HashMap<>();
    row.put("integer_column", "123");
    row.put("decimal_column", "123.456");
    ValidationResult validationResult = mapValuesTypesValidator.validate(row);
    Assert.assertTrue(validationResult.getResult());
    Assert.assertEquals(0, validationResult.getMessages().length);
  }

  @Test
  public void givenRowAndTypesWhenValidateThenIntegerValidationFailed() {
    Map<String, String> row = new HashMap<>();
    row.put("integer_column", "123.123");
    row.put("decimal_column", "123.456");
    ValidationResult validationResult = mapValuesTypesValidator.validate(row);
    Assert.assertFalse(validationResult.getResult());
    Assert.assertEquals(1, validationResult.getMessages().length);
  }

  @Test
  public void givenRowAndTypesWhenValidateThenDecimalValidationFailed() {
    Map<String, String> row = new HashMap<>();
    row.put("integer_column", "123");
    row.put("decimal_column", "123. 456");
    ValidationResult validationResult = mapValuesTypesValidator.validate(row);
    Assert.assertFalse(validationResult.getResult());
    Assert.assertEquals(1, validationResult.getMessages().length);
  }

}