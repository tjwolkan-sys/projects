package com.powerdialer.dao.impl;

import com.powerdialer.dao.IJobConfigDao;
import com.powerdialer.dto.JobConfigCampaignDTO;
import com.powerdialer.dto.JobConfigFilterDTO;
import com.powerdialer.model.JobConfig;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.springframework.stereotype.Repository;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Repository
public class JobConfigDao extends GenericDao<JobConfig> implements IJobConfigDao {

    public JobConfigDao() { super(JobConfig.class); }

    private Session session() { return getSessionFactory().getCurrentSession(); }

    @Override
    public List<Long> findEligibleJobConfigIds_FirstScan() {

        Query<Long> q = session().createQuery(
                "select distinct jc.id " +
                        "from JobConfig jc " +
                        "where jc.enabled = true and jc.deleted = false " +
                        "  and exists ( " +
                        "       select 1 " +
                        "       from CampaignJobs cjx " +
                        "       join cjx.campaign cx " +
                        "       where cjx.jobConfig = jc " +
                        "         and cjx.deleted = false and cjx.enabled = true " +
                        "         and cx.stoppedDate is not null " +
                        "  ) " +
                        "order by jc.id asc", Long.class);
        q.setReadOnly(true);
        return q.list();


    }

    @Override
    public List<JobConfigCampaignDTO> findCampaignRowsByJobConfigIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) return Collections.emptyList();

        Query<JobConfigCampaignDTO> q = session().createQuery(
                "select new com.powerdialer.dto.JobConfigCampaignDTO(" +
                        "  jc.id, jc.jobName, jc.expireUserId, jc.resultCodeId, " +
                        "  cj.id, cj.campaignTableName, " +
                        "  c.id, c.name, c.dbResultTable, c.stoppedDate, " +
                        "  c.dbConnectionString, c.dbUsername, c.dbPassword) " +
                        "from JobConfig jc " +
                        "join jc.campaignJobs cj " +
                        "join cj.campaign c " +
                        "where jc.id in (:ids) " +
                        "  and jc.enabled = true and jc.deleted = false " +
                        "  and cj.enabled = true and cj.deleted = false " +
                        "  and c.stoppedDate is not null " +
                        "order by jc.id asc, cj.id asc",
                JobConfigCampaignDTO.class);
        q.setParameter("ids", ids);
        q.setReadOnly(true);
        return q.getResultList();
    }

    @Override
    public List<JobConfigFilterDTO> findFiltersByJobConfigIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) return Collections.emptyList();

        String hql =
                "select new com.powerdialer.dto.JobConfigFilterDTO(" +
                        "  jc.id, " +
                        "  f.id, " +
                        "  f.filterField, " +
                        "  f.operationType, " +
                        "  f.filterValue, " +
                        "  f.filterDateValue, " +
                        "  f.expireThresholdDays, " +
                        "  f.filterType," +
                        "  f.freeQueryText" +
                        ") " +
                        "from JobConfigFilter f " +
                        "join f.jobConfig jc " +
                        "where f.deleted = false and f.enabled=true " +
                        " and jc.deleted = false and jc.enabled=true "+
                        "  and jc.id in (:ids) " +
                        "order by jc.id asc, f.id asc";

        Query<JobConfigFilterDTO> q = session().createQuery(hql, JobConfigFilterDTO.class);
        q.setParameterList("ids", ids);
        q.setReadOnly(true);
        return q.list();
    }

    @Override
    public List<JobConfigCampaignDTO> findActiveJobConfigsForCampaign(Long campaignId) {
        if (campaignId == null) return Collections.emptyList();

        Query<JobConfigCampaignDTO> q = session().createQuery(
                "select new com.powerdialer.dto.JobConfigCampaignDTO(" +
                        "  jc.id, jc.jobName, jc.expireUserId, jc.resultCodeId, " +
                        "  cj.id, cj.campaignTableName, " +
                        "  c.id, c.name, c.dbResultTable, c.stoppedDate, " +
                        "  c.dbConnectionString, c.dbUsername, c.dbPassword) " +
                        "from JobConfig jc " +
                        "join jc.campaignJobs cj " +
                        "join cj.campaign c " +
                        "where c.id = :campaignId " +
                        "  and jc.enabled = true and jc.deleted = false " +
                        "  and cj.enabled = true and cj.deleted = false " +
                        "order by jc.id asc, cj.id asc",
                JobConfigCampaignDTO.class);
        q.setParameter("campaignId", campaignId);
        q.setReadOnly(true);
        return q.getResultList();
    }

}
