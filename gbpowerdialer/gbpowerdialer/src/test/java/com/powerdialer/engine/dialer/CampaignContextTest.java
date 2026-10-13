package com.powerdialer.engine.dialer;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.ExecutorService;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.powerdialer.engine.dialer.state.CampaignState;
import com.powerdialer.model.Campaign;
import com.powerdialer.util.Constants.CAMPAIGN_ACTION;
import com.powerdialer.util.Constants.END_LIST_BEHAVIOUR;
import com.powerdialer.util.LoggerDecorator;
import org.powermock.reflect.Whitebox;

/**
 * Tests for CampaignContext class
 */
@RunWith(MockitoJUnitRunner.class)
public class CampaignContextTest {

  @Mock
  private DialerFacade dialerFacade;

  @Mock
  private ExecutorService campaignStopExecutor;

  @Mock
  private CampaignState state;

  @Mock
  private CampaignFilterStatsCalculator campaignStatsCalculator;

  private Campaign campaign = new Campaign();
  private CampaignContext campaignContext;

  @Before
  public void init() {
    when(dialerFacade.getLoggerDecorator()).thenReturn(new LoggerDecorator());
    campaign.setEndListBehaviour(END_LIST_BEHAVIOUR.CONTINUE);
    campaign.setAmdEnabled(false);
    campaign.setRingTimeout(0);
    campaign.setCacheLimit(0);
    campaign.setConcurrentLines(0);
    campaignContext = new CampaignContext(campaign, dialerFacade, campaignStatsCalculator);
    Whitebox.setInternalState(campaignContext, "campaignStopExecutor", campaignStopExecutor);
  }

  @Test
  public void constructorShouldSetInstanceVariables() {
    assertSame(campaign, campaignContext.getCampaign());
    assertSame(dialerFacade, campaignContext.getDialerFacade());
    assertNotNull(campaignContext.getStartState());
    assertNotNull(campaignContext.getPauseState());
    assertNotNull(campaignContext.getStopState());
    assertNotNull(campaignContext.getCallConsumer());
  }

  @Test
  public void shouldSetCampaignState() {
    campaignContext.setCampaignState(state);
    assertSame(state, Whitebox.getInternalState(campaignContext, "campaignState"));
  }

  @Test
  public void shouldStartCampaignState() {
    campaignContext.setCampaignState(state);
    campaignContext.start();
    verify(state).start();
  }

  @Test
  public void shouldPauseCampaignState() {
    campaignContext.setCampaignState(state);
    campaignContext.pause();
    verify(state).pause();
  }

  @Test
  public void shouldStopCampaignState() {
    campaignContext.setCampaignState(state);
    campaignContext.stop(CAMPAIGN_ACTION.STOP_FORCEFUL);
    verify(state).stop(CAMPAIGN_ACTION.STOP_FORCEFUL);
  }

  @Test
  public void shouldReloadCampaignState() {
    campaignContext.setCampaignState(state);
    campaignContext.reload();
    verify(state).reload();
  }

  @Test
  public void shouldResetCampaignState() {
    campaignContext.setCampaignState(state);
    campaignContext.reset();
    verify(state).reset();
  }

  @Test
  public void shouldStartCampaignStopJob() {
    campaignContext.startCampaignStopJob(CAMPAIGN_ACTION.STOP_FORCEFUL);
    assertNotNull(campaign.getCampaignStopJob());
  }

}
