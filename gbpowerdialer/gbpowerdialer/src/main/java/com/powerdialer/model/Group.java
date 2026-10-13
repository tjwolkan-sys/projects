package com.powerdialer.model;

import java.util.ArrayList;
import java.util.HashSet;
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
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.OneToMany;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import javax.persistence.Transient;

import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.annotations.Where;
import org.hibernate.annotations.WhereJoinTable;
import org.hibernate.envers.NotAudited;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.powerdialer.model.base.BaseEntityTenant;
import com.powerdialer.serialization.GroupDeserializer;
import com.powerdialer.serialization.GroupSerializer;

@Entity
@Table(name = "wbp_group")
@JsonSerialize(using = GroupSerializer.class)
@JsonDeserialize(using = GroupDeserializer.class)
public class Group extends BaseEntityTenant {

  private static final long serialVersionUID = -2237526151218578392L;
  private Long id;
  private String name;
  private String description;
  private Integer wrapupTimerSeconds;
  private List<ResultCode> resultCodes = new ArrayList<>();
  private List<CampaignGroup> campaignGroups = new ArrayList<>();
  private Set<Queue> queues = new HashSet<>();

  public Group() {

  }

  public Group(Long id) {
    this.id = id;
  }

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO, generator = "baseEntity_seq")
  @SequenceGenerator(name = "baseEntity_seq", sequenceName = "WBP_GROUP_FCSEQ", allocationSize = 1)
  @Column(name = "id")
  public Long getId() {
    return this.id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  @Column(name = "group_name")
  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  @Column(name = "group_description")
  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  /**
   * Webphone grup ayarı "Wrapup timer" (saniye). Hibernate kolonu değildir; Redis user JSON'undaki
   * {@code groupPropertyXML}'den {@link com.powerdialer.serialization.GroupDeserializer} doldurur,
   * DB'den yüklenen Group'ta null kalır.
   */
  @Transient
  public Integer getWrapupTimerSeconds() {
    return wrapupTimerSeconds;
  }

  public void setWrapupTimerSeconds(Integer wrapupTimerSeconds) {
    this.wrapupTimerSeconds = wrapupTimerSeconds;
  }

  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(name = "wbp_result_code_group", joinColumns = { @JoinColumn(name = "group_id") }, inverseJoinColumns = @JoinColumn(name = "result_code_id"))
  @WhereJoinTable(clause = "deleted = 0")
  @Fetch(FetchMode.SUBSELECT)
  @NotAudited
  public List<ResultCode> getResultCodes() {
    return resultCodes;
  }

  public void setResultCodes(List<ResultCode> resultCodes) {
    this.resultCodes = resultCodes;
  }

  @OneToMany(cascade = { CascadeType.ALL }, fetch = FetchType.LAZY, mappedBy = "group")
  @Fetch(FetchMode.SUBSELECT)
  public List<CampaignGroup> getCampaignGroups() {
    return campaignGroups;
  }

  public void setCampaignGroups(List<CampaignGroup> campaignGroups) {
    this.campaignGroups = campaignGroups;
  }

  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(name = "wbp_queue_group", joinColumns = { @JoinColumn(name = "group_id") }, inverseJoinColumns = @JoinColumn(name = "queue_id"))
  @Where(clause = "deleted = 0")
  @Fetch(FetchMode.SUBSELECT)
  public Set<Queue> getQueues() {
    return queues;
  }

  public void setQueues(Set<Queue> queues) {
    this.queues = queues;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof Group)) {
      return false;
    }
    final Group obj = (Group) o;
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
    return "Group [id=" + id + ", name=" + name + ", description=" + description + "]";
  }

}
