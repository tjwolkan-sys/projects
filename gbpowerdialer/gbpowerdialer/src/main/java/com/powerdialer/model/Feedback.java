package com.powerdialer.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import javax.validation.constraints.Size;

import com.powerdialer.model.base.BaseEntityTenant;
import com.powerdialer.util.Constants.FEEDBACK_TYPE;

@Entity
@Table(name = "wbp_feedback")
public class Feedback extends BaseEntityTenant {

  private static final long serialVersionUID = 894845883819214765L;
  private Long id;
  private String message;
  private FEEDBACK_TYPE type;
  private String logArray;
  private Boolean isRead = false;
  private Integer status = 0;
  private Integer source = 3;

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO, generator = "baseEntity_seq")
  @SequenceGenerator(name = "baseEntity_seq", sequenceName = "WBP_FEEDBACK_FCSEQ", allocationSize = 1)
  @Column(name = "id")
  public Long getId() {
    return this.id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  @Column(name = "source")
  public Integer getSource() {
    return source;
  }

  public void setSource(Integer source) {
    if (source != null) {
      this.source = source;
    }
  }

  @Column(name = "is_read")
  public Boolean getIsRead() {
    return isRead;
  }

  public void setIsRead(Boolean isRead) {
    this.isRead = isRead;
  }

  @Size(message = "{feedback.message.range}", max = 255)
  @Column(name = "message", columnDefinition = "NVARCHAR(255)")
  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  @Enumerated(EnumType.ORDINAL)
  @Column(name = "type")
  public FEEDBACK_TYPE getType() {
    return type;
  }

  public void setType(FEEDBACK_TYPE type) {
    this.type = type;
  }

  @Column(name = "status")
  public Integer getStatus() {
    return status;
  }

  public void setStatus(Integer status) {
    if (status != null) {
      this.status = status;
    }
  }

  @Column(name = "log_array", columnDefinition = "NVARCHAR(255)")
  public String getLogArray() {
    return logArray;
  }

  public void setLogArray(String logArray) {
    this.logArray = logArray;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof Feedback)) {
      return false;
    }
    final Feedback obj = (Feedback) o;
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
    return "Feedback [id=" + id + ", message=" + message + ", type=" + type + "]";
  }

}
