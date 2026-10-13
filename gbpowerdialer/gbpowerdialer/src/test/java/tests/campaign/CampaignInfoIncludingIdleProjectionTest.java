package tests.campaign;

import com.powerdialer.dao.ICampaignDao;
import com.powerdialer.dao.ITenantDao;
import com.powerdialer.dto.CampaignListDTO;
import com.powerdialer.engine.acd.ACDEngine;
import com.powerdialer.engine.db.CampaignDbOperations;
import com.powerdialer.engine.dialer.DialerFacade;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.CampaignMemento;
import com.powerdialer.service.CampaignMementoService;
import com.powerdialer.service.CampaignService;
import org.dozer.Mapper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.powermock.reflect.Whitebox;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CampaignService#campaignInfo(Long[])} davranışını kilitler:
 * running kampanya context'ten (canlı realtime), idle kampanya DB entity + memento
 * snapshot'ından beslenir; boş ids yalnız running döndürür ve DB'ye gitmez.
 */
@RunWith(MockitoJUnitRunner.class)
public class CampaignInfoIncludingIdleProjectionTest {

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
  public void returnsRunningFromContextAndIdleFromDbMemento() {
    Long runningId = 100L;
    Long idleId = 200L;
    Long[] requestedIds = new Long[] {runningId, idleId};

    Campaign runningCampaign = new Campaign();
    runningCampaign.setId(runningId);
    CampaignListDTO runningDto = new CampaignListDTO();
    runningDto.setId(runningId);

    when(dialerFacade.getCampaignsRealtimeInfo(requestedIds)).thenReturn(Collections.singletonList(runningCampaign));
    when(mapper.map(runningCampaign, CampaignListDTO.class)).thenReturn(runningDto);
    when(dialerFacade.getAcdEngine()).thenReturn(acdEngine);
    // Task 325654: agent size tek registered-user snapshot'i kullanan batch cagriyla hesaplanir.
    when(acdEngine.countCampaignAllActiveUsersByCampaignId(anyCollection()))
        .thenReturn(Collections.singletonMap(runningId, 7));

    Campaign idleCampaign = new Campaign();
    idleCampaign.setId(idleId);
    CampaignListDTO idleDto = new CampaignListDTO();
    idleDto.setId(idleId);
    when(campaignDao.getAll(null, false, new Long[] {idleId})).thenReturn(Collections.singletonList(idleCampaign));
    when(mapper.map(idleCampaign, CampaignListDTO.class)).thenReturn(idleDto);

    CampaignMemento memento = mock(CampaignMemento.class);
    Campaign mementoCampaign = new Campaign();
    mementoCampaign.setId(idleId);
    when(memento.getCampaign()).thenReturn(mementoCampaign);
    when(campaignMementoService.getCampaignMementosByCampaignIds(anyList())).thenReturn(Collections.singletonList(memento));

    List<CampaignListDTO> result = campaignService.campaignInfo(requestedIds);

    assertEquals(2, result.size());
    assertTrue(result.contains(runningDto));
    assertTrue(result.contains(idleDto));
    assertEquals(7, findById(result, runningId).getAgentsSize());
    verify(acdEngine).countCampaignAllActiveUsersByCampaignId(anyCollection());
    // idle DTO memento snapshot ile dolduruldu
    verify(memento).restore(idleDto);
    // DB yalnız idle id için sorgulandı (running id dışlandı)
    verify(campaignDao).getAll(null, false, new Long[] {idleId});
  }

  @Test
  public void idleCampaignWithoutMementoIsReturnedFromDbEntity() {
    Long idleId = 500L;
    Long[] requestedIds = new Long[] {idleId};

    when(dialerFacade.getCampaignsRealtimeInfo(requestedIds)).thenReturn(Collections.emptyList());

    Campaign idleCampaign = new Campaign();
    idleCampaign.setId(idleId);
    CampaignListDTO idleDto = new CampaignListDTO();
    idleDto.setId(idleId);
    when(campaignDao.getAll(null, false, requestedIds)).thenReturn(Collections.singletonList(idleCampaign));
    when(mapper.map(idleCampaign, CampaignListDTO.class)).thenReturn(idleDto);
    when(campaignMementoService.getCampaignMementosByCampaignIds(anyList())).thenReturn(Collections.emptyList());

    List<CampaignListDTO> result = campaignService.campaignInfo(requestedIds);

    assertEquals(1, result.size());
    assertEquals(idleId, result.get(0).getId());
  }

  @Test
  public void emptyIdsReturnsOnlyRunningWithoutDbLookup() {
    Long[] emptyIds = new Long[0];

    Campaign runningCampaign = new Campaign();
    runningCampaign.setId(1L);
    CampaignListDTO runningDto = new CampaignListDTO();
    runningDto.setId(1L);
    when(dialerFacade.getCampaignsRealtimeInfo(emptyIds)).thenReturn(Collections.singletonList(runningCampaign));
    when(mapper.map(runningCampaign, CampaignListDTO.class)).thenReturn(runningDto);
    when(dialerFacade.getAcdEngine()).thenReturn(acdEngine);
    when(acdEngine.countCampaignAllActiveUsersByCampaignId(anyCollection()))
        .thenReturn(Collections.singletonMap(1L, 3));

    List<CampaignListDTO> result = campaignService.campaignInfo(emptyIds);

    assertEquals(1, result.size());
    verify(campaignDao, never()).getAll(any(), any(Boolean.class), any());
    verify(campaignMementoService, never()).getCampaignMementosByCampaignIds(anyList());
  }

  private static CampaignListDTO findById(List<CampaignListDTO> list, Long id) {
    return list.stream().filter(dto -> id.equals(dto.getId())).findFirst().orElse(null);
  }
}
