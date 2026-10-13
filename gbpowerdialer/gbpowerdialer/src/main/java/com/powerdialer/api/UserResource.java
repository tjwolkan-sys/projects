package com.powerdialer.api;

import java.util.Queue;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.powerdialer.engine.acd.ACDEngine;
import com.powerdialer.model.Group;
import com.powerdialer.model.User;

/**
 * Rest controller providing idle users information
 */
@RestController
@RequestMapping(value = "/api/users", produces = MediaType.APPLICATION_JSON_VALUE)
public class UserResource {

  @Autowired
  private ACDEngine acdEngine;

  /**
   * Get users by groupId
   * @param groupId
   * @return list of users
   */
  @RequestMapping(value = "/group/{groupId}", method = RequestMethod.GET)
  public Queue<User> getUsersByGroup(@PathVariable Long groupId) {
    return acdEngine.getIdleUsersInGroup(new Group(groupId));
  }

  /**
   * Get all users
   * @return list of users
   */
  @RequestMapping(method = RequestMethod.GET)
  public Queue<User> getUsers() {
    return acdEngine.getAllIdleUsers();
  }

}
