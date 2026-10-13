package com.powerdialer.dto;

import com.powerdialer.util.Constants;

import java.io.Serializable;
import java.sql.Date;


public class JobConfigFilterDTO  implements Serializable {
    private Long jobConfigId;
    private Long id;
    private String filterField;
    private Constants.FILTER_CONDITION operationType;
    private String filterValue;
    private Date filterDateValue;
    private Integer expireThresholdDays;
    private Constants.FILTER_TYPE filterType;
    private String freeQueryTxt;

    public JobConfigFilterDTO(Long jobConfigId,
                              Long id,
                              String filterField,
                              Constants.FILTER_CONDITION operationType,
                              String filterValue,
                              java.util.Date filterDateValue,
                              Integer expireThresholdDays,
                              Constants.FILTER_TYPE filterType,
                              String freeQueryTxt) {
        this.jobConfigId = jobConfigId;
        this.id = id;
        this.filterField = filterField;
        this.operationType = operationType;
        this.filterValue = filterValue;
        this.filterDateValue = (filterDateValue == null) ? null : new java.sql.Date(filterDateValue.getTime());
        this.expireThresholdDays = expireThresholdDays;
        this.filterType = filterType;
        this.freeQueryTxt = freeQueryTxt;
    }



    private static Date dateValOrNull(Date d){ return d == null ? null : new Date(d.getTime()); }

    public Long getJobConfigId() {
        return jobConfigId;
    }

    public Long getId() {
        return id;
    }

    public String getFilterField() {
        return filterField;
    }

    public Constants.FILTER_CONDITION getOperationType() {
        return operationType;
    }

    public String getFilterValue() {
        return filterValue;
    }

    public Date getFilterDateValue() {
        return filterDateValue;
    }

    public Integer getExpireThresholdDays() {
        return expireThresholdDays;
    }

    public Constants.FILTER_TYPE getFilterType() {
        return filterType;
    }

    public String getFreeQueryTxt() {
        return freeQueryTxt;
    }

}
