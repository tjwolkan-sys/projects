package com.powerdialer.engine;

import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.Group;
import com.powerdialer.model.Status;
import com.powerdialer.model.User;
import com.powerdialer.redis.RedisUtil;
import com.powerdialer.redis.message.UserPauseMessage;
import com.powerdialer.util.Constants;
import com.powerdialer.util.LoggerDecorator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

/**
 * Keep all {@link Call}s objects up to date with respect to redis messages
 */
@Component
public class CallUpToDateKeeper {

  private LoggerDecorator LOG;
  private final Queue<Call> calls = new ConcurrentLinkedQueue<>();

  @Autowired
  private LoggerDecorator loggerDecorator;

  @Autowired
  private RedisUtil redisUtil;

  @PostConstruct
  public void init() {
    this.LOG = loggerDecorator.getLogger(CallUpToDateKeeper.class);
  }

  /**
   * Register call in up to date service in order to update it's state with new
   * redis messages
   */
  public void registerCall(Call call) {
    if (call != null && call.getUser() != null) {
      calls.add(call);
    }
  }

  /**
   * Remove call from keeper
   * @param call
   */
  public void unregisterCall(Call call) {
    if (call != null) {
      calls.remove(call);
    }
  }

  /**
   * Get all user calls registered in service
   * @param user
   * @return
   */
  public List<Call> getUserCalls(User user) {
    if (user == null) {
      return new ArrayList<>();
    } else {
      return calls.stream().filter(call -> user.equals(call.getUser())).collect(Collectors.toList());
    }
  }

  public List<User> getAllRegisteredUsers() {
    Set<User> result = new HashSet<>();
    for (Call call : calls) {
      if (call != null && call.getUser() != null)
        result.add(call.getUser());
    }
    return new ArrayList<>(result);
  }

  /**
   * Get call with user which has given group
   * @param userGroup
   * @return
   */
  public Map<User, List<Call>> getCallsByUserGroup(Group userGroup) {
    Map<User, List<Call>> result = new HashMap<>();
    for (Call call : calls) {
      User user = call.getUser();
      if (user != null && isSameGroup(user.getGroup(), userGroup)) {
        if (!result.containsKey(user))
          result.put(user, new ArrayList<>());
        result.get(user).add(call);
      }
    }
    return result;
  }

  /**
   * Grup kiyasi identifier uzerinden yapilir; {@link Group#equals(Object)} CAGRILMAZ.
   *
   * <p>{@code User.group} lazy {@code @ManyToOne}'dir. DB'den yuklenmis (detached) bir agent'in grup
   * alani initialize edilmemis bir Hibernate proxy olabilir. Proxy uzerinde equals/hashCode cagrisi
   * proxy'yi initialize etmeye calisir; session kapali oldugu icin LazyInitializationException atar.
   * Bu keeper ACD hot path'inde (her originate tick'i ve her FastAGI dial script'i) tarandigi icin
   * TEK bozuk kayit tum agent secimini dusuruyordu (10.08.2026 uretim kaydi). Identifier getter'i
   * proxy'yi initialize etmez; id kiyasi hem detached hem initialized nesnede guvenlidir.</p>
   *
   * <p>Davranis {@code Group.equals} ile ayni tutulur: ayni referans esittir, id'si olmayan nesneler
   * esit degildir. Iki fark bilincli iyilestirmedir: grup null oldugunda eski kodun attigi
   * NullPointerException yerine false donulur (grubu olmayan agent {@code ACDEngine} tarafinda da
   * ayrica ele alinir) ve {@code generateEquals} varargs allocation'i hot path'ten kalkar.</p>
   */
  private static boolean isSameGroup(Group first, Group second) {
    if (first == null || second == null) {
      return false;
    }
    if (first == second) {
      return true;
    }
    Long firstId = first.getId();
    return firstId != null && firstId.equals(second.getId());
  }

  /**
   * Listener for {@link Constants.MSG_ACTION_TYPE#STATUS_UPDATE} event
   * @param user
   * @param status
   */
  public void onUserStatusUpdate(User user, Status status) {
    for (Call call : calls) {
      if (call.getUser() != null && call.getUser().equals(user)) {
        updateUserCall(call, user, status);
      }
    }
  }

  /**
   * Update calls on user logout
   */
  public void onUserLogout(User user) {
    if (user != null) {
      calls.removeIf(call -> user.equals(call.getUser()));
    }
  }

  /**
   * Update single user's call and manage user status in Asterisk
   */
  private void updateUserCall(Call call, User freshUser, Status status) {
    call.getUser().setStatus(status);
    // Ayni agent'in call'a bagli KOPYASINDA statusSetDate de tazelenmeli (User.equals id-bazli,
    // kopyalar ayri nesneler). Onceki satir kendine-atama oldugu icin no-op'tu; bayat statusSetDate
    // avail siralamasini (UserAvailTimeComparator), availableThreshold hesabini (calculateTimeInStatus)
    // ve shouldSwitchUser kiyasini eski tarihle bozuyordu. Login yolundan (handleLogin ->
    // handleStatusChange) statusSetDate'siz user gelebilir -> null ile ezme, mevcut degeri koru.
    if (freshUser.getStatusSetDate() != null) {
      call.getUser().setStatusSetDate(freshUser.getStatusSetDate());
    }
    manageUserStatusUpdateForAsterisk(call.getUser(), status, call.getCampaign());
  }

  /**
   * Pause user with non empty calls for
   * {@link Constants.CAMPAIGN_TYPE#PROGRESSIVE_RESERVED) campaign type
   * @param user
   * @param status
   * @param campaign
   */
  private void manageUserStatusUpdateForAsterisk(User user, Status status, Campaign campaign) {
    if (Constants.CAMPAIGN_TYPE.PROGRESSIVE_RESERVED.equals(campaign.getType()) && status.isAvailableForDialerCalls() && isUserHasNotCompletedCall(user)) {
      LOG.info(user + " has changed status to " + status + " but was reserved for PROGRESSIVE_RESERVED type call",
               Constants.LOG_MODULE.CAMPAIGN,
               campaign);
      redisUtil.publishUserPauseMessage(new UserPauseMessage(user.getId(), true));
    }
  }

  /**
   * Check if user has calls with no result code
   * @param user
   * @return true if user has not completed calls else return false
   */
  public boolean isUserHasNotCompletedCall(User user) {
    List<Call> userCalls = getUserCalls(user);
    if (!CollectionUtils.isEmpty(userCalls)) {
      return userCalls.stream().filter(Objects::nonNull).map(Call::getTelephonyResultCode).anyMatch(Objects::isNull);
    }
    return false;
  }

}
