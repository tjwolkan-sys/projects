package com.powerdialer.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.util.Date;

import java.text.SimpleDateFormat;

import org.junit.Assert;
import org.junit.Test;

/**
 * Test for Utils class
 */
public class UtilsTest {

  /**
   * Test two way conversion
   */
  @Test
  public void convertDoubleToLongDateTest() throws Exception {
    Date expected = new SimpleDateFormat(Constants.DATA_FORMAT_WITH_DATE).parse("2016-10-21 11:11:11.000");
    long actual = Utils.convertDoubleToLongDate(Utils.convertDateToDouble(expected));
    Assert.assertEquals(expected.getTime(), actual);
  }

  @Test
  public void convertDateToDoubleSameDayTest() throws Exception {
    SimpleDateFormat dateFormat = new SimpleDateFormat(Constants.DATA_FORMAT_WITH_DATE);
    Date date1 = dateFormat.parse("2022-10-25 00:00:00.000");
    Date date2 = dateFormat.parse("2022-10-25 23:59:59.000");
    double double1 = Utils.convertDateToDouble(date1);
    double double2 = Utils.convertDateToDouble(date2);

    assertEquals(44857, Math.floor(double1), 0.0);
    assertEquals(44857, Math.floor(double2), 0.0);
  }

  @Test
  public void convertDateToDoubleDifferentDaysTest() throws Exception {
    SimpleDateFormat dateFormat = new SimpleDateFormat(Constants.DATA_FORMAT_WITH_DATE);
    Date date1 = dateFormat.parse("2022-10-25 23:59:00.000");
    Date date2 = dateFormat.parse("2022-10-26 00:00:00.000");
    double double1 = Utils.convertDateToDouble(date1);
    double double2 = Utils.convertDateToDouble(date2);

    assertTrue(double2 - double1 < 1);
    assertEquals(44857, Math.floor(double1), 0.0);
    assertEquals(44858, Math.floor(double2), 0.0);
  }

}
