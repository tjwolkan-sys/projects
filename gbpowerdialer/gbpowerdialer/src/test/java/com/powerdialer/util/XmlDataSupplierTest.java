package com.powerdialer.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.util.Map;

import java.io.IOException;

import com.mysql.jdbc.NotImplemented;
import org.apache.commons.io.IOUtils;
import org.junit.Test;

import com.powerdialer.datamanipulation.supplier.XmlDataSupplier;

public class XmlDataSupplierTest {

  @Test
  public void testGetNextObject() throws Exception {
    String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + "<!DOCTYPE root>" + "<root>" + "<element>" + "<name>elephant</name>" + "</element>" + "</root>";
    XmlDataSupplier<Map<String, String>> xmlDataSupplier = new XmlDataSupplier(IOUtils.toInputStream(xml), Map.class);
    Map<String, String> nextObject = xmlDataSupplier.getNextObject();
    assertFalse(nextObject.isEmpty());
    assertTrue(nextObject.containsKey("name"));
    assertEquals("elephant", nextObject.get("name"));
    assertNull(xmlDataSupplier.getNextObject());
  }

  @Test
  public void testGetSequentialNextObject() throws Exception {
    String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + "<!DOCTYPE root>\n" + "<root>\n" + "\t<element>\n" + "\t\t<id>2</id>\n"
        + "\t\t<fullname>Sasha Vishnyak</fullname>\n" + "\t\t<phone_number_1>90979214088</phone_number_1>\n" + "\t</element>" + "<element>\n" + "\t\t<id>213</id>\n"
        + "\t\t<fullname>Test Name</fullname>\n" + "\t\t<phone_number_1>213213</phone_number_1>\n" + "\t</element>\n" + "</root>";
    XmlDataSupplier<Map<String, String>> xmlDataSupplier = new XmlDataSupplier(IOUtils.toInputStream(xml), Map.class);
    Map<String, String> nextObject = xmlDataSupplier.getNextObject();
    assertEquals("Sasha Vishnyak", nextObject.get("fullname"));
    nextObject = xmlDataSupplier.getNextObject();
    assertEquals("Test Name", nextObject.get("fullname"));
    assertNull(xmlDataSupplier.getNextObject());
  }

  @Test
  public void testGetSequentialNextString() throws IOException, NotImplemented {
    String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + "<!DOCTYPE root>" + "<root>" + "<element>elephant</element>" + "<element>row2</element>"
        + "<element>cow</element>" + "</root>";
    XmlDataSupplier<String> xmlDataSupplier = new XmlDataSupplier(IOUtils.toInputStream(xml), String.class);
    assertEquals("elephant", xmlDataSupplier.getNextObject());
    assertEquals("row2", xmlDataSupplier.getNextObject());
    assertEquals("cow", xmlDataSupplier.getNextObject());
    assertNull(xmlDataSupplier.getNextObject());
  }
}
