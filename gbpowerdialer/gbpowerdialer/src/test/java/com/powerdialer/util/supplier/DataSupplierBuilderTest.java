package com.powerdialer.util.supplier;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.util.Map;

import java.io.IOException;

import org.junit.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import com.powerdialer.datamanipulation.supplier.DataSupplier;
import com.powerdialer.datamanipulation.supplier.DataSupplierBuilder;
import com.powerdialer.datamanipulation.supplier.JsonDataSupplier;
import com.powerdialer.datamanipulation.supplier.XmlDataSupplier;
import com.powerdialer.exceptions.UnknownFileExtensionException;

/**
 * Test for DataSupplierBuilder class
 */
public class DataSupplierBuilderTest {

  /**
   * Test build JsonDataSupplier
   */
  @Test
  public void testBuildJsonDataSupplier() throws Exception {
    MockMultipartFile mockMultipartFile = new MockMultipartFile("test.json", "test.json", MediaType.APPLICATION_JSON_VALUE, new byte[]{ 123 });
    DataSupplier<Map> dataSupplier = DataSupplierBuilder.<Map>getInstance().dataType(Map.class)
      .usingMultipartFileSource(mockMultipartFile)
      .sourceFirstRowColumnNames(true)
      .build();
    assertNotNull(dataSupplier);
    assertTrue(dataSupplier instanceof JsonDataSupplier);
  }

  /**
   * Test build JsonDataSupplier from empty file
   */
  @Test
  public void testBuildJsonDataSupplierFromEmptyFile() throws Exception {
    MockMultipartFile mockMultipartFile = new MockMultipartFile("test.json", "test.json", MediaType.APPLICATION_JSON_VALUE, new byte[]{});
    DataSupplier<Map> dataSupplier = DataSupplierBuilder.<Map>getInstance().dataType(Map.class)
      .usingMultipartFileSource(mockMultipartFile)
      .sourceFirstRowColumnNames(true)
      .build();
    assertNull(dataSupplier);
  }

  /**
   * Test fail
   */
  @Test(expected = UnknownFileExtensionException.class)
  public void testWrongFileExtension() throws IOException {
    MockMultipartFile mockMultipartFile = new MockMultipartFile("test.213", "test.213", MediaType.APPLICATION_JSON_VALUE, new byte[]{ 123 });
    DataSupplier<Map> dataSupplier = DataSupplierBuilder.<Map>getInstance().dataType(Map.class)
      .usingMultipartFileSource(mockMultipartFile)
      .sourceFirstRowColumnNames(true)
      .build();
  }

  /**
   * Test build XmlDataSupplier
   */
  @Test
  public void testBuildXmlDataSupplier() throws Exception {
    MockMultipartFile mockMultipartFile = new MockMultipartFile("test.xml", "test.xml", MediaType.APPLICATION_JSON_VALUE, "<root></root>"
      .getBytes());
    DataSupplier<Map> dataSupplier = DataSupplierBuilder.<Map>getInstance().dataType(Map.class)
      .usingMultipartFileSource(mockMultipartFile)
      .sourceFirstRowColumnNames(true)
      .build();
    assertNotNull(dataSupplier);
    assertTrue(dataSupplier instanceof XmlDataSupplier);
  }

}
