package com.powerdialer.service;

import static org.junit.Assert.assertEquals;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.junit.Before;
import org.junit.Test;
import org.powermock.reflect.Whitebox;

/**
 * Test for LocaleService class
 */
public class LocaleServiceTest {

  LocaleService localeService = new LocaleService();

  @Before
  public void init() {
    Properties properties = new Properties();
    properties.put("testTemplate1", "This is a {test} tail test1");
    properties.put("testTemplate2", "This is a test without expression");
    Whitebox.setInternalState(localeService, "en", properties);
  }

  /**
   * test successful and correct execution
   */
  @Test
  public void testGetEvaluatingAttributesSuccessful() {
    Map<String, Object> attributes = new HashMap<String, Object>();
    attributes.put("test", "testValue");
    String evaluatingAttributes = localeService.getEvaluatingAttributes("testTemplate1", "en", attributes);
    assertEquals("This is a testValue tail test1", evaluatingAttributes);
  }

  /**
   * Test empty attributes
   */
  @Test
  public void testGetEvaluatingAttributesEmpty() {
    Map<String, Object> attributes = new HashMap<String, Object>();
    String evaluatingAttributes = localeService.getEvaluatingAttributes("testTemplate1", "en", attributes);
    assertEquals("This is a {test} tail test1", evaluatingAttributes);
  }

  /**
   * Test templates without any expression
   */
  @Test
  public void testGetEvaluatingAttributesWithoutExpression() {
    Map<String, Object> attributes = new HashMap<String, Object>();
    attributes.put("test", "testValue");
    String evaluatingAttributes = localeService.getEvaluatingAttributes("testTemplate2", "en", attributes);
    assertEquals("This is a test without expression", evaluatingAttributes);
  }

  /**
   * Test with null attributes object
   */
  @Test
  public void testGetEvaluatingAttributesNullAttributes() {
    String evaluatingAttributes = localeService.getEvaluatingAttributes("testTemplate2", "en", null);
    assertEquals("This is a test without expression", evaluatingAttributes);
  }
}
