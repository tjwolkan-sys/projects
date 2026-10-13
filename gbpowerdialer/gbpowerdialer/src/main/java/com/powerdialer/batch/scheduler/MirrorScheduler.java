package com.powerdialer.batch.scheduler;

import com.powerdialer.engine.db.DBEngine;
import com.powerdialer.model.ParametrizedQuery;
import com.powerdialer.util.EnCipherUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@EnableScheduling
@Lazy(false)
public class MirrorScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(MirrorScheduler.class);
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final DBEngine dbEngine;
    private final EnCipherUtils enCipherUtils;

    private final String srcUrl;
    private final String srcUser;
    private final String srcPass;

    public MirrorScheduler(DBEngine dbEngine, EnCipherUtils enCipherUtils) {
        this.dbEngine = dbEngine;
        this.enCipherUtils = enCipherUtils;
        this.srcUrl  = dbEngine.getMainDatabaseUrl();
        this.srcUser = dbEngine.getMainDatabaseUsername();
        this.srcPass = dbEngine.getMainDatabasePassword();
    }

    @Value("${mirror.batch.size.rc}")  private int BATCH_SIZE_RC;
    @Value("${mirror.batch.size.rcc}") private int BATCH_SIZE_RCC;
    @Value("${mirror.batch.size.trc}") private int BATCH_SIZE_TRC;


    @FunctionalInterface
    private interface RowMapper {
        List<List<Object>> map(String[][] rows);
    }

    private enum TableSync {
        TELEPHONY_RC(
                "SELECT TOP (?) id, name FROM dbo.pd_telephony_rc WHERE id > ? ORDER BY id",
                "dbo.pd_telephony_rc_stg",
                Arrays.asList("id", "name"),
                new RowMapper() {
                    @Override public List<List<Object>> map(String[][] rows) {
                        List<List<Object>> out = new ArrayList<List<Object>>(rows.length);
                        for (String[] r : rows) {
                            out.add(Arrays.<Object>asList(parseIntOrNull(r[0]), r[1]));
                        }
                        return out;
                    }
                }
        ),
        RESULT_CODE(
                "SELECT TOP (?) id, result_code_text FROM dbo.wbp_result_code WHERE  deleted='0' and enabled='1' and id > ? ORDER BY id",
                "dbo.wbp_result_code_stg",
                Arrays.asList("id", "result_code_text"),
                new RowMapper() {
                    @Override public List<List<Object>> map(String[][] rows) {
                        List<List<Object>> out = new ArrayList<List<Object>>(rows.length);
                        for (String[] r : rows) {
                            out.add(Arrays.<Object>asList(parseIntOrNull(r[0]), r[1]));
                        }
                        return out;
                    }
                }
        ),


        STATUS_CRITERIA(
                "SELECT TOP (?) id, result_code_id, telephony_result_code_id, statusCode , group_id  " +
                        " FROM dbo.wbp_result_code_status_criteria WHERE deleted='0' and enabled='1' and id > ? ORDER BY id",
                "dbo.wbp_result_code_status_criteria_stg",
                Arrays.asList("id", "result_code_id", "telephony_result_code_id", "statusCode" ,"group_id"),
                new RowMapper() {
                    @Override public List<List<Object>> map(String[][] rows) {
                        List<List<Object>> out = new ArrayList<List<Object>>(rows.length);
                        for (String[] r : rows) {
                            out.add(Arrays.<Object>asList(
                                    parseIntOrNull(r[0]),
                                    parseIntOrNull(r[1]),
                                    parseIntOrNull(r[2]),
                                    r[3],
                                    parseIntOrNull(r[4])

                            ));
                        }
                        return out;
                    }
                }
        );

        final String selectSql;
        final String stagingTable;
        final List<String> columns;
        final RowMapper mapper;

        TableSync(String selectSql, String stagingTable, List<String> columns, RowMapper mapper) {
            this.selectSql = selectSql;
            this.stagingTable = stagingTable;
            this.columns = columns;
            this.mapper = mapper;
        }
    }


    @Scheduled(cron = "${jobs.mirror-result.cron}")
    public void runDaily() {
        if (!running.compareAndSet(false, true)) {
            LOG.warn("Mirror already running, skipping this tick.");
            return;
        }
        try {
            final long t0 = System.currentTimeMillis();
            LOG.info("Mirror refresh started");

            try {
                List<MirrorTarget> targets = loadMirrorTargets();
                if (targets.isEmpty()) {
                    LOG.warn("No mirror targets found. Skipping.");
                    return;
                }

                for (MirrorTarget tgt : targets) {
                    long started = System.currentTimeMillis();
                    int trc = 0, rc = 0, rcc = 0;
                    final String dstPwd = decryptSafe(tgt.encPass);

                    Long jobLogId = null;
                    try {
                        jobLogId = beginJobLog(tgt.url, tgt.user);

                        ensureSchemaForTarget(tgt.url, tgt.user, dstPwd);
                        truncateStaging(tgt.url, tgt.user, dstPwd);

                        // generic sync
                        trc = sync(TableSync.TELEPHONY_RC, BATCH_SIZE_TRC, srcPass, tgt.url, tgt.user, dstPwd);
                        rc  = sync(TableSync.RESULT_CODE,   BATCH_SIZE_RC,  srcPass, tgt.url, tgt.user, dstPwd);
                        rcc = sync(TableSync.STATUS_CRITERIA, BATCH_SIZE_RCC, srcPass, tgt.url, tgt.user, dstPwd);

                        swapToLive(tgt.url, tgt.user, dstPwd);

                        if (jobLogId != null) {
                            completeJobLog(jobLogId, "SUCCESS", trc, rc, rcc, null);
                        }
                        LOG.info("Mirror DONE rc={}, rcc={}, trc={}, took={} ms",
                                rc, rcc, trc, (System.currentTimeMillis() - started));
                    } catch (Exception e) {
                        String err = left(safeMessage(e), 500);
                        LOG.error("Mirror FAILED rc={}, rcc={}, trc={}, reason={}", rc, rcc, trc, err);
                        if (jobLogId != null) safeCompleteJobLog(jobLogId, "FAILED", trc, rc, rcc, err);
                        else {
                            try {
                                Long id = beginJobLog(tgt.url, tgt.user);
                                if (id != null) safeCompleteJobLog(id, "FAILED", trc, rc, rcc, err);
                            } catch (Exception ignore) { /* no-op */ }
                        }
                    }
                }

                LOG.info("Mirror refresh FINISHED for {} target(s), took={} ms",
                        targets.size(), (System.currentTimeMillis() - t0));

            } catch (Exception e) {
                LOG.error("Mirror refresh FAILED to start: {}", safeMessage(e));
            }
        } finally {
            running.set(false);
        }
    }


    private int sync(TableSync cfg,
                     int batchSize,
                     String srcPwdEnc,
                     String dstUrl, String dstUser, String dstPwd) throws Exception {
        int total = 0;
        long lastId = 0L;

        while (true) {
            String[][] rows = dbEngine.select(
                    new ParametrizedQuery(cfg.selectSql, Arrays.asList(batchSize, lastId)),
                    null, null, srcUrl, srcUser, srcPwdEnc
            );
            if (rows.length == 0) break;

            int inserted = bulkInsertValues(
                    cfg.stagingTable,
                    cfg.columns,
                    cfg.mapper.map(rows),
                    dstUrl, dstUser, dstPwd
            );

            lastId = parseLongSafe(rows[rows.length - 1][0], lastId);
            total += inserted;
        }
        return total;
    }


    private Long beginJobLog(String targetUrl, String targetUser) throws Exception {
        org.json.JSONArray arr = dbEngine.executeQuery(
                "SELECT CAST(NEXT VALUE FOR dbo.PD_MIRROR_JOB_LOG_SEQ AS BIGINT) AS id",
                null, null, srcUrl, srcUser, srcPass);
        if (arr.length() == 0) {
            LOG.warn("Mirror job log sequence returned no id. Continuing without job log.");
            return null;
        }

        org.json.JSONObject row = arr.getJSONObject(0);
        if (!row.has("id") || row.isNull("id")) {
            LOG.warn("Mirror job log sequence returned a row without id. Continuing without job log.");
            return null;
        }

        Long id = Long.valueOf(row.getLong("id"));

        dbEngine.executeBatchParametrizedUpdate(
                new ParametrizedQuery(
                        "INSERT INTO dbo.pd_mirror_job_log " +
                                "(id, started_at, target_jdbc_url, target_username, status) " +
                                "VALUES (?, SYSDATETIME(), ?, ?, N'RUNNING')",
                        Arrays.<Object>asList(id, left(targetUrl, 500), left(targetUser, 500))
                ),
                null, srcUrl, srcUser, srcPass
        );
        return id;
    }

    private void completeJobLog(Long id, String status, int trc, int rc, int rcc, String error) throws Exception {
        dbEngine.executeBatchParametrizedUpdate(
                new ParametrizedQuery(
                        "UPDATE dbo.pd_mirror_job_log SET " +
                                " finished_at = SYSDATETIME(), " +
                                " status = ?, rows_trc = ?, rows_rc = ?, rows_rcc = ?, error_message = ? " +
                                "WHERE id = ?",
                        Arrays.<Object>asList(
                                left(status, 50),
                                trc, rc, rcc,
                                left(error, 500),
                                id
                        )
                ),
                null, srcUrl, srcUser, srcPass
        );
    }

    private void safeCompleteJobLog(Long id, String status, int trc, int rc, int rcc, String error) {
        try {
            completeJobLog(id, status, trc, rc, rcc, error);
        } catch (Exception ex) {
            LOG.warn("completeJobLog failed: {}", safeMessage(ex));
        }
    }

    private static String left(String s, int len) {
        if (s == null) return null;
        return (s.length() <= len) ? s : s.substring(0, len);
    }


    private List<MirrorTarget> loadMirrorTargets() throws Exception {
        String sql =
                "SELECT DISTINCT " +
                        "  LTRIM(RTRIM(db_connection_string))  AS jdbc_url, " +
                        "  LTRIM(RTRIM(db_username))           AS db_user, " +
                        "  LTRIM(RTRIM(db_password))           AS db_pass " +
                        "FROM dbo.pd_campaign " +
                        "WHERE db_connection_string IS NOT NULL " +
                        "  AND db_username          IS NOT NULL " +
                        "  AND db_password          IS NOT NULL";

        List<MirrorTarget> list = new ArrayList<MirrorTarget>();
        org.json.JSONArray rows = dbEngine.executeQuery(sql, null, null, srcUrl, srcUser, srcPass);

        HashSet<String> seenUrl = new HashSet<String>();
        for (int i = 0; i < rows.length(); i++) {
            org.json.JSONObject r = rows.getJSONObject(i);
            String url  = optStringTrim(r, "jdbc_url");
            String user = optStringTrim(r, "db_user");
            String pwd  = r.optString("db_pass", null);

            if (StringUtils.isBlank(url) || StringUtils.isBlank(user) || StringUtils.isBlank(pwd)) continue;
            if (equalsIgnoreCaseSafe(url, srcUrl)) continue;

            if (seenUrl.add(url)) list.add(new MirrorTarget(url, user, pwd));
        }
        return list;
    }


    private void ensureSchemaForTarget(String dstUrl, String dstUser, String dstPwd) throws Exception {
        List<ParametrizedQuery> ddl = Arrays.asList(
                new ParametrizedQuery(
                        "IF OBJECT_ID('dbo.wbp_result_code_stg','U') IS NULL " +
                                "BEGIN CREATE TABLE dbo.wbp_result_code_stg (id INT NOT NULL, result_code_text NVARCHAR(500) NULL); END",
                        Collections.emptyList()),
                new ParametrizedQuery(
                        "IF OBJECT_ID('dbo.wbp_result_code_status_criteria_stg','U') IS NULL " +
                                "BEGIN CREATE TABLE dbo.wbp_result_code_status_criteria_stg (" +
                                " id INT NOT NULL, result_code_id INT NULL, telephony_result_code_id INT NULL, statusCode NVARCHAR(500) NULL , group_id BIGINT NULL ); END",
                        Collections.emptyList()),
                new ParametrizedQuery(
                        "IF OBJECT_ID('dbo.pd_telephony_rc_stg','U') IS NULL " +
                                "BEGIN CREATE TABLE dbo.pd_telephony_rc_stg (id INT NOT NULL, name NVARCHAR(500) NULL); END",
                        Collections.emptyList()),
                new ParametrizedQuery(
                        "IF OBJECT_ID('dbo.wbp_result_code_local','U') IS NULL " +
                                "BEGIN CREATE TABLE dbo.wbp_result_code_local (id INT NOT NULL PRIMARY KEY, result_code_text NVARCHAR(500) NULL); END",
                        Collections.emptyList()),
                new ParametrizedQuery(
                        "IF OBJECT_ID('dbo.wbp_result_code_status_criteria_local','U') IS NULL " +
                                "BEGIN CREATE TABLE dbo.wbp_result_code_status_criteria_local (" +
                                " id INT NOT NULL PRIMARY KEY, result_code_id INT NULL, telephony_result_code_id INT NULL, statusCode NVARCHAR(500) NULL , group_id BIGINT NULL ); END",
                        Collections.emptyList()),
                new ParametrizedQuery(
                        "IF OBJECT_ID('dbo.pd_telephony_rc_local','U') IS NULL " +
                                "BEGIN CREATE TABLE dbo.pd_telephony_rc_local (id INT NOT NULL PRIMARY KEY, name NVARCHAR(500) NULL); END",
                        Collections.emptyList()),
                new ParametrizedQuery(
                        "IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='IX_rcc_local_rcid_status' " +
                                "AND object_id=OBJECT_ID('dbo.wbp_result_code_status_criteria_local')) " +
                                "CREATE NONCLUSTERED INDEX IX_rcc_local_rcid_status " +
                                "ON dbo.wbp_result_code_status_criteria_local (result_code_id, statusCode  );",
                        Collections.emptyList()),
                new ParametrizedQuery(
                        "IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='IX_rcc_local_trcid' " +
                                "AND object_id=OBJECT_ID('dbo.wbp_result_code_status_criteria_local')) " +
                                "CREATE NONCLUSTERED INDEX IX_rcc_local_trcid " +
                                "ON dbo.wbp_result_code_status_criteria_local (telephony_result_code_id) INCLUDE (statusCode, result_code_id );",
                        Collections.emptyList())
        );
        dbEngine.executeBatchParametrizedUpdate(ddl, null, dstUrl, dstUser, dstPwd);
    }

    private void truncateStaging(String dstUrl, String dstUser, String dstPwd) throws Exception {
        try {
            dbEngine.executeBatchParametrizedUpdate(Arrays.asList(
                    new ParametrizedQuery("TRUNCATE TABLE dbo.wbp_result_code_stg", Collections.emptyList()),
                    new ParametrizedQuery("TRUNCATE TABLE dbo.wbp_result_code_status_criteria_stg", Collections.emptyList()),
                    new ParametrizedQuery("TRUNCATE TABLE dbo.pd_telephony_rc_stg", Collections.emptyList())
            ), null, dstUrl, dstUser, dstPwd);
            LOG.info("Staging truncated");
        } catch (Exception ex) {
            LOG.warn("Truncate failed ({}). Ensuring schema and retrying once…", safeMessage(ex));
            ensureSchemaForTarget(dstUrl, dstUser, dstPwd);
            dbEngine.executeBatchParametrizedUpdate(Arrays.asList(
                    new ParametrizedQuery("TRUNCATE TABLE dbo.wbp_result_code_stg", Collections.emptyList()),
                    new ParametrizedQuery("TRUNCATE TABLE dbo.wbp_result_code_status_criteria_stg", Collections.emptyList()),
                    new ParametrizedQuery("TRUNCATE TABLE dbo.pd_telephony_rc_stg", Collections.emptyList())
            ), null, dstUrl, dstUser, dstPwd);
        }
    }

    private void swapToLive(String dstUrl, String dstUser, String dstPwd) throws Exception {
        dbEngine.executeBatchParametrizedUpdate(Arrays.asList(
                new ParametrizedQuery("DELETE FROM dbo.wbp_result_code_local", Collections.<Object>emptyList()),
                new ParametrizedQuery(
                        "INSERT INTO dbo.wbp_result_code_local (id, result_code_text) SELECT id, result_code_text FROM dbo.wbp_result_code_stg",
                        Collections.<Object>emptyList()
                ),
                new ParametrizedQuery("DELETE FROM dbo.wbp_result_code_status_criteria_local", Collections.<Object>emptyList()),
                new ParametrizedQuery(
                        "INSERT INTO dbo.wbp_result_code_status_criteria_local (id, result_code_id, telephony_result_code_id, statusCode , group_id ) " +
                                "SELECT id, result_code_id, telephony_result_code_id, statusCode , group_id  FROM dbo.wbp_result_code_status_criteria_stg",
                        Collections.<Object>emptyList()
                ),
                new ParametrizedQuery("DELETE FROM dbo.pd_telephony_rc_local", Collections.<Object>emptyList()),
                new ParametrizedQuery(
                        "INSERT INTO dbo.pd_telephony_rc_local (id, name) SELECT id, name FROM dbo.pd_telephony_rc_stg",
                        Collections.<Object>emptyList()
                )
        ), null, dstUrl, dstUser, dstPwd);
        LOG.info("Swap to live completed");
    }



    private static class MirrorTarget {
        final String url;
        final String user;
        final String encPass;
        MirrorTarget(String url, String user, String encPass) {
            this.url = url; this.user = user; this.encPass = encPass;
        }
    }

    private static Integer parseIntOrNull(String s) {
        try { return (s == null ? null : Integer.valueOf(s)); }
        catch (NumberFormatException e) { return null; }
    }

    private static long parseLongSafe(String s, long fallback) {
        try { return (s == null ? fallback : Long.parseLong(s)); }
        catch (NumberFormatException e) { return fallback; }
    }

    private static String optStringTrim(org.json.JSONObject o, String key) {
        if (o == null) return null;
        String v = o.optString(key, null);
        return StringUtils.trimToNull(v);
    }

    private static boolean equalsIgnoreCaseSafe(String a, String b) {
        return a == null ? b == null : (b != null && a.equalsIgnoreCase(b));
    }

    private String decryptSafe(String enc) {
        try { return enCipherUtils.decryptPassword(enc); }
        catch (Exception e) {
            LOG.warn("decryptPassword failed, using raw value. reason={}", safeMessage(e));
            return enc;
        }
    }

    private static String safeMessage(Throwable t) {
        String msg = (t == null ? "unknown" : t.getMessage());
        return msg == null ? "unknown" : msg.replaceAll("[\\r\\n]+", " ");
    }

    private int bulkInsertValues(String table,
                                 List<String> columns,
                                 List<List<Object>> paramRows,
                                 String dstUrl, String dstUser, String dstPwd) throws Exception {
        if (paramRows == null || paramRows.isEmpty()) return 0;

        final int cols = columns.size();
        final int MAX_PARAMS_SAFE = 2090;
        final int MAX_ROWS_SQL_SERVER = 1000;
        final int maxRowsPerInsert = Math.min(MAX_ROWS_SQL_SERVER, Math.max(1, MAX_PARAMS_SAFE / cols));

        int total = 0;
        int from = 0;
        while (from < paramRows.size()) {
            int to = Math.min(from + maxRowsPerInsert, paramRows.size());
            List<List<Object>> slice = paramRows.subList(from, to);

            StringBuilder sql = new StringBuilder(64 + slice.size() * cols * 3);
            sql.append("INSERT INTO ").append(table).append(" (")
                    .append(String.join(", ", columns)).append(") VALUES ");

            List<Object> flatParams = new ArrayList<Object>(slice.size() * cols);
            for (int i = 0; i < slice.size(); i++) {
                if (i > 0) sql.append(", ");
                sql.append('(');
                for (int c = 0; c < cols; c++) {
                    if (c > 0) sql.append(", ");
                    sql.append('?');
                }
                sql.append(')');
                flatParams.addAll(slice.get(i));
            }

            total += dbEngine.executeBatchParametrizedUpdate(
                    new ParametrizedQuery(sql.toString(), flatParams),
                    null, dstUrl, dstUser, dstPwd
            );
            from = to;
        }
        return total;
    }
}
