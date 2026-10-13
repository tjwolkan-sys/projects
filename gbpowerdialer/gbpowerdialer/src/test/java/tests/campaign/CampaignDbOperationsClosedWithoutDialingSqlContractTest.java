package tests.campaign;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import com.powerdialer.engine.db.CampaignDbOperations;
import com.powerdialer.engine.db.DBEngine;
import com.powerdialer.model.Campaign;
import com.powerdialer.util.Constants;
import com.powerdialer.util.EnCipherUtils;
import com.powerdialer.util.LoggerDecorator;

/**
 * FEAT-325559 Faz 4: {@code setClosedWithoutDialing} SQL contract'i.
 *
 * <p>DNC kapatmasindan iki KRITIK farki kilitlenir:</p>
 *
 * <ul>
 * <li>{@code telephony_result_code} YAZILMAZ (K11) — yapilmamis cagri icin sonuc kodu uretilmez ve
 * kayit daha once gercekten aranmissa o cagrinin TRC'si korunur.</li>
 * <li>{@code user_id} / {@code user_result_code} alanlarina DOKUNULMAZ — kayit gecmiste agent
 * disposition'i almis olabilir; onlari NULL'lamak URC kaybi olurdu.</li>
 * </ul>
 */
public class CampaignDbOperationsClosedWithoutDialingSqlContractTest {

  private DBEngine dbEngine;
  private CampaignDbOperations operations;

  @Before
  public void init() {
    dbEngine = mock(DBEngine.class);
    EnCipherUtils enCipherUtils = mock(EnCipherUtils.class);
    when(enCipherUtils.decryptPassword(any())).thenReturn("plain");
    operations = new CampaignDbOperations(dbEngine, new LoggerDecorator(), null, enCipherUtils, null);
  }

  /**
   * Kaynak ve result tablosu AYNI: eksik satir insert'i gerekmez, tek UPDATE beklenir.
   */
  private Campaign sameTableCampaign(String closeReasonBinding) {
    Campaign campaign = new Campaign();
    campaign.setId(99L);
    campaign.setDbListTable("single_table");
    campaign.setDbResultTable("single_table");
    campaign.setColUniqueKey("id");
    campaign.setColResultCompleteDate("complete_date");
    campaign.setColResultTelephonyRc("telephony_result_code");
    campaign.setColResultUserId("user_id");
    campaign.setColResultUserRc("user_result_code");
    campaign.setColResultCloseReason(closeReasonBinding);
    campaign.setDbConnectionString("jdbc:sqlserver://placeholder");
    campaign.setDbUsername("placeholder");
    campaign.setDbPassword("encrypted");
    return campaign;
  }

  private Campaign separateTableCampaign() {
    Campaign campaign = sameTableCampaign("close_reason");
    campaign.setDbListTable("list_table");
    campaign.setDbResultTable("result_table");
    campaign.setColResultUniqueKey("result_id");
    return campaign;
  }

  private List<String> capturedUpdates() throws Exception {
    ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
    verify(dbEngine, org.mockito.Mockito.atLeastOnce())
        .executeUpdate(captor.capture(), eq(99L), anyString(), anyString(), eq("plain"));
    return captor.getAllValues();
  }

  @Test
  public void updateSetsCompleteDateAndDisabledWithCloseReason() throws Exception {
    operations.setClosedWithoutDialing(sameTableCampaign("close_reason"),
        Arrays.asList("41", "42"), Constants.CLOSE_REASON_NUMBER_LIMIT_REACHED);

    String query = capturedUpdates().get(0);

    assertTrue(query.startsWith("UPDATE single_table SET "));
    assertTrue(query.contains("complete_date = '"));
    assertTrue(query.contains("is_disabled = " + Constants.DISABLED));
    assertTrue(query.contains("close_reason = '" + Constants.CLOSE_REASON_NUMBER_LIMIT_REACHED + "'"));
    assertTrue(query.contains("WHERE id IN (41,42)"));
    assertTrue("Yalniz henuz kapanmamis satirlara dokunulmali",
        query.contains("AND complete_date IS NULL"));
  }

  /**
   * K11: TRC yazilmaz. DNC kapatmasindan en onemli fark.
   */
  @Test
  public void telephonyResultCodeIsNeverWritten() throws Exception {
    operations.setClosedWithoutDialing(sameTableCampaign("close_reason"),
        Collections.singletonList("41"), Constants.CLOSE_REASON_NO_CALLABLE_NUMBER);

    String query = capturedUpdates().get(0);

    assertFalse("Yapilmamis cagri icin sonuc kodu uretilmemeli",
        query.contains("telephony_result_code"));
  }

  /**
   * Kayit gecmiste aranmis ve agent disposition'i almis olabilir; user alanlari korunmalidir.
   */
  @Test
  public void userOwnershipFieldsAreNotNullified() throws Exception {
    operations.setClosedWithoutDialing(sameTableCampaign("close_reason"),
        Collections.singletonList("41"), Constants.CLOSE_REASON_NUMBER_LIMIT_REACHED);

    String query = capturedUpdates().get(0);

    assertFalse("user_id NULL'lanmamali (URC kaybi riski)", query.contains("user_id = NULL"));
    assertFalse("user_result_code NULL'lanmamali", query.contains("user_result_code = NULL"));
  }

  @Test
  public void closeReasonIsOmittedWhenBindingIsMissing() throws Exception {
    operations.setClosedWithoutDialing(sameTableCampaign(null),
        Collections.singletonList("41"), Constants.CLOSE_REASON_NUMBER_LIMIT_REACHED);

    String query = capturedUpdates().get(0);

    assertFalse(query.contains("close_reason"));
    assertTrue("Kapanis yine de gerceklesmeli", query.contains("complete_date = '"));
    assertTrue(query.contains("is_disabled = " + Constants.DISABLED));
  }

  @Test
  public void closeReasonIsOmittedWhenReasonIsBlank() throws Exception {
    operations.setClosedWithoutDialing(sameTableCampaign("close_reason"),
        Collections.singletonList("41"), "   ");

    String query = capturedUpdates().get(0);

    assertFalse(query.contains("close_reason ="));
  }

  /**
   * Result satiri yoksa UPDATE 0 satir etkiler ve kayit her tick'te yeniden SELECT edilirdi;
   * bu yuzden ayri tablolarda once eksik satirlar eklenir.
   */
  @Test
  public void missingResultRowsAreInsertedBeforeUpdateWhenTablesDiffer() throws Exception {
    operations.setClosedWithoutDialing(separateTableCampaign(),
        Collections.singletonList("41"), Constants.CLOSE_REASON_NUMBER_LIMIT_REACHED);

    List<String> queries = capturedUpdates();

    assertEquals("Once INSERT sonra UPDATE beklenir", 2, queries.size());
    assertTrue(queries.get(0).startsWith("INSERT INTO result_table(result_id)"));
    assertTrue(queries.get(0).contains("LEFT JOIN result_table"));
    assertTrue("Yalniz result satiri OLMAYAN key'ler eklenmeli",
        queries.get(0).contains("res.result_id IS NULL"));
    assertTrue(queries.get(1).startsWith("UPDATE result_table SET "));
    assertTrue(queries.get(1).contains("WHERE result_id IN (41)"));
  }

  @Test
  public void singleTableCampaignSkipsInsertStep() throws Exception {
    operations.setClosedWithoutDialing(sameTableCampaign("close_reason"),
        Collections.singletonList("41"), Constants.CLOSE_REASON_NUMBER_LIMIT_REACHED);

    List<String> queries = capturedUpdates();

    assertEquals("Tek tabloda insert adimi gereksizdir", 1, queries.size());
    assertTrue(queries.get(0).startsWith("UPDATE "));
  }

  @Test
  public void emptyKeyListPerformsNoDatabaseCall() throws Exception {
    operations.setClosedWithoutDialing(sameTableCampaign("close_reason"),
        Collections.<String>emptyList(), Constants.CLOSE_REASON_NUMBER_LIMIT_REACHED);

    verify(dbEngine, never()).executeUpdate(anyString(), anyLong(), anyString(), anyString(), anyString());
  }

  @Test
  public void nullKeyListPerformsNoDatabaseCall() throws Exception {
    operations.setClosedWithoutDialing(sameTableCampaign("close_reason"), null,
        Constants.CLOSE_REASON_NUMBER_LIMIT_REACHED);

    verify(dbEngine, never()).executeUpdate(anyString(), anyLong(), anyString(), anyString(), anyString());
  }

  /**
   * 1000'den fazla key parcalara bolunur (mevcut {@code partitionKeys} deseni).
   */
  @Test
  public void keyListIsChunkedAtOneThousand() throws Exception {
    List<String> keys = new java.util.ArrayList<>();
    for (int i = 0; i < 2500; i++) {
      keys.add(String.valueOf(i));
    }

    operations.setClosedWithoutDialing(sameTableCampaign("close_reason"), keys,
        Constants.CLOSE_REASON_NUMBER_LIMIT_REACHED);

    assertEquals("2500 key -> 3 UPDATE", 3, capturedUpdates().size());
  }

  @Test
  public void singleQuoteInReasonIsEscaped() throws Exception {
    operations.setClosedWithoutDialing(sameTableCampaign("close_reason"),
        Collections.singletonList("41"), "limit's reached");

    String query = capturedUpdates().get(0);

    assertTrue(query.contains("close_reason = 'limit''s reached'"));
  }

}
