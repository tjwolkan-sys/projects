package com.powerdialer.model;

import java.util.ArrayList;
import java.util.Date;
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
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import javax.validation.Valid;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.powerdialer.model.base.BaseEntity;
import com.powerdialer.util.Constants.APPLY_TO;

@Entity
@Table(name = "pd_rc_schedule")
@DynamicUpdate
public class ResultCodeSchedule extends BaseEntity implements CampaignHolder {

  private static final long serialVersionUID = 2425789439031797196L;
  private Long id;
  private Campaign campaign;
  private ResultCode resultCode;
  private TelephonyResultCode telephonyResultCode;
  private APPLY_TO applyTo;
  private int retryAmount;
  private double retryInterval;
  private boolean advancedIntervalEnabled;
  private Date minScheduleTime;
  private Date maxScheduleTime;
  private boolean macroCycle;
  private List<ResultCodeScheduleAdvanced> resultCodeScheduleAdvanceds = new ArrayList<>();

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO, generator = "baseEntity_seq")
  @SequenceGenerator(name = "baseEntity_seq", sequenceName = "PD_RC_SCHEDULE_FCSEQ", allocationSize = 1)
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

  @JsonIgnore
  @ManyToOne(cascade = { CascadeType.PERSIST }, fetch = FetchType.LAZY)
  @JoinColumn(name = "result_code_id")
  public ResultCode getResultCode() {
    return resultCode;
  }

  public void setResultCode(ResultCode resultCode) {
    this.resultCode = resultCode;
  }

  @Valid
  @JsonIgnore
  @ManyToOne(cascade = { CascadeType.PERSIST }, fetch = FetchType.LAZY)
  @JoinColumn(name = "telephony_rc_id")
  public TelephonyResultCode getTelephonyResultCode() {
    return telephonyResultCode;
  }

  public void setTelephonyResultCode(TelephonyResultCode telephonyResultCode) {
    this.telephonyResultCode = telephonyResultCode;
  }

  @NotNull(message = "{result_code_schedule.apply_to.empty}")
  @Enumerated(EnumType.ORDINAL)
  @Column(name = "apply_to", nullable = false)
  public APPLY_TO getApplyTo() {
    return applyTo;
  }

  public void setApplyTo(APPLY_TO applyTo) {
    this.applyTo = applyTo;
  }

  @Min(message = "{result_code_schedule.retry_amount.min}", value = 0)
  @Column(name = "retry_amount", nullable = false)
  public int getRetryAmount() {
    return retryAmount;
  }

  public void setRetryAmount(int retryAmount) {
    this.retryAmount = retryAmount;
  }

  @Min(message = "{result_code_schedule.retry_interval.min}", value = 0)
  @Column(name = "retry_interval", nullable = false)
  public double getRetryInterval() {
    return retryInterval;
  }

  public void setRetryInterval(double retryInterval) {
    this.retryInterval = retryInterval;
  }

  @Column(name = "advanced_interval_enabled", nullable = false)
  public boolean isAdvancedIntervalEnabled() {
    return advancedIntervalEnabled;
  }

  public void setAdvancedIntervalEnabled(boolean advancedIntervalEnabled) {
    this.advancedIntervalEnabled = advancedIntervalEnabled;
  }

  @NotNull(message = "{result_code_schedule.min_schedule_time.empty}")
  @Column(name = "min_schedule_time", nullable = false)
  public Date getMinScheduleTime() {
    return minScheduleTime;
  }

  public void setMinScheduleTime(Date minScheduleTime) {
    this.minScheduleTime = minScheduleTime;
  }

  @NotNull(message = "{result_code_schedule.max_schedule_time.empty}")
  @Column(name = "max_schedule_time", nullable = false)
  public Date getMaxScheduleTime() {
    return maxScheduleTime;
  }

  public void setMaxScheduleTime(Date maxScheduleTime) {
    this.maxScheduleTime = maxScheduleTime;
  }

  @Column(name = "macro_cycle", nullable = false)
  public boolean isMacroCycle() {
    return macroCycle;
  }

  public void setMacroCycle(boolean macroCycle) {
    this.macroCycle = macroCycle;
  }

  @Valid
  @OneToMany(cascade = { CascadeType.ALL }, fetch = FetchType.EAGER, mappedBy = "resultCodeSchedule")
  @Fetch(FetchMode.SUBSELECT)
  public List<ResultCodeScheduleAdvanced> getResultCodeScheduleAdvanceds() {
    return resultCodeScheduleAdvanceds;
  }

  public void setResultCodeScheduleAdvanceds(List<ResultCodeScheduleAdvanced> resultCodeScheduleAdvanceds) {
    this.resultCodeScheduleAdvanceds = resultCodeScheduleAdvanceds;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ResultCodeSchedule)) {
      return false;
    }
    final ResultCodeSchedule obj = (ResultCodeSchedule) o;
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
    return "ResultCodeSchedule [id=" + id + ", resultCode=" + resultCode + ", telephonyResultCode=" + telephonyResultCode + "]";
  }

}
