package tests.campaign.db;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
import com.powerdialer.util.Constants;
import com.powerdialer.util.EnCipherUtils;
import com.powerdialer.util.LoggerDecorator;

@RunWith(MockitoJUnitRunner.class)
public class CountActualListSizeStatusFilterIsolationTest {

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
  public void excludesIsDisabledStatusFilter() throws SQLException {
    Campaign campaign = baseCampaign();
    campaign.setFilters(filters(statusFilter("is_disabled", "0")));

    String sql = captureCountActualListSizeSql(campaign);

    assertFalse("is_disabled status filter must not appear in denominator SQL: " + sql,
        sql.contains("is_disabled"));
    assertFalse("Denominator SQL must not include any WHERE when only a status filter is present: " + sql,
        sql.contains("WHERE"));
  }

  @Test
  public void excludesCompleteDateStatusFilter() throws SQLException {
    Campaign campaign = baseCampaign();
    campaign.setFilters(filters(statusFilter("complete_date", "NULL")));

    String sql = captureCountActualListSizeSql(campaign);

    assertFalse("complete_date status filter must not appear in denominator SQL: " + sql,
        sql.contains("complete_date"));
  }

  @Test
  public void excludesIsFetchedStatusFilter() throws SQLException {
    Campaign campaign = baseCampaign();
    campaign.setFilters(filters(statusFilter("is_fetched", "NULL")));

    String sql = captureCountActualListSizeSql(campaign);

    assertFalse("is_fetched status filter must not appear in denominator SQL: " + sql,
        sql.contains("is_fetched"));
  }

  @Test
  public void excludesAttemptsStatusFilter() throws SQLException {
    Campaign campaign = baseCampaign();
    campaign.setFilters(filters(statusFilter("attempts", "0")));

    String sql = captureCountActualListSizeSql(campaign);

    assertFalse("attempts status filter must not appear in denominator SQL: " + sql,
        sql.contains("attempts"));
  }

  @Test
  public void excludesCallbackDateStatusFilter() throws SQLException {
    Campaign campaign = baseCampaign();
    campaign.setFilters(filters(statusFilter("callback_date", "NULL")));

    String sql = captureCountActualListSizeSql(campaign);

    assertFalse("callback_date status filter must not appear in denominator SQL: " + sql,
        sql.contains("callback_date"));
  }

  @Test
  public void excludesRetryDateStatusFilter() throws SQLException {
    Campaign campaign = baseCampaign();
    campaign.setFilters(filters(statusFilter("retry_date", "NULL")));

    String sql = captureCountActualListSizeSql(campaign);

    assertFalse("retry_date status filter must not appear in denominator SQL: " + sql,
        sql.contains("retry_date"));
  }

  @Test
  public void keepsBusinessFiltersInDenominator() throws SQLException {
    Campaign campaign = baseCampaign();
    campaign.setFilters(filters(businessFilter("Filter", "371")));

    String sql = captureCountActualListSizeSql(campaign);

    assertTrue("Business filter must remain in denominator SQL: " + sql,
        sql.contains("Filter"));
    assertTrue("Business filter value must remain in denominator SQL: " + sql,
        sql.contains("371"));
  }

  @Test
  public void keepsBusinessFilterWhenMixedWithStatusFilter() throws SQLException {
    Campaign campaign = baseCampaign();
    campaign.setFilters(filters(
        statusFilter("is_disabled", "0"),
        businessFilter("Filter", "371")));

    String sql = captureCountActualListSizeSql(campaign);

    assertFalse("Status filter must be stripped: " + sql, sql.contains("is_disabled"));
    assertTrue("Business filter must survive: " + sql, sql.contains("Filter"));
    assertTrue("Business value must survive: " + sql, sql.contains("371"));
  }

  @Test
  public void caseInsensitiveStatusFieldMatching() throws SQLException {
    Campaign campaign = baseCampaign();
    campaign.setFilters(filters(statusFilter("IS_DISABLED", "0")));

    String sql = captureCountActualListSizeSql(campaign);

    assertFalse("Status filter matching must be case-insensitive: " + sql,
        sql.toLowerCase().contains("is_disabled"));
  }

  @Test
  public void trimsStatusFieldNameDuringMatching() throws SQLException {
    Campaign campaign = baseCampaign();
    campaign.setFilters(filters(statusFilter("  is_disabled  ", "0")));

    String sql = captureCountActualListSizeSql(campaign);

    assertFalse("Whitespace around status field must not bypass exclusion: " + sql,
        sql.contains("is_disabled"));
  }

  @Test
  public void respectsCampaignConfigurableStatusColumnName() throws SQLException {
    Campaign campaign = baseCampaign();
    campaign.setColResultCompleteDate("tamamlanma_tarihi");
    campaign.setFilters(filters(statusFilter("tamamlanma_tarihi", "NULL")));

    String sql = captureCountActualListSizeSql(campaign);

    assertFalse("Campaign-configurable status column must be excluded too: " + sql,
        sql.contains("tamamlanma_tarihi"));
  }

  @Test
  public void excludesDuplicateStatusFilters() throws SQLException {
    Campaign campaign = baseCampaign();
    campaign.setFilters(filters(
        statusFilter("is_disabled", "0"),
        statusFilter("is_disabled", "0")));

    String sql = captureCountActualListSizeSql(campaign);

    assertFalse("Both duplicate status filters must be stripped: " + sql,
        sql.contains("is_disabled"));
    assertFalse("With only status filters, WHERE clause must be empty: " + sql,
        sql.contains("WHERE"));
  }

  @Test
  public void countCompletedCallsAppliesSameStatusFilterIsolation() throws SQLException {
    // Faz 2 simetri: countCompletedCalls da statü filter'larını ayıklar. Aksi halde
    // user filter is_disabled=0, completion predicate'in is_disabled=1 dalıyla çelişir
    // ve elenmiş kayıtlar completed sayılamaz (Faz 1 kazanımının silinmesi).
    Campaign campaign = baseCampaign();
    campaign.setFilters(filters(
        statusFilter("is_disabled", "0"),
        businessFilter("Filter", "371")));

    when(dbEngine.executeQuery(any(), any(), any(), any(), any(), any())).thenReturn(null);
    dbOperations.countCompletedCalls(campaign);
    ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
    verify(dbEngine).executeQuery(captor.capture(), any(), any(), any(), any(), any());
    String sql = captor.getValue();

    assertFalse("User is_disabled=0 filter must be stripped so completion predicate is_disabled=1 branch survives: " + sql,
        sql.contains("is_disabled = 0"));
    assertTrue("Completion predicate is_disabled=1 branch must remain: " + sql,
        sql.contains("is_disabled = 1"));
    assertTrue("Business filter Filter=371 must survive in completed query: " + sql,
        sql.contains("Filter"));
    assertTrue("Business filter value must survive: " + sql,
        sql.contains("371"));
  }

  private String captureCountActualListSizeSql(Campaign campaign) throws SQLException {
    when(dbEngine.executeQuery(any(), any(), any(), any(), any(), any())).thenReturn(null);
    dbOperations.countActualListSize(campaign);
    ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
    verify(dbEngine).executeQuery(captor.capture(), any(), any(), any(), any(), any());
    return captor.getValue();
  }

  private Campaign baseCampaign() {
    Campaign c = new Campaign();
    c.setId(79L);
    c.setDbConnectionString("jdbc:sqlserver://localhost:1433;databaseName=test");
    c.setDbUsername("u");
    c.setDbPassword("p");
    c.setDbListTable("LIST_TBL");
    c.setDbResultTable("LIST_TBL");
    c.setColResultCompleteDate("complete_date");
    c.setColResultFetchedDataFlag("is_fetched");
    c.setColResultAttempts("attempts");
    c.setColResultCallbackDate("callback_date");
    c.setColResultRetryDate("retry_date");
    c.setFilterJoinType(Constants.FILTER_JOIN_TYPE.AND);
    return c;
  }

  private List<Filter> filters(Filter... fs) {
    return new ArrayList<>(Arrays.asList(fs));
  }

  private Filter statusFilter(String fieldName, String value) {
    return buildFilter(fieldName, value);
  }

  private Filter businessFilter(String fieldName, String value) {
    return buildFilter(fieldName, value);
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
}
