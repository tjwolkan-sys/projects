package com.powerdialer.model;

import com.powerdialer.model.base.BaseEntity;
import org.hibernate.annotations.DynamicUpdate;

import javax.persistence.*;

@Entity
@Table(name = "pd_campaign_job_result_log")
@DynamicUpdate
public class CampaignJobResultLog extends BaseEntity {

    private Long id;
    private CampaignJobLog campaignJobLog;
    private Long resultTableId;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "baseEntity_seq")
    @SequenceGenerator(name = "baseEntity_seq", sequenceName = "PD_CAMPAIGNJOBRESULTLOG_FCSEQ", allocationSize = 1)
    @Column(name = "id")
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_job_log_id", nullable = false)
    public CampaignJobLog getCampaignJobLog() { return campaignJobLog; }
    public void setCampaignJobLog(CampaignJobLog campaignJobLog) { this.campaignJobLog = campaignJobLog; }

    @Column(name = "result_table_id", nullable = false)
    public Long getResultTableId() { return resultTableId; }
    public void setResultTableId(Long resultTableId) { this.resultTableId = resultTableId; }

    @Override public int hashCode(){ return generateHash(31,42,getId()); }
    @Override public boolean equals(Object o){
        if (this==o) return true;
        if (!(o instanceof CampaignJobResultLog)) return false;
        CampaignJobResultLog other=(CampaignJobResultLog)o;
        if (getId()==null && other.getId()==null) return false;
        return generateEquals(getId(), other.getId());
    }
    @Override public String toString(){ return "CampaignJobResultLog{id="+id+"}"; }
}
