package tests.campaign;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;

import org.junit.Test;

import org.json.JSONArray;
import org.json.JSONObject;
import org.mockito.ArgumentCaptor;

import com.powerdialer.engine.db.CampaignDbOperations;
import com.powerdialer.engine.db.DBEngine;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.Field;
import com.powerdialer.model.Filter;
import com.powerdialer.util.Constants;
import com.powerdialer.util.EnCipherUtils;
import com.powerdialer.util.LoggerDecorator;

public class CampaignFreshDataListSizeSqlRegressionTest {

  @Test
  public void freshDataListSizeCountsUnfetchedRowsWithoutRetryOrCallbackState() throws Exception {
    DBEngine dbEngine = mock(DBEngine.class);
    EnCipherUtils enCipherUtils = mock(EnCipherUtils.class);
    when(enCipherUtils.decryptPassword("encrypted")).thenReturn("plain");
    when(dbEngine.executeQuery(anyString(), anyLong(), isNull(Integer.class), anyString(), anyString(), anyString()))
        .thenReturn(countResult(7));

    CampaignDbOperations operations = new CampaignDbOperations(dbEngine, new LoggerDecorator(), null, enCipherUtils, null);

    int count = operations.countFreshDataListSize(campaign(false));

    assertEquals(7, count);
    String query = capturedQuery(dbEngine);
    assertTrue(query.contains("SELECT COUNT(*) AS \"PD_ROWS\" FROM list_table src LEFT JOIN result_table res ON (src.id = res.result_id)"));
    assertTrue(query.contains("res.complete_date IS NULL"));
    assertTrue(query.contains("res.callback_date IS NULL"));
    assertTrue(query.contains("(res.retry_history IS NULL OR res.retry_history = '')"));
    assertTrue(query.contains("(res.attempts IS NULL OR res.attempts = '' OR res.attempts = '0')"));
    assertTrue(query.contains("(res.is_disabled IS NULL OR res.is_disabled <> 1)"));
  }

  @Test
  public void freshDataListSizeAppliesCampaignFiltersWhenRetryFiltersAreEnabled() throws Exception {
    DBEngine dbEngine = mock(DBEngine.class);
    EnCipherUtils enCipherUtils = mock(EnCipherUtils.class);
    when(enCipherUtils.decryptPassword("encrypted")).thenReturn("plain");
    when(dbEngine.executeQuery(anyString(), anyLong(), isNull(Integer.class), anyString(), anyString(), anyString()))
        .thenReturn(countResult(3));

    CampaignDbOperations operations = new CampaignDbOperations(dbEngine, new LoggerDecorator(), null, enCipherUtils, null);

    int count = operations.countFreshDataListSize(campaign(true));

    assertEquals(3, count);
    String query = capturedQuery(dbEngine);
    assertTrue(query.contains("AND (src.customer_status is not null)"));
  }

  private Campaign campaign(boolean applyFilters) {
    Campaign campaign = new Campaign();
    campaign.setId(99L);
    campaign.setDbConnectionString("jdbc:sqlserver://localhost;databaseName=campaign");
    campaign.setDbUsername("user");
    campaign.setDbPassword("encrypted");
    campaign.setDbListTable("list_table");
    campaign.setDbResultTable("result_table");
    campaign.setColUniqueKey("id");
    campaign.setColResultUniqueKey("result_id");
    campaign.setColResultCompleteDate("complete_date");
    campaign.setColResultFetchedDataFlag("is_fetched");
    campaign.setColResultCallbackDate("callback_date");
    campaign.setColResultRetryHistory("retry_history");
    campaign.setColResultAttempts("attempts");
    if (applyFilters) {
      campaign.setApplyFilterTo(Constants.APPLY_TO.RETRIES);
      campaign.setFilterJoinType(Constants.FILTER_JOIN_TYPE.AND);
      campaign.setFilters(Arrays.asList(filter("customer_status", Constants.FILTER_CONDITION.ISNOTNULL, null)));
    }
    return campaign;
  }

  private Filter filter(String fieldName, Constants.FILTER_CONDITION condition, String value) {
    Field field = new Field();
    field.setFieldName(fieldName);
    field.setFieldType(Constants.DB_FIELD_TYPES.INT);

    Filter filter = new Filter();
    filter.setEnabled(true);
    filter.setType(Constants.FILTER_TYPE.PREDEFINED);
    filter.setTargetTable(Constants.FILTER_TARGET_TABLE.SOURCE_TABLE);
    filter.setField(field);
    filter.setCondition(condition);
    filter.setValue(value);
    return filter;
  }

  private JSONArray countResult(int count) {
    return new JSONArray().put(new JSONObject().put("PD_ROWS", String.valueOf(count)));
  }

  private String capturedQuery(DBEngine dbEngine) throws Exception {
    ArgumentCaptor<String> query = ArgumentCaptor.forClass(String.class);
    verify(dbEngine).executeQuery(query.capture(), anyLong(), isNull(Integer.class), anyString(), anyString(), anyString());
    return query.getValue();
  }
}
