package com.powerdialer.engine.dialer.endlistbehaviour;

import static org.junit.Assert.*;
import static org.mockito.Mockito.doReturn;

import com.powerdialer.engine.dialer.CampaignFilterStatsCalculator;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import com.powerdialer.TestUtils;
import com.powerdialer.engine.dialer.CampaignContext;
import com.powerdialer.engine.dialer.DialerFacade;
import com.powerdialer.model.Campaign;
import com.powerdialer.util.Constants;

/**
 * Test for EndListBehaviourFactory class
 */
public class EndListBehaviourFactoryTest {

  @Mock
  private Campaign campaignMock;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private DialerFacade dialerFacadeMock;

  @Mock
  private CampaignFilterStatsCalculator campaignFilterStatsCalculatorMock;

  private CampaignContext campaignContext;

  @Before
  public void init() throws Exception {
    TestUtils.setFinalStatic(StopAcceleratingBehaviour.class, "CHECK_PERIOD", 10L);
    MockitoAnnotations.initMocks(this);
  }

  /**
   * Test create STOP_ACCELERATING_RETRIES_AND_CALLBACKS behaviour
   * @throws Exception
   */
  @Test
  public void testCreateStopAcceleratingBehaviour() throws Exception {
    doReturn(Constants.END_LIST_BEHAVIOUR.STOP_ACCELERATING_RETRIES_AND_CALLBACKS).when(campaignMock).getEndListBehaviour();
    campaignContext = Mockito.spy(new CampaignContext(campaignMock, dialerFacadeMock, campaignFilterStatsCalculatorMock));
    EndListBehaviour behaviour = EndListBehaviourFactory.create(campaignContext);
    assertTrue(behaviour instanceof StopAcceleratingBehaviour);
  }

  /**
   * Test create STOP_DISCARDING_RETRIES_AND_CALLBACKS behaviour
   * @throws Exception
   */
  @Test
  public void testCreateStopDiscardingBehaviour() throws Exception {
    doReturn(Constants.END_LIST_BEHAVIOUR.STOP_DISCARDING_RETRIES_AND_CALLBACKS).when(campaignMock).getEndListBehaviour();
    campaignContext = Mockito.spy(new CampaignContext(campaignMock, dialerFacadeMock, campaignFilterStatsCalculatorMock));
    EndListBehaviour behaviour = EndListBehaviourFactory.create(campaignContext);
    assertTrue(behaviour instanceof StopDiscardingBehaviour);
  }

}
