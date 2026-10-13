package tests.campaign.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;

import com.powerdialer.engine.db.CampaignDbOperations;
import com.powerdialer.engine.db.DBEngine;
import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.util.EnCipherUtils;
import com.powerdialer.util.LoggerDecorator;

/**
 * FIX-330538 — Fetch rezervasyonu ({@code is_fetched}) SELECT ile UPDATE arasında atomik
 * değildi ve UPDATE kaybettiği yarışı fark edemiyordu.
 *
 * <p>Vaka (17.09.2026, KFK_TAHSILAT): iki fetch aynı satırları rezerve etti; ikinci UPDATE ilkinin
 * işaretini sessizce ezdi ve iki Call de arandı. Bu test rezervasyon UPDATE'inin yalnız hâlâ boş
 * ({@code IS NULL}) satırları kapadığını ve kapatılamayan satırların tek bir yeniden-okuma ile
 * saptanıp çağırana bildirildiğini kilitler.</p>
 *
 * <p>Fail-open kuralı: yeniden-okuma bu fetch'in işaretini HİÇBİR satırda doğrulayamazsa batch
 * bugünkü davranışla korunur (hiçbir satır düşürülmez). Aksi hâlde yeniden-okumanın güvenilmez olduğu
 * bir tabloda (kolon tipi/literal dönüşümü/hatalı rowcount) her tick tüm batch düşer, satırlar
 * "DB'de fetched, cache'te yok" kalır ve kampanya durur. Düşürme yalnız yeniden-okuma çalıştığını
 * kanıtladığında (en az bir satırı doğruladığında) yapılır.</p>
 */
public class FetchedFlagReservationGuardRegressionTest {

  private static final String MSSQL_URL = "jdbc:sqlserver://localhost;databaseName=campaign";
  private static final String ORACLE_URL = "jdbc:oracle:thin:@localhost:1521:campaign";
  private static final Pattern FLAG_LITERAL = Pattern.compile("is_fetched = ('?[0-9.]+'?)");

  @Test
  public void reservationUpdateOnlyClaimsRowsThatAreStillUnreserved() throws Exception {
    DBEngine dbEngine = dbEngine();
    when(dbEngine.executeUpdate(anyString(), anyLong(), anyString(), anyString(), anyString())).thenReturn(2);

    Set<String> notReserved = operations(dbEngine).reserveRowsForFetch(sameTableCampaign(MSSQL_URL), calls("310493", "310195"));

    String update = capturedUpdates(dbEngine, 1).get(0);
    assertTrue("rezervasyon sonuç tablosuna yapılmalı: " + update, update.startsWith("UPDATE campaign_list SET is_fetched = '"));
    assertTrue("start/answer reset korunmalı: " + update,
        update.contains(", start_date = NULL, answer_date = NULL"));
    assertTrue("UPDATE yalnız hâlâ rezerve edilmemiş satırları kapamalı: " + update,
        update.endsWith(" WHERE id IN (310493,310195) AND is_fetched IS NULL"));
    assertTrue("tüm satırlar kapandıysa rezerve edilemeyen olmamalı", notReserved.isEmpty());
    verify(dbEngine, never()).executeQuery(anyString(), anyLong(), any(), anyString(), anyString(), anyString());
  }

  @Test
  public void rowsReservedElsewhereAreReportedWithoutOverwritingTheirReservation() throws Exception {
    DBEngine dbEngine = dbEngine();
    when(dbEngine.executeUpdate(anyString(), anyLong(), anyString(), anyString(), anyString())).thenReturn(1);
    when(dbEngine.executeQuery(anyString(), anyLong(), any(), anyString(), anyString(), anyString()))
        .thenReturn(rows("310493"));

    Set<String> notReserved = operations(dbEngine).reserveRowsForFetch(sameTableCampaign(MSSQL_URL), calls("310493", "310195"));

    assertEquals(Collections.singleton("310195"), notReserved);
    String update = capturedUpdates(dbEngine, 1).get(0);
    String reread = capturedQuery(dbEngine);
    assertTrue("yeniden-okuma bu fetch'in işaretini aramalı: " + reread,
        reread.startsWith("SELECT id AS \"PD_KEY\" FROM campaign_list WHERE id IN (310493,310195) AND is_fetched = '"));
    assertEquals("yeniden-okuma UPDATE ile aynı işaret literalini kullanmalı",
        flagLiteral(update), flagLiteral(reread));
  }

  @Test
  public void separateResultTableCountsInsertedRowsAsReserved() throws Exception {
    DBEngine dbEngine = dbEngine();
    when(dbEngine.executeUpdate(anyString(), anyLong(), anyString(), anyString(), anyString())).thenReturn(1, 1);

    Set<String> notReserved = operations(dbEngine).reserveRowsForFetch(separateTableCampaign(), calls("310493", "310195"));

    assertTrue("UPDATE + INSERT toplamı batch'i kapatıyorsa yeniden-okuma gerekmez", notReserved.isEmpty());
    List<String> statements = capturedUpdates(dbEngine, 2);
    assertTrue(statements.get(0).startsWith("UPDATE result_table SET is_fetched = '"));
    assertTrue(statements.get(0).endsWith(" WHERE result_id IN (310493,310195) AND is_fetched IS NULL"));
    assertTrue("INSERT branch değişmemeli: " + statements.get(1), statements.get(1).startsWith("INSERT INTO result_table(is_fetched,result_id)"));
    verify(dbEngine, never()).executeQuery(anyString(), anyLong(), any(), anyString(), anyString(), anyString());
  }

  @Test
  public void oracleUsesUnquotedNumericLiteralInBothStatements() throws Exception {
    DBEngine dbEngine = dbEngine();
    when(dbEngine.executeUpdate(anyString(), anyLong(), anyString(), anyString(), anyString())).thenReturn(1);
    when(dbEngine.executeQuery(anyString(), anyLong(), any(), anyString(), anyString(), anyString()))
        .thenReturn(rows("310493"));

    Set<String> notReserved = operations(dbEngine).reserveRowsForFetch(sameTableCampaign(ORACLE_URL), calls("310493", "310195"));

    assertEquals(Collections.singleton("310195"), notReserved);
    String update = capturedUpdates(dbEngine, 1).get(0);
    String reread = capturedQuery(dbEngine);
    assertTrue("Oracle'da işaret tırnaksız sayısal olmalı: " + update, update.contains("SET is_fetched = 4"));
    assertTrue("yeniden-okuma da tırnaksız olmalı: " + reread, reread.contains("AND is_fetched = 4"));
    assertEquals(flagLiteral(update), flagLiteral(reread));
  }

  /**
   * Fail-open: DB "1 satır kapandı" diyor ama yeniden-okuma bu fetch'in işaretini hiçbir satırda
   * görmüyor. Yeniden-okuma güvenilmez kabul edilir; batch bugünkü davranışla korunur, hiçbir satır
   * düşürülmez ve ek bir UPDATE (geri alma) gönderilmez. Böylece bozuk rowcount/kolon tipi olan bir
   * tabloda kampanya durmaz.
   */
  @Test
  public void rereadConfirmingNoRowFailsOpenAndKeepsBatch() throws Exception {
    DBEngine dbEngine = dbEngine();
    when(dbEngine.executeUpdate(anyString(), anyLong(), anyString(), anyString(), anyString())).thenReturn(1);
    when(dbEngine.executeQuery(anyString(), anyLong(), any(), anyString(), anyString(), anyString()))
        .thenReturn(new JSONArray());

    Set<String> notReserved = operations(dbEngine).reserveRowsForFetch(sameTableCampaign(MSSQL_URL), calls("310493", "310195"));

    assertTrue("hiçbir satır doğrulanamadıysa batch korunmalı (fail-open)", notReserved.isEmpty());
    capturedUpdates(dbEngine, 1);
  }

  /**
   * Aynı fail-open kuralı rowcount 0 ve sonuç null iken de geçerli: hiçbir satır düşürülmez.
   */
  @Test
  public void zeroRowCountWithNullRereadKeepsBatch() throws Exception {
    DBEngine dbEngine = dbEngine();
    when(dbEngine.executeUpdate(anyString(), anyLong(), anyString(), anyString(), anyString())).thenReturn(0);
    when(dbEngine.executeQuery(anyString(), anyLong(), any(), anyString(), anyString(), anyString())).thenReturn(null);

    Set<String> notReserved = operations(dbEngine).reserveRowsForFetch(sameTableCampaign(MSSQL_URL), calls("310493", "310195"));

    assertTrue(notReserved.isEmpty());
    capturedUpdates(dbEngine, 1);
  }

  /**
   * Yeniden-okuma anahtarı farklı biçimde döndürse ("310493.0") eşleşme bozulmamalı; anahtarlar
   * sayısal eşdeğerlikle karşılaştırılır.
   */
  @Test
  public void rereadKeysAreMatchedByNumericEquivalence() throws Exception {
    DBEngine dbEngine = dbEngine();
    when(dbEngine.executeUpdate(anyString(), anyLong(), anyString(), anyString(), anyString())).thenReturn(1);
    when(dbEngine.executeQuery(anyString(), anyLong(), any(), anyString(), anyString(), anyString()))
        .thenReturn(rows("310493.0"));

    Set<String> notReserved = operations(dbEngine).reserveRowsForFetch(sameTableCampaign(MSSQL_URL), calls("310493", "310195"));

    assertEquals(Collections.singleton("310195"), notReserved);
  }

  @Test
  public void emptyBatchIssuesNoStatements() throws Exception {
    DBEngine dbEngine = dbEngine();

    Set<String> notReserved = operations(dbEngine).reserveRowsForFetch(sameTableCampaign(MSSQL_URL), Collections.<Call>emptyList());

    assertTrue(notReserved.isEmpty());
    verify(dbEngine, never()).executeUpdate(anyString(), anyLong(), anyString(), anyString(), anyString());
  }

  private DBEngine dbEngine() {
    return mock(DBEngine.class);
  }

  private CampaignDbOperations operations(DBEngine dbEngine) {
    EnCipherUtils enCipherUtils = mock(EnCipherUtils.class);
    when(enCipherUtils.decryptPassword("encrypted")).thenReturn("plain");
    LoggerDecorator loggerDecorator = mock(LoggerDecorator.class, Answers.RETURNS_DEEP_STUBS);
    return new CampaignDbOperations(dbEngine, loggerDecorator, null, enCipherUtils, null);
  }

  private Campaign sameTableCampaign(String connectionString) {
    Campaign campaign = baseCampaign(connectionString);
    campaign.setDbListTable("campaign_list");
    campaign.setDbResultTable("campaign_list");
    return campaign;
  }

  private Campaign separateTableCampaign() {
    Campaign campaign = baseCampaign(MSSQL_URL);
    campaign.setDbListTable("list_table");
    campaign.setDbResultTable("result_table");
    campaign.setColResultUniqueKey("result_id");
    return campaign;
  }

  private Campaign baseCampaign(String connectionString) {
    Campaign campaign = new Campaign();
    campaign.setId(6L);
    campaign.setDbConnectionString(connectionString);
    campaign.setDbUsername("user");
    campaign.setDbPassword("encrypted");
    campaign.setColUniqueKey("id");
    campaign.setColResultFetchedDataFlag("is_fetched");
    campaign.setColResultStartDate("start_date");
    campaign.setColResultAnswerDate("answer_date");
    return campaign;
  }

  private static List<Call> calls(String... uniqueKeys) {
    List<Call> calls = new java.util.ArrayList<>();
    for (String uniqueKey : uniqueKeys) {
      Call call = new Call();
      call.setUniqueKey(uniqueKey);
      calls.add(call);
    }
    return calls;
  }

  private static JSONArray rows(String... keys) {
    JSONArray rows = new JSONArray();
    for (String key : keys) {
      rows.put(new JSONObject().put("PD_KEY", key));
    }
    return rows;
  }

  private static List<String> capturedUpdates(DBEngine dbEngine, int expectedCount) throws Exception {
    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    verify(dbEngine, times(expectedCount)).executeUpdate(sql.capture(), anyLong(), anyString(), anyString(), anyString());
    return sql.getAllValues();
  }

  private static String capturedQuery(DBEngine dbEngine) throws Exception {
    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    verify(dbEngine, times(1)).executeQuery(sql.capture(), anyLong(), any(), anyString(), anyString(), anyString());
    return sql.getValue();
  }

  private static String flagLiteral(String statement) {
    Matcher matcher = FLAG_LITERAL.matcher(statement);
    assertTrue("işaret literali bulunamadı: " + statement, matcher.find());
    return matcher.group(1);
  }
}
