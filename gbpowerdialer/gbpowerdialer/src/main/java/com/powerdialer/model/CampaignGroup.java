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

import org.hibernate.annotations.DynamicUpdate;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.powerdialer.model.base.BaseEntity;

@Entity
@Table(name = "pd_campaign_group")
@DynamicUpdate
public class CampaignGroup extends BaseEntity implements CampaignHolder {

  private static final long serialVersionUID = 6262352070126097391L;
  private Long id;
  private Campaign campaign;
  private Group group;
  private int priority;

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO, generator = "baseEntity_seq")
  @SequenceGenerator(name = "baseEntity_seq", sequenceName = "PD_CAMPAIGN_GROUP_FCSEQ", allocationSize = 1)
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

  public void setCampaign(Campaign campaign) {
    this.campaign = campaign;
  }

  @JsonIgnore
  @ManyToOne(cascade = { CascadeType.PERSIST }, fetch = FetchType.LAZY)
  @JoinColumn(name = "group_id", nullable = false)
  public Group getGroup() {
    return group;
  }

  public void setGroup(Group group) {
    this.group = group;
  }

  @Column(name = "priority", nullable = false)
  public int getPriority() {
    return priority;
  }

  public void setPriority(int priority) {
    this.priority = priority;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof CampaignGroup)) {
      return false;
    }
    final CampaignGroup obj = (CampaignGroup) o;
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
    return "CampaignGroup [id=" + id + ", campaign=" + campaign + ", group=" + group + ", priority=" + priority + "]";
  }

}
