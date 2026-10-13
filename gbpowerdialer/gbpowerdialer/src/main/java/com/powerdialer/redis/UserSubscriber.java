package com.powerdialer.redis;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powerdialer.engine.CallCleaner;
import com.powerdialer.engine.CallUpToDateKeeper;
import com.powerdialer.engine.IncallAgentsRefresher;
import com.powerdialer.engine.UserHolder;
import com.powerdialer.engine.UserLoginLogoutHandler;
import com.powerdialer.engine.acd.ACDEngine;
import com.powerdialer.engine.dialer.CampaignContext;
import com.powerdialer.engine.dialer.DialerFacade;
import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.User;
import com.powerdialer.redis.message.UserCurrentStatusMessage;
import com.powerdialer.redis.message.UserSessionMessage;
import com.powerdialer.util.Constants;
import com.powerdialer.util.StripedExecutor;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.redisson.api.listener.MessageListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.sql.Date;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
@Lazy(false)
public class UserSubscriber implements MessageListener<String> {

  private static final Log LOG = LogFactory.getLog(UserSubscriber.class);
  private final ObjectMapper objectMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  private static final Pattern USER_ID_PATTERN = Pattern.compile("\"userId\"\\s*:\\s*(\\d+)");
  // [^{}]*? (lazy + brace'siz): user objesinin DOGRUDAN icindeki ILK "id" yakalanir. Eski hali
  // ([^}]* greedy) ilk '}' oncesindeki SON "id"yi, yani nested group/status gibi alt objenin
  // id'sini yakaliyordu (code-review bulgusu) -> LOGIN farkli serite gidip ayni agent'in mesaj
  // sirasi bozulabilirdi. Nested obje id'den once gelirse eslesme olmaz -> 0 serit'ine duser
  // (o kullanicinin TUM bu tur mesajlari ayni 0 seridine dustugu icin sira yine korunur).
  private static final Pattern USER_ID_IN_USER_PATTERN = Pattern.compile("\"user\"\\s*:\\s*\\{[^{}]*?\"id\"\\s*:\\s*(\\d+)");

  @Autowired
  private UserHolder userHolder;

  @Autowired
  private RedisUtil redisUtil;

  @Autowired
  private CallUpToDateKeeper callUpToDateKeeper;

  @Autowired
  private CallCleaner callCleaner;

  @Autowired
  private ACDEngine acdEngine;

  @Autowired
  private IncallAgentsRefresher incallAgentsRefresher;

  @Autowired
  private UserLoginLogoutHandler userLoginLogoutHandler;

  @Autowired
  private CallEventPublisher callEventPublisher;

  @Autowired
  private DialerFacade dialerFacade;

  @Autowired
  @Qualifier("userEventStripedExecutor")
  private StripedExecutor userEventStripedExecutor;


  @Autowired
  private InteractionNotifierPublisher interactionNotifierPublisher;

  @PostConstruct
  private void init() {
    redisUtil.subscribe(Constants.REDIS_CHANNEL.channelUserCurrentStatus.toString(), this);
    redisUtil.subscribe(Constants.REDIS_CHANNEL.channelUserSession.toString(), this);
  }

  @Override
  public void onMessage(CharSequence channel, String message) {
    if (Constants.REDIS_PING_MESSAGE.equals(message)) return;
    final String ch = channel.toString();
    final String msg = message;
    // SORUN 4: ayni agent'in mesajlari userId'ye gore ayni tek-thread serite yonlendirilir ->
    // Available/Mola gibi art arda mesajlar ASLA ters sirada islenemez (bellek bayat kalamaz).
    // Farkli agent'lar farkli seritlerde paralel isler; login firtinasinda throughput korunur.
    userEventStripedExecutor.execute(extractRoutingKeyFast(msg), () -> {
      try {
        if (Constants.REDIS_CHANNEL.channelUserCurrentStatus.toString().equals(ch))
          handleCurrentStatusMessage(msg);
        else if (Constants.REDIS_CHANNEL.channelUserSession.toString().equals(ch))
          handleSessionMessage(msg);
        else
          LOG.warn("Unknown channel: " + ch);
      } catch (Exception e) {
        LOG.error("Could not handle message in channel " + ch + ": " + msg, e);
      }
    });
  }

  /**
   * Fast userId extraction for routing key only.
   * Runs on Redisson thread — NO objectMapper, NO heavy parse.
   * Falls back to 0 if not found (still processed, ordering not critical for unknown users).
   */
  private long extractRoutingKeyFast(String raw) {
    // "userId": 12345  → LOGIN/LOGOUT/STATUS mesajlarında ortak
    Matcher m = USER_ID_PATTERN.matcher(raw);
    if (m.find()) {
      try { return Long.parseLong(m.group(1)); }
      catch (NumberFormatException ignore) {}
    }
    // LOGIN mesajında "user": { "id": 12345 }
    Matcher m2 = USER_ID_IN_USER_PATTERN.matcher(raw);
    if (m2.find()) {
      try { return Long.parseLong(m2.group(1)); }
      catch (NumberFormatException ignore) {}
    }
    return 0L;
  }


  private void  handleCurrentStatusMessage(String rawMessage) throws IOException {
    if (Constants.REDIS_PING_MESSAGE.equals(rawMessage))
      return;
    UserCurrentStatusMessage message = objectMapper.readValue(rawMessage, UserCurrentStatusMessage.class);
    LOG.info("Got message: " + message);
    User user = userHolder.getUser(message.getUserId());
    if (user == null) {
      LOG.warn("Could not find online user with id " + message.getUserId() + " to change status");
      return;
    }
    user.setStatus(message.getStatus());
    user.setStatusSetDate(new Date(message.getStatusSetDate()));
    handleStatusChange(user);
  }

  private void handleSessionMessage(String rawMessage) throws IOException {
    UserSessionMessage message = objectMapper.readValue(rawMessage, UserSessionMessage.class);
    LOG.info("Got message: " + message);
    if (UserSessionMessage.SESSION_MESSAGE_TYPE.LOGIN.equals(message.getType())) {
      handleLogin(message.getUser());
    } else if (UserSessionMessage.SESSION_MESSAGE_TYPE.LOGOUT.equals(message.getType())) {
      handleLogout(message.getUserId());
    }
    else {
      LOG.error("Unknown session message type: " + rawMessage);
    }
  }

  public void handleLogin(User user) {
    userHolder.addUser(user);
    userLoginLogoutHandler.handleLogin(user);
    handleStatusChange(user);
  }

  public void handleLogout(Long userId) {
    User user = userHolder.removeUser(userId);
    if (user != null) {
      userLoginLogoutHandler.handleLogout(user);
    }
  }

  /**
   * Kampanyanin INCALL agent sayacini yeniden hesap icin isaretler.
   *
   * <p>Task 325654: Bu metot eskiden kampanyanin TUM agent populasyonu icin Redis'ten
   * bulk status okuyordu ve {@code handleStatusChange} her status olayinda eslesen her
   * kampanya icin bunu cagiriyordu; maliyet "olay x kampanya x agent" seklinde
   * carpiliyordu. Artik yalniz dirty isaretlemesi yapilir (IO yok) ve gercek hesap
   * {@link IncallAgentsRefresher} tarafindan coalesce edilerek yapilir.</p>
   */
  private void updateIncallAgents(Campaign campaign) {
    incallAgentsRefresher.markDirty(campaign);
  }

  private void handleStatusChange(User user) {
    if (user.getStatus() == null) {
      LOG.info("User has an empty status - nothing to handle: " + user);
      return;
    }
    callUpToDateKeeper.onUserStatusUpdate(user, user.getStatus());
    callCleaner.clearExpiredPreviewCallsForUser(user);
    if (user.getStatus().isAvailableForDialerCalls() && !callUpToDateKeeper.isUserHasNotCompletedCall(user)) {
      acdEngine.addToIdleUsers(user);
    } else {
      acdEngine.removeFromIdleUsers(user);
    }
    acdEngine.addToActiveUsers(user);

    for (CampaignContext ctx : dialerFacade.getCampaignContexts().values()) {
      Campaign campaign = ctx.getCampaign();
      if (acdEngine.isUserHasMatchingSkillsAndGroup(user, campaign)) {
        campaign.setAvailAgentsSize(
                acdEngine.countCampaignAvailableUsersNow(campaign)
        );
        updateIncallAgents(campaign);
      }
    }


    if (!Constants.SYSTEM_STATUS.PREVIEW.getId().equals(user.getStatus().getId())) {
      boolean isNotRingingStatus = !Constants.SYSTEM_STATUS.RINGING.getId().equals(user.getStatus().getId());
      for (Call call : callUpToDateKeeper.getUserCalls(user)) {
        boolean wasCancelled = call.cancelPreviewFuture();
        if (wasCancelled && isNotRingingStatus) {
          LOG.info("Preview future was cancelled for " + call);
          callEventPublisher.publishCallEndEvent(call.getId(), new java.util.Date());
          interactionNotifierPublisher.publishDialerCallReport(call);

        }
      }
    }
  }

}
