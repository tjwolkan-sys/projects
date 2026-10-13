package tests.dialer;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import com.powerdialer.engine.db.CampaignDbOperations;
import com.powerdialer.engine.db.DBEngine;
import com.powerdialer.model.CDR;
import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.TelephonyResultCode;
import com.powerdialer.util.Constants;
import com.powerdialer.util.EnCipherUtils;
import com.powerdialer.util.LoggerDecorator;

/**
 * FEAT-325559 Faz 3 — kapanis nedeninin result tablosuna yazim contract'i.
 *
 * <p>Yazim KOSULLUDUR: yalniz binding eslendiginde VE gercekten bir kapanis nedeni uretildiginde
 * SET listesine girer. Bunun iki gerekcesi var:</p>
 *
 * <ul>
 * <li><b>R12:</b> kolonu bulunmayan bir result tablosuna yazim denemesi SQL Server'da
 * "Invalid column name" ile TUM UPDATE'i dusururdu ve cagri sonucu hic kaydedilmezdi
 * (FEAT-318332 vakasinin aynisi).</li>
 * <li><b>D3:</b> son cagrinin gercek TRC/URC'si EZILMEZ; kapanis nedeni ayri bir alanda
 * tutulur.</li>
 * </ul>
 */
public class MaxAttemptPerNumberCloseReasonPersistenceTest {

  private DBEngine dbEngine;
  private CampaignDbOperations operations;

  @Before
  public void init() throws Exception {
    dbEngine = mock(DBEngine.class);
    EnCipherUtils enCipherUtils = mock(EnCipherUtils.class);
    when(enCipherUtils.decryptPassword(any())).thenReturn("plain");
    operations = new CampaignDbOperations(dbEngine, new LoggerDecorator(), null, enCipherUtils, null);

    // rowExistsInResultTableByCallUniqueKey -> UPDATE dalina yonlendir.
    JSONArray existingRow = new JSONArray();
    existingRow.put(new JSONObject().put("PD_ROWS", "1"));
    when(dbEngine.executeQuery(anyString(), anyLong(), any(), anyString(), anyString(), anyString()))
        .thenReturn(existingRow);
  }

  private Campaign campaign(String closeReasonBinding) {
    Campaign campaign = new Campaign();
    campaign.setId(99L);
    campaign.setDbResultTable("result_table");
    campaign.setDbListTable("list_table");
    campaign.setDbConnectionString("jdbc:sqlserver://placeholder");
    campaign.setDbUsername("placeholder");
    campaign.setDbPassword("encrypted");
    campaign.setColUniqueKey("id");
    campaign.setColResultUniqueKey("result_id");
    campaign.setColResultCompleteDate("complete_date");
    campaign.setColResultTelephonyRc("telephony_result_code");
    campaign.setColResultCloseReason(closeReasonBinding);
    return campaign;
  }

  private Call call(Campaign campaign, String closeReason) {
    Call call = new Call();
    call.setCampaign(campaign);
    call.setRawData(new JSONObject());
    call.setUniqueKey("41");
    call.setCdr(new CDR());
    call.setCompleteDate(45000D);
    call.setCloseReason(closeReason);

    TelephonyResultCode userBusy = new TelephonyResultCode();
    userBusy.setId(11L);
    userBusy.setCode(17);
    call.setTelephonyResultCode(userBusy);
    return call;
  }

  private String capturedUpdate() throws Exception {
    ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
    verify(dbEngine).executeUpdate(captor.capture(), eq(99L), anyString(), anyString(), eq("plain"));
    return captor.getValue();
  }

  @Test
  public void closeReasonIsWrittenWhenBindingAndReasonBothExist() throws Exception {
    operations.insertCallResultToDB(call(campaign("close_reason"), Constants.CLOSE_REASON_NUMBER_LIMIT_REACHED));

    String query = capturedUpdate();

    assertTrue(query.startsWith("UPDATE result_table SET "));
    assertTrue("Kapanis nedeni yazilmalidir",
        query.contains("close_reason = '" + Constants.CLOSE_REASON_NUMBER_LIMIT_REACHED + "'"));
  }

  @Test
  public void customBindingColumnNameIsUsedVerbatim() throws Exception {
    operations.insertCallResultToDB(call(campaign("kapanis_nedeni"), Constants.CLOSE_REASON_NUMBER_LIMIT_REACHED));

    String query = capturedUpdate();

    assertTrue(query.contains("kapanis_nedeni = '" + Constants.CLOSE_REASON_NUMBER_LIMIT_REACHED + "'"));
    assertFalse(query.contains("close_reason ="));
  }

  /**
   * R12: binding eslenmemisse kolon adi UPDATE'e HIC girmez; boylece kolonu olmayan
   * kampanyalarin result yazimi etkilenmez.
   */
  @Test
  public void unboundCloseReasonIsNeverWrittenEvenWhenReasonIsSet() throws Exception {
    operations.insertCallResultToDB(call(campaign(null), Constants.CLOSE_REASON_NUMBER_LIMIT_REACHED));

    String query = capturedUpdate();

    assertFalse("Binding yokken kolon adi sorguya girmemelidir", query.contains("close_reason"));
    assertTrue("Diger alanlar normal yazilmaya devam etmeli", query.contains("complete_date = "));
  }

  /**
   * Basarili kapanista neden set edilmedigi icin kolon da yazilmaz (R11'in persistence tarafi).
   */
  @Test
  public void noCloseReasonMeansColumnIsNotInUpdateStatement() throws Exception {
    operations.insertCallResultToDB(call(campaign("close_reason"), null));

    String query = capturedUpdate();

    assertFalse(query.contains("close_reason ="));
    assertTrue(query.contains("complete_date = "));
  }

  @Test
  public void blankCloseReasonIsTreatedAsAbsent() throws Exception {
    operations.insertCallResultToDB(call(campaign("close_reason"), "   "));

    String query = capturedUpdate();

    assertFalse(query.contains("close_reason ="));
  }

  /**
   * D3: kapanis nedeni yazilirken son cagrinin gercek TRC'si korunur, EZILMEZ.
   */
  @Test
  public void telephonyResultCodeIsPreservedWhileCloseReasonIsWritten() throws Exception {
    operations.insertCallResultToDB(call(campaign("close_reason"), Constants.CLOSE_REASON_NUMBER_LIMIT_REACHED));

    String query = capturedUpdate();

    assertTrue("Gercek TRC korunmalidir", query.contains("telephony_result_code = '11'"));
    assertTrue(query.contains("close_reason = '"));
  }

  /**
   * Callback kaynakli kapanis kendi nedenini tasir.
   */
  @Test
  public void callbackCloseReasonIsWrittenVerbatim() throws Exception {
    operations.insertCallResultToDB(call(campaign("close_reason"), Constants.CLOSE_REASON_MAX_ATTEMPT_ON_CALLBACK));

    String query = capturedUpdate();

    assertTrue(query.contains("close_reason = '" + Constants.CLOSE_REASON_MAX_ATTEMPT_ON_CALLBACK + "'"));
  }

  /**
   * Tek tirnak SQL literal'ini bozmadan kacislanir (nedenler sabit olsa da yazim yolu guvenli
   * kalmalidir).
   */
  @Test
  public void singleQuoteInCloseReasonIsEscaped() throws Exception {
    operations.insertCallResultToDB(call(campaign("close_reason"), "limit's reached"));

    String query = capturedUpdate();

    assertTrue(query.contains("close_reason = 'limit''s reached'"));
  }

}
