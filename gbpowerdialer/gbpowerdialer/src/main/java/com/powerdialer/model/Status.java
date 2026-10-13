package com.powerdialer.model;

import java.io.Serializable;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.powerdialer.serialization.StatusDeserializer;

@JsonDeserialize(using = StatusDeserializer.class)
public class Status implements Serializable {

  private static final long serialVersionUID = -7648225974467168749L;
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
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((id == null) ? 0 : id.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    Status other = (Status) obj;
    if (id == null) {
      return other.id == null;
    } else return id.equals(other.id);
  }

  @Override
  public String toString() {
    return "Status [id=" + id + ", name=" + name + "]";
  }

}
