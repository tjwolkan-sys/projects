package tests.campaign;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.ArgumentMatchers;
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
import com.powerdialer.engine.db.DBEngine;
import com.powerdialer.model.Campaign;
import com.powerdialer.service.CampaignRecordService;
import com.powerdialer.util.EnCipherUtils;
import com.powerdialer.util.LoggerDecorator;

/**
 * Task 325469 (A4-1): fetchRecords tum kayitlari ara `records` listesine
 * kopyalayip ikinci bir dongude grupluyordu (buyuk kampanyada milyonlarca
 * gecici obje). Bu karakterizasyon testi gruplama davranisini (ListID+status
 * anahtari, Record Count, Record Ids birlesimi, null/JSONObject.NULL
 * semantigi) refactor oncesi kilitler; ara listenin kaldirilmasi sonucu
 * degistirmemelidir.
 */
@RunWith(MockitoJUnitRunner.class)
public class CampaignRecordFetchGroupingCharacterizationTest {

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

  @InjectMocks
  private CampaignRecordService service;

  private final Campaign campaign = new Campaign();

  @Before
  public void init() {
    Whitebox.setInternalState(service, "dbEngine", dbEngine);
    campaign.setId(7L);
    campaign.setDbListTable("[db].[dbo].[list_test]");
    campaign.setDbConnectionString("jdbc:test");
    campaign.setDbUsername("u");
    campaign.setDbPassword("enc");
    campaign.setRetryOveralAttempt(3);
    Map<Long, Campaign> campaignMap = Whitebox.getInternalState(service, "campaignMap");
    campaignMap.put(7L, campaign);
  }

  private JSONObject row(int id, String listId) {
    return new JSONObject()
        .put("id", id)
        .put("ListID", listId)
        .put("complete_date", JSONObject.NULL)
        .put("is_disabled", JSONObject.NULL)
        .put("start_date", JSONObject.NULL)
        .put("is_fetched", JSONObject.NULL)
        .put("retry_date", JSONObject.NULL)
        .put("callback_date", JSONObject.NULL)
        .put("updated_date", JSONObject.NULL)
        .put("telephony_result_code", JSONObject.NULL)
        .put("UpdatedUser", JSONObject.NULL);
  }

  @Test
  public void fetchRecordsGroupsRowsByListIdAndStatusKeepingAllIds() throws Exception {
    JSONArray rows = new JSONArray();
    rows.put(row(101, "L1"));
    rows.put(row(102, "L1"));
    rows.put(row(103, "L2"));
    rows.put(row(104, "L1").put("complete_date", "2026-07-28 12:00:00"));
    when(enCipherUtils.decryptPassword("enc")).thenReturn("pw");
    // Task 327143: fetch, JSONArray biriktiren executeQuery yerine streaming
    // yoluna tasindi; fixture ve assertion'lar degistirilmeden yalniz besleme
    // mekanizmasi guncellendi. Karakterizasyon kilidi ayni kaldi.
    when(dbEngine.executeQueryStreaming(anyString(), eq(7L), eq(Integer.MAX_VALUE), eq("jdbc:test"), eq("u"),
        eq("pw"), ArgumentMatchers.<Consumer<JSONObject>>any())).thenAnswer(new Answer<Boolean>() {
          @Override
          public Boolean answer(InvocationOnMock invocation) {
            Consumer<JSONObject> consumer = invocation.getArgument(6);
            for (int i = 0; i < rows.length(); i++) {
              consumer.accept(rows.getJSONObject(i));
            }
            return false;
          }
        });

    CampaignRecordFilterDTO dto = new CampaignRecordFilterDTO();
    dto.setCampaignId(7L);

    List<Map<String, Object>> result = service.fetchRecords(dto);

    long totalCount = 0;
    Set<String> allIds = new HashSet<>();
    for (Map<String, Object> group : result) {
      assertNotNull(group.get("ListID"));
      assertNotNull(group.get("Statue"));
      List<String> ids = castIds(group);
      totalCount += (Long) group.get("Record Count");
      assertEquals("Record Count grubun id sayisina esit olmali", (long) ids.size(), ((Long) group.get("Record Count")).longValue());
      allIds.addAll(ids);
    }
    assertEquals("Tum satirlar bir grupta sayilmali", rows.length(), totalCount);
    Set<String> expectedIds = new HashSet<>();
    expectedIds.add("101");
    expectedIds.add("102");
    expectedIds.add("103");
    expectedIds.add("104");
    assertEquals("Record Ids birlesimi tum satir id'lerini icermeli", expectedIds, allIds);

    // Gozlenen mevcut davranis (karakterizasyon): bu input setiyle uc L1 satiri
    // ayni status'e siniflanip tek grupta toplanir; L2 ayri gruptur.
    assertEquals("ListID+status anahtariyla 2 grup olusmali", 2, result.size());

    Map<String, Object> groupOfL1 = groupContaining(result, "101");
    assertTrue("Ayni ListID + ayni status satirlari ayni grupta olmali (102)", castIds(groupOfL1).contains("102"));
    assertTrue("Ayni ListID + ayni status satirlari ayni grupta olmali (104)", castIds(groupOfL1).contains("104"));
    assertEquals("L1", groupOfL1.get("ListID"));
    assertEquals(Long.valueOf(3L), groupOfL1.get("Record Count"));

    Map<String, Object> groupOfL2 = groupContaining(result, "103");
    assertTrue("Farkli ListID ayri grupta olmali", groupOfL2 != groupOfL1);
    assertEquals("L2", groupOfL2.get("ListID"));
    assertEquals(Long.valueOf(1L), groupOfL2.get("Record Count"));
  }

  @SuppressWarnings("unchecked")
  private List<String> castIds(Map<String, Object> group) {
    return (List<String>) group.get("Record Ids");
  }

  private Map<String, Object> groupContaining(List<Map<String, Object>> result, String id) {
    for (Map<String, Object> group : result) {
      if (castIds(group).contains(id)) {
        return group;
      }
    }
    throw new AssertionError("Hicbir grup " + id + " id'sini icermiyor");
  }
}
