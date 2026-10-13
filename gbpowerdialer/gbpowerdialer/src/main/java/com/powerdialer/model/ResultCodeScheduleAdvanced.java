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
import javax.validation.Valid;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

import org.hibernate.annotations.DynamicUpdate;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.powerdialer.model.base.BaseEntity;
import com.powerdialer.util.Constants.NUMBER_TO_TRY;

@Entity
@Table(name = "pd_rc_schedule_advanced")
@DynamicUpdate
public class ResultCodeScheduleAdvanced extends BaseEntity {

  private static final long serialVersionUID = -8076391928960193405L;
  private Long id;
  private ResultCodeSchedule resultCodeSchedule;
  private int dialAttempt;
  private double retryInterval;
  private NUMBER_TO_TRY numberToTry;

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO, generator = "baseEntity_seq")
  @SequenceGenerator(name = "baseEntity_seq", sequenceName = "PD_RC_SCHEDULE_ADVANCED_FCSEQ", allocationSize = 1)
  @Column(name = "id")
  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  @Valid
  @JsonIgnore
  @ManyToOne(cascade = { CascadeType.PERSIST }, fetch = FetchType.LAZY)
  @JoinColumn(name = "rc_schedule_id", nullable = false)
  public ResultCodeSchedule getResultCodeSchedule() {
    return resultCodeSchedule;
  }

  public void setResultCodeSchedule(ResultCodeSchedule resultCodeSchedule) {
    this.resultCodeSchedule = resultCodeSchedule;
  }

  @Min(message = "{result_code_schedule_advanced.dial_attempt.min}", value = 0)
  @Column(name = "dial_attempt", nullable = false)
  public int getDialAttempt() {
    return dialAttempt;
  }

  public void setDialAttempt(int dialAttempt) {
    this.dialAttempt = dialAttempt;
  }

  @Min(message = "{result_code_schedule_advanced.retry_interval.min}", value = 0)
  @Column(name = "retry_interval", nullable = false)
  public double getRetryInterval() {
    return retryInterval;
  }

  public void setRetryInterval(double retryInterval) {
    this.retryInterval = retryInterval;
  }

  @NotNull(message = "{result_code_schedule_advanced.number_to_try.empty}")
  @Enumerated(EnumType.ORDINAL)
  @Column(name = "number_to_try", nullable = false)
  public NUMBER_TO_TRY getNumberToTry() {
    return numberToTry;
  }

  public void setNumberToTry(NUMBER_TO_TRY numberToTry) {
    this.numberToTry = numberToTry;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ResultCodeScheduleAdvanced)) {
      return false;
    }
    final ResultCodeScheduleAdvanced obj = (ResultCodeScheduleAdvanced) o;
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
    return "ResultCodeScheduleAdvanced [id=" + id + ", dialAttempt=" + dialAttempt + ", retryInterval=" + retryInterval + ", numberToTry=" + numberToTry + "]";
  }

}
