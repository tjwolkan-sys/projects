package com.powerdialer.request;

/**
 * Request for creating campaign's default source table
 */
public class CreateSourceTableRequest extends DbTableRequest {

  private boolean fetchDataRandomly;

  public CreateSourceTableRequest() {}

  public boolean isFetchDataRandomly() {
    return fetchDataRandomly;
  }

  public void setFetchDataRandomly(boolean fetchDataRandomly) {
    this.fetchDataRandomly = fetchDataRandomly;
  }

}
