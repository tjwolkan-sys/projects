package com.powerdialer.dao;

import com.powerdialer.model.User;

public interface IUserDao extends IGenericDao<User> {

  User getUserByUsername(String username);
  
  User getUserByUsernameOrAdNickname(String name);

  User getUserById(Long userId);

  User getDncSystemUser();
}
