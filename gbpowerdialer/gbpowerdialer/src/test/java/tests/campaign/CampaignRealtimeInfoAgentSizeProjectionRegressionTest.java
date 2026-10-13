package tests.campaign;

import com.powerdialer.dao.ICampaignDao;
import com.powerdialer.dao.ITenantDao;
import com.powerdialer.dto.CampaignListDTO;
import com.powerdialer.engine.acd.ACDEngine;
import com.powerdialer.engine.db.CampaignDbOperations;
import com.powerdialer.engine.dialer.DialerFacade;
import com.powerdialer.model.Campaign;
import com.powerdialer.service.CampaignMementoService;
import com.powerdialer.service.CampaignService;
import org.dozer.Mapper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.powermock.reflect.Whitebox;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class CampaignRealtimeInfoAgentSizeProjectionRegressionTest {

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

  @Mock
  private Mapper mapper;

  @Mock
  private ACDEngine acdEngine;

  private CampaignService campaignService;

  @Before
  public void setUp() {
    campaignService = new CampaignService(campaignDao, campaignDbOperations, tenantDao, dialerFacade, campaignMementoService);
    Whitebox.setInternalState(campaignService, "mapper", mapper);
  }

  @Test
  public void realTimeInfoReturnsCalculatedAgentSizeWithoutMutatingStoredCounter() {
    Long[] requestedIds = new Long[] {296945L};
    Campaign campaign = new Campaign();
    campaign.setId(296945L);
    campaign.setAgentsSize(1);

    CampaignListDTO dto = new CampaignListDTO();
    dto.setId(296945L);
    dto.setAgentsSize(1);

    when(dialerFacade.getCampaignsRealtimeInfo(requestedIds)).thenReturn(Collections.singletonList(campaign));
    when(mapper.map(campaign, CampaignListDTO.class)).thenReturn(dto);
    when(dialerFacade.getAcdEngine()).thenReturn(acdEngine);
    // Task 325654: agent size artik tek registered-user snapshot'i kullanan batch
    // cagriyla hesaplanir; DTO projeksiyonu ve stored counter davranisi degismez.
    when(acdEngine.countCampaignAllActiveUsersByCampaignId(anyCollection()))
        .thenReturn(Collections.singletonMap(296945L, 16));

    List<CampaignListDTO> result = campaignService.realTimeInfo(requestedIds);

    assertEquals(1, result.size());
    assertEquals(16, result.get(0).getAgentsSize());
    assertEquals(1, campaign.getAgentsSize().get());
    verify(acdEngine).countCampaignAllActiveUsersByCampaignId(anyCollection());
  }
}
