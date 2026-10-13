package com.powerdialer.util;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ActuatorUtil {

  private static final Log log = LogFactory.getLog(ApplicationUtil.class);

  @Value(value ="${project.version}")
  private String projectVersion;

  @Value(value ="${project.buildDate}")
  private String projectBuildDate;

  @Value(value = "${maven.build.timestamp.format}")
  private String dateFormat;

  private DateFormat dateFormatter;

  public String getProjectVersion() {
    return projectVersion;
  }

  public Long getProjectBuildDateTimestamp() {
    if (dateFormatter == null)
      dateFormatter = new SimpleDateFormat(dateFormat);
    try {
      return dateFormatter.parse(projectBuildDate).getTime();
    } catch (ParseException e) {
      log.error("Cannot parse build date: " + projectBuildDate);
      return null;
    }
  }
}
