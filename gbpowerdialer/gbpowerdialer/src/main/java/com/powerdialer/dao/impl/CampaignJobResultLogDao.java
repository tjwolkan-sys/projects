package com.powerdialer.dao.impl;

import com.powerdialer.dao.ICampaignJobResultLogDao;
import com.powerdialer.model.CampaignJobLog;
import com.powerdialer.model.CampaignJobResultLog;
import org.hibernate.Session;
import org.hibernate.resource.transaction.spi.TransactionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import javax.persistence.PersistenceException;
import javax.transaction.Transactional;
import java.util.*;
import java.util.stream.Collectors;

@Repository
public class CampaignJobResultLogDao extends GenericDao<CampaignJobResultLog>
        implements ICampaignJobResultLogDao {

    private static final Logger LOG = LoggerFactory.getLogger(CampaignJobResultLogDao.class);

    public CampaignJobResultLogDao() { super(CampaignJobResultLog.class); }

    private Session session() { return getSessionFactory().getCurrentSession(); }

    private static final String INSERT_PREFIX =
            "INSERT INTO dbo.pd_campaign_job_result_log " +
                    "(id, create_date, create_user, enabled, deleted, campaign_job_log_id, result_table_id) " +
                    "SELECT NEXT VALUE FOR dbo.PD_CAMPAIGNJOBRESULTLOG_FCSEQ, :createDate, :createUser, 1, 0, :logId, v.rid " +
                    "FROM (VALUES ";

    private static final String INSERT_SUFFIX =
            ") AS v(rid) " +
                    "LEFT JOIN dbo.pd_campaign_job_result_log r " +
                    "  ON r.campaign_job_log_id = :logId AND r.result_table_id = v.rid " +
                    "WHERE r.id IS NULL;";

    @Override
    @Transactional
    public int createLogsBulk(CampaignJobLog log, Collection<Long> ids) {
        long t0 = System.currentTimeMillis();

        if (log == null) {
            LOG.debug("[ResultLogBulk] campaignJobLog is null, skipping insert.");
            return 0;
        }
        if (ids == null || ids.isEmpty()) {
            LOG.debug("[ResultLogBulk] Empty id list for logId={}, nothing to insert.", log.getId());
            return 0;
        }

        int totalInsertedRows = 0;

        try {
            final LinkedHashSet<Long> unique = ids.stream()
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            if (unique.isEmpty()) {
                LOG.debug("[ResultLogBulk] All ids were null for logId={}, skip.", log.getId());
                return 0;
            }

            final int CHUNK_SIZE = 1000;
            final List<Long> list = new ArrayList<>(unique);

            final java.sql.Timestamp now = new java.sql.Timestamp(System.currentTimeMillis());
            final Long createUserId = (log.getCreateUser() != null ? log.getCreateUser().getId() : null);

            for (int from = 0; from < list.size(); from += CHUNK_SIZE) {
                final int to = Math.min(from + CHUNK_SIZE, list.size());
                final List<Long> chunk = list.subList(from, to);

                final String valuesPart = buildValuesPlaceholders(chunk.size());

                final String sql = INSERT_PREFIX + valuesPart + INSERT_SUFFIX;

                final javax.persistence.Query q = session().createNativeQuery(sql);
                q.setParameter("createDate", now);
                q.setParameter("createUser", createUserId);
                q.setParameter("logId", log.getId());

                for (int i = 0; i < chunk.size(); i++) {
                    q.setParameter("p" + i, chunk.get(i));
                }

                final int affected = q.executeUpdate();
                totalInsertedRows += affected;

                    LOG.debug("[ResultLogBulk] logId={} chunk=[{}..{}) requestedRows={} inserted={}",
                            log.getId(), from, to, chunk.size(), affected);

                session().flush();
                session().clear();
            }

            LOG.info("[ResultLogBulk] DONE logId={} insertedRows={} elapsedMs={}",
                    log.getId(), totalInsertedRows, (System.currentTimeMillis() - t0));
            return totalInsertedRows;

        } catch (PersistenceException pe) {
            LOG.error("[ResultLogBulk] PersistenceException on logId={}, msg={}",
                    log.getId(), pe);
            throw pe;
        } catch (Exception e) {
            LOG.error("[ResultLogBulk] Unexpected error on logId={}, msg={}", log.getId(), e);
            throw e;
        } finally {
            try {
                TransactionStatus st = session().getTransaction().getStatus();
                LOG.debug("[ResultLogBulk] txnStatus={} logId={} elapsedMs={}",
                        st, log.getId(), (System.currentTimeMillis() - t0));
            } catch (Exception ignore) { }
        }
    }


    private static String buildValuesPlaceholders(int count) {
        if (count <= 0) return "";
        StringBuilder sb = new StringBuilder(count * 6);
        for (int i = 0; i < count; i++) {
            if (i > 0) sb.append(", ");
            sb.append("(:p").append(i).append(")");
        }
        return sb.toString();
    }
}
