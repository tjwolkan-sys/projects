package com.powerdialer.model;

import java.io.Serializable;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import javax.persistence.Transient;

import com.powerdialer.model.base.BaseEntity;


@Entity
@Table(name = "wbp_status")
public class GroupStatus extends BaseEntity implements Serializable {

  private static final long serialVersionUID = 1L;
  private Long id;
  private String statusText;
  private boolean systemStatus = false;

  private String code;
  // UniqueId or CallId (from registrar srvr)
  private String uniqueId;
  private String groupName;

  public GroupStatus() {
    enabled = true;
  }

  public GroupStatus(Long id,
                     String statusText,
                     String code) {
    this.id = id;
    this.statusText = statusText;
    this.code = code;
    enabled = true;
  }


  @Id
  @GeneratedValue(strategy = GenerationType.AUTO, generator = "baseEntity_seq")
  @SequenceGenerator(name = "baseEntity_seq", sequenceName = "WBP_STATUS_FCSEQ", allocationSize = 1)
  @Column(name = "id")
  public Long getId() {
    return this.id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  @Transient
  public String getUniqueId() {
    return uniqueId;
  }

  public void setUniqueId(String uniqueId) {
    this.uniqueId = uniqueId;
  }

  @Column(name = "status_text")
  public String getStatusText() {
    return statusText;
  }

  public void setStatusText(String statusText) {
    this.statusText = statusText;
  }

  @Column(name = "system_status")
  public boolean isSystemStatus() {
    return systemStatus;
  }

  public void setSystemStatus(boolean systemStatus) {
    this.systemStatus = systemStatus;
  }

  @Column(name = "code")
  public String getCode() {
    return code;
  }

  public void setCode(String code) {
    this.code = code;
  }

  @Transient
  public String getGroupName() {
    return groupName;
  }

  public void setGroupName(String groupName) {
    this.groupName = groupName;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof GroupStatus)) {
      return false;
    }
    final GroupStatus obj = (GroupStatus) o;
    return generateEquals(getId(), obj.getId());
  }

  @Override
  public int hashCode() {
    return generateHash(31, 42, getId());
  }

  @Override
  public String toString() {
    return "Status [id=" + id + ", statusText=" + statusText + "]";
  }

}

