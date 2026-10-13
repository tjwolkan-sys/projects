package tests.campaign;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
 * Task 327143: DataManipulation fetch yolunda kolon projeksiyonu + streaming
 * gruplama refactor'undan once classifyRecord'un 10 statusunun tamamini
 * uretim semantigiyle kilitler.
 *
 * <p>Uretim semantigi: DBEngine null kolonlari JSONObject'e HIC koymaz
 * (DBEngine.executeParametrizedQuery yalniz getString != null iken put eder).
 * Bu yuzden fixture'larda NULL kolonlar JSONObject.NULL degil, TAMAMEN YOK
 * olarak temsil edilir; classifyRecord bu durumda Java null gorur.</p>
 *
 * <p>Ek kilit: isEnabled(null) == false oldugu icin is_disabled kolonu NULL
 * olan satir retry_date tasisa bile RETRY olamaz, UNPROCESSED'e duser. Bu
 * bilinen ve korunmasi gereken mevcut davranistir.</p>
 */
@RunWith(MockitoJUnitRunner.class)
public class CampaignRecordClassifyStatusCharacterizationTest {

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
    when(enCipherUtils.decryptPassword("enc")).thenReturn("pw");
  }

  /**
   * Fetch edilen satirlari service'e verir. Task 327143 refactor'unda JSONArray
   * donusu streaming consumer'a tasindi; fixture'lar ve assertion'lar
   * degismedi. Cap Integer.MAX_VALUE beklenir cunku bu testte
   * campaignRecords.fetch.maxRows enjekte edilmez (0 -> sinirsiz guard'i).
   */
  private void stubFetchRows(final JSONArray rows) throws Exception {
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
  }

  /** Uretim semantigi: NULL kolonlar JSONObject'te hic yer almaz. */
  private JSONObject row(int id, String listId) {
    return new JSONObject().put("id", id).put("ListID", listId);
  }

  private Map<String, String> statueByListId(List<Map<String, Object>> groups) {
    Map<String, String> byListId = new HashMap<>();
    for (Map<String, Object> group : groups) {
      byListId.put((String) group.get("ListID"), (String) group.get("Statue"));
    }
    return byListId;
  }

  @Test
  public void classifyRecordCoversAllTenStatusesWithProductionNullSemantics() throws Exception {
    JSONArray rows = new JSONArray();
    // 1. COMPLETED: complete_date dolu, attempt limiti asilmamis (ATTEMPTS yok).
    rows.put(row(1, "L_COMPLETED").put("complete_date", "2026-08-01 10:00:00"));
    // 2. ATTEMPTS_LIMIT_REACHED: complete_date dolu + ATTEMPTS >= retryOveralAttempt(3).
    rows.put(row(2, "L_ATTEMPTS").put("complete_date", "2026-08-01 10:00:00").put("ATTEMPTS", "3"));
    // 3. CALLABLE: start_date dolu, aktif (is_disabled=0), retry/callback/complete yok.
    rows.put(row(3, "L_CALLABLE").put("start_date", "2026-08-01 09:00:00").put("is_disabled", "0"));
    // 4. ELIMINATED: pasif (is_disabled=1), updated_date ve UpdatedUser yok.
    rows.put(row(4, "L_ELIMINATED").put("is_disabled", "1"));
    // 5. IN_PROCESS: is_fetched dolu, start_date yok.
    rows.put(row(5, "L_INPROCESS").put("is_fetched", "1").put("is_disabled", "0"));
    // 6. RETRY: retry_date dolu, aktif; start_date dolu (IN_PROCESS'e dusmesin).
    rows.put(row(6, "L_RETRY").put("retry_date", "2026-08-02 10:00:00")
        .put("start_date", "2026-08-01 09:00:00").put("is_disabled", "0"));
    // 7. CALLBACK: callback_date dolu, aktif; start_date dolu, retry yok.
    rows.put(row(7, "L_CALLBACK").put("callback_date", "2026-08-02 16:00:00")
        .put("start_date", "2026-08-01 09:00:00").put("is_disabled", "0"));
    // 8. EXCLUDED: pasif + UpdatedUser dolu (updated_date dolu -> ELIMINATED degil).
    rows.put(row(8, "L_EXCLUDED").put("is_disabled", "1")
        .put("updated_date", "2026-08-01 11:00:00").put("UpdatedUser", "agent1"));
    // 9. DNC: telephony_result_code=76, aktif, diger alanlar yok.
    rows.put(row(9, "L_DNC").put("telephony_result_code", "76").put("is_disabled", "0"));
    // 10. UNPROCESSED: yalniz id + ListID.
    rows.put(row(10, "L_UNPROCESSED"));
    stubFetchRows(rows);

    CampaignRecordFilterDTO dto = new CampaignRecordFilterDTO();
    dto.setCampaignId(7L);

    List<Map<String, Object>> result = service.fetchRecords(dto);
    Map<String, String> statues = statueByListId(result);

    assertEquals("Her satir kendi ListID'siyle ayri grup olmali", 10, result.size());
    assertEquals("status.completed", statues.get("L_COMPLETED"));
    assertEquals("status.attempts_limit_reached", statues.get("L_ATTEMPTS"));
    assertEquals("status.callable", statues.get("L_CALLABLE"));
    assertEquals("status.eliminated", statues.get("L_ELIMINATED"));
    assertEquals("status.in_process", statues.get("L_INPROCESS"));
    assertEquals("status.retry", statues.get("L_RETRY"));
    assertEquals("status.callback", statues.get("L_CALLBACK"));
    assertEquals("status.excluded", statues.get("L_EXCLUDED"));
    assertEquals("status.dnc", statues.get("L_DNC"));
    assertEquals("status.unprocessed", statues.get("L_UNPROCESSED"));
  }

  @Test
  public void nullDisabledFlagBlocksEnabledStatusesAndFallsBackToUnprocessed() throws Exception {
    // isEnabled(null) == false: is_disabled NULL iken retry_date dolu olsa bile
    // RETRY olamaz; ELIMINATED da olamaz (isDisabled(null) == false) -> UNPROCESSED.
    JSONArray rows = new JSONArray();
    rows.put(row(11, "L_NULLDISABLED")
        .put("retry_date", "2026-08-02 10:00:00")
        .put("start_date", "2026-08-01 09:00:00"));
    stubFetchRows(rows);

    CampaignRecordFilterDTO dto = new CampaignRecordFilterDTO();
    dto.setCampaignId(7L);

    List<Map<String, Object>> result = service.fetchRecords(dto);

    assertEquals(1, result.size());
    assertEquals("status.unprocessed", result.get(0).get("Statue"));
  }

  @Test
  public void attemptsBelowLimitOrUnparsableStaysCompleted() throws Exception {
    JSONArray rows = new JSONArray();
    // Limitin altinda.
    rows.put(row(12, "L_BELOW").put("complete_date", "2026-08-01 10:00:00").put("ATTEMPTS", "2"));
    // Sayi olmayan ATTEMPTS sessizce yutulur (NumberFormatException -> false).
    rows.put(row(13, "L_BADNUM").put("complete_date", "2026-08-01 10:00:00").put("ATTEMPTS", "abc"));
    stubFetchRows(rows);

    CampaignRecordFilterDTO dto = new CampaignRecordFilterDTO();
    dto.setCampaignId(7L);

    Map<String, String> statues = statueByListId(service.fetchRecords(dto));

    assertEquals("status.completed", statues.get("L_BELOW"));
    assertEquals("status.completed", statues.get("L_BADNUM"));
  }
}
