package com.powerdialer.model;

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
import javax.validation.constraints.Size;

import org.hibernate.annotations.LazyToOne;
import org.hibernate.annotations.LazyToOneOption;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.powerdialer.model.base.BaseEntity;
import com.powerdialer.util.Constants.REASON;

import java.util.Date;

@Entity
@Table(name = "pd_dnc_pn")
public class DNCPhoneNumber extends BaseEntity {

  private static final long serialVersionUID = 5959659271375437235L;
  private Long id;
  private String phoneNumber;
  private REASON reason;
  private DNC dncList;
  private Date expiredDate;

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO, generator = "baseEntity_seq")
  @SequenceGenerator(name = "baseEntity_seq", sequenceName = "PD_DNC_PN_FCSEQ", allocationSize = 1)
  @Column(name = "id")
  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  @Size(message = "{dnc_phone_number.name.range}", max = 255)
  @Column(name = "phone_number", columnDefinition = "NVARCHAR(255)")
  public String getPhoneNumber() {
    return phoneNumber;
  }

  public void setPhoneNumber(String phoneNumber) {
    this.phoneNumber = phoneNumber;
  }

  @Enumerated(EnumType.ORDINAL)
  @Column(name = "reason", nullable = false)
  public REASON getReason() {
    return reason;
  }

  public void setReason(REASON reason) {
    this.reason = reason;
  }

  @JsonIgnore
  @ManyToOne(cascade = { CascadeType.PERSIST }, fetch = FetchType.LAZY)
  @JoinColumn(name = "dnc_list_id")
  @LazyToOne(LazyToOneOption.PROXY)
  public DNC getDncList() {
    return dncList;
  }

  public void setDncList(DNC dncList) {
    this.dncList = dncList;
  }

  @Column(name = "expired_date", nullable = true)
  public Date getExpiredDate() {
    return expiredDate;
  }
  public void setExpiredDate(Date expiredDate) {
    this.expiredDate = expiredDate;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof DNCPhoneNumber)) {
      return false;
    }
    final DNCPhoneNumber obj = (DNCPhoneNumber) o;
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
    return "DNCPhoneNumber [id=" + id + ", phoneNumber=" + phoneNumber + ", expiredDate=" + expiredDate + ", reason=" + reason + "]";
  }

}
