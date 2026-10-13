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
import javax.persistence.OneToMany;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.annotations.Where;

import com.powerdialer.model.base.BaseObject;

@Entity
@Table(name = "wbp_permission_profile")
public class PermissionProfile extends BaseObject {

  private static final long serialVersionUID = 1710045935082627149L;
  private Long id;
  private String name;
  private String description;
  private List<Permission> permissions = new ArrayList<>();

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO, generator = "baseEntity_seq")
  @SequenceGenerator(name = "baseEntity_seq", sequenceName = "WBP_PERMISSION_PROFILE_FCSEQ", allocationSize = 1)
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

  @Where(clause = "permission_type = 'dialer'")
  @OneToMany(cascade = { CascadeType.ALL }, fetch = FetchType.EAGER, mappedBy = "permissionProfile")
  @Fetch(FetchMode.SUBSELECT)
  public List<Permission> getPermissions() {
    return permissions;
  }

  public void setPermissions(List<Permission> permissions) {
    this.permissions = permissions;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof PermissionProfile)) {
      return false;
    }
    final PermissionProfile obj = (PermissionProfile) o;

    return generateEquals(getId(), obj.getId());
  }

  @Override
  public int hashCode() {
    return generateHash(31, 42, getId());
  }

  @Override
  public String toString() {
    return "PermissionProfile [id=" + id + ", name=" + name + ", description=" + description + "]";
  }

}
