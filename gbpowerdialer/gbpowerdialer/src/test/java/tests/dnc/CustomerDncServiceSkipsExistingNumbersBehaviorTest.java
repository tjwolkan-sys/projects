package tests.dnc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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
import com.powerdialer.model.Campaign;
import com.powerdialer.model.DNC;
import com.powerdialer.model.PhoneNumberColumn;
import com.powerdialer.service.DNCCustomerPhoneService;
import com.powerdialer.service.DNCPhoneNumberService;
import com.powerdialer.util.Constants;

/**
 * Idempotency: pd_dnc_pn'de zaten kayıtlı numaralar için create tekrar
 * çağrılmaz; ilişki yazımı (pd_dnc_customer) her durumda createIfAbsent'e
 * devredilir (dedupe servisin içindedir). API yolu (blockByCustomerNo)
 * ADMINISTRATIVE reason kullanır ve dönen engellenen-numara seti doludur.
 */
@RunWith(MockitoJUnitRunner.class)
public class CustomerDncServiceSkipsExistingNumbersBehaviorTest {

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
    PhoneNumberColumn column = new PhoneNumberColumn();
    column.setColNumber("gsm_1");
    column.setColSort(0);
    column.setEnabled(true);
    column.setDeleted(false);
    campaign.getPhoneNumberColumns().add(column);

    dnc = new DNC();
    dnc.setId(DNC_ID);

    JSONArray customerRows = new JSONArray();
    customerRows.put(new JSONObject().put("customer_no", CUSTOMER_NO).put("gsm_1", "05321112233"));
    when(campaignDbOperations.fetchRowsByCustomerNo(campaign, CUSTOMER_NO)).thenReturn(customerRows);
  }

  @Test
  public void existingPhoneRowIsNotReinsertedButRelationStillRecorded() throws Exception {
    when(dncPhoneNumberService.existsByPhoneNumberAndDncId(anyString(), eq(DNC_ID))).thenReturn(true);

    Set<String> blocked = customerDncService.blockByCustomerNo(CUSTOMER_NO, campaign, dnc);

    assertEquals(1, blocked.size());
    assertTrue("Idempotent durumda da engellenen set dönmeli", blocked.contains("05321112233"));
    // pd_dnc_pn'de zaten var → PHONE kaydı tekrar oluşturulmaz
    verify(dncPhoneNumberService, never()).create(anyString(), any(), any());
    // ilişki yazımı yine denenir; dedupe pd_dnc_customer servisindedir
    verify(dncCustomerPhoneService).createIfAbsent(CUSTOMER_NO, "05321112233", Constants.REASON.ADMINISTRATIVE, dnc);
    verify(dncEngine, times(1)).updateDnc(DNC_ID);
    verify(campaignDbOperations).setFilteredByDncListStatusByCustomerNo(campaign, CUSTOMER_NO, null);
  }

  @Test
  public void apiPathCreatesMissingEntriesWithAdministrativeReason() throws Exception {
    when(dncPhoneNumberService.existsByPhoneNumberAndDncId(anyString(), eq(DNC_ID))).thenReturn(false);

    customerDncService.blockByCustomerNo(CUSTOMER_NO, campaign, dnc);

    verify(dncPhoneNumberService).create("05321112233", Constants.REASON.ADMINISTRATIVE, dnc);
    verify(dncCustomerPhoneService).createIfAbsent(CUSTOMER_NO, "05321112233", Constants.REASON.ADMINISTRATIVE, dnc);
  }
}
