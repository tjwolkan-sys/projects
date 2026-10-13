package com.powerdialer.model;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

import com.powerdialer.model.base.BaseObject;

@Entity
@Table(name = "wbp_permission")
public class Permission extends BaseObject {

  private static final long serialVersionUID = -7222189540533383437L;
  private Long id;
  private PermissionProfile permissionProfile;
  private String permissionType;
  private String permissionKey;
  private String permissionValue;
  private String permissionStringValue;
  private Tenant tenant;

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO, generator = "baseEntity_seq")
  @SequenceGenerator(name = "baseEntity_seq", sequenceName = "WBP_PERMISSION_FCSEQ", allocationSize = 1)
  @Column(name = "id")
  public Long getId() {
    return this.id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  @Column(name = "permission_value_label")
  public String getPermissionStringValue() {
    return permissionStringValue;
  }

  public void setPermissionStringValue(String permissionStringValue) {
    this.permissionStringValue = permissionStringValue;
  }

  @ManyToOne(cascade = { CascadeType.PERSIST }, fetch = FetchType.EAGER)
  @JoinColumn(name = "tenant_id")
  public Tenant getTenant() {
    return tenant;
  }

  public void setTenant(Tenant tenant) {
    this.tenant = tenant;
  }

  @ManyToOne(cascade = { CascadeType.PERSIST }, fetch = FetchType.LAZY)
  @JoinColumn(name = "permission_profile_id")
  public PermissionProfile getPermissionProfile() {
    return permissionProfile;
  }

  public void setPermissionProfile(PermissionProfile permissionProfile) {
    this.permissionProfile = permissionProfile;
  }

  @Column(name = "permission_type")
  public String getPermissionType() {
    return permissionType;
  }

  public void setPermissionType(String permissionType) {
    this.permissionType = permissionType;
  }

  @Column(name = "permission_key")
  public String getPermissionKey() {
    return permissionKey;
  }

  public void setPermissionKey(String permissionKey) {
    this.permissionKey = permissionKey;
  }

  @Column(name = "permission_value")
  public String getPermissionValue() {
    return permissionValue;
  }

  public void setPermissionValue(String permissionValue) {
    this.permissionValue = permissionValue;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof Permission)) {
      return false;
    }
    final Permission obj = (Permission) o;

    return generateEquals(getId(), obj.getId());
  }

  @Override
  public int hashCode() {
    return generateHash(31, 42, getId());
  }

  @Override
  public String toString() {
    return "Permission [id=" + id + ", permissionType=" + permissionType + ", permissionKey=" + permissionKey + ", permissionValue=" + permissionValue + "]";
  }

}
