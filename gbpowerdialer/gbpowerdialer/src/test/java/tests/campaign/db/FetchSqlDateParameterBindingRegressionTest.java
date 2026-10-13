package tests.campaign.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import com.powerdialer.engine.db.CampaignDbOperations;
import com.powerdialer.engine.db.DBEngine;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.Filter;
import com.powerdialer.model.ParametrizedQuery;
import com.powerdialer.util.Constants;
import com.powerdialer.util.EnCipherUtils;
import com.powerdialer.util.LoggerDecorator;

/**
 * FIX-325461 — Retry/callback fetch SQL'lerindeki tarih literalinin parametreye çevrilmesi.
 *
 * <p>Vaka: {@code Utils.convertDateToDouble(new Date())} milisaniye çözünürlüklü bir double
 * üretiyor ve SQL metnine gömülüyordu ({@code src.retry_date <= 46229.42290931713}). Her
 * çalıştırma yeni bir sorgu metni demek olduğu için plan hiç yeniden kullanılamıyor; compat
 * level 150'de varsayılan açık olan row-mode memory grant feedback öğrenemiyor ve plan cache
 * kirleniyor. 28.07.2026 vakasında timeout'ların %76'sı (500 örnekte 252 retry + 129 callback)
 * plan'ı yeniden kullanamayan bu iki aileden geldi.</p>
 */
public class FetchSqlDateParameterBindingRegressionTest {

  private static final String MSSQL_URL = "jdbc:sqlserver://localhost;databaseName=campaign";
  private static final Pattern INLINE_RETRY_DATE = Pattern.compile("retry_date\\s*<=\\s*\\d");
  private static final Pattern INLINE_CALLBACK_DATE = Pattern.compile("callback_date\\s*<=\\s*\\d");

  @Test
  public void retryFetchBindsRetryDateAsParameter() throws Exception {
    DBEngine dbEngine = dbEngine();
    operations(dbEngine).fetchDataForProducerRetries(campaign(), 50, Optional.empty());

    ParametrizedQuery captured = capturedParametrizedQuery(dbEngine);
    assertTrue("retry_date koşulu parametre ile bağlanmalı: " + captured.getQuery(),
        captured.getQuery().contains("src.retry_date <= ?"));
    assertFalse("retry_date koşulunda literal kalmamalı: " + captured.getQuery(),
        INLINE_RETRY_DATE.matcher(captured.getQuery()).find());
    assertEquals("tek parametre beklenir", 1, captured.getParameters().size());
    assertTrue("parametre Double olmalı", captured.getParameters().get(0) instanceof Double);
    assertTrue("parametre bugünün Excel-serial değeri olmalı",
        (Double) captured.getParameters().get(0) > 40000d);
  }

  @Test
  public void callbackFetchBindsCallbackDateAsParameter() throws Exception {
    DBEngine dbEngine = dbEngine();
    operations(dbEngine).fetchDataForProducerCallbacks(campaign(), 30, null, Optional.empty());

    ParametrizedQuery captured = capturedParametrizedQuery(dbEngine);
    assertTrue("callback_date koşulu parametre ile bağlanmalı: " + captured.getQuery(),
        captured.getQuery().contains("src.callback_date <= ?"));
    assertFalse("callback_date koşulunda literal kalmamalı: " + captured.getQuery(),
        INLINE_CALLBACK_DATE.matcher(captured.getQuery()).find());
    assertEquals("tek parametre beklenir", 1, captured.getParameters().size());
    assertTrue("parametre Double olmalı", captured.getParameters().get(0) instanceof Double);
  }

  /**
   * Aynı kampanya için sorgu metni çalıştırmalar arasında sabit kalmalı — plan reuse'un
   * ölçülebilir koşulu bu. (Tarih parametreye taşındığı için metin artık değişmiyor.)
   */
  @Test
  public void retryFetchQueryTextIsStableAcrossExecutions() throws Exception {
    Campaign campaign = campaign();
    DBEngine dbEngine = dbEngine();
    CampaignDbOperations operations = operations(dbEngine);

    operations.fetchDataForProducerRetries(campaign, 50, Optional.empty());
    Thread.sleep(5L);
    operations.fetchDataForProducerRetries(campaign, 50, Optional.empty());

    ArgumentCaptor<ParametrizedQuery> captor = ArgumentCaptor.forClass(ParametrizedQuery.class);
    verify(dbEngine, org.mockito.Mockito.times(2))
        .executeParametrizedQuery(captor.capture(), anyLong(), any(), anyString(), anyString(), anyString());
    List<ParametrizedQuery> values = captor.getAllValues();
    assertEquals("iki çalıştırmanın SQL metni aynı olmalı",
        values.get(0).getQuery(), values.get(1).getQuery());
  }

  /**
   * FREE filter metni kullanıcı tarafından yazılıyor; içindeki {@code ?} işaretinin parametre
   * sayısını kaydırmadığı doğrulanır (tırnak içindeki soru işareti sürücü tarafından parametre
   * sayılmaz; burada üretilen parametre listesinin şişmediği kilitlenir).
   */
  @Test
  public void freeFilterQuestionMarkDoesNotAddParameters() throws Exception {
    Campaign campaign = campaign();
    Filter freeFilter = new Filter();
    freeFilter.setEnabled(true);
    freeFilter.setSegmentation(false);
    freeFilter.setType(Constants.FILTER_TYPE.FREE);
    freeFilter.setValue("Aciklama LIKE '%?%'");
    campaign.setFilters(Arrays.asList(freeFilter));
    campaign.setFilterJoinType(Constants.FILTER_JOIN_TYPE.AND);
    campaign.setApplyFilterTo(Constants.APPLY_TO.RETRIES_AND_CALLBACKS);

    DBEngine dbEngine = dbEngine();
    operations(dbEngine).fetchDataForProducerRetries(campaign, 50, Optional.empty());

    ParametrizedQuery captured = capturedParametrizedQuery(dbEngine);
    assertTrue("free filter sorguya girmeli: " + captured.getQuery(),
        captured.getQuery().contains("Aciklama LIKE '%?%'"));
    assertEquals("free filter parametre eklememeli", 1, captured.getParameters().size());
  }

  private DBEngine dbEngine() throws Exception {
    DBEngine dbEngine = mock(DBEngine.class);
    when(dbEngine.executeQuery(anyString(), anyLong(), any(), anyString(), anyString(), anyString()))
        .thenReturn(new JSONArray());
    when(dbEngine.executeParametrizedQuery(any(ParametrizedQuery.class), anyLong(), any(), anyString(), anyString(), anyString()))
        .thenReturn(new JSONArray());
    return dbEngine;
  }

  private CampaignDbOperations operations(DBEngine dbEngine) throws Exception {
    EnCipherUtils enCipherUtils = mock(EnCipherUtils.class);
    when(enCipherUtils.decryptPassword("encrypted")).thenReturn("plain");
    return new CampaignDbOperations(dbEngine, new LoggerDecorator(), null, enCipherUtils, null);
  }

  private Campaign campaign() {
    Campaign campaign = new Campaign();
    campaign.setId(160L);
    campaign.setDbConnectionString(MSSQL_URL);
    campaign.setDbUsername("user");
    campaign.setDbPassword("encrypted");
    campaign.setDbListTable("campaign_list");
    campaign.setDbResultTable("campaign_list");
    campaign.setColUniqueKey("id");
    campaign.setColResultFetchedDataFlag("is_fetched");
    campaign.setColResultCompleteDate("complete_date");
    campaign.setColResultRetryDate("retry_date");
    campaign.setColResultCallbackDate("callback_date");
    campaign.setColResultAttempts("ATTEMPTS");
    campaign.setTimezoneType(Constants.TIMEZONE_TYPE.OFF);
    campaign.setSkillEnabled(false);
    campaign.setAllowCallback(true);
    campaign.setRetryScheduleEnabled(true);
    return campaign;
  }

  private ParametrizedQuery capturedParametrizedQuery(DBEngine dbEngine) throws Exception {
    ArgumentCaptor<ParametrizedQuery> captor = ArgumentCaptor.forClass(ParametrizedQuery.class);
    verify(dbEngine).executeParametrizedQuery(captor.capture(), anyLong(), any(), anyString(), anyString(), anyString());
    return captor.getValue();
  }
}
