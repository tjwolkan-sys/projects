package com.powerdialer.model;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.MapKeyJoinColumn;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.annotations.Where;
import org.hibernate.envers.NotAudited;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.powerdialer.model.base.BaseEntityTenant;
import com.powerdialer.serialization.SkillDeserializer;

@Entity
@Table(name = "wbp_skill")
@JsonDeserialize(using = SkillDeserializer.class)
public class Skill extends BaseEntityTenant {

  private static final long serialVersionUID = -7077837083833451527L;
  private Long id;
  private String name;
  private String description;
  private Map<User, Long> usersToWeights;
  private Set<Campaign> campaigns = new HashSet<>();

  public Skill() {}

  public Skill(String name) {
    this.name = name;
  }

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO, generator = "baseEntity_seq")
  @SequenceGenerator(name = "baseEntity_seq", sequenceName = "WBP_SKILL_FCSEQ", allocationSize = 1)
  @Column(name = "id")
  public Long getId() {
    return this.id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  @Column(name = "skill_name")
  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  @Column(name = "skill_description")
  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  @NotAudited
  @ElementCollection(fetch = FetchType.LAZY)
  @MapKeyJoinColumn(name = "user_id")
  @CollectionTable(name = "wbp_skill_user", joinColumns = { @JoinColumn(name = "skill_id") })
  @Column(name = "weight")
  @Fetch(FetchMode.SUBSELECT)
  public Map<User, Long> getUsersToWeights() {
    return usersToWeights;
  }

  public void setUsersToWeights(Map<User, Long> usersToWeights) {
    this.usersToWeights = usersToWeights;
  }

  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(name = "pd_campaign_skill", joinColumns = { @JoinColumn(name = "skill_id") }, inverseJoinColumns = @JoinColumn(name = "campaign_id"))
  @Where(clause = "deleted = 0")
  @Fetch(FetchMode.SUBSELECT)
  public Set<Campaign> getCampaigns() {
    return campaigns;
  }

  public void setCampaigns(Set<Campaign> campaigns) {
    this.campaigns = campaigns;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((name == null) ? 0 : name.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    Skill other = (Skill) obj;
    if (name == null) {
      return other.name == null;
    } else return name.equals(other.name);
  }

  @Override
  public String toString() {
    return "Skill [id=" + id + ", name=" + name + ", description=" + description + "]";
  }

}
