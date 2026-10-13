package com.powerdialer.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

import com.powerdialer.model.base.BaseEntityTenant;

@Entity
@Table(name = "wbp_ivr_application")
public class IVRApplication extends BaseEntityTenant {

  private static final long serialVersionUID = -9117461782502038145L;
  private Long id;
  private String name;
  private String context;
  private String description;

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO, generator = "baseEntity_seq")
  @SequenceGenerator(name = "baseEntity_seq", sequenceName = "WBP_IVR_APPLICATION_FCSEQ", allocationSize = 1)
  @Column(name = "id")
  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  @Column(name = "name")
  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  @Column(name = "context")
  public String getContext() {
    return context;
  }

  public void setContext(String context) {
    this.context = context;
  }

  @Column(name = "description")
  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof IVRApplication)) {
      return false;
    }
    final IVRApplication obj = (IVRApplication) o;
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
    return "IVRApplication [id=" + id + ", name=" + name + ", context=" + context + "]";
  }

}
