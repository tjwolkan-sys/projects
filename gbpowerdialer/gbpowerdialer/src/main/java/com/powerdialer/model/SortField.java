package com.powerdialer.model;

import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.Where;

import com.fasterxml.jackson.annotation.JsonIgnore;

import com.powerdialer.model.base.BaseEntity;
import com.powerdialer.util.Constants;

@Entity
@Table(name = "pd_sort_field")
@DynamicUpdate
public class SortField extends BaseEntity implements CampaignHolder {

  private Long id;
  private Campaign campaign;
  private String name;
  private List<SortFieldValue> values;
  private int priority;
  private Constants.DB_SORT_DIRECTION sortDirection;
  private Constants.MULTIPLE_SORT_METHOD sortMethod;

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO, generator = "baseEntity_seq")
  @SequenceGenerator(name = "baseEntity_seq", sequenceName = "PD_SORT_FIELD_FCSEQ", allocationSize = 1)
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

  @Column(name = "name", nullable = false)
  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  @OneToMany(cascade = { CascadeType.ALL }, fetch = FetchType.LAZY, mappedBy = "sortField")
  @Where(clause = "deleted = 0")
  public List<SortFieldValue> getValues() {
    return values;
  }

  public void setValues(List<SortFieldValue> values) {
    this.values = values;
  }

  @Column(name = "priority", nullable = false)
  public int getPriority() {
    return priority;
  }

  public void setPriority(int priority) {
    this.priority = priority;
  }

  @Column(name = "sort_direction")
  public Constants.DB_SORT_DIRECTION getSortDirection() {
    return sortDirection;
  }

  public void setSortDirection(Constants.DB_SORT_DIRECTION sortDirection) {
    this.sortDirection = sortDirection;
  }

  @Column(name = "sort_method")
  public Constants.MULTIPLE_SORT_METHOD getSortMethod() {
    return sortMethod;
  }

  public void setSortMethod(Constants.MULTIPLE_SORT_METHOD sortMethod) {
    this.sortMethod = sortMethod;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof SortField)) {
      return false;
    }
    final SortField obj = (SortField) o;
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
    return "SortField [id=" + id + ", name=" + name + "]";
  }

}
