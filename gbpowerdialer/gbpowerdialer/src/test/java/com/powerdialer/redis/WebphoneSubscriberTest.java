package com.powerdialer.redis;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import java.io.IOException;

import org.asteriskjava.manager.AuthenticationFailedException;
import org.asteriskjava.manager.TimeoutException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.powermock.reflect.internal.WhiteboxImpl;

import com.powerdialer.engine.CallCleaner;
import com.powerdialer.engine.CallUpToDateKeeper;
import com.powerdialer.engine.DNCEngine;
import com.powerdialer.engine.UserLoginLogoutHandler;
import com.powerdialer.engine.acd.ACDEngine;
import com.powerdialer.engine.asterisk.ami.ManagerConnectionComposition;
import com.powerdialer.engine.asterisk.ami.ManagerConnectionDecorator;
import com.powerdialer.engine.dialer.CallOriginator;
import com.powerdialer.engine.dialer.DialerFacade;
import com.powerdialer.model.Call;
import com.powerdialer.model.CallResultCode;
import com.powerdialer.model.CDR;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.DNC;
import com.powerdialer.model.PhoneNumberColumn;
import com.powerdialer.model.ResultCode;
import com.powerdialer.model.Server;
import com.powerdialer.model.TelephonyResultCode;
import com.powerdialer.model.User;
import com.powerdialer.redis.message.RedisMessage;
import com.powerdialer.service.CDRService;
import com.powerdialer.service.CampaignResultService;
import com.powerdialer.service.DNCPhoneNumberService;
import com.powerdialer.service.UserService;
import com.powerdialer.util.Constants;
import com.powerdialer.util.Constants.MSG_ACTION_TYPE;
import com.powerdialer.util.Constants.RETRY_STRATEGY;

/**
 * Tests for WebphoneSubscriber class
 */
@RunWith(MockitoJUnitRunner.class)
public class WebphoneSubscriberTest {

  private static final Long USER_ID = 1L;
  private static final String CALL_PARENT_ID = "dl-callId";

  private Server originalServer = new Server();
  private Server updatedServer = new Server();
  private CallResultCode callResultCode;
  private User user;

  private Call call;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private ManagerConnectionDecorator mockManagerConnectionDecorator;

  @Mock
  private RedisMessage redisMessage;

  @Mock
  private ACDEngine acdEngine;

  @Mock
  private UserLoginLogoutHandler userLoginLogoutHandler;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private RedisUtil redisUtil;

  @Mock
  private CallCleaner callCleaner;

  @Mock
  private CallUpToDateKeeper callUpToDateKeeper;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private ManagerConnectionComposition managerConnectionComposition;

  @Mock
  private DNCPhoneNumberService dncPhoneNumberService;

  @Mock
  private DNCEngine dncEngine;

  @Mock
  private DialerFacade dialerFacade;

  @Mock
  private CDRService cdrService;

  @Mock
  private CampaignResultService campaignResultService;

  @Mock
  private UserService userService;

  @InjectMocks
  private WebphoneSubscriber webphoneSubscriber; // SUT

  private TelephonyResultCode amiLossTrc;
  private TelephonyResultCode normalClearingTrc;

  @Before
  public void init() {
    initServers();

    callResultCode = new CallResultCode();
    callResultCode.setParentId(CALL_PARENT_ID);
    callResultCode.setUserId(USER_ID);
    ResultCode resultCode = new ResultCode();
    callResultCode.setResultCode(resultCode);
    user = new User();
    user.setId(USER_ID);
    call = createDumbCall();

    when(mockManagerConnectionDecorator.getCalls().getIfPresent(CALL_PARENT_ID)).thenReturn(call);
    when(mockManagerConnectionDecorator.getAwaitingUserResultCodeCalls().getIfPresent(CALL_PARENT_ID)).thenReturn(null);
    when(mockManagerConnectionDecorator.getRecoverableConnectionLossCalls().getIfPresent(CALL_PARENT_ID)).thenReturn(null);
    Map<String, ManagerConnectionDecorator> openedConnections = new HashMap<>();
    openedConnections.put("srv", mockManagerConnectionDecorator);
    when(managerConnectionComposition.getOpenedConnections()).thenReturn(openedConnections);
    when(managerConnectionComposition.getCdrService()).thenReturn(cdrService);

    amiLossTrc = new TelephonyResultCode();
    amiLossTrc.setId(75L);
    amiLossTrc.setCode(Constants.ERROR_RESULT_CODES.AMI_CONNECTION_LOSS.getCode());

    normalClearingTrc = new TelephonyResultCode();
    normalClearingTrc.setId(10L);
    normalClearingTrc.setCode(Constants.TELEPHONY_RC_NORMAL_CLEARING);

    Map<Integer, TelephonyResultCode> ituMap = new HashMap<>();
    ituMap.put(Constants.TELEPHONY_RC_NORMAL_CLEARING, normalClearingTrc);
    when(managerConnectionComposition.getItuToTelephonyResultCodeRelations()).thenReturn(ituMap);
  }

  private void initServers() {
    originalServer.setId(1L);
    originalServer.setType(Constants.SERVER_TYPE.REGISTRAR);
    originalServer.setIp("127.0.0.1");
    originalServer.setEnabled(true);
    originalServer.setInUse(true);

    updatedServer.setId(originalServer.getId());
    updatedServer.setType(originalServer.getType());
    updatedServer.setIp(originalServer.getIp());
    updatedServer.setEnabled(originalServer.isEnabled());
    updatedServer.setInUse(originalServer.isInUse());
  }

  @Test
  public void givenCallWhenUserResultCodeDNCPerClientThenAddNumberToDNCCalls() throws Exception {
    callResultCode.getResultCode().setDncIdentifier(true);
    when(redisMessage.getType()).thenReturn(MSG_ACTION_TYPE.ADD);
    when(redisMessage.getSerializableObject()).thenReturn(callResultCode);

    WhiteboxImpl.invokeMethod(webphoneSubscriber, "handleResultCodeEntityMessage", redisMessage);

    assertThat(call.getDNCNumbers().size(), is(not(0)));
  }

  @Test
  public void givenCallWhenGetResultCodeMessageThenSetResultCode() throws Exception {
    when(redisMessage.getType()).thenReturn(MSG_ACTION_TYPE.ADD);
    when(redisMessage.getSerializableObject()).thenReturn(callResultCode);

    WhiteboxImpl.invokeMethod(webphoneSubscriber, "handleResultCodeEntityMessage", redisMessage);

    assertThat("User result code was not set for the call", call.getUserResultCode(), is(callResultCode.getResultCode()));
  }

  @Test
  public void givenRecoverableConnectionLossCallWhenGetResultCodeMessageThenSetResultCode() throws Exception {
    call.setTelephonyResultCode(null);
    when(mockManagerConnectionDecorator.getCalls().getIfPresent(CALL_PARENT_ID)).thenReturn(null);
    when(mockManagerConnectionDecorator.getRecoverableConnectionLossCalls().getIfPresent(CALL_PARENT_ID)).thenReturn(call);
    when(redisMessage.getType()).thenReturn(MSG_ACTION_TYPE.ADD);
    when(redisMessage.getSerializableObject()).thenReturn(callResultCode);

    WhiteboxImpl.invokeMethod(webphoneSubscriber, "handleResultCodeEntityMessage", redisMessage);

    assertThat("User result code was not set for the recoverable call", call.getUserResultCode(), is(callResultCode.getResultCode()));
    verify(mockManagerConnectionDecorator, never()).processCallResult(eq(call), any(Date.class));
  }

  @Test
  public void givenAwaitingUserResultCallWhenGetResultCodeMessageThenFinalizeCall() throws Exception {
    when(mockManagerConnectionDecorator.getCalls().getIfPresent(CALL_PARENT_ID)).thenReturn(null);
    when(mockManagerConnectionDecorator.getAwaitingUserResultCodeCalls().getIfPresent(CALL_PARENT_ID)).thenReturn(call);
    when(redisMessage.getType()).thenReturn(MSG_ACTION_TYPE.ADD);
    when(redisMessage.getSerializableObject()).thenReturn(callResultCode);

    WhiteboxImpl.invokeMethod(webphoneSubscriber, "handleResultCodeEntityMessage", redisMessage);

    assertThat("User result code was not set for the awaiting call", call.getUserResultCode(), is(callResultCode.getResultCode()));
    verify(mockManagerConnectionDecorator).processCallResult(eq(call), any(Date.class));
  }

  @Test
  public void givenCallWithoutAssignedUserWhenGetResultCodeMessageThenDoNotSetResultCode() throws Exception {
    call.setUser(null);
    when(redisMessage.getType()).thenReturn(MSG_ACTION_TYPE.ADD);
    when(redisMessage.getSerializableObject()).thenReturn(callResultCode);

    WhiteboxImpl.invokeMethod(webphoneSubscriber, "handleResultCodeEntityMessage", redisMessage);

    assertThat("User result code was set for the call which has no user", call.getUserResultCode(), is(nullValue()));
  }

  @Test
  public void givenCallWithDifferentAssignedUserWhenGetResultCodeMessageThenDoNotSetResultCode() throws Exception {
    User assignedUser = new User();
    assignedUser.setId(2L);
    call.setUser(assignedUser);
    when(redisMessage.getType()).thenReturn(MSG_ACTION_TYPE.ADD);
    when(redisMessage.getSerializableObject()).thenReturn(callResultCode);

    WhiteboxImpl.invokeMethod(webphoneSubscriber, "handleResultCodeEntityMessage", redisMessage);

    assertThat("User result code was set for the call with different assigned user", call.getUserResultCode(), is(nullValue()));
  }

  @Test
  public void givenCallWithResultCodeWhenGetResultCodeMessageThenDoNotSetResultCode() throws Exception {
    ResultCode resultCode = new ResultCode();
    call.setUserResultCode(resultCode);
    when(redisMessage.getType()).thenReturn(MSG_ACTION_TYPE.ADD);
    when(redisMessage.getSerializableObject()).thenReturn(callResultCode);

    WhiteboxImpl.invokeMethod(webphoneSubscriber, "handleResultCodeEntityMessage", redisMessage);

    assertThat("User result code was set for the call which already has result code",
        call.getUserResultCode(), is(callResultCode.getResultCode()));
  }

  @Test
  public void givenNoActiveCallAndSuccessfulCdrWhenGetResultCodeMessageThenPersistDurableFallback() throws Exception {
    long resultCodeId = 50L;
    callResultCode.setResultCode(resultCode(resultCodeId));
    CDR cdr = successfulCdr(user(USER_ID), user(2L), null);
    when(mockManagerConnectionDecorator.getCalls().getIfPresent(CALL_PARENT_ID)).thenReturn(null);
    when(cdrService.getByInteractionId(CALL_PARENT_ID)).thenReturn(cdr);
    when(redisMessage.getType()).thenReturn(MSG_ACTION_TYPE.ADD);
    when(redisMessage.getSerializableObject()).thenReturn(callResultCode);

    WhiteboxImpl.invokeMethod(webphoneSubscriber, "handleResultCodeEntityMessage", redisMessage);

    assertThat(cdr.getUserResultCodeId(), is(resultCodeId));
    verify(cdrService).saveOrUpdate(cdr);
    verify(campaignResultService).updateUserResultCode(cdr, resultCodeId);
  }

  @Test
  public void givenNoAssignedUserAndConnectedUserMatchesWhenGetResultCodeMessageThenPersistDurableFallback() throws Exception {
    long resultCodeId = 50L;
    callResultCode.setResultCode(resultCode(resultCodeId));
    CDR cdr = successfulCdr(null, user(USER_ID), null);
    when(mockManagerConnectionDecorator.getCalls().getIfPresent(CALL_PARENT_ID)).thenReturn(null);
    when(cdrService.getByInteractionId(CALL_PARENT_ID)).thenReturn(cdr);
    when(redisMessage.getType()).thenReturn(MSG_ACTION_TYPE.ADD);
    when(redisMessage.getSerializableObject()).thenReturn(callResultCode);

    WhiteboxImpl.invokeMethod(webphoneSubscriber, "handleResultCodeEntityMessage", redisMessage);

    assertThat(cdr.getUserResultCodeId(), is(resultCodeId));
    verify(cdrService).saveOrUpdate(cdr);
    verify(campaignResultService).updateUserResultCode(cdr, resultCodeId);
  }

  @Test
  public void givenActiveCallAndFinalizationFailsWhenGetResultCodeMessageThenPersistDurableFallback() throws Exception {
    long resultCodeId = 50L;
    callResultCode.setResultCode(resultCode(resultCodeId));
    CDR cdr = successfulCdr(user(USER_ID), user(USER_ID), null);
    call.setCdr(cdr);
    doThrow(new NullPointerException("finalization failed"))
        .when(mockManagerConnectionDecorator).processCallResult(eq(call), any(Date.class));
    when(redisMessage.getType()).thenReturn(MSG_ACTION_TYPE.ADD);
    when(redisMessage.getSerializableObject()).thenReturn(callResultCode);

    WhiteboxImpl.invokeMethod(webphoneSubscriber, "handleResultCodeEntityMessage", redisMessage);

    assertThat(call.getUserResultCode(), is(callResultCode.getResultCode()));
    assertThat(cdr.getUserResultCodeId(), is(resultCodeId));
    verify(cdrService).saveOrUpdate(cdr);
    verify(campaignResultService).updateUserResultCode(cdr, resultCodeId);
  }

  @Test
  public void givenNoActiveCallAndCdrUserDoesNotMatchWhenGetResultCodeMessageThenDoNotPersistFallback() throws Exception {
    callResultCode.setResultCode(resultCode(50L));
    CDR cdr = successfulCdr(user(99L), user(USER_ID), null);
    when(mockManagerConnectionDecorator.getCalls().getIfPresent(CALL_PARENT_ID)).thenReturn(null);
    when(cdrService.getByInteractionId(CALL_PARENT_ID)).thenReturn(cdr);
    when(redisMessage.getType()).thenReturn(MSG_ACTION_TYPE.ADD);
    when(redisMessage.getSerializableObject()).thenReturn(callResultCode);

    WhiteboxImpl.invokeMethod(webphoneSubscriber, "handleResultCodeEntityMessage", redisMessage);

    assertThat(cdr.getUserResultCodeId(), is(nullValue()));
    verify(cdrService, never()).saveOrUpdate(cdr);
    verify(campaignResultService, never()).updateUserResultCode(eq(cdr), any(Long.class));
  }

  @Test
  public void givenNoActiveCallAndNonSuccessfulCdrWhenGetResultCodeMessageThenDoNotPersistFallback() throws Exception {
    callResultCode.setResultCode(resultCode(50L));
    CDR cdr = successfulCdr(user(USER_ID), user(USER_ID), null);
    cdr.getTelephonyRC().setId(76L);
    when(mockManagerConnectionDecorator.getCalls().getIfPresent(CALL_PARENT_ID)).thenReturn(null);
    when(cdrService.getByInteractionId(CALL_PARENT_ID)).thenReturn(cdr);
    when(redisMessage.getType()).thenReturn(MSG_ACTION_TYPE.ADD);
    when(redisMessage.getSerializableObject()).thenReturn(callResultCode);

    WhiteboxImpl.invokeMethod(webphoneSubscriber, "handleResultCodeEntityMessage", redisMessage);

    assertThat(cdr.getUserResultCodeId(), is(nullValue()));
    verify(cdrService, never()).saveOrUpdate(cdr);
    verify(campaignResultService, never()).updateUserResultCode(eq(cdr), any(Long.class));
  }

  @Test
  public void givenNoActiveCallAndForcefullyStoppedCdrWhenGetResultCodeMessageThenPersistCdrAndCampaignFallback() throws Exception {
    long resultCodeId = 50L;
    callResultCode.setResultCode(resultCode(resultCodeId));
    CDR cdr = successfulCdr(user(USER_ID), user(USER_ID), null);
    cdr.getTelephonyRC().setId(99L);
    cdr.getTelephonyRC().setCode(Constants.TELEPHONE_RC_CAMPAIGN_IS_STOPPED_FORCEFULLY);
    when(mockManagerConnectionDecorator.getCalls().getIfPresent(CALL_PARENT_ID)).thenReturn(null);
    when(cdrService.getByInteractionId(CALL_PARENT_ID)).thenReturn(cdr);
    when(redisMessage.getType()).thenReturn(MSG_ACTION_TYPE.ADD);
    when(redisMessage.getSerializableObject()).thenReturn(callResultCode);

    WhiteboxImpl.invokeMethod(webphoneSubscriber, "handleResultCodeEntityMessage", redisMessage);

    assertThat(cdr.getUserResultCodeId(), is(resultCodeId));
    verify(cdrService).saveOrUpdate(cdr);
    verify(campaignResultService).updateUserResultCode(cdr, resultCodeId);
  }

  @Test
  public void givenNoActiveCallAndCdrAlreadyHasUserResultCodeWhenGetResultCodeMessageThenDoNotOverwriteCdr() throws Exception {
    callResultCode.setResultCode(resultCode(50L));
    CDR cdr = successfulCdr(user(USER_ID), user(USER_ID), 40L);
    when(mockManagerConnectionDecorator.getCalls().getIfPresent(CALL_PARENT_ID)).thenReturn(null);
    when(cdrService.getByInteractionId(CALL_PARENT_ID)).thenReturn(cdr);
    when(redisMessage.getType()).thenReturn(MSG_ACTION_TYPE.ADD);
    when(redisMessage.getSerializableObject()).thenReturn(callResultCode);

    WhiteboxImpl.invokeMethod(webphoneSubscriber, "handleResultCodeEntityMessage", redisMessage);

    assertThat(cdr.getUserResultCodeId(), is(40L));
    verify(cdrService, never()).saveOrUpdate(cdr);
    verify(campaignResultService, never()).updateUserResultCode(eq(cdr), any(Long.class));
  }

  @Test
  public void givenActiveCallWithoutAssignedUserAndCdrConnectedUserMatchesWhenGetResultCodeMessageThenPersistFallback() throws Exception {
    long resultCodeId = 50L;
    callResultCode.setResultCode(resultCode(resultCodeId));
    call.setUser(null);
    CDR cdr = successfulCdr(null, user(USER_ID), null);
    when(cdrService.getByInteractionId(CALL_PARENT_ID)).thenReturn(cdr);
    when(redisMessage.getType()).thenReturn(MSG_ACTION_TYPE.ADD);
    when(redisMessage.getSerializableObject()).thenReturn(callResultCode);

    WhiteboxImpl.invokeMethod(webphoneSubscriber, "handleResultCodeEntityMessage", redisMessage);

    assertThat("Active call user yoksa in-memory call.userResultCode set edilmemeli; durable fallback DB CDR'ı günceller.",
        call.getUserResultCode(), is(nullValue()));
    assertThat(cdr.getUserResultCodeId(), is(resultCodeId));
    verify(cdrService).saveOrUpdate(cdr);
    verify(campaignResultService).updateUserResultCode(cdr, resultCodeId);
  }

  @Test
  public void givenActiveCallWithDifferentAssignedUserAndCdrAssignedUserMatchesWhenGetResultCodeMessageThenPersistFallback() throws Exception {
    long resultCodeId = 50L;
    callResultCode.setResultCode(resultCode(resultCodeId));
    User mismatchedUser = new User();
    mismatchedUser.setId(2L);
    call.setUser(mismatchedUser);
    CDR cdr = successfulCdr(user(USER_ID), user(2L), null);
    when(cdrService.getByInteractionId(CALL_PARENT_ID)).thenReturn(cdr);
    when(redisMessage.getType()).thenReturn(MSG_ACTION_TYPE.ADD);
    when(redisMessage.getSerializableObject()).thenReturn(callResultCode);

    WhiteboxImpl.invokeMethod(webphoneSubscriber, "handleResultCodeEntityMessage", redisMessage);

    assertThat("Active call user mismatch durumunda in-memory call.userResultCode set edilmemeli; durable fallback DB CDR'ı günceller.",
        call.getUserResultCode(), is(nullValue()));
    assertThat(cdr.getUserResultCodeId(), is(resultCodeId));
    verify(cdrService).saveOrUpdate(cdr);
    verify(campaignResultService).updateUserResultCode(cdr, resultCodeId);
  }

  @Test
  public void givenActiveCallWithDifferentAssignedUserAndCdrUsersDoNotMatchWhenGetResultCodeMessageThenSkipFallback() throws Exception {
    callResultCode.setResultCode(resultCode(50L));
    User mismatchedUser = new User();
    mismatchedUser.setId(2L);
    call.setUser(mismatchedUser);
    CDR cdr = successfulCdr(user(99L), user(98L), null);
    when(cdrService.getByInteractionId(CALL_PARENT_ID)).thenReturn(cdr);
    when(redisMessage.getType()).thenReturn(MSG_ACTION_TYPE.ADD);
    when(redisMessage.getSerializableObject()).thenReturn(callResultCode);

    WhiteboxImpl.invokeMethod(webphoneSubscriber, "handleResultCodeEntityMessage", redisMessage);

    assertThat("Hiçbir CDR user alanı result code userId'sine eşleşmiyorsa fallback yazmamalı.",
        cdr.getUserResultCodeId(), is(nullValue()));
    verify(cdrService, never()).saveOrUpdate(cdr);
    verify(campaignResultService, never()).updateUserResultCode(eq(cdr), any(Long.class));
  }

  @Test
  public void givenActiveCallWithExistingResultCodeWhenGetResultCodeMessageThenSkipFallback() throws Exception {
    ResultCode existingResultCode = new ResultCode();
    existingResultCode.setId(40L);
    call.setUserResultCode(existingResultCode);
    callResultCode.setResultCode(resultCode(50L));
    CDR cdr = successfulCdr(user(USER_ID), user(USER_ID), null);
    when(redisMessage.getType()).thenReturn(MSG_ACTION_TYPE.ADD);
    when(redisMessage.getSerializableObject()).thenReturn(callResultCode);

    WhiteboxImpl.invokeMethod(webphoneSubscriber, "handleResultCodeEntityMessage", redisMessage);

    assertThat("Active call zaten userResultCode'a sahipse mevcut değer korunmalı.",
        call.getUserResultCode(), is(existingResultCode));
    assertThat("Active call zaten userResultCode'a sahipse durable fallback denenmemeli.",
        cdr.getUserResultCodeId(), is(nullValue()));
    verify(cdrService, never()).getByInteractionId(CALL_PARENT_ID);
    verify(cdrService, never()).saveOrUpdate(cdr);
    verify(campaignResultService, never()).updateUserResultCode(eq(cdr), any(Long.class));
  }

  @Test
  public void givenAmiLossCdrWithRegistrarAgentLegWhenGetResultCodeMessageThenPromoteToSuccess() throws Exception {
    long resultCodeId = 34L;
    callResultCode.setResultCode(resultCode(resultCodeId));
    Date callAnswerTime = new Date(2000L);
    Date agentAnswerTime = new Date(3000L);
    CDR cdr = amiLossCdr(callAnswerTime);
    when(mockManagerConnectionDecorator.getCalls().getIfPresent(CALL_PARENT_ID)).thenReturn(null);
    when(cdrService.getByInteractionId(CALL_PARENT_ID)).thenReturn(cdr);
    when(cdrService.findAgentRegistrarAnswerTime(CALL_PARENT_ID)).thenReturn(agentAnswerTime);
    when(cdrService.hasSuccessfulInteractionStatus(CALL_PARENT_ID, USER_ID)).thenReturn(true);
    when(userService.getUserByIdWithGroup(USER_ID)).thenReturn(user(USER_ID));
    when(redisMessage.getType()).thenReturn(MSG_ACTION_TYPE.ADD);
    when(redisMessage.getSerializableObject()).thenReturn(callResultCode);

    WhiteboxImpl.invokeMethod(webphoneSubscriber, "handleResultCodeEntityMessage", redisMessage);

    assertThat("Promote sonrası user_result_code_id event'teki id ile dolmalı",
        cdr.getUserResultCodeId(), is(resultCodeId));
    assertThat("Promote sonrası telephony_rc Normal Clearing entity'sine geçmeli",
        cdr.getTelephonyRC(), sameInstance(normalClearingTrc));
    assertThat("Promote sonrası call_handling_start Asterisk agent answer zamanına eşit olmalı",
        cdr.getCallHandlingStartTime(), is(agentAnswerTime));
    assertThat("Predictive başarılı çağrı için assigned_user dolmalı",
        cdr.getAssignedUser(), is(not(nullValue())));
    assertThat(cdr.getAssignedUser().getId(), is(USER_ID));
    assertThat("Başarılı bağlantıda connected_user dolmalı",
        cdr.getConnectedUser(), is(not(nullValue())));
    assertThat(cdr.getConnectedUser().getId(), is(USER_ID));
    verify(cdrService).saveOrUpdate(cdr);
    // FIX-333607: synthetic call liste satırının durumunu (rawData) taşımaz; liste tablosu güncellenmez.
    verify(mockManagerConnectionDecorator, never()).insertCallResultToDB(any(Call.class));
  }

  @Test
  public void givenAmiLossCdrWithoutContactIdWhenGetResultCodeMessageThenSkipPromoteToAvoidDuplicate() throws Exception {
    callResultCode.setResultCode(resultCode(34L));
    CDR cdr = amiLossCdr(new Date(2000L));
    cdr.setContactId(null);
    when(mockManagerConnectionDecorator.getCalls().getIfPresent(CALL_PARENT_ID)).thenReturn(null);
    when(cdrService.getByInteractionId(CALL_PARENT_ID)).thenReturn(cdr);
    when(redisMessage.getType()).thenReturn(MSG_ACTION_TYPE.ADD);
    when(redisMessage.getSerializableObject()).thenReturn(callResultCode);

    WhiteboxImpl.invokeMethod(webphoneSubscriber, "handleResultCodeEntityMessage", redisMessage);

    assertThat("contactId yoksa campaign result tablosunda duplicate riskine düşmemek için promote skip",
        cdr.getUserResultCodeId(), is(nullValue()));
    verify(cdrService, never()).findAgentRegistrarAnswerTime(any());
    verify(cdrService, never()).saveOrUpdate(cdr);
    verify(mockManagerConnectionDecorator, never()).insertCallResultToDB(any(Call.class));
  }

  @Test
  public void givenAmiLossCdrWithoutRegistrarAgentLegWhenGetResultCodeMessageThenSkipPromote() throws Exception {
    callResultCode.setResultCode(resultCode(34L));
    CDR cdr = amiLossCdr(new Date(2000L));
    when(mockManagerConnectionDecorator.getCalls().getIfPresent(CALL_PARENT_ID)).thenReturn(null);
    when(cdrService.getByInteractionId(CALL_PARENT_ID)).thenReturn(cdr);
    when(cdrService.findAgentRegistrarAnswerTime(CALL_PARENT_ID)).thenReturn(null);
    when(redisMessage.getType()).thenReturn(MSG_ACTION_TYPE.ADD);
    when(redisMessage.getSerializableObject()).thenReturn(callResultCode);

    WhiteboxImpl.invokeMethod(webphoneSubscriber, "handleResultCodeEntityMessage", redisMessage);

    assertThat("Asterisk-side agent legi yoksa promote yapılmamalı",
        cdr.getUserResultCodeId(), is(nullValue()));
    assertThat("Promote skip edildiğinde trc kalmalı (AMI loss)",
        cdr.getTelephonyRC().getCode(), is(Constants.ERROR_RESULT_CODES.AMI_CONNECTION_LOSS.getCode()));
    verify(cdrService, never()).saveOrUpdate(cdr);
    verify(mockManagerConnectionDecorator, never()).insertCallResultToDB(any(Call.class));
  }

  /**
   * {@code callAnswerTime} is no longer a standalone promote gate: it can be null purely because PD's
   * customer-answer event was processed late on the lagging event worker. The authoritative gate is the
   * Asterisk registrar agent-answer leg. When that leg is absent the call truly was not handled by an
   * agent, so the promote is still skipped (no CDR/campaign rewrite) — even though, unlike before, the
   * registrar lookup is now performed (it is the gate) instead of being short-circuited by callAnswerTime.
   */
  @Test
  public void givenAmiLossCdrNullCallAnswerTimeButNoRegistrarEvidenceWhenGetResultCodeMessageThenSkipPromote() throws Exception {
    callResultCode.setResultCode(resultCode(34L));
    CDR cdr = amiLossCdr(null);
    when(mockManagerConnectionDecorator.getCalls().getIfPresent(CALL_PARENT_ID)).thenReturn(null);
    when(cdrService.getByInteractionId(CALL_PARENT_ID)).thenReturn(cdr);
    when(cdrService.findAgentRegistrarAnswerTime(CALL_PARENT_ID)).thenReturn(null); // no agent-answer leg
    when(redisMessage.getType()).thenReturn(MSG_ACTION_TYPE.ADD);
    when(redisMessage.getSerializableObject()).thenReturn(callResultCode);

    WhiteboxImpl.invokeMethod(webphoneSubscriber, "handleResultCodeEntityMessage", redisMessage);

    assertThat("Registrar agent legi yoksa (gerçekten cevaplanmamış) promote yapılmamalı",
        cdr.getUserResultCodeId(), is(nullValue()));
    verify(cdrService, never()).saveOrUpdate(cdr);
    verify(mockManagerConnectionDecorator, never()).insertCallResultToDB(any(Call.class));
  }

  @Test
  public void givenAmiLossCdrButUserNotResolvedWhenGetResultCodeMessageThenSkipPromote() throws Exception {
    callResultCode.setResultCode(resultCode(34L));
    CDR cdr = amiLossCdr(new Date(2000L));
    when(mockManagerConnectionDecorator.getCalls().getIfPresent(CALL_PARENT_ID)).thenReturn(null);
    when(cdrService.getByInteractionId(CALL_PARENT_ID)).thenReturn(cdr);
    when(cdrService.findAgentRegistrarAnswerTime(CALL_PARENT_ID)).thenReturn(new Date(3000L));
    when(cdrService.hasSuccessfulInteractionStatus(CALL_PARENT_ID, USER_ID)).thenReturn(true);
    when(userService.getUserByIdWithGroup(USER_ID)).thenReturn(null);
    when(redisMessage.getType()).thenReturn(MSG_ACTION_TYPE.ADD);
    when(redisMessage.getSerializableObject()).thenReturn(callResultCode);

    WhiteboxImpl.invokeMethod(webphoneSubscriber, "handleResultCodeEntityMessage", redisMessage);

    assertThat("User resolve edilemezse promote yapılmamalı",
        cdr.getUserResultCodeId(), is(nullValue()));
    verify(cdrService, never()).saveOrUpdate(cdr);
    verify(mockManagerConnectionDecorator, never()).insertCallResultToDB(any(Call.class));
  }

  @Test
  public void givenAmiLossCdrAlreadyHasUserResultCodeWhenGetResultCodeMessageThenSkipPromote() throws Exception {
    callResultCode.setResultCode(resultCode(34L));
    CDR cdr = amiLossCdr(new Date(2000L));
    cdr.setUserResultCodeId(99L);
    when(mockManagerConnectionDecorator.getCalls().getIfPresent(CALL_PARENT_ID)).thenReturn(null);
    when(cdrService.getByInteractionId(CALL_PARENT_ID)).thenReturn(cdr);
    when(redisMessage.getType()).thenReturn(MSG_ACTION_TYPE.ADD);
    when(redisMessage.getSerializableObject()).thenReturn(callResultCode);

    WhiteboxImpl.invokeMethod(webphoneSubscriber, "handleResultCodeEntityMessage", redisMessage);

    assertThat("Idempotent guard mevcut user_result_code_id'yi korumalı",
        cdr.getUserResultCodeId(), is(99L));
    verify(cdrService, never()).findAgentRegistrarAnswerTime(any());
    verify(cdrService, never()).saveOrUpdate(cdr);
    verify(mockManagerConnectionDecorator, never()).insertCallResultToDB(any(Call.class));
  }

  @Test
  public void givenAmiLossCdrWithEventUserIdNullWhenGetResultCodeMessageThenSkipPromote() throws Exception {
    callResultCode.setResultCode(resultCode(34L));
    callResultCode.setUserId(null);
    CDR cdr = amiLossCdr(new Date(2000L));
    when(mockManagerConnectionDecorator.getCalls().getIfPresent(CALL_PARENT_ID)).thenReturn(null);
    when(cdrService.getByInteractionId(CALL_PARENT_ID)).thenReturn(cdr);
    when(redisMessage.getType()).thenReturn(MSG_ACTION_TYPE.ADD);
    when(redisMessage.getSerializableObject()).thenReturn(callResultCode);

    WhiteboxImpl.invokeMethod(webphoneSubscriber, "handleResultCodeEntityMessage", redisMessage);

    assertThat("Event userId yoksa promote candidate olmamalı",
        cdr.getUserResultCodeId(), is(nullValue()));
    verify(cdrService, never()).findAgentRegistrarAnswerTime(any());
    verify(cdrService, never()).saveOrUpdate(cdr);
    verify(mockManagerConnectionDecorator, never()).insertCallResultToDB(any(Call.class));
  }

  @Test
  public void givenAmiLossCdrWithoutWebphoneInteractionStatusWhenGetResultCodeMessageThenSkipPromote() throws Exception {
    callResultCode.setResultCode(resultCode(34L));
    CDR cdr = amiLossCdr(new Date(2000L));
    when(mockManagerConnectionDecorator.getCalls().getIfPresent(CALL_PARENT_ID)).thenReturn(null);
    when(cdrService.getByInteractionId(CALL_PARENT_ID)).thenReturn(cdr);
    when(cdrService.findAgentRegistrarAnswerTime(CALL_PARENT_ID)).thenReturn(new Date(3000L));
    when(cdrService.hasSuccessfulInteractionStatus(CALL_PARENT_ID, USER_ID)).thenReturn(false);
    when(redisMessage.getType()).thenReturn(MSG_ACTION_TYPE.ADD);
    when(redisMessage.getSerializableObject()).thenReturn(callResultCode);

    WhiteboxImpl.invokeMethod(webphoneSubscriber, "handleResultCodeEntityMessage", redisMessage);

    assertThat("Webphone interaction status yoksa promote yapılmamalı",
        cdr.getUserResultCodeId(), is(nullValue()));
    verify(cdrService, never()).saveOrUpdate(cdr);
    verify(mockManagerConnectionDecorator, never()).insertCallResultToDB(any(Call.class));
  }

  @Test
  public void givenAbandonedTimeoutCdrWithCrossCheckEvidenceWhenGetResultCodeMessageThenPromoteToSuccess() throws Exception {
    long resultCodeId = 34L;
    callResultCode.setResultCode(resultCode(resultCodeId));
    Date agentAnswerTime = new Date(3000L);
    CDR cdr = amiLossCdr(new Date(2000L));
    TelephonyResultCode abandonedTrc = new TelephonyResultCode();
    abandonedTrc.setId(78L);
    abandonedTrc.setCode(Constants.TELEPHONY_RC_ABANDONED_TIMEOUT);
    cdr.setTelephonyRC(abandonedTrc);
    when(mockManagerConnectionDecorator.getCalls().getIfPresent(CALL_PARENT_ID)).thenReturn(null);
    when(cdrService.getByInteractionId(CALL_PARENT_ID)).thenReturn(cdr);
    when(cdrService.findAgentRegistrarAnswerTime(CALL_PARENT_ID)).thenReturn(agentAnswerTime);
    when(cdrService.hasSuccessfulInteractionStatus(CALL_PARENT_ID, USER_ID)).thenReturn(true);
    when(userService.getUserByIdWithGroup(USER_ID)).thenReturn(user(USER_ID));
    when(redisMessage.getType()).thenReturn(MSG_ACTION_TYPE.ADD);
    when(redisMessage.getSerializableObject()).thenReturn(callResultCode);

    WhiteboxImpl.invokeMethod(webphoneSubscriber, "handleResultCodeEntityMessage", redisMessage);

    assertThat("Abandoned timeout (78) için cross-check kanıt varsa promote olmalı",
        cdr.getUserResultCodeId(), is(resultCodeId));
    assertThat(cdr.getTelephonyRC(), sameInstance(normalClearingTrc));
    assertThat(cdr.getCallHandlingStartTime(), is(agentAnswerTime));
    verify(cdrService).saveOrUpdate(cdr);
    verify(mockManagerConnectionDecorator, never()).insertCallResultToDB(any(Call.class));
  }

  @Test
  public void givenCustomerHangupBeforeAgentCdrWithCrossCheckEvidenceWhenGetResultCodeMessageThenPromoteToSuccess() throws Exception {
    long resultCodeId = 34L;
    callResultCode.setResultCode(resultCode(resultCodeId));
    Date agentAnswerTime = new Date(3000L);
    CDR cdr = amiLossCdr(new Date(2000L));
    TelephonyResultCode customerHangupTrc = new TelephonyResultCode();
    customerHangupTrc.setId(84L);
    customerHangupTrc.setCode(Constants.TELEPHONY_RC_CUSTOMER_HANGUP_BEFORE_AGENT);
    cdr.setTelephonyRC(customerHangupTrc);
    when(mockManagerConnectionDecorator.getCalls().getIfPresent(CALL_PARENT_ID)).thenReturn(null);
    when(cdrService.getByInteractionId(CALL_PARENT_ID)).thenReturn(cdr);
    when(cdrService.findAgentRegistrarAnswerTime(CALL_PARENT_ID)).thenReturn(agentAnswerTime);
    when(cdrService.hasSuccessfulInteractionStatus(CALL_PARENT_ID, USER_ID)).thenReturn(true);
    when(userService.getUserByIdWithGroup(USER_ID)).thenReturn(user(USER_ID));
    when(redisMessage.getType()).thenReturn(MSG_ACTION_TYPE.ADD);
    when(redisMessage.getSerializableObject()).thenReturn(callResultCode);

    WhiteboxImpl.invokeMethod(webphoneSubscriber, "handleResultCodeEntityMessage", redisMessage);

    assertThat("Customer Hangup Before Agent (84) için cross-check kanıt varsa promote olmalı",
        cdr.getUserResultCodeId(), is(resultCodeId));
    assertThat(cdr.getTelephonyRC(), sameInstance(normalClearingTrc));
    verify(cdrService).saveOrUpdate(cdr);
    verify(mockManagerConnectionDecorator, never()).insertCallResultToDB(any(Call.class));
  }

  /**
   * AMI event-worker lag regression for durable recovery: the customer answered and an agent handled
   * the call, but PD's own answer event was processed late on the lagging event worker, so the
   * persisted CDR still has {@code callAnswerTime == null} when the agent's webphone result code
   * arrives. Recovery must NOT be blocked by this PD-internal lag artifact: when the independent
   * authoritative evidence (Asterisk registrar agent-answer leg + webphone interaction status) confirms
   * the agent handled the call, the abandon-class CDR (TRC 84) is still promoted to success and the
   * user result code is preserved.
   */
  @Test
  public void givenAmiLossCdrNullCallAnswerTimeWithCrossCheckEvidenceWhenGetResultCodeMessageThenPromoteToSuccess() throws Exception {
    long resultCodeId = 34L;
    callResultCode.setResultCode(resultCode(resultCodeId));
    Date agentAnswerTime = new Date(3000L);
    CDR cdr = amiLossCdr(null); // PD answer event lagged → callAnswerTime not persisted yet
    TelephonyResultCode customerHangupTrc = new TelephonyResultCode();
    customerHangupTrc.setId(84L);
    customerHangupTrc.setCode(Constants.TELEPHONY_RC_CUSTOMER_HANGUP_BEFORE_AGENT);
    cdr.setTelephonyRC(customerHangupTrc);
    when(mockManagerConnectionDecorator.getCalls().getIfPresent(CALL_PARENT_ID)).thenReturn(null);
    when(cdrService.getByInteractionId(CALL_PARENT_ID)).thenReturn(cdr);
    when(cdrService.findAgentRegistrarAnswerTime(CALL_PARENT_ID)).thenReturn(agentAnswerTime);
    when(cdrService.hasSuccessfulInteractionStatus(CALL_PARENT_ID, USER_ID)).thenReturn(true);
    when(userService.getUserByIdWithGroup(USER_ID)).thenReturn(user(USER_ID));
    when(redisMessage.getType()).thenReturn(MSG_ACTION_TYPE.ADD);
    when(redisMessage.getSerializableObject()).thenReturn(callResultCode);

    WhiteboxImpl.invokeMethod(webphoneSubscriber, "handleResultCodeEntityMessage", redisMessage);

    assertThat("callAnswerTime null olsa da registrar+webphone kanıtı varken promote olmalı",
        cdr.getUserResultCodeId(), is(resultCodeId));
    assertThat(cdr.getTelephonyRC(), sameInstance(normalClearingTrc));
    assertThat(cdr.getCallHandlingStartTime(), is(agentAnswerTime));
    verify(cdrService).saveOrUpdate(cdr);
    verify(mockManagerConnectionDecorator, never()).insertCallResultToDB(any(Call.class));
  }

  @Test
  public void givenAmdTrcCdrWhenGetResultCodeMessageThenSkipPromote() throws Exception {
    callResultCode.setResultCode(resultCode(34L));
    CDR cdr = amiLossCdr(new Date(2000L));
    TelephonyResultCode amdTrc = new TelephonyResultCode();
    amdTrc.setId(71L);
    amdTrc.setCode(900);
    cdr.setTelephonyRC(amdTrc);
    when(mockManagerConnectionDecorator.getCalls().getIfPresent(CALL_PARENT_ID)).thenReturn(null);
    when(cdrService.getByInteractionId(CALL_PARENT_ID)).thenReturn(cdr);
    when(redisMessage.getType()).thenReturn(MSG_ACTION_TYPE.ADD);
    when(redisMessage.getSerializableObject()).thenReturn(callResultCode);

    WhiteboxImpl.invokeMethod(webphoneSubscriber, "handleResultCodeEntityMessage", redisMessage);

    assertThat("AMD (71) recovery listesinde değil, promote olmamalı",
        cdr.getUserResultCodeId(), is(nullValue()));
    verify(cdrService, never()).findAgentRegistrarAnswerTime(any());
    verify(cdrService, never()).saveOrUpdate(cdr);
  }

  @Test
  public void givenCallExpirationInternalErrorTrcCdrWhenGetResultCodeMessageThenSkipPromote() throws Exception {
    callResultCode.setResultCode(resultCode(34L));
    CDR cdr = amiLossCdr(new Date(2000L));
    TelephonyResultCode callExpirationTrc = new TelephonyResultCode();
    callExpirationTrc.setId(73L);
    callExpirationTrc.setCode(901);
    cdr.setTelephonyRC(callExpirationTrc);
    when(mockManagerConnectionDecorator.getCalls().getIfPresent(CALL_PARENT_ID)).thenReturn(null);
    when(cdrService.getByInteractionId(CALL_PARENT_ID)).thenReturn(cdr);
    when(redisMessage.getType()).thenReturn(MSG_ACTION_TYPE.ADD);
    when(redisMessage.getSerializableObject()).thenReturn(callResultCode);

    WhiteboxImpl.invokeMethod(webphoneSubscriber, "handleResultCodeEntityMessage", redisMessage);

    assertThat("Call expiration internal error (73) recovery listesinde değil, promote olmamalı",
        cdr.getUserResultCodeId(), is(nullValue()));
    verify(cdrService, never()).findAgentRegistrarAnswerTime(any());
    verify(cdrService, never()).saveOrUpdate(cdr);
  }

  @Test
  public void givenAsteriskRegistrarLookupThrowsWhenGetResultCodeMessageThenSkipPromoteWithoutFailure() throws Exception {
    callResultCode.setResultCode(resultCode(34L));
    CDR cdr = amiLossCdr(new Date(2000L));
    when(mockManagerConnectionDecorator.getCalls().getIfPresent(CALL_PARENT_ID)).thenReturn(null);
    when(cdrService.getByInteractionId(CALL_PARENT_ID)).thenReturn(cdr);
    when(cdrService.findAgentRegistrarAnswerTime(CALL_PARENT_ID))
        .thenThrow(new RuntimeException("DB timeout"));
    when(redisMessage.getType()).thenReturn(MSG_ACTION_TYPE.ADD);
    when(redisMessage.getSerializableObject()).thenReturn(callResultCode);

    WhiteboxImpl.invokeMethod(webphoneSubscriber, "handleResultCodeEntityMessage", redisMessage);

    assertThat("Asterisk lookup hata atarsa promote yapılmamalı, uygulama akışı kırılmamalı",
        cdr.getUserResultCodeId(), is(nullValue()));
    verify(cdrService, never()).saveOrUpdate(cdr);
    verify(mockManagerConnectionDecorator, never()).insertCallResultToDB(any(Call.class));
  }

  @Test
  public void givenWebphoneInteractionStatusLookupThrowsWhenGetResultCodeMessageThenSkipPromoteWithoutFailure() throws Exception {
    callResultCode.setResultCode(resultCode(34L));
    CDR cdr = amiLossCdr(new Date(2000L));
    when(mockManagerConnectionDecorator.getCalls().getIfPresent(CALL_PARENT_ID)).thenReturn(null);
    when(cdrService.getByInteractionId(CALL_PARENT_ID)).thenReturn(cdr);
    when(cdrService.findAgentRegistrarAnswerTime(CALL_PARENT_ID)).thenReturn(new Date(3000L));
    when(cdrService.hasSuccessfulInteractionStatus(CALL_PARENT_ID, USER_ID))
        .thenThrow(new RuntimeException("DB timeout"));
    when(redisMessage.getType()).thenReturn(MSG_ACTION_TYPE.ADD);
    when(redisMessage.getSerializableObject()).thenReturn(callResultCode);

    WhiteboxImpl.invokeMethod(webphoneSubscriber, "handleResultCodeEntityMessage", redisMessage);

    assertThat("Webphone interaction status lookup hata atarsa promote yapılmamalı",
        cdr.getUserResultCodeId(), is(nullValue()));
    verify(cdrService, never()).saveOrUpdate(cdr);
    verify(mockManagerConnectionDecorator, never()).insertCallResultToDB(any(Call.class));
  }

  @Test
  public void givenEnabledRegistrarServerWhenHandleAddMessageThenStartAll() throws Exception {
    doHandleServerEntityMessage(Constants.SERVER_TYPE.REGISTRAR, MSG_ACTION_TYPE.ADD);

    assertThatManagerConnectionWasTouched(false, true);
    assertThatCallOriginatorWasTouched(false, true);
  }

  @Test
  public void givenEnabledNotInUseRegistrarServerWhenHandleAddMessageThenStartConnection() throws Exception {
    updatedServer.setInUse(false);

    doHandleServerEntityMessage(Constants.SERVER_TYPE.REGISTRAR, MSG_ACTION_TYPE.ADD);

    assertThatManagerConnectionWasTouched(false, true);
    assertThatCallOriginatorWasTouched(false, false);
  }

  @Test
  public void givenDisabledRegistrarServerWhenHandleAddMessageThenDontStart() throws Exception {
    updatedServer.setEnabled(false);
    doHandleServerEntityMessage(Constants.SERVER_TYPE.REGISTRAR, MSG_ACTION_TYPE.ADD);

    assertThatManagerConnectionWasTouched(false, false);
    assertThatCallOriginatorWasTouched(false, false);
  }

  @Test
  public void givenEnabledMediaServerWhenHandleAddMessageThenDontStart() throws Exception {
    doHandleServerEntityMessage(Constants.SERVER_TYPE.MEDIA, MSG_ACTION_TYPE.ADD);

    assertThatManagerConnectionWasTouched(false, false);
    assertThatCallOriginatorWasTouched(false, false);
  }

  @Test
  public void givenEnabledClusterServerWhenHandleAddMessageThenDontStart() throws Exception {
    doHandleServerEntityMessage(Constants.SERVER_TYPE.CLUSTER, MSG_ACTION_TYPE.ADD);

    assertThatManagerConnectionWasTouched(false, false);
    assertThatCallOriginatorWasTouched(false, false);
  }

  @Test
  public void givenEnabledRegistrarServerAndConnectionSettingsAreUnchangedWhenHandleEditMessageThenDoNothing() throws Exception {
    setupExistentServer();

    doHandleServerEntityMessage(Constants.SERVER_TYPE.REGISTRAR, MSG_ACTION_TYPE.EDIT);

    assertThatManagerConnectionWasTouched(false, false);
    assertThatCallOriginatorWasTouched(false, false);
  }

  @Test
  public void givenEnabledRegistrarServerAndIpChangedIsWhenHandleEditMessageThenRestartConnection() throws Exception {
    updatedServer.setIp("127.0.0.2");
    setupExistentServer();

    doHandleServerEntityMessage(Constants.SERVER_TYPE.REGISTRAR, MSG_ACTION_TYPE.EDIT);

    assertThatManagerConnectionWasTouched(true, true);
    assertThatCallOriginatorWasTouched(false, null);
  }

  @Test
  public void givenDisabledRegistrarServerWhenHandleEditMessageThenStop() throws Exception {
    updatedServer.setEnabled(false);
    setupExistentServer();

    doHandleServerEntityMessage(Constants.SERVER_TYPE.REGISTRAR, MSG_ACTION_TYPE.EDIT);

    assertThatManagerConnectionWasTouched(true, false);
    assertThatCallOriginatorWasTouched(true, false);
  }

  @Test
  public void givenEnabledMediaServerWhenHandleEditMessageThenStop() throws Exception {
    setupExistentServer();

    doHandleServerEntityMessage(Constants.SERVER_TYPE.MEDIA, MSG_ACTION_TYPE.EDIT);

    assertThatManagerConnectionWasTouched(true, false);
    assertThatCallOriginatorWasTouched(true, false);
  }

  @Test
  public void givenEnabledClusterServerWhenHandleEditMessageThenStop() throws Exception {
    setupExistentServer();

    doHandleServerEntityMessage(Constants.SERVER_TYPE.CLUSTER, MSG_ACTION_TYPE.EDIT);

    assertThatManagerConnectionWasTouched(true, false);
    assertThatCallOriginatorWasTouched(true, false);
  }

  @Test
  public void givenEnabledRegistrarServerWhenHandleDeleteMessageThenStop() throws Exception {
    setupExistentServer();

    doHandleServerEntityMessage(Constants.SERVER_TYPE.REGISTRAR, MSG_ACTION_TYPE.DELETE);

    assertThatManagerConnectionWasTouched(true, false);
    assertThatCallOriginatorWasTouched(true, false);
  }

  @Test
  public void givenDisabledRegistrarServerWhenHandleDeleteMessageThenStop() throws Exception {
    setupExistentServer();
    updatedServer.setEnabled(false);

    doHandleServerEntityMessage(Constants.SERVER_TYPE.REGISTRAR, MSG_ACTION_TYPE.DELETE);

    assertThatManagerConnectionWasTouched(true, false);
    assertThatCallOriginatorWasTouched(true, false);
  }

  @Test
  public void givenDisabledMediaServerWhenHandleDeleteMessageThenStop() throws Exception {
    setupExistentServer();
    updatedServer.setEnabled(false);

    doHandleServerEntityMessage(Constants.SERVER_TYPE.MEDIA, MSG_ACTION_TYPE.DELETE);

    assertThatManagerConnectionWasTouched(true, false);
    assertThatCallOriginatorWasTouched(true, false);
  }

  @Test
  public void givenDisabledClusterServerWhenHandleDeleteMessageThenStop() throws Exception {
    setupExistentServer();
    updatedServer.setEnabled(false);

    doHandleServerEntityMessage(Constants.SERVER_TYPE.CLUSTER, MSG_ACTION_TYPE.DELETE);

    assertThatManagerConnectionWasTouched(true, false);
    assertThatCallOriginatorWasTouched(true, false);
  }

  @Test
  public void WhenHandleWrongTypesOfServerEntityMessageThenDoNothing() throws Exception {
    doHandleServerEntityMessage(Constants.SERVER_TYPE.REGISTRAR, MSG_ACTION_TYPE.STATUS_UPDATE);
    doHandleServerEntityMessage(Constants.SERVER_TYPE.REGISTRAR, MSG_ACTION_TYPE.LOGOUT);
    doHandleServerEntityMessage(Constants.SERVER_TYPE.REGISTRAR, MSG_ACTION_TYPE.ORIGINATE_ACTION_ERROR);
    doHandleServerEntityMessage(Constants.SERVER_TYPE.REGISTRAR, MSG_ACTION_TYPE.ORIGINATE_CALL);
    doHandleServerEntityMessage(Constants.SERVER_TYPE.REGISTRAR, MSG_ACTION_TYPE.PING);
    doHandleServerEntityMessage(Constants.SERVER_TYPE.REGISTRAR, MSG_ACTION_TYPE.ONLINE_USER_SYNCHRONIZATION);
    doHandleServerEntityMessage(Constants.SERVER_TYPE.REGISTRAR, MSG_ACTION_TYPE.LOGIN);

    assertThatManagerConnectionWasTouched(false, false);
    assertThatCallOriginatorWasTouched(false, false);
  }

  private void doHandleServerEntityMessage(Constants.SERVER_TYPE updatedServerType, Constants.MSG_ACTION_TYPE messageType) throws Exception {
    updatedServer.setType(updatedServerType);
    when(redisMessage.getSerializableObject()).thenReturn(updatedServer);
    when(redisMessage.getType()).thenReturn(messageType);
    WhiteboxImpl.invokeMethod(webphoneSubscriber, "handleServerEntityMessage", redisMessage);
  }

  private void setupExistentServer() {
    ManagerConnectionDecorator connectionDecorator = mock(ManagerConnectionDecorator.class);
    when(connectionDecorator.getServer()).thenReturn(originalServer);
    Map<String, ManagerConnectionDecorator> openedConnections = new HashMap<>();
    openedConnections.put(originalServer.getIp(), connectionDecorator);
    when(managerConnectionComposition.getOpenedConnections()).thenReturn(openedConnections);
  }

  /**
   * Helper method to evaluate interaction with the manager connection.
   * @param stopExpected if true then assert that connection was stopped. If false then assert that connection was not stopped. If null then don`t care about this.
   * @param startExpected if true then assert that connection was started. If false then assert that connection was not started. If null then don`t care about this.
   */
  private void assertThatManagerConnectionWasTouched(Boolean stopExpected, Boolean startExpected) throws AuthenticationFailedException, TimeoutException, IOException {
    if (Boolean.TRUE.equals(stopExpected))
      verify(managerConnectionComposition).stopManagerConnection(originalServer.getIp());
    else if (Boolean.FALSE.equals(stopExpected))
      verify(managerConnectionComposition, never()).stopManagerConnection(originalServer.getIp());
    if (Boolean.TRUE.equals(startExpected))
      verify(managerConnectionComposition).openManagerConnection(updatedServer);
    else if (Boolean.FALSE.equals(startExpected))
      verify(managerConnectionComposition, never()).openManagerConnection(updatedServer);
  }

  /**
   * Helper method to evaluate interaction with the call originator.
   * @param stopExpected if true then assert that originator was stopped. If false then assert that originator was not stopped. If null then don`t care about this.
   * @param startExpected if true then assert that originator was started. If false then assert that originator was not started. If null then don`t care about this.
   */
  private void assertThatCallOriginatorWasTouched(Boolean stopExpected, Boolean startExpected) {
    if (Boolean.TRUE.equals(stopExpected))
      verify(dialerFacade).stopCallOriginatorForServer(originalServer);
    else if (Boolean.FALSE.equals(stopExpected))
      verify(dialerFacade, never()).stopCallOriginatorForServer(originalServer);
    if (Boolean.TRUE.equals(startExpected))
      verify(dialerFacade).startCallOriginatorForServer(updatedServer);
    else if (Boolean.FALSE.equals(startExpected))
      verify(dialerFacade, never()).startCallOriginatorForServer(updatedServer);
  }

  private Call createDumbCall() {
    PhoneNumberColumn numberColumn = new PhoneNumberColumn();
    numberColumn.setEnabled(true);

    Campaign campaign = new Campaign();
    DNC dnc = new DNC();
    dnc.setId(1L);
    campaign.setRetryScheduleEnabled(true);
    campaign.setRetryStrategy(RETRY_STRATEGY.REACH_RETRY_LIMIT_BEFORE_NEXT_NUMBER);
    campaign.setRetryOveralAttempt(2);
    campaign.setMacroMaxAttempt(1);
    campaign.setPhoneNumberColumns(Collections.singletonList(numberColumn));
    campaign.setColResultDNCNumbers("dnc_numbers");
    campaign.setDnc(dnc);

    Call call = new Call();
    call.setId("dl-callId");
    call.setCampaign(campaign);
    call.setTelephonyResultCode(new TelephonyResultCode());
    call.setCalledNumber("SOME_PHONE_NUMBER");
    call.setRawData(new JSONObject());
    call.setCallOriginator(mock(CallOriginator.class));
    call.setUser(user);
    return call;
  }

  private ResultCode resultCode(long id) {
    ResultCode resultCode = new ResultCode();
    resultCode.setId(id);
    resultCode.setFinalStatus(true);
    return resultCode;
  }

  private CDR amiLossCdr(Date callAnswerTime) {
    CDR cdr = new CDR();
    cdr.setId(200L);
    cdr.setInteractionId(CALL_PARENT_ID);
    Campaign campaign = new Campaign();
    campaign.setType(Constants.CAMPAIGN_TYPE.PREDICTIVE);
    cdr.setCampaign(campaign);
    cdr.setContactId(612436L);
    cdr.setTelephonyRC(amiLossTrc);
    cdr.setCallAnswerTime(callAnswerTime);
    cdr.setCallEndTime(new Date());
    // assigned/connected null — cleanup tarafından null'lanmış durum
    // call_handling_start null — agent_answer event PD'ye ulaşmadan AMI down olmuş
    return cdr;
  }

  private CDR successfulCdr(User assignedUser, User connectedUser, Long userResultCodeId) {
    CDR cdr = new CDR();
    cdr.setId(100L);
    cdr.setInteractionId(CALL_PARENT_ID);
    cdr.setCampaign(new Campaign());
    cdr.setContactId(612436L);
    TelephonyResultCode telephonyResultCode = new TelephonyResultCode();
    telephonyResultCode.setId(10L);
    telephonyResultCode.setCode(Constants.TELEPHONY_RC_NORMAL_CLEARING);
    cdr.setTelephonyRC(telephonyResultCode);
    cdr.setAssignedUser(assignedUser);
    cdr.setConnectedUser(connectedUser);
    cdr.setCallAnswerTime(new Date());
    cdr.setCallHandlingStartTime(new Date());
    cdr.setCallEndTime(new Date());
    cdr.setUserResultCodeId(userResultCodeId);
    return cdr;
  }

  private User user(Long id) {
    User user = new User();
    user.setId(id);
    return user;
  }

}
