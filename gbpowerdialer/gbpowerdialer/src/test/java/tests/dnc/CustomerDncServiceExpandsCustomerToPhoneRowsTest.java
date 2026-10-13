package tests.dnc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;

import org.json.JSONArray;
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
 * Customer-scope DNC çekirdeği (FEAT-318332 refactor): customer_no üzerinden
 * müşterinin tüm numaraları toplanır; pd_dnc_pn'e saf PHONE kaydı (customer_no
 * taşımaz) + pd_dnc_customer'a (customer_no, phone) ilişkisi yazılır (artık
 * marker/dnc_type yok), tek cache refresh yapılır ve müşterinin açık sibling
 * satırları aranamaz işaretlenir.
 */
@RunWith(MockitoJUnitRunner.class)
public class CustomerDncServiceExpandsCustomerToPhoneRowsTest {

  private static final long DNC_ID = 7L;
  private static final String CUSTOMER_NO = "CUST-999";

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
  public void init() throws Exception {
    campaign = new Campaign();
    campaign.setId(318332L);
    campaign.setColResultCustomerNo("customer_no");
    campaign.getPhoneNumberColumns().add(phoneNumberColumn("gsm_1", 0));
    campaign.getPhoneNumberColumns().add(phoneNumberColumn("ev_tel", 1));

    dnc = new DNC();
    dnc.setId(DNC_ID);

    JSONArray customerRows = new JSONArray();
    customerRows.put(new JSONObject()
        .put("customer_no", CUSTOMER_NO)
        .put("gsm_1", "05321112233")
        .put("ev_tel", "02121112233"));
    customerRows.put(new JSONObject()
        .put("customer_no", CUSTOMER_NO)
        .put("gsm_1", "05449998877"));
    when(campaignDbOperations.fetchRowsByCustomerNo(campaign, CUSTOMER_NO)).thenReturn(customerRows);

    when(dncPhoneNumberService.existsByPhoneNumberAndDncId(anyString(), eq(DNC_ID))).thenReturn(false);
  }

  @Test
  public void expandsAllCustomerPhonesIntoPhoneRowsAndRelations() throws Exception {
    Call call = new Call();
    call.setId("dl-1");
    call.setUniqueKey("41");
    call.setCalledNumber("05321112233");
    call.setCampaign(campaign);
    call.setRawData(new JSONObject().put("customer_no", CUSTOMER_NO).put("gsm_1", "05321112233"));

    Set<String> blocked = customerDncService.blockByCustomerScope(call, campaign, dnc);

    assertEquals(3, blocked.size());
    assertTrue(blocked.contains("05321112233"));
    assertTrue(blocked.contains("02121112233"));
    assertTrue(blocked.contains("05449998877"));

    // pd_dnc_pn: saf telefon block-list (customer_no taşımaz)
    verify(dncPhoneNumberService).create("05321112233", Constants.REASON.WRAPUP_CODE, dnc);
    verify(dncPhoneNumberService).create("02121112233", Constants.REASON.WRAPUP_CODE, dnc);
    verify(dncPhoneNumberService).create("05449998877", Constants.REASON.WRAPUP_CODE, dnc);
    // pd_dnc_customer: (customer_no, phone) ilişkisi
    verify(dncCustomerPhoneService).createIfAbsent(CUSTOMER_NO, "05321112233", Constants.REASON.WRAPUP_CODE, dnc);
    verify(dncCustomerPhoneService).createIfAbsent(CUSTOMER_NO, "02121112233", Constants.REASON.WRAPUP_CODE, dnc);
    verify(dncCustomerPhoneService).createIfAbsent(CUSTOMER_NO, "05449998877", Constants.REASON.WRAPUP_CODE, dnc);
    verify(dncEngine, times(1)).updateDnc(DNC_ID);
    verify(campaignDbOperations).setFilteredByDncListStatusByCustomerNo(campaign, CUSTOMER_NO, "41");
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
