package com.powerdialer.model;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import javax.persistence.Transient;

import com.powerdialer.util.Constants;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.annotations.WhereJoinTable;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.powerdialer.model.base.BaseEntity;
import com.powerdialer.serialization.ResultCodeDeserializer;

@Entity
@Table(name = "wbp_result_code")
@JsonDeserialize(using = ResultCodeDeserializer.class)
public class ResultCode extends BaseEntity {

  private static final long serialVersionUID = 1675843405937971399L;
  private Long id;
  private String name;
  private String description;
  private int sort;
  private Set<Group> groups = new HashSet<>();
  private boolean finalStatus;
  private boolean dncIdentifier;
  private String callbackNumber;
  private Date callbackDate;
  private Constants.CALLBACKTYPE callbackType;

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO, generator = "baseEntity_seq")
  @SequenceGenerator(name = "baseEntity_seq", sequenceName = "WBP_RESULT_CODE_FCSEQ", allocationSize = 1)
  @Column(name = "id")
  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  @Column(name = "result_code_text")
  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  @Column(name = "description")
  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  @Column(name = "sort")
  public int getSort() {
    return sort;
  }

  public void setSort(int sort) {
    this.sort = sort;
  }

  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(name = "wbp_result_code_group", joinColumns = { @JoinColumn(name = "result_code_id") }, inverseJoinColumns = @JoinColumn(name = "group_id"))
  @WhereJoinTable(clause = "deleted = 0")
  @Fetch(FetchMode.SUBSELECT)
  public Set<Group> getGroups() {
    return groups;
  }

  public void setGroups(Set<Group> groups) {
    this.groups = groups;
  }

  @Column(name = "final_status")
  public boolean isFinalStatus() {
    return finalStatus;
  }

  public void setFinalStatus(boolean finalStatus) {
    this.finalStatus = finalStatus;
  }

  @Column(name = "dnc_identifier")
  public boolean isDncIdentifier() {
    return dncIdentifier;
  }

  public void setDncIdentifier(boolean dncIdentifier) {
    this.dncIdentifier = dncIdentifier;
  }

  @Transient
  public String getCallbackNumber() {
    return callbackNumber;
  }

  public void setCallbackNumber(String callbackNumber) {
    this.callbackNumber = callbackNumber;
  }

  @Transient
  public Date getCallbackDate() {
    return callbackDate;
  }

  public void setCallbackDate(Date callbackDate) {
    this.callbackDate = callbackDate;
  }

  @Transient
  public Constants.CALLBACKTYPE getCallbackType() {
    return callbackType;
  }
  public void setCallbackType(Constants.CALLBACKTYPE callbackType) {
    this.callbackType = callbackType;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((getId() == null) ? 0 : getId().hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (!(obj instanceof ResultCode))
      return false;
    ResultCode other = (ResultCode) obj;
    if (getId() == null) {
      return other.getId() == null;
    } else return getId().equals(other.getId());
  }

  @Override
  public String toString() {
    return "ResultCode [id=" + id + ", name=" + name + ", description=" + description + ", callbackNumber=" + callbackNumber + ", callbackDate=" + callbackDate + "]";
  }

}
