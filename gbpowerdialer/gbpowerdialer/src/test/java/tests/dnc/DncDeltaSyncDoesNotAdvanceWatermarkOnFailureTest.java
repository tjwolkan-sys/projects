package tests.dnc;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import com.powerdialer.batch.scheduler.DncDeltaSyncScheduler;
import com.powerdialer.engine.CustomerDncService;
import com.powerdialer.engine.DNCEngine;
import com.powerdialer.engine.db.CampaignDbOperations;
import com.powerdialer.engine.db.DBEngine;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.DNC;
import com.powerdialer.model.ParametrizedQuery;
import com.powerdialer.model.PhoneNumberColumn;
import com.powerdialer.service.CampaignService;
import com.powerdialer.service.DNCCustomerPhoneService;
import com.powerdialer.service.DNCPhoneNumberService;
import com.powerdialer.service.DNCService;

/**
 * Retry güvenliği: pencere işlenirken hata oluşursa pd_dnc_sync_log'a FAILED
 * yazılır ve watermark İLERLEMEZ (max_date_entered NULL) — sonraki koşu aynı
 * pencereyi yeniden dener.
 */
@RunWith(MockitoJUnitRunner.class)
public class DncDeltaSyncDoesNotAdvanceWatermarkOnFailureTest {

  private static final long CAMPAIGN_ID = 318332L;
  private static final String LAST_WATERMARK = "2026-06-01 00:00:00.0";
  private static final String NEW_MAX = "2026-06-03 01:00:00.0";

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
    campaign.setColUniqueKey("id");
    campaign.setColDateEntered("date_entered");
    campaign.setColResultCustomerNo("customer_no");
    campaign.setDncEnabled(true);
    PhoneNumberColumn gsm = new PhoneNumberColumn();
    gsm.setColNumber("gsm_1");
    gsm.setColSort(0);
    gsm.setEnabled(true);
    gsm.setDeleted(false);
    campaign.getPhoneNumberColumns().add(gsm);

    DNC defaultDnc = new DNC();
    defaultDnc.setId(7L);

    when(dbEngine.executeQuery(contains("pd_campaign"), isNull(), isNull(), any(), any(), any()))
        .thenReturn(new JSONArray().put(new JSONObject().put("id", CAMPAIGN_ID)));
    when(dbEngine.executeQuery(contains("pd_dnc_sync_log"), isNull(), isNull(), any(), any(), any()))
        .thenReturn(new JSONArray().put(new JSONObject().put("watermark", LAST_WATERMARK)));

    when(campaignService.getCampaignWithPhoneNumberColumnsByNameOrId(String.valueOf(CAMPAIGN_ID))).thenReturn(campaign);
    when(dncService.getDefaultDNCByCampaignId(CAMPAIGN_ID)).thenReturn(defaultDnc);
    when(dncService.getDncForCampaign(CAMPAIGN_ID)).thenReturn(Collections.singletonList(defaultDnc));
    when(dncService.getGlobalDnc(true)).thenReturn(Collections.emptyList());

    when(campaignDbOperations.findMaxDateEnteredAfter(campaign, LAST_WATERMARK)).thenReturn(NEW_MAX);

    JSONArray page = new JSONArray();
    page.put(new JSONObject().put("id", 41).put("gsm_1", "05321110001").put("date_entered", NEW_MAX));
    when(campaignDbOperations.fetchRowsEnteredAfter(campaign, LAST_WATERMARK, NEW_MAX, null, 500)).thenReturn(page);
    when(dncPhoneNumberService.findActivePhoneNumbersByDncIdsAndPhoneNumbers(any(), any(), any()))
        .thenReturn(new HashSet<>(Collections.singletonList("05321110001")));
    Mockito.lenient().when(dncCustomerPhoneService.findActiveCustomerNosByDncIdsAndCustomerNos(any(), any(), any()))
        .thenReturn(Collections.emptySet());
  }

  @Test
  public void failureWritesFailedLogWithoutAdvancingWatermark() throws Exception {
    doThrow(new SQLException("update failed"))
        .when(campaignDbOperations).setFilteredByDncListStatusByUniqueKeys(any(), any());

    scheduler.runDeltaSync();

    ArgumentCaptor<ParametrizedQuery> logCaptor = ArgumentCaptor.forClass(ParametrizedQuery.class);
    org.mockito.Mockito.verify(dbEngine, atLeastOnce())
        .executeBatchParametrizedUpdate(logCaptor.capture(), isNull(), any(), any(), any());
    ParametrizedQuery logInsert = logCaptor.getValue();
    assertTrue(logInsert.getQuery().contains("pd_dnc_sync_log"));
    List<Object> params = logInsert.getParameters();
    assertTrue("FAILED statüsü yazılmalı", params.contains("FAILED"));
    assertFalse("Watermark hatada İLERLEMEMELİ (NEW_MAX yazılmamalı)", params.contains(NEW_MAX));
  }
}
