package com.powerdialer.model;

import com.powerdialer.model.base.BaseEntity;
import org.hibernate.annotations.DynamicUpdate;

import javax.persistence.*;
import javax.validation.constraints.Size;

@Entity
@Table(name = "pd_campaign_job")
@DynamicUpdate
public class CampaignJobs extends BaseEntity {

    private Long id;
    private String campaignTableName;
    private JobConfig jobConfig;
    private Campaign campaign;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "baseEntity_seq")
    @SequenceGenerator(name = "baseEntity_seq", sequenceName = "PD_CAMPAIGNJOB_FCSEQ", allocationSize = 1)
    @Column(name = "id")
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_config_id", nullable = false)
    public JobConfig getJobConfig() { return jobConfig; }
    public void setJobConfig(JobConfig jobConfig) { this.jobConfig = jobConfig; }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id", nullable = false)
    public Campaign getCampaign() { return campaign; }
    public void setCampaign(Campaign campaign) { this.campaign = campaign; }

    @Size(max = 100)
    @Column(name = "campaign_table_name", nullable = false, length = 100)
    public String getCampaignTableName() { return campaignTableName; }
    public void setCampaignTableName(String campaignTableName) { this.campaignTableName = campaignTableName; }

    @Override public int hashCode(){ return generateHash(31,42,getId()); }
    @Override public boolean equals(Object o){
        if (this==o) return true;
        if (!(o instanceof CampaignJobs)) return false;
        CampaignJobs other=(CampaignJobs)o;
        if (getId()==null && other.getId()==null) return false;
        return generateEquals(getId(), other.getId());
    }
    @Override public String toString(){ return "CampaignJobs{id="+id+", campaignTableName='"+campaignTableName+"'}"; }
}
