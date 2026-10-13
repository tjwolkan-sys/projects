package com.powerdialer.dto;

public class ServerListDTO {

  private Long id;
  private String name;
  private String ip;
  private String domain;
  private String type;
  private String connectionState;
  private boolean enabled;
  private boolean inUse;
  private Integer cpsLimit = 0;
  private Integer maxLines = 0;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getIp() {
    return ip;
  }

  public void setIp(String ip) {
    this.ip = ip;
  }

  public String getDomain() {
    return domain;
  }

  public void setDomain(String domain) {
    this.domain = domain;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public String getConnectionState() {
    return connectionState;
  }

  public void setConnectionState(String connectionState) {
    this.connectionState = connectionState;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public boolean isInUse() {
    return inUse;
  }

  public void setInUse(boolean inUse) {
    this.inUse = inUse;
  }

  public Integer getCpsLimit() {
    return cpsLimit;
  }

  public void setCpsLimit(Integer cpsLimit) {
    this.cpsLimit = cpsLimit;
  }

  public Integer getMaxLines() {
    return maxLines;
  }

  public void setMaxLines(Integer maxLines) {
    this.maxLines = maxLines;
  }

}
