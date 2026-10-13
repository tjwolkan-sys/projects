package com.powerdialer.model;

import java.util.ArrayList;
import java.util.List;

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
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.annotations.Where;

import com.powerdialer.model.base.BaseEntityTenant;

@Entity
@Table(name = "wbp_location")
public class Location extends BaseEntityTenant {

  private static final long serialVersionUID = -5319612306312311233L;
  private Long id;
  private String name;
  private String description;
  private List<Server> servers = new ArrayList<>();
  private List<User> users = new ArrayList<>();
  private List<Campaign> campaigns = new ArrayList<>();

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO, generator = "baseEntity_seq")
  @SequenceGenerator(name = "baseEntity_seq", sequenceName = "WBP_LOCATION_FCSEQ", allocationSize = 1)
  @Column(name = "id")
  public Long getId() {
    return this.id;
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

  @ManyToMany(cascade = { CascadeType.PERSIST }, fetch = FetchType.LAZY)
  @JoinTable(name = "wbp_server_location", joinColumns = { @JoinColumn(name = "location_id") }, inverseJoinColumns = @JoinColumn(name = "server_id"))
  @Where(clause = "deleted = 0 and enabled = 1")
  @Fetch(FetchMode.SUBSELECT)
  public List<Server> getServers() {
    return servers;
  }

  public void setServers(List<Server> servers) {
    this.servers = servers;
  }

  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(name = "wbp_user_location", joinColumns = { @JoinColumn(name = "location_id") }, inverseJoinColumns = @JoinColumn(name = "user_id"))
  @Where(clause = "deleted = 0")
  @Fetch(FetchMode.SUBSELECT)
  public List<User> getUsers() {
    return users;
  }

  public void setUsers(List<User> users) {
    this.users = users;
  }

  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(name = "pd_campaign_location", joinColumns = { @JoinColumn(name = "location_id") }, inverseJoinColumns = @JoinColumn(name = "campaign_id"))
  @Where(clause = "deleted = 0")
  @Fetch(FetchMode.SUBSELECT)
  public List<Campaign> getCampaigns() {
    return campaigns;
  }

  public void setCampaigns(List<Campaign> campaigns) {
    this.campaigns = campaigns;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof Location)) {
      return false;
    }
    final Location obj = (Location) o;
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
    return "Location [id=" + id + ", name=" + name + "]";
  }

}
