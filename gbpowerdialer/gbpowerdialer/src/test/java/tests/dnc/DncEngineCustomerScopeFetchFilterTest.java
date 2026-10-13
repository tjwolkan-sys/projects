package tests.dnc;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import com.powerdialer.engine.DNCEngine;
import com.powerdialer.engine.StopConditionChecker;
import com.powerdialer.engine.db.CampaignDbOperations;
import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.DNC;
import com.powerdialer.model.DNCPhoneNumber;
import com.powerdialer.model.PhoneNumberColumn;
import com.powerdialer.model.Tenant;
import com.powerdialer.service.CDRService;
import com.powerdialer.service.CampaignService;
import com.powerdialer.service.DNCCustomerPhoneService;
import com.powerdialer.service.DNCPhoneNumberService;
import com.powerdialer.service.DNCService;
import com.powerdialer.util.LoggerDecorator;

/**
 * Müşteri kararı (03.06.2026): aynı customer_no ile sonradan yüklenen data
 * yeni bir telefonla gelirse, delta-sync cron'unu beklemeden CALL PRODUCER
 * fetch'inde yakalanıp TRC=76 ile kapatılmalıdır. applyDncToCalls, telefon
 * filtresine ek olarak fetch sayfası başına TEK batch sorguyla aktif
 * CUSTOMER marker eşleşmesini kontrol eder; customer_no mapping'i olmayan
 * kampanyalarda bu kontrol sıfır maliyetlidir, sorgu hatasında fail-open
 * davranır (delta-sync gece telafi eder).
 */
@RunWith(MockitoJUnitRunner.class)
public class DncEngineCustomerScopeFetchFilterTest {

  private static final long TENANT_ID = 9999L;
  private static final long CAMPAIGN_ID = 318332L;
  private static final long CAMPAIGN_DNC_LIST_ID = 7L;
  private static final String BLOCKED_CUSTOMER_NO = "CUST-999";

  @InjectMocks
  private DNCEngine dncEngine;

  @Mock
  private DNCService dncService;

  @Mock
  private CampaignDbOperations campaignDbOperations;

  @Mock
  private StopConditionChecker stopConditionChecker;

  @Mock
  private DNCPhoneNumberService dncPhoneNumberService;

  @Mock
  private DNCCustomerPhoneService dncCustomerPhoneService;

  @Mock
  private CampaignService campaignService;

  @Mock
  private CDRService cdrService;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private LoggerDecorator loggerDecorator;

  private Campaign runningCampaign;

  @Before
  public void init() {
    Tenant tenant = new Tenant();
    tenant.setId(TENANT_ID);

    DNC campaignDnc = new DNC();
    campaignDnc.setId(CAMPAIGN_DNC_LIST_ID);
    campaignDnc.setEnabled(true);
    campaignDnc.setDeleted(false);
    campaignDnc.setGlobal(false);
    campaignDnc.setTenant(tenant);

    DNCPhoneNumber phoneEntry = new DNCPhoneNumber();
    phoneEntry.setId(1L);
    phoneEntry.setPhoneNumber("5550009999");
    phoneEntry.setDncList(campaignDnc);
    phoneEntry.setEnabled(true);
    phoneEntry.setDeleted(false);
    campaignDnc.setPhoneNumbers(Collections.singletonList(phoneEntry));

    when(dncService.getGlobalDnc(true)).thenReturn(Collections.emptyList());
    when(dncService.getDncForCampaign(CAMPAIGN_ID)).thenReturn(Collections.singletonList(campaignDnc));
    when(dncPhoneNumberService.getByListId(CAMPAIGN_DNC_LIST_ID)).thenReturn(campaignDnc.getPhoneNumbers());
    Mockito.lenient().when(dncPhoneNumberService.findActivePhoneNumbersByDncIdsAndPhoneNumbers(any(), any(), any(Date.class)))
        .thenAnswer(invocation -> new HashSet<>((Collection<String>) invocation.getArgument(1)));
    Mockito.lenient().doNothing().when(cdrService).save(any());

    runningCampaign = new Campaign();
    runningCampaign.setId(CAMPAIGN_ID);
    runningCampaign.setDncEnabled(true);
    runningCampaign.setTenant(tenant);
    runningCampaign.setColResultCustomerNo("customer_no");
    runningCampaign.getPhoneNumberColumns().add(new PhoneNumberColumn());

    dncEngine.init();
    dncEngine.addRunningCampaign(runningCampaign);
  }

  @Test
  public void fetchedCallWithActiveCustomerMarkerIsFilteredBeforeQueueing() throws Exception {
    when(dncCustomerPhoneService.findActiveCustomerNosByDncIdsAndCustomerNos(any(), anyCollection(), any(Date.class)))
        .thenReturn(Collections.singleton(BLOCKED_CUSTOMER_NO));

    Call blockedCustomerCall = callWithCustomerNo("dl-a", "05321110001", BLOCKED_CUSTOMER_NO);
    Call otherCustomerCall = callWithCustomerNo("dl-b", "05321110002", "CUST-OK");
    Call noCustomerCall = callWithCustomerNo("dl-c", "05321110003", null);

    List<Call> calls = new ArrayList<>(Arrays.asList(blockedCustomerCall, otherCustomerCall, noCustomerCall));
    dncEngine.applyDncToCalls(runningCampaign, calls);

    assertFalse("Aktif CUSTOMER marker'lı çağrı fetch'te düşmeli", calls.contains(blockedCustomerCall));
    assertTrue(calls.contains(otherCustomerCall));
    assertTrue(calls.contains(noCustomerCall));

    verify(campaignDbOperations).setFilteredByDncListStatus(org.mockito.ArgumentMatchers.eq(runningCampaign), any());
    verify(cdrService).save(any());
  }

  @Test
  public void campaignWithoutCustomerNoMappingNeverQueriesCustomerMarkers() throws Exception {
    runningCampaign.setColResultCustomerNo(null);

    Call call = callWithCustomerNo("dl-a", "05321110001", BLOCKED_CUSTOMER_NO);
    List<Call> calls = new ArrayList<>(Collections.singletonList(call));
    dncEngine.applyDncToCalls(runningCampaign, calls);

    assertTrue(calls.contains(call));
    verify(dncCustomerPhoneService, never()).findActiveCustomerNosByDncIdsAndCustomerNos(any(), anyCollection(), any(Date.class));
  }

  @Test
  public void customerLookupFailureIsFailOpenAndKeepsCalls() throws Exception {
    when(dncCustomerPhoneService.findActiveCustomerNosByDncIdsAndCustomerNos(any(), anyCollection(), any(Date.class)))
        .thenThrow(new RuntimeException("DB timeout"));

    Call call = callWithCustomerNo("dl-a", "05321110001", BLOCKED_CUSTOMER_NO);
    List<Call> calls = new ArrayList<>(Collections.singletonList(call));
    dncEngine.applyDncToCalls(runningCampaign, calls);

    assertTrue("Customer lookup hatasında çağrılar düşürülmemeli (fail-open; delta-sync telafi eder)",
        calls.contains(call));
    verify(campaignDbOperations, never()).setFilteredByDncListStatus(any(), any());
  }

  @Test
  public void callsWithoutAnyCustomerNoSkipCustomerLookupEntirely() throws Exception {
    Call call = callWithCustomerNo("dl-a", "05321110001", null);
    List<Call> calls = new ArrayList<>(Collections.singletonList(call));
    dncEngine.applyDncToCalls(runningCampaign, calls);

    assertTrue(calls.contains(call));
    verify(dncCustomerPhoneService, never()).findActiveCustomerNosByDncIdsAndCustomerNos(any(), anyCollection(), any(Date.class));
  }

  private Call callWithCustomerNo(String id, String number, String customerNo) throws Exception {
    Call call = new Call();
    call.setId(id);
    call.setUniqueKey(id);
    call.setCalledNumber(number);
    call.setCampaign(runningCampaign);
    call.setResultCodeSequence(0);
    JSONObject rawData = new JSONObject();
    if (customerNo != null) {
      rawData.put("customer_no", customerNo);
    }
    call.setRawData(rawData);
    return call;
  }
}
