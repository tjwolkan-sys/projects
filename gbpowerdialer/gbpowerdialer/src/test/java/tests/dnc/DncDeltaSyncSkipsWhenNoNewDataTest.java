package tests.dnc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import com.powerdialer.batch.scheduler.DncDeltaSyncScheduler;
import com.powerdialer.engine.CustomerDncService;
import com.powerdialer.engine.DNCEngine;
import com.powerdialer.engine.db.CampaignDbOperations;
import com.powerdialer.engine.db.DBEngine;
import com.powerdialer.model.Campaign;
import com.powerdialer.service.CampaignService;
import com.powerdialer.service.DNCCustomerPhoneService;
import com.powerdialer.service.DNCPhoneNumberService;
import com.powerdialer.service.DNCService;

/**
 * Kriter 3 + Edge Case 3: delta-sync, watermark'tan yeni date_entered değeri
 * yoksa ağır tarama yapmadan ucuzca pas geçer; karşılaştırma GETDATE() ile
 * değil son başarılı senkronun MAX(date_entered) watermark'ıyla yapılır ve
 * skip durumunda watermark değişmez.
 */
@RunWith(MockitoJUnitRunner.class)
public class DncDeltaSyncSkipsWhenNoNewDataTest {

  private static final long CAMPAIGN_ID = 318332L;
  private static final String LAST_WATERMARK = "2026-06-01 00:00:00.0";

  @InjectMocks
  private DncDeltaSyncScheduler scheduler;

  @Mock
  private DBEngine dbEngine;

  @Mock
  private DNCService dncService;

  @Mock
  private DNCPhoneNumberService dncPhoneNumberService;

  @Mock
  private DNCCustomerPhoneService dncCustomerPhoneService;

  @Mock
  private CustomerDncService customerDncService;

  @Mock
  private CampaignDbOperations campaignDbOperations;

  @Mock
  private DNCEngine dncEngine;

  @Mock
  private CampaignService campaignService;

  private Campaign campaign;

  @Before
  public void init() throws Exception {
    ReflectionTestUtils.setField(scheduler, "batchSize", 500);

    campaign = new Campaign();
    campaign.setId(CAMPAIGN_ID);
    campaign.setColDateEntered("date_entered");
    campaign.setDncEnabled(true);

    JSONArray eligible = new JSONArray().put(new JSONObject().put("id", CAMPAIGN_ID));
    when(dbEngine.executeQuery(contains("pd_campaign"), isNull(), isNull(), any(), any(), any()))
        .thenReturn(eligible);
    JSONArray watermarkRow = new JSONArray().put(new JSONObject().put("watermark", LAST_WATERMARK));
    when(dbEngine.executeQuery(contains("pd_dnc_sync_log"), isNull(), isNull(), any(), any(), any()))
        .thenReturn(watermarkRow);

    when(campaignService.getCampaignWithPhoneNumberColumnsByNameOrId(String.valueOf(CAMPAIGN_ID))).thenReturn(campaign);
    // Skip akışında DNC listeleri hiç çözülmez (ucuz skip) — dncService
    // stub'larına gerek yoktur; çağrılmadıkları ayrıca verify edilir.
  }

  @Test
  public void skipsHeavyScanWhenNoRowsNewerThanWatermark() throws Exception {
    when(campaignDbOperations.findMaxDateEnteredAfter(campaign, LAST_WATERMARK)).thenReturn(null);

    scheduler.runDeltaSync();

    verify(campaignDbOperations).findMaxDateEnteredAfter(campaign, LAST_WATERMARK);
    verify(campaignDbOperations, never()).fetchRowsEnteredAfter(any(), anyString(), anyString(), any(), anyInt());
    verify(campaignDbOperations, never()).setFilteredByDncListStatusByUniqueKeys(any(), any());
    verify(customerDncService, never()).blockByCustomerNo(anyString(), any(), any());
    verify(dncEngine, never()).dropQueuedCallsForNumbers(any(), any());
    verify(dncService, never()).getDncForCampaign(any());
    // Skip durumunda watermark kaydı yazılmaz (pd_dnc_sync_log INSERT yok).
    verify(dbEngine, never()).executeBatchParametrizedUpdate(any(com.powerdialer.model.ParametrizedQuery.class),
        isNull(), any(), any(), any());
  }

  @Test
  public void firstRunWithoutWatermarkProcessesAllRowsAsNew() throws Exception {
    when(dbEngine.executeQuery(contains("pd_dnc_sync_log"), isNull(), isNull(), any(), any(), any()))
        .thenReturn(new JSONArray());
    when(campaignDbOperations.findMaxDateEnteredAfter(campaign, null)).thenReturn(null);

    scheduler.runDeltaSync();

    verify(campaignDbOperations).findMaxDateEnteredAfter(eq(campaign), isNull());
  }
}
