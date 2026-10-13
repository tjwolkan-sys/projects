package com.powerdialer.model;

import com.powerdialer.model.base.BaseEntity;
import com.powerdialer.util.Constants;
import org.hibernate.annotations.DynamicUpdate;

import javax.persistence.*;
import java.util.Date;

@Entity
@Table(name = "pd_campaign_job_log")
@DynamicUpdate
public class CampaignJobLog extends BaseEntity {

    private Long id;
    private JobConfig jobConfig;

    private String campaignName;
    private Long campaignId;

    private Date executionStartDate;
    private Date executionEndDate;

    private Constants.CAMPAIGN_JOB_RESULT_STATUS executionStatus;
    private String errorMessage;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "baseEntity_seq")
    @SequenceGenerator(name = "baseEntity_seq", sequenceName = "PD_CAMPAIGNJOBLOG_FCSEQ", allocationSize = 1)
    @Column(name = "id")
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_config_id", nullable = false)
    public JobConfig getJobConfig() { return jobConfig; }
    public void setJobConfig(JobConfig jobConfig) { this.jobConfig = jobConfig; }

    @Column(name = "campaign_name", nullable = false, length = 100)
    public String getCampaignName() { return campaignName; }
    public void setCampaignName(String campaignName) { this.campaignName = campaignName; }

    @Column(name = "campaign_id", nullable = false)
    public Long getCampaignId() { return campaignId; }
    public void setCampaignId(Long campaignId) { this.campaignId = campaignId; }

    @Column(name = "execution_start_date", nullable = false)
    public Date getExecutionStartDate() { return executionStartDate; }
    public void setExecutionStartDate(Date executionStartDate) { this.executionStartDate = executionStartDate; }

    @Column(name = "execution_end_date")
    public Date getExecutionEndDate() { return executionEndDate; }
    public void setExecutionEndDate(Date executionEndDate) { this.executionEndDate = executionEndDate; }

    @Enumerated(EnumType.ORDINAL) // mevcut şema ile uyum
    @Column(name = "execution_status", nullable = false)
    public Constants.CAMPAIGN_JOB_RESULT_STATUS getExecutionStatus() { return executionStatus; }
    public void setExecutionStatus(Constants.CAMPAIGN_JOB_RESULT_STATUS executionStatus) { this.executionStatus = executionStatus; }

    @Column(name = "error_message", length = 2000)
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }


    @Override
    public int hashCode() {
        return generateHash(31, 42, getId());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CampaignJobLog)) {
            return false;
        }
        final CampaignJobLog obj = (CampaignJobLog) o;
        if (getId() == null && obj.getId() == null)
            return false;
        return generateEquals(getId(), obj.getId());
    }

    @Override
    public String toString() {
        return "CampaignJobLog{id=" + id + ", campaignName='" + campaignName +
                "', campaignId=" + campaignId + "}";
    }
}
