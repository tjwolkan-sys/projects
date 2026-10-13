package com.powerdialer.redis;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Collections;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.powermock.reflect.Whitebox;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.powerdialer.engine.CallCleaner;
import com.powerdialer.engine.CallUpToDateKeeper;
import com.powerdialer.engine.UserHolder;
import com.powerdialer.engine.UserLoginLogoutHandler;
import com.powerdialer.engine.acd.ACDEngine;
import com.powerdialer.engine.dialer.DialerFacade;
import com.powerdialer.model.Status;
import com.powerdialer.model.User;
import com.powerdialer.redis.message.UserCurrentStatusMessage;
import com.powerdialer.redis.message.UserMessage;
import com.powerdialer.redis.message.UserSessionMessage;
import com.powerdialer.util.Constants;
import com.powerdialer.util.StripedExecutor;

@RunWith(MockitoJUnitRunner.class)
public class UserSubscriberTest {

  private static final String RAW_MESSAGE = "rawMessage";
  private UserMessage message;
  private User user = new User();

  @Mock
  private Status status;

  @Mock
  private ACDEngine acdEngine;

  @Mock
  private UserLoginLogoutHandler userLoginLogoutHandler;

  @Mock
  private CallCleaner callCleaner;

  @Mock
  private CallUpToDateKeeper callUpToDateKeeper;

  @Mock
  private ObjectMapper objectMapper;

  @Mock
  private UserHolder userHolder;

  @Mock
  private RedisUtil redisUtil;

  @Mock
  private DialerFacade dialerFacade;

  @Mock
  private CallEventPublisher callEventPublisher;

  @Mock
  private InteractionNotifierPublisher interactionNotifierPublisher;

  @Mock
  private StripedExecutor userEventStripedExecutor;

  @InjectMocks
  private UserSubscriber userSubscriber;

  @Before
  public void init() throws Exception {
    user.setId(1L);
    doAnswer(inv -> {
      inv.<Runnable>getArgument(1).run();
      return null;
    }).when(userEventStripedExecutor).execute(anyLong(), any(Runnable.class));
    when(dialerFacade.getCampaignContexts()).thenReturn(Collections.emptyMap());
    when(callUpToDateKeeper.getUserCalls(user)).thenReturn(Collections.emptyList());
    Whitebox.setInternalState(userSubscriber, "objectMapper", objectMapper);
  }

  @Test
  public void givenStatusMessageWhenOnMessageThenUpdateUserStatusInfo() throws IOException {
    Long statusSetTimestamp = 123L;
    UserCurrentStatusMessage message = new UserCurrentStatusMessage();
    message.setUserId(user.getId());
    message.setStatus(status);
    message.setStatusSetDate(statusSetTimestamp);
    when(objectMapper.readValue(RAW_MESSAGE, UserCurrentStatusMessage.class)).thenReturn(message);
    when(userHolder.getUser(user.getId())).thenReturn(user);

    userSubscriber.onMessage(Constants.REDIS_CHANNEL.channelUserCurrentStatus.toString(), RAW_MESSAGE);

    assertThat(user.getStatus(), is(status));
    assertThat(user.getStatusSetDate().getTime(), is(statusSetTimestamp));
  }

  @Test
  public void givenAvailableForDialerCallsStatusMessageWhenOnMessageThenAddUserToAcdEngine() throws IOException {
    UserCurrentStatusMessage message = new UserCurrentStatusMessage();
    message.setUserId(user.getId());
    message.setStatus(status);
    message.setStatusSetDate(111L);
    when(objectMapper.readValue(RAW_MESSAGE, UserCurrentStatusMessage.class)).thenReturn(message);
    when(status.isAvailableForDialerCalls()).thenReturn(true);
    when(userHolder.getUser(user.getId())).thenReturn(user);

    userSubscriber.onMessage(Constants.REDIS_CHANNEL.channelUserCurrentStatus.toString(), RAW_MESSAGE);

    verify(acdEngine).addToIdleUsers(user);
  }

  @Test
  public void givenUnavailableForDialerCallsStatusMessageWhenOnMessageThenRemoveUserFromAcdEngine() throws IOException {
    UserCurrentStatusMessage message = new UserCurrentStatusMessage();
    message.setUserId(user.getId());
    message.setStatus(status);
    message.setStatusSetDate(111L);
    when(objectMapper.readValue(RAW_MESSAGE, UserCurrentStatusMessage.class)).thenReturn(message);
    when(status.isAvailableForDialerCalls()).thenReturn(false);
    when(userHolder.getUser(user.getId())).thenReturn(user);

    userSubscriber.onMessage(Constants.REDIS_CHANNEL.channelUserCurrentStatus.toString(), RAW_MESSAGE);

    verify(acdEngine).removeFromIdleUsers(user);
  }

  @Test
  public void givenLoginMessageWhenOnMessageThenAddUserToAcdEngine() throws IOException {
    UserSessionMessage message = new UserSessionMessage();
    message.setUserId(user.getId());
    message.setUser(user);
    message.setType(UserSessionMessage.SESSION_MESSAGE_TYPE.LOGIN);
    when(objectMapper.readValue(RAW_MESSAGE, UserSessionMessage.class)).thenReturn(message);

    userSubscriber.onMessage(Constants.REDIS_CHANNEL.channelUserSession.toString(), RAW_MESSAGE);

    verify(userLoginLogoutHandler).handleLogin(user);
  }

  @Test
  public void givenLogoutMessageWhenOnMessageThenRemoveUserFromAcdEngine() throws IOException {
    UserSessionMessage message = new UserSessionMessage();
    message.setUserId(user.getId());
    message.setType(UserSessionMessage.SESSION_MESSAGE_TYPE.LOGOUT);
    when(objectMapper.readValue(RAW_MESSAGE, UserSessionMessage.class)).thenReturn(message);
    when(userHolder.removeUser(user.getId())).thenReturn(user);

    userSubscriber.onMessage(Constants.REDIS_CHANNEL.channelUserSession.toString(), RAW_MESSAGE);

    verify(userLoginLogoutHandler).handleLogout(user);
  }

}
