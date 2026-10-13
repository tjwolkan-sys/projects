package com.powerdialer.engine.synchronization;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.Sets;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.powerdialer.engine.UserHolder;
import com.powerdialer.model.User;
import com.powerdialer.redis.RedisUtil;
import com.powerdialer.redis.UserSubscriber;

@Component
@Lazy(false)
public class UserSynchronizer {

  private static final Log log = LogFactory.getLog(UserSynchronizer.class);
  private static final long REMOTE_USERS_SYNCHRONIZATION_INTERVAL = 20000;
  private static final int REDUNDANT_USER_CATCH_LIMIT_BEFORE_LOGOUT = 2;
  private final Map<Long, Integer> redundantUserCandidates = new HashMap<>();

  @Autowired
  private RedisUtil redisUtil;

  @Autowired
  private UserHolder userHolder;

  @Autowired
  private UserSubscriber userSubscriber;

  @Scheduled(fixedDelay = REMOTE_USERS_SYNCHRONIZATION_INTERVAL)
  void synchronizeUsers() {
    Set<Long> redisUserIds = redisUtil.getLiveUserIds();
    Set<Long> memoryUserIds = userHolder.getOnlineUserIds();
    addMissingUsers(Sets.difference(redisUserIds, memoryUserIds));
    processRedundantUsers(new HashSet<>(Sets.difference(memoryUserIds, redisUserIds)));
  }

  private void addMissingUsers(Set<Long> userIds) {
    for (Long id : userIds) {
      log.info("Loading missing user " + id + "...");
      User user = redisUtil.getUserFromRedis(id);
      if (user != null) {
        userSubscriber.handleLogin(user);
      }
    }
  }

  private void processRedundantUsers(Set<Long> userIds) {
    removeEscapedRedundantCandidates(userIds);
    checkPresentedRedundantCandidates(userIds);
    addNewRedundantCandidates(userIds);
  }

  private void checkPresentedRedundantCandidates(Set<Long> userIds) {
    Set<Long> userIdsToLogout = new HashSet<>();
    redundantUserCandidates.entrySet().forEach(e -> {
      if (e.getValue() < REDUNDANT_USER_CATCH_LIMIT_BEFORE_LOGOUT) {
        e.setValue(e.getValue() + 1);
        log.info("Incremented redundant user catch count. User id : " + e.getKey() + ", catch: " + e.getValue());
      } else {
        userIdsToLogout.add(e.getKey());
      }
    });
    userIdsToLogout.forEach(id -> {
      log.warn("Logging out user with id " + id + " due to redundant user catch limit");
      userSubscriber.handleLogout(id);
      redundantUserCandidates.remove(id);
      userIds.remove(id);
    });
  }

  private void addNewRedundantCandidates(Set<Long> userIds) {
    CollectionUtils.subtract(userIds, redundantUserCandidates.keySet()).forEach(id -> {
      log.info("Redundant user has caught in first time: " + id);
      redundantUserCandidates.put(id, 1);
    });
  }

  private void removeEscapedRedundantCandidates(Set<Long> userIds) {
    CollectionUtils.subtract(redundantUserCandidates.keySet(), userIds).forEach(id -> {
      log.info("Removing redundant user candidate: " + id);
      redundantUserCandidates.remove(id);
    });
  }


}
