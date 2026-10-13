package com.powerdialer.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.powerdialer.model.base.BaseObject;
import com.powerdialer.serialization.ServerDeserializer;
import com.powerdialer.serialization.ServerSerializer;
import com.powerdialer.util.Constants.SERVER_TYPE;
import org.asteriskjava.manager.ManagerConnectionState;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.annotations.Where;
import org.springframework.util.StringUtils;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "wbp_server")
@JsonDeserialize(using = ServerDeserializer.class)
@JsonSerialize(using = ServerSerializer.class)
public class Server extends BaseObject {

  private static final long serialVersionUID = -4547300915228044889L;
  private Long id;
  private String name;
  private String ip;
  private String domain;
  private String properties;
  private int userCount;
  private boolean master;
  private boolean inUse;
  private boolean enabled;
  private boolean deleted;
  private SERVER_TYPE type;
  private ManagerConnectionState connectionState;
  private Integer cpsLimit = 0;
  private Integer maxLines = 0;
  private Map<String, String> propertiesMap;
  private List<Tenant> tenants = new ArrayList<>();
  private List<Location> locations = new ArrayList<>();

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO, generator = "baseEntity_seq")
  @SequenceGenerator(name = "baseEntity_seq", sequenceName = "WBP_SERVER_FCSEQ", allocationSize = 1)
  @Column(name = "id")
  public Long getId() {
    return id;
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

  @Column(name = "ip")
  public String getIp() {
    return ip;
  }

  public void setIp(String ip) {
    this.ip = ip;
  }

  @Column(name = "domain")
  public String getDomain() {
    return domain;
  }

  public void setDomain(String domain) {
    this.domain = domain;
  }

  @Column(name = "properties")
  public String getProperties() {
    return properties;
  }

  public void setProperties(String properties) {
    this.properties = properties;
  }

  @Column(name = "user_count")
  public int getUserCount() {
    return userCount;
  }

  public void setUserCount(int userCount) {
    this.userCount = userCount;
  }

  @Column(name = "master")
  public boolean isMaster() {
    return master;
  }

  public void setMaster(boolean master) {
    this.master = master;
  }

  @Column(name = "in_use")
  public boolean isInUse() {
    return inUse;
  }

  public void setInUse(boolean inUse) {
    this.inUse = inUse;
  }

  @Transient
  public ManagerConnectionState getConnectionState() {
    return connectionState;
  }

  public void setConnectionState(ManagerConnectionState connectionState) {
    this.connectionState = connectionState;
  }

  @Enumerated(EnumType.ORDINAL)
  @Column(name = "type")
  public SERVER_TYPE getType() {
    return type;
  }

  public void setType(SERVER_TYPE type) {
    this.type = type;
  }

  @Column(name = "enabled")
  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  @Column(name = "deleted")
  public boolean isDeleted() {
    return deleted;
  }

  public void setDeleted(boolean deleted) {
    this.deleted = deleted;
  }

  @Column(name = "cps_limit")
  public Integer getCpsLimit() {
    return cpsLimit;
  }

  public void setCpsLimit(Integer cpsLimit) {
    this.cpsLimit = cpsLimit;
  }

  @Column(name = "max_lines")
  public Integer getMaxLines() {
    return maxLines;
  }

  public void setMaxLines(Integer maxLines) {
    this.maxLines = maxLines;
  }

  @Transient
  public String getProperty(String key) {
    if (propertiesMap == null) {
      propertiesMap = new HashMap<>();
      if (getProperties() != null) {
        String[] propertyArr = getProperties().split(";");
        for (String property : propertyArr) {
          String[] propertyKeyValue = property.split("=");
          if (propertyKeyValue.length == 2 && !(StringUtils.isEmpty(propertyKeyValue[0]) && StringUtils.isEmpty(propertyKeyValue[1]))) {
            propertiesMap.put(propertyKeyValue[0], propertyKeyValue[1]);
          }
        }
      }
    }
    return propertiesMap.get(key);
  }

  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(name = "wbp_server_tenant", joinColumns = { @JoinColumn(name = "server_id") }, inverseJoinColumns = @JoinColumn(name = "tenant_id"))
  @Where(clause = "deleted = 0")
  @Fetch(FetchMode.SUBSELECT)
  public List<Tenant> getTenants() {
    return tenants;
  }

  public void setTenants(List<Tenant> tenants) {
    this.tenants = tenants;
  }

  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(name = "wbp_server_location", joinColumns = { @JoinColumn(name = "server_id") }, inverseJoinColumns = @JoinColumn(name = "location_id"))
  @Where(clause = "deleted = 0")
  @Fetch(FetchMode.SUBSELECT)
  public List<Location> getLocations() {
    return locations;
  }

  public void setLocations(List<Location> locations) {
    this.locations = locations;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof Server)) {
      return false;
    }
    final Server obj = (Server) o;
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
    return "Server [id=" + id + ", name=" + name + ", ip=" + ip + ", domain=" + domain + "]";
  }

}
