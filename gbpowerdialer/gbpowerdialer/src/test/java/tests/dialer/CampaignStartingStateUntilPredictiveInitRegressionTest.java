package tests.dialer;

import static org.junit.Assert.fail;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.powerdialer.engine.DNCEngine;
import com.powerdialer.engine.LSCEngine;
import com.powerdialer.engine.dialer.CallConsumer;
import com.powerdialer.engine.dialer.CallProducer;
import com.powerdialer.engine.dialer.CampaignContext;
import com.powerdialer.engine.dialer.CampaignRealTimeRingingManager;
import com.powerdialer.engine.dialer.DialerFacade;
import com.powerdialer.engine.dialer.state.CampaignStateTemplate;
import com.powerdialer.engine.dialer.state.PauseState;
import com.powerdialer.engine.predictive.gamma.processor.PredictiveGammaCallManager;
import com.powerdialer.model.Campaign;
import com.powerdialer.util.Constants;
import com.powerdialer.util.Constants.CAMPAIGN_STATE;
import com.powerdialer.util.LoggerDecorator;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * FIX-333607: campaign, predictive manager init'i bitene kadar {@code STARTING} görünür.
 *
 * <p>Eskiden {@code CampaignStateTemplate.start()} campaign'i ilk adımda {@code RUNNING} yapıyor,
 * GAMMA startup sorguları sonra çalışıyordu. 02.10.2026'da C71 ekranda 9 dakika {@code RUNNING}
 * göründü ama producer/consumer başlamadığı için hiç arama çıkarmadı. Init hata verse bile son durum
 * eskisi gibi {@code RUNNING} olur ki regular stop çalışmaya devam etsin.</p>
 */
@RunWith(MockitoJUnitRunner.class)
public class CampaignStartingStateUntilPredictiveInitRegressionTest {

  @Mock private CampaignContext campaignContext;
  @Mock private DialerFacade dialerFacade;
  @Mock private LoggerDecorator loggerDecorator;
  @Mock private Campaign campaign;
  @Mock private PredictiveGammaCallManager gammaCallManager;
  @Mock private CallProducer callProducer;
  @Mock private CallConsumer callConsumer;

  private PauseState pauseState;

  @Before
  public void setUp() {
    when(dialerFacade.getLoggerDecorator()).thenReturn(loggerDecorator);
    when(loggerDecorator.getLogger(PauseState.class)).thenReturn(loggerDecorator);
    when(loggerDecorator.getLogger(CampaignStateTemplate.class)).thenReturn(loggerDecorator);
    when(dialerFacade.getDncEngine()).thenReturn(mock(DNCEngine.class));
    when(dialerFacade.getLscEngine()).thenReturn(mock(LSCEngine.class));
    when(dialerFacade.getCampaignRealTimeRingingManager()).thenReturn(mock(CampaignRealTimeRingingManager.class));
    when(dialerFacade.getPredictiveGammaCallManager()).thenReturn(gammaCallManager);
    when(campaignContext.getDialerFacade()).thenReturn(dialerFacade);
    when(campaignContext.getCampaign()).thenReturn(campaign);
    when(campaign.getLocations()).thenReturn(Collections.emptySet());
    when(campaign.getType()).thenReturn(Constants.CAMPAIGN_TYPE.PREDICTIVE);
    when(campaign.getPredictiveStrategy()).thenReturn(Constants.PREDICTIVE_STRATEGY.GAMMA);
    pauseState = new PauseState(campaignContext);
  }

  @Test
  public void campaignBecomesRunningOnlyAfterGammaInitAndBeforeProducerStarts() {
    when(campaignContext.getCallProducer()).thenReturn(callProducer);
    when(campaignContext.getCallConsumer()).thenReturn(callConsumer);

    pauseState.start();

    InOrder order = inOrder(gammaCallManager, campaign, callProducer, callConsumer);
    order.verify(gammaCallManager).onCampaignStart(campaign);
    order.verify(campaign).setState(CAMPAIGN_STATE.RUNNING);
    order.verify(callProducer).start();
    order.verify(callConsumer).start();
  }

  @Test
  public void failedPredictiveInitStillLeavesCampaignRunningAsBefore() {
    doThrow(new IllegalStateException("init failure")).when(gammaCallManager).onCampaignStart(campaign);

    try {
      pauseState.start();
      fail("Init hatası çağırana iletilmeli");
    } catch (IllegalStateException expected) {
      // beklenen
    }

    verify(campaign).setState(CAMPAIGN_STATE.RUNNING);
  }
}
