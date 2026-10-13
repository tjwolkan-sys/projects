package com.powerdialer.engine.dialer.state;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.powerdialer.engine.DNCEngine;
import com.powerdialer.engine.LSCEngine;
import com.powerdialer.engine.asterisk.ami.ManagerConnectionDecorator;
import com.powerdialer.engine.db.CampaignDbOperations;
import com.powerdialer.engine.dialer.*;
import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.Location;
import com.powerdialer.model.Server;
import com.powerdialer.service.CampaignMementoService;
import com.powerdialer.util.Constants.CAMPAIGN_ACTION;
import com.powerdialer.util.LoggerDecorator;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.powermock.reflect.Whitebox;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.*;

/**
 * Tests for StartState class
 */
@RunWith(MockitoJUnitRunner.class)
public class StartStateTest {

  @Mock
  private CampaignContext campaignContext;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private DialerFacade dialerFacade;

  @Mock
  private LoggerDecorator loggerDecorator;

  @Mock
  private Campaign campaign;

  @Mock
  private CampaignMementoService campaignMementoService;

  private CampaignStateTemplate startState;

  @Before
  public void init() {
    when(loggerDecorator.getLogger(StartState.class)).thenReturn(loggerDecorator);
    when(loggerDecorator.getLogger(CampaignStateTemplate.class)).thenReturn(loggerDecorator);
    when(dialerFacade.getLoggerDecorator()).thenReturn(loggerDecorator);
    when(dialerFacade.getCampaignMementoService()).thenReturn(campaignMementoService);
    when(dialerFacade.getDncEngine()).thenReturn(mock(DNCEngine.class));
    when(dialerFacade.getLscEngine()).thenReturn(mock(LSCEngine.class));
    when(dialerFacade.getCampaignDbOperations()).thenReturn(mock(CampaignDbOperations.class));
    when(campaignContext.getDialerFacade()).thenReturn(dialerFacade);
    when(campaignContext.getCallProducer()).thenReturn(mock(CallProducer.class));
    when(campaignContext.getCallConsumer()).thenReturn(mock(CallConsumer.class));
    when(campaignContext.getCampaign()).thenReturn(campaign);
    startState = spy(new StartState(campaignContext));
  }

  @Test
  public void constructorShouldSetInstanceVariables() {
    assertSame(campaignContext, startState.campaignContext);
  }

  @Test
  public void shouldPauseCampaign() {
    Location location = mock(Location.class);
    Server server = mock(Server.class);
    CallOriginator callOriginator = mock(CallOriginator.class);
    BlockingDeque<Call> callQueue = new LinkedBlockingDeque<>();
    when(server.getId()).thenReturn(0L);
    when(location.getServers()).thenReturn(Arrays.asList(server));
    when(campaign.getLocations()).thenReturn(new HashSet() {
      {
        add(location);
      }
    });
    when(dialerFacade.getAsteriskCallOriginators()).thenReturn(new HashMap() {
      {
        put(0L, callOriginator);
      }
    });
    when(campaignContext.getCallConsumer().getCallQueue()).thenReturn(callQueue);

    ScheduledFuture scheduledFuture = mock(ScheduledFuture.class);
    Whitebox.setInternalState(campaignContext.getCallProducer(), "scheduledFuture", scheduledFuture);
    Whitebox.setInternalState(campaignContext.getCallConsumer(), "scheduledFuture", scheduledFuture);
    startState.pause();
    verify(campaignContext).setCampaignState(campaignContext.getPauseState());
    verify(callOriginator).removeCallQueue(callQueue);
  }

  @Test
  public void shouldStopCampaignForcefullyRemovingCallQueuesAndCalls() {
    Location location = mock(Location.class);
    CallOriginator callOriginator = mock(CallOriginator.class);
    when(location.getServers()).thenReturn(Arrays.asList(mock(Server.class)));
    when(campaign.getLocations()).thenReturn(new HashSet() {
      {
        add(location);
      }
    });
    when(dialerFacade.getAsteriskCallOriginators()).thenReturn(new HashMap() {
      {
        put(0L, callOriginator);
      }
    });

    ScheduledExecutorService executorService = mock(ScheduledExecutorService.class);
    Whitebox.setInternalState(campaignContext.getCallProducer(), "scheduledExecutorService", executorService);
    Whitebox.setInternalState(campaignContext.getCallConsumer(), "scheduledExecutorService", executorService);

    Call call = spy(Call.class);
    call.setId("dl-2910-asdf-ds12");
    Cache<String, Call> originatedCalls = CacheBuilder.newBuilder().build();
    originatedCalls.put(call.getId(), call);
    when(campaign.getOriginatedCalls()).thenReturn(originatedCalls);

    CallOriginator originator = mock(CallOriginator.class);
    lenient().when(call.getCallOriginator()).thenReturn(originator);
    lenient().when(originator.getConnectionDecorator()).thenReturn(mock(ManagerConnectionDecorator.class));

    startState.stop(CAMPAIGN_ACTION.STOP_FORCEFUL);

    verify(campaignContext).setCampaignState(campaignContext.getStopState());
    verify(callOriginator).removeCallQueue(campaignContext.getCallConsumer().getCallQueue());
    verify(campaignContext).startCampaignStopJob(CAMPAIGN_ACTION.STOP_FORCEFUL);
  }

  @Test
  public void shouldReloadCampaign() {
    startState.reload();
    verify(campaignContext.getCallProducer()).setRowFetchedFlagsToNull();
    verify(campaignContext.getCallConsumer()).setRowFetchedFlagsToNull();
  }

  @Test
  public void shouldResetCampaign() {
    startState.reset();
    verify(campaign).reset();
    verify(campaignMementoService).resetCampaignMemento(campaign.getId());
  }

}
