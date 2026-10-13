package tests.datamanipulation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.powerdialer.datamanipulation.supplier.DataSupplier;
import com.powerdialer.engine.CustomerDncService;
import com.powerdialer.engine.DNCEngine;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.DNC;
import com.powerdialer.service.CampaignService;
import com.powerdialer.service.DNCDataManipulationService;
import com.powerdialer.service.DNCPhoneNumberService;
import com.powerdialer.service.DNCService;

/**
 * Puzzle/harici sistem API'si: customer_no listesi kampanyanın default DNC
 * listesine müşteri kapsamıyla işlenir — her customer_no CustomerDncService
 * ile telefonlara genişletilir, sonda tüm engellenen numaraların union
 * setiyle TEK canlı kuyruk hot-drop'u yapılır.
 */
@RunWith(MockitoJUnitRunner.class)
public class DncCustomerApiInsertsCustomerScopeTest {

  private static final long CAMPAIGN_ID = 318332L;
  private static final long DNC_ID = 7L;

  @InjectMocks
  private DNCDataManipulationService dncDataManipulationService;

  @Mock
  private DNCService dncService;

  @Mock
  private DNCPhoneNumberService dncPhoneNumberService;

  @Mock
  private DNCEngine dncEngine;

  @Mock
  private CustomerDncService customerDncService;

  @Mock
  private CampaignService campaignService;

  @Mock
  private DataSupplier<String> dataSupplier;

  private Campaign campaign;
  private DNC defaultDnc;

  @Before
  public void init() {
    defaultDnc = new DNC();
    defaultDnc.setId(DNC_ID);

    campaign = new Campaign();
    campaign.setId(CAMPAIGN_ID);
    campaign.setColResultCustomerNo("customer_no");

    when(dncService.getDefaultDNCByCampaignId(CAMPAIGN_ID)).thenReturn(defaultDnc);
    when(campaignService.getCampaignWithPhoneNumberColumnsByNameOrId(String.valueOf(CAMPAIGN_ID))).thenReturn(campaign);
  }

  @Test
  public void processesEachCustomerAndDropsQueuedCallsOnceWithUnionSet() throws Exception {
    when(dataSupplier.getNextObject()).thenReturn("CUST-1", "CUST-2", null);
    when(customerDncService.blockByCustomerNo("CUST-1", campaign, defaultDnc))
        .thenReturn(new LinkedHashSet<>(Arrays.asList("05321110001", "02121110001")));
    when(customerDncService.blockByCustomerNo("CUST-2", campaign, defaultDnc))
        .thenReturn(new LinkedHashSet<>(Arrays.asList("05321110002")));

    int processedCount = dncDataManipulationService.processInsertCustomersForDefaultDnc(CAMPAIGN_ID, dataSupplier);

    assertEquals(2, processedCount);
    verify(customerDncService).blockByCustomerNo("CUST-1", campaign, defaultDnc);
    verify(customerDncService).blockByCustomerNo("CUST-2", campaign, defaultDnc);

    ArgumentCaptor<Set<String>> blockedCaptor = ArgumentCaptor.forClass((Class) Set.class);
    verify(dncEngine, times(1)).dropQueuedCallsForNumbers(eq(defaultDnc), blockedCaptor.capture());
    Set<String> unionSet = blockedCaptor.getValue();
    assertEquals(3, unionSet.size());
    assertTrue(unionSet.contains("05321110001"));
    assertTrue(unionSet.contains("02121110001"));
    assertTrue(unionSet.contains("05321110002"));
  }

  @Test
  public void skipsBlankCustomerNosWithoutFailing() throws Exception {
    when(dataSupplier.getNextObject()).thenReturn("CUST-1", "  ", null);
    when(customerDncService.blockByCustomerNo("CUST-1", campaign, defaultDnc))
        .thenReturn(new LinkedHashSet<>(Arrays.asList("05321110001")));

    int processedCount = dncDataManipulationService.processInsertCustomersForDefaultDnc(CAMPAIGN_ID, dataSupplier);

    assertEquals(1, processedCount);
    verify(customerDncService, times(1)).blockByCustomerNo("CUST-1", campaign, defaultDnc);
    verify(dncEngine, times(1)).dropQueuedCallsForNumbers(eq(defaultDnc), any());
  }
}
