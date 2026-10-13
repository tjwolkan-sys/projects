package com.powerdialer.engine.dialer.state;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.powerdialer.engine.DNCEngine;
import com.powerdialer.engine.LSCEngine;
import com.powerdialer.engine.dialer.CallConsumer;
import com.powerdialer.engine.dialer.CallOriginator;
import com.powerdialer.engine.dialer.CallProducer;
import com.powerdialer.engine.dialer.CampaignContext;
import com.powerdialer.engine.dialer.CampaignRealTimeRingingManager;
import com.powerdialer.engine.dialer.DialerFacade;
import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.Location;
import com.powerdialer.model.Server;
import com.powerdialer.util.Constants.CAMPAIGN_STATE;
import com.powerdialer.util.LoggerDecorator;

@RunWith(MockitoJUnitRunner.class)
public class PauseStateTest {

  @Mock
  private CampaignContext campaignContext;

  @Mock
  private DialerFacade dialerFacade;

  @Mock
  private LoggerDecorator loggerDecorator;

  @Mock
  private Campaign campaign;

  @Mock
  private CallProducer callProducer;

  @Mock
  private CallConsumer callConsumer;

  @Mock
  private CampaignRealTimeRingingManager campaignRealTimeRingingManager;

  private CampaignStateTemplate pauseState;

  @Before
  public void init() {
    when(loggerDecorator.getLogger(PauseState.class)).thenReturn(loggerDecorator);
    when(loggerDecorator.getLogger(CampaignStateTemplate.class)).thenReturn(loggerDecorator);
    when(dialerFacade.getLoggerDecorator()).thenReturn(loggerDecorator);
    when(dialerFacade.getDncEngine()).thenReturn(mock(DNCEngine.class));
    when(dialerFacade.getLscEngine()).thenReturn(mock(LSCEngine.class));
    when(dialerFacade.getCampaignRealTimeRingingManager()).thenReturn(campaignRealTimeRingingManager);
    when(campaignContext.getDialerFacade()).thenReturn(dialerFacade);
    when(campaignContext.getCampaign()).thenReturn(campaign);
    when(campaignContext.getCallProducer()).thenReturn(callProducer);
    when(campaignContext.getCallConsumer()).thenReturn(callConsumer);
    pauseState = new PauseState(campaignContext);
  }

  @Test
  public void shouldReattachCallQueueWhenPausedCampaignStartsAgain() {
    Location location = mock(Location.class);
    Server server = mock(Server.class);
    CallOriginator callOriginator = mock(CallOriginator.class);
    BlockingDeque<Call> callQueue = new LinkedBlockingDeque<>();

    when(server.getId()).thenReturn(10L);
    when(location.getServers()).thenReturn(Collections.singletonList(server));
    when(campaign.getLocations()).thenReturn(new HashSet<>(Collections.singletonList(location)));
    when(dialerFacade.getAsteriskCallOriginators()).thenReturn(new HashMap<Long, CallOriginator>() {
      {
        put(10L, callOriginator);
      }
    });
    when(callConsumer.getCallQueue()).thenReturn(callQueue);

    pauseState.start();

    verify(callOriginator).addCallQueue(callQueue);
    verify(campaign).setState(CAMPAIGN_STATE.RUNNING);
  }
}
