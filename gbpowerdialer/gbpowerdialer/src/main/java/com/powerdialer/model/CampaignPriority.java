package com.powerdialer.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.powerdialer.engine.dialer.CampaignStopJob;
import com.powerdialer.model.base.BaseEntity;
import com.powerdialer.model.base.BaseEntityTenant;
import com.powerdialer.serialization.CampaignSerializer;
import com.powerdialer.util.Constants;
import com.powerdialer.util.Constants.*;
import org.apache.commons.lang3.SerializationUtils;
import org.hibernate.annotations.*;
import org.hibernate.validator.constraints.NotBlank;
import org.hibernate.validator.constraints.Range;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

import javax.persistence.*;
import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.Table;
import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Entity
@Table(name = "pd_campaign_priority")
@DynamicUpdate
public class CampaignPriority extends BaseEntity {

  private static final long serialVersionUID = -4832375275243669237L;
  private Long id;
  private String campaignName;
  private List<CampaignPriorityFilter> campaignPriorityFilters;

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO, generator = "baseEntity_seq")
  @SequenceGenerator(name = "baseEntity_seq", sequenceName = "PD_CAMPAIGN_PRIORITY_FCSEQ", allocationSize = 1)
  @Column(name = "id")
  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  @Column(name = "campaign_name", nullable = false, columnDefinition = "NVARCHAR(255)")
  public String getCampaignName() {
    return campaignName;
  }

  public void setCampaignName(String campaignName) {
    this.campaignName = campaignName;
  }

  @OneToMany(cascade = { CascadeType.ALL }, fetch = FetchType.LAZY, mappedBy = "campaignPriority")
  @Fetch(FetchMode.SUBSELECT)
  @Where(clause = "deleted = 0")
  public List<CampaignPriorityFilter> getCampaignPriorityFilters() {
    return campaignPriorityFilters;
  }

  public void setCampaignPriorityFilters(List<CampaignPriorityFilter> campaignPriorityFilters) {
    this.campaignPriorityFilters = campaignPriorityFilters;
  }

  @Override
  public String toString() {
    return "CampaignPriority{" +
            "id=" + id +
            ", campaignName='" + campaignName + '\'' +
            '}';
  }

  @Override
  public boolean equals(Object o) {
    return false;
  }

  @Override
  public int hashCode() {
    return 0;
  }
}
