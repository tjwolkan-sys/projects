package tests.redis;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONObject;
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
import com.powerdialer.model.Call;
import com.powerdialer.model.CallResultCode;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.DNC;
import com.powerdialer.model.DNCPhoneNumber;
import com.powerdialer.model.PhoneNumberColumn;
import com.powerdialer.model.ResultCode;
import com.powerdialer.model.TelephonyResultCode;
import com.powerdialer.model.User;
import com.powerdialer.redis.WebphoneSubscriber;
import com.powerdialer.redis.message.RedisMessage;
import com.powerdialer.service.DNCPhoneNumberService;
import com.powerdialer.util.Constants;
import com.powerdialer.util.Constants.MSG_ACTION_TYPE;

/**
 * Geriye uyumluluk kilidi: dncScope alanı YOK (eski gbwebphone) veya PHONE
 * geldiğinde wrap-up DNC davranışı bugünküyle birebir aynıdır — yalnız
 * aranan numara DNC'ye eklenir, customer-scope servisi ve hot-drop devreye
 * girmez.
 */
@RunWith(MockitoJUnitRunner.class)
public class WebphoneSubscriberPhoneScopeUnchangedRegressionTest {

  private static final Long USER_ID = 1L;
  private static final String CALL_PARENT_ID = "dl-callId";
  private static final String CALLED_NUMBER = "05321112233";

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

  @InjectMocks
  private WebphoneSubscriber webphoneSubscriber;

  private Call call;
  private DNC dnc;
  private CallResultCode callResultCode;

  @Before
  public void init() {
    dnc = new DNC();
    dnc.setId(1L);

    PhoneNumberColumn numberColumn = new PhoneNumberColumn();
    numberColumn.setEnabled(true);

    Campaign campaign = new Campaign();
    campaign.setPhoneNumberColumns(Arrays.asList(numberColumn));
    campaign.setColResultDNCNumbers("dnc_numbers");
    campaign.setDnc(dnc);

    User user = new User();
    user.setId(USER_ID);

    call = new Call();
    call.setId(CALL_PARENT_ID);
    call.setCampaign(campaign);
    call.setTelephonyResultCode(new TelephonyResultCode());
    call.setCalledNumber(CALLED_NUMBER);
    call.setRawData(new JSONObject());
    call.setUser(user);

    ResultCode resultCode = new ResultCode();
    resultCode.setId(50L);
    resultCode.setDncIdentifier(true);

    callResultCode = new CallResultCode();
    callResultCode.setParentId(CALL_PARENT_ID);
    callResultCode.setUserId(USER_ID);
    callResultCode.setResultCode(resultCode);

    when(connectionDecorator.getCalls().getIfPresent(CALL_PARENT_ID)).thenReturn(call);
    Map<String, ManagerConnectionDecorator> openedConnections = new HashMap<>();
    openedConnections.put("srv", connectionDecorator);
    when(managerConnectionComposition.getOpenedConnections()).thenReturn(openedConnections);

    when(redisMessage.getType()).thenReturn(MSG_ACTION_TYPE.ADD);
    when(redisMessage.getSerializableObject()).thenReturn(callResultCode);

    when(dncPhoneNumberService.existsByPhoneNumberAndDncId(CALLED_NUMBER, 1L)).thenReturn(false);
    when(dncPhoneNumberService.create(CALLED_NUMBER, Constants.REASON.WRAPUP_CODE, dnc)).thenReturn(new DNCPhoneNumber());
  }

  @Test
  public void missingScopeFollowsExistingSingleNumberPath() throws Exception {
    callResultCode.setDncScope(null);

    WhiteboxImpl.invokeMethod(webphoneSubscriber, "handleResultCodeEntityMessage", redisMessage);

    assertExistingPhonePathExecuted();
  }

  @Test
  public void phoneScopeFollowsExistingSingleNumberPath() throws Exception {
    callResultCode.setDncScope(Constants.DNC_SCOPE.PHONE);

    WhiteboxImpl.invokeMethod(webphoneSubscriber, "handleResultCodeEntityMessage", redisMessage);

    assertExistingPhonePathExecuted();
  }

  private void assertExistingPhonePathExecuted() {
    assertTrue("Aranan numara call DNCNumbers setine eklenmeli", call.getDNCNumbers().contains(CALLED_NUMBER));
    verify(dncPhoneNumberService).create(CALLED_NUMBER, Constants.REASON.WRAPUP_CODE, dnc);
    verify(dncEngine).updatePhoneNumberInDnc(any(DNCPhoneNumber.class));
    verify(dncEngine).updateDnc(1L);
    verify(customerDncService, never()).blockByCustomerScope(any(), any(), any());
    verify(customerDncService, never()).blockByCustomerNo(anyString(), any(), any());
    verify(dncEngine, never()).dropQueuedCallsForNumbers(any(), any());
  }
}
