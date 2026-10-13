package com.powerdialer.service;

import org.hibernate.Hibernate;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.powerdialer.dao.IUserDao;
import com.powerdialer.dto.UserFormDTO;
import com.powerdialer.dto.UserListDTO;
import com.powerdialer.model.User;
import com.powerdialer.validators.UserValidator;

@Service
public class UserService extends GenericService<User, UserFormDTO, UserListDTO> {

  @Autowired
  public UserService(IUserDao dao) {
    super(dao, User.class, UserFormDTO.class, UserListDTO.class, new UserValidator());
  }

  public User getUserById(Long userId) {
    return ((IUserDao) getDao()).getUserById(userId);
  }

  @Transactional(readOnly = true)
  public User getUserByIdWithGroup(Long userId) {
    User user = ((IUserDao) getDao()).getUserById(userId);
    if (user != null) {
      Hibernate.initialize(user.getGroup());
    }
    return user;
  }

  public User getUserByUsernameWithGroupAndSkills(String username) {
    User user = ((IUserDao) getDao()).getUserByUsername(username);
    if (user != null) {
      Hibernate.initialize(user.getGroup());
      Hibernate.initialize(user.getSkillWeightPair());
    }
    return user;
  }

  public User getUserByUsername(String username) {
    User user = ((IUserDao) getDao()).getUserByUsername(username);
    if (user != null) {
      Hibernate.initialize(user.getTenant());
      Hibernate.initialize(user.getTenants());
    }
    return user;
  }
  
  public User getUserByUsernameOrAdNickname(String name) {
    User user = ((IUserDao) getDao()).getUserByUsernameOrAdNickname(name);
    if (user != null) {
      Hibernate.initialize(user.getTenant());
      Hibernate.initialize(user.getTenants());
    }
    return user;
  }

}
