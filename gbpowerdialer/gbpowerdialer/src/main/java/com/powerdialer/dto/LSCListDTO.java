package com.powerdialer.dto;

public class LSCListDTO extends BaseEntityTenantDTO {

  private String url;
  private String httpMethod;
  private String payload;
  private String headers;
  private int replyType;
  private String replyKey;
  private String replyResult;
  private String responseKeys;
  private Integer requestTimeout;
  private boolean global;
  private boolean freePayloadEnabled;
  private boolean tokenRequired;
  private LSCAuthFormDTO authorization;
  private boolean startsWithZero;

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public String getHttpMethod() {
    return httpMethod;
  }

  public void setHttpMethod(String httpMethod) {
    this.httpMethod = httpMethod;
  }

  public String getPayload() {
    return payload;
  }

  public void setPayload(String payload) {
    this.payload = payload;
  }

  public String getHeaders() {
    return headers;
  }

  public void setHeaders(String headers) {
    this.headers = headers;
  }

  public int getReplyType() {
    return replyType;
  }

  public void setReplyType(int replyType) {
    this.replyType = replyType;
  }

  public String getReplyKey() {
    return replyKey;
  }

  public void setReplyKey(String replyKey) {
    this.replyKey = replyKey;
  }

  public String getReplyResult() {
    return replyResult;
  }

  public void setReplyResult(String replyResult) {
    this.replyResult = replyResult;
  }

  public Integer getRequestTimeout() {
    return requestTimeout;
  }

  public void setRequestTimeout(Integer requestTimeout) {
    this.requestTimeout = requestTimeout;
  }

  public boolean isGlobal() {
    return global;
  }

  public void setGlobal(boolean global) {
    this.global = global;
  }

  public boolean isFreePayloadEnabled() {
    return freePayloadEnabled;
  }

  public void setFreePayloadEnabled(boolean freePayloadEnabled) {
    this.freePayloadEnabled = freePayloadEnabled;
  }

  public boolean isTokenRequired() {
    return tokenRequired;
  }

  public void setTokenRequired(boolean tokenRequired) {
    this.tokenRequired = tokenRequired;
  }

  public LSCAuthFormDTO getAuthorization() {
    return authorization;
  }

  public void setAuthorization(LSCAuthFormDTO authorization) {
    this.authorization = authorization;
  }

  public boolean isStartsWithZero() {
    return startsWithZero;
  }

  public void setStartsWithZero(boolean startsWithZero) {
    this.startsWithZero = startsWithZero;
  }

  public String getResponseKeys() {
    return responseKeys;
  }

  public void setResponseKeys(String responseKeys) {
    this.responseKeys = responseKeys;
  }
}
