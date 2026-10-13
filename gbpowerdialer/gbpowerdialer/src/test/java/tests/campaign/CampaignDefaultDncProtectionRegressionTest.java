package tests.campaign;

import static org.junit.Assert.assertTrue;

import java.util.HashSet;

import com.powerdialer.dao.ICampaignDao;
import com.powerdialer.dao.ITenantDao;
import com.powerdialer.engine.db.CampaignDbOperations;
import com.powerdialer.engine.dialer.DialerFacade;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.DNC;
import com.powerdialer.service.CampaignMementoService;
import com.powerdialer.service.CampaignService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

@RunWith(MockitoJUnitRunner.class)
public class CampaignDefaultDncProtectionRegressionTest {

  @Mock
  private ICampaignDao campaignDao;

  @Mock
  private CampaignDbOperations campaignDbOperations;

  @Mock
  private ITenantDao tenantDao;

  @Mock
  private DialerFacade dialerFacade;

  @Mock
  private CampaignMementoService campaignMementoService;

  private CampaignService campaignService;

  @Before
  public void setUp() {
    campaignService = new CampaignService(campaignDao, campaignDbOperations, tenantDao, dialerFacade, campaignMementoService);
  }

  @Test
  public void defaultDncShouldStayMappedWhenCampaignPayloadOmitsIt() {
    DNC defaultDnc = new DNC();
    defaultDnc.setId(102L);

    Campaign persistedCampaign = new Campaign();
    persistedCampaign.setDnc(defaultDnc);

    DNC selectedDnc = new DNC();
    selectedDnc.setId(103L);

    Campaign campaignFromRequest = new Campaign();
    campaignFromRequest.setDncs(new HashSet<DNC>());
    campaignFromRequest.getDncs().add(selectedDnc);

    ReflectionTestUtils.invokeMethod(campaignService, "ensureDefaultDncCannotBeRemoved", campaignFromRequest, persistedCampaign);

    assertTrue(campaignFromRequest.getDncs().contains(defaultDnc));
  }
}
