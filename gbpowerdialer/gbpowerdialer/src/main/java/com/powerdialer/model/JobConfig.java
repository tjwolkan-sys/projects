package com.powerdialer.model;

import com.powerdialer.model.base.BaseEntity;
import org.hibernate.annotations.DynamicUpdate;

import javax.persistence.*;
import javax.validation.constraints.Size;
import java.util.*;

@Entity
@Table(name = "pd_job_config")
@DynamicUpdate
public class JobConfig extends BaseEntity {

    private Long id;
    private String jobName;

    private Long expireUserId;
    private Long resultCodeId;

    private List<CampaignJobs> campaignJobs = new ArrayList<>();
    private Set<JobConfigFilter> jobConfigFilters = new LinkedHashSet<>();
    private List<CampaignJobLog> campaignJobLogs = new ArrayList<>();

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "baseEntity_seq")
    @SequenceGenerator(name = "baseEntity_seq", sequenceName = "PD_JOBCONFIG_FCSEQ", allocationSize = 1)
    @Column(name = "id")
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    @Size(max = 100)
    @Column(name = "job_name", nullable = false, length = 100)
    public String getJobName() { return jobName; }
    public void setJobName(String jobName) { this.jobName = jobName; }

    @Column(name = "expire_user_id", nullable = false)
    public Long getExpireUserId() { return expireUserId; }
    public void setExpireUserId(Long expireUserId) { this.expireUserId = expireUserId; }

    @Column(name = "result_code_id", nullable = false)
    public Long getResultCodeId() { return resultCodeId; }
    public void setResultCodeId(Long resultCodeId) { this.resultCodeId = resultCodeId; }

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.LAZY, mappedBy = "jobConfig")
    public List<CampaignJobs> getCampaignJobs() { return campaignJobs; }
    public void setCampaignJobs(List<CampaignJobs> campaignJobs) { this.campaignJobs = campaignJobs; }

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.LAZY, mappedBy = "jobConfig")
    public Set<JobConfigFilter> getJobConfigFilters() { return jobConfigFilters; }
    public void setJobConfigFilters(Set<JobConfigFilter> jobConfigFilters) {
        this.jobConfigFilters = (jobConfigFilters != null)
                ? new LinkedHashSet<>(jobConfigFilters) : new LinkedHashSet<>();
    }

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.LAZY, mappedBy = "jobConfig")
    public List<CampaignJobLog> getCampaignJobLogs() { return campaignJobLogs; }
    public void setCampaignJobLogs(List<CampaignJobLog> campaignJobLogs) { this.campaignJobLogs = campaignJobLogs; }

    public void addCampaignJob(CampaignJobs cj){ cj.setJobConfig(this); campaignJobs.add(cj); }
    public void removeCampaignJob(CampaignJobs cj){ cj.setJobConfig(null); campaignJobs.remove(cj); }

    public void addFilter(JobConfigFilter f){ f.setJobConfig(this); jobConfigFilters.add(f); }
    public void removeFilter(JobConfigFilter f){ f.setJobConfig(null); jobConfigFilters.remove(f); }

    public void addJobLog(CampaignJobLog l){ l.setJobConfig(this); campaignJobLogs.add(l); }
    public void removeJobLog(CampaignJobLog l){ l.setJobConfig(null); campaignJobLogs.remove(l); }

    @Override public int hashCode() { return generateHash(31, 42, getId()); }
    @Override public boolean equals(Object o){
        if (this == o) return true;
        if (!(o instanceof JobConfig)) return false;
        JobConfig other = (JobConfig) o;
        if (getId()==null && other.getId()==null) return false;
        return generateEquals(getId(), other.getId());
    }
    @Override public String toString(){ return "JobConfig{id=" + id + ", jobName='" + jobName + "'}"; }
}
