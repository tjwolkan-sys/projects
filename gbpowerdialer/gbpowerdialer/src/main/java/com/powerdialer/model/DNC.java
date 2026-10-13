package com.powerdialer.model;

import java.util.Date;
import java.util.List;
import java.util.Set;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToMany;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import javax.validation.Valid;
import javax.validation.constraints.Size;

import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.annotations.Where;
import org.hibernate.validator.constraints.NotBlank;

import com.powerdialer.model.base.BaseEntityTenant;

@Entity
@Table(name = "pd_dnc_list")
@DynamicUpdate
public class DNC extends BaseEntityTenant {

  private static final long serialVersionUID = -2194383361297934249L;
  private Long id;
  private String name;
  private String description;
  private boolean global;
  private List<DNCPhoneNumber> phoneNumbers;
  private Set<Campaign> campaigns;
  private Campaign campaign;
  private Date expireDate;

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO, generator = "baseEntity_seq")
  @SequenceGenerator(name = "baseEntity_seq", sequenceName = "PD_DNC_LIST_FCSEQ", allocationSize = 1)
  @Column(name = "id")
  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  @NotBlank(message = "{dnc.name.blank}")
  @Size(message = "{dnc.name.range}", max = 255)
  @Column(name = "name", nullable = false, columnDefinition = "NVARCHAR(255)")
  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  @Size(message = "{dnc.description.range}", max = 255)
  @Column(name = "description", columnDefinition = "NVARCHAR(255)")
  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  @Column(name = "global", nullable = false)
  public boolean getGlobal() {
    return global;
  }

  public void setGlobal(boolean global) {
    this.global = global;
  }

  @Valid
  @OneToMany(cascade = { CascadeType.ALL }, fetch = FetchType.LAZY, mappedBy = "dncList")
  @Where(clause = "deleted = 0")
  @Fetch(FetchMode.SUBSELECT)
  public List<DNCPhoneNumber> getPhoneNumbers() {
    return phoneNumbers;
  }

  public void setPhoneNumbers(List<DNCPhoneNumber> phoneNumbers) {
    this.phoneNumbers = phoneNumbers;
  }

  @ManyToMany(fetch = FetchType.LAZY, mappedBy = "dncs")
  @Where(clause = "deleted = 0")
  @Fetch(FetchMode.SUBSELECT)
  public Set<Campaign> getCampaigns() {
    return campaigns;
  }

  public void setCampaigns(Set<Campaign> campaigns) {
    this.campaigns = campaigns;
  }

  @OneToOne(cascade = { CascadeType.PERSIST }, fetch = FetchType.LAZY, optional = true)
  @JoinColumn(name = "campaign_id", nullable = true)
  @Fetch(FetchMode.JOIN)
  public Campaign getCampaign() {
    return campaign;
  }

  public void setCampaign(Campaign campaign) {
    this.campaign = campaign;
  }

  @Column(name = "expire_date")
  public Date getExpireDate() {
    return expireDate;
  }

  public void setExpireDate(Date expireDate) {
    this.expireDate = expireDate;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof DNC)) {
      return false;
    }
    final DNC obj = (DNC) o;
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
    return "DNC [id=" + id + ", name=" + name + ", global=" + global + "]";
  }
}
