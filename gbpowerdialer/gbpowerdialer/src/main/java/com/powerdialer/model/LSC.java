package com.powerdialer.model;

import java.util.Set;

import javax.persistence.*;
import javax.validation.constraints.NotNull;

import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.annotations.Where;
import org.hibernate.validator.constraints.NotBlank;

import com.powerdialer.model.base.BaseEntityTenant;
import com.powerdialer.util.Constants.HTTP_METHOD;
import com.powerdialer.util.Constants.REPLY_TYPE;

@Entity
@Table(name = "pd_lsc_service")
@DynamicUpdate
public class LSC extends BaseEntityTenant {

  private static final long serialVersionUID = 5322873658509231460L;
  private Long id;
  private String name;
  private String description;
  private String url;
  private HTTP_METHOD httpMethod;
  private String payload;
  private String headers;
  private REPLY_TYPE replyType;
  private String replyKey;
  private String replyResult;
  private String responseKeys;
  private Integer requestTimeout;
  private boolean global;
  private Set<Campaign> campaigns;
  private boolean freePayloadEnabled;
  private boolean tokenRequired;
  private LSCAuthorization authorization;
  private boolean startsWithZero;

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO, generator = "baseEntity_seq")
  @SequenceGenerator(name = "baseEntity_seq", sequenceName = "PD_LSC_SERVICE_FCSEQ", allocationSize = 1)
  @Column(name = "id")
  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  @NotBlank(message = "{lsc.name.blank}")
  @Column(name = "name", nullable = false, columnDefinition = "NVARCHAR(255)")
  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  @Column(name = "description", columnDefinition = "NVARCHAR(255)")
  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  @NotBlank(message = "{lsc.url.blank}")
  @Column(name = "url", nullable = false, columnDefinition = "NVARCHAR(4000)")
  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  @NotNull(message = "{lsc.http_method.empty}")
  @Enumerated(EnumType.STRING)
  @Column(name = "http_method", nullable = false)
  public HTTP_METHOD getHttpMethod() {
    return httpMethod;
  }

  public void setHttpMethod(HTTP_METHOD httpMethod) {
    this.httpMethod = httpMethod;
  }

  @Column(name = "payload", columnDefinition = "NVARCHAR(4000)")
  public String getPayload() {
    return payload;
  }

  public void setPayload(String payload) {
    this.payload = payload;
  }

  @Column(name = "headers")
  public String getHeaders() {
    return headers;
  }

  public void setHeaders(String headers) {
    this.headers = headers;
  }

  @NotNull(message = "{lsc.reply_type.empty}")
  @Enumerated(EnumType.ORDINAL)
  @Column(name = "reply_type", nullable = false)
  public REPLY_TYPE getReplyType() {
    return replyType;
  }

  public void setReplyType(REPLY_TYPE replyType) {
    this.replyType = replyType;
  }

  @NotBlank(message = "{lsc.reply_key.blank}")
  @Column(name = "reply_key", columnDefinition = "NVARCHAR(255)")
  public String getReplyKey() {
    return replyKey;
  }

  public void setReplyKey(String replyKey) {
    this.replyKey = replyKey;
  }

  @NotBlank(message = "{lsc.reply_result.blank}")
  @Column(name = "reply_result", nullable = false, columnDefinition = "NVARCHAR(255)")
  public String getReplyResult() {
    return replyResult;
  }

  public void setReplyResult(String replyResult) {
    this.replyResult = replyResult;
  }

  @Column(name = "request_timeout")
  public Integer getRequestTimeout() {
    return requestTimeout;
  }

  public void setRequestTimeout(Integer requestTimeout) {
    this.requestTimeout = requestTimeout;
  }

  @Column(name = "global", nullable = false)
  public boolean getGlobal() {
    return global;
  }

  public void setGlobal(boolean global) {
    this.global = global;
  }

  @ManyToMany(fetch = FetchType.LAZY, mappedBy = "lscs")
  @Where(clause = "deleted = 0")
  @Fetch(FetchMode.SUBSELECT)
  public Set<Campaign> getCampaigns() {
    return campaigns;
  }

  public void setCampaigns(Set<Campaign> campaigns) {
    this.campaigns = campaigns;
  }

  @Column(name = "free_payload_enabled", nullable = false)
  public boolean isFreePayloadEnabled() {
    return freePayloadEnabled;
  }

  public void setFreePayloadEnabled(boolean freePayloadEnabled) {
    this.freePayloadEnabled = freePayloadEnabled;
  }

  @OneToOne()
  @JoinColumn(name = "lsc_authorization_id")
  public LSCAuthorization getAuthorization() {
    return authorization;
  }

  public void setAuthorization(LSCAuthorization authorization) {
    this.authorization = authorization;
  }

  @Column(name = "token_required", nullable = false, columnDefinition = "BIT DEFAULT 0")
  public boolean isTokenRequired() {
    return tokenRequired;
  }

  public void setTokenRequired(boolean tokenRequired) {
    this.tokenRequired = tokenRequired;
  }

  @Column(name = "starts_with_zero", nullable = false, columnDefinition = "BIT DEFAULT 0")
  public boolean isStartsWithZero() {
    return startsWithZero;
  }

  public void setStartsWithZero(boolean startsWithZero) {
    this.startsWithZero = startsWithZero;
  }

  @Column(name = "response_keys", columnDefinition = "NVARCHAR(4000)")
  public String getResponseKeys() {
    return responseKeys;
  }

  public void setResponseKeys(String responseKeys) {
    this.responseKeys = responseKeys;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof LSC)) {
      return false;
    }
    final LSC obj = (LSC) o;
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
    return "LSC [id=" + id + ", name=" + name + ", url=" + url + "]";
  }

}
