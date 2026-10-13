package tests.dnc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;

import org.json.JSONArray;
import org.junit.Before;
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
 * Cross-DB güvenliği: kampanya data tabloları PD DB'sinden farklı sunucuda
 * olabilir; delta-sync'in kampanya DB'sine ürettiği hiçbir SQL pd_dnc_pn'i
 * referans alamaz (JOIN yok — adaylar PD tarafında ayrıca kontrol edilir).
 * Ayrıca uniqueKey bazlı toplu işaretleme UPDATE'inin alan semantiği
 * kilitlenir.
 */
public class DncDeltaSyncCrossDbNoJoinContractTest {

  private DBEngine dbEngine;
  private CampaignDbOperations operations;

  @Before
  public void init() {
    dbEngine = mock(DBEngine.class);
    EnCipherUtils enCipherUtils = mock(EnCipherUtils.class);
    when(enCipherUtils.decryptPassword("encrypted")).thenReturn("plain");
    operations = new CampaignDbOperations(dbEngine, new LoggerDecorator(), null, enCipherUtils, null);
  }

  @Test
  public void deltaSyncCampaignQueriesNeverReferencePdDncPn() throws Exception {
    when(dbEngine.executeParametrizedQuery(any(ParametrizedQuery.class), anyLong(), any(), anyString(), anyString(), eq("plain")))
        .thenReturn(new JSONArray());

    operations.findMaxDateEnteredAfter(campaign(), "2026-06-01 00:00:00.0");
    operations.fetchRowsEnteredAfter(campaign(), "2026-06-01 00:00:00.0", "2026-06-03 01:00:00.0", "41", 500);

    ArgumentCaptor<ParametrizedQuery> captor = ArgumentCaptor.forClass(ParametrizedQuery.class);
    verify(dbEngine, org.mockito.Mockito.times(2))
        .executeParametrizedQuery(captor.capture(), anyLong(), any(), anyString(), anyString(), eq("plain"));

    for (ParametrizedQuery query : captor.getAllValues()) {
      assertFalse("Kampanya DB sorgusu pd_dnc_pn referansı içeremez (cross-DB JOIN yasak): " + query.getQuery(),
          query.getQuery().toLowerCase().contains("pd_dnc_pn"));
      assertTrue("Kampanya DB sorgusu kampanya tablosunu hedeflemeli: " + query.getQuery(),
          query.getQuery().contains("list_table"));
    }
  }

  @Test
  public void fetchRowsEnteredAfterUsesBoundedWindowWithKeysetPagination() throws Exception {
    when(dbEngine.executeParametrizedQuery(any(ParametrizedQuery.class), anyLong(), any(), anyString(), anyString(), eq("plain")))
        .thenReturn(new JSONArray());

    operations.fetchRowsEnteredAfter(campaign(), "2026-06-01 00:00:00.0", "2026-06-03 01:00:00.0", "41", 500);

    ArgumentCaptor<ParametrizedQuery> captor = ArgumentCaptor.forClass(ParametrizedQuery.class);
    verify(dbEngine).executeParametrizedQuery(captor.capture(), anyLong(), eq(Integer.valueOf(500)), anyString(), anyString(), eq("plain"));

    String query = captor.getValue().getQuery();
    assertTrue("Pencere alt sınırı parametrized olmalı", query.contains("date_entered > ?"));
    assertTrue("Pencere üst sınırı sabitlenmeli", query.contains("date_entered <= ?"));
    assertTrue("Keyset sayfalama unique key üzerinden olmalı", query.contains("id > ?"));
    assertTrue("Deterministik sıralama olmalı", query.contains("ORDER BY"));
    assertEquals(Arrays.asList("2026-06-01 00:00:00.0", "2026-06-03 01:00:00.0", "41"),
        captor.getValue().getParameters());
  }

  @Test
  public void setFilteredByUniqueKeysCarriesDncFilteredFieldSemantics() throws Exception {
    operations.setFilteredByDncListStatusByUniqueKeys(campaign(), Arrays.asList("41", "42"));

    ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
    verify(dbEngine).executeUpdate(queryCaptor.capture(), eq(99L), anyString(), anyString(), eq("plain"));

    String query = queryCaptor.getValue();
    assertTrue(query.startsWith("UPDATE result_table SET "));
    assertTrue(query.contains("complete_date = '"));
    assertTrue(query.contains("telephony_result_code = " + Constants.TELEPHONY_RC_ID_FILTERED_BY_DNC));
    assertTrue(query.contains("is_disabled = " + Constants.DISABLED));
    assertTrue(query.contains("result_id IN (41,42)"));
    assertTrue("Yalnız açık satırlar işaretlenmeli", query.contains("complete_date IS NULL"));
  }

  private Campaign campaign() {
    Campaign campaign = new Campaign();
    campaign.setId(99L);
    campaign.setDbConnectionString("jdbc:sqlserver://localhost;databaseName=campaign");
    campaign.setDbUsername("user");
    campaign.setDbPassword("encrypted");
    campaign.setDbListTable("list_table");
    campaign.setDbResultTable("result_table");
    campaign.setColUniqueKey("id");
    campaign.setColResultUniqueKey("result_id");
    campaign.setColResultCompleteDate("complete_date");
    campaign.setColResultTelephonyRc("telephony_result_code");
    campaign.setColResultFetchedDataFlag("is_fetched");
    campaign.setColResultUserId("user_id");
    campaign.setColResultUserRc("user_result_code");
    campaign.setColResultCustomerNo("customer_no");
    campaign.setColDateEntered("date_entered");
    return campaign;
  }
}
