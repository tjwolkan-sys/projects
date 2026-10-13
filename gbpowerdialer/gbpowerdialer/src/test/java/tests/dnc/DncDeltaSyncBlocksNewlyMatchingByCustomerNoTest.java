package tests.dnc;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
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
import com.powerdialer.model.DNC;
import com.powerdialer.model.PhoneNumberColumn;
import com.powerdialer.service.CampaignService;
import com.powerdialer.service.DNCCustomerPhoneService;
import com.powerdialer.service.DNCPhoneNumberService;
import com.powerdialer.service.DNCService;

/**
 * Delta-sync: yeni yüklenen satırların customer_no'su aktif CUSTOMER marker
 * ile eşleşiyorsa satırlar aranamaz işaretlenir, müşterinin telefonları
 * blockByCustomerNo ile PHONE kayıtlarına genişletilir ve dönen union set
 * canlı kuyruk hot-drop'una verilir.
 */
@RunWith(MockitoJUnitRunner.class)
public class DncDeltaSyncBlocksNewlyMatchingByCustomerNoTest {

  private static final long CAMPAIGN_ID = 318332L;
  private static final long DNC_ID = 7L;
  private static final String LAST_WATERMARK = "2026-06-01 00:00:00.0";
  private static final String NEW_MAX = "2026-06-03 01:00:00.0";
  private static final String CUSTOMER_NO = "CUST-999";

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
  private DNC defaultDnc;

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

    defaultDnc = new DNC();
    defaultDnc.setId(DNC_ID);

    when(dbEngine.executeQuery(contains("pd_campaign"), isNull(), isNull(), any(), any(), any()))
        .thenReturn(new JSONArray().put(new JSONObject().put("id", CAMPAIGN_ID)));
    when(dbEngine.executeQuery(contains("pd_dnc_sync_log"), isNull(), isNull(), any(), any(), any()))
        .thenReturn(new JSONArray().put(new JSONObject().put("watermark", LAST_WATERMARK)));

    when(campaignService.getCampaignWithPhoneNumberColumnsByNameOrId(String.valueOf(CAMPAIGN_ID))).thenReturn(campaign);
    when(dncService.getDefaultDNCByCampaignId(CAMPAIGN_ID)).thenReturn(defaultDnc);
    when(dncService.getDncForCampaign(CAMPAIGN_ID)).thenReturn(Collections.singletonList(defaultDnc));
    when(dncService.getGlobalDnc(true)).thenReturn(Collections.emptyList());

    when(campaignDbOperations.findMaxDateEnteredAfter(campaign, LAST_WATERMARK)).thenReturn(NEW_MAX);
  }

  @Test
  public void expandsMatchedCustomersAndDropsQueuedCallsWithUnionSet() throws Exception {
    JSONArray page = new JSONArray();
    page.put(new JSONObject().put("id", 41).put("gsm_1", "05321110001")
        .put("customer_no", CUSTOMER_NO).put("date_entered", NEW_MAX));
    when(campaignDbOperations.fetchRowsEnteredAfter(campaign, LAST_WATERMARK, NEW_MAX, null, 500)).thenReturn(page);

    when(dncPhoneNumberService.findActivePhoneNumbersByDncIdsAndPhoneNumbers(any(), any(), any()))
        .thenReturn(Collections.emptySet());
    when(dncCustomerPhoneService.findActiveCustomerNosByDncIdsAndCustomerNos(any(), any(), any()))
        .thenReturn(new HashSet<>(Collections.singletonList(CUSTOMER_NO)));

    Set<String> expandedNumbers = new LinkedHashSet<>(Arrays.asList("05321110001", "02121110001"));
    when(customerDncService.blockByCustomerNo(CUSTOMER_NO, campaign, defaultDnc)).thenReturn(expandedNumbers);

    scheduler.runDeltaSync();

    ArgumentCaptor<Collection<String>> keysCaptor = ArgumentCaptor.forClass((Class) Collection.class);
    verify(campaignDbOperations).setFilteredByDncListStatusByUniqueKeys(eq(campaign), keysCaptor.capture());
    assertTrue(keysCaptor.getValue().contains("41"));

    verify(customerDncService, times(1)).blockByCustomerNo(CUSTOMER_NO, campaign, defaultDnc);

    ArgumentCaptor<Set<String>> droppedCaptor = ArgumentCaptor.forClass((Class) Set.class);
    verify(dncEngine, times(1)).dropQueuedCallsForNumbers(eq(defaultDnc), droppedCaptor.capture());
    assertTrue(droppedCaptor.getValue().contains("05321110001"));
    assertTrue(droppedCaptor.getValue().contains("02121110001"));
  }
}
