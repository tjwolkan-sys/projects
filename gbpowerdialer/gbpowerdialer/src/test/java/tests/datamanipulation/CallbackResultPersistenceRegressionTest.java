package tests.datamanipulation;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.powerdialer.engine.db.CampaignDbOperations;
import com.powerdialer.engine.db.DBEngine;
import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.util.EnCipherUtils;
import com.powerdialer.util.LoggerDecorator;

@RunWith(MockitoJUnitRunner.class)
public class CallbackResultPersistenceRegressionTest {

  @Mock
  private DBEngine dbEngine;

  @Mock
  private EnCipherUtils enCipherUtils;

  @Test
  public void resultUpdateClearsCallbackNumberWhenCallbackDateIsCleared() throws Exception {
    Campaign campaign = campaign();
    Call call = callbackCallFromCampaignTable(campaign);
    CampaignDbOperations operations = new CampaignDbOperations(dbEngine, new LoggerDecorator(), null, enCipherUtils, null);
    when(enCipherUtils.decryptPassword("encrypted")).thenReturn("plain");
    when(dbEngine.executeQuery(anyString(), anyLong(), isNull(Integer.class), anyString(), anyString(), anyString()))
        .thenReturn(new JSONArray().put(new JSONObject().put("PD_ROWS", "1")));

    operations.insertCallResultToDB(call);

    ArgumentCaptor<String> updateSql = ArgumentCaptor.forClass(String.class);
    verify(dbEngine).executeUpdate(updateSql.capture(), anyLong(), anyString(), anyString(), anyString());
    assertTrue(updateSql.getValue().contains("callback_date = NULL"));
    assertTrue(updateSql.getValue().contains("callback_number = NULL"));
    assertFalse(updateSql.getValue().contains("callback_number = '905551112233'"));
  }

  @Test
  public void resultInsertDoesNotCarryCallbackNumberWhenCallbackDateIsAbsent() throws Exception {
    Campaign campaign = campaign();
    campaign.setDbListTable("campaign_source");
    campaign.setColResultUniqueKey("source_id");
    Call call = callbackCallFromCampaignTable(campaign);
    CampaignDbOperations operations = new CampaignDbOperations(dbEngine, new LoggerDecorator(), null, enCipherUtils, null);
    when(enCipherUtils.decryptPassword("encrypted")).thenReturn("plain");
    when(dbEngine.executeQuery(anyString(), anyLong(), isNull(Integer.class), anyString(), anyString(), anyString()))
        .thenReturn(new JSONArray().put(new JSONObject().put("PD_ROWS", "0")));

    operations.insertCallResultToDB(call);

    ArgumentCaptor<String> insertSql = ArgumentCaptor.forClass(String.class);
    verify(dbEngine).executeUpdate(insertSql.capture(), anyLong(), anyString(), anyString(), anyString());
    assertTrue(insertSql.getValue().contains("callback_date"));
    assertTrue(insertSql.getValue().contains("NULL"));
    assertFalse(insertSql.getValue().contains("callback_number"));
    assertFalse(insertSql.getValue().contains("905551112233"));
  }

  @Test
  public void dncFilteredStatusClearsUserResultCodeInCampaignTable() throws Exception {
    Campaign campaign = campaign();
    campaign.setColResultCompleteDate("complete_date");
    campaign.setColResultTelephonyRc("telephony_result_code");
    campaign.setColResultUserId("user_id");
    campaign.setColResultUserRc("user_result_code");
    CampaignDbOperations operations = new CampaignDbOperations(dbEngine, new LoggerDecorator(), null, enCipherUtils, null);
    when(enCipherUtils.decryptPassword("encrypted")).thenReturn("plain");

    Call call = new Call();
    call.setUniqueKey("42");

    operations.setFilteredByDncListStatus(campaign, java.util.Collections.singletonList(call));

    ArgumentCaptor<String> updateSql = ArgumentCaptor.forClass(String.class);
    verify(dbEngine).executeUpdate(updateSql.capture(), anyLong(), anyString(), anyString(), anyString());
    assertTrue(updateSql.getValue().contains("telephony_result_code = 76"));
    assertTrue(updateSql.getValue().contains("user_id = NULL"));
    assertTrue(updateSql.getValue().contains("user_result_code = NULL"));
  }

  @Test
  public void resultUpdateClearsDncNumbersWhenDncNumbersBecomeEmpty() throws Exception {
    Campaign campaign = campaign();
    campaign.setColResultDNCNumbers("dnc_numbers");
    Call call = callWithEmptyDncNumbersFromCampaignTable(campaign);
    CampaignDbOperations operations = new CampaignDbOperations(dbEngine, new LoggerDecorator(), null, enCipherUtils, null);
    when(enCipherUtils.decryptPassword("encrypted")).thenReturn("plain");
    when(dbEngine.executeQuery(anyString(), anyLong(), isNull(Integer.class), anyString(), anyString(), anyString()))
        .thenReturn(new JSONArray().put(new JSONObject().put("PD_ROWS", "1")));

    operations.insertCallResultToDB(call);

    ArgumentCaptor<String> updateSql = ArgumentCaptor.forClass(String.class);
    verify(dbEngine).executeUpdate(updateSql.capture(), anyLong(), anyString(), anyString(), anyString());
    assertTrue(updateSql.getValue().contains("dnc_numbers = NULL"));
    assertFalse(updateSql.getValue().contains("dnc_numbers = '905551112233'"));
  }

  @Test
  public void resultUpdateWritesFreshCallProcessForFreshOnlyCall() throws Exception {
    Campaign campaign = campaign();
    campaign.setColResultCallProcess("call_process");
    Call call = freshCall(campaign);
    CampaignDbOperations operations = new CampaignDbOperations(dbEngine, new LoggerDecorator(), null, enCipherUtils, null);
    when(enCipherUtils.decryptPassword("encrypted")).thenReturn("plain");
    when(dbEngine.executeQuery(anyString(), anyLong(), isNull(Integer.class), anyString(), anyString(), anyString()))
        .thenReturn(new JSONArray().put(new JSONObject().put("PD_ROWS", "1")));

    operations.insertCallResultToDB(call);

    ArgumentCaptor<String> updateSql = ArgumentCaptor.forClass(String.class);
    verify(dbEngine).executeUpdate(updateSql.capture(), anyLong(), anyString(), anyString(), anyString());
    assertTrue(updateSql.getValue().contains("call_process = 'F'"));
  }

  @Test
  public void resultInsertWritesHistoryValuesWithSingleClosingQuote() throws Exception {
    Campaign campaign = campaign();
    campaign.setDbListTable("campaign_source");
    campaign.setColResultUniqueKey("source_id");
    campaign.setColResultRetryHistory("retry_history");
    campaign.setColResultCbHistory("cb_history");
    campaign.setColResultCallProcess("call_process");
    Call call = freshCall(campaign);
    call.getRetryHistory().add("905551112233");
    call.addCallbackHistoryEntry("905559998877");
    CampaignDbOperations operations = new CampaignDbOperations(dbEngine, new LoggerDecorator(), null, enCipherUtils, null);
    when(enCipherUtils.decryptPassword("encrypted")).thenReturn("plain");
    when(dbEngine.executeQuery(anyString(), anyLong(), isNull(Integer.class), anyString(), anyString(), anyString()))
        .thenReturn(new JSONArray().put(new JSONObject().put("PD_ROWS", "0")));

    operations.insertCallResultToDB(call);

    ArgumentCaptor<String> insertSql = ArgumentCaptor.forClass(String.class);
    verify(dbEngine).executeUpdate(insertSql.capture(), anyLong(), anyString(), anyString(), anyString());
    assertTrue(insertSql.getValue().contains("retry_history"));
    assertTrue(insertSql.getValue().contains("cb_history"));
    assertTrue(insertSql.getValue().contains("call_process"));
    assertTrue(insertSql.getValue().contains("'905551112233',"));
    assertTrue(insertSql.getValue().contains("'905559998877',"));
    assertTrue(insertSql.getValue().contains("'F;CB'"));
    assertFalse(insertSql.getValue().contains("'905551112233'',"));
    assertFalse(insertSql.getValue().contains("'905559998877'',"));
    assertFalse(insertSql.getValue().contains("'F;CB'',"));
  }

  private Call callbackCallFromCampaignTable(Campaign campaign) {
    Call call = new Call();
    call.setCampaign(campaign);
    call.setUniqueKey("42");
    call.setRawData(new JSONObject()
        .put("id", "42")
        .put("callback_date", 1770000000000D)
        .put("callback_number", "905551112233"));
    call.setCallbackDate(null);
    call.setCallbackNumber(null);
    return call;
  }

  private Call callWithEmptyDncNumbersFromCampaignTable(Campaign campaign) {
    Call call = new Call();
    call.setCampaign(campaign);
    call.setUniqueKey("42");
    call.setRawData(new JSONObject()
        .put("id", "42")
        .put("dnc_numbers", "905551112233"));
    call.setDNCNumbers(java.util.Collections.emptySet());
    return call;
  }

  private Call freshCall(Campaign campaign) {
    Call call = new Call();
    call.setCampaign(campaign);
    call.setUniqueKey("42");
    call.setRawData(new JSONObject().put("id", "42"));
    return call;
  }

  private Campaign campaign() {
    Campaign campaign = new Campaign();
    campaign.setId(1L);
    campaign.setDbConnectionString("jdbc:sqlserver://test-host;databaseName=test");
    campaign.setDbUsername("user");
    campaign.setDbPassword("encrypted");
    campaign.setDbListTable("campaign_result");
    campaign.setDbResultTable("campaign_result");
    campaign.setColUniqueKey("id");
    campaign.setColResultCallbackDate("callback_date");
    campaign.setColResultCallbackNumber("callback_number");
    return campaign;
  }
}
