package com.powerdialer.dto;

import java.util.Date;

public class AdminLogListDTO extends BaseEntityDTO {

  private int actionType;
  private int actionTarget;
  private UserFormDTO user;
  private TenantFormDTO tenant;
  private Integer actionTargetVersion;
  private Long actionValue;
  private String actionValueName;
  private Date actionDate;

  public int getActionType() {
    return actionType;
  }

  public void setActionType(int actionType) {
    this.actionType = actionType;
  }

  public int getActionTarget() {
    return actionTarget;
  }

  public void setActionTarget(int actionTarget) {
    this.actionTarget = actionTarget;
  }

  public UserFormDTO getUser() {
    return user;
  }

  public void setUser(UserFormDTO user) {
    this.user = user;
  }

  public TenantFormDTO getTenant() {
    return tenant;
  }

  public void setTenant(TenantFormDTO tenant) {
    this.tenant = tenant;
  }

  public Integer getActionTargetVersion() {
    return actionTargetVersion;
  }

  public void setActionTargetVersion(Integer actionTargetVersion) {
    this.actionTargetVersion = actionTargetVersion;
  }

  public Long getActionValue() {
    return actionValue;
  }

  public void setActionValue(Long actionValue) {
    this.actionValue = actionValue;
  }

  public String getActionValueName() {
    return actionValueName;
  }

  public void setActionValueName(String actionValueName) {
    this.actionValueName = actionValueName;
  }

  public Date getActionDate() {
    return actionDate;
  }

  public void setActionDate(Date actionDate) {
    this.actionDate = actionDate;
  }

}
