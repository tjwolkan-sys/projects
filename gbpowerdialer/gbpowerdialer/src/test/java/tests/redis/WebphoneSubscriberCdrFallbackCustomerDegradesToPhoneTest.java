package tests.redis;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.powermock.reflect.internal.WhiteboxImpl;

import com.powerdialer.engine.CustomerDncService;
import com.powerdialer.engine.DNCEngine;
import com.powerdialer.engine.asterisk.ami.ManagerConnectionComposition;
import com.powerdialer.engine.asterisk.ami.ManagerConnectionDecorator;
import com.powerdialer.model.CDR;
import com.powerdialer.model.CallResultCode;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.DNC;
import com.powerdialer.model.DNCPhoneNumber;
import com.powerdialer.model.ResultCode;
import com.powerdialer.model.TelephonyResultCode;
import com.powerdialer.model.User;
import com.powerdialer.redis.WebphoneSubscriber;
import com.powerdialer.redis.message.RedisMessage;
import com.powerdialer.service.CDRService;
import com.powerdialer.service.CampaignResultService;
import com.powerdialer.service.DNCPhoneNumberService;
import com.powerdialer.util.Constants;
import com.powerdialer.util.Constants.MSG_ACTION_TYPE;

/**
 * v1 sınırı: call bellekte yokken (CDR fallback) CUSTOMER scope PHONE'a
 * degrade olur — yalnız cdr.contactNumber DNC'ye eklenir, customer
 * genişletmesi yapılmaz (Redis subscriber thread'inde kampanya DB lookup'ı
 * yapılmaz; kalan numaraları delta-sync telafi eder).
 */
@RunWith(MockitoJUnitRunner.class)
public class WebphoneSubscriberCdrFallbackCustomerDegradesToPhoneTest {

  private static final Long USER_ID = 1L;
  private static final String CALL_PARENT_ID = "dl-callId";
  private static final String CONTACT_NUMBER = "05321112233";

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private ManagerConnectionDecorator connectionDecorator;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private ManagerConnectionComposition managerConnectionComposition;

  @Mock
  private RedisMessage redisMessage;

  @Mock
  private DNCPhoneNumberService dncPhoneNumberService;

  @Mock
  private DNCEngine dncEngine;

  @Mock
  private CustomerDncService customerDncService;

  @Mock
  private CDRService cdrService;

  @Mock
  private CampaignResultService campaignResultService;

  @InjectMocks
  private WebphoneSubscriber webphoneSubscriber;

  private CallResultCode callResultCode;
  private DNC dnc;
  private CDR cdr;

  @Before
  public void init() {
    dnc = new DNC();
    dnc.setId(1L);

    Campaign campaign = new Campaign();
    campaign.setDnc(dnc);
    campaign.setColResultCustomerNo("customer_no");

    cdr = new CDR();
    cdr.setId(100L);
    cdr.setInteractionId(CALL_PARENT_ID);
    cdr.setCampaign(campaign);
    cdr.setContactId(612436L);
    cdr.setContactNumber(CONTACT_NUMBER);
    TelephonyResultCode normalClearing = new TelephonyResultCode();
    normalClearing.setId(10L);
    normalClearing.setCode(Constants.TELEPHONY_RC_NORMAL_CLEARING);
    cdr.setTelephonyRC(normalClearing);
    User agent = new User();
    agent.setId(USER_ID);
    cdr.setAssignedUser(agent);
    cdr.setCallAnswerTime(new Date());
    cdr.setCallHandlingStartTime(new Date());
    cdr.setCallEndTime(new Date());

    ResultCode resultCode = new ResultCode();
    resultCode.setId(50L);
    resultCode.setDncIdentifier(true);
    resultCode.setFinalStatus(true);

    callResultCode = new CallResultCode();
    callResultCode.setParentId(CALL_PARENT_ID);
    callResultCode.setUserId(USER_ID);
    callResultCode.setResultCode(resultCode);
    callResultCode.setDncScope(Constants.DNC_SCOPE.CUSTOMER);

    when(connectionDecorator.getCalls().getIfPresent(CALL_PARENT_ID)).thenReturn(null);
    when(connectionDecorator.getAwaitingUserResultCodeCalls().getIfPresent(CALL_PARENT_ID)).thenReturn(null);
    when(connectionDecorator.getRecoverableConnectionLossCalls().getIfPresent(CALL_PARENT_ID)).thenReturn(null);
    Map<String, ManagerConnectionDecorator> openedConnections = new HashMap<>();
    openedConnections.put("srv", connectionDecorator);
    when(managerConnectionComposition.getOpenedConnections()).thenReturn(openedConnections);
    when(managerConnectionComposition.getCdrService()).thenReturn(cdrService);
    when(cdrService.getByInteractionId(CALL_PARENT_ID)).thenReturn(cdr);

    when(redisMessage.getType()).thenReturn(MSG_ACTION_TYPE.ADD);
    when(redisMessage.getSerializableObject()).thenReturn(callResultCode);

    when(dncPhoneNumberService.existsByPhoneNumberAndDncId(CONTACT_NUMBER, 1L)).thenReturn(false);
    when(dncPhoneNumberService.create(CONTACT_NUMBER, Constants.REASON.WRAPUP_CODE, dnc)).thenReturn(new DNCPhoneNumber());
  }

  @Test
  public void cdrFallbackWithCustomerScopeDegradesToSingleContactNumber() throws Exception {
    WhiteboxImpl.invokeMethod(webphoneSubscriber, "handleResultCodeEntityMessage", redisMessage);

    verify(dncPhoneNumberService).create(CONTACT_NUMBER, Constants.REASON.WRAPUP_CODE, dnc);
    verify(dncEngine).updatePhoneNumberInDnc(any(DNCPhoneNumber.class));
    verify(dncEngine).updateDnc(1L);
    verify(customerDncService, never()).blockByCustomerScope(any(), any(), any());
    verify(customerDncService, never()).blockByCustomerNo(anyString(), any(), any());
  }
}
