package com.powerdialer.engine.asterisk.fastagi;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.InetAddress;

import org.asteriskjava.fastagi.AgiChannel;
import org.asteriskjava.fastagi.AgiHangupException;
import org.asteriskjava.fastagi.AgiRequest;
import org.asteriskjava.fastagi.command.DialCommand;
import org.asteriskjava.fastagi.reply.AgiReply;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.powermock.reflect.Whitebox;

import com.powerdialer.engine.UserHolder;
import com.powerdialer.engine.acd.ACDEngine;
import com.powerdialer.engine.asterisk.ami.ManagerConnectionComposition;
import com.powerdialer.model.Call;
import com.powerdialer.model.Group;
import com.powerdialer.model.Server;
import com.powerdialer.model.Status;
import com.powerdialer.model.User;
import com.powerdialer.redis.CallEventPublisher;
import com.powerdialer.util.Constants;
import com.powerdialer.util.LoggerDecorator;

/**
 * Tests for {@link DialWithAbandonTimeoutScript}.
 *
 * <p>Covers the regression behaviours (successful dial, multi-agent dial, abandon with/without IVR)
 * and the FIX-320176 fixes: an agent that fails a dial is not re-dialed within the same call
 * (same-user retry storm), the loop stops immediately when the customer channel is gone, and the
 * loop polls instead of busy-spinning when no fresh agent is available.
 *
 * <p>Uses {@link MockitoJUnitRunner.Silent} because scenarios exercise different code paths and not
 * every shared stub is used by every test.
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class DialWithAbandonTimeoutScriptTest {

  private static final String DIALSTATUS_VARIABLE = "${DIALSTATUS}";

  @Mock
  private ACDEngine acdEngine;

  @Mock
  private ManagerConnectionComposition managerConnectionComposition;

  @Mock
  private UserHolder userHolder;

  @Mock
  private CallEventPublisher callEventPublisher;

  private AgiRequest mockAgiRequest;
  private AgiChannel mockAgiChannel;
  private DialWithAbandonTimeoutScript script;
  private AgiReply mockAgiReply;
  private Call call;

  @Before
  public void init() throws Exception {
    LoggerDecorator loggerDecorator = mock(LoggerDecorator.class, RETURNS_DEEP_STUBS);
    script = spy(new DialWithAbandonTimeoutScript(loggerDecorator));
    Whitebox.setInternalState(script, "acdEngine", acdEngine);
    Whitebox.setInternalState(script, "managerConnectionComposition", managerConnectionComposition);
    Whitebox.setInternalState(script, "userHolder", userHolder);
    Whitebox.setInternalState(script, "callEventPublisher", callEventPublisher);
    Whitebox.setInternalState(script, "agentDialLockRegistry", new AgentDialLockRegistry());

    mockAgiRequest = mock(AgiRequest.class);
    mockAgiChannel = mock(AgiChannel.class);
    mockAgiReply = mock(AgiReply.class);

    call = mock(Call.class, RETURNS_DEEP_STUBS);
    when(call.getTelephonyResultCode()).thenReturn(null);
    when(call.getId()).thenReturn("dl-1");
    when(call.getCampaign().isPredictiveCampaign()).thenReturn(false);
    when(call.getCampaign().getAbandonTimeout()).thenReturn(1);

    doNothing().when(script).setVariable(any(), any());
    doReturn(0).when(script).sendAgentCalledByDialerEvent(any());
    doReturn(0).when(script).exec(any(), any());
    // Default channel variable reads (MOH lookup, DIALSTATUS probe) return empty.
    doReturn("").when(script).getFullVariable(any());

    doReturn(call).when(managerConnectionComposition).getOriginatedCall("dl-1");
    doReturn("dl-1").when(mockAgiChannel).getVariable(Constants.MANAGER_ACTION_VARIABLE.ORIGIN_ID.getParam());
    doReturn(InetAddress.getByName("127.0.0.1")).when(mockAgiRequest).getRemoteAddress();
  }

  private User user(long id, String username) {
    Status status = new Status();
    status.setAvailableForDialerCalls(true);
    Server server = new Server();
    server.setIp("192.168.1.1");
    Group group = new Group();
    group.setId(1L);
    User user = new User();
    user.setId(id);
    user.setUsername(username);
    user.setStatus(status);
    user.setServer(server);
    user.setGroup(group);
    return user;
  }

  /**
   * Agent answers on the first dial: exactly one dial, no ACD re-selection, no abandon.
   */
  @Test
  public void testSuccessfulService() throws Exception {
    when(call.getUser()).thenReturn(user(1L, "81341"));
    when(call.isAnsweredByUser()).thenReturn(true);
    doReturn(mockAgiReply).when(script).sendCommand(any(DialCommand.class));

    script.service(mockAgiRequest, mockAgiChannel);

    verify(script, times(1)).sendCommand(any(DialCommand.class));
    verify(acdEngine, never()).processACD(any());
    verify(script, never()).hangup();
  }

  /**
   * First agent does not answer; ACD offers a different agent who answers. Two dials, no abandon.
   */
  @Test
  public void testSecondAgentAnswers() throws Exception {
    final User first = user(1L, "81348");
    final User second = user(2L, "81342");
    when(call.getUser()).thenReturn(first);
    // Answered only once the second (different) agent is the current user.
    when(call.isAnsweredByUser()).thenAnswer(invocation -> call.getUser() == second);
    doAnswer(invocation -> {
      when(call.getUser()).thenReturn(second);
      return null;
    }).when(acdEngine).processACD(any());
    doReturn(mockAgiReply).when(script).sendCommand(any(DialCommand.class));

    script.service(mockAgiRequest, mockAgiChannel);

    verify(script, times(2)).sendCommand(any(DialCommand.class));
    verify(script, never()).hangup();
  }

  /**
   * FIX-320176 core: even if ACD keeps re-offering the same agent who fails, that agent is dialed
   * only once and the call ends in abandon (no same-user retry storm).
   */
  @Test
  public void testSameFailingAgentIsDialedOnlyOnce() throws Exception {
    final User failing = user(1L, "81348");
    when(call.getUser()).thenReturn(failing);
    when(call.isAnsweredByUser()).thenReturn(false);
    // Simulate the bug condition: ACD always re-selects the same (failing) agent.
    doAnswer(invocation -> {
      when(call.getUser()).thenReturn(failing);
      return null;
    }).when(acdEngine).processACD(any());
    doReturn(mockAgiReply).when(script).sendCommand(any(DialCommand.class));
    doNothing().when(script).hangup();

    script.service(mockAgiRequest, mockAgiChannel);

    verify(script, times(1)).sendCommand(any(DialCommand.class));
    verify(script, times(1)).hangup();
  }

  /**
   * Customer hangup detected via {@code DIALSTATUS}: the loop stops immediately, no ACD re-selection
   * and no abandon processing (the normal hangup flow sets the final result).
   */
  @Test
  public void testCallerHangupViaDialStatusStopsDialing() throws Exception {
    when(call.getUser()).thenReturn(user(1L, "81348"));
    when(call.isAnsweredByUser()).thenReturn(false);
    doReturn(mockAgiReply).when(script).sendCommand(any(DialCommand.class));
    // DIALSTATUS probe reports the calling channel was cancelled (customer gone).
    doReturn("CANCEL").when(script).getFullVariable(DIALSTATUS_VARIABLE);

    script.service(mockAgiRequest, mockAgiChannel);

    verify(script, times(1)).sendCommand(any(DialCommand.class));
    verify(acdEngine, never()).processACD(any());
    verify(script, never()).hangup();
  }

  /**
   * Customer hangup surfaced as {@link AgiHangupException} from the Dial command: the loop stops
   * immediately, no ACD re-selection.
   */
  @Test
  public void testCallerHangupViaHangupExceptionStopsDialing() throws Exception {
    when(call.getUser()).thenReturn(user(1L, "81348"));
    when(call.isAnsweredByUser()).thenReturn(false);
    doThrow(new AgiHangupException()).when(script).sendCommand(any(DialCommand.class));

    script.service(mockAgiRequest, mockAgiChannel);

    verify(script, times(1)).sendCommand(any(DialCommand.class));
    verify(acdEngine, never()).processACD(any());
    verify(script, never()).hangup();
  }

  /**
   * No fresh agent available: the loop polls in bounded fashion (not a busy-spin) until the abandon
   * timeout, never dials, and finally abandons.
   */
  @Test
  public void testNoFreshAgentPollsBoundedThenAbandons() throws Exception {
    when(call.getUser()).thenReturn(null);
    when(call.isAnsweredByUser()).thenReturn(false);
    doAnswer(invocation -> {
      when(call.getUser()).thenReturn(null);
      return null;
    }).when(acdEngine).processACD(any());
    doNothing().when(script).hangup();

    script.service(mockAgiRequest, mockAgiChannel);

    verify(script, never()).sendCommand(any(DialCommand.class));
    verify(script, times(1)).hangup();
    // ~10 polls over a 1s window at 100ms; an upper bound proves bounded polling, not busy-spin.
    verify(acdEngine, atMost(20)).processACD(any());
  }

  /**
   * Abandon with IVR configured: after the failed agent, the abandon IVR is dialed and the abandon
   * IVR event is published.
   */
  @Test
  public void testAbandonTimeoutExceedWithIvr() throws Exception {
    final User failing = user(1L, "81348");
    when(call.getUser()).thenReturn(failing);
    when(call.isAnsweredByUser()).thenReturn(false);
    when(call.getCampaign().getAbandonIvr()).thenReturn("111222");
    when(call.getCampaign().getRingTimeout()).thenReturn(30);
    doAnswer(invocation -> {
      when(call.getUser()).thenReturn(failing);
      return null;
    }).when(acdEngine).processACD(any());
    doNothing().when(callEventPublisher).publishCallAbandonIVREvent(any());
    doReturn(mockAgiReply).when(script).sendCommand(any(DialCommand.class));

    script.service(mockAgiRequest, mockAgiChannel);

    // One agent dial + one abandon-IVR dial.
    verify(script, times(2)).sendCommand(any(DialCommand.class));
    verify(callEventPublisher, times(1)).publishCallAbandonIVREvent("dl-1");
  }

  /**
   * An agent currently locked by another concurrent dialer call (e.g. still bridged/blocked on its
   * own Dial) must not be dialed: {@code tryLockUser} fails via the shared {@link
   * AgentDialLockRegistry}, the agent is skipped, and with no other fresh agent the call abandons.
   */
  @Test
  public void testAgentLockedByAnotherCallIsSkipped() throws Exception {
    AgentDialLockRegistry registry = Whitebox.getInternalState(script, "agentDialLockRegistry");
    registry.tryLock(1L, "another-active-call");
    when(call.getUser()).thenReturn(user(1L, "81348"));
    when(call.isAnsweredByUser()).thenReturn(false);
    // After the locked agent is skipped, ACD offers no fresh user, so the call polls then abandons.
    doAnswer(invocation -> {
      when(call.getUser()).thenReturn(null);
      return null;
    }).when(acdEngine).processACD(any());
    doNothing().when(script).hangup();

    script.service(mockAgiRequest, mockAgiChannel);

    verify(script, never()).sendCommand(any(DialCommand.class));
    verify(script, times(1)).hangup();
  }

  /**
   * AMI event-worker lag regression: the agent actually answered and the customer was bridged
   * (Asterisk's own synchronous {@code DIALSTATUS=ANSWER}), but the async {@code AgentPredictiveAnswer}
   * event that sets {@code answeredByUser} is still queued on the lagging event worker, so
   * {@code isAnsweredByUser()} is still false when the AGI classifies the dial outcome. The AGI must
   * trust its synchronous DIALSTATUS and classify ANSWERED: dial exactly once, never re-select another
   * agent (no {@code call.user} churn) and never abandon. Without this, the agent who actually handled
   * the call is dropped from {@code call.user}, the call is finalized as 78/84 abandon, and the agent's
   * webphone result code is rejected as "not the assigned user".
   */
  @Test
  public void testAgentAnsweredViaDialStatusWhenAnsweredFlagLagged() throws Exception {
    when(call.getUser()).thenReturn(user(1L, "81570"));
    when(call.isAnsweredByUser()).thenReturn(false); // lagged event worker: flag not set yet
    doReturn(mockAgiReply).when(script).sendCommand(any(DialCommand.class));
    doNothing().when(script).hangup();
    // Asterisk's synchronous Dial result: the agent answered and the customer was bridged.
    doReturn("ANSWER").when(script).getFullVariable(DIALSTATUS_VARIABLE);

    script.service(mockAgiRequest, mockAgiChannel);

    verify(script, times(1)).sendCommand(any(DialCommand.class));
    verify(acdEngine, never()).processACD(any());
    verify(script, never()).hangup();
  }
}
