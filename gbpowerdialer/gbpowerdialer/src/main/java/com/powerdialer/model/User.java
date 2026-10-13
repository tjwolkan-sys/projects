package com.powerdialer.model;

import java.sql.Date;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.persistence.CascadeType;
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
import javax.persistence.ManyToOne;
import javax.persistence.MapKeyJoinColumn;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import javax.persistence.Transient;

import org.apache.commons.collections.CollectionUtils;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.annotations.Where;
import org.hibernate.envers.NotAudited;
import org.springframework.security.core.GrantedAuthority;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.powerdialer.model.base.BaseEntityTenant;
import com.powerdialer.security.GrantedAuthorityWithValue;
import com.powerdialer.serialization.UserDeserializer;
import com.powerdialer.serialization.UserSerializer;

@Entity
@Table(name = "wbp_user")
@JsonSerialize(using = UserSerializer.class)
@JsonDeserialize(using = UserDeserializer.class)
public class User extends BaseEntityTenant {

  private static final long serialVersionUID = -7442803858304807471L;
  private Long id;
  private String username;
  private String password;
  private String firstname;
  private String lastname;
  private Long sessionId;
  private Set<Role> roles = new HashSet<>();
  private Group group;
  private Map<Skill, Long> skillWeightPair = new HashMap<>();
  private Status status;
  private Server server;
  private String adNickname;
  private int ldapAuth;
  private Set<PermissionProfile> permissionProfiles = new HashSet<>();
  private Long teamId;
  private Date statusSetDate;
  private boolean changePassword;
  private Set<Tenant> tenants = new HashSet<>();
  private boolean dialInProcess;

  @Column(name = "ad_nickname")
  public String getAdNickname() {
    return adNickname;
  }

  public void setAdNickname(String adNickname) {
    this.adNickname = adNickname;
  }

  @Column(name = "ldap_auth")
  public int getLdapAuth() {
    return ldapAuth;
  }

  public void setLdapAuth(int ldapAuth) {
    this.ldapAuth = ldapAuth;
  }

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO, generator = "baseEntity_seq")
  @SequenceGenerator(name = "baseEntity_seq", sequenceName = "WBP_USER_FCSEQ", allocationSize = 1)
  @Column(name = "id")
  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  @Column(name = "username")
  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  @Column(name = "password")
  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  @Column(name = "firstname")
  public String getFirstname() {
    return firstname;
  }

  public void setFirstname(String firstname) {
    this.firstname = firstname;
  }

  public String getLastname() {
    return lastname;
  }

  @Column(name = "lastname")
  public void setLastname(String lastname) {
    this.lastname = lastname;
  }

  @Transient
  public Long getSessionId() {
    return sessionId;
  }

  public void setSessionId(Long sessionId) {
    this.sessionId = sessionId;
  }

  @Transient
  public String getFullname() {
    return firstname + " " + lastname;
  }

  @ManyToMany(fetch = FetchType.EAGER)
  @JoinTable(name = "wbp_user_role", joinColumns = { @JoinColumn(name = "user_id") }, inverseJoinColumns = @JoinColumn(name = "role_id"))
  @Fetch(FetchMode.SUBSELECT)
  public Set<Role> getRoles() {
    return roles;
  }

  public void setRoles(Set<Role> roles) {
    this.roles = roles;
  }

  @ManyToOne(cascade = { CascadeType.PERSIST }, fetch = FetchType.LAZY)
  @JoinColumn(name = "group_id")
  public Group getGroup() {
    return group;
  }

  public void setGroup(Group group) {
    this.group = group;
  }

  @ElementCollection(fetch = FetchType.LAZY)
  @MapKeyJoinColumn(name = "skill_id")
  @CollectionTable(name = "wbp_skill_user", joinColumns = { @JoinColumn(name = "user_id") })
  @Column(name = "weight")
  @Fetch(FetchMode.SUBSELECT)
  public Map<Skill, Long> getSkillWeightPair() {
    return skillWeightPair;
  }

  public void setSkillWeightPair(Map<Skill, Long> skillWeightPair) {
    this.skillWeightPair = skillWeightPair;
  }

  @NotAudited
  @ManyToMany(fetch = FetchType.EAGER)
  @JoinTable(name = "wbp_permission_profile_user", joinColumns = { @JoinColumn(name = "user_id") }, inverseJoinColumns = @JoinColumn(name = "permission_profile_id"))
  @Fetch(FetchMode.SUBSELECT)
  public Set<PermissionProfile> getPermissionProfiles() {
    return permissionProfiles;
  }

  public void setPermissionProfiles(Set<PermissionProfile> permissionProfiles) {
    this.permissionProfiles = permissionProfiles;
  }

  @Column(name = "team_id")
  public Long getTeamId() {
    return teamId;
  }

  public void setTeamId(Long teamId) {
    this.teamId = teamId;
  }

  @Transient
  public Status getStatus() {
    return status;
  }

  public void setStatus(Status status) {
    this.status = status;
  }

  @Transient
  public Server getServer() {
    return server;
  }

  public void setServer(Server server) {
    this.server = server;
  }

  @Transient
  @JsonIgnore
  public Set<GrantedAuthority> getAuthorities() {
    Set<GrantedAuthority> authorities = new LinkedHashSet<>(roles);
    if (!CollectionUtils.isEmpty(getPermissionProfiles()))
      getPermissionProfiles().stream().map(PermissionProfile::getPermissions).filter(CollectionUtils::isNotEmpty).flatMap(Collection::stream)
          .map(GrantedAuthorityWithValue::fromPermission).forEach(authorities::add);
    return authorities;
  }

  @Transient
  public List<String> getRolesNames() {
    List<String> names = new ArrayList<>();
    for (Role role : roles) {
      names.add(role.getRoleName());
    }
    return names;
  }

  @Transient
  public Date getStatusSetDate() {
    return statusSetDate;
  }

  public void setStatusSetDate(Date statusSetDate) {
    this.statusSetDate = statusSetDate;
  }

  public Integer calculateTimeInStatus() {
    int difference = (int) (Calendar.getInstance().getTime().getTime() - getStatusSetDate().getTime());
    return (difference / 1000);
  }

  @Column(name = "change_password")
  public Boolean isChangePassword() {
    return changePassword;
  }

  public void setChangePassword(Boolean changePassword) {
    this.changePassword = changePassword;
  }

  @ManyToMany(fetch = FetchType.LAZY)
  @NotAudited
  @JoinTable(name = "wbp_user_tenant", joinColumns = { @JoinColumn(name = "user_id") }, inverseJoinColumns = @JoinColumn(name = "tenant_id", nullable = false))
  @Where(clause = "deleted = 0")
  @Fetch(FetchMode.SUBSELECT)
  public Set<Tenant> getTenants() {
    return tenants;
  }

  public void setTenants(Set<Tenant> tenants) {
    this.tenants = tenants;
  }

  @Transient
  @JsonIgnore
  public boolean isDialInProcess() {
    return dialInProcess;
  }

  public void setDialInProcess(boolean dialInProcess) {
    this.dialInProcess = dialInProcess;
  }

  @Transient
  public long getSummarySkillWeight(Collection<Skill> skills) {
    return getSkillWeightPair().entrySet().stream()
        .filter(e -> skills.contains(e.getKey()))
        .map(Map.Entry::getValue)
        .filter(Objects::nonNull)
        .mapToLong(Long::longValue)
        .sum();
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((id == null) ? 0 : id.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    User other = (User) obj;
    if (id == null) {
      return other.id == null;
    } else return id.equals(other.id);
  }

  @Override
  public String toString() {
    return "User [id=" + id + ", username=" + username + ", firstname=" + firstname + ", lastname=" + lastname + ", status=" + status + "]";
  }

}
