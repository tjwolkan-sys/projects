package com.powerdialer.engine.dialer;

import com.powerdialer.engine.predictive.beta.processor.PredictiveBetaCallManager;
import com.powerdialer.engine.predictive.gamma.processor.PredictiveGammaCallManager;
import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.util.Constants;
import com.powerdialer.util.LoggerDecorator;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.powermock.reflect.Whitebox;

import java.util.concurrent.BlockingDeque;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class CallConsumerGammaBudgetTest {

  @Mock private CampaignContext campaignContext;
  @Mock private DialerFacade dialerFacade;
  @Mock private CallProducer callProducer;
  @Mock private Campaign campaign;
  @Mock private LoggerDecorator loggerDecorator;
  @Mock private LoggerDecorator logger;
  @Mock private PredictiveBetaCallManager predictiveBetaCallManager;
  @Mock private PredictiveGammaCallManager predictiveGammaCallManager;

  private BlockingQueue<Call> dataQueue;
  private CallConsumer callConsumer;

  private static final long CID = 1L;

  @Before
  public void setUp() {
    dataQueue = new LinkedBlockingDeque<>();

    when(campaignContext.getCampaign()).thenReturn(campaign);
    when(campaignContext.getCallProducer()).thenReturn(callProducer);
    when(campaignContext.getDialerFacade()).thenReturn(dialerFacade);
    when(callProducer.getDataQueue()).thenReturn(dataQueue);
    when(dialerFacade.getLoggerDecorator()).thenReturn(loggerDecorator);
    when(loggerDecorator.getLogger(CallConsumer.class)).thenReturn(logger);
    when(dialerFacade.getPredictiveBetaCallManager()).thenReturn(predictiveBetaCallManager);
    when(dialerFacade.getPredictiveGammaCallManager()).thenReturn(predictiveGammaCallManager);
    when(campaign.getId()).thenReturn(CID);
    when(campaign.getConcurrentLines()).thenReturn(10);

    when(predictiveGammaCallManager.getAnswerRateCost(any(Call.class), eq(CID))).thenAnswer(invocation -> {
      Call call = invocation.getArgument(0);
      if (Constants.CALL_TYPES.RETRY.equals(call.getCallType())) return 0.2;
      if (Constants.CALL_TYPES.FRESH.equals(call.getCallType())) return 0.5;
      if (Constants.CALL_TYPES.CALLBACK.equals(call.getCallType())) return 0.8;
      return 1.0;
    });

    callConsumer = new CallConsumer(campaignContext);
  }

  @Test
  public void drainWithBudget_decrementsBudgetByEachQueuedCallType() throws Exception {
    dataQueue.offer(call(Constants.CALL_TYPES.RETRY));
    dataQueue.offer(call(Constants.CALL_TYPES.FRESH));
    dataQueue.offer(call(Constants.CALL_TYPES.FRESH));
    dataQueue.offer(call(Constants.CALL_TYPES.CALLBACK));

    int drained = Whitebox.invokeMethod(callConsumer, "drainWithBudget", 1.2d, 10, 10);

    BlockingDeque<Call> callQueue = callConsumer.getCallQueue();
    assertEquals(3, drained);
    assertEquals(3, callQueue.size());
    assertEquals(1, dataQueue.size());
    assertEquals(Constants.CALL_TYPES.CALLBACK, dataQueue.peek().getCallType());
  }

  private Call call(Constants.CALL_TYPES callType) {
    Call call = new Call();
    call.setCallType(callType);
    return call;
  }
}
