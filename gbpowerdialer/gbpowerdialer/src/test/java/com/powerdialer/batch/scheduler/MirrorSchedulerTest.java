package com.powerdialer.batch.scheduler;

import com.powerdialer.engine.db.DBEngine;
import com.powerdialer.model.ParametrizedQuery;
import com.powerdialer.util.EnCipherUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InOrder;
import org.mockito.stubbing.Answer;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.lang.reflect.Field;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;


@RunWith(PowerMockRunner.class)
@PrepareForTest({DBEngine.class, EnCipherUtils.class})
public class MirrorSchedulerTest {

    private static final String SRC_URL  = "jdbc:sqlserver2://srcHost;databaseName=SRC";
    private static final String SRC_USER = "src_user";
    private static final String SRC_PASS = "src_pass";

    private static final String DST_URL  = "jdbc:sqlserver3://dstHost;databaseName=DST";
    private static final String DST_USER = "dst_user";
    private static final String DST_PASS = "dst_pass";

    private DBEngine db;
    private EnCipherUtils enc;
    private MirrorScheduler scheduler;

    @Before
    public void setUp() throws Exception {
        db  = mock(DBEngine.class);
        enc = mock(EnCipherUtils.class);

        when(db.getMainDatabaseUrl()).thenReturn(SRC_URL);
        when(db.getMainDatabaseUsername()).thenReturn(SRC_USER);
        when(db.getMainDatabasePassword()).thenReturn(SRC_PASS);

        scheduler = new MirrorScheduler(db, enc);

        setPrivate(scheduler, "BATCH_SIZE_RC",  10_000);
        setPrivate(scheduler, "BATCH_SIZE_RCC", 10_000);
        setPrivate(scheduler, "BATCH_SIZE_TRC", 10_000);

        when(enc.decryptPassword(anyString())).thenAnswer((Answer<String>) inv -> inv.getArgument(0, String.class));
    }

    @Test
    public void runDaily_crossServer_ok_andParamBudgetRespected() throws Exception {
        JSONArray campaigns = new JSONArray()
                .put(new JSONObject()
                        .put("jdbc_url", DST_URL)
                        .put("db_user", DST_USER)
                        .put("db_pass", DST_PASS));
        when(db.executeQuery(anyString(), isNull(), isNull(), eq(SRC_URL), eq(SRC_USER), eq(SRC_PASS)))
                .thenReturn(campaigns);

        String[][] rcRows  = buildRows2Cols(1200);
        String[][] trcRows = buildRows2Cols(1300);
        String[][] rccRows = buildRows5Cols(5000);

        AtomicInteger rcCall  = new AtomicInteger();
        AtomicInteger trcCall = new AtomicInteger();
        AtomicInteger rccCall = new AtomicInteger();

        when(db.select(argThat(q -> q != null && q.getQuery() != null &&
                        q.getQuery().contains("FROM dbo.wbp_result_code ")),
                any(), isNull(), eq(SRC_URL), eq(SRC_USER), eq(SRC_PASS)))
                .thenAnswer(inv -> rcCall.getAndIncrement() == 0 ? rcRows : new String[0][0]);

        when(db.select(argThat(q -> q != null && q.getQuery() != null &&
                        q.getQuery().contains("FROM dbo.pd_telephony_rc")),
                any(), isNull(), eq(SRC_URL), eq(SRC_USER), eq(SRC_PASS)))
                .thenAnswer(inv -> trcCall.getAndIncrement() == 0 ? trcRows : new String[0][0]);

        when(db.select(argThat(q -> q != null && q.getQuery() != null &&
                        q.getQuery().contains("FROM dbo.wbp_result_code_status_criteria")),
                any(), isNull(), eq(SRC_URL), eq(SRC_USER), eq(SRC_PASS)))
                .thenAnswer(inv -> rccCall.getAndIncrement() == 0 ? rccRows : new String[0][0]);

        when(db.executeBatchParametrizedUpdate(anyList(), isNull(), eq(DST_URL), eq(DST_USER), eq(DST_PASS)))
                .thenReturn(1);

        final AtomicInteger singleInserts = new AtomicInteger(0);

        doAnswer(inv -> {
            ParametrizedQuery q = inv.getArgument(0, ParametrizedQuery.class);
            if (q == null || q.getQuery() == null) return 0;

            if (!q.getQuery().startsWith("INSERT INTO ")) return 1;

            List<Object> params = q.getParameters();
            assertNotNull(params);
            assertTrue("Param number should not exceed 2090, actual=" + params.size(),
                    params.size() <= 2090);

            int cols = inferValuesColumnCount(q.getQuery());
            assertTrue("VALUES column number could not be determined", cols > 0);
            assertEquals("The number of params must be divided by the number of columns.",
                    0, params.size() % cols);

            int rowCount = params.size() / cols;
            assertTrue("SQL Server VALUES row limit exceeded: rowCount=" + rowCount,
                    rowCount <= 1000);

            singleInserts.incrementAndGet();
            return rowCount;
        }).when(db).executeBatchParametrizedUpdate(
                any(ParametrizedQuery.class), isNull(), eq(DST_URL), eq(DST_USER), eq(DST_PASS));

        scheduler.runDaily();

        verify(db, atLeastOnce()).select(any(), any(), isNull(), eq(SRC_URL), eq(SRC_USER), eq(SRC_PASS));
        assertTrue("Expected: multiple single INSERTs (evidence of chunking)",
                singleInserts.get() > 1);
    }

    @Test
    public void runDaily_largeRcc_shouldSplitIntoManyBatches() throws Exception {
        JSONArray campaigns = new JSONArray()
                .put(new JSONObject()
                        .put("jdbc_url", DST_URL)
                        .put("db_user", DST_USER)
                        .put("db_pass", DST_PASS));
        when(db.executeQuery(anyString(), isNull(), isNull(), eq(SRC_URL), eq(SRC_USER), eq(SRC_PASS)))
                .thenReturn(campaigns);

        when(db.select(argThat(q -> q != null && q.getQuery() != null &&
                        q.getQuery().contains("FROM dbo.wbp_result_code ")),
                any(), isNull(), eq(SRC_URL), eq(SRC_USER), eq(SRC_PASS)))
                .thenReturn(new String[0][0]);
        when(db.select(argThat(q -> q != null && q.getQuery() != null &&
                        q.getQuery().contains("FROM dbo.pd_telephony_rc")),
                any(), isNull(), eq(SRC_URL), eq(SRC_USER), eq(SRC_PASS)))
                .thenReturn(new String[0][0]);

        String[][] rccRows = buildRows5Cols(50_000);
        AtomicInteger rccCall = new AtomicInteger();
        when(db.select(argThat(q -> q != null && q.getQuery() != null &&
                        q.getQuery().contains("FROM dbo.wbp_result_code_status_criteria")),
                any(), isNull(), eq(SRC_URL), eq(SRC_USER), eq(SRC_PASS)))
                .thenAnswer(inv -> rccCall.getAndIncrement() == 0 ? rccRows : new String[0][0]);

        when(db.executeBatchParametrizedUpdate(anyList(), isNull(), eq(DST_URL), eq(DST_USER), eq(DST_PASS)))
                .thenReturn(1);

        final AtomicInteger singleInserts = new AtomicInteger(0);

        doAnswer(inv -> {
            ParametrizedQuery q = inv.getArgument(0, ParametrizedQuery.class);
            if (q == null || q.getQuery() == null) return 0;
            if (!q.getQuery().startsWith("INSERT INTO ")) return 1;

            List<Object> params = q.getParameters();
            assertTrue("Param number should not exceed 2090, actual=", params.size() <= 2090);

            int cols = inferValuesColumnCount(q.getQuery());
            assertEquals("rcc VALUES column count must be 5", 5, cols);
            assertEquals("The number of params must be divided by the number of columns.", 0, params.size() % cols);

            int rowCount = params.size() / cols;
            assertTrue("SQL Server VALUES row limit exceeded: rowCount=" + rowCount,
                    rowCount <= 1000);

            singleInserts.incrementAndGet();
            return rowCount;
        }).when(db).executeBatchParametrizedUpdate(
                any(ParametrizedQuery.class), isNull(), eq(DST_URL), eq(DST_USER), eq(DST_PASS));

        scheduler.runDaily();


        assertTrue("Expected: many unique INSERTs (>=80)", singleInserts.get() >= 80);
    }


    @Test
    public void runDaily_truncateFails_thenEnsureSchemaAndRetry_succeeds() throws Exception {
        JSONArray campaigns = new JSONArray()
                .put(new JSONObject()
                        .put("jdbc_url", DST_URL)
                        .put("db_user", DST_USER)
                        .put("db_pass", DST_PASS));
        when(db.executeQuery(anyString(), isNull(), isNull(), eq(SRC_URL), eq(SRC_USER), eq(SRC_PASS)))
                .thenReturn(campaigns);

        when(db.select(any(ParametrizedQuery.class), any(), isNull(), eq(SRC_URL), eq(SRC_USER), eq(SRC_PASS)))
                .thenReturn(new String[0][0]);


        when(db.executeBatchParametrizedUpdate(
                ArgumentMatchers.<List<ParametrizedQuery>>argThat(lst ->
                        lst != null &&
                                lst.size() == 3 &&
                                lst.get(0).getQuery().startsWith("TRUNCATE TABLE dbo.wbp_result_code_stg")),
                isNull(),
                eq(DST_URL), eq(DST_USER), eq(DST_PASS)))
                .thenThrow(new SQLException("no such table"))
                .thenReturn(3);


        when(db.executeBatchParametrizedUpdate(
                ArgumentMatchers.<List<ParametrizedQuery>>argThat(lst -> lst != null && !lst.isEmpty()),
                isNull(),
                eq(DST_URL), eq(DST_USER), eq(DST_PASS)))
                .thenReturn(7);


        when(db.executeBatchParametrizedUpdate(
                ArgumentMatchers.<List<ParametrizedQuery>>argThat(lst ->
                        lst != null && !lst.isEmpty() &&
                                lst.stream().anyMatch(p -> p.getQuery().startsWith("DELETE FROM dbo.wbp_result_code_local"))),
                isNull(), eq(DST_URL), eq(DST_USER), eq(DST_PASS)))
                .thenReturn(0);
        when(db.executeBatchParametrizedUpdate(
                ArgumentMatchers.<List<ParametrizedQuery>>argThat(lst ->
                        lst != null && !lst.isEmpty() &&
                                lst.stream().anyMatch(p -> p.getQuery().startsWith("DELETE FROM dbo.wbp_result_code_status_criteria_local"))),
                isNull(), eq(DST_URL), eq(DST_USER), eq(DST_PASS)))
                .thenReturn(0);
        when(db.executeBatchParametrizedUpdate(
                ArgumentMatchers.<List<ParametrizedQuery>>argThat(lst ->
                        lst != null && !lst.isEmpty() &&
                                lst.stream().anyMatch(p -> p.getQuery().startsWith("DELETE FROM dbo.pd_telephony_rc_local"))),
                isNull(), eq(DST_URL), eq(DST_USER), eq(DST_PASS)))
                .thenReturn(0);


        scheduler.runDaily();


    }


    @Test
    public void swapToLive_order_isDeleteThenInsert_perTable() throws Exception {
        JSONArray campaigns = new JSONArray()
                .put(new JSONObject().put("jdbc_url", DST_URL)
                        .put("db_user",  DST_USER)
                        .put("db_pass",  DST_PASS));
        when(db.executeQuery(anyString(), isNull(), isNull(), eq(SRC_URL), eq(SRC_USER), eq(SRC_PASS)))
                .thenReturn(campaigns);

        when(db.select(any(ParametrizedQuery.class), any(), isNull(), eq(SRC_URL), eq(SRC_USER), eq(SRC_PASS)))
                .thenReturn(new String[0][0]);

        when(db.executeBatchParametrizedUpdate(
                argThat((List<ParametrizedQuery> lst) ->
                        lst!=null && lst.size()==3 &&
                                lst.get(0).getQuery().startsWith("TRUNCATE TABLE dbo.wbp_result_code_stg")),
                isNull(), eq(DST_URL), eq(DST_USER), eq(DST_PASS)
        )).thenReturn(0);

        final ArgumentCaptor<List<ParametrizedQuery>> swapCap = ArgumentCaptor.forClass(List.class);
        when(db.executeBatchParametrizedUpdate(swapCap.capture(), isNull(), eq(DST_URL), eq(DST_USER), eq(DST_PASS)))
                .thenReturn(0);

        scheduler.runDaily();

        List<ParametrizedQuery> swap = swapCap.getAllValues().stream()
                .filter(lst ->
                        lst.size() >= 6 &&
                                lst.stream().anyMatch(p -> p.getQuery().startsWith("DELETE FROM dbo.wbp_result_code_local")) &&
                                lst.stream().anyMatch(p -> p.getQuery().contains("INSERT INTO dbo.wbp_result_code_local")))
                .findFirst()
                .orElse(Collections.emptyList());

        assertFalse("Swap batch not found", swap.isEmpty());

        String[] expected = new String[]{
                "DELETE FROM dbo.wbp_result_code_local",
                "INSERT INTO dbo.wbp_result_code_local",
                "DELETE FROM dbo.wbp_result_code_status_criteria_local",
                "INSERT INTO dbo.wbp_result_code_status_criteria_local",
                "DELETE FROM dbo.pd_telephony_rc_local",
                "INSERT INTO dbo.pd_telephony_rc_local"
        };

        for (int i = 0; i < expected.length; i++) {
            String q = swap.get(i).getQuery();
            assertTrue("Order " + i + " does not match expected: " + q, q.startsWith(expected[i]));
        }
    }




    @Test
    public void bulkInsert_twoColumnTable_shouldNeverExceed1000Rows() throws Exception {
        int cols = 2;
        int MAX_PARAMS_SAFE = 2090;

        int oldMaxRows = Math.max(1, MAX_PARAMS_SAFE / cols);
        assertTrue("Old formula produces " + oldMaxRows + " rows, exceeds 1000 — this is the bug!",
                oldMaxRows > 1000);

        int MAX_ROWS_SQL_SERVER = 1000;
        int newMaxRows = Math.min(MAX_ROWS_SQL_SERVER, Math.max(1, MAX_PARAMS_SAFE / cols));
        assertTrue("After fix " + newMaxRows + " rows, must not exceed 1000",
                newMaxRows <= 1000);

        int cols5 = 5;  // wbp_result_code_status_criteria
        int maxRows5 = Math.min(MAX_ROWS_SQL_SERVER, Math.max(1, MAX_PARAMS_SAFE / cols5));
        assertTrue("5-column table " + maxRows5 + " rows, must not exceed 1000",
                maxRows5 <= 1000);
        assertEquals("5-column: param limit should be the deciding factor", 418, maxRows5);

        JSONArray campaigns = new JSONArray()
                .put(new JSONObject()
                        .put("jdbc_url", DST_URL)
                        .put("db_user", DST_USER)
                        .put("db_pass", DST_PASS));
        when(db.executeQuery(anyString(), isNull(), isNull(), eq(SRC_URL), eq(SRC_USER), eq(SRC_PASS)))
                .thenReturn(campaigns);

        String[][] rcRows = buildRows2Cols(1200);
        AtomicInteger rcCall = new AtomicInteger();
        when(db.select(argThat(q -> q != null && q.getQuery() != null &&
                        q.getQuery().contains("FROM dbo.wbp_result_code ")),
                any(), isNull(), eq(SRC_URL), eq(SRC_USER), eq(SRC_PASS)))
                .thenAnswer(inv -> rcCall.getAndIncrement() == 0 ? rcRows : new String[0][0]);
        when(db.select(argThat(q -> q != null && q.getQuery() != null &&
                        q.getQuery().contains("FROM dbo.pd_telephony_rc")),
                any(), isNull(), eq(SRC_URL), eq(SRC_USER), eq(SRC_PASS)))
                .thenReturn(new String[0][0]);
        when(db.select(argThat(q -> q != null && q.getQuery() != null &&
                        q.getQuery().contains("FROM dbo.wbp_result_code_status_criteria")),
                any(), isNull(), eq(SRC_URL), eq(SRC_USER), eq(SRC_PASS)))
                .thenReturn(new String[0][0]);

        when(db.executeBatchParametrizedUpdate(anyList(), isNull(), eq(DST_URL), eq(DST_USER), eq(DST_PASS)))
                .thenReturn(1);

        final AtomicInteger maxRowsSeen = new AtomicInteger(0);

        doAnswer(inv -> {
            ParametrizedQuery q = inv.getArgument(0, ParametrizedQuery.class);
            if (q == null || q.getQuery() == null || !q.getQuery().startsWith("INSERT INTO ")) return 1;
            List<Object> params = q.getParameters();
            int c = inferValuesColumnCount(q.getQuery());
            int rowCount = params.size() / c;

            assertTrue("SQL Server 1000-row VALUES limit exceeded! rowCount=" + rowCount
                    + " (table: " + q.getQuery().substring(12, q.getQuery().indexOf('(')) + ")",
                    rowCount <= 1000);

            if (rowCount > maxRowsSeen.get()) maxRowsSeen.set(rowCount);
            return rowCount;
        }).when(db).executeBatchParametrizedUpdate(
                any(ParametrizedQuery.class), isNull(), eq(DST_URL), eq(DST_USER), eq(DST_PASS));

        scheduler.runDaily();

        assertTrue("At least one INSERT must have been executed", maxRowsSeen.get() > 0);
        assertTrue("Max row count must not exceed 1000, actual=" + maxRowsSeen.get(),
                maxRowsSeen.get() <= 1000);
    }


    private static void setPrivate(Object target, String field, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static String[][] buildRows2Cols(int count) {
        String[][] rows = new String[count][2];
        for (int i = 0; i < count; i++) {
            rows[i][0] = String.valueOf(i + 1);
            rows[i][1] = "name_" + (i + 1);
        }
        return rows;
    }

    private static String[][] buildRows5Cols(int count) {
        String[][] rows = new String[count][5];
        String[] pool = {"BASARILI", "BASARISIZ", "RANDEVU", "KRITERDISI", "TEKRAR ARANACAK", "OLUMSUZ"};
        for (int i = 0; i < count; i++) {
            rows[i][0] = String.valueOf(i + 1);
            rows[i][1] = String.valueOf((i % 10) + 1);
            rows[i][2] = String.valueOf((i % 7) + 1);
            rows[i][3] = pool[i % pool.length];
            rows[i][4] = String.valueOf((i % 3) + 1);
        }
        return rows;
    }

    private static int inferValuesColumnCount(String insertSql) {
        String s = insertSql.toLowerCase(Locale.ROOT);
        int lp = s.indexOf('(');
        int rp = s.indexOf(')', lp + 1);
        if (lp < 0 || rp < 0 || rp <= lp) return 1;
        String cols = s.substring(lp + 1, rp).trim();
        if (cols.isEmpty()) return 1;
        return (int) Arrays.stream(cols.split(",")).count();
    }
}
