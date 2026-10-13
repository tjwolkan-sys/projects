package com.powerdialer.model;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import javax.persistence.Transient;

import com.powerdialer.model.base.BaseObject;

@Entity
@Table(name = "pd_admin_log_details")
public class AdminLogDetails extends BaseObject {

  private static final long serialVersionUID = 5409795004412885817L;
  private Long id;
  private String name;
  private String description;  
  private AdminLog adminLog;
  private String entityName;
  private String field;
  private String previousValue;
  private String newValue;
  private String added;
  private String removed;

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO, generator = "baseEntity_seq")
  @SequenceGenerator(name = "baseEntity_seq", sequenceName = "PD_ADMIN_LOG_DETAILS_FCSEQ", allocationSize = 1)
  @Column(name = "id")
  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  @ManyToOne(cascade = { CascadeType.PERSIST }, fetch = FetchType.LAZY)
  @JoinColumn(name = "admin_log_id")
  public AdminLog getAdminLog() {
    return adminLog;
  }

  public void setAdminLog(AdminLog adminLog) {
    this.adminLog = adminLog;
  }

  @Column(name = "entity_name", columnDefinition="NVARCHAR(255)")
  public String getEntityName() {
    return entityName;
  }

  public void setEntityName(String entityName) {
    this.entityName = entityName;
  }

  @Column(name = "field", columnDefinition="NVARCHAR(255)")
  public String getField() {
    return field;
  }

  public void setField(String field) {
    this.field = field;
  }

  @Column(name = "prev_value", columnDefinition="NVARCHAR(MAX)")
  public String getPreviousValue() {
    return previousValue;
  }

  public void setPreviousValue(String previousValue) {
    this.previousValue = previousValue;
  }

  @Column(name = "new_value", columnDefinition="NVARCHAR(MAX)")
  public String getNewValue() {
    return newValue;
  }

  public void setNewValue(String newValue) {
    this.newValue = newValue;
  }

  @Column(name = "added", columnDefinition="NVARCHAR(MAX)")
  public String getAdded() {
    return added;
  }

  public void setAdded(String added) {
    this.added = added;
  }

  @Column(name = "removed", columnDefinition="NVARCHAR(MAX)")
  public String getRemoved() {
    return removed;
  }

  public void setRemoved(String removed) {
    this.removed = removed;
  }

  @Transient
  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  @Transient
  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  @Override
  public String toString() {
    return "id = " + getId();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof AdminLogDetails)) {
      return false;
    }
    final AdminLogDetails obj = (AdminLogDetails) o;
    if (getId() == null && obj.getId() == null)
      return false;
    return generateEquals(getId(), obj.getId());
  }

  @Override
  public int hashCode() {
    return generateHash(31, 42, getId());
  }

}
