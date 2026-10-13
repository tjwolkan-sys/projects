package com.powerdialer.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

import com.powerdialer.model.base.BaseEntity;

@Entity
@Table(name = "wbp_tenant")
public class Tenant extends BaseEntity {

  private static final long serialVersionUID = -7685036458365702659L;
  private Long id;
  private String name;
  private String description;
  private String email;
  private boolean ldapAuth;
  private boolean ldapSSL;
  private String ldapServerIp;
  private String ldapFailoverServerIp;
  private String ldapSuffix;

  @Column(name = "ldap_auth")
  public boolean isLdapAuth() {
    return ldapAuth;
  }

  public void setLdapAuth(boolean ldapLoginEnabled) {
    this.ldapAuth = ldapLoginEnabled;
  }

  @Column(name = "ldap_ssl")
  public boolean isLdapSSL() {
    return ldapSSL;
  }

  public void setLdapSSL(boolean ldapSSL) {
    this.ldapSSL = ldapSSL;
  }

  @Column(name = "ldap_server_ip")
  public String getLdapServerIp() {
    return ldapServerIp;
  }

  public void setLdapServerIp(String ldapServerIp) {
    this.ldapServerIp = ldapServerIp;
  }

  @Column(name = "ldap_failover_server_ip")
  public String getLdapFailoverServerIp() {
    return ldapFailoverServerIp;
  }

  public void setLdapFailoverServerIp(String ldapFailoverServerIp) {
    this.ldapFailoverServerIp = ldapFailoverServerIp;
  }

  @Column(name = "ldap_suffix")
  public String getLdapSuffix() {
    return ldapSuffix;
  }

  public void setLdapSuffix(String ldapSuffix) {
    this.ldapSuffix = ldapSuffix;
  }

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO, generator = "baseEntity_seq")
  @SequenceGenerator(name = "baseEntity_seq", sequenceName = "WBP_TENANT_FCSEQ", allocationSize = 1)
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

  @Column(name = "email")
  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof Tenant)) {
      return false;
    }
    final Tenant obj = (Tenant) o;
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
    return "Tenant [id=" + id + ", name=" + name + "]";
  }

}
