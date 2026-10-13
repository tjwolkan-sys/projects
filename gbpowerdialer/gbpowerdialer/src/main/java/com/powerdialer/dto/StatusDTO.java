package com.powerdialer.dto;

public class StatusDTO {

  private Long id;
  private String name;
  private boolean availableForDialerCalls;

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

  public boolean isAvailableForDialerCalls() {
    return availableForDialerCalls;
  }

  public void setAvailableForDialerCalls(boolean availableForDialerCalls) {
    this.availableForDialerCalls = availableForDialerCalls;
  }

  @Override
  public String toString() {
    return "Status [id=" + id + ", name=" + name + ", availableForDialerCalls=" + availableForDialerCalls + "]";
  }

}
