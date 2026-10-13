package com.powerdialer.model;

import java.util.Date;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

import org.hibernate.annotations.DynamicUpdate;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.powerdialer.model.base.BaseObject;
import com.powerdialer.util.Constants.LOG_LEVEL;
import com.powerdialer.util.Constants.LOG_MODULE;

@Entity
@Table(name = "pd_action_log")
@DynamicUpdate
public class ActionLog extends BaseObject {

  private static final long serialVersionUID = -7225267432264058953L;
  private Long id;
  private LOG_MODULE logModule;
  private String log;
  private LOG_LEVEL logLevel;
  private Date createDate;
  private Campaign campaign;
  private LSC lsc;
  private DNC dnc;
  private Tenant tenant;
  private User user;

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO, generator = "baseEntity_seq")
  @SequenceGenerator(name = "baseEntity_seq", sequenceName = "PD_ACTION_LOG_FCSEQ", allocationSize = 1)
  @Column(name = "id")
  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  @Enumerated(EnumType.ORDINAL)
  @Column(name = "log_module", nullable = false)
  public LOG_MODULE getLogModule() {
    return logModule;
  }

  public void setLogModule(LOG_MODULE logModule) {
    this.logModule = logModule;
  }

  @Column(name = "log", nullable = false, columnDefinition = "NVARCHAR(4000)")
  public String getLog() {
    return log;
  }

  public void setLog(String log) {
    this.log = log;
  }

  @Enumerated(EnumType.ORDINAL)
  @Column(name = "log_level", nullable = false)
  public LOG_LEVEL getLogLevel() {
    return logLevel;
  }

  public void setLogLevel(LOG_LEVEL logLevel) {
    this.logLevel = logLevel;
  }

  @Column(name = "create_date", nullable = false)
  public Date getCreateDate() {
    return createDate;
  }

  public void setCreateDate(Date createDate) {
    this.createDate = createDate;
  }

  @JsonIgnore
  @ManyToOne(cascade = { CascadeType.PERSIST }, fetch = FetchType.LAZY)
  @JoinColumn(name = "campaign_id")
  public Campaign getCampaign() {
    return campaign;
  }

  public void setCampaign(Campaign campaign) {
    this.campaign = campaign;
  }

  @JsonIgnore
  @ManyToOne(cascade = { CascadeType.PERSIST }, fetch = FetchType.LAZY)
  @JoinColumn(name = "lsc_id")
  public LSC getLsc() {
    return lsc;
  }

  public void setLsc(LSC lsc) {
    this.lsc = lsc;
  }

  @JsonIgnore
  @ManyToOne(cascade = { CascadeType.PERSIST }, fetch = FetchType.LAZY)
  @JoinColumn(name = "dnc_id")
  public DNC getDnc() {
    return dnc;
  }

  public void setDnc(DNC dnc) {
    this.dnc = dnc;
  }

  @JsonIgnore
  @ManyToOne(cascade = { CascadeType.PERSIST }, fetch = FetchType.LAZY)
  @JoinColumn(name = "tenant_id")
  public Tenant getTenant() {
    return tenant;
  }

  public void setTenant(Tenant tenant) {
    this.tenant = tenant;
  }

  @JsonIgnore
  @ManyToOne(cascade = { CascadeType.PERSIST }, fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id")
  public User getUser() {
    return user;
  }

  public void setUser(User user) {
    this.user = user;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ActionLog)) {
      return false;
    }
    final ActionLog obj = (ActionLog) o;
    if (getId() == null && obj.getId() == null)
      return false;
    return generateEquals(getId(), obj.getId());
  }

  @Override
  public int hashCode() {
    return generateHash(31, 42, getId());
  }

  @Override
  public String toString() {
    return "id = " + id + ", log = " + log;
  }
}
