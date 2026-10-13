package tests.dnc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.json.JSONArray;
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
import com.powerdialer.service.CampaignService;
import com.powerdialer.service.DNCCustomerPhoneService;
import com.powerdialer.service.DNCPhoneNumberService;
import com.powerdialer.service.DNCService;

/**
 * Re-entrancy guard: bir tick hâlâ çalışırken tetiklenen ikinci tick işe
 * başlamadan döner (AtomicBoolean, MirrorScheduler pattern'i).
 */
@RunWith(MockitoJUnitRunner.class)
public class DncDeltaSyncReentrancyGuardTest {

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

  @Before
  public void init() {
    ReflectionTestUtils.setField(scheduler, "batchSize", 500);
  }

  @Test
  public void secondTickWhileRunningReturnsWithoutLoadingCampaigns() throws Exception {
    // İlk tick çalışırken (eligible sorgusu sırasında) ikinci tick tetiklenir;
    // guard nedeniyle ikinci tick kampanya yüklemeden döner → eligible
    // sorgusu toplamda yalnız 1 kez çalışır.
    when(dbEngine.executeQuery(contains("pd_campaign"), isNull(), isNull(), any(), any(), any()))
        .thenAnswer(invocation -> {
          scheduler.runDeltaSync();
          return new JSONArray();
        });

    scheduler.runDeltaSync();

    verify(dbEngine, times(1)).executeQuery(contains("pd_campaign"), isNull(), isNull(), any(), any(), any());
  }
}
