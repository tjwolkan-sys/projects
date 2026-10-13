package com.powerdialer.model;

import com.powerdialer.model.base.BaseEntity;
import com.powerdialer.util.Constants;
import org.hibernate.annotations.DynamicUpdate;

import javax.persistence.*;
import javax.validation.constraints.Size;
import java.util.Date;

@Entity
@Table(name = "pd_job_config_filter")
@DynamicUpdate
public class JobConfigFilter extends BaseEntity {

    private Long id;
    private JobConfig jobConfig;

    private Constants.FILTER_TYPE filterType;
    private String filterField;
    private Integer expireThresholdDays;
    private String filterValue;
    private Date filterDateValue;
    private Constants.FILTER_CONDITION operationType;
    private String freeQueryText;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "baseEntity_seq")
    @SequenceGenerator(name = "baseEntity_seq", sequenceName = "PD_JOBCONFIGFILTER_FCSEQ", allocationSize = 1)
    @Column(name = "id")
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_config_id", nullable = false)
    public JobConfig getJobConfig() { return jobConfig; }
    public void setJobConfig(JobConfig jobConfig) { this.jobConfig = jobConfig; }

    @Enumerated(EnumType.ORDINAL)
    @Column(name = "filter_type", nullable = false)
    public Constants.FILTER_TYPE getFilterType() { return filterType; }
    public void setFilterType(Constants.FILTER_TYPE filterType) { this.filterType = filterType; }

    @Size(max = 100)
    @Column(name = "filter_field", nullable = false, length = 100)
    public String getFilterField() { return filterField; }
    public void setFilterField(String filterField) { this.filterField = filterField; }

    @Column(name = "expire_threshold_days")
    public Integer getExpireThresholdDays() { return expireThresholdDays; }
    public void setExpireThresholdDays(Integer expireThresholdDays) { this.expireThresholdDays = expireThresholdDays; }

    @Column(name = "filter_value")
    public String getFilterValue() { return filterValue; }
    public void setFilterValue(String filterValue) { this.filterValue = filterValue; }

    @Column(name = "filter_date_value")
    public Date getFilterDateValue() { return filterDateValue; }
    public void setFilterDateValue(Date filterDateValue) { this.filterDateValue = filterDateValue; }

    @Enumerated(EnumType.ORDINAL)
    @Column(name = "operation_type")
    public Constants.FILTER_CONDITION getOperationType() { return operationType; }
    public void setOperationType(Constants.FILTER_CONDITION operationType) { this.operationType = operationType; }

    @Column(name = "free_query_text")
    public String getFreeQueryText() { return freeQueryText; }
    public void setFreeQueryText(String freeQueryText) { this.freeQueryText = freeQueryText; }

    @Override public int hashCode(){ return generateHash(31,42,getId()); }
    @Override public boolean equals(Object o){
        if (this==o) return true;
        if (!(o instanceof JobConfigFilter)) return false;
        JobConfigFilter other=(JobConfigFilter)o;
        if (getId()==null && other.getId()==null) return false;
        return generateEquals(getId(), other.getId());
    }
    @Override public String toString(){
        return "JobConfigFilter{id="+id+", type="+filterType+", field='"+filterField+"', op="+operationType+"}";
    }
}
