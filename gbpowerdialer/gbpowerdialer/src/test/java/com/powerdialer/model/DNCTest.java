package com.powerdialer.model;

import static org.junit.Assert.assertFalse;
import java.util.Set;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;

import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Test for class DNC. Testing Hibernate Validator constraints
 */
public class DNCTest {

  private static Validator validator;

  @BeforeClass
  public static void setUp() {
    ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
  }

  /**
   * Test DNC name size constraint
   */
  @Test
  public void testLongName() {
    DNC dnc = new DNC();
    StringBuilder name = new StringBuilder();
    for (int i = 0; i < 256; i++) {
      name.append(i);
    }
    dnc.setName(name.toString());

    Set<ConstraintViolation<DNC>> validateResult = validator.validateProperty(dnc, "name");
    assertFalse(validateResult.isEmpty());
  }

  /**
   * Test DNC name blank constraint
   */
  @Test
  public void testBlankName() {
    DNC dnc = new DNC();
    dnc.setName("");
    Set<ConstraintViolation<DNC>> validateResult = validator.validateProperty(dnc, "name");
    assertFalse(validateResult.isEmpty());
  }
}
