package com.powerdialer.model;

import java.util.Date;

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

import org.hibernate.annotations.DynamicUpdate;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.powerdialer.model.base.BaseEntity;
import com.powerdialer.util.Constants.DAY_OF_WEEK;

@Entity
@Table(name = "pd_schedule")
@DynamicUpdate
public class Schedule extends BaseEntity implements CampaignHolder {

  private static final long serialVersionUID = -2176444267679604808L;
  private Long id;
  private Campaign campaign;
  private DAY_OF_WEEK day;
  private Date from;
  private Date till;
  private boolean resetEnabled;

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO, generator = "baseEntity_seq")
  @SequenceGenerator(name = "baseEntity_seq", sequenceName = "PD_SCHEDULE_FCSEQ", allocationSize = 1)
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

  @Enumerated(EnumType.ORDINAL)
  @Column(name = "day", nullable = false)
  public DAY_OF_WEEK getDay() {
    return day;
  }

  public void setDay(DAY_OF_WEEK day) {
    this.day = day;
  }

  @Column(name = "from_time", nullable = false)
  public Date getFrom() {
    return from;
  }

  public void setFrom(Date from) {
    this.from = from;
  }

  @Column(name = "till_time", nullable = false)
  public Date getTill() {
    return till;
  }

  public void setTill(Date till) {
    this.till = till;
  }

  @Column(name = "reset_enabled", nullable = false, columnDefinition = "BIT DEFAULT 0")
  public boolean isResetEnabled() {
    return resetEnabled;
  }

  public void setResetEnabled(boolean resetEnabled) {
    this.resetEnabled = resetEnabled;
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

  @Override
  public String toString() {
    return "Schedule [id=" + id + ", campaign=" + campaign + ", day=" + day + ", from=" + from + ", till=" + till + "]";
  }

}
