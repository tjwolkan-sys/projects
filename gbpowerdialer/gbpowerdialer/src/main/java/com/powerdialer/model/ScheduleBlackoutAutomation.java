package com.powerdialer.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.powerdialer.model.base.BaseEntity;
import org.hibernate.annotations.DynamicUpdate;

import javax.persistence.*;
import java.util.Date;

@Entity
@Table(name = "pd_schedule_blackout_automation")
@DynamicUpdate
public class ScheduleBlackoutAutomation extends BaseEntity implements CampaignHolder {


  private Long id;
  private Campaign campaign;
  private Date startDate;
  private Date endDate;
  private String description;

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO, generator = "baseEntity_seq")
  @SequenceGenerator(name = "baseEntity_seq", sequenceName = "PD_SCHEDULE_BLACKAUTOMATION_FCSEQ", allocationSize = 1)
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


  @Column(name = "startDate", nullable = false)
  public Date getStartDate() {
    return startDate;
  }

  public void setStartDate(Date startDate) {
    this.startDate = startDate;
  }
  @Column(name = "endDate", nullable = false)
  public Date getEndDate() {
    return endDate;
  }

  public void setEndDate(Date endDate) {
    this.endDate = endDate;
  }
  @Column(name = "description", nullable = false)
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
    if (!(o instanceof ScheduleBlackoutAutomation)) {
      return false;
    }
    final ScheduleBlackoutAutomation obj = (ScheduleBlackoutAutomation) o;
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
    return "ScheduleBlackoutAutomation{" +
            "id=" + id +
            ", campaignId=" + (campaign != null ? campaign.getId() : null) +
            ", startDate=" + startDate +
            ", endDate=" + endDate +
            ", description='" + description + '\'' +
            '}';
  }

}
