package com.powerdialer.model;

import static com.powerdialer.util.Constants.COMPARISON_STRATEGY_TYPE;
import static com.powerdialer.util.Constants.SCHEDULE_ACTION_TYPE;
import java.util.List;

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
import javax.persistence.JoinTable;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import javax.validation.constraints.Size;

import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.validator.constraints.NotBlank;

import com.fasterxml.jackson.annotation.JsonIgnore;

import com.powerdialer.model.base.BaseEntity;

@Entity
@Table(name = "pd_schedule_automation")
@DynamicUpdate
public class ScheduleAutomation extends BaseEntity implements CampaignHolder {

  private Long id;
  private String name;
  private String description;
  private List<Queue> queues;
  private List<Group> groups;
  private SCHEDULE_ACTION_TYPE actionType;
  private String formula;
  private int threshold;
  private Campaign campaign;
  private COMPARISON_STRATEGY_TYPE comparisonType;

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO, generator = "baseEntity_seq")
  @SequenceGenerator(name = "baseEntity_seq", sequenceName = "PD_SCHEDULE_AUTOMATION_FCSEQ", allocationSize = 1)
  @Column(name = "automation_id")
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

  @Column(name = "description")
  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  @OneToMany(fetch = FetchType.EAGER)
  @JoinTable(name="pd_automation_queue",
      joinColumns={ @JoinColumn(name="schedule_automation_id", referencedColumnName="automation_id") },
      inverseJoinColumns={ @JoinColumn(name="queue_id", referencedColumnName="id") })
  @Fetch(FetchMode.SUBSELECT)
  public List<Queue> getQueues() {
    return queues;
  }

  public void setQueues(List<Queue> queues) {
    this.queues = queues;
  }

  @OneToMany(fetch = FetchType.EAGER)
  @JoinTable(name="pd_automation_group",
          joinColumns={ @JoinColumn(name="schedule_automation_id", referencedColumnName="automation_id") },
          inverseJoinColumns={ @JoinColumn(name="group_id", referencedColumnName="id") })
  @Fetch(FetchMode.SUBSELECT)
  public List<Group> getGroups() {
    return groups;
  }

  public void setGroups(List<Group> groups) {
    this.groups = groups;
  }

  @Enumerated(EnumType.ORDINAL)
  @Column(name = "action_type", nullable = false)
  public SCHEDULE_ACTION_TYPE getActionType() {
    return actionType;
  }

  public void setActionType(SCHEDULE_ACTION_TYPE actionType) {
    this.actionType = actionType;
  }

  @NotBlank
  @Size(max = 4000)
  @Column(name = "formula", nullable = false, length = 4000)
  public String getFormula() {
    return formula;
  }

  public void setFormula(String formula) {
    this.formula = formula;
  }

  @Column(name = "threshold")
  public int getThreshold() {
    return threshold;
  }

  public void setThreshold(int threshold) {
    this.threshold = threshold;
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

  @Enumerated(EnumType.ORDINAL)
  @Column(name = "comparison_type", nullable = false)
  public COMPARISON_STRATEGY_TYPE getComparisonType() {
    return comparisonType;
  }

  public void setComparisonType(COMPARISON_STRATEGY_TYPE comparisonType) {
    this.comparisonType = comparisonType;
  }

  @Override
  public String toString() {
    return "ScheduleAutomation [id=" + id + ", name=" + name + ", actionType=" + actionType + ", campaign=" + campaign + "]";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof Schedule)) {
      return false;
    }
    final Schedule obj = (Schedule) o;
    if (getId() == null && obj.getId() == null)
      return false;
    return generateEquals(getId(), obj.getId());
  }

  @Override
  public int hashCode() {
    return generateHash(31, 42, getId());
  }
}
