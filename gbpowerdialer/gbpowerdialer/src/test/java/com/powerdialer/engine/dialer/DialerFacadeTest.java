package com.powerdialer.engine.dialer;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;
import java.util.HashMap;
import java.util.Map;

import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.powermock.reflect.Whitebox;
import org.mockito.junit.MockitoJUnitRunner;

import com.powerdialer.engine.DNCEngine;
import com.powerdialer.engine.LSCEngine;
import com.powerdialer.engine.StopConditionChecker;
import com.powerdialer.engine.acd.ACDEngine;
import com.powerdialer.engine.asterisk.ami.ManagerConnectionComposition;
import com.powerdialer.engine.asterisk.ami.ManagerConnectionDecorator;
import com.powerdialer.engine.boost.DynamicBoostManager;
import com.powerdialer.engine.dialer.state.CampaignStateTemplate;
import com.powerdialer.engine.predictive.beta.processor.PredictiveBetaCallManager;
import com.powerdialer.engine.predictive.gamma.processor.PredictiveGammaCallManager;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.Server;
import com.powerdialer.service.CampaignDataTypeChecker;
import com.powerdialer.service.CampaignMementoService;
import com.powerdialer.service.CampaignResultJobService;
import com.powerdialer.util.Constants.CAMPAIGN_ACTION;
import com.powerdialer.util.Constants.CAMPAIGN_STATE;
import com.powerdialer.util.Constants.END_LIST_BEHAVIOUR;
import com.powerdialer.util.LoggerDecorator;

/**
 * Tests for DialerFacade class
 */
@RunWith(MockitoJUnitRunner.class)
public class DialerFacadeTest {

  private Map<String, ManagerConnectionDecorator> openedConnections = new HashMap<>();

  @Mock
  private ManagerConnectionComposition connectionComposition;

  @Mock
  private Campaign campaign;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private LoggerDecorator loggerDecorator;

  @Mock
  private CampaignMementoService campaignMementoService;

  @Mock
  private CampaignResultJobService campaignResultJobService;

  @Mock
  private CampaignRealTimeRingingManager campaignRealTimeRingingManager;

  @Mock
  private DynamicBoostManager dynamicBoostManager;

  @Mock
  private PredictiveBetaCallManager predictiveBetaCallManager;

  @Mock
  private PredictiveGammaCallManager predictiveGammaCallManager;

  @Mock
  private DNCEngine dncEngine;

  @Mock
  private LSCEngine lscEngine;

  @Mock
  private CampaignDataTypeChecker campaignDataTypeChecker;

  @Mock
  private ACDEngine acdEngine;

  @Mock
  private StopConditionChecker stopConditionChecker;

  @InjectMocks
  private DialerFacade dialerFacade;

  @Mock
  private CampaignContext campaignContext;

  @Before
  public void init() {
    when(campaign.isEnabled()).thenReturn(true);
    when(campaign.getStartDate()).thenReturn(new DateTime().minusHours(1).toDate());
    when(campaign.getEndDate()).thenReturn(new DateTime().plusHours(1).toDate());
    when(campaign.getEndListBehaviour()).thenReturn(END_LIST_BEHAVIOUR.CONTINUE);
    lenient().when(campaign.getPredictiveStrategy()).thenReturn(com.powerdialer.util.Constants.PREDICTIVE_STRATEGY.ALPHA);
    when(loggerDecorator.getLogger(CampaignStateTemplate.class)).thenReturn(mock(LoggerDecorator.class));
    when(campaignContext.getCampaign()).thenReturn(campaign);
    when(connectionComposition.getDialerFacade()).thenReturn(dialerFacade);
    when(connectionComposition.getOpenedConnections()).thenReturn(openedConnections);
    when(connectionComposition.getLoggerDecorator()).thenReturn(new LoggerDecorator());
    dialerFacade.getCampaignContexts().put(campaign.getId(), campaignContext);
  }

  @Test
  public void shouldCreateCallOriginators() {
    Server server = new Server();
    server.setInUse(true);
    server.setId(0L);
    server.setIp("127.0.0.1");

    ManagerConnectionDecorator connectionDecorator = mock(ManagerConnectionDecorator.class);
    when(connectionDecorator.getServer()).thenReturn(server);
    when(connectionDecorator.getConnectionComposition()).thenReturn(connectionComposition);
    openedConnections.put(server.getIp(), connectionDecorator);

    dialerFacade.startCallOriginators();
    assertEquals(1, dialerFacade.getAsteriskCallOriginators().size());
  }

  @Test
  public void shouldNotCreateCallOriginatorsForNotInUseServer() {
    Server server = new Server();
    server.setInUse(false);
    server.setId(0L);
    server.setIp("127.0.0.1");

    ManagerConnectionDecorator connectionDecorator = mock(ManagerConnectionDecorator.class);
    when(connectionDecorator.getServer()).thenReturn(server);
    lenient().when(connectionDecorator.getConnectionComposition()).thenReturn(connectionComposition);
    openedConnections.put(server.getIp(), connectionDecorator);

    dialerFacade.startCallOriginators();
    assertEquals(0, dialerFacade.getAsteriskCallOriginators().size());
  }

  @Test
  public void shouldStartStoppedCampaign() {
    Whitebox.setInternalState(dialerFacade, "campaignContexts", mock(HashMap.class));
    doReturn(true).when(campaignDataTypeChecker).checkColumnsTypes(campaign);
    lenient().when(campaign.getState()).thenReturn(CAMPAIGN_STATE.STOPPED);
    dialerFacade.startCampaign(campaign);
    verify(campaign).setState(CAMPAIGN_STATE.STARTING);
  }

  @Test
  public void shouldUnpausePausedCampaign() {
    when(campaign.getState()).thenReturn(CAMPAIGN_STATE.PAUSED);
    dialerFacade.unpauseCampaign(0L);
    verify(campaign).setManuallyPaused(false);
    verify(campaign).setState(CAMPAIGN_STATE.STARTING);
    verify(campaignContext).start();
  }

  @Test
  public void shouldShutdownRunningCampaign() {
    when(campaign.getState()).thenReturn(CAMPAIGN_STATE.RUNNING);
    dialerFacade.shutdownCampaign(campaign.getId(), CAMPAIGN_ACTION.STOP_FORCEFUL);
    verify(campaign).setManuallyPaused(false);
    verify(campaign).setState(CAMPAIGN_STATE.STOPPING);
    verify(campaignContext).stop(CAMPAIGN_ACTION.STOP_FORCEFUL);
  }

  @Test
  public void shouldMarkManuallyPausedOnManualPause() {
    when(campaign.getState()).thenReturn(CAMPAIGN_STATE.RUNNING);
    dialerFacade.pauseCampaign(campaign.getId(), true);
    verify(campaign).setManuallyPaused(true);
    verify(campaign).setState(CAMPAIGN_STATE.PAUSING);
    verify(campaignContext).pause();
  }

  @Test
  public void shouldNotMarkManuallyPausedOnAutomationPause() {
    when(campaign.getState()).thenReturn(CAMPAIGN_STATE.RUNNING);
    dialerFacade.pauseCampaign(campaign.getId(), false);
    verify(campaign).setManuallyPaused(false);
    verify(campaign).setState(CAMPAIGN_STATE.PAUSING);
    verify(campaignContext).pause();
  }

  @Test
  public void shouldReloadCampaign() {
    dialerFacade.reloadCampaign(campaign.getId());
    verify(campaignContext).reload();
  }

  @Test
  public void shouldResetCampaign() {
    dialerFacade.resetCampaign(campaign.getId());
    verify(campaignContext).reset();
  }

  @Test
  public void shouldGetCampaignsRealtimeInfo() {
    assertEquals(1, dialerFacade.getCampaignsRealtimeInfo(new Long[] { campaign.getId() }).size());
  }

}
