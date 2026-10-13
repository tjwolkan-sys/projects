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
import javax.validation.constraints.Min;

import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.validator.constraints.NotBlank;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.powerdialer.model.base.BaseEntity;

@Entity
@Table(name = "pd_pn_column")
@DynamicUpdate
public class PhoneNumberColumn extends BaseEntity implements CampaignHolder {

  private static final long serialVersionUID = 5860203475529342377L;
  private Long id;
  private Campaign campaign;
  private String colNumber;
  private String colCallerId;
  private String colCallFromTime;
  private String colCallTillTime;
  private String colTimezoneOffset;
  private int colSort;

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO, generator = "baseEntity_seq")
  @SequenceGenerator(name = "baseEntity_seq", sequenceName = "PD_PN_COLUMN_FCSEQ", allocationSize = 1)
  @Column(name = "id")
  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  @JsonIgnore
  @ManyToOne(cascade = { CascadeType.PERSIST }, fetch = FetchType.LAZY)
  @JoinColumn(name = "campaign_id", nullable = false)
  public Campaign getCampaign() {
    return campaign;
  }

  @Override
  public void setCampaign(Campaign campaign) {
    this.campaign = campaign;
  }

  @NotBlank(message = "{phone_number_column.col_number.blank}")
  @Column(name = "col_number", nullable = false, columnDefinition = "NVARCHAR(255)")
  public String getColNumber() {
    return colNumber;
  }

  public void setColNumber(String colNumber) {
    this.colNumber = colNumber;
  }

  @Column(name = "col_caller_id", columnDefinition = "NVARCHAR(255)")
  public String getColCallerId() {
    return colCallerId;
  }

  public void setColCallerId(String colCallerId) {
    this.colCallerId = colCallerId;
  }

  @Column(name = "col_call_from_time", columnDefinition = "NVARCHAR(255)")
  public String getColCallFromTime() {
    return colCallFromTime;
  }

  public void setColCallFromTime(String colCallFromTime) {
    this.colCallFromTime = colCallFromTime;
  }

  @Column(name = "col_call_till_time", columnDefinition = "NVARCHAR(255)")
  public String getColCallTillTime() {
    return colCallTillTime;
  }

  public void setColCallTillTime(String colCallTillTime) {
    this.colCallTillTime = colCallTillTime;
  }

  @Column(name = "col_timezone_offset", columnDefinition = "NVARCHAR(255)")
  public String getColTimezoneOffset() {
    return colTimezoneOffset;
  }

  public void setColTimezoneOffset(String colTimezoneOffset) {
    this.colTimezoneOffset = colTimezoneOffset;
  }

  @Min(message = "{phone_number_column.col_sort.min}", value = 0)
  @Column(name = "col_sort", nullable = false)
  public int getColSort() {
    return colSort;
  }

  public void setColSort(int colSort) {
    this.colSort = colSort;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof PhoneNumberColumn)) {
      return false;
    }
    final PhoneNumberColumn obj = (PhoneNumberColumn) o;
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
    return "PhoneNumberColumn [id=" + id + ", colNumber=" + colNumber + "]";
  }

}
