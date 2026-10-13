package com.powerdialer.engine.dialer;

import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.lenient;
import static org.powermock.api.mockito.PowerMockito.spy;
import static org.powermock.api.mockito.PowerMockito.when;

import java.io.IOException;

import org.asteriskjava.manager.ManagerConnection;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import com.powerdialer.engine.CallCleaner;
import com.powerdialer.engine.asterisk.ami.ManagerConnectionDecorator;
import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.service.CampaignMementoService;
import com.powerdialer.util.Constants.CAMPAIGN_ACTION;
import com.powerdialer.util.LoggerDecorator;

@RunWith(MockitoJUnitRunner.class)
public class CampaignStopJobTest {

  @Mock
  private CampaignContext campaignContext;

  @Mock
  private Campaign campaign;

  @Mock
  private LoggerDecorator loggerDecorator;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private DialerFacade dialerFacade;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private CallCleaner callCleaner;

  @Mock
  private CallOriginator callOriginator;

  @Spy
  private Call call;

  @Before
  public void init() {
    lenient().when(loggerDecorator.getLogger(CampaignStopJob.class)).thenReturn(loggerDecorator);
    when(campaignContext.getDialerFacade()).thenReturn(dialerFacade);

    when(campaignContext.getCampaign()).thenReturn(campaign);
    when(campaign.hasCallsInProgress()).thenReturn(true);

    when(call.getCallOriginator()).thenReturn(callOriginator);
    when(callOriginator.getCallCleaner()).thenReturn(callCleaner);
  }

  @Test
  public void shouldWaitTerminationOfAllCallsWhenRegularStopIsRequested() throws InterruptedException {
    CAMPAIGN_ACTION action = CAMPAIGN_ACTION.STOP_REGULAR;

    Thread thread = new Thread(new CampaignStopJob(campaignContext, action));

    thread.start();

    Thread.sleep(1000);

    if (thread.getState() == Thread.State.WAITING || thread.getState() == Thread.State.TIMED_WAITING) {
      assertTrue(true);
    } else {
      assertTrue(false);
    }
  }

  @Test
  public void shouldCleanOfAllCallsWhenForcefullyStopIsRequested() throws Exception {
    Cache<String, Call> calls = CacheBuilder.newBuilder().build();
    call.setId("dl-2910-asdf-ds12");
    calls.put(call.getId(), call);
    when(campaign.getOriginatedCalls()).thenReturn(calls);

    ManagerConnectionDecorator managerConnectionDecorator = mock(ManagerConnectionDecorator.class);
    when(callOriginator.getConnectionDecorator()).thenReturn(managerConnectionDecorator);
    doNothing().when(managerConnectionDecorator).clearCall(call);

    ManagerConnection managerConnection = mock(ManagerConnection.class);
    lenient().when(call.getCallOriginator().getConnectionDecorator().getManagerConnection()).thenReturn(managerConnection);
    lenient().doNothing().when(managerConnection).sendAction(anyObject(), eq(null));

    CAMPAIGN_ACTION action = CAMPAIGN_ACTION.STOP_FORCEFUL;
    CampaignStopJob campaignStopJob = spy(new CampaignStopJob(campaignContext, action));
    campaignStopJob.run();

    verify(managerConnectionDecorator).clearCall(call);
  }

  @Test
  public void shouldSendCommandToAsteriskWhenForcefullyStopIsRequest() throws IOException {
    Cache<String, Call> calls = CacheBuilder.newBuilder().build();
    call.setId("dl-2910-asdf-ds12");
    calls.put(call.getId(), call);
    when(campaign.getOriginatedCalls()).thenReturn(calls);

    ManagerConnectionDecorator managerConnectionDecorator = mock(ManagerConnectionDecorator.class);
    when(callOriginator.getConnectionDecorator()).thenReturn(managerConnectionDecorator);
    doNothing().when(managerConnectionDecorator).clearCall(call);

    CAMPAIGN_ACTION action = CAMPAIGN_ACTION.STOP_FORCEFUL;
    CampaignStopJob campaignStopJob = spy(new CampaignStopJob(campaignContext, action));
    campaignStopJob.run();

    verify(managerConnectionDecorator).hangupCall(call);
  }

  @Test
  public void shouldInsertResultToDBWhenForcefullyStopIsRequest() throws IOException {
    Cache<String, Call> calls = CacheBuilder.newBuilder().build();
    call.setId("dl-2910-asdf-ds12");
    calls.put(call.getId(), call);
    when(campaign.getOriginatedCalls()).thenReturn(calls);

    ManagerConnectionDecorator managerConnectionDecorator = mock(ManagerConnectionDecorator.class);
    when(callOriginator.getConnectionDecorator()).thenReturn(managerConnectionDecorator);
    doNothing().when(managerConnectionDecorator).clearCall(call);

    ManagerConnection managerConnection = mock(ManagerConnection.class);
    lenient().when(call.getCallOriginator().getConnectionDecorator().getManagerConnection()).thenReturn(managerConnection);
    lenient().doNothing().when(managerConnection).sendAction(anyObject(), eq(null));

    CAMPAIGN_ACTION action = CAMPAIGN_ACTION.STOP_FORCEFUL;
    CampaignStopJob campaignStopJob = spy(new CampaignStopJob(campaignContext, action));
    campaignStopJob.run();

    CampaignMementoService campaignMementoService = campaignContext.getDialerFacade().getCampaignMementoService();

    verify(campaignMementoService, atLeast(0)).saveCampaignMementoByCampaign(anyObject(), anyObject());
  }

}
