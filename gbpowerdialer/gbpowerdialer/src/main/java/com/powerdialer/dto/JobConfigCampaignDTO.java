package com.powerdialer.dto;

import java.io.Serializable;
import java.util.Date;

public class JobConfigCampaignDTO implements Serializable {
    private final Long jobConfigId;
    private final String jobName;
    private final Long campaignId;
    private final Long expireUserId;
    private final Long campaignJobId;
    private final String campaignTableName;

    private final Long resultCodeId;
    private final String campaignName;
    private final String dbResultTable;
    private final Date stoppedDate;


    private final String dbConnectionString;
    private final String dbUsername;
    private final String dbPassword;

    public JobConfigCampaignDTO(Long jobConfigId, String jobName,Long expireUserId,Long resultCodeId,
                                Long campaignJobId, String campaignTableName,
                                Long campaignId, String campaignName,
                                String dbResultTable, Date stoppedDate,
                                String dbConnectionString, String dbUsername, String dbPassword) {
        this.jobConfigId = jobConfigId;
        this.jobName = jobName;
        this.campaignJobId = campaignJobId;
        this.campaignTableName = campaignTableName;
        this.campaignId = campaignId;
        this.campaignName = campaignName;
        this.dbResultTable = dbResultTable;
        this.stoppedDate = stoppedDate == null ? null : new Date(stoppedDate.getTime());
        this.dbConnectionString = dbConnectionString;
        this.dbUsername = dbUsername;
        this.dbPassword = dbPassword;
        this.expireUserId = expireUserId;
        this.resultCodeId = resultCodeId;
    }

    public Long getJobConfigId() { return jobConfigId; }
    public String getJobName() { return jobName; }
    public Long getCampaignJobId() { return campaignJobId; }
    public String getCampaignTableName() { return campaignTableName; }
    public Long getCampaignId() { return campaignId; }
    public String getCampaignName() { return campaignName; }
    public String getDbResultTable() { return dbResultTable; }
    public Date getStoppedDate() { return stoppedDate == null ? null : new Date(stoppedDate.getTime()); }

    public String getDbConnectionString() { return dbConnectionString; }
    public String getDbUsername() { return dbUsername; }
    public String getDbPassword() { return dbPassword; }
    public Long getExpireUserId() { return expireUserId; }
    public Long getResultCodeId() { return resultCodeId; }
}
