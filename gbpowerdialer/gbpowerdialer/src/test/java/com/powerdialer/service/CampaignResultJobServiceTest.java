package com.powerdialer.service;

import com.powerdialer.dao.IJobConfigDao;
import com.powerdialer.dao.ICampaignJobLogDao;
import com.powerdialer.dao.ICampaignJobResultLogDao;
import com.powerdialer.dto.JobConfigCampaignDTO;
import com.powerdialer.engine.db.DBEngine;
import com.powerdialer.model.CampaignJobLog;
import com.powerdialer.model.ParametrizedQuery;
import com.powerdialer.model.User;
import com.powerdialer.util.Constants;
import com.powerdialer.util.EnCipherUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.*;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.sql.SQLException;
import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class CampaignResultJobServiceTest {

    @Mock private IJobConfigDao jobConfigDao;
    @Mock private ICampaignJobLogDao campaignJobLogDao;
    @Mock private ICampaignJobResultLogDao campaignJobResultLogDao;
    @Mock private DBEngine dbEngine;
    @Mock private EnCipherUtils enCipherUtils;
    @Mock private UserService userService;

    private CampaignResultJobService service;
    private Fixture fixture;

    @Before
    public void setUp() {
        PlatformTransactionManager txManager = new PlatformTransactionManager() {
            @Override public TransactionStatus getTransaction(TransactionDefinition d) { return new SimpleTransactionStatus(); }
            @Override public void commit(TransactionStatus s) {}
            @Override public void rollback(TransactionStatus s) {}
        };

        service = new CampaignResultJobService(
                jobConfigDao, campaignJobLogDao, campaignJobResultLogDao,
                dbEngine, enCipherUtils, userService, txManager
        );

        fixture = new Fixture();
    }

    @SuppressWarnings("unchecked")
    private static java.util.List<Object> extractParamsFlexible(Object q) {
        if (q == null) return java.util.Collections.emptyList();

        String[] getterNames = { "getParams", "getParameters", "getArgs", "getArguments", "params", "parameters" };
        for (String g : getterNames) {
            try {
                java.lang.reflect.Method m = q.getClass().getMethod(g);
                Object r = m.invoke(q);
                if (r instanceof java.util.List) return (java.util.List<Object>) r;
                if (r != null && r.getClass().isArray()) {
                    Object[] arr = (Object[]) r;
                    return java.util.Arrays.asList(arr);
                }
            } catch (NoSuchMethodException ignore) {
            } catch (Exception ignore) { }
        }

        Class<?> c = q.getClass();
        while (c != null && c != Object.class) {
            for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                try {
                    f.setAccessible(true);
                    Object r = f.get(q);
                    if (r instanceof java.util.List) return (java.util.List<Object>) r;
                    if (r != null && r.getClass().isArray()) {
                        Object[] arr = (Object[]) r;
                        return java.util.Arrays.asList(arr);
                    }
                    if (r != null && !(r instanceof String) && !(r.getClass().isPrimitive())) {
                        for (java.lang.reflect.Field f2 : r.getClass().getDeclaredFields()) {
                            try {
                                f2.setAccessible(true);
                                Object r2 = f2.get(r);
                                if (r2 instanceof java.util.List) return (java.util.List<Object>) r2;
                                if (r2 != null && r2.getClass().isArray()) {
                                    Object[] arr = (Object[]) r2;
                                    return java.util.Arrays.asList(arr);
                                }
                            } catch (Exception ignore) { }
                        }
                    }
                } catch (Exception ignore) { }
            }
            c = c.getSuperclass();
        }
        return java.util.Collections.emptyList();
    }

    @Test
    public void runJob_noEligibleConfigs_doesNothing() {
        fixture.givenEligibleJobConfigIds(Collections.emptyList());
        fixture.whenRunJob();
        fixture.thenEligibleScanPerformedOnce();
        fixture.thenNoInteractions(dbEngine, campaignJobLogDao, campaignJobResultLogDao, enCipherUtils, userService);
    }

    @Test
    public void runJob_expireUserMissing_skipsRow() {
        fixture.givenEligibleJobConfigIds(Arrays.asList(1L));
        fixture.givenCampaignRowsForIds(
                Collections.singletonList(
                        TestData.row()
                                .jobConfigId(1L).campaignJobId(101L).campaignId(1001L)
                                .table("ResultTable")
                                .connection("jdbc:url").dbUser("sa").encPwd("enc")
                                .expireUserId(777L).resultCodeId(null).jobName("JobX")
                                .build()
                )
        );
        fixture.givenEmptyFilters();
        when(userService.getUserById(777L)).thenReturn(null);
        fixture.whenRunJob();
        verify(userService, times(1)).getUserById(777L);
        fixture.thenNoInteractions(dbEngine, campaignJobLogDao, campaignJobResultLogDao, enCipherUtils);
    }

    @Test
    public void runJob_missingConnection_marksFailed() {
        fixture.givenEligibleJobConfigIds(Arrays.asList(1L));
        JobConfigCampaignDTO dto = TestData.row()
                .jobConfigId(1L).campaignJobId(101L).campaignId(1001L)
                .table("ResultTable")
                .connection(null)
                .dbUser("sa").encPwd("enc")
                .expireUserId(null).resultCodeId(null).jobName("JobX")
                .build();
        fixture.givenCampaignRowsForIds(Collections.singletonList(dto));
        fixture.givenEmptyFilters();

        CampaignJobLog log = new CampaignJobLog();
        when(campaignJobLogDao.createLog(anyLong(), anyLong(), anyString(), any(), any(), any(), isNull()))
                .thenReturn(log);

        fixture.whenRunJob();

        verify(campaignJobLogDao).updateLog(
                eq(log),
                eq(Constants.CAMPAIGN_JOB_RESULT_STATUS.JOB_RESULT_FAILED),
                eq("Connection information missing.")
        );
        fixture.thenNoInteractions(dbEngine);
    }

    @Test
    public void runJob_emptyWhere_skipsAndMarksLog() throws Exception {
        fixture.givenEligibleJobConfigIds(Arrays.asList(1L));
        JobConfigCampaignDTO dto = TestData.row()
                .jobConfigId(1L).campaignJobId(101L).campaignId(1001L)
                .table("ResultTable")
                .connection("jdbc:url").dbUser("sa").encPwd("enc")
                .expireUserId(null).resultCodeId(null).jobName("JobX")
                .build();
        fixture.givenCampaignRowsForIds(Collections.singletonList(dto));
        fixture.givenEmptyFilters();
        when(enCipherUtils.decryptPassword("enc")).thenReturn("plain");
        fixture.givenWhitelistColumns("ResultTable", "id", "is_disabled", "UpdatedDate");
        CampaignJobLog log = new CampaignJobLog();
        when(campaignJobLogDao.createLog(anyLong(), anyLong(), anyString(), any(), any(), any(), isNull()))
                .thenReturn(log);
        fixture.whenRunJob();

        verify(campaignJobLogDao).updateLog(
                eq(log),
                eq(Constants.CAMPAIGN_JOB_RESULT_STATUS.JOB_RESULT_SKIPPED),
                eq("Filter condition is empty.")
        );
        verify(dbEngine, never()).executeBatchParametrizedUpdate(
                any(ParametrizedQuery.class), anyLong(), anyString(), anyString(), anyString()
        );
        verify(campaignJobResultLogDao, never()).createLogsBulk(any(CampaignJobLog.class), anyList());
    }

    @Test
    public void runJob_noSelectedIds_skipsWithoutUpdate() throws Exception {
        fixture.givenEligibleJobConfigIds(Arrays.asList(1L));
        JobConfigCampaignDTO dto = TestData.row()
                .jobConfigId(1L).campaignJobId(101L).campaignId(1001L)
                .table("ResultTable")
                .connection("jdbc:url").dbUser("sa").encPwd("enc")
                .jobName("JobX")
                .build();
        fixture.givenCampaignRowsForIds(Collections.singletonList(dto));
        fixture.givenStructuredFilters(
                Collections.singletonList(
                        new com.powerdialer.dto.JobConfigFilterDTO(
                                1L, 10L, "is_disabled",
                                com.powerdialer.util.Constants.FILTER_CONDITION.EQUAL,
                                "0",
                                null,
                                null,
                                com.powerdialer.util.Constants.FILTER_TYPE.PREDEFINED,
                                null
                        )
                )
        );

        when(enCipherUtils.decryptPassword("enc")).thenReturn("plain");
        fixture.givenWhitelistColumns("ResultTable", "id", "is_disabled", "UpdatedDate");
        fixture.givenSelectIdsReturnsEmpty();
        CampaignJobLog log = new CampaignJobLog();
        when(campaignJobLogDao.createLog(anyLong(), anyLong(), anyString(), any(), any(), any(), isNull()))
                .thenReturn(log);

        fixture.whenRunJob();

        verify(campaignJobLogDao).updateLog(
                eq(log),
                eq(Constants.CAMPAIGN_JOB_RESULT_STATUS.JOB_RESULT_SKIPPED),
                eq("No data found in the result table that meets the requirements.")
        );

        verify(dbEngine, never()).executeBatchParametrizedUpdate(
                any(com.powerdialer.model.ParametrizedQuery.class),
                anyLong(), anyString(), anyString(), anyString()
        );
        verify(campaignJobResultLogDao, never()).createLogsBulk(any(CampaignJobLog.class), anyList());
    }

    @Test
    public void runJob_happyPath_updatesAndLogsSuccessful() throws Exception {
        fixture.givenEligibleJobConfigIds(Arrays.asList(1L));
        JobConfigCampaignDTO dto = TestData.row()
                .jobConfigId(1L).campaignJobId(101L).campaignId(1001L)
                .table("ResultTable")
                .connection("jdbc:url").dbUser("sa").encPwd("enc")
                .resultCodeId(55L)
                .jobName("JobX")
                .build();
        fixture.givenCampaignRowsForIds(Collections.singletonList(dto));
        fixture.givenStructuredFilters(Collections.singletonList(
                new com.powerdialer.dto.JobConfigFilterDTO(
                        1L, 10L, "is_disabled",
                        com.powerdialer.util.Constants.FILTER_CONDITION.EQUAL,
                        "0",
                        null, null,
                        com.powerdialer.util.Constants.FILTER_TYPE.PREDEFINED,
                        null
                )
        ));

        when(enCipherUtils.decryptPassword("enc")).thenReturn("plain");
        fixture.givenWhitelistColumns("ResultTable", "id", "is_disabled", "user_result_code", "UpdatedUser", "UpdatedDate");
        fixture.givenSelectIdsReturn(1010L, 2020L);
        fixture.givenUpdateAffectsRows(2);

        CampaignJobLog log = new CampaignJobLog();
        when(campaignJobLogDao.createLog(anyLong(), anyLong(), anyString(), any(), any(), any(), isNull()))
                .thenReturn(log);
        when(campaignJobResultLogDao.createLogsBulk(eq(log), anyList())).thenReturn(2);
        fixture.whenRunJob();

        verify(dbEngine, times(1)).executeBatchParametrizedUpdate(
                any(com.powerdialer.model.ParametrizedQuery.class),
                eq(1001L), anyString(), anyString(), anyString()
        );

        verify(campaignJobResultLogDao).createLogsBulk(eq(log), argThat(list -> list.size() == 2));

        verify(campaignJobLogDao).updateLog(eq(log),
                eq(Constants.CAMPAIGN_JOB_RESULT_STATUS.JOB_RESULT_SUCCESSFUL),
                isNull());
    }

    @Test
    public void runJob_updateThrows_marksFailedAndNoResultLog() throws Exception {
        fixture.givenEligibleJobConfigIds(Arrays.asList(1L));
        JobConfigCampaignDTO dto = TestData.row()
                .jobConfigId(1L).campaignJobId(101L).campaignId(1001L)
                .table("ResultTable")
                .connection("jdbc:url").dbUser("sa").encPwd("enc")
                .jobName("JobX")
                .build();
        fixture.givenCampaignRowsForIds(Collections.singletonList(dto));

        fixture.givenStructuredFilters(Collections.singletonList(
                new com.powerdialer.dto.JobConfigFilterDTO(
                        1L, 10L, "is_disabled",
                        com.powerdialer.util.Constants.FILTER_CONDITION.EQUAL,
                        "0",
                        null, null,
                        com.powerdialer.util.Constants.FILTER_TYPE.PREDEFINED,
                        null
                )
        ));

        when(enCipherUtils.decryptPassword("enc")).thenReturn("plain");
        fixture.givenWhitelistColumns("ResultTable", "id", "is_disabled", "UpdatedDate");
        fixture.givenSelectIdsReturn(111L);

        CampaignJobLog log = new CampaignJobLog();
        when(campaignJobLogDao.createLog(anyLong(), anyLong(), anyString(), any(), any(), any(), isNull()))
                .thenReturn(log);

        fixture.givenUpdateThrows(new RuntimeException("boom"));
        fixture.whenRunJob();
        verify(campaignJobLogDao).updateLog(
                eq(log),
                eq(Constants.CAMPAIGN_JOB_RESULT_STATUS.JOB_RESULT_FAILED),
                eq("boom")
        );

        verify(campaignJobResultLogDao, never()).createLogsBulk(any(CampaignJobLog.class), anyList());
    }

    @Test
    public void runJob_whitelistHasNoColumns_marksFailedEarly() throws Exception {
        fixture.givenEligibleJobConfigIds(Arrays.asList(1L));

        JobConfigCampaignDTO dto = TestData.row()
                .jobConfigId(1L).campaignJobId(101L).campaignId(1001L)
                .table("MissingTable")
                .connection("jdbc:url").dbUser("sa").encPwd("enc")
                .jobName("JobX")
                .build();
        fixture.givenCampaignRowsForIds(Collections.singletonList(dto));
        fixture.givenStructuredFilters(Collections.singletonList(
                new com.powerdialer.dto.JobConfigFilterDTO(
                        1L, 10L, "is_disabled",
                        com.powerdialer.util.Constants.FILTER_CONDITION.EQUAL,
                        "0", null, null,
                        com.powerdialer.util.Constants.FILTER_TYPE.PREDEFINED,
                        null
                )
        ));

        when(enCipherUtils.decryptPassword("enc")).thenReturn("plain");

        fixture.givenWhitelistEmpty();

        CampaignJobLog log = new CampaignJobLog();
        when(campaignJobLogDao.createLog(anyLong(), anyLong(), anyString(), any(), any(), any(), isNull()))
                .thenReturn(log);

        fixture.whenRunJob();

        verify(campaignJobLogDao).updateLog(
                eq(log),
                eq(Constants.CAMPAIGN_JOB_RESULT_STATUS.JOB_RESULT_FAILED),
                argThat(msg -> msg != null && msg.contains("Table not found or has no columns"))
        );

        verify(dbEngine, never()).executeParametrizedQuery(
                argThat(q -> q != null && q.getQuery() != null &&
                        q.getQuery().startsWith("SELECT id AS campaign_id")),
                anyLong(), anyInt(), anyString(), anyString(), anyString()
        );
        verify(dbEngine, never()).executeBatchParametrizedUpdate(
                any(com.powerdialer.model.ParametrizedQuery.class),
                anyLong(), anyString(), anyString(), anyString()
        );
        verify(campaignJobResultLogDao, never()).createLogsBulk(any(CampaignJobLog.class), anyList());
    }

    @Test
    public void runJob_setClause_includesResultCodeAndUpdatedUser() throws Exception {
        fixture.givenEligibleJobConfigIds(Arrays.asList(1L));

        JobConfigCampaignDTO dto = TestData.row()
                .jobConfigId(1L).campaignJobId(101L).campaignId(1001L)
                .table("ResultTable")
                .connection("jdbc:url").dbUser("sa").encPwd("enc")
                .resultCodeId(77L)
                .expireUserId(777L)
                .jobName("JobX")
                .build();
        fixture.givenCampaignRowsForIds(Collections.singletonList(dto));
        when(userService.getUserById(777L)).thenReturn(new com.powerdialer.model.User());

        fixture.givenStructuredFilters(Collections.singletonList(
                new com.powerdialer.dto.JobConfigFilterDTO(
                        1L, 10L, "is_disabled",
                        com.powerdialer.util.Constants.FILTER_CONDITION.EQUAL,
                        "0", null, null,
                        com.powerdialer.util.Constants.FILTER_TYPE.PREDEFINED,
                        null
                )
        ));

        when(enCipherUtils.decryptPassword("enc")).thenReturn("plain");

        fixture.givenWhitelistColumns("ResultTable", "id", "is_disabled", "user_result_code", "UpdatedUser", "UpdatedDate");
        fixture.givenSelectIdsReturn(999L);

        fixture.givenUpdateAffectsRows(1);

        User expUser = new User();
        when(userService.getUserById(777L)).thenReturn(expUser);

        CampaignJobLog log = new CampaignJobLog();
        when(campaignJobLogDao.createLog(
                eq(1L), eq(1001L), anyString(),
                any(), any(), any(),
                eq(expUser)
        )).thenReturn(log);

        when(campaignJobResultLogDao.createLogsBulk(any(CampaignJobLog.class), anyList())).thenReturn(1);

        ArgumentCaptor<com.powerdialer.model.ParametrizedQuery> updateCaptor =
                ArgumentCaptor.forClass(com.powerdialer.model.ParametrizedQuery.class);

        fixture.whenRunJob();
        verify(dbEngine, times(1)).executeBatchParametrizedUpdate(
                updateCaptor.capture(),
                eq(1001L), anyString(), anyString(), anyString()
        );

        String updateSql = updateCaptor.getValue().getQuery();
        assertTrue(updateSql.contains("UPDATE ResultTable SET"));
        assertTrue(updateSql.contains("is_disabled = ?"));
        assertTrue(updateSql.contains("[user_result_code] = ?"));
        assertTrue(updateSql.contains("[UpdatedUser] = ?"));
        assertTrue(updateSql.contains("[UpdatedDate] = ?"));
        assertTrue(updateSql.contains(" WHERE "));

        java.util.List<Object> params = extractParamsFlexible(updateCaptor.getValue());
        if (!params.isEmpty()) {
            assertTrue("params should be >=5", params.size() >= 5);
            assertEquals(1, params.get(0));
            assertEquals(77L, ((Number) params.get(1)).longValue());
            assertEquals(777L, ((Number) params.get(2)).longValue());
            assertTrue(params.get(3) instanceof java.sql.Timestamp);

            boolean hasZeroInTail = params.subList(4, params.size()).stream()
                    .anyMatch(p -> String.valueOf(p).equals("0")
                            || (p instanceof Number && ((Number) p).intValue() == 0));
            assertTrue("WHERE param (0) finded", hasZeroInTail);
        }

        verify(campaignJobResultLogDao).createLogsBulk(eq(log), argThat(list -> list.size() == 1));


        verify(campaignJobLogDao).updateLog(eq(log),
                eq(Constants.CAMPAIGN_JOB_RESULT_STATUS.JOB_RESULT_SUCCESSFUL),
                isNull());
    }

    @Test
    public void runJob_filterWithUnknownColumn_skipsAsEmptyWhere() throws Exception {
        fixture.givenEligibleJobConfigIds(Arrays.asList(1L));

        JobConfigCampaignDTO dto = TestData.row()
                .jobConfigId(1L).campaignJobId(101L).campaignId(1001L)
                .table("ResultTable")
                .connection("jdbc:url").dbUser("sa").encPwd("enc")
                .jobName("JobX")
                .build();
        fixture.givenCampaignRowsForIds(Collections.singletonList(dto));

        fixture.givenStructuredFilters(Collections.singletonList(
                new com.powerdialer.dto.JobConfigFilterDTO(
                        1L, 10L, "not_a_real_column",
                        com.powerdialer.util.Constants.FILTER_CONDITION.EQUAL,
                        "123", null, null,
                        com.powerdialer.util.Constants.FILTER_TYPE.PREDEFINED,
                        null
                )
        ));

        when(enCipherUtils.decryptPassword("enc")).thenReturn("plain");
        fixture.givenWhitelistColumns("ResultTable", "id", "is_disabled", "UpdatedDate");
        CampaignJobLog log = new CampaignJobLog();
        when(campaignJobLogDao.createLog(anyLong(), anyLong(), anyString(), any(), any(), any(), any()))
                .thenReturn(log);
        fixture.whenRunJob();
        verify(campaignJobLogDao).updateLog(
                eq(log),
                eq(Constants.CAMPAIGN_JOB_RESULT_STATUS.JOB_RESULT_SKIPPED),
                eq("Filter condition is empty.")
        );
        verify(dbEngine, never()).executeParametrizedQuery(
                argThat(q -> q != null && q.getQuery() != null &&
                        q.getQuery().startsWith("SELECT id AS campaign_id")),
                anyLong(), anyInt(), anyString(), anyString(), anyString()
        );
        verify(dbEngine, never()).executeBatchParametrizedUpdate(
                any(ParametrizedQuery.class), anyLong(), anyString(), anyString(), anyString()
        );
        verify(campaignJobResultLogDao, never()).createLogsBulk(any(CampaignJobLog.class), anyList());
    }

    @Test
    public void runJob_expireThresholdDays_buildsDateOnlyWhere_andUpdates() throws Exception {
        fixture.givenEligibleJobConfigIds(Arrays.asList(1L));
        JobConfigCampaignDTO dto = TestData.row()
                .jobConfigId(1L).campaignJobId(101L).campaignId(1001L)
                .table("ResultTable")
                .connection("jdbc:url").dbUser("sa").encPwd("enc")
                .jobName("JobX")
                .build();
        fixture.givenCampaignRowsForIds(Collections.singletonList(dto));

        fixture.givenStructuredFilters(Collections.singletonList(
                new com.powerdialer.dto.JobConfigFilterDTO(
                        1L, 20L, "expire_date",
                        com.powerdialer.util.Constants.FILTER_CONDITION.GREATEROREQUAL,
                        null,
                        null,
                        3,
                        com.powerdialer.util.Constants.FILTER_TYPE.PREDEFINED,
                        null
                )
        ));

        when(enCipherUtils.decryptPassword("enc")).thenReturn("plain");
        fixture.givenWhitelistColumns("ResultTable",
                "id", "is_disabled", "expire_date", "UpdatedDate");

        fixture.givenSelectIdsReturn(11L, 22L, 33L);
        fixture.givenUpdateAffectsRows(3);
        CampaignJobLog log = new CampaignJobLog();
        when(campaignJobLogDao.createLog(anyLong(), anyLong(), anyString(), any(), any(), any(), any()))
                .thenReturn(log);
        when(campaignJobResultLogDao.createLogsBulk(eq(log), anyList())).thenReturn(3);

        fixture.whenRunJob();

        ArgumentCaptor<com.powerdialer.model.ParametrizedQuery> updateCap =
                ArgumentCaptor.forClass(com.powerdialer.model.ParametrizedQuery.class);
        verify(dbEngine).executeBatchParametrizedUpdate(
                updateCap.capture(), eq(1001L), anyString(), anyString(), anyString());

        String upSql = updateCap.getValue().getQuery();
        assertTrue(
                "UPDATE must contain expire_date date-only compare",
                upSql.contains("CAST(expire_date AS date)") &&
                        upSql.toUpperCase().contains("DATEADD(DAY, -3, GETDATE())")
        );

        java.util.List<Object> params = extractParamsFlexible(updateCap.getValue());
        assertTrue("params should be >= 2 (SET side)", params.size() >= 2);

        verify(campaignJobResultLogDao).createLogsBulk(eq(log),
                argThat(list -> list.size() == 3));
        verify(campaignJobLogDao).updateLog(eq(log),
                eq(Constants.CAMPAIGN_JOB_RESULT_STATUS.JOB_RESULT_SUCCESSFUL), isNull());
    }

    @Test
    public void runJob_thresholdWithInvalidOperator_marksFailed() throws Exception {
        fixture.givenEligibleJobConfigIds(Arrays.asList(1L));

        JobConfigCampaignDTO dto = TestData.row()
                .jobConfigId(1L).campaignJobId(101L).campaignId(1001L)
                .table("ResultTable")
                .connection("jdbc:url").dbUser("sa").encPwd("enc")
                .jobName("JobX")
                .build();
        fixture.givenCampaignRowsForIds(Collections.singletonList(dto));

        com.powerdialer.dto.JobConfigFilterDTO invalid =
                new com.powerdialer.dto.JobConfigFilterDTO(
                        1L, 10L, "expire_date",
                        Constants.FILTER_CONDITION.EQUAL,
                        null, null,
                        3,
                        com.powerdialer.util.Constants.FILTER_TYPE.PREDEFINED,
                        null
                );
        fixture.givenStructuredFilters(Collections.singletonList(invalid));

        when(enCipherUtils.decryptPassword("enc")).thenReturn("plain");
        fixture.givenWhitelistColumns("ResultTable", "expire_date", "id");

        CampaignJobLog log = new CampaignJobLog();
        when(campaignJobLogDao.createLog(anyLong(), anyLong(), anyString(), any(), any(), any(), isNull()))
                .thenReturn(log);

        fixture.whenRunJob();

        verify(campaignJobLogDao).updateLog(
                eq(log),
                eq(Constants.CAMPAIGN_JOB_RESULT_STATUS.JOB_RESULT_FAILED),
                argThat(msg -> msg != null && msg.contains("Unsupported compare operator for expireDays"))
        );

        verify(dbEngine, never()).executeBatchParametrizedUpdate(
                any(ParametrizedQuery.class), anyLong(), anyString(), anyString(), anyString()
        );
    }
    @Test
    public void runJob_thresholdPrecedence_overValueAndDate() throws Exception {
        fixture.givenEligibleJobConfigIds(Arrays.asList(1L));

        JobConfigCampaignDTO dto = TestData.row()
                .jobConfigId(1L).campaignJobId(101L).campaignId(1001L)
                .table("ResultTable")
                .connection("jdbc:url").dbUser("sa").encPwd("enc")
                .jobName("JobX")
                .build();
        fixture.givenCampaignRowsForIds(Collections.singletonList(dto));


        com.powerdialer.dto.JobConfigFilterDTO f = new com.powerdialer.dto.JobConfigFilterDTO(
                1L, 10L,
                "expire_date",
                com.powerdialer.util.Constants.FILTER_CONDITION.GREATEROREQUAL,
                "2024-01-01",
                new java.util.Date(),
                3,
                com.powerdialer.util.Constants.FILTER_TYPE.PREDEFINED,
                null
        );
        fixture.givenStructuredFilters(Collections.singletonList(f));

        when(enCipherUtils.decryptPassword("enc")).thenReturn("plain");
        fixture.givenWhitelistColumns("ResultTable", "id", "expire_date", "UpdatedDate", "is_disabled");


        final java.util.concurrent.atomic.AtomicReference<com.powerdialer.model.ParametrizedQuery> seenSelect
                = new java.util.concurrent.atomic.AtomicReference<>();

        org.json.JSONArray ids = new org.json.JSONArray()
                .put(new org.json.JSONObject().put("campaign_id", 555));

        when(dbEngine.executeParametrizedQuery(
                argThat(q -> {
                    if (q != null && q.getQuery() != null &&
                            q.getQuery().startsWith("SELECT id AS campaign_id")) {
                        seenSelect.set(q);
                        return true;
                    }
                    return false;
                }),
                anyLong(), anyInt(), anyString(), anyString(), anyString()
        )).thenReturn(ids);

        when(dbEngine.executeBatchParametrizedUpdate(any(ParametrizedQuery.class),
                anyLong(), anyString(), anyString(), anyString())).thenReturn(1);

        CampaignJobLog log = new CampaignJobLog();
        when(campaignJobLogDao.createLog(anyLong(), anyLong(), anyString(), any(), any(), any(), isNull()))
                .thenReturn(log);
        when(campaignJobResultLogDao.createLogsBulk(eq(log), anyList())).thenReturn(1);

        fixture.whenRunJob();

        com.powerdialer.model.ParametrizedQuery selectQ = seenSelect.get();
        org.junit.Assert.assertNotNull("SELECT query not captured", selectQ);

        String whereSql = selectQ.getQuery();
        assertTrue(whereSql, whereSql.contains("DATEADD(DAY, -3"));
        assertTrue(whereSql, whereSql.contains("CAST(expire_date AS date) >="));

        org.junit.Assert.assertFalse(whereSql, whereSql.contains("CAST(? AS date)"));
        org.junit.Assert.assertFalse(whereSql, whereSql.contains("expire_date = ?"));

        java.util.List<Object> selectParams = extractParamsFlexible(selectQ);
        assertTrue("SELECT should have no params when using threshold", selectParams.isEmpty());

        verify(campaignJobLogDao).updateLog(eq(log),
                eq(Constants.CAMPAIGN_JOB_RESULT_STATUS.JOB_RESULT_SUCCESSFUL),
                isNull());
    }

    @Test
    public void runJob_dateValueTakesPrecedenceOverValue() throws Exception {
        fixture.givenEligibleJobConfigIds(Arrays.asList(1L));
        JobConfigCampaignDTO dto = TestData.row()
                .jobConfigId(1L).campaignJobId(101L).campaignId(1001L)
                .table("ResultTable")
                .connection("jdbc:url").dbUser("sa").encPwd("enc")
                .jobName("JobX")
                .build();
        fixture.givenCampaignRowsForIds(Collections.singletonList(dto));

        com.powerdialer.dto.JobConfigFilterDTO f =
                new com.powerdialer.dto.JobConfigFilterDTO(
                        1L, 10L,
                        "expire_date",
                        com.powerdialer.util.Constants.FILTER_CONDITION.GREATEROREQUAL,
                        "2024-01-01",
                        new java.util.Date(),
                        null,
                        com.powerdialer.util.Constants.FILTER_TYPE.PREDEFINED,
                        null
                );
        fixture.givenStructuredFilters(Collections.singletonList(f));

        when(enCipherUtils.decryptPassword("enc")).thenReturn("plain");
        fixture.givenWhitelistColumns("ResultTable", "id", "expire_date", "UpdatedDate");

        fixture.givenSelectIdsReturn(123L);
        fixture.givenUpdateAffectsRows(1);

        CampaignJobLog log = new CampaignJobLog();
        when(campaignJobLogDao.createLog(anyLong(), anyLong(), anyString(), any(), any(), any(), isNull()))
                .thenReturn(log);
        when(campaignJobResultLogDao.createLogsBulk(eq(log), anyList())).thenReturn(1);

        fixture.whenRunJob();

        InOrder io = inOrder(dbEngine);

        io.verify(dbEngine).executeParametrizedQuery(
                argThat(q -> q != null
                        && q.getQuery() != null
                        && q.getQuery().toUpperCase().contains("INFORMATION_SCHEMA.COLUMNS")),
                anyLong(), anyInt(), anyString(), anyString(), anyString()
        );

        ArgumentCaptor<com.powerdialer.model.ParametrizedQuery> selectCap =
                ArgumentCaptor.forClass(com.powerdialer.model.ParametrizedQuery.class);

        io.verify(dbEngine).executeParametrizedQuery(
                selectCap.capture(),
                anyLong(), anyInt(), anyString(), anyString(), anyString()
        );

        io.verify(dbEngine).executeBatchParametrizedUpdate(
                any(com.powerdialer.model.ParametrizedQuery.class),
                eq(1001L), anyString(), anyString(), anyString()
        );

        String selectSql = selectCap.getValue().getQuery();
        assertTrue(selectSql.startsWith("SELECT id AS campaign_id"));
        assertTrue(selectSql.contains("CAST(expire_date AS date)"));
        assertTrue(selectSql.contains("CAST(? AS date)"));
        org.junit.Assert.assertFalse(selectSql.contains("2024-01-01"));

        java.util.List<Object> selectParams = extractParamsFlexible(selectCap.getValue());
        org.junit.Assert.assertFalse(selectParams.isEmpty());
        assertTrue(selectParams.get(0) instanceof java.sql.Timestamp);
        assertTrue(selectParams.stream().noneMatch(p -> "2024-01-01".equals(String.valueOf(p))));
    }


    @Test
    public void runJob_filterDateValueWithInvalidOperator_marksFailed() throws Exception {
        fixture.givenEligibleJobConfigIds(Arrays.asList(1L));
        JobConfigCampaignDTO dto = TestData.row()
                .jobConfigId(1L).campaignJobId(101L).campaignId(1001L)
                .table("ResultTable")
                .connection("jdbc:url").dbUser("sa").encPwd("enc")
                .jobName("JobX")
                .build();
        fixture.givenCampaignRowsForIds(Collections.singletonList(dto));

        com.powerdialer.dto.JobConfigFilterDTO invalid =
                new com.powerdialer.dto.JobConfigFilterDTO(
                        1L, 10L, "expire_date",
                        Constants.FILTER_CONDITION.NOTIN,
                        null,
                        new java.util.Date(),
                        null,
                        Constants.FILTER_TYPE.PREDEFINED,
                        null
                );
        fixture.givenStructuredFilters(Collections.singletonList(invalid));

        when(enCipherUtils.decryptPassword("enc")).thenReturn("plain");
        fixture.givenWhitelistColumns("ResultTable", "expire_date", "id");

        CampaignJobLog log = new CampaignJobLog();
        when(campaignJobLogDao.createLog(anyLong(), anyLong(), anyString(), any(), any(), any(), isNull()))
                .thenReturn(log);

        fixture.whenRunJob();

        verify(campaignJobLogDao).updateLog(
                eq(log),
                eq(Constants.CAMPAIGN_JOB_RESULT_STATUS.JOB_RESULT_FAILED),
                argThat(msg -> msg != null && msg.contains("Unsupported compare operator for filterDateValue"))
        );

        verify(dbEngine, times(1)).executeParametrizedQuery(
                argThat(q -> q != null && q.getQuery() != null &&
                        q.getQuery().toUpperCase().contains("INFORMATION_SCHEMA.COLUMNS")),
                anyLong(), anyInt(), anyString(), anyString(), anyString()
        );

        verify(dbEngine, never()).executeParametrizedQuery(
                argThat(q -> q != null && q.getQuery() != null &&
                        q.getQuery().startsWith("SELECT id AS campaign_id")),
                anyLong(), anyInt(), anyString(), anyString(), anyString()
        );
        verify(dbEngine, never()).executeBatchParametrizedUpdate(
                any(ParametrizedQuery.class), anyLong(), anyString(), anyString(), anyString()
        );

        verify(campaignJobResultLogDao, never()).createLogsBulk(any(CampaignJobLog.class), anyList());
    }

    @Test
    public void runJob_valueList_withIN_operator_selectsAndUpdates() throws Exception {
        fixture.givenEligibleJobConfigIds(Arrays.asList(1L));

        JobConfigCampaignDTO dto = TestData.row()
                .jobConfigId(1L).campaignJobId(101L).campaignId(1001L)
                .table("ResultTable")
                .connection("jdbc:url").dbUser("sa").encPwd("enc")
                .jobName("JobX")
                .build();
        fixture.givenCampaignRowsForIds(Collections.singletonList(dto));

        com.powerdialer.dto.JobConfigFilterDTO filter =
                new com.powerdialer.dto.JobConfigFilterDTO(
                        1L, 10L, "status_id",
                        com.powerdialer.util.Constants.FILTER_CONDITION.IN,
                        "1,2,5,7",
                        null, null,
                        com.powerdialer.util.Constants.FILTER_TYPE.PREDEFINED,
                        null
                );
        fixture.givenStructuredFilters(Collections.singletonList(filter));

        when(enCipherUtils.decryptPassword("enc")).thenReturn("plain");
        fixture.givenWhitelistColumns("ResultTable", "id", "status_id", "UpdatedDate");

        fixture.givenSelectIdsReturn(10L, 20L);
        fixture.givenUpdateAffectsRows(2);

        CampaignJobLog log = new CampaignJobLog();
        when(campaignJobLogDao.createLog(anyLong(), anyLong(), anyString(), any(), any(), any(), isNull()))
                .thenReturn(log);
        when(campaignJobResultLogDao.createLogsBulk(eq(log), anyList())).thenReturn(2);

        fixture.whenRunJob();

        InOrder io = inOrder(dbEngine);

        io.verify(dbEngine).executeParametrizedQuery(
                argThat(q -> q != null
                        && q.getQuery() != null
                        && q.getQuery().toUpperCase().contains("INFORMATION_SCHEMA.COLUMNS")),
                anyLong(), anyInt(), anyString(), anyString(), anyString()
        );

        ArgumentCaptor<com.powerdialer.model.ParametrizedQuery> selectCap =
                ArgumentCaptor.forClass(com.powerdialer.model.ParametrizedQuery.class);

        io.verify(dbEngine).executeParametrizedQuery(
                selectCap.capture(),
                anyLong(), anyInt(), anyString(), anyString(), anyString()
        );

        String selectSql = selectCap.getValue().getQuery();
        assertTrue(
                "WHERE IN control:\n" + selectSql,
                selectSql.matches("(?is).*status_id\\s+IN\\s*\\(\\s*\\?\\s*,\\s*\\?\\s*,\\s*\\?\\s*,\\s*\\?\\s*\\).*")
        );

        java.util.List<Object> params = extractParamsFlexible(selectCap.getValue());
        assertEquals(4, params.size());
        assertEquals(1L, ((Number) params.get(0)).longValue());
        assertEquals(2L, ((Number) params.get(1)).longValue());
        assertEquals(5L, ((Number) params.get(2)).longValue());
        assertEquals(7L, ((Number) params.get(3)).longValue());

        io.verify(dbEngine).executeBatchParametrizedUpdate(
                any(ParametrizedQuery.class),
                eq(1001L), anyString(), anyString(), anyString()
        );

        verify(campaignJobLogDao).updateLog(
                eq(log),
                eq(Constants.CAMPAIGN_JOB_RESULT_STATUS.JOB_RESULT_SUCCESSFUL),
                isNull()
        );
    }


    @Test
    public void runJob_valueList_withNOTIN_operator_selectsAndUpdates() throws Exception {
        fixture.givenEligibleJobConfigIds(Arrays.asList(1L));

        JobConfigCampaignDTO dto = TestData.row()
                .jobConfigId(1L).campaignJobId(101L).campaignId(1001L)
                .table("ResultTable")
                .connection("jdbc:url").dbUser("sa").encPwd("enc")
                .jobName("JobX")
                .build();
        fixture.givenCampaignRowsForIds(Collections.singletonList(dto));

        com.powerdialer.dto.JobConfigFilterDTO filter =
                new com.powerdialer.dto.JobConfigFilterDTO(
                        1L, 10L,
                        "status_code",
                        com.powerdialer.util.Constants.FILTER_CONDITION.NOTIN,
                        "404, 500, 503",
                        null, null,
                        com.powerdialer.util.Constants.FILTER_TYPE.PREDEFINED,
                        null
                );
        fixture.givenStructuredFilters(Collections.singletonList(filter));

        when(enCipherUtils.decryptPassword("enc")).thenReturn("plain");
        fixture.givenWhitelistColumns("ResultTable", "id", "status_code", "is_disabled", "UpdatedDate");

        fixture.givenSelectIdsReturn(101L, 202L);
        fixture.givenUpdateAffectsRows(2);

        CampaignJobLog log = new CampaignJobLog();
        when(campaignJobLogDao.createLog(anyLong(), anyLong(), anyString(), any(), any(), any(), isNull()))
                .thenReturn(log);
        when(campaignJobResultLogDao.createLogsBulk(eq(log), anyList())).thenReturn(2);

        fixture.whenRunJob();

        InOrder io = inOrder(dbEngine);

        io.verify(dbEngine).executeParametrizedQuery(
                argThat(q -> q != null
                        && q.getQuery() != null
                        && q.getQuery().toUpperCase().contains("INFORMATION_SCHEMA.COLUMNS")),
                anyLong(), anyInt(), anyString(), anyString(), anyString()
        );

        ArgumentCaptor<com.powerdialer.model.ParametrizedQuery> selectCaptor =
                ArgumentCaptor.forClass(com.powerdialer.model.ParametrizedQuery.class);

        io.verify(dbEngine).executeParametrizedQuery(
                selectCaptor.capture(),
                anyLong(), anyInt(), anyString(), anyString(), anyString()
        );

        io.verify(dbEngine).executeBatchParametrizedUpdate(
                any(com.powerdialer.model.ParametrizedQuery.class),
                eq(1001L), anyString(), anyString(), anyString()
        );

        String selectSql = selectCaptor.getValue().getQuery();
        assertTrue(selectSql.startsWith("SELECT id AS campaign_id"));
        assertTrue(selectSql.contains("status_code NOT IN"));
        assertTrue(selectSql.contains("(?, ?, ?)"));

        java.util.List<Object> params = extractParamsFlexible(selectCaptor.getValue());
        assertEquals(3, params.size());
        assertTrue(params.contains(404L));
        assertTrue(params.contains(500L));
        assertTrue(params.contains(503L));

        verify(campaignJobResultLogDao).createLogsBulk(eq(log), argThat(list -> list.size() == 2));
        verify(campaignJobLogDao).updateLog(eq(log),
                eq(Constants.CAMPAIGN_JOB_RESULT_STATUS.JOB_RESULT_SUCCESSFUL),
                isNull());
    }

    @Test
    public void runJob_valueList_withIN_operator_butEmptyItems_skipsAsEmptyWhere() throws Exception {
        fixture.givenEligibleJobConfigIds(Arrays.asList(1L));

        JobConfigCampaignDTO dto = TestData.row()
                .jobConfigId(1L).campaignJobId(101L).campaignId(1001L)
                .table("ResultTable")
                .connection("jdbc:url").dbUser("sa").encPwd("enc")
                .jobName("JobX")
                .build();
        fixture.givenCampaignRowsForIds(Collections.singletonList(dto));

        com.powerdialer.dto.JobConfigFilterDTO f = new com.powerdialer.dto.JobConfigFilterDTO(
                1L, 10L,
                "status",
                com.powerdialer.util.Constants.FILTER_CONDITION.IN,
                " ,  ,   ,",
                null,
                null,
                com.powerdialer.util.Constants.FILTER_TYPE.PREDEFINED,
                null
        );
        fixture.givenStructuredFilters(Collections.singletonList(f));

        when(enCipherUtils.decryptPassword("enc")).thenReturn("plain");
        fixture.givenWhitelistColumns("ResultTable", "id", "status", "UpdatedDate");

        CampaignJobLog log = new CampaignJobLog();
        when(campaignJobLogDao.createLog(anyLong(), anyLong(), anyString(), any(), any(), any(), isNull()))
                .thenReturn(log);

        fixture.whenRunJob();

        verify(campaignJobLogDao).updateLog(
                eq(log),
                eq(Constants.CAMPAIGN_JOB_RESULT_STATUS.JOB_RESULT_SKIPPED),
                eq("Filter condition is empty.")
        );

        verify(dbEngine, never()).executeParametrizedQuery(
                argThat(q -> q != null && q.getQuery() != null &&
                        q.getQuery().startsWith("SELECT id AS campaign_id")),
                anyLong(), anyInt(), anyString(), anyString(), anyString()
        );

        verify(dbEngine, never()).executeBatchParametrizedUpdate(
                any(ParametrizedQuery.class), anyLong(), anyString(), anyString(), anyString()
        );

        verify(campaignJobResultLogDao, never()).createLogsBulk(any(CampaignJobLog.class), anyList());
    }

    @Test
    public void runJob_likeOperator_wrapsValueWithPercents_selectsAndUpdates() throws Exception {
        fixture.givenJobConfigWithFilter(Constants.FILTER_CONDITION.LIKE, "expireDate");
        fixture.whenRunJob();

        final java.util.concurrent.atomic.AtomicReference<ParametrizedQuery> seenSelect =
                new java.util.concurrent.atomic.AtomicReference<>();

        verify(dbEngine, atLeastOnce()).executeParametrizedQuery(
                ArgumentMatchers.argThat((ParametrizedQuery q) -> {
                    if (q != null && q.getQuery() != null &&
                            q.getQuery().startsWith("SELECT id AS campaign_id")) {
                        seenSelect.set(q);
                        return true;
                    }
                    return false;
                }),
                anyLong(), anyInt(), anyString(), anyString(), anyString()
        );

        ParametrizedQuery selectQ = seenSelect.get();
        org.junit.Assert.assertNotNull("SELECT query not captured", selectQ);

        String whereSql = selectQ.getQuery();
        assertTrue("WHERE LIKE expected", whereSql.contains(" LIKE ?"));

        java.util.List<Object> likeParams = extractParamsFlexible(selectQ);
        org.junit.Assert.assertFalse("LIKE param cannot empty", likeParams.isEmpty());
        assertTrue(
                "LIKE param %expireDate% must be",
                likeParams.stream().anyMatch(o -> "%expireDate%".equals(String.valueOf(o)))
        );
    }

    @Test
    public void runJob_notLikeOperator_wrapsValueWithPercents_selectsAndUpdates() throws Exception {
        fixture.givenJobConfigWithFilter(Constants.FILTER_CONDITION.NOTLIKE, "test");
        fixture.whenRunJob();

        final java.util.concurrent.atomic.AtomicReference<ParametrizedQuery> seenSelect =
                new java.util.concurrent.atomic.AtomicReference<>();

        verify(dbEngine, atLeastOnce()).executeParametrizedQuery(
                ArgumentMatchers.argThat((ParametrizedQuery q) -> {
                    if (q != null && q.getQuery() != null &&
                            q.getQuery().startsWith("SELECT id AS campaign_id")) {
                        seenSelect.set(q);
                        return true;
                    }
                    return false;
                }),
                anyLong(), anyInt(), anyString(), anyString(), anyString()
        );

        ParametrizedQuery selectQ = seenSelect.get();
        org.junit.Assert.assertNotNull("SELECT query not captured", selectQ);

        String whereSql = selectQ.getQuery();
        assertTrue("WHERE NOT LIKE expected", whereSql.contains(" NOT LIKE ?"));

        java.util.List<Object> notLikeParams = extractParamsFlexible(selectQ);
        org.junit.Assert.assertFalse("NOT LIKE param not be empty", notLikeParams.isEmpty());
        assertTrue(
                "NOT LIKE param  %test% must be",
                notLikeParams.stream().anyMatch(o -> "%test%".equals(String.valueOf(o)))
        );
    }

    @Test
    public void runJob_multipleFilters_AND_selectsAndUpdates() throws Exception {
        fixture.givenEligibleJobConfigIds(Arrays.asList(1L));

        JobConfigCampaignDTO dto = TestData.row()
                .jobConfigId(1L).campaignJobId(101L).campaignId(1001L)
                .table("ResultTable")
                .connection("jdbc:url").dbUser("sa").encPwd("enc")
                .jobName("JobX")
                .build();
        fixture.givenCampaignRowsForIds(Collections.singletonList(dto));

        List<com.powerdialer.dto.JobConfigFilterDTO> filters = Arrays.asList(
                new com.powerdialer.dto.JobConfigFilterDTO(
                        1L, 10L, "is_disabled",
                        com.powerdialer.util.Constants.FILTER_CONDITION.EQUAL,
                        "0", null, null,
                        com.powerdialer.util.Constants.FILTER_TYPE.PREDEFINED,
                        null
                ),
                new com.powerdialer.dto.JobConfigFilterDTO(
                        1L, 11L, "user_result_code",
                        com.powerdialer.util.Constants.FILTER_CONDITION.IN,
                        "77,88", null, null,
                        com.powerdialer.util.Constants.FILTER_TYPE.PREDEFINED,
                        null
                )
        );
        fixture.givenStructuredFilters(filters);

        when(enCipherUtils.decryptPassword("enc")).thenReturn("plain");
        fixture.givenWhitelistColumns("ResultTable", "id", "is_disabled", "user_result_code", "UpdatedDate");

        fixture.givenSelectIdsReturn(100L, 200L);
        fixture.givenUpdateAffectsRows(2);

        CampaignJobLog log = new CampaignJobLog();
        when(campaignJobLogDao.createLog(anyLong(), anyLong(), anyString(), any(), any(), any(), any()))
                .thenReturn(log);
        when(campaignJobResultLogDao.createLogsBulk(eq(log), anyList())).thenReturn(2);

        fixture.whenRunJob();

        final java.util.concurrent.atomic.AtomicReference<ParametrizedQuery> seenSelect =
                new java.util.concurrent.atomic.AtomicReference<>();

        verify(dbEngine, atLeastOnce()).executeParametrizedQuery(
                argThat((ParametrizedQuery q) -> {
                    if (q != null && q.getQuery() != null &&
                            q.getQuery().startsWith("SELECT id AS campaign_id")) {
                        seenSelect.set(q);
                        return true;
                    }
                    return false;
                }),
                anyLong(), anyInt(), anyString(), anyString(), anyString()
        );

        ParametrizedQuery selectQ = seenSelect.get();
        org.junit.Assert.assertNotNull("SELECT query not captured", selectQ);

        String selectSql = selectQ.getQuery();
        assertTrue("WHERE must contain is_disabled = ?",
                selectSql.contains("(is_disabled = ?)"));
        assertTrue("WHERE must contain user_result_code IN (?, ?)",
                selectSql.matches("(?s).*\\(user_result_code\\s+IN\\s*\\(\\s*\\?\\s*,\\s*\\?\\s*\\)\\).*"));

        java.util.List<Object> paramsAll = extractParamsFlexible(selectQ);
        assertEquals("SELECT param count should be 3 (0,77,88)", 3, paramsAll.size());
        assertEquals("0", String.valueOf(paramsAll.get(0)));
        assertTrue(String.valueOf(paramsAll.get(1)).equals("77") || String.valueOf(paramsAll.get(1)).equals("77.0"));
        assertTrue(String.valueOf(paramsAll.get(2)).equals("88") || String.valueOf(paramsAll.get(2)).equals("88.0"));

        verify(dbEngine, times(1)).executeBatchParametrizedUpdate(
                any(ParametrizedQuery.class),
                eq(1001L), anyString(), anyString(), anyString()
        );
        verify(campaignJobResultLogDao).createLogsBulk(eq(log), argThat(list -> list.size() == 2));
        verify(campaignJobLogDao).updateLog(eq(log),
                eq(Constants.CAMPAIGN_JOB_RESULT_STATUS.JOB_RESULT_SUCCESSFUL),
                isNull());
    }
    @Test
    public void runJob_filterDateValue_withLessOrEqual_operator_selectsAndUpdates() throws Exception {
        fixture.givenEligibleJobConfigIds(Arrays.asList(1L));
        JobConfigCampaignDTO dto = TestData.row()
                .jobConfigId(1L).campaignJobId(101L).campaignId(1001L)
                .table("ResultTable")
                .connection("jdbc:url").dbUser("sa").encPwd("enc")
                .jobName("JobX")
                .build();
        fixture.givenCampaignRowsForIds(Collections.singletonList(dto));

        com.powerdialer.dto.JobConfigFilterDTO f =
                new com.powerdialer.dto.JobConfigFilterDTO(
                        1L, 10L,
                        "expire_date",
                        com.powerdialer.util.Constants.FILTER_CONDITION.LESSOREQUAL,
                        null,
                        new java.util.Date(),
                        null,
                        com.powerdialer.util.Constants.FILTER_TYPE.PREDEFINED,
                        null
                );
        fixture.givenStructuredFilters(Collections.singletonList(f));

        when(enCipherUtils.decryptPassword("enc")).thenReturn("plain");
        fixture.givenWhitelistColumns("ResultTable", "id", "expire_date", "UpdatedDate");

        fixture.givenSelectIdsReturn(111L, 222L);
        fixture.givenUpdateAffectsRows(2);

        CampaignJobLog log = new CampaignJobLog();
        when(campaignJobLogDao.createLog(anyLong(), anyLong(), anyString(), any(), any(), any(), isNull()))
                .thenReturn(log);
        when(campaignJobResultLogDao.createLogsBulk(eq(log), anyList())).thenReturn(2);

        fixture.whenRunJob();

        ArgumentCaptor<com.powerdialer.model.ParametrizedQuery> selectCap =
                ArgumentCaptor.forClass(com.powerdialer.model.ParametrizedQuery.class);

        verify(dbEngine, atLeastOnce()).executeParametrizedQuery(
                selectCap.capture(), anyLong(), anyInt(), anyString(), anyString(), anyString()
        );

        com.powerdialer.model.ParametrizedQuery selectQ = selectCap.getValue();
        org.junit.Assert.assertNotNull("SELECT query not captured", selectQ);

        String selectSql = selectQ.getQuery();

        assertTrue(
                "WHERE date compare expected (<=)",
                selectSql.contains("CAST(expire_date AS date) <=") &&
                        selectSql.contains("CAST(? AS date)")
        );
        org.junit.Assert.assertFalse("Literal date should not appear in SQL", selectSql.contains("2024-"));

        java.util.List<Object> params = extractParamsFlexible(selectQ);
        org.junit.Assert.assertFalse("SELECT params should not be empty", params.isEmpty());
        assertTrue("First param must be Timestamp", params.get(0) instanceof java.sql.Timestamp);

        verify(dbEngine, times(1)).executeBatchParametrizedUpdate(
                any(com.powerdialer.model.ParametrizedQuery.class),
                eq(1001L), anyString(), anyString(), anyString()
        );
        verify(campaignJobResultLogDao).createLogsBulk(eq(log), argThat(list -> list.size() == 2));
        verify(campaignJobLogDao).updateLog(eq(log),
                eq(Constants.CAMPAIGN_JOB_RESULT_STATUS.JOB_RESULT_SUCCESSFUL),
                isNull());
    }



    @Test
    public void freeQuery_EQ_text_selectsAndUpdates() throws Exception {
        fixture.setupFreeBaseRowAndConn();
        when(enCipherUtils.decryptPassword("enc")).thenReturn("plain");
        fixture.givenFreeFilterJson(
                "{\"field\":\"status\",\"op\":\"=\",\"type\":\"string\",\"value\":\"ACTIVE\"}"
        );
        fixture.setupWhitelist("id", "status", "is_disabled", "UpdatedDate");
        fixture.expectSelectIdsAndUpdate(2, 10L, 20L);
        CampaignJobLog log = fixture.setupLogAndResultLogMocks(2);

        fixture.whenRunJob();

        verify(dbEngine, atLeastOnce()).executeParametrizedQuery(
                argThat(q -> q != null && q.getQuery() != null &&
                        q.getQuery().trim().toUpperCase(Locale.ROOT)
                                .startsWith("SELECT ID AS CAMPAIGN_ID")),
                anyLong(), anyInt(), anyString(), anyString(), anyString());

        ParametrizedQuery selectQ = fixture.captureLastSelect();
        org.junit.Assert.assertNotNull(selectQ);
        assertTrue(
                selectQ.getQuery().toUpperCase(Locale.ROOT).contains("STATUS = ?")
        );
        java.util.List<Object> params = extractParamsFlexible(selectQ);
        assertEquals(1, params.size());
        assertEquals("ACTIVE", String.valueOf(params.get(0)));

        verify(dbEngine).executeBatchParametrizedUpdate(
                any(com.powerdialer.model.ParametrizedQuery.class),
                eq(1001L),
                anyString(), anyString(), anyString()
        );

        verify(campaignJobResultLogDao).createLogsBulk(eq(log),
                argThat(list -> list != null && list.size() == 2));
    }


    @Test
    public void freeQuery_like_wrapsWithPercents() throws Exception {
        fixture.setupFreeBaseRowAndConn();
        when(enCipherUtils.decryptPassword("enc")).thenReturn("plain");
        fixture.givenFreeFilterJson(
                "{\"field\":\"reason_text\",\"op\":\"LIKE\",\"type\":\"string\",\"value\":\"timeout\"}"
        );
        fixture.setupWhitelist("id","reason_text","is_disabled","UpdatedDate");
        fixture.expectSelectIdsAndUpdate(2, 1L, 2L);
        fixture.setupLogAndResultLogMocks(2);

        fixture.whenRunJob();

        ParametrizedQuery q = fixture.captureLastSelect();
        assertTrue(q.getQuery().toUpperCase(Locale.ROOT).contains("REASON_TEXT LIKE ?"));
        List<Object> params = extractParamsFlexible(q);
        assertEquals("%timeout%", params.get(0));
    }

    @Test
    public void freeQuery_notLike_wrapsWithPercents() throws Exception {
        fixture.setupFreeBaseRowAndConn();
        when(enCipherUtils.decryptPassword("enc")).thenReturn("plain");
        fixture.givenFreeFilterJson(
                "{\"field\":\"reason_text\",\"op\":\"NOT_LIKE\",\"type\":\"string\",\"value\":\"test\"}"
        );
        fixture.setupWhitelist("id","reason_text","is_disabled","UpdatedDate");
        fixture.expectSelectIdsAndUpdate(1, 77L);
        fixture.setupLogAndResultLogMocks(1);

        fixture.whenRunJob();

        ParametrizedQuery q = fixture.captureLastSelect();
        assertTrue(q.getQuery().toUpperCase(Locale.ROOT).contains("NOT LIKE ?"));
        List<Object> params = extractParamsFlexible(q);
        assertEquals("%test%", params.get(0));
    }

    @Test
    public void freeQuery_in_list_selectsAndUpdates() throws Exception {
        fixture.setupFreeBaseRowAndConn();
        when(enCipherUtils.decryptPassword("enc")).thenReturn("plain");
        fixture.givenFreeFilterJson(
                "{\"field\":\"status_id\",\"op\":\"IN\",\"type\":\"number\",\"values\":[1,2,5,7]}"
        );
        fixture.setupWhitelist("id","status_id","is_disabled","UpdatedDate");
        fixture.expectSelectIdsAndUpdate(2, 10L, 20L);
        fixture.setupLogAndResultLogMocks(2);

        fixture.whenRunJob();

        ParametrizedQuery q = fixture.captureLastSelect();
        assertTrue(q.getQuery().toUpperCase(Locale.ROOT).contains("STATUS_ID IN"));

        List<Object> params = extractParamsFlexible(q);
        List<Long> actual = params.stream()
                .map(p -> ((Number) p).longValue())
                .collect(java.util.stream.Collectors.toList());

        org.junit.Assert.assertEquals(
                java.util.Arrays.asList(1L, 2L, 5L, 7L),
                actual
        );
    }
    @Test
    public void freeQuery_between_dates_selectsAndUpdates() throws Exception {
        fixture.setupFreeBaseRowAndConn();
        when(enCipherUtils.decryptPassword("enc")).thenReturn("plain");
        fixture.givenFreeFilterJson(
                "{\"field\":\"completed_date\",\"op\":\"BETWEEN\",\"type\":\"date\",\"values\":[\"2000-01-01\",\"2000-12-31\"]}"
        );
        fixture.setupWhitelist("id","completed_date","is_disabled","UpdatedDate");
        fixture.expectSelectIdsAndUpdate(1, 42L);
        fixture.setupLogAndResultLogMocks(1);

        fixture.whenRunJob();

        ParametrizedQuery q = fixture.captureLastSelect();
        assertTrue(q.getQuery().toUpperCase(Locale.ROOT).contains("BETWEEN"));
        List<Object> params = extractParamsFlexible(q);
        assertEquals(2, params.size());
    }

    @Test
    public void freeQuery_thresholdDate_lte_buildsDateCompare() throws Exception {
        fixture.setupFreeBaseRowAndConn();
        when(enCipherUtils.decryptPassword("enc")).thenReturn("plain");
        fixture.givenFreeFilterJson(
                "{\"field\":\"expire_date\",\"op\":\"thresholdDate\",\"operator\":\"<=\",\"days\":3}"
        );
        fixture.setupWhitelist("id","expire_date","is_disabled","UpdatedDate");
        fixture.expectSelectIdsAndUpdate(2, 1L, 2L);
        fixture.setupLogAndResultLogMocks(2);

        fixture.whenRunJob();

        ParametrizedQuery q = fixture.captureLastSelect();
        assertTrue(q.getQuery().toUpperCase(Locale.ROOT).contains("DATEADD"));
        List<Object> params = extractParamsFlexible(q);
        assertEquals(-3, ((Number)params.get(0)).intValue());
    }


    @Test
    public void freeQuery_isNull_and_isNotNull_combinesWithAnd() throws Exception {
        fixture.setupFreeBaseRowAndConn();
        when(enCipherUtils.decryptPassword("enc")).thenReturn("plain");
        fixture.givenFreeFilterJson(
                "{\"logic\":\"AND\",\"conditions\":[{\"field\":\"complete_date\",\"type\":\"date\",\"op\":\"IS_NULL\"},{\"field\":\"expire_date\",\"type\":\"date\",\"op\":\"IS_NOT_NULL\"}]}"
        );
        fixture.setupWhitelist("id","complete_date","expire_date","is_disabled","UpdatedDate");
        fixture.expectSelectIdsAndUpdate(1, 5L);
        fixture.setupLogAndResultLogMocks(1);

        fixture.whenRunJob();

        ParametrizedQuery q = fixture.captureLastSelect();
        assertTrue(q.getQuery().toUpperCase(Locale.ROOT).contains("COMPLETE_DATE IS NULL"));
        assertTrue(q.getQuery().toUpperCase(Locale.ROOT).contains("EXPIRE_DATE IS NOT NULL"));
        List<Object> params = extractParamsFlexible(q);
        assertTrue(params.isEmpty());
    }
    @Test
    public void freeQuery_raw_isIgnored_leadsToSkipped() throws Exception {
        fixture.setupFreeBaseRowAndConn();
        when(enCipherUtils.decryptPassword("enc")).thenReturn("plain");
        fixture.givenFreeFilterJson("{\"raw\":\"1=1 OR DROP TABLE X\"}");
        fixture.setupWhitelist("id","is_disabled","UpdatedDate");
        CampaignJobLog log = fixture.setupLogAndResultLogMocks(0);

        fixture.whenRunJob();

        verify(campaignJobLogDao).updateLog(eq(log),
                eq(Constants.CAMPAIGN_JOB_RESULT_STATUS.JOB_RESULT_SKIPPED),
                contains("Filter condition is empty"));
        verify(dbEngine, never()).executeBatchParametrizedUpdate(
                any(ParametrizedQuery.class), anyLong(), anyString(), anyString(), anyString());
    }


    private class Fixture {
        void givenEligibleJobConfigIds(List<Long> ids) {
            when(jobConfigDao.findEligibleJobConfigIds_FirstScan()).thenReturn(ids);
        }
        void givenWhitelistColumns(String table, String... columns) throws Exception {
            when(dbEngine.executeParametrizedQuery(
                    ArgumentMatchers.argThat((com.powerdialer.model.ParametrizedQuery q) ->
                            q != null &&
                                    q.getQuery() != null &&
                                    q.getQuery().toUpperCase().contains("INFORMATION_SCHEMA.COLUMNS")
                    ),
                    anyLong(), anyInt(), anyString(), anyString(), anyString()
            )).thenReturn(buildColumnsArray(columns));
        }
        void givenStructuredFilters(java.util.List<com.powerdialer.dto.JobConfigFilterDTO> filters) {
            when(jobConfigDao.findFiltersByJobConfigIds(ArgumentMatchers.<java.util.Set<Long>>any()))
                    .thenReturn(filters);
        }
        void givenUpdateThrows(RuntimeException ex) throws Exception {
            when(dbEngine.executeBatchParametrizedUpdate(
                    any(com.powerdialer.model.ParametrizedQuery.class),
                    anyLong(), anyString(), anyString(), anyString()
            )).thenThrow(ex);
        }
        void givenWhitelistEmpty() throws Exception {
            when(dbEngine.executeParametrizedQuery(
                    ArgumentMatchers.argThat((com.powerdialer.model.ParametrizedQuery q) ->
                            q != null &&
                                    q.getQuery() != null &&
                                    q.getQuery().toUpperCase().contains("INFORMATION_SCHEMA.COLUMNS")
                    ),
                    anyLong(), anyInt(), anyString(), anyString(), anyString()
            )).thenReturn(new org.json.JSONArray());
        }
        void givenSelectIdsReturn(Long... ids) throws Exception {
            org.json.JSONArray arr = new org.json.JSONArray();
            for (Long id : ids) {
                org.json.JSONObject o = new org.json.JSONObject();
                o.put("campaign_id", id);
                arr.put(o);
            }
            when(dbEngine.executeParametrizedQuery(
                    ArgumentMatchers.argThat((com.powerdialer.model.ParametrizedQuery q) ->
                            q != null &&
                                    q.getQuery() != null &&
                                    q.getQuery().startsWith("SELECT id AS campaign_id")
                    ),
                    anyLong(), anyInt(), anyString(), anyString(), anyString()
            )).thenReturn(arr);
        }
        void givenUpdateAffectsRows(int n) throws Exception {
            when(dbEngine.executeBatchParametrizedUpdate(
                    any(com.powerdialer.model.ParametrizedQuery.class),
                    anyLong(), anyString(), anyString(), anyString()
            )).thenReturn(n);
        }
        void givenSelectIdsReturnsEmpty() throws Exception {
            when(dbEngine.executeParametrizedQuery(
                    ArgumentMatchers.argThat((com.powerdialer.model.ParametrizedQuery q) ->
                            q != null &&
                                    q.getQuery() != null &&
                                    q.getQuery().startsWith("SELECT id AS campaign_id")
                    ),
                    anyLong(), anyInt(), anyString(), anyString(), anyString()
            )).thenReturn(new org.json.JSONArray());
        }
        void givenJobConfigWithFilter(Constants.FILTER_CONDITION op, String value) {
            givenEligibleJobConfigIds(java.util.Collections.singletonList(1L));

            com.powerdialer.dto.JobConfigCampaignDTO dto = TestData.row()
                    .jobConfigId(1L).campaignJobId(101L).campaignId(1001L)
                    .table("ResultTable")
                    .connection("jdbc:url").dbUser("sa").encPwd("enc")
                    .jobName("JobX")
                    .build();
            givenCampaignRowsForIds(java.util.Collections.singletonList(dto));

            com.powerdialer.dto.JobConfigFilterDTO f = new com.powerdialer.dto.JobConfigFilterDTO(
                    1L, 10L, "some_text_col",
                    op,
                    value,
                    null,
                    null,
                    com.powerdialer.util.Constants.FILTER_TYPE.PREDEFINED,
                    null
            );
            givenStructuredFilters(java.util.Collections.singletonList(f));

            when(enCipherUtils.decryptPassword("enc")).thenReturn("plain");
            try {
                givenWhitelistColumns("ResultTable", "id", "some_text_col", "is_disabled", "UpdatedDate");
                givenSelectIdsReturn(11L, 22L);
                givenUpdateAffectsRows(2);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            com.powerdialer.model.CampaignJobLog log = new com.powerdialer.model.CampaignJobLog();
            when(campaignJobLogDao.createLog(anyLong(), anyLong(), anyString(), any(), any(), any(), isNull()))
                    .thenReturn(log);
            when(campaignJobResultLogDao.createLogsBulk(eq(log), anyList())).thenReturn(2);
        }

        void givenCampaignRowsForIds(List<JobConfigCampaignDTO> rows) {
            when(jobConfigDao.findCampaignRowsByJobConfigIds(ArgumentMatchers.<Collection<Long>>any()))
                    .thenReturn(rows);
        }
        void givenEmptyFilters() {
            when(jobConfigDao.findFiltersByJobConfigIds(ArgumentMatchers.<Set<Long>>any()))
                    .thenReturn(Collections.emptyList());
        }
        void whenRunJob() {
            service.runJob();
        }
        void thenEligibleScanPerformedOnce() {
            verify(jobConfigDao, times(1)).findEligibleJobConfigIds_FirstScan();
        }
        void thenNoInteractions(Object... mocks) {
            for (Object m : mocks) {
                verifyNoInteractions(m);
            }
        }
        private org.json.JSONArray buildColumnsArray(String... cols) {
            org.json.JSONArray arr = new org.json.JSONArray();
            for (String c : cols) {
                org.json.JSONObject o = new org.json.JSONObject();
                o.put("COLUMN_NAME", c);
                arr.put(o);
            }
            return arr;
        }

        void givenFreeFilterJson(String json) {
            com.powerdialer.dto.JobConfigFilterDTO free =
                    new com.powerdialer.dto.JobConfigFilterDTO(
                            1L, 10L,
                            null,
                            null,
                            null,
                            null,
                            null,
                            com.powerdialer.util.Constants.FILTER_TYPE.FREE,
                            json
                    );
            givenStructuredFilters(java.util.Collections.singletonList(free));
        }

        void setupFreeBaseRowAndConn() {
            givenEligibleJobConfigIds(java.util.Collections.singletonList(1L));
            JobConfigCampaignDTO dto = TestData.row()
                    .jobConfigId(1L).campaignJobId(101L).campaignId(1001L)
                    .table("ResultTable").connection("jdbc:url")
                    .dbUser("sa").encPwd("enc").jobName("JobX").build();
            givenCampaignRowsForIds(java.util.Collections.singletonList(dto));

            when(enCipherUtils.decryptPassword("enc")).thenReturn("plain");
        }

        void setupWhitelist(String... columns) throws Exception {
            givenWhitelistColumns("ResultTable", columns);
        }

        CampaignJobLog setupLogAndResultLogMocks(int expectedResultRows) {
            CampaignJobLog log = new CampaignJobLog();
            when(campaignJobLogDao.createLog(anyLong(), anyLong(), anyString(), any(), any(), any(), any()))
                    .thenReturn(log);
            if (expectedResultRows >= 0) {
                when(campaignJobResultLogDao.createLogsBulk(eq(log), anyList())).thenReturn(expectedResultRows);
            }
            return log;
        }

        void expectSelectIdsAndUpdate(int updateAffected, Long... ids) throws Exception {
            givenSelectIdsReturn(ids);
            givenUpdateAffectsRows(updateAffected);
        }

        void expectNoSelectNoUpdate() throws Exception {
        }
        private ArgumentMatcher<ParametrizedQuery> selectIdsMatcherCI() {
            return new ArgumentMatcher<ParametrizedQuery>() {
                @Override public boolean matches(ParametrizedQuery q) {
                    if (q == null || q.getQuery() == null) return false;
                    String s = q.getQuery().trim().toUpperCase(Locale.ROOT);
                    return s.startsWith("SELECT ID AS CAMPAIGN_ID");
                }
                @Override public String toString() { return "SELECT ID AS CAMPAIGN_ID ... (case-insensitive)"; }
            };
        }

        private ParametrizedQuery captureLastSelect() throws SQLException {
            ArgumentCaptor<ParametrizedQuery> cap = ArgumentCaptor.forClass(ParametrizedQuery.class);

            verify(dbEngine, atLeastOnce()).executeParametrizedQuery(
                    cap.capture(),
                    anyLong(),
                    anyInt(),
                    anyString(),
                    anyString(),
                    anyString()
            );

            List<ParametrizedQuery> all = cap.getAllValues();
            return all.isEmpty() ? null : all.get(all.size() - 1);
        }


        private ArgumentMatcher<List<Long>> listSizeIs(final int n) {
            return new ArgumentMatcher<List<Long>>() {
                @Override public boolean matches(List<Long> list) { return list != null && list.size() == n; }
                @Override public String toString() { return "List size == " + n; }
            };
        }

    }




    private static class TestData {
        static RowBuilder row() { return new RowBuilder(); }

        static class RowBuilder {
            private Long jcId = 1L;
            private String jobName = "Job";
            private Long expireUserId = null;
            private Long resultCodeId = null;
            private Long cjId = 101L;
            private String cjTable = "cj_table";
            private Long cId = 1001L;
            private String cName = "campName";
            private String table = "ResultTable";
            private Date stoppedDate = new Date();
            private String conn = "jdbc:url";
            private String dbUser = "sa";
            private String encPwd = "enc";

            RowBuilder jobConfigId(Long v) { this.jcId = v; return this; }
            RowBuilder jobName(String v) { this.jobName = v; return this; }
            RowBuilder expireUserId(Long v) { this.expireUserId = v; return this; }
            RowBuilder resultCodeId(Long v) { this.resultCodeId = v; return this; }
            RowBuilder campaignJobId(Long v) { this.cjId = v; return this; }
            RowBuilder campaignId(Long v) { this.cId = v; return this; }
            RowBuilder table(String v) { this.table = v; return this; }
            RowBuilder connection(String v) { this.conn = v; return this; }
            RowBuilder dbUser(String v) { this.dbUser = v; return this; }
            RowBuilder encPwd(String v) { this.encPwd = v; return this; }

            JobConfigCampaignDTO build() {
                return new JobConfigCampaignDTO(
                        jcId, jobName, expireUserId, resultCodeId,
                        cjId, cjTable,
                        cId, cName, table, stoppedDate,
                        conn, dbUser, encPwd
                );
            }
        }
    }
}
