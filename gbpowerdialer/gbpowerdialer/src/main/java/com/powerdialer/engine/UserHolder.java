package com.powerdialer.engine;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.stereotype.Component;

import com.powerdialer.model.User;

@Component
public class UserHolder {

  private static final Log log = LogFactory.getLog(UserHolder.class);
  private Map<Long, User> idToOnlineUser = new ConcurrentHashMap<>();

  public Set<Long> getOnlineUserIds() {
    return idToOnlineUser.keySet();
  }

  public User getUser(Long id) {
    return idToOnlineUser.get(id);
  }

  public void addUser(User user) {
    if (user != null) {
      idToOnlineUser.put(user.getId(), user);
      log.info("Added online user " + user);
    }
  }

  public User removeUser(Long id) {
    User user = idToOnlineUser.remove(id);
    if (user != null)
      log.info("Removed online user " + user);
    return user;
  }

}
