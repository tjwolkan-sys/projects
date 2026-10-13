package com.powerdialer.dto;

import java.util.Date;

import com.powerdialer.util.Constants.LOG_LEVEL;
import com.powerdialer.util.Constants.LOG_MODULE;

public class ActionLogDTO {

  private long id;
  private LOG_LEVEL logLevel;
  private String log;
  private LOG_MODULE logModule;
  private EntityNameDTO campaign;
  private EntityNameDTO lsc;
  private EntityNameDTO dnc;
  private EntityNameDTO user;
  private EntityNameDTO tenant;
  private Date createDate;

  public long getId() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
  }

  public LOG_LEVEL getLogLevel() {
    return logLevel;
  }

  public void setLogLevel(LOG_LEVEL logLevel) {
    this.logLevel = logLevel;
  }

  public String getLog() {
    return log;
  }

  public void setLog(String log) {
    this.log = log;
  }

  public LOG_MODULE getLogModule() {
    return logModule;
  }

  public void setLogModule(LOG_MODULE logModule) {
    this.logModule = logModule;
  }

  public EntityNameDTO getCampaign() {
    return campaign;
  }

  public void setCampaign(EntityNameDTO campaign) {
    this.campaign = campaign;
  }

  public EntityNameDTO getLsc() {
    return lsc;
  }

  public void setLsc(EntityNameDTO lsc) {
    this.lsc = lsc;
  }

  public EntityNameDTO getDnc() {
    return dnc;
  }

  public void setDnc(EntityNameDTO dnc) {
    this.dnc = dnc;
  }

  public EntityNameDTO getUser() {
    return user;
  }

  public void setUser(EntityNameDTO user) {
    this.user = user;
  }

  public EntityNameDTO getTenant() {
    return tenant;
  }

  public void setTenant(EntityNameDTO tenant) {
    this.tenant = tenant;
  }

  public Date getCreateDate() {
    return createDate;
  }

  public void setCreateDate(Date createDate) {
    this.createDate = createDate;
  }

}
