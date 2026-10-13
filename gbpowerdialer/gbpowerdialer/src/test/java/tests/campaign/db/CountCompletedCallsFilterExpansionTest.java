package tests.campaign.db;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.SQLException;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.powerdialer.engine.acd.ACDEngine;
import com.powerdialer.engine.db.CampaignDbOperations;
import com.powerdialer.engine.db.DBEngine;
import com.powerdialer.engine.priority.PriorityService;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.Field;
import com.powerdialer.model.Filter;
import com.powerdialer.model.ParametrizedQuery;
import com.powerdialer.util.EnCipherUtils;
import com.powerdialer.util.Constants;
import com.powerdialer.util.LoggerDecorator;

@RunWith(MockitoJUnitRunner.class)
public class CountCompletedCallsFilterExpansionTest {

  @Mock
  private DBEngine dbEngine;
  @Mock
  private LoggerDecorator loggerDecorator;
  @Mock
  private ACDEngine acdEngine;
  @Mock
  private EnCipherUtils enCipherUtils;
  @Mock
  private PriorityService priorityService;

  private CampaignDbOperations dbOperations;

  @Before
  public void setUp() {
    when(loggerDecorator.getLogger(CampaignDbOperations.class)).thenReturn(loggerDecorator);
    when(enCipherUtils.decryptPassword(anyString())).thenAnswer(inv -> inv.getArgument(0));
    dbOperations = new CampaignDbOperations(dbEngine, loggerDecorator, acdEngine, enCipherUtils, priorityService);
  }

  @Test
  public void preservesCompleteDateNotNullAsCompleted() throws SQLException {
    String sql = captureCountCompletedCallsSql(baseCampaign());
    assertTrue("SQL must keep complete_date IS NOT NULL: " + sql,
        sql.contains("complete_date IS NOT NULL"));
  }

  @Test
  public void includesIsDisabledOneAsCompleted() throws SQLException {
    String sql = captureCountCompletedCallsSql(baseCampaign());
    assertTrue("SQL must treat is_disabled = 1 as completed: " + sql,
        sql.contains("is_disabled = 1"));
  }

  @Test
  public void escapesIsDisabledColumnNameForOracle() throws SQLException {
    Campaign campaign = baseCampaign();
    campaign.setDbConnectionString("jdbc:oracle:thin:@localhost:1521:test");

    String sql = captureCountCompletedCallsSql(campaign);

    assertTrue("Oracle must use case-sensitive escaped is_disabled identifier: " + sql,
        sql.contains("\"is_disabled\" = 1"));
  }

  @Test
  public void escapesIsDisabledColumnNameForPostgreSql() throws SQLException {
    Campaign campaign = baseCampaign();
    campaign.setDbConnectionString("jdbc:postgresql://localhost:5432/test");

    String sql = captureCountCompletedCallsSql(campaign);

    assertTrue("PostgreSQL must use case-sensitive escaped is_disabled identifier: " + sql,
        sql.contains("\"is_disabled\" = 1"));
  }

  @Test
  public void doesNotEscapeIsDisabledForMsSql() throws SQLException {
    String sql = captureCountCompletedCallsSql(baseCampaign());
    assertFalse("MSSQL must use raw is_disabled identifier (no quotes): " + sql,
        sql.contains("\"is_disabled\""));
  }

  @Test
  public void includesFetchedNotNullAsCompleted() throws SQLException {
    String sql = captureCountCompletedCallsSql(baseCampaign());
    assertTrue("SQL must treat is_fetched IS NOT NULL as completed: " + sql,
        sql.contains("is_fetched IS NOT NULL"));
  }

  @Test
  public void includesAttemptsThresholdWhenCountAllCallTypesAndOveralPositive() throws SQLException {
    Campaign campaign = baseCampaign();
    campaign.setCountAllCallTypes(true);
    campaign.setRetryOveralAttempt(3);

    String sql = captureCountCompletedCallsSql(campaign);

    assertTrue("SQL must include attempts >= 3 threshold: " + sql,
        sql.contains("attempts >= 3"));
  }

  @Test
  public void omitsAttemptsThresholdWhenCountAllCallTypesIsFalse() throws SQLException {
    Campaign campaign = baseCampaign();
    campaign.setCountAllCallTypes(false);
    campaign.setRetryOveralAttempt(3);

    String sql = captureCountCompletedCallsSql(campaign);

    assertFalse("SQL must NOT include attempts threshold when countAllCallTypes is false: " + sql,
        sql.contains("attempts >="));
  }

  @Test
  public void omitsAttemptsThresholdWhenRetryOveralAttemptIsNull() throws SQLException {
    Campaign campaign = baseCampaign();
    campaign.setCountAllCallTypes(true);
    campaign.setRetryOveralAttempt(null);

    String sql = captureCountCompletedCallsSql(campaign);

    assertFalse("SQL must NOT include attempts threshold when retryOveralAttempt is null: " + sql,
        sql.contains("attempts >="));
  }

  @Test
  public void omitsAttemptsThresholdWhenRetryOveralAttemptIsZero() throws SQLException {
    Campaign campaign = baseCampaign();
    campaign.setCountAllCallTypes(true);
    campaign.setRetryOveralAttempt(0);

    String sql = captureCountCompletedCallsSql(campaign);

    assertFalse("SQL must NOT include attempts threshold when retryOveralAttempt is 0: " + sql,
        sql.contains("attempts >="));
  }

  @Test
  public void wrapsCompletionPredicateInParenthesesAfterWhere() throws SQLException {
    String sql = captureCountCompletedCallsSql(baseCampaign());
    assertTrue("Completion predicate must be parenthesized to bind correctly with later AND <user_filters>: " + sql,
        sql.contains("WHERE ("));
  }

  @Test
  public void retryFetchOmitsRowsAtPerCycleOverallAttemptsLimit() throws SQLException {
    Campaign campaign = baseCampaign();
    campaign.setRetryStrategy(Constants.RETRY_STRATEGY.ROUND_ROBIN_DIAL_WITH_PER_CYCLE_RETRY_LIMIT_SCHEDULE_NEXT_NUMBER);
    campaign.setRetryOveralAttempt(3);

    String sql = captureFetchDataForProducerRetriesSql(campaign);

    assertTrue("Retry fetch must skip rows that reached attempts limit: " + sql,
        sql.contains("(src.attempts IS NULL OR src.attempts < 3)"));
  }

  @Test
  public void retryFetchKeepsExistingQueryForNonPerCycleStrategies() throws SQLException {
    Campaign campaign = baseCampaign();
    campaign.setRetryStrategy(Constants.RETRY_STRATEGY.ROUND_ROBIN_DIAL_WITH_OVERALL_RETRY_LIMIT_SCHEDULE_NEXT_NUMBER);
    campaign.setRetryOveralAttempt(3);

    String sql = captureFetchDataForProducerRetriesSql(campaign);

    assertFalse("Non per-cycle strategies must not receive the per-cycle attempts fetch guard: " + sql,
        sql.contains("src.attempts < 3"));
  }

  @Test
  public void stripsIsDisabledStatusFilterFromCompletedQuery() throws SQLException {
    // TURKCELL-79 senaryosu: user filter'ı is_disabled=0. Faz 2 simetri: Faz 1'in
    // "is_disabled=1 → completed" kazanımı user filter'ı tarafından silinmemeli.
    Campaign campaign = baseCampaign();
    campaign.setFilters(java.util.Arrays.asList(buildFilter("is_disabled", "0")));

    String sql = captureCountCompletedCallsSql(campaign);

    assertFalse("User is_disabled=0 filter must not contradict completion predicate: " + sql,
        sql.contains("is_disabled = 0"));
    assertTrue("Completion predicate must still count is_disabled=1 as completed: " + sql,
        sql.contains("is_disabled = 1"));
  }

  @Test
  public void keepsBusinessFilterAndStripsStatusFilterInCompletedQuery() throws SQLException {
    Campaign campaign = baseCampaign();
    campaign.setFilters(java.util.Arrays.asList(
        buildFilter("is_disabled", "0"),
        buildFilter("Filter", "371")));

    String sql = captureCountCompletedCallsSql(campaign);

    assertFalse("Status filter must be stripped: " + sql, sql.contains("is_disabled = 0"));
    assertTrue("Business filter must survive in completed query: " + sql, sql.contains("Filter"));
    assertTrue("Business filter value must survive: " + sql, sql.contains("371"));
  }

  @Test
  public void stripsAllStatusFiltersFromCompletedQueryForTurkcellDuplicate() throws SQLException {
    // TURKCELL-79 pd_filter dump: ID 180 ve 426 ikisi de is_disabled=0. Her ikisi de
    // completed query'sinden çıkmalı; ne 180'inci ne 426'ıncı is_disabled=0 SQL'e sızmasın.
    Campaign campaign = baseCampaign();
    campaign.setFilters(java.util.Arrays.asList(
        buildFilter("is_disabled", "0"),
        buildFilter("is_disabled", "0")));

    String sql = captureCountCompletedCallsSql(campaign);

    assertFalse("Duplicate status filters must both be stripped: " + sql,
        sql.contains("is_disabled = 0"));
    assertTrue("is_disabled=1 completed branch must survive: " + sql,
        sql.contains("is_disabled = 1"));
  }

  private Filter buildFilter(String fieldName, String value) {
    Field field = new Field();
    field.setFieldName(fieldName);
    field.setFieldType(Constants.DB_FIELD_TYPES.INT);
    Filter filter = new Filter();
    filter.setField(field);
    filter.setType(Constants.FILTER_TYPE.PREDEFINED);
    filter.setCondition(Constants.FILTER_CONDITION.EQUAL);
    filter.setValue(value);
    filter.setEnabled(true);
    filter.setTargetTable(Constants.FILTER_TARGET_TABLE.SOURCE_TABLE);
    return filter;
  }

  private String captureCountCompletedCallsSql(Campaign campaign) throws SQLException {
    when(dbEngine.executeQuery(any(), any(), any(), any(), any(), any())).thenReturn(null);
    dbOperations.countCompletedCalls(campaign);
    ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
    verify(dbEngine).executeQuery(captor.capture(), any(), any(), any(), any(), any());
    return captor.getValue();
  }

  private String captureFetchDataForProducerRetriesSql(Campaign campaign) throws SQLException {
    // Task 325461: retry fetch artık parametreli yolu kullanıyor (retry_date koşulu "?" ile
    // bağlanıyor, satır limiti sorgu metninde taşınıyor). Assertion'ların amacı değişmedi.
    when(dbEngine.executeParametrizedQuery(any(), any(), any(), any(), any(), any())).thenReturn(null);
    dbOperations.fetchDataForProducerRetries(campaign, 10, java.util.Optional.empty());
    ArgumentCaptor<ParametrizedQuery> captor = ArgumentCaptor.forClass(ParametrizedQuery.class);
    verify(dbEngine).executeParametrizedQuery(captor.capture(), any(), any(), any(), any(), any());
    return captor.getValue().getQuery();
  }

  private Campaign baseCampaign() {
    Campaign c = new Campaign();
    c.setId(42L);
    c.setDbConnectionString("jdbc:sqlserver://localhost:1433;databaseName=test");
    c.setDbUsername("u");
    c.setDbPassword("p");
    c.setDbListTable("list_table");
    c.setDbResultTable("list_table");
    c.setColResultCompleteDate("complete_date");
    c.setColResultAttempts("attempts");
    c.setColResultFetchedDataFlag("is_fetched");
    c.setColResultRetryDate("retry_date");
    c.setAllowCallback(false);
    c.setSkillEnabled(false);
    c.setTimezoneType(Constants.TIMEZONE_TYPE.OFF);
    return c;
  }
}
