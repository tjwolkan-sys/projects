package tests.dnc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Set;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.powerdialer.engine.CustomerDncService;
import com.powerdialer.engine.DNCEngine;
import com.powerdialer.engine.db.CampaignDbOperations;
import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.DNC;
import com.powerdialer.model.PhoneNumberColumn;
import com.powerdialer.service.DNCCustomerPhoneService;
import com.powerdialer.service.DNCPhoneNumberService;
import com.powerdialer.util.Constants;

/**
 * Edge Case 1: customer_no mapping'i yok veya satırda değer boşsa sistem hata
 * üretmez; mevcut satırın (rawData) telefon kolonlarındaki numaralar pd_dnc_pn'e
 * (saf PHONE) yazılır; pd_dnc_customer ilişkisi ve sibling işaretleme YAPILMAZ,
 * kampanya tablosuna SELECT atılmaz.
 */
@RunWith(MockitoJUnitRunner.class)
public class CustomerDncServiceFallsBackToRowNumbersWhenCustomerNoNullTest {

  private static final long DNC_ID = 7L;

  @InjectMocks
  private CustomerDncService customerDncService;

  @Mock
  private CampaignDbOperations campaignDbOperations;

  @Mock
  private DNCPhoneNumberService dncPhoneNumberService;

  @Mock
  private DNCCustomerPhoneService dncCustomerPhoneService;

  @Mock
  private DNCEngine dncEngine;

  private Campaign campaign;
  private DNC dnc;

  @Before
  public void init() {
    campaign = new Campaign();
    campaign.setId(318332L);
    campaign.getPhoneNumberColumns().add(phoneNumberColumn("phone_1", 0));
    campaign.getPhoneNumberColumns().add(phoneNumberColumn("phone_2", 1));

    dnc = new DNC();
    dnc.setId(DNC_ID);

    lenient().when(dncPhoneNumberService.existsByPhoneNumberAndDncId(anyString(), eq(DNC_ID))).thenReturn(false);
  }

  @Test
  public void mappingMissingFallsBackToRawDataNumbersWithoutErrors() throws Exception {
    campaign.setColResultCustomerNo(null);

    Call call = callWithRawData(new JSONObject()
        .put("phone_1", "05321112233")
        .put("phone_2", "02121112233"));

    Set<String> blocked = customerDncService.blockByCustomerScope(call, campaign, dnc);

    assertEquals(2, blocked.size());
    assertTrue(blocked.contains("05321112233"));
    assertTrue(blocked.contains("02121112233"));

    verify(campaignDbOperations, never()).fetchRowsByCustomerNo(any(), anyString());
    verify(campaignDbOperations, never()).setFilteredByDncListStatusByCustomerNo(any(), anyString(), any());
    verify(dncCustomerPhoneService, never()).createIfAbsent(anyString(), anyString(), any(), any());
    verify(dncPhoneNumberService).create("05321112233", Constants.REASON.WRAPUP_CODE, dnc);
    verify(dncPhoneNumberService).create("02121112233", Constants.REASON.WRAPUP_CODE, dnc);
    verify(dncEngine, times(1)).updateDnc(DNC_ID);
  }

  @Test
  public void blankCustomerNoValueInRowFallsBackToRawDataNumbers() throws Exception {
    campaign.setColResultCustomerNo("customer_no");

    Call call = callWithRawData(new JSONObject()
        .put("customer_no", " ")
        .put("phone_1", "05321112233"));

    Set<String> blocked = customerDncService.blockByCustomerScope(call, campaign, dnc);

    assertTrue(blocked.contains("05321112233"));
    verify(campaignDbOperations, never()).fetchRowsByCustomerNo(any(), anyString());
    verify(dncCustomerPhoneService, never()).createIfAbsent(anyString(), anyString(), any(), any());
  }

  @Test
  public void fallbackIncludesCalledNumberEvenIfMissingFromRawData() throws Exception {
    campaign.setColResultCustomerNo(null);

    Call call = callWithRawData(new JSONObject().put("phone_1", "05321112233"));
    call.setCalledNumber("05990001122");

    Set<String> blocked = customerDncService.blockByCustomerScope(call, campaign, dnc);

    assertTrue("Tetikleyen çağrının numarası her durumda engellenmeli", blocked.contains("05990001122"));
    assertTrue(blocked.contains("05321112233"));
    verify(dncPhoneNumberService).create("05990001122", Constants.REASON.WRAPUP_CODE, dnc);
  }

  private Call callWithRawData(JSONObject rawData) {
    Call call = new Call();
    call.setId("dl-1");
    call.setUniqueKey("41");
    call.setCalledNumber("05321112233");
    call.setCampaign(campaign);
    call.setRawData(rawData);
    return call;
  }

  private PhoneNumberColumn phoneNumberColumn(String colNumber, int sort) {
    PhoneNumberColumn column = new PhoneNumberColumn();
    column.setColNumber(colNumber);
    column.setColSort(sort);
    column.setEnabled(true);
    column.setDeleted(false);
    return column;
  }
}
