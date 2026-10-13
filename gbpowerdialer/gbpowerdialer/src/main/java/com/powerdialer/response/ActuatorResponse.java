package com.powerdialer.response;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;

public class ActuatorResponse {

  private String version;

  @JsonFormat(pattern="dd.MM.yyyy HH:mm:ss")
  private Date buildDate;

  public ActuatorResponse(String version, Date buildDate) {
    this.version = version;
    this.buildDate = buildDate;
  }

  public Date getBuildDate() {
    return buildDate;
  }

  public void setBuildDate(Date buildDate) {
    this.buildDate = buildDate;
  }

  public String getVersion() {
    return version;
  }

  public void setVersion(String version) {
    this.version = version;
  }
}
