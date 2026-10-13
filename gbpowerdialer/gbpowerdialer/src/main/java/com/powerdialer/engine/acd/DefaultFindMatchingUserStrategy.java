package com.powerdialer.engine.acd;

import com.google.common.collect.ComparisonChain;
import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.CampaignGroup;
import com.powerdialer.model.Group;
import com.powerdialer.model.Skill;
import com.powerdialer.model.User;
import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * Default strategy for finding user based on campaign's group and skill
 * settings.
 *
 * <p>Group iteration is priority-aware: campaign groups are sorted by
 * priority DESC (then by inbound queue count ASC as tie-breaker), and groups
 * sharing the same priority are merged into a single pool. Within a pool the
 * standard sorting mechanism (LCD for skill-less, skill weight + LCD for
 * skill-enabled) chooses the agent. If a higher-priority pool yields no
 * eligible agent the search falls through to the next priority pool. This
 * preserves operator-intended priority semantics while giving fair
 * (longest-wait-first) distribution among equally prioritised groups.
 */
public class DefaultFindMatchingUserStrategy implements FindMatchingUserStrategy {

  private static final Logger LOG = LoggerFactory.getLogger(DefaultFindMatchingUserStrategy.class);

  private final Comparator<CampaignGroup> campaignGroupComparator = (o1, o2) -> ComparisonChain.start()
      .compare(o2.getPriority(), o1.getPriority())
      .compare(o1.getGroup().getQueues().size(), o2.getGroup().getQueues().size())
      .result();
  private final ACDEngine acdEngine;
  private final Campaign campaign;
  private final Call call;
  private final boolean useOnlyIdleUsers;

  public DefaultFindMatchingUserStrategy(ACDEngine acdEngine, Call call, boolean useOnlyIdleUsers) {
    this.acdEngine = acdEngine;
    this.campaign = call.getCampaign();
    this.call = call;
    this.useOnlyIdleUsers = useOnlyIdleUsers;
  }

  /**
   * Find matching user to handle call.
   *
   * <p>Iterates campaign groups in priority DESC order, merges groups sharing
   * the same priority into a pool, and delegates pool resolution to
   * {@link #getMostIdleUser(List)} or {@link #getMostIdleUserWithMatchingSkills(List, Set)}.
   * The first non-null pool result is returned; otherwise falls through to
   * the next priority pool.
   */
  @Override
  public User findMatchingUser() {
    List<CampaignGroup> campaignGroups = campaign.getEnabledCampaignGroups();
    if (CollectionUtils.isEmpty(campaignGroups)) {
      return null;
    }
    campaignGroups.sort(campaignGroupComparator);

    int i = 0;
    while (i < campaignGroups.size()) {
      int currentPriority = campaignGroups.get(i).getPriority();
      List<CampaignGroup> bucket = new ArrayList<>();
      int j = i;
      while (j < campaignGroups.size() && campaignGroups.get(j).getPriority() == currentPriority) {
        bucket.add(campaignGroups.get(j));
        j++;
      }

      User user = campaign.getSkillEnabled()
          ? getMostIdleUserWithMatchingSkills(bucket, call.getSkills())
          : getMostIdleUser(bucket);

      if (user != null) {
        return user;
      }
      i = j;
    }
    LOG.debug("{} No matched user found", call.getId());
    return null;
  }

  /**
   * Resolve a single priority-bucket of campaign groups via cross-group pooling.
   * Each group contributes its own per-group {@code getUsersQueue} result (so the
   * existing minCallsNumber load-balancing filter is preserved per-group). The
   * pooled users are deduplicated and sorted by {@link UserAvailTimeComparator}:
   * agents with no recorded statusSetDate come first, then agents who entered
   * their current status earliest (i.e. longest avail wait).
   *
   * @param campaignGroups groups that share the same priority
   * @return first eligible user from the pool, or {@code null}
   */
  public User getMostIdleUser(List<CampaignGroup> campaignGroups) {
    PooledCandidates pooled = collectPool(campaignGroups);
    if (pooled.isEmpty()) {
      return null;
    }
    pooled.users.sort(new UserAvailTimeComparator());
    for (User user : pooled.users) {
      Queue<User> sourceQueue = pooled.userToQueue.get(user);
      if (sourceQueue != null && removeFromIdleUsersAndCheck(sourceQueue, user)) {
        LOG.debug("{} Matched user: {}", call.getId(), user.getUsername());
        return user;
      }
    }
    return null;
  }

  /**
   * Skill-aware variant of {@link #getMostIdleUser(List)}: pools users across
   * same-priority groups, filters by required skills, and orders by skill weight
   * DESC then LCD via {@link ACDEngine#findUsersWithMatchingSkills(java.util.Collection, Set)}.
   * The underlying TreeSet comparator (UserSkillWeightComparator + UserAvailTimeComparator)
   * is unchanged; only the pooled input set is widened across the priority bucket.
   *
   * @param campaignGroups groups that share the same priority
   * @param skills required skill set for the call
   * @return first eligible user from the skill-matched pool, or {@code null}
   */
  public User getMostIdleUserWithMatchingSkills(List<CampaignGroup> campaignGroups, Set<Skill> skills) {
    PooledCandidates pooled = collectPool(campaignGroups);
    if (pooled.isEmpty()) {
      return null;
    }
    Set<User> matchedUsers = acdEngine.findUsersWithMatchingSkills(pooled.users, skills);
    LOG.debug("{} Users with matching skills: {}", call.getId(), matchedUsers);
    for (User user : matchedUsers) {
      Queue<User> sourceQueue = pooled.userToQueue.get(user);
      if (sourceQueue != null && removeFromIdleUsersAndCheck(sourceQueue, user)) {
        LOG.debug("{} Matched user: {}", call.getId(), user.getUsername());
        return user;
      }
    }
    return null;
  }

  /**
   * Get queue of users for finding matching user. Either idle user or users
   * with not bridged calls for campaigns with {@link Campaign#dialingBoost}
   * option greater than 1.
   */
  private Queue<User> getUsersQueue(Group group) {
    return useOnlyIdleUsers ? acdEngine.getIdleUsersInGroup(group) : acdEngine.getAvailableUsersQueueInGroup(group);
  }

  /**
   * Pool eligible users from the given groups, tracking each user's source
   * queue so {@link #removeFromIdleUsersAndCheck(Queue, User)} can return the
   * user to the correct queue when the threshold is not met.
   */
  private PooledCandidates collectPool(List<CampaignGroup> campaignGroups) {
    PooledCandidates pooled = new PooledCandidates();
    for (CampaignGroup campaignGroup : campaignGroups) {
      Queue<User> usersInGroup = getUsersQueue(campaignGroup.getGroup());
      LOG.debug("{} Users in group '{}': {}", call.getId(), campaignGroup.getGroup().getName(), usersInGroup);
      if (CollectionUtils.isEmpty(usersInGroup)) {
        continue;
      }
      for (User user : usersInGroup) {
        if (user != null && !pooled.userToQueue.containsKey(user)) {
          pooled.users.add(user);
          pooled.userToQueue.put(user, usersInGroup);
        }
      }
    }
    return pooled;
  }

  /**
   * Remove user form idle and check where he can handle call. More formally if
   * campaign has dialing boost > 1 then user always can handle call otherwise
   * user can handle call if he is idle.
   */
  private boolean removeFromIdleUsersAndCheck(Queue<User> usersInGroup, User user) {
    boolean isUserPassedThreshold = userPassedThreshold(campaign, user);
    boolean userIsIdle = false;
    if (isUserPassedThreshold) {
      // SORUN 1 / 1A: GAMMA'da originate (ring) fazinda secilen agent idle'dan CIKARILMAZ.
      // On atama semboliktir; gercek secim musteri actiginda (answer fazinda) yapilir. Eski
      // rezervasyon hem idle'i bosaltip starvation uretiyor (originate katmani "avail yok"
      // diye Xn'in istedigi cagriyi cikarmiyordu) hem de ring boyunca gercekten bos agentlari
      // answer seciminden gizliyordu (kapmaca, kanit dl-bb9bcdc2). GAMMA useOnlyIdleUsers=false
      // calistigindan asagidaki donus degeri userIsIdle'a zaten bagli degildir; atlanan sey
      // yalnizca idle kuyruguna yan etkidir. Answer fazinda cikarma AYNEN surer: dial edilecek
      // agent o an havuzdan alinir (es zamanli answer'lar tryLock ile de korunur).
      // NOT: bu dala per-originate log BILEREK konmadi (sn'de onlarca satir basardi); 1A'nin canli
      // kaniti "Idle user removed" satirinin originate aninda ARTIK gorunmemesidir (answer'da gorunur).
      //
      // FIX-GLB90060295: ayni starvation PROGRESSIVE_RELEASED'de de olculdu (10.08.2026, 5 agent'lik
      // kampanyada idle havuz zamanin %75,6'sinda BOS, zaman-agirlikli ortalama 0,30 agent; ayni anda
      // supervisor ekrani 5 agent'i Available gosteriyordu). Kapsam OriginatePhase icinde tutulur;
      // PROGRESSIVE_RESERVED, PREVIEW_* ve PREDICTIVE-ALPHA/BETA disaridadir. PROGRESSIVE_RELEASED'de
      // useOnlyIdleUsers zaten UserFindingContextBuilder tarafindan false'a cekilir, boylece asagidaki
      // donus degeri GAMMA'daki gibi userIsIdle'a bagli kalmaz.
      if (!OriginatePhase.isWithoutReservation(call)) {
        userIsIdle = acdEngine.removeFromIdleUsers(user);
      }
    } else {
      boolean removed = usersInGroup.remove(user);
      if (removed) {
        usersInGroup.add(user);
      }
    }
    return (userIsIdle || !useOnlyIdleUsers) && isUserPassedThreshold;
  }

  private boolean userPassedThreshold(Campaign campaign, User user) {
    return user.calculateTimeInStatus() >= campaign.getAvailableThreshold();
  }

  /** Container for users pooled across a priority bucket and their origin queues. */
  private static final class PooledCandidates {
    final List<User> users = new ArrayList<>();
    final Map<User, Queue<User>> userToQueue = new HashMap<>();

    boolean isEmpty() {
      return users.isEmpty();
    }
  }

}
