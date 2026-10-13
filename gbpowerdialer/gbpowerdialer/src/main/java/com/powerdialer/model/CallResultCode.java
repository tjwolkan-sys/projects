package com.powerdialer.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import com.powerdialer.model.base.BaseEntity;
import com.powerdialer.serialization.CallResultCodeDeserializer;
import com.powerdialer.util.Constants;

@JsonDeserialize(using = CallResultCodeDeserializer.class)
public class CallResultCode extends BaseEntity {

  private static final long serialVersionUID = 8331439691233920071L;
  private Long id;
  private ResultCode resultCode;
  private Long userId;
  private String callId;
  private String parentId;
  // Opsiyonel DNC kapsamı (additive Redis contract alanı): null veya PHONE
  // bugünkü tekil numara davranışıdır; CUSTOMER müşteri bazlı genişletmeyi
  // tetikler. Eski gbwebphone mesajları bu alanı göndermez (null kalır).
  private Constants.DNC_SCOPE dncScope;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public ResultCode getResultCode() {
    return resultCode;
  }

  public void setResultCode(ResultCode resultCode) {
    this.resultCode = resultCode;
  }

  public Long getUserId() {
    return userId;
  }

  public void setUserId(Long userId) {
    this.userId = userId;
  }

  public String getCallId() {
    return callId;
  }

  public void setCallId(String callId) {
    this.callId = callId;
  }

  public String getParentId() {
    return parentId;
  }

  public void setParentId(String parentId) {
    this.parentId = parentId;
  }

  public Constants.DNC_SCOPE getDncScope() {
    return dncScope;
  }

  public void setDncScope(Constants.DNC_SCOPE dncScope) {
    this.dncScope = dncScope;
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
    CallResultCode other = (CallResultCode) obj;
    if (id == null) {
      return other.id == null;
    } else return id.equals(other.id);
  }

  @Override
  public String toString() {
    return "CallResultCode [id=" + id + ", resultCode=" + resultCode + ", callId=" + callId + ", userId=" + userId + ", parentId=" + parentId + ", dncScope=" + dncScope + "]";
  }

}
