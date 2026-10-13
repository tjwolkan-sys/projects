package tests.campaign.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.json.JSONArray;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import com.powerdialer.engine.db.CampaignDbOperations;
import com.powerdialer.engine.db.DBEngine;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.ParametrizedQuery;
import com.powerdialer.util.Constants;
import com.powerdialer.util.EnCipherUtils;
import com.powerdialer.util.LoggerDecorator;

/**
 * FIX-325461 — Producer fetch SQL'lerinde satır limitinin sorgu metnine taşınması.
 *
 * <p>Vaka: limit yalnız {@code PreparedStatement.setMaxRows} ile veriliyordu. Sürücü bunu
 * sunucuda {@code SET ROWCOUNT} olarak uygular ve optimizer satır tahmininde/memory grant
 * hesabında kullanmaz. {@code ORDER BY} blocking operatör olduğu için sıralama tüm eşleşen
 * küme için boyutlanır; grant kuyruğa girer ({@code RESOURCE_SEMAPHORE}) ve sorgu
 * {@code DBEngine}'in 10 saniyelik timeout'una takılır. Ölçüm (28.07.2026, canlı campaign DB):
 * ORDER BY'lı retry fetch 52.014 ms / 15 ms CPU, aynı sorgu ORDER BY'sız 45 ms.</p>
 *
 * <p>Bu test satır limitinin sorgu metninde taşındığını (MS SQL: {@code TOP (n)}) ve
 * {@code n <= 0} durumunda bugünkü limitsiz davranışın korunduğunu kilitler.</p>
 */
public class FetchSqlRowLimitRegressionTest {

  private static final String MSSQL_URL = "jdbc:sqlserver://localhost;databaseName=campaign";
  private static final String ORACLE_URL = "jdbc:oracle:thin:@localhost:1521:campaign";

  @Test
  public void freshFetchCarriesRowLimitInsideQueryForMsSql() throws Exception {
    DBEngine dbEngine = dbEngine();
    operations(dbEngine).fetchDataForProducerFresh(campaign(MSSQL_URL), 100, Optional.empty());

    String query = capturedQuery(dbEngine);
    assertTrue("fresh fetch satır limitini sorguda taşımalı: " + query,
        query.startsWith("SELECT TOP (100) * FROM "));
  }

  @Test
  public void retryFetchCarriesRowLimitInsideQueryForMsSql() throws Exception {
    DBEngine dbEngine = dbEngine();
    operations(dbEngine).fetchDataForProducerRetries(campaign(MSSQL_URL), 50, Optional.empty());

    String query = capturedQuery(dbEngine);
    assertTrue("retry fetch satır limitini sorguda taşımalı: " + query,
        query.startsWith("SELECT TOP (50) * FROM "));
  }

  @Test
  public void callbackFetchCarriesRowLimitInsideQueryForMsSql() throws Exception {
    DBEngine dbEngine = dbEngine();
    operations(dbEngine).fetchDataForProducerCallbacks(campaign(MSSQL_URL), 30, null, Optional.empty());

    String query = capturedQuery(dbEngine);
    assertTrue("callback fetch satır limitini sorguda taşımalı: " + query,
        query.startsWith("SELECT TOP (30) * FROM "));
  }

  /**
   * Karakterizasyon: JDBC'de {@code setMaxRows(0)} "limitsiz" demektir ve
   * {@code CallProducer.fetchCallsWithoutSegmentation} retry çağrısına 0 gönderebiliyor
   * ({@code retryRatio * remain / 100}). {@code TOP (0)} yazmak "hiç satır" anlamına gelip
   * davranışı değiştirirdi; bu yüzden limit ifadesi üretilmez (FIX-325461 Açık Soru 2).
   */
  @Test
  public void nonPositiveRowLimitKeepsCurrentUnlimitedBehaviour() throws Exception {
    DBEngine zeroLimit = dbEngine();
    operations(zeroLimit).fetchDataForProducerRetries(campaign(MSSQL_URL), 0, Optional.empty());
    assertFalse("limit 0 iken TOP üretilmemeli: " + capturedQuery(zeroLimit),
        capturedQuery(zeroLimit).contains("TOP ("));

    DBEngine negativeLimit = dbEngine();
    operations(negativeLimit).fetchDataForProducerRetries(campaign(MSSQL_URL), -5, Optional.empty());
    assertFalse("limit negatif iken TOP üretilmemeli: " + capturedQuery(negativeLimit),
        capturedQuery(negativeLimit).contains("TOP ("));

    DBEngine nullLimit = dbEngine();
    operations(nullLimit).fetchDataForProducerRetries(campaign(MSSQL_URL), null, Optional.empty());
    assertFalse("limit null iken TOP üretilmemeli: " + capturedQuery(nullLimit),
        capturedQuery(nullLimit).contains("TOP ("));
  }

  /**
   * FIX-325461 kapsamı MS SQL'dir (kullanıcı kararı 28.07.2026). Diğer DB tipleri bugünkü
   * davranışta bırakılır. Oracle'a taşıma yapıldığında bu test bilinçli olarak güncellenir:
   * Oracle 12c+ için limit ORDER BY'dan SONRA {@code FETCH FIRST n ROWS ONLY} olarak eklenir.
   */
  @Test
  public void rowLimitIsNotEmittedForNonMsSqlDatabases() throws Exception {
    DBEngine dbEngine = dbEngine();
    operations(dbEngine).fetchDataForProducerFresh(campaign(ORACLE_URL), 100, Optional.empty());

    String query = capturedQuery(dbEngine);
    assertFalse("MS SQL dışında TOP üretilmemeli: " + query, query.contains("TOP ("));
    assertFalse("MS SQL dışında FETCH FIRST üretilmemeli: " + query, query.contains("FETCH FIRST"));
    assertFalse("MS SQL dışında LIMIT üretilmemeli: " + query, query.contains(" LIMIT "));
  }

  /**
   * Karakterizasyon + regresyon: limit eklenmesi WHERE koşullarını ve ORDER BY'ı bozmamalı.
   * Asıl kazanç ORDER BY ile birlikte gelir (Sort → Top N Sort).
   */
  @Test
  public void rowLimitCoexistsWithOrderByAndExistingPredicates() throws Exception {
    Campaign campaign = campaign(MSSQL_URL);
    campaign.setColSortBy("ATTEMPTS");
    campaign.setDbListSortDirection(Constants.DB_SORT_DIRECTION.ASC);

    DBEngine dbEngine = dbEngine();
    operations(dbEngine).fetchDataForProducerFresh(campaign, 100, Optional.empty());

    String query = capturedQuery(dbEngine);
    assertTrue(query.startsWith("SELECT TOP (100) * FROM campaign_list src "));
    assertTrue(query.contains("src.is_fetched IS NULL"));
    assertTrue(query.contains("AND src.complete_date IS NULL"));
    assertTrue(query.contains("AND src.retry_date IS NULL"));
    assertTrue(query.contains("AND src.callback_date IS NULL"));
    assertTrue("ORDER BY korunmalı: " + query, query.contains(" ORDER BY src.ATTEMPTS ASC"));
    assertEquals("TOP tek kez üretilmeli", query.indexOf("TOP ("), query.lastIndexOf("TOP ("));
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

  private Campaign campaign(String connectionString) {
    Campaign campaign = new Campaign();
    campaign.setId(160L);
    campaign.setDbConnectionString(connectionString);
    campaign.setDbUsername("user");
    campaign.setDbPassword("encrypted");
    // Vakadaki kampanya tek tablo kullanıyor (list = result) → result alias'ı da "src.".
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

  /**
   * Üretilen SQL'i yakalar. Retry/callback fetch'leri FIX-325461 ile parametreli yola
   * ({@code executeParametrizedQuery}) taşındığı için her iki çağrı yolu da desteklenir.
   */
  private String capturedQuery(DBEngine dbEngine) throws Exception {
    ArgumentCaptor<String> plainQuery = ArgumentCaptor.forClass(String.class);
    verify(dbEngine, atLeast(0)).executeQuery(plainQuery.capture(), anyLong(), any(), anyString(), anyString(), anyString());
    List<String> plainValues = plainQuery.getAllValues();
    if (!plainValues.isEmpty()) {
      return plainValues.get(0);
    }

    ArgumentCaptor<ParametrizedQuery> parametrized = ArgumentCaptor.forClass(ParametrizedQuery.class);
    verify(dbEngine, atLeast(0)).executeParametrizedQuery(parametrized.capture(), anyLong(), any(), anyString(), anyString(), anyString());
    List<ParametrizedQuery> parametrizedValues = parametrized.getAllValues();
    assertFalse("DBEngine'e hiç sorgu gitmedi", parametrizedValues.isEmpty());
    return parametrizedValues.get(0).getQuery();
  }
}
