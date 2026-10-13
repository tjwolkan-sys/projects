package tests.redis;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

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
 * Wrap-up'ta scope=CUSTOMER geldiğinde DNC güncellemesi CustomerDncService
 * üzerinden müşteri genişletmesiyle yapılır ve dönen engellenen numara seti
 * canlı kuyruk hot-drop'una verilir; tekil telefon ekleme yolu çalışmaz.
 */
@RunWith(MockitoJUnitRunner.class)
public class WebphoneSubscriberCustomerScopeWrapupTest {

  private static final Long USER_ID = 1L;
  private static final String CALL_PARENT_ID = "dl-callId";

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
  private Campaign campaign;
  private DNC dnc;
  private CallResultCode callResultCode;

  @Before
  public void init() {
    dnc = new DNC();
    dnc.setId(1L);

    PhoneNumberColumn numberColumn = new PhoneNumberColumn();
    numberColumn.setEnabled(true);

    campaign = new Campaign();
    campaign.setPhoneNumberColumns(Arrays.asList(numberColumn));
    campaign.setColResultDNCNumbers("dnc_numbers");
    campaign.setColResultCustomerNo("customer_no");
    campaign.setDnc(dnc);

    User user = new User();
    user.setId(USER_ID);

    call = new Call();
    call.setId(CALL_PARENT_ID);
    call.setCampaign(campaign);
    call.setTelephonyResultCode(new TelephonyResultCode());
    call.setCalledNumber("05321112233");
    call.setRawData(new JSONObject());
    call.setUser(user);

    ResultCode resultCode = new ResultCode();
    resultCode.setId(50L);
    resultCode.setDncIdentifier(true);

    callResultCode = new CallResultCode();
    callResultCode.setParentId(CALL_PARENT_ID);
    callResultCode.setUserId(USER_ID);
    callResultCode.setResultCode(resultCode);
    callResultCode.setDncScope(Constants.DNC_SCOPE.CUSTOMER);

    when(connectionDecorator.getCalls().getIfPresent(CALL_PARENT_ID)).thenReturn(call);
    Map<String, ManagerConnectionDecorator> openedConnections = new HashMap<>();
    openedConnections.put("srv", connectionDecorator);
    when(managerConnectionComposition.getOpenedConnections()).thenReturn(openedConnections);

    when(redisMessage.getType()).thenReturn(MSG_ACTION_TYPE.ADD);
    when(redisMessage.getSerializableObject()).thenReturn(callResultCode);
  }

  @Test
  public void customerScopeDelegatesToCustomerDncServiceAndDropsQueuedCalls() throws Exception {
    Set<String> blockedNumbers = new LinkedHashSet<>(Arrays.asList("05321112233", "02121112233"));
    when(customerDncService.blockByCustomerScope(call, campaign, dnc)).thenReturn(blockedNumbers);

    WhiteboxImpl.invokeMethod(webphoneSubscriber, "handleResultCodeEntityMessage", redisMessage);

    verify(customerDncService).blockByCustomerScope(call, campaign, dnc);
    verify(dncEngine).dropQueuedCallsForNumbers(dnc, blockedNumbers);
    verify(dncPhoneNumberService, never()).create(anyString(), any(), any());
    verify(dncPhoneNumberService, never()).existsByPhoneNumberAndDncId(anyString(), any());
    verify(dncEngine, never()).updatePhoneNumberInDnc(any());
  }

  @Test
  public void customerScopeStillRecordsTriggeringNumberOnCallDncNumbers() throws Exception {
    when(customerDncService.blockByCustomerScope(call, campaign, dnc))
        .thenReturn(new HashSet<>(Arrays.asList("05321112233")));

    WhiteboxImpl.invokeMethod(webphoneSubscriber, "handleResultCodeEntityMessage", redisMessage);

    org.junit.Assert.assertTrue("dnc_numbers kolonuna yazılmak üzere tetikleyen numara call'a işlenmeli",
        call.getDNCNumbers().contains("05321112233"));
  }
}
