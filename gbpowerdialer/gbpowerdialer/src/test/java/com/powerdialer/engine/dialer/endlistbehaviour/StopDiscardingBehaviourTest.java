package com.powerdialer.engine.dialer.endlistbehaviour;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.sql.SQLException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.powerdialer.engine.dialer.CampaignFilterStatsCalculator;
import com.powerdialer.model.CampaignFiltersStats;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import com.powerdialer.CountDownLatchAnswer;
import com.powerdialer.TestUtils;
import com.powerdialer.engine.dialer.CampaignContext;
import com.powerdialer.engine.dialer.DialerFacade;
import com.powerdialer.model.Campaign;
import com.powerdialer.util.Constants.CAMPAIGN_ACTION;
import com.powerdialer.util.Constants.END_LIST_BEHAVIOUR;

/**
 * Test for class
 */
public class StopDiscardingBehaviourTest {

  @Mock
  private Campaign campaignMock;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private DialerFacade dialerFacadeMock;

  @Mock
  private CampaignFilterStatsCalculator campaignFilterStatsCalculatorMock;

  private CampaignContext campaignContext;

  @Before
  public void init() throws Exception {
    TestUtils.setFinalStatic(StopDiscardingBehaviour.class, "CHECK_PERIOD", 10L);
    MockitoAnnotations.initMocks(this);
    doReturn(1L).when(campaignMock).getId();
    doReturn("oracle").when(campaignMock).getDbConnectionString();
    doReturn("retry_date").when(campaignMock).getColResultRetryDate();
    doReturn("complete_date").when(campaignMock).getColResultCompleteDate();
    doReturn("callback_date").when(campaignMock).getColResultCallbackDate();
    doReturn(END_LIST_BEHAVIOUR.STOP_DISCARDING_RETRIES_AND_CALLBACKS).when(campaignMock).getEndListBehaviour();
    campaignContext = Mockito.spy(new CampaignContext(campaignMock, dialerFacadeMock, campaignFilterStatsCalculatorMock));
  }

  /**
   * Test successful stop campaign on first check
   * @throws InterruptedException
   */
  @Test
  public void testProcessEndListSuccessfulStop() throws Exception {
    EndListBehaviour discardBehaviour = EndListBehaviourFactory.create(campaignContext);
    CountDownLatch countDownLatch = new CountDownLatch(1);
    Mockito.doAnswer(new CountDownLatchAnswer(countDownLatch, true)).when(dialerFacadeMock).shutdownCampaign(eq(1L), eq(CAMPAIGN_ACTION.STOP_REGULAR));
    discardBehaviour.processEndList();
    assertTrue(countDownLatch.await(1L, TimeUnit.SECONDS));
    verify(dialerFacadeMock.getCampaignDbOperations()).setResultColumnToValue(any(), any(), any(), any(), any());
    verify(dialerFacadeMock).shutdownCampaign(campaignMock.getId(), CAMPAIGN_ACTION.STOP_REGULAR);
  }

  /**
   * Test successful stop campaign on second check
   * @throws InterruptedException
   */
  @Test
  public void testProcessEndListSuccessfulStop2() throws InterruptedException, SQLException {
    doReturn(true).doReturn(false).when(campaignMock).hasCallsInProgress();
    EndListBehaviour discardBehaviour = EndListBehaviourFactory.create(campaignContext);
    CountDownLatch countDownLatch = new CountDownLatch(1);
    Mockito.doAnswer(new CountDownLatchAnswer(countDownLatch, true)).when(dialerFacadeMock).shutdownCampaign(eq(1L), eq(CAMPAIGN_ACTION.STOP_REGULAR));
    discardBehaviour.processEndList();
    assertTrue(countDownLatch.await(2L, TimeUnit.SECONDS));
    verify(dialerFacadeMock.getCampaignDbOperations()).setResultColumnToValue(any(), any(), any(), any(), any());
    verify(dialerFacadeMock).shutdownCampaign(campaignMock.getId(), CAMPAIGN_ACTION.STOP_REGULAR);
    verify(campaignMock, times(2)).hasCallsInProgress();
  }

}
