package tests.ami;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import com.powerdialer.engine.CallCleaner;
import com.powerdialer.engine.asterisk.CallerIdManager;
import com.powerdialer.engine.asterisk.ami.ManagerConnectionComposition;
import com.powerdialer.engine.asterisk.ami.ManagerConnectionDecorator;
import com.powerdialer.engine.dialer.CallOriginator;
import com.powerdialer.engine.dialer.DialerFacade;
import com.powerdialer.model.CDR;
import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.PhoneNumberColumn;
import com.powerdialer.model.Server;
import com.powerdialer.redis.CallEventPublisher;
import com.powerdialer.service.CDRService;
import com.powerdialer.service.CampaignResultService;
import com.powerdialer.util.Constants;
import com.powerdialer.util.LoggerDecorator;

@RunWith(MockitoJUnitRunner.class)
public class InitialCdrOriginationRegressionTest {

  private static final String CALL_ID = "dl-initial-cdr-origination";

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

  private CallOriginator callOriginator;

  @Before
  public void setUp() {
    when(connectionDecorator.getConnectionComposition()).thenReturn(connectionComposition);
    when(connectionComposition.getLoggerDecorator()).thenReturn(loggerDecorator);
    when(loggerDecorator.getLogger(CallOriginator.class)).thenReturn(loggerDecorator);
    when(dialerFacade.getCdrService()).thenReturn(cdrService);
    when(dialerFacade.getCallEventPublisher()).thenReturn(callEventPublisher);
    when(connectionDecorator.getServer()).thenReturn(server());

    callOriginator = new CallOriginator(connectionDecorator, dialerFacade, callerIdManager, callCleaner, campaignResultService);
  }

  @Test
  public void shouldSaveInitialCdrWhenOriginationSucceedsWithoutReadingCallCache() {
    Call call = callReadyForOrigination();
    when(connectionDecorator.originateCall(call)).thenReturn(true);

    ReflectionTestUtils.invokeMethod(callOriginator, "processOrigination", "bulk-1", call);

    ArgumentCaptor<CDR> cdrCaptor = ArgumentCaptor.forClass(CDR.class);
    verify(cdrService).saveOrUpdate(cdrCaptor.capture());
    CDR savedCdr = cdrCaptor.getValue();

    assertThat(savedCdr.getInteractionId(), is(CALL_ID));
    assertThat(savedCdr.getCallDialTime(), is(notNullValue()));
    assertThat(savedCdr.getCallEndTime(), is(notNullValue()));
    assertThat(savedCdr.getContactNumberColumn(), is("phone1"));
    assertThat(savedCdr.getContactNumberOrder(), is(0));
    verify(connectionDecorator, never()).getCalls();
  }

  @Test
  public void shouldNotSaveInitialCdrWhenOriginationFailsBeforeSubmittingAction() {
    Call call = callReadyForOrigination();
    when(connectionDecorator.originateCall(call)).thenReturn(false);

    ReflectionTestUtils.invokeMethod(callOriginator, "processOrigination", "bulk-1", call);

    verify(cdrService, never()).saveOrUpdate(any(CDR.class));
  }

  private Call callReadyForOrigination() {
    Campaign campaign = new Campaign();
    campaign.setId(88L);
    campaign.setType(Constants.CAMPAIGN_TYPE.PROGRESSIVE_RELEASED);
    campaign.setColResultRcSequence("rc_sequence");
    PhoneNumberColumn phoneNumberColumn = new PhoneNumberColumn();
    phoneNumberColumn.setColNumber("phone1");
    campaign.getPhoneNumberColumns().add(phoneNumberColumn);

    Call call = new Call();
    call.setId(CALL_ID);
    call.setCampaign(campaign);
    call.setCalledNumber("5551000");
    call.setRawData(new JSONObject().put("phone1", "5551000"));
    call.setResultCodeSequence(0);
    call.getCdr().setInteractionId(CALL_ID);
    return call;
  }

  private Server server() {
    Server server = new Server();
    server.setIp("127.0.0.1");
    server.setName("asterisk-1");
    return server;
  }
}
