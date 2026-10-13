package tests.dialer;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import com.powerdialer.engine.CallCleaner;
import com.powerdialer.engine.CallUpToDateKeeper;
import com.powerdialer.engine.acd.ACDEngine;
import com.powerdialer.engine.asterisk.CallerIdManager;
import com.powerdialer.engine.asterisk.ami.ManagerConnectionComposition;
import com.powerdialer.engine.asterisk.ami.ManagerConnectionDecorator;
import com.powerdialer.engine.dialer.CallAttemptPolicy;
import com.powerdialer.engine.dialer.CallConsumer;
import com.powerdialer.engine.dialer.CallOriginator;
import com.powerdialer.engine.dialer.CampaignContext;
import com.powerdialer.engine.dialer.DialerFacade;
import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.redis.CallEventPublisher;
import com.powerdialer.service.CDRService;
import com.powerdialer.service.CampaignResultService;
import com.powerdialer.util.Constants;
import com.powerdialer.util.LoggerDecorator;

@RunWith(MockitoJUnitRunner.class)
public class PauseCampaignOriginatorRegressionTest {

  private static final Long CAMPAIGN_ID = 34L;

  @Mock
  private LoggerDecorator loggerDecorator;

  @Mock
  private ManagerConnectionDecorator connectionDecorator;

  @Mock
  private ManagerConnectionComposition connectionComposition;

  @Mock
  private DialerFacade dialerFacade;

  @Mock
  private CallerIdManager callerIdManager;

  @Mock
  private CallCleaner callCleaner;

  @Mock
  private CampaignResultService campaignResultService;

  @Mock
  private CDRService cdrService;

  @Mock
  private CallEventPublisher callEventPublisher;

  @Mock
  private CallUpToDateKeeper callUpToDateKeeper;

  @Mock
  private ACDEngine acdEngine;

  @Mock
  private CampaignContext campaignContext;

  @Mock
  private CallConsumer callConsumer;

  private BlockingDeque<Call> pausedCallQueue;
  private CallOriginator callOriginator;

  @Before
  public void setUp() {
    pausedCallQueue = new LinkedBlockingDeque<>();

    when(connectionDecorator.getConnectionComposition()).thenReturn(connectionComposition);
    when(connectionComposition.getLoggerDecorator()).thenReturn(loggerDecorator);
    when(connectionComposition.getCallUpToDateKeeper()).thenReturn(callUpToDateKeeper);
    when(loggerDecorator.getLogger(CallOriginator.class)).thenReturn(loggerDecorator);

    Map<Long, CampaignContext> contexts = new HashMap<>();
    contexts.put(CAMPAIGN_ID, campaignContext);
    when(dialerFacade.getCampaignContexts()).thenReturn(contexts);
    when(campaignContext.getCallConsumer()).thenReturn(callConsumer);
    when(callConsumer.getCallQueue()).thenReturn(pausedCallQueue);

    callOriginator = new CallOriginator(connectionDecorator, dialerFacade, callerIdManager, callCleaner, campaignResultService);
  }

  @Test
  public void shouldNotOriginateOrPublishLscCompletedCallWhenCampaignIsPaused() {
    Call call = pausedCampaignCall();
    CallAttemptPolicy.incrementForOrigination(call);

    ReflectionTestUtils.invokeMethod(callOriginator, "processOrigination", "bulk-pause", call);

    assertThat(pausedCallQueue.peekFirst(), is(call));
    assertThat(call.getAttempts(), is(0));
    assertThat(call.getCallOriginator(), is((CallOriginator) null));
    verify(connectionDecorator, never()).originateCall(any(Call.class));
    verify(callUpToDateKeeper).unregisterCall(call);
    verifyNoInteractions(callEventPublisher, cdrService);
  }

  private Call pausedCampaignCall() {
    Campaign campaign = new Campaign();
    campaign.setId(CAMPAIGN_ID);
    campaign.setState(Constants.CAMPAIGN_STATE.PAUSED);
    campaign.setType(Constants.CAMPAIGN_TYPE.PROGRESSIVE_RELEASED);
    campaign.setColResultAttempts("attempts");
    campaign.setPhoneNumberColumns(Collections.emptyList());

    Call call = new Call();
    call.setId("dl-paused-lsc-call");
    call.setCampaign(campaign);
    call.setCalledNumber("5551000");
    call.setRawData(new JSONObject().put("attempts", 0));
    call.setCallOriginator(callOriginator);
    return call;
  }
}
