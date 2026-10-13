package com.powerdialer.model;

import java.util.List;

public class ParametrizedQuery {

  private String query;
  private List<Object> parameters;

  public ParametrizedQuery(String query, List<Object> parameters) {
    this.query = query;
    this.parameters = parameters;
  }

  public String getQuery() {
    return query;
  }

  public void setQuery(String query) {
    this.query = query;
  }

  public List<Object> getParameters() {
    return parameters;
  }

  public void setParameters(List<Object> parameters) {
    this.parameters = parameters;
  }

}
