package tests.datamanipulation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;
import org.powermock.reflect.Whitebox;

import com.powerdialer.dao.impl.CampaignDao;
import com.powerdialer.dao.impl.CampaignRecordDao;
import com.powerdialer.dao.impl.TenantDao;
import com.powerdialer.dto.CampaignRecordFilterDTO;
import com.powerdialer.engine.db.CampaignDbOperations;
import com.powerdialer.engine.db.DBEngine;
import com.powerdialer.model.Campaign;
import com.powerdialer.service.CampaignRecordService;
import com.powerdialer.util.Constants;
import com.powerdialer.util.EnCipherUtils;
import com.powerdialer.util.LoggerDecorator;

/**
 * Task 327143: DataManipulation fetch'inin bellek fix'ini kilitler:
 * (1) SELECT * yerine metadata kesisimli, declared-case korunan kolon
 * projeksiyonu; (2) Integer.MAX_VALUE yerine konfigurable satir tavani;
 * (3) tavan asildiginda truncated bilgisinin fetchAll sonucuna tasinmasi;
 * (4) metadata alinamazsa SELECT * fallback'i (davranis korunur).
 */
@RunWith(MockitoJUnitRunner.class)
public class CampaignRecordFetchProjectionAndCapRegressionTest {

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private LoggerDecorator loggerDecorator;

  @Mock
  private CampaignDao campaignDao;

  @Mock
  private TenantDao tenantDao;

  @Mock
  private EnCipherUtils enCipherUtils;

  @Mock
  private CampaignRecordDao campaignRecordDao;

  @Mock
  private DBEngine dbEngine;

  @Mock
  private CampaignDbOperations campaignDbOperations;

  @InjectMocks
  private CampaignRecordService service;

  private final Campaign campaign = new Campaign();

  private static final String[][] FULL_METADATA = new String[][] {
      {"ID", "int"},
      {"ListID", "nvarchar"},
      {"Complete_Date", "datetime"},
      {"is_disabled", "bit"},
      {"start_date", "datetime"},
      {"is_fetched", "bit"},
      {"retry_date", "datetime"},
      {"callback_date", "datetime"},
      {"updated_date", "datetime"},
      {"telephony_result_code", "int"},
      {"UpdatedUser", "nvarchar"},
      {"attempts", "int"},
      {"customer_name", "nvarchar"},
      {"address", "nvarchar"}
  };

  private static final String EXPECTED_PROJECTION =
      "ID, ListID, Complete_Date, is_disabled, start_date, is_fetched, retry_date, "
          + "callback_date, updated_date, telephony_result_code, UpdatedUser, attempts";

  @Before
  public void init() {
    Whitebox.setInternalState(service, "dbEngine", dbEngine);
    Whitebox.setInternalState(service, "campaignDbOperations", campaignDbOperations);
    campaign.setId(7L);
    campaign.setDbListTable("[db].[dbo].[list_test]");
    campaign.setDbConnectionString("jdbc:test");
    campaign.setDbUsername("u");
    campaign.setDbPassword("enc");
    campaign.setRetryOveralAttempt(3);
    Map<Long, Campaign> campaignMap = Whitebox.getInternalState(service, "campaignMap");
    campaignMap.put(7L, campaign);
    when(enCipherUtils.decryptPassword("enc")).thenReturn("pw");
  }

  private void stubMetadata(String[][] metadata) throws SQLException {
    when(campaignDbOperations.getTableColumnsWithTypes("[db].[dbo].[list_test]", 7L, "jdbc:test", "u", "pw"))
        .thenReturn(metadata);
  }

  private ArgumentCaptor<String> stubStreaming(final JSONArray rows, final boolean truncated) throws SQLException {
    ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
    when(dbEngine.executeQueryStreaming(queryCaptor.capture(), eq(7L), anyInt(), eq("jdbc:test"), eq("u"), eq("pw"),
        any())).thenAnswer(new Answer<Boolean>() {
          @Override
          public Boolean answer(InvocationOnMock invocation) {
            @SuppressWarnings("unchecked")
            Consumer<JSONObject> consumer = (Consumer<JSONObject>) invocation.getArgument(6);
            for (int i = 0; i < rows.length(); i++) {
              consumer.accept(rows.getJSONObject(i));
            }
            return truncated;
          }
        });
    return queryCaptor;
  }

  private JSONArray singleRow() {
    return new JSONArray().put(new JSONObject().put("id", 1).put("ListID", "L1"));
  }

  private CampaignRecordFilterDTO dto() {
    CampaignRecordFilterDTO dto = new CampaignRecordFilterDTO();
    dto.setCampaignId(7L);
    return dto;
  }

  @Test
  public void fetchProjectsOnlyClassificationColumnsPreservingDeclaredCase() throws Exception {
    stubMetadata(FULL_METADATA);
    ArgumentCaptor<String> query = stubStreaming(singleRow(), false);

    service.fetchRecords(dto());

    assertEquals("SELECT " + EXPECTED_PROJECTION + " FROM [db].[dbo].[list_test]", query.getValue());
    assertFalse("Genis musteri kolonlari SELECT'e girmemeli", query.getValue().contains("customer_name"));
  }

  @Test
  public void fetchPassesConfiguredRowCapToDbEngine() throws Exception {
    stubMetadata(FULL_METADATA);
    Whitebox.setInternalState(service, "fetchRecordsMaxRows", 123);
    JSONArray rows = singleRow();
    ArgumentCaptor<Integer> capCaptor = ArgumentCaptor.forClass(Integer.class);
    when(dbEngine.executeQueryStreaming(anyString(), eq(7L), capCaptor.capture(), eq("jdbc:test"), eq("u"), eq("pw"),
        any())).thenAnswer(feedRows(rows, false));

    service.fetchRecords(dto());

    assertEquals(Integer.valueOf(123), capCaptor.getValue());
  }

  @Test
  public void unsetOrNonPositiveCapFallsBackToUnlimited() throws Exception {
    stubMetadata(FULL_METADATA);
    // @Value enjekte edilmemis (unit test) -> alan 0; davranis bugunku gibi sinirsiz olmali.
    JSONArray rows = singleRow();
    ArgumentCaptor<Integer> capCaptor = ArgumentCaptor.forClass(Integer.class);
    when(dbEngine.executeQueryStreaming(anyString(), eq(7L), capCaptor.capture(), eq("jdbc:test"), eq("u"), eq("pw"),
        any())).thenAnswer(feedRows(rows, false));

    service.fetchRecords(dto());

    assertEquals(Integer.valueOf(Integer.MAX_VALUE), capCaptor.getValue());
  }

  @Test
  public void emptyMetadataFallsBackToSelectStar() throws Exception {
    stubMetadata(new String[0][]);
    ArgumentCaptor<String> query = stubStreaming(singleRow(), false);

    service.fetchRecords(dto());

    assertTrue("Metadata bos ise SELECT * korunmali: " + query.getValue(),
        query.getValue().startsWith("SELECT * FROM [db].[dbo].[list_test]"));
  }

  @Test
  public void metadataFailureFallsBackToSelectStar() throws Exception {
    when(campaignDbOperations.getTableColumnsWithTypes("[db].[dbo].[list_test]", 7L, "jdbc:test", "u", "pw"))
        .thenThrow(new SQLException("metadata unavailable"));
    ArgumentCaptor<String> query = stubStreaming(singleRow(), false);

    service.fetchRecords(dto());

    assertTrue("Metadata hatasi fetch'i kirmamali, SELECT * ile devam etmeli",
        query.getValue().startsWith("SELECT * FROM [db].[dbo].[list_test]"));
  }

  @Test
  public void whereClauseFromFiltersIsPreservedWithProjection() throws Exception {
    stubMetadata(FULL_METADATA);
    ArgumentCaptor<String> query = stubStreaming(singleRow(), false);

    CampaignRecordFilterDTO dto = dto();
    dto.setLogicOperator("AND");
    CampaignRecordFilterDTO.FilterRow row = new CampaignRecordFilterDTO.FilterRow();
    row.setField("customer_name");
    row.setCondition(Constants.FILTER_CONDITION.EQUAL);
    row.setValue("x");
    row.setFieldType("STRING");
    dto.setFilterRows(Collections.singletonList(row));
    service.fetchRecords(dto);

    assertTrue("Filtre WHERE'i korunmali", query.getValue().contains(" WHERE "));
    assertTrue("Projeksiyona girmeyen kolon WHERE'de kullanilabilmeli",
        query.getValue().indexOf("customer_name") > query.getValue().indexOf(" WHERE "));
    assertTrue("SELECT kismi projeksiyonlu kalmali",
        query.getValue().startsWith("SELECT " + EXPECTED_PROJECTION + " FROM"));
  }

  @Test
  public void truncationFlagPropagatesToFetchAllOutcome() throws Exception {
    stubMetadata(FULL_METADATA);
    stubStreaming(singleRow(), true);
    when(dbEngine.executeQuery(anyString(), eq(7L), eq(Integer.MAX_VALUE), eq("jdbc:test"), eq("u"), eq("pw")))
        .thenReturn(new JSONArray());

    CampaignRecordService.FetchAllOutcome outcome = service.fetchAllWithTruncation(dto());

    assertTrue("DBEngine truncated dondugunde fetchAll sonucuna tasinmali", outcome.isRecordsTruncated());
    List<Map<String, Object>> records = outcome.getData().get(Constants.RECORDS);
    assertEquals(1, records.size());
  }

  @Test
  public void noTruncationYieldsFalseFlagAndSameDataShape() throws Exception {
    stubMetadata(FULL_METADATA);
    stubStreaming(singleRow(), false);
    when(dbEngine.executeQuery(anyString(), eq(7L), eq(Integer.MAX_VALUE), eq("jdbc:test"), eq("u"), eq("pw")))
        .thenReturn(new JSONArray());

    CampaignRecordService.FetchAllOutcome outcome = service.fetchAllWithTruncation(dto());

    assertFalse(outcome.isRecordsTruncated());
    assertTrue(outcome.getData().containsKey(Constants.RECORDS));
    assertTrue(outcome.getData().containsKey(Constants.UserRC));
    assertTrue(outcome.getData().containsKey(Constants.TelephonyRC));
  }

  private Answer<Boolean> feedRows(final JSONArray rows, final boolean truncated) {
    return new Answer<Boolean>() {
      @Override
      public Boolean answer(InvocationOnMock invocation) {
        @SuppressWarnings("unchecked")
        Consumer<JSONObject> consumer = (Consumer<JSONObject>) invocation.getArgument(6);
        for (int i = 0; i < rows.length(); i++) {
          consumer.accept(rows.getJSONObject(i));
        }
        return truncated;
      }
    };
  }

  /** fetchRecords eski public imzasini korumali: gruplari dogrudan dondurur. */
  @Test
  public void fetchRecordsPublicSignatureStillReturnsGroups() throws Exception {
    stubMetadata(FULL_METADATA);
    stubStreaming(singleRow(), false);

    List<Map<String, Object>> groups = service.fetchRecords(dto());

    assertEquals(1, groups.size());
    assertEquals("L1", groups.get(0).get("ListID"));
    assertEquals(new ArrayList<>(Collections.singletonList("1")), groups.get(0).get("Record Ids"));
  }
}
