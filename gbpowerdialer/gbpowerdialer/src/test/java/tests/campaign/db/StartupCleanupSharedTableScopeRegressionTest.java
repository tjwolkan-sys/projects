package tests.campaign.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Date;

import org.junit.Test;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;

import com.powerdialer.engine.db.CampaignDbOperations;
import com.powerdialer.engine.db.DBEngine;
import com.powerdialer.model.Campaign;
import com.powerdialer.util.EnCipherUtils;
import com.powerdialer.util.LoggerDecorator;
import com.powerdialer.util.Utils;

/**
 * FIX-333599 — Startup DB check cleanup'ı ({@code removeFetchedDataFlagFromUnfinishedCalls})
 * kampanya kapsamı taşımadığı için aynı result tablosunu kullanan, o anda canlı başka bir
 * kampanyanın rezervasyonlarını da siliyordu.
 *
 * <p>Vaka (02.10.2026, nonturkcell): campaign 6 ve 17 aynı {@code KFK_TAHSILAT} tablosunu kullanıyor
 * ve 09:00'da aynı saniyede start ediliyor. 09:00:16.583'te 17'nin cleanup'ı çalıştı; campaign 6'nın
 * 09:00:16.8'deki ilk rezervasyonları ({@code is_fetched = now, start_date = NULL}) cleanup koşuluna
 * uyduğu için silindi ve 10 sn sonra aynı satırlar, çağrıları sürerken yeniden çekildi.</p>
 *
 * <p>Beklenen: kesme anı verilirse cleanup yalnız o andan ÖNCE yapılmış rezervasyonları temizler;
 * kesme yoksa SQL bugünküyle birebir aynıdır.</p>
 */
public class StartupCleanupSharedTableScopeRegressionTest {

  private static final String MSSQL_URL = "jdbc:sqlserver://localhost;databaseName=campaign";
  private static final String ORACLE_URL = "jdbc:oracle:thin:@localhost:1521:campaign";
  private static final String LEGACY_SQL = "UPDATE campaign_list SET is_fetched = NULL WHERE complete_date IS NULL"
      + " AND is_fetched IS NOT NULL AND (start_date IS NULL OR start_date < retry_date OR start_date < callback_date)";

  /** Karakterizasyon: mevcut tek-argümanlı çağrı bugünkü SQL'i birebir üretir. */
  @Test
  public void legacyCleanupSqlIsUnchanged() throws Exception {
    DBEngine dbEngine = mock(DBEngine.class);

    operations(dbEngine).removeFetchedDataFlagFromUnfinishedCalls(campaign(MSSQL_URL));

    assertEquals(LEGACY_SQL, capturedUpdate(dbEngine));
  }

  /** Kesme verilmezse (canlı kardeş kampanya yok) davranış birebir bugünkü gibidir. */
  @Test
  public void cleanupWithoutCutoffKeepsLegacySql() throws Exception {
    DBEngine dbEngine = mock(DBEngine.class);

    operations(dbEngine).removeFetchedDataFlagFromUnfinishedCalls(campaign(MSSQL_URL), null);

    assertEquals(LEGACY_SQL, capturedUpdate(dbEngine));
  }

  /**
   * Vakanın karşılığı: kardeş kampanya canlıya geçtikten sonra yaptığı rezervasyonlar
   * ({@code is_fetched >= kesme}) korunur; yalnız daha eski (bayat) rezervasyonlar temizlenir.
   */
  @Test
  public void cleanupWithCutoffOnlyReleasesReservationsOlderThanCutoff() throws Exception {
    DBEngine dbEngine = mock(DBEngine.class);
    Date cutoff = new Date(1790920816493L);

    operations(dbEngine).removeFetchedDataFlagFromUnfinishedCalls(campaign(MSSQL_URL), cutoff);

    assertEquals(LEGACY_SQL + " AND is_fetched < '" + Utils.convertDateToDouble(cutoff) + "'", capturedUpdate(dbEngine));
  }

  /** Rezervasyonla aynı literal kuralı: Oracle'da tırnaksız sayısal. */
  @Test
  public void oracleCutoffUsesUnquotedNumericLiteral() throws Exception {
    DBEngine dbEngine = mock(DBEngine.class);
    Date cutoff = new Date(1790920816493L);

    operations(dbEngine).removeFetchedDataFlagFromUnfinishedCalls(campaign(ORACLE_URL), cutoff);

    String sql = capturedUpdate(dbEngine);
    assertTrue(sql, sql.endsWith(" AND is_fetched < " + Utils.convertDateToDouble(cutoff)));
  }

  private CampaignDbOperations operations(DBEngine dbEngine) {
    EnCipherUtils enCipherUtils = mock(EnCipherUtils.class);
    when(enCipherUtils.decryptPassword("encrypted")).thenReturn("plain");
    LoggerDecorator loggerDecorator = mock(LoggerDecorator.class, Answers.RETURNS_DEEP_STUBS);
    return new CampaignDbOperations(dbEngine, loggerDecorator, null, enCipherUtils, null);
  }

  private Campaign campaign(String connectionString) {
    Campaign campaign = new Campaign();
    campaign.setId(17L);
    campaign.setDbConnectionString(connectionString);
    campaign.setDbUsername("user");
    campaign.setDbPassword("encrypted");
    campaign.setColUniqueKey("id");
    campaign.setDbListTable("campaign_list");
    campaign.setDbResultTable("campaign_list");
    campaign.setColResultFetchedDataFlag("is_fetched");
    campaign.setColResultCompleteDate("complete_date");
    campaign.setColResultStartDate("start_date");
    campaign.setColResultRetryDate("retry_date");
    campaign.setColResultCallbackDate("callback_date");
    return campaign;
  }

  private static String capturedUpdate(DBEngine dbEngine) throws Exception {
    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    verify(dbEngine, times(1)).executeUpdate(sql.capture(), anyLong(), anyString(), anyString(), anyString());
    return sql.getValue();
  }
}
