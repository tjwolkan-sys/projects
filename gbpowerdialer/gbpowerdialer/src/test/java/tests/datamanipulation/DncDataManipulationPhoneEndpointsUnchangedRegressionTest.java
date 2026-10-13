package tests.datamanipulation;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

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
import com.powerdialer.model.DNC;
import com.powerdialer.model.DNCPhoneNumber;
import com.powerdialer.service.CampaignService;
import com.powerdialer.service.DNCDataManipulationService;
import com.powerdialer.service.DNCPhoneNumberService;
import com.powerdialer.service.DNCService;

/**
 * Geriye uyumluluk kilidi: mevcut telefon bazlı /data/dnc add/disable
 * akışları customer-scope eklentilerinden etkilenmez — batch insert/disable
 * + tek updateDnc davranışı birebir korunur; customer servisi ve hot-drop
 * bu yollarda devreye girmez.
 */
@RunWith(MockitoJUnitRunner.class)
public class DncDataManipulationPhoneEndpointsUnchangedRegressionTest {

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

  private DNC defaultDnc;

  @Before
  public void init() {
    defaultDnc = new DNC();
    defaultDnc.setId(DNC_ID);
    when(dncService.getDefaultDNCByCampaignId(CAMPAIGN_ID)).thenReturn(defaultDnc);
  }

  @Test
  public void phoneInsertEndpointBehaviorIsUnchanged() throws Exception {
    when(dataSupplier.getNextObject()).thenReturn("05321110001", "05321110002", null);

    int insertedCount = dncDataManipulationService.processInsertDataForDefaultDnc(CAMPAIGN_ID, dataSupplier);

    assertEquals(2, insertedCount);
    ArgumentCaptor<List<DNCPhoneNumber>> bulkCaptor = ArgumentCaptor.forClass((Class) List.class);
    verify(dncPhoneNumberService, times(1)).batchInsert(bulkCaptor.capture());
    assertEquals(2, bulkCaptor.getValue().size());
    verify(dncEngine, times(1)).updateDnc(DNC_ID);
    verify(customerDncService, never()).blockByCustomerNo(anyString(), any(), any());
    verify(dncEngine, never()).dropQueuedCallsForNumbers(any(), any());
  }

  @Test
  public void phoneDisableEndpointBehaviorIsUnchanged() throws Exception {
    when(dataSupplier.getNextObject()).thenReturn("05321110001", null);

    int disabledCount = dncDataManipulationService.processDisableDataForDefaultDnc(CAMPAIGN_ID, dataSupplier);

    assertEquals(1, disabledCount);
    verify(dncPhoneNumberService).disableByDncAndPhoneNumber(eq(DNC_ID), anyList());
    verify(dncEngine, times(1)).updateDnc(DNC_ID);
    verify(customerDncService, never()).blockByCustomerNo(anyString(), any(), any());
    verify(dncEngine, never()).dropQueuedCallsForNumbers(any(), any());
  }
}
