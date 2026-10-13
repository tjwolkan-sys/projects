package com.powerdialer.dao.impl;

import com.powerdialer.dao.ICampaignJobLogDao;
import com.powerdialer.model.CampaignJobLog;
import com.powerdialer.model.JobConfig;
import com.powerdialer.model.User;
import com.powerdialer.util.Constants;
import org.hibernate.Session;
import org.springframework.stereotype.Repository;

import org.springframework.transaction.annotation.Transactional;
import java.util.Date;
import java.util.EnumSet;
import java.util.Objects;

@Repository
public class CampaignJobLogDao extends GenericDao<CampaignJobLog> implements ICampaignJobLogDao {

    public CampaignJobLogDao() {
        super(CampaignJobLog.class);
    }

    private Session session() {
        return getSessionFactory().getCurrentSession();
    }

    @Override
    @Transactional(readOnly = true)
    public CampaignJobLog getCampaignLogByCampaignId(Long campaignId) {
        if (campaignId == null) return null;

        final String hql =
                "select l " +
                        "from CampaignJobLog l " +
                        "where l.campaignId = :cid " +
                        "and l.executionEndDate is null";

        return session()
                .createQuery(hql, CampaignJobLog.class)
                .setParameter("cid", campaignId)
                .setMaxResults(1)
                .uniqueResultOptional()
                .orElse(null);
    }


    private static final EnumSet<Constants.CAMPAIGN_JOB_RESULT_STATUS> TERMINAL_STATUSES =
            EnumSet.of(
                    Constants.CAMPAIGN_JOB_RESULT_STATUS.JOB_RESULT_SUCCESSFUL,
                    Constants.CAMPAIGN_JOB_RESULT_STATUS.JOB_RESULT_FAILED,
                    Constants.CAMPAIGN_JOB_RESULT_STATUS.JOB_RESULT_SKIPPED
            );

    @Override
    @Transactional
    public void updateLog(CampaignJobLog log,
                          Constants.CAMPAIGN_JOB_RESULT_STATUS status,
                          String errorMessage) {

        Objects.requireNonNull(log, "log");
        final Date now = new Date();

        log.setExecutionStatus(status);
        if (errorMessage != null && !errorMessage.isEmpty()) {
            log.setErrorMessage(errorMessage);
        }
        if (TERMINAL_STATUSES.contains(status) && log.getExecutionEndDate() == null) {
            log.setExecutionEndDate(now);
        }

        saveOrUpdate(log);
    }

    @Override
    @Transactional
    public CampaignJobLog createLog(Long jobConfigId,
                                    Long campaignId,
                                    String campaignName,
                                    Date startDate,
                                    Constants.CAMPAIGN_JOB_RESULT_STATUS status,
                                    String errorMessage,
                                    User user) {

        if (jobConfigId == null) {
            throw new IllegalArgumentException("jobConfigId required");
        }

        final Date now = new Date();

        JobConfig jcRef = session().get(JobConfig.class, jobConfigId);
        if (jcRef == null) {
            throw new IllegalStateException("JobConfig not found: id=" + jobConfigId);
        }

        CampaignJobLog log = new CampaignJobLog();
        log.setJobConfig(jcRef);
        log.setCampaignId(campaignId);
        log.setCampaignName(campaignName);
        log.setExecutionStartDate(startDate != null ? startDate : now);
        log.setExecutionEndDate(null);
        log.setExecutionStatus(status);
        log.setCreateUser(user);
        if (errorMessage != null && !errorMessage.isEmpty()) {
            log.setErrorMessage(errorMessage);
        }
        log.setCreateDate(now);
        log.setEnabled(true);
        log.setDeleted(false);
        saveOrUpdate(log);
        return log;
    }
}
