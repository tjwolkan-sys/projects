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
import javax.persistence.Table;
import javax.validation.constraints.Size;

import org.hibernate.annotations.LazyToOne;
import org.hibernate.annotations.LazyToOneOption;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.powerdialer.model.base.BaseEntity;
import com.powerdialer.util.Constants.REASON;

/**
 * Müşteri bazlı (customer-scope) DNC'de customer_no ile eklenen numaraların
 * customer_no ↔ phone_number ilişkisini tutar (FEAT-318332 refactor).
 *
 * pd_dnc_pn saf telefon block-list olarak kalır (dial-time motor onu okur);
 * bu tablo yalnız "hangi numara hangi müşteri için müşteri-scope engellendi"
 * bilgisini raporlama + delta-sync yeniden-genişletme için tutar. Her satır
 * bir (dnc_list_id, customer_no, phone_number) ilişkisidir; customer_no
 * doğal olarak birden çok satırda tekrar eder (1 müşteri → N numara).
 */
@Entity
@Table(name = "pd_dnc_customer")
public class DNCCustomerPhone extends BaseEntity {

  private static final long serialVersionUID = 4712233905119374411L;
  private Long id;
  private DNC dncList;
  private String customerNo;
  private String phoneNumber;
  private REASON reason;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id")
  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
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

  @Size(max = 255)
  @Column(name = "customer_no", columnDefinition = "NVARCHAR(255)")
  public String getCustomerNo() {
    return customerNo;
  }

  public void setCustomerNo(String customerNo) {
    this.customerNo = customerNo;
  }

  @Size(max = 255)
  @Column(name = "phone_number", columnDefinition = "NVARCHAR(255)")
  public String getPhoneNumber() {
    return phoneNumber;
  }

  public void setPhoneNumber(String phoneNumber) {
    this.phoneNumber = phoneNumber;
  }

  @Enumerated(EnumType.ORDINAL)
  @Column(name = "reason")
  public REASON getReason() {
    return reason;
  }

  public void setReason(REASON reason) {
    this.reason = reason;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof DNCCustomerPhone)) {
      return false;
    }
    final DNCCustomerPhone obj = (DNCCustomerPhone) o;
    if (getId() == null && obj.getId() == null)
      return false;
    return generateEquals(getId(), obj.getId());
  }

  @Override
  public int hashCode() {
    return generateHash(31, 43, getId());
  }

  @Override
  public String toString() {
    return "DNCCustomerPhone [id=" + id + ", customerNo=" + customerNo + ", phoneNumber=" + phoneNumber + ", reason=" + reason + "]";
  }
}
