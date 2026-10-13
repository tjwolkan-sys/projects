package com.powerdialer.engine.asterisk.fastagi;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import com.powerdialer.engine.boost.DynamicBoostManager;
import com.powerdialer.engine.predictive.beta.model.CallType;
import com.powerdialer.engine.predictive.beta.processor.PredictiveBetaCallManager;
import org.asteriskjava.fastagi.AgiChannel;
import org.asteriskjava.fastagi.AgiException;
import org.asteriskjava.fastagi.AgiHangupException;
import org.asteriskjava.fastagi.AgiRequest;
import org.asteriskjava.fastagi.BaseAgiScript;
import org.asteriskjava.fastagi.command.DialCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import com.powerdialer.engine.UserHolder;
import com.powerdialer.engine.acd.ACDEngine;
import com.powerdialer.engine.asterisk.ami.ManagerConnectionComposition;
import com.powerdialer.model.Call;
import com.powerdialer.model.Group;
import com.powerdialer.model.Tenant;
import com.powerdialer.model.User;
import com.powerdialer.redis.CallEventPublisher;
import com.powerdialer.util.Constants;
import com.powerdialer.util.LoggerDecorator;

/**
 * AGI script which is executed for every originate action of campaign having
 * progressive type. This script dials the agent (and finds new one if current
 * agent can't handle call) with given abandon timeout.
 */
public class DialWithAbandonTimeoutScript extends BaseAgiScript {

  private static final String TRANSFER_ENABLED_FOR_CALLEE_DIAL_OPTION = "t";
  private static final String MUSIC_ON_HOLD_CLASS_DIAL_OPTION = "m(%s)";
  private static final String RESERVATION_DIAL_OPTION = "b(agent_reservation,s,1(%s))";
  private static final String USER_EVENT_AGENT_ANSWER_DIAL_OPTION = "U(user_event_agent_predictive_answer,s,1)";
  /**
   * Asterisk {@code DIALSTATUS} value set when the calling (customer) channel hangs up during the
   * Dial. Agent-side failures (BUSY/NOANSWER/CHANUNAVAIL/CONGESTION) are NOT treated as caller gone.
   * Confirm against live YKB logs if the exact value differs in this Asterisk version.
   */
  private static final String CALLER_HANGUP_DIAL_STATUS = "CANCEL";
  /**
   * Asterisk {@code DIALSTATUS} channel variable and the value set when the dialed agent answered and
   * was bridged. Read synchronously by the AGI right after the Dial so agent-answer detection does not
   * depend on the asynchronous {@code AgentPredictiveAnswer} AMI event, which is processed on the
   * (possibly lagging) event worker.
   */
  private static final String DIAL_STATUS_VARIABLE = "${DIALSTATUS}";
  private static final String DIAL_STATUS_ANSWER = "ANSWER";
  /**
   * Poll interval used while waiting for a fresh (not-yet-tried) agent to free up, instead of
   * busy-spinning the AGI thread until the abandon timeout.
   */
  private static final long NO_FRESH_USER_POLL_INTERVAL_MS = 100L;
  /**
   * T4 — Dial komutunun agent'tan cevap bekleme suresi (saniye).
   *
   * <p>Onceden buraya {@code campaign.abandonTimeout} veriliyordu; ayni deger
   * {@link #isAbandonTimeoutExceed} icinde "musteri ne kadar beklerse abandon" olarak da
   * kullanildigi icin TEK deneme musterinin TUM sabrini tuketiyor, ikinci agent'a sira
   * gelmiyordu (uretimde 60 sn / 60 sn).
   *
   * <p>Agent tarafinda otomatik cevaplama var ({@code Answer-Mode: Auto}); olculen baglanma
   * suresi p50 = 216 ms, cagrilarin %79,8'i 0,5 sn altinda baglaniyor. 5 sn'de baglanmayan
   * agent pratikte musait degildir — beklemek yerine siradaki agent denenmelidir.
   *
   * <p>Deger secimi (olculen kayip riski): 3 sn -> 27 cagri, 5 sn -> ~18 cagri, 10 sn -> ~7 cagri.
   * 5 sn secildi: 3 sn+ surede baglananlarin %95'inde agent zaten MESGULDU (dial onun bosalmasini
   * bekliyordu) ve o anlarda ortalama 13,6 baska agent bostaydi — dial kesilse cagri baska
   * agent'a giderdi. Riski %0,4.
   */
  private static final int DIAL_ANSWER_WAIT_SEC = 5;
  private final LoggerDecorator LOG;
  private final Logger logger = LoggerFactory.getLogger(DialWithAbandonTimeoutScript.class);

  @Autowired
  private ACDEngine acdEngine;

  @Autowired
  private ManagerConnectionComposition managerConnectionComposition;

  @Autowired
  private UserHolder userHolder;

  @Autowired
  private CallEventPublisher callEventPublisher;

  @Autowired
  private DynamicBoostManager dynamicBoostManager;

  @Autowired
  private PredictiveBetaCallManager predictiveBetaCallManager;

  @Autowired
  private AgentDialLockRegistry agentDialLockRegistry;


  public DialWithAbandonTimeoutScript(LoggerDecorator loggerDecorator) {
    LOG = loggerDecorator.getLogger(DialWithAbandonTimeoutScript.class);
  }

  /** Outcome of a single Dial attempt to an agent. */
  private enum DialOutcome {
    /** Agent answered and the customer is bridged. */
    ANSWERED,
    /** Dial completed without an answer (agent-side failure); another agent may be tried. */
    NOT_ANSWERED,
    /** Agent could not be dialed (locked by another call or no longer available for calls). */
    SKIPPED,
    /** Customer channel is gone; stop trying to bridge an agent. */
    CALLER_GONE
  }

  @Override
  public void service(AgiRequest request, AgiChannel channel) throws AgiException {
    Call call = null;
    try {
      String callId = channel.getVariable(Constants.MANAGER_ACTION_VARIABLE.ORIGIN_ID.getParam());
      String channelIp = request.getRemoteAddress().getHostAddress();
      call = managerConnectionComposition.getOriginatedCall(callId);
      LOG.info("Executing AGI script for " + call);
      if (call == null) {
        LOG.warn("Can't find call with id: " + callId + " to process AgiRequest");
      } else {
        processDialCommand(call, channelIp);
      }
    } finally {
      LOG.info("Finished AGI script for " + call);
    }
  }

  /**
   * Send dialCommand for given call.
   *
   * <p>Bridges the answered customer to an agent within the abandon window. To stop the uncontrolled
   * same-agent retry storm (an agent whose dial failed used to be re-added to idle and immediately
   * re-selected by ACD), every agent dialed in this call is excluded from further dials for the rest
   * of this customer-answered window ({@code triedUsers}). When the customer channel is gone the loop
   * stops immediately and lets the normal hangup flow set the final result. When no fresh agent is
   * available the thread polls instead of busy-spinning. Excluded agents are returned to the idle
   * pool when the script finishes.
   * @param call
   * @param channelIp
   * @throws AgiException
   */
  private void processDialCommand(Call call, String channelIp) throws AgiException {
    long startTime = System.currentTimeMillis();

    if(call.getCampaign().isPredictiveCampaign() && call.getCampaign().getPredictiveStrategy() == Constants.PREDICTIVE_STRATEGY.ALPHA) {
      dynamicBoostManager.onCallAnsweredByClient(call);
    }

    Map<Long, User> triedUsers = new LinkedHashMap<>();
    try {
      acdEngine.trySearchLessOccupiedUser(call);
      DialOutcome outcome = dialCurrentUser(call, channelIp, triedUsers);
      while (outcome != DialOutcome.ANSWERED && !call.isAnsweredByUser() && call.getTelephonyResultCode() == null && outcome != DialOutcome.CALLER_GONE) {
        if (isAbandonTimeoutExceed(call, startTime)) {
          processAbandonTimeout(call);
          break;
        }
        if (selectNextFreshUser(call, triedUsers)) {
          if (isAbandonTimeoutExceed(call, startTime)) {
            processAbandonTimeout(call);
            break;
          }
          outcome = dialCurrentUser(call, channelIp, triedUsers);
        } else {
          // No fresh (not-yet-tried) agent available right now. Poll briefly instead of
          // busy-spinning the AGI thread; a busy agent may free up before the abandon timeout.
          waitForFreshUser();
        }
      }
    } finally {
      returnTriedUsersToIdle(triedUsers);
    }
  }

  /**
   * Send the Dial command to {@code call.getUser()} and classify the outcome. The dialed agent is
   * recorded in {@code triedUsers} unless it answered, so it is not dialed again within this call.
   * Unlike the previous behaviour, a not-answered agent is NOT re-added to the idle pool here;
   * {@link #returnTriedUsersToIdle(Map)} restores still-available agents when the script finishes.
   * @param call
   * @param channelIp
   * @param triedUsers agents already dialed in this call
   * @return the dial outcome
   * @throws AgiException
   */
  private DialOutcome dialCurrentUser(Call call, String channelIp, Map<Long, User> triedUsers) throws AgiException {
    User user = call.getUser();
    if (!tryLockUser(user, call.getId())) {
      // T5: KARA LISTEYE ALINMAZ. Kilit basarisizligi "bu agent o an baska bir cagri tarafindan
      // araniyor" demektir — GECICI bir durumdur, agent'in telefonu bu cagri icin HIC calmadi.
      // Eskiden burada excludeForThisCall cagriliyordu ve agent cagri boyunca kalici olarak
      // yaniyordu; birkac saniyede havuzun tamami tukeniyor, sonra 100 ms'lik poll dongusu ACD'nin
      // onerdigi HER agent'i kendi kara listesi yuzunden reddediyordu (olculen: bekleme boyunca
      // %99,9 vakada hic denenmemis agent vardi, ortalama 20,8 kisi).
      return DialOutcome.SKIPPED;
    }
    try {
      user.setDialInProcess(true);
      if (!user.getStatus().isAvailableForDialerCalls()) {
        // T5: KARA LISTEYE ALINMAZ — ayni gerekce (gecici mesguliyet, telefon calmadi).
        return DialOutcome.SKIPPED;
      }
      setSIPHeader("Answer-Mode", "Auto");
      setCdrVariables(call);
      String onHoldMusicClass = getFullVariable("${MOH" + user.getGroup().getId() + "}");
      if (!StringUtils.isEmpty(onHoldMusicClass))
        exec("SetMusicOnHold", onHoldMusicClass);
      sendAgentCalledByDialerEvent(call);
      LOG.info("Sending Dial command for " + call);
      DialOutcome outcome = sendDialAndClassifyOutcome(call, channelIp);
      LOG.info("dialCurrentUser() dial outcome=" + outcome + " for user=" + user + ", call=" + call.getId());
      if (outcome == DialOutcome.ANSWERED) {
        acdEngine.updateUserLastCallEndDate(user, new Date());
      } else {
        // SR1: do not re-dial this agent for the rest of this call and do not re-add it to idle now;
        // returnTriedUsersToIdle() restores it when the script finishes.
        excludeForThisCall(triedUsers, user);
        LOG.warn(call + " not answered by " + user + "; excluding agent from further dials for this call.");
      }
      return outcome;
    } finally {
      if (!call.isTransferred()) {
        // On transfer the AMI handler (handleNewTransferChannel) already cleared dialInProcess and
        // released this agent's dial lock so a new call could re-acquire it; don't clobber that here.
        user.setDialInProcess(false);
      }
      releaseUser(user, call.getId());
    }
  }

  /**
   * Send the Dial command and classify whether the agent answered, the dial failed, or the customer
   * channel is gone.
   * @param call
   * @param channelIp
   * @return the dial outcome
   * @throws AgiException
   */
  private DialOutcome sendDialAndClassifyOutcome(Call call, String channelIp) throws AgiException {
    try {
      // T4: cevap bekleme suresi artik abandon_timeout'tan BAGIMSIZ (bkz. DIAL_ANSWER_WAIT_SEC).
      // Abandon karari degismedi; onu hala isAbandonTimeoutExceed + campaign.abandonTimeout verir.
      sendCommand(new DialCommand(getDialTarget(call, channelIp), DIAL_ANSWER_WAIT_SEC, getDialOptions(call, channelIp)));
    } catch (AgiHangupException e) {
      LOG.info("Caller hung up during dial for " + call + "; stopping agent dial attempts.");
      return DialOutcome.CALLER_GONE;
    }
    if (isAgentAnswered(call)) {
      return DialOutcome.ANSWERED;
    }
    if (isCallerGone(call)) {
      LOG.info("Caller gone after dial for " + call + "; stopping agent dial attempts.");
      return DialOutcome.CALLER_GONE;
    }
    return DialOutcome.NOT_ANSWERED;
  }

  /**
   * Whether the dialed agent answered and was bridged to the customer. Prefers Asterisk's own
   * synchronous {@code DIALSTATUS=ANSWER} so the decision does not depend on the asynchronous
   * {@code AgentPredictiveAnswer} event ({@link Call#isAnsweredByUser()}), which is handled on the AMI
   * event worker and can lag under predictive load. Under that lag the flag is still false when the
   * Dial returns, which previously caused a real answer to be misclassified as {@code NOT_ANSWERED};
   * the AGI then churned {@code call.user} to the next agent and abandoned a call the agent actually
   * handled, dropping the agent's webphone result code. If the event has already been processed the
   * flag is honoured too.
   * @param call the call whose dial just returned
   * @return true if the agent answered
   */
  private boolean isAgentAnswered(Call call) {
    if (call.isAnsweredByUser()) {
      return true;
    }
    try {
      return DIAL_STATUS_ANSWER.equalsIgnoreCase(getFullVariable(DIAL_STATUS_VARIABLE));
    } catch (AgiException e) {
      // A probe failure (including a caller-side hangup) must not invent an answer; the caller-gone
      // and not-answered paths handle those cases.
      return false;
    }
  }

  /**
   * Return true if the customer channel is gone, so dialing further agents is pointless. Detection
   * is defensive (SR3): the customer hangup may already be finalized by the AMI hangup handler, the
   * Dial may report a caller-side {@code DIALSTATUS}, or probing the channel may itself signal a
   * hangup. Agent-side failures (BUSY/NOANSWER/CHANUNAVAIL/CONGESTION) are not treated as caller
   * gone, so the next agent can still be tried.
   * @param call
   * @return true if the customer is gone
   */
  private boolean isCallerGone(Call call) {
    if (call.getTelephonyResultCode() != null) {
      return true;
    }
    try {
      return CALLER_HANGUP_DIAL_STATUS.equalsIgnoreCase(getFullVariable(DIAL_STATUS_VARIABLE));
    } catch (AgiHangupException e) {
      return true;
    } catch (AgiException e) {
      // A probe failure must not change dial logic; assume the customer is still present.
      return false;
    }
  }

  /**
   * Ask ACD for the next agent and ensure it has not already been tried in this call. Returns true
   * only when a fresh agent is selected (left in {@code call.getUser()}).
   * @param call
   * @param triedUsers agents already dialed in this call
   * @return true if a fresh agent was selected
   */
  private boolean selectNextFreshUser(Call call, Map<Long, User> triedUsers) {
    acdEngine.processACD(call);
    User selected = call.getUser();
    if (selected == null) {
      return false;
    }
    if (triedUsers.containsKey(selected.getId())) {
      // ACD re-offered an already-tried agent; do not re-dial within this call (SR1). It stays held
      // out of selection (kept in triedUsers) and is restored to idle when the script finishes.
      call.setUser(null);
      return false;
    }
    return true;
  }

  /**
   * Record an agent as already dialed (or unavailable) in this call so it is not reselected.
   * @param triedUsers agents already dialed in this call
   * @param user agent to exclude
   */
  private void excludeForThisCall(Map<Long, User> triedUsers, User user) {
    if (user != null && user.getId() != null) {
      triedUsers.put(user.getId(), user);
    }
  }

  /**
   * Sleep briefly so the loop polls for a freed agent instead of busy-spinning the AGI thread. The
   * abandon timeout is still enforced by the surrounding loop.
   */
  private void waitForFreshUser() {
    try {
      Thread.sleep(NO_FRESH_USER_POLL_INTERVAL_MS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /**
   * Restore agents that were tried (and held out) during this call back to the idle pool, preserving
   * the original online + available guard so other calls can use them again.
   * @param triedUsers agents dialed in this call
   */
  private void returnTriedUsersToIdle(Map<Long, User> triedUsers) {
    for (User user : triedUsers.values()) {
      if (user != null
          && !user.isDialInProcess()
          && userHolder.getUser(user.getId()) != null
          && user.getStatus() != null
          && user.getStatus().isAvailableForDialerCalls()) {
        acdEngine.addToIdleUsers(user);
      }
    }
  }

  /**
   * Try to lock user for processing Dial command
   * @param user
   * @return true if user successfully locked for processing Dial command
   */
  private boolean tryLockUser(User user, String callId) {
    boolean locked = user != null && agentDialLockRegistry.tryLock(user.getId(), callId);
    LOG.info("tryLockUser() user=" + user + ", callId=" + callId + " locked=" + locked);
    return locked;
  }

  /**
   * Set SIP header. 51 is because the asterisk limits do 50 calls to the func
   * "SipAddHeader" and this way it wont interfere
   */
  private void setSIPHeader(String headerName, String headerValue) throws AgiException {
    setVariable("__SIPADDHEADER51", headerName + ":" + headerValue);
  }

  private void setCdrVariables(Call call) {
    setCdrVariable(Constants.CDR_VARIABLE.AGENT_ID.getParam(), call.getUser().getId().toString());
    Optional.of(call).map(Call::getUser).map(User::getGroup).map(Group::getId).ifPresent(groupId -> setCdrVariable(Constants.CDR_VARIABLE.GROUP_ID.getParam(), groupId.toString()));
    Optional.of(call).map(Call::getUser).map(User::getTeamId).ifPresent(teamId -> setCdrVariable(Constants.CDR_VARIABLE.TEAM_ID.getParam(), teamId.toString()));
    Optional.of(call).map(Call::getUser).map(User::getSessionId).ifPresent(sessionId -> setCdrVariable(Constants.CDR_VARIABLE.SESSION_ID.getParam(), sessionId.toString()));
    Optional.of(call).map(Call::getUser).map(User::getTenant).map(Tenant::getId)
        .ifPresent(tenantId -> setCdrVariable(Constants.CDR_VARIABLE.TENANT_ID.getParam(), tenantId.toString()));
    Optional.of(call).map(Call::getId).ifPresent(callId -> setCdrVariable(Constants.CDR_VARIABLE.PARENT_ID.getParam(), callId));
  }

  private void setCdrVariable(String key, String value) {
    try {
      setVariable("CDR(" + key + ")", value);
    } catch (AgiException e) {
      throw new RuntimeException(e);
    }
  }

  public int sendAgentCalledByDialerEvent(Call call) throws AgiException {
    return exec("UserEvent", "AgentCalledByDialer,Agent: " + call.getUser().getUsername()
        + ",DialedNumber: " + call.getCalledNumber() + ",Channel: " + getChannel().getName() + ",ParentId: " + call.getId());
  }

  /**
   * Get dial target. IP of agent's registrar == IP of chanel set target as
   * SIP/81XXX else set target as SIP/{user's_registrar_ip}/81XXX
   */
  private String getDialTarget(Call call, String channelIp) {
    String userIp = call.getUser().getServer().getIp();
    StringBuilder stringBuilder = new StringBuilder("SIP/");
    if (channelIp.equalsIgnoreCase(userIp)) {
      stringBuilder.append(call.getUser().getUsername());
    } else {
      stringBuilder.append(userIp).append("/").append(call.getUser().getUsername());
    }
    return stringBuilder.toString();
  }

  private String getDialOptions(Call call, String channelIp) {
    StringBuilder options = new StringBuilder(TRANSFER_ENABLED_FOR_CALLEE_DIAL_OPTION);
    options.append(USER_EVENT_AGENT_ANSWER_DIAL_OPTION);
    if (channelIp.equalsIgnoreCase(call.getUser().getServer().getIp()))
      options.append(String.format(RESERVATION_DIAL_OPTION, call.getUser().getUsername()));
    if (StringUtils.hasText(call.getCampaign().getMohClassCallingParty()))
      options.append(String.format(MUSIC_ON_HOLD_CLASS_DIAL_OPTION, call.getCampaign().getMohClassCallingParty()));
    return options.toString();
  }

  /**
   * Check if abandon timeout exceeded for call
   * @return true if abandon timeout exceeded
   */
  private boolean isAbandonTimeoutExceed(Call call, long startTime) {
    int abandonTimeout = call.getCampaign().getAbandonTimeout() * 1000;
    long elapsedTime = System.currentTimeMillis() - startTime;
    return elapsedTime > abandonTimeout;
  }

  /**
   * Processing call with exceeded abandon timeout
   */
  private void processAbandonTimeout(Call call) throws AgiException {
    LOG.info("Abandon timeout exceed for: " + call, Constants.LOG_MODULE.CAMPAIGN, call.getCampaign());
    call.getCampaign().incrementAbandonedCallsSize();
    call.setAbandoned(true);

    if (call.getCampaign().isPredictiveCampaign() && call.getCampaign().getPredictiveStrategy() == Constants.PREDICTIVE_STRATEGY.ALPHA) {
      dynamicBoostManager.onCallAbandoned(call);
    } else if (call.getCampaign().isPredictiveCampaign() && call.getCampaign().getPredictiveStrategy() == Constants.PREDICTIVE_STRATEGY.BETA){
      // Abandon timeout exceeded for call
      logger.info("Predictive Beta - Call is in ABANDONED state, incrementing abandoned call size:  callId: {} ", call.getId());
      logger.info("Predictive Beta - Call is in ABANDONED state, decrementing active call size:  callId: {} ", call.getId());
      predictiveBetaCallManager.updateCallState(call, CallType.ABANDONED,true);
      predictiveBetaCallManager.updateCallState(call, CallType.ACTIVE,false);
    }

    if (StringUtils.hasLength(call.getCampaign().getAbandonIvr())) {
      callEventPublisher.publishCallAbandonIVREvent(call.getId());
      sendCommand(new DialCommand("Local/" + call.getCampaign().getAbandonIvr() + "@outbound-to-media", call.getCampaign().getRingTimeout(), null));
    } else {
      hangupNotFinished(call);
    }
  }

  /**
   * Try to hangup channel if not already hanged
   * @param call
   * @throws AgiException
   */
  private void hangupNotFinished(Call call) throws AgiException {
    try {
      hangup();
    } catch (AgiHangupException e) {
      LOG.info("Channel is already hanged for " + call);
    }
  }

  /**
   * Remove user from lock list
   * @param user
   */
  private void releaseUser(User user, String callId) {
    if (user != null) {
      boolean released = agentDialLockRegistry.release(user.getId(), callId);
      LOG.info("releaseUser() user=" + user + ", callId=" + callId + " released=" + released);
    }
  }

}
