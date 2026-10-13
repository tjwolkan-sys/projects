package com.powerdialer.model;

import org.hibernate.annotations.CreationTimestamp;

import javax.persistence.*;
import java.util.Date;

@Entity
@Table(name = "wbp_attached_data_log")
public class AttachedDataItem {
  private Long id;
  private String parentCallId;
  private String fieldName;
  private String fieldValue;
  private Date createDate;

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO, generator = "baseEntity_seq")
  @SequenceGenerator(name = "baseEntity_seq", sequenceName = "WBP_ATTACHED_DATA_LOG_FCSEQ", allocationSize = 1)
  @Column(name = "id")
  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  @Column(name = "parent_call_id", nullable = false)
  public String getParentCallId() {
    return parentCallId;
  }

  public void setParentCallId(String parentCallId) {
    this.parentCallId = parentCallId;
  }

  @Column(name = "field_name", nullable = false)
  public String getFieldName() {
    return fieldName;
  }

  public void setFieldName(String fieldName) {
    this.fieldName = fieldName;
  }

  @Column(name = "field_value")
  public String getFieldValue() {
    return fieldValue;
  }

  public void setFieldValue(String fieldValue) {
    this.fieldValue = fieldValue;
  }

  @CreationTimestamp
  @Column(name = "create_date")
  public Date getCreateDate() {
    return createDate;
  }

  public void setCreateDate(Date createDate) {
    this.createDate = createDate;
  }
}
