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

import com.powerdialer.model.base.BaseEntityTenant;
import com.powerdialer.util.Constants.NUMBER_TYPE;

@Entity
@Table(name = "wbp_number")
public class Number extends BaseEntityTenant {

  private static final long serialVersionUID = -2193561626089236959L;
  private Long id;
  private String name;
  private String digits;
  private NUMBER_TYPE type;
  private IVRApplication ivrApplication;

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO, generator = "baseEntity_seq")
  @SequenceGenerator(name = "baseEntity_seq", sequenceName = "WBP_NUMBER_FCSEQ", allocationSize = 1)
  @Column(name = "id")
  public Long getId() {
    return this.id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  @Column(name = "number_name")
  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  @Column(name = "number_digits")
  public String getDigits() {
    return digits;
  }

  public void setDigits(String digits) {
    this.digits = digits;
  }

  @Enumerated(EnumType.ORDINAL)
  @Column(name = "type")
  public NUMBER_TYPE getType() {
    return type;
  }

  public void setType(NUMBER_TYPE type) {
    this.type = type;
  }

  @ManyToOne(cascade = { CascadeType.PERSIST }, fetch = FetchType.LAZY)
  @JoinColumn(name = "command_app")
  public IVRApplication getIvrApplication() {
    return ivrApplication;
  }

  public void setIvrApplication(IVRApplication ivrApplication) {
    this.ivrApplication = ivrApplication;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof Number)) {
      return false;
    }
    final Number obj = (Number) o;

    return generateEquals(getId(), obj.getId());
  }

  @Override
  public int hashCode() {
    return generateHash(31, 42, getId());
  }

  @Override
  public String toString() {
    return "Number [id=" + id + ", name=" + name + ", digits=" + digits + "]";
  }

}
