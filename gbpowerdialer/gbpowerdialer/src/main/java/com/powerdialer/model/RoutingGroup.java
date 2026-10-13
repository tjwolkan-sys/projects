package com.powerdialer.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.validation.constraints.Size;

import org.hibernate.annotations.Nationalized;
import org.hibernate.validator.constraints.NotBlank;

import com.fasterxml.jackson.annotation.JsonIgnore;

import com.powerdialer.model.base.BaseEntity;
import com.powerdialer.util.Constants;

@Entity
@Table(name = "qss_routing_group")
public class RoutingGroup extends BaseEntity {

  private Long id;
  private String name;

  @JsonIgnore
  private Constants.ROUTING_GROUP_TYPE type;

  @Id
  @Column(name = "id", nullable = false)
  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  @Nationalized
  @NotBlank
  @Size(max = 255)
  @Column(name = "name", nullable = false)
  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  @Column(name = "interaction_type", nullable = false)
  public Constants.ROUTING_GROUP_TYPE getType() {
    return type;
  }

  public void setType(Constants.ROUTING_GROUP_TYPE interactionType) {
    this.type = interactionType;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((getId() == null) ? 0 : getId().hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (!(obj instanceof RoutingGroup))
      return false;
    RoutingGroup other = (RoutingGroup) obj;
    if (getId() == null) {
      return other.getId() == null;
    } else return getId().equals(other.getId());
  }

  @Override
  public String toString() {
    return "RoutingGroup [id=" + id
        + ", name=" + name
        + "]";
  }

}