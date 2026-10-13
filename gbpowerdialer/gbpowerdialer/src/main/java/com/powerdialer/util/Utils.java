package com.powerdialer.util;

import java.util.Date;

import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.DateUtil;
import org.joda.time.LocalTime;

/**
 * Custom util class
 */
public class Utils {

  private static final int EXCEL_MSSQL_DAYS_SHIFT = 2;

  /**
   * Convert current localtime to float
   * @return float value from beginning of day
   */
  public static double convertTimeToDouble(LocalTime lt) {
    double hours = lt.getHourOfDay();
    double minutes = lt.getMinuteOfHour();
    double seconds = lt.getSecondOfMinute();
    if (seconds > 0) {
      double secondsToMinutes = 60D / seconds;
      secondsToMinutes = 1D / secondsToMinutes;
      minutes += secondsToMinutes;
    }
    if (minutes > 0) {
      double minutesToHours = 60D / minutes;
      minutesToHours = 1D / minutesToHours;
      hours += minutesToHours;
    }
    return hours / 24;
  }

  /**
   * Calculate days and seconds passed from 1900
   * @return elapsed time
   */
  public static double convertDateToDouble(Date date) {
    return date != null ? DateUtil.getExcelDate(date) - EXCEL_MSSQL_DAYS_SHIFT : 0D;
  }

  /**
   * Calculate milliseconds passed from 1900
   * @see Utils#convertDateToDouble(Date)
   * @return elapsed time
   */
  public static long convertDoubleToLongDate(Double d) {
    Date date = DateUtil.getJavaDate(d + EXCEL_MSSQL_DAYS_SHIFT);
    return date.getTime();
  }

  /**
   * Check if string can be parsed to integer
   * @param s string to check
   * @return true if string can be parsed to integer
   */
  public static boolean isStringInteger(String s) {
    return !StringUtils.isEmpty(s) && s.matches("^-?\\d+$");
  }

}
