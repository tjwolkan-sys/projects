package com.powerdialer.model;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

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
import javax.persistence.OneToMany;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import javax.persistence.Transient;

import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

import com.powerdialer.model.base.BaseObject;
import com.powerdialer.util.Constants.ACTION_TARGET;
import com.powerdialer.util.Constants.ACTION_TYPE;

@Entity
@Table(name = "pd_admin_log")
public class AdminLog extends BaseObject {

  private static final long serialVersionUID = -6318706340636165475L;
  private Long id;
  private User user;
  private ACTION_TYPE actionType;
  private ACTION_TARGET actionTarget;
  private Integer actionTargetVersion;
  private Tenant tenant;
  private Long actionValue;
  private String actionValueName;
  private Date actionDate;
  private String changeLog;
  private List<AdminLogDetails> adminLogDetailsList = new ArrayList<>();

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO, generator = "baseEntity_seq")
  @SequenceGenerator(name = "baseEntity_seq", sequenceName = "PD_ADMIN_LOG_FCSEQ", allocationSize = 1)
  @Column(name = "id")
  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  @ManyToOne(cascade = { CascadeType.PERSIST }, fetch = FetchType.EAGER)
  @JoinColumn(name = "user_id", nullable = false)
  public User getUser() {
    return user;
  }

  public void setUser(User user) {
    this.user = user;
  }

  @Enumerated(EnumType.ORDINAL)
  @Column(name = "action_type", nullable = false)
  public ACTION_TYPE getActionType() {
    return actionType;
  }

  public void setActionType(ACTION_TYPE actionType) {
    this.actionType = actionType;
  }

  @Enumerated(EnumType.ORDINAL)
  @Column(name = "action_target", nullable = false)
  public ACTION_TARGET getActionTarget() {
    return actionTarget;
  }

  public void setActionTarget(ACTION_TARGET actionTarget) {
    this.actionTarget = actionTarget;
  }

  @Transient
  public Integer getActionTargetVersion() {
    return actionTargetVersion;
  }

  public void setActionTargetVersion(Integer actionTargetVersion) {
    this.actionTargetVersion = actionTargetVersion;
  }

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "tenant_id")
  public Tenant getTenant() {
    return tenant;
  }

  public void setTenant(Tenant tenant) {
    this.tenant = tenant;
  }

  @Column(name = "action_value", nullable = false)
  public Long getActionValue() {
    return actionValue;
  }

  public void setActionValue(Long actionValue) {
    this.actionValue = actionValue;
  }

  @Column(name = "action_date", nullable = false)
  public Date getActionDate() {
    return actionDate;
  }

  public void setActionDate(Date actionDate) {
    this.actionDate = actionDate;
  }

  @Column(name = "change_log", nullable = false, columnDefinition = "NVARCHAR(4000)")
  public String getChangeLog() {
    return changeLog;
  }

  public void setChangeLog(String changeLog) {
    this.changeLog = changeLog;
  }

  @Column(name = "action_value_name", columnDefinition = "NVARCHAR(255)")
  public String getActionValueName() {
    return actionValueName;
  }

  public void setActionValueName(String actionValueName) {
    this.actionValueName = actionValueName;
  }

  @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.LAZY, mappedBy = "adminLog")
  @Fetch(FetchMode.SUBSELECT)
  public List<AdminLogDetails> getAdminLogDetailsList() {
    return adminLogDetailsList;
  }

  public void setAdminLogDetailsList(List<AdminLogDetails> adminLogDetailsList) {
    this.adminLogDetailsList = adminLogDetailsList;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof AdminLog)) {
      return false;
    }
    final AdminLog obj = (AdminLog) o;
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
    return "id = " + id;
  }

}
