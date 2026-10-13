package com.powerdialer.util.supplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.util.Map;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParseException;
import org.apache.commons.io.IOUtils;
import org.junit.Test;

import com.powerdialer.datamanipulation.supplier.JsonDataSupplier;

/**
 * Test for JsonDataSupplier class
 */
public class JsonDataSupplierTest {

  /**
   * Test successful read from {@link JsonDataSupplier}
   */
  @Test
  public void testGetNextObject() throws Exception {
    String json = "[{\"testKey1\": \"1\"}]";
    JsonDataSupplier<Map<String, String>> jsonDataSupplier = new JsonDataSupplier(IOUtils.toInputStream(json), Map.class);
    Map<String, String> nextObject = jsonDataSupplier.getNextObject();
    assertFalse(nextObject.isEmpty());
    assertTrue(nextObject.containsKey("testKey1"));
    assertEquals("1", nextObject.get("testKey1"));
    assertNull(jsonDataSupplier.getNextObject());
  }

  /**
   * Test sequential successful read from {@link JsonDataSupplier}
   */
  @Test
  public void testSequentialNextObject() throws Exception {
    String json = "[{\"testKey1\": \"1\"}, {\"testKey2\": \"2\"}]";
    JsonDataSupplier<Map<String, String>> jsonDataSupplier = new JsonDataSupplier(IOUtils.toInputStream(json), Map.class);
    Map<String, String> nextObject = jsonDataSupplier.getNextObject();
    assertTrue(nextObject.containsKey("testKey1"));
    nextObject = jsonDataSupplier.getNextObject();
    assertTrue(nextObject.containsKey("testKey2"));
    assertEquals("2", nextObject.get("testKey2"));
    assertNull(jsonDataSupplier.getNextObject());
  }

  @Test
  public void testSequentialStringNextObject() throws Exception {
    String json = "[\"str1\", \"str2\"]";
    JsonDataSupplier<String> jsonDataSupplier = new JsonDataSupplier<>(IOUtils.toInputStream(json), String.class);
    String str = jsonDataSupplier.getNextObject();
    assertEquals("str1", str);
    str = jsonDataSupplier.getNextObject();
    assertEquals("str2", str);
    assertNull(jsonDataSupplier.getNextObject());
  }

  @Test(expected = JsonParseException.class)
  public void testWrongJsonFormat() throws IOException {
    String json = "[{\"testKey1\": \"1\"]";
    JsonDataSupplier<Map<String, String>> jsonDataSupplier = new JsonDataSupplier(IOUtils.toInputStream(json), Map.class);
    jsonDataSupplier.getNextObject();
  }

  @Test(timeout = 1000L)
  public void givenSingleObjectWhenGetNextObjectsThenReturnObjectThenReturnNull() throws Exception {
    String json = "{\"testKey1\": \"1\"}";
    JsonDataSupplier<Map<String, String>> jsonDataSupplier = new JsonDataSupplier(IOUtils.toInputStream(json), Map.class);
    Map<String, String> nextObject = jsonDataSupplier.getNextObject();
    assertEquals("1", nextObject.get("testKey1"));
    assertNull(jsonDataSupplier.getNextObject());
  }

  @Test(timeout = 1000L)
  public void givenEmptyStringWhenGetNextObjectThenReturnNull() throws Exception {
    String json = "";
    JsonDataSupplier<Map<String, String>> jsonDataSupplier = new JsonDataSupplier(IOUtils.toInputStream(json), Map.class);
    assertNull(jsonDataSupplier.getNextObject());
  }

}
