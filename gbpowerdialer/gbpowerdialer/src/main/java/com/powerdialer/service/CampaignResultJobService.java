package com.powerdialer.service;

import com.powerdialer.dao.IJobConfigDao;
import com.powerdialer.dao.ICampaignJobLogDao;
import com.powerdialer.dao.ICampaignJobResultLogDao;
import com.powerdialer.dto.JobConfigCampaignDTO;
import com.powerdialer.dto.JobConfigFilterDTO;
import com.powerdialer.engine.db.DBEngine;
import com.powerdialer.model.CampaignJobLog;
import com.powerdialer.model.ParametrizedQuery;
import com.powerdialer.model.User;
import com.powerdialer.util.Constants;
import com.powerdialer.util.EnCipherUtils;
import com.powerdialer.util.ParamSql;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Lazy(false)
public class CampaignResultJobService {

    private static final Logger LOG = LoggerFactory.getLogger(CampaignResultJobService.class);
    private static final int SERVICE_BATCH_SIZE = 500;

    private final IJobConfigDao jobConfigDao;
    private final ICampaignJobLogDao campaignJobLogDao;
    private final ICampaignJobResultLogDao campaignJobResultLogDao;
    private final DBEngine dbEngine;
    private final EnCipherUtils enCipherUtils;
    private final UserService userService;
    private final TransactionTemplate transactionTemplate;
    private final UpdateExecutor updateExecutor = new SqlServerUpdateExecutor();

    private static String safeTrim(String s) { return StringUtils.trimToNull(s); }

    public CampaignResultJobService(IJobConfigDao jobConfigDao,
                                    ICampaignJobLogDao campaignJobLogDao,
                                    ICampaignJobResultLogDao campaignJobResultLogDao,
                                    DBEngine dbEngine,
                                    EnCipherUtils enCipherUtils,
                                    UserService userService,
                                    PlatformTransactionManager tm) {
        this.jobConfigDao = jobConfigDao;
        this.campaignJobLogDao = campaignJobLogDao;
        this.campaignJobResultLogDao = campaignJobResultLogDao;
        this.dbEngine = dbEngine;
        this.enCipherUtils = enCipherUtils;
        this.userService = userService;
        this.transactionTemplate = new TransactionTemplate(tm);
    }

    @Scheduled(cron = "${jobs.campaign-result.cron}")
    public void runJob() {
        LOG.info("[CRON] CampaignResultJobService started");

        List<Long> ids = transactionTemplate.execute(status ->
                jobConfigDao.findEligibleJobConfigIds_FirstScan()
        );
        if (ids == null || ids.isEmpty()) {
            LOG.info("[CRON] No eligible job config found");
            return;
        }

        for (int i = 0; i < ids.size(); i += SERVICE_BATCH_SIZE) {
            processSlice(ids.subList(i, Math.min(i + SERVICE_BATCH_SIZE, ids.size())));
        }

        LOG.info("[CRON] CampaignResultJobService finished. scannedConfigs={}", ids.size());
    }

    private void processSlice(List<Long> jobConfigIdSlice) {
        LOG.debug("Processing slice size={}", jobConfigIdSlice.size());

        final List<JobConfigCampaignDTO> rows = transactionTemplate.execute(status ->
                jobConfigDao.findCampaignRowsByJobConfigIds(jobConfigIdSlice)
        );
        if (rows == null || rows.isEmpty()) return;

        final Map<Long, List<JobConfigFilterDTO>> filtersByJc = transactionTemplate.execute(status -> {
            Set<Long> jcIds = rows.stream()
                    .map(JobConfigCampaignDTO::getJobConfigId)
                    .collect(Collectors.toSet());

            return jobConfigDao.findFiltersByJobConfigIds(jcIds)
                    .stream()
                    .collect(Collectors.groupingBy(JobConfigFilterDTO::getJobConfigId));
        });

        for (JobConfigCampaignDTO r : rows) {
            List<JobConfigFilterDTO> fl = (filtersByJc != null)
                    ? filtersByJc.getOrDefault(r.getJobConfigId(), Collections.emptyList())
                    : Collections.emptyList();
            processRow(r, fl);
        }
    }

    private void processRow(JobConfigCampaignDTO jobConfigDto, List<JobConfigFilterDTO> filters) {
        Long expireUserId = jobConfigDto.getExpireUserId();
        User expireUser = null;
        if (expireUserId != null) {
            expireUser = userService.getUserById(expireUserId);
            if (expireUser == null) {
                LOG.debug("ExpireUser not found. jobConfigId={}, campaignId={}, expireUserId={}",
                        jobConfigDto.getJobConfigId(), jobConfigDto.getCampaignId(), expireUserId);
                return;
            }
        }

        final String from = safeTrim(jobConfigDto.getDbResultTable());
        if (from == null) {
            LOG.debug("Table name not found in JobConfig. jobConfigId={}, cjId={}, campaignId={}",
                    jobConfigDto.getJobConfigId(), jobConfigDto.getCampaignJobId(), jobConfigDto.getCampaignId());
            return;
        }

        final String jobName = (jobConfigDto.getJobName() != null
                ? jobConfigDto.getJobName()
                : "JobConfig#" + jobConfigDto.getJobConfigId());

        final User expUser = expireUser;
        final Long jobCfgIdF = jobConfigDto.getJobConfigId();
        final Long campIdF   = jobConfigDto.getCampaignId();
        final String campNameF = (jobConfigDto.getCampaignName() != null ? jobConfigDto.getCampaignName() : from);

        CampaignJobLog campaignJobLog = transactionTemplate.execute(status ->
                campaignJobLogDao.createLog(
                        jobCfgIdF,
                        campIdF,
                        campNameF,
                        new Date(),
                        Constants.CAMPAIGN_JOB_RESULT_STATUS.JOB_RESULT_BUILDING,
                        null,
                        expUser
                )
        );

        try {
            final String conn  = safeTrim(jobConfigDto.getDbConnectionString());
            final String user  = safeTrim(jobConfigDto.getDbUsername());
            final String encPw = safeTrim(jobConfigDto.getDbPassword());

            if (conn == null || user == null) {
                LOG.debug("Connection info missing. campaignId={}, jobConfigId={}, table={}",
                        jobConfigDto.getCampaignId(), jobConfigDto.getJobConfigId(), from);

                final CampaignJobLog logRef = campaignJobLog;
                transactionTemplate.execute(s2 -> {
                    campaignJobLogDao.updateLog(
                            logRef,
                            Constants.CAMPAIGN_JOB_RESULT_STATUS.JOB_RESULT_FAILED,
                            "Connection information missing."
                    );
                    return null;
                });
                return;
            }
            final String pwd = (encPw == null ? null : enCipherUtils.decryptPassword(encPw));

            final SqlWhitelist wl = SqlWhitelist.load(dbEngine, campIdF, conn, user, pwd, from);
            final ParamSql where = new CompositeWhereClauseBuilder(wl).build(filters);
            if (StringUtils.isBlank(where.getSql())) {
                LOG.debug("Filter condition is empty. jobConfigId={}, table={}", jobConfigDto.getJobConfigId(), from);

                final CampaignJobLog logRef = campaignJobLog;
                transactionTemplate.execute(s2 -> {
                    campaignJobLogDao.updateLog(
                            logRef,
                            Constants.CAMPAIGN_JOB_RESULT_STATUS.JOB_RESULT_SKIPPED,
                            "Filter condition is empty."
                    );
                    return null;
                });
                return;
            }

            final SetSql setSql = getSetSql(jobConfigDto);

            final String selectIdsSql = "SELECT id AS campaign_id FROM " + wl.table + " WHERE " + where.getSql() + ";";
            final ParametrizedQuery selectQ = new ParametrizedQuery(selectIdsSql, where.getParams());

            final JSONArray selected = dbEngine.executeParametrizedQuery(
                    selectQ, campIdF, Integer.MAX_VALUE, conn, user, pwd
            );

            final List<Long> affectedIds = parseAffectedIds(selected);
            if (affectedIds.isEmpty()) {
                final CampaignJobLog logRef = campaignJobLog;
                transactionTemplate.execute(s2 -> {
                    campaignJobLogDao.updateLog(
                            logRef,
                            Constants.CAMPAIGN_JOB_RESULT_STATUS.JOB_RESULT_SKIPPED,
                            "No data found in the result table that meets the requirements."
                    );
                    return null;
                });
                LOG.debug("JOB {} table={} WHERE={} -> skipped (0 matches)", jobName, from, where.getSql());
                return;
            }

            final String updateSql = updateExecutor.buildUpdateSqlNoOutput(wl.table, setSql.clause, where.getSql());
            final ArrayList<Object> updateParams = new ArrayList<>(setSql.params.size() + where.getParams().size());
            updateParams.addAll(setSql.params);
            updateParams.addAll(where.getParams());

            final ParametrizedQuery updateQ = new ParametrizedQuery(updateSql, updateParams);
            final int affectedCount = dbEngine.executeBatchParametrizedUpdate(
                    updateQ, campIdF, conn, user, pwd
            );

            final CampaignJobLog logRef = campaignJobLog;
            transactionTemplate.execute(s2 -> {
                int insertedRows = campaignJobResultLogDao.createLogsBulk(logRef, affectedIds);
                LOG.debug("ResultLog insertedRows={}", insertedRows);
                campaignJobLogDao.updateLog(
                        logRef,
                        Constants.CAMPAIGN_JOB_RESULT_STATUS.JOB_RESULT_SUCCESSFUL,
                        null
                );
                return null;
            });

            LOG.debug("JOB {} table={} WHERE={} -> affectedRows={} (selectedIds={})",
                    jobName, from, where.getSql(), affectedCount, affectedIds.size());

        } catch (Exception e) {
            final CampaignJobLog logRef = campaignJobLog;
            transactionTemplate.execute(s2 -> {
                campaignJobLogDao.updateLog(
                        logRef,
                        Constants.CAMPAIGN_JOB_RESULT_STATUS.JOB_RESULT_FAILED,
                        e.getMessage()
                );
                return null;
            });
            LOG.error("JOB {} table={} failed. WHERE={} error={}", jobName, from, "-", e);
        }
    }

    private static List<Long> parseAffectedIds(JSONArray affected) {
        final List<Long> list = new ArrayList<>(affected != null ? affected.length() : 0);
        if (affected == null) return list;
        for (int j = 0; j < affected.length(); j++) {
            final JSONObject obj = affected.getJSONObject(j);
            final long id = obj.optLong("campaign_id", -1);
            if (id > 0) list.add(id);
        }
        return list;
    }

    private static SetSql getSetSql(JobConfigCampaignDTO jobConfigDto) {
        final List<String> parts = new ArrayList<>();
        final List<Object> params = new ArrayList<>();

        parts.add("is_disabled = ?");
        params.add(1);

        if (jobConfigDto.getResultCodeId() != null) {
            parts.add("[user_result_code] = ?");
            params.add(jobConfigDto.getResultCodeId());
        }
        if (jobConfigDto.getExpireUserId() != null) {
            parts.add("[UpdatedUser] = ?");
            params.add(jobConfigDto.getExpireUserId());
        }

        parts.add("[UpdatedDate] = ?");
        params.add(new Timestamp(System.currentTimeMillis()));

        return new SetSql(String.join(", ", parts), params);
    }

    public CampaignJobLog getCampaigJobLogByCampaignId(Long campaignId) {
        return campaignJobLogDao.getCampaignLogByCampaignId(campaignId);
    }

    static final class SetSql {
        final String clause;
        final List<Object> params;
        SetSql(String clause, List<Object> params) { this.clause = clause; this.params = params; }
    }

    interface UpdateExecutor {
        String buildUpdateSqlNoOutput(String tableName, String setClause, String whereClause);
        String buildUpdateSql(String tableName, String setClause, String whereClause);
    }

    static final class SqlServerUpdateExecutor implements UpdateExecutor {
        @Override
        public String buildUpdateSqlNoOutput(String tableName, String setClause, String whereClause) {
            if (StringUtils.isBlank(tableName)) throw new IllegalArgumentException("tableName is empty");
            if (StringUtils.isBlank(setClause)) throw new IllegalArgumentException("setClause is empty");
            if (StringUtils.isBlank(whereClause)) throw new IllegalArgumentException("WHERE is empty");
            return "UPDATE " + tableName + " SET " + setClause + " WHERE " + whereClause + ";";
        }

        @Override
        public String buildUpdateSql(String tableName, String setClause, String whereClause) {
            if (StringUtils.isBlank(tableName)) throw new IllegalArgumentException("tableName is empty");
            if (StringUtils.isBlank(setClause)) throw new IllegalArgumentException("setClause is empty");
            if (StringUtils.isBlank(whereClause)) throw new IllegalArgumentException("WHERE is empty");
            return "UPDATE " + tableName + " SET " + setClause + " WHERE " + whereClause + " OUTPUT inserted.id AS campaign_id;";
        }
    }

}
