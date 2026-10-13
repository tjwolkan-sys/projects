package com.powerdialer.model;

import com.powerdialer.util.Constants;
import com.powerdialer.util.Constants.RequestBodyType;
import org.hibernate.annotations.DynamicUpdate;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import java.io.Serializable;
import java.util.Date;
import java.util.Objects;

@Entity
@Table(name = "pd_lsc_authorization")
@DynamicUpdate
public class LSCAuthorization implements Serializable {
    private static final long serialVersionUID = -3871370851697868835L;

    private Long id;
    private String url;
    private String method;
    private RequestBodyType bodyType;
    private String headers;
    private String body;
    private String response;
    private String token;
    private String tokenHeaderName;
    private String responseKey;
    private Constants.REPLY_TYPE responseType;
    private Date updateDate;
    private Date createDate;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO, generator = "baseEntity_seq")
    @SequenceGenerator(name = "baseEntity_seq", sequenceName = "PD_LSC_AUTH_FCSEQ", allocationSize = 1)
    @Column(name = "id")
    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }

    @Column(name = "url", nullable = false, columnDefinition = "NVARCHAR(4000)")
    public String getUrl() {
        return url;
    }
    public void setUrl(String url) {
        this.url = url;
    }

    @Column(name = "method", nullable = false, columnDefinition = "NVARCHAR(50)")
    public String getMethod() {
        return method;
    }
    public void setMethod(String method) {
        this.method = method;
    }

    @Enumerated(EnumType.STRING)
    @Column(name = "body_type", nullable = false, columnDefinition = "NVARCHAR(50)")
    public RequestBodyType getBodyType() {
        return bodyType;
    }
    public void setBodyType(RequestBodyType bodyType) {
        this.bodyType = bodyType;
    }

    @Column(name = "headers", columnDefinition = "NVARCHAR(4000)")
    public String getHeaders() {
        return headers;
    }

    public void setHeaders(String headers) {
        this.headers = headers;
    }


    @Column(name = "body", columnDefinition = "NVARCHAR(4000)")
    public String getBody() {
        return body;
    }
    public void setBody(String body) {
        this.body = body;
    }

    @Column(name = "response", nullable = true, columnDefinition = "NVARCHAR(4000)")
    public String getResponse() {
        return response;
    }

    public void setResponse(String response) {
        this.response = response;
    }

    @Column(name = "token", nullable = true, columnDefinition = "NVARCHAR(4000)")
    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    @Column(name = "token_header_name", nullable = true, columnDefinition = "NVARCHAR(4000)")
    public String getTokenHeaderName() {
        return tokenHeaderName;
    }

    public void setTokenHeaderName(String tokenHeaderName) {
        this.tokenHeaderName = tokenHeaderName;
    }

    @Column(name = "update_date")
    public Date getUpdateDate() {
        return updateDate;
    }

    public void setUpdateDate(Date updateDate) {
        this.updateDate = updateDate;
    }

    @Column(name = "create_date")
    public Date getCreateDate() {
        return createDate;
    }

    public void setCreateDate(Date createDate) {
        this.createDate = createDate;
    }

    @Column(name = "response_key", columnDefinition = "NVARCHAR(4000)")
    public String getResponseKey() {
        return responseKey;
    }

    public void setResponseKey(String responseKey) {
        this.responseKey = responseKey;
    }

    @Enumerated(EnumType.STRING)
    @Column(name = "response_type", columnDefinition = "NVARCHAR(50)")
    public Constants.REPLY_TYPE getResponseType() {
        return responseType;
    }

    public void setResponseType(Constants.REPLY_TYPE responseType) {
        this.responseType = responseType;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        LSCAuthorization that = (LSCAuthorization) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(url, that.url) &&
                Objects.equals(method, that.method) && bodyType == that.bodyType &&
                Objects.equals(headers, that.headers) &&
                Objects.equals(body, that.body) &&
                Objects.equals(response, that.response) &&
                Objects.equals(token, that.token) &&
                Objects.equals(tokenHeaderName, that.tokenHeaderName) &&
                Objects.equals(updateDate, that.updateDate) &&
                Objects.equals(createDate, that.createDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, url, method, bodyType, headers, body, response, token, tokenHeaderName, updateDate, createDate);
    }

    @Override
    public String toString() {
        return "LSCAuthorization{" +
                "id=" + id +
                ", url='" + url + '\'' +
                ", method='" + method + '\'' +
                ", bodyType=" + bodyType +
                ", headers='" + headers + '\'' +
                ", body='" + body + '\'' +
                ", response='" + response + '\'' +
                ", token='" + token + '\'' +
                ", tokenHeaderName='" + tokenHeaderName + '\'' +
                ", updateDate=" + updateDate +
                ", createDate=" + createDate +
                '}';
    }
}
