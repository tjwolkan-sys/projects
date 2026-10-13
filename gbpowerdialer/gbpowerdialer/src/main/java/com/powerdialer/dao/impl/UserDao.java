package com.powerdialer.dao.impl;

import org.hibernate.Criteria;
import org.hibernate.criterion.Criterion;
import org.hibernate.criterion.Restrictions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.powerdialer.dao.ITenantDao;
import com.powerdialer.dao.IUserDao;
import com.powerdialer.model.User;

@Repository
public class UserDao extends GenericDao<User> implements IUserDao {

  @Autowired
  private ITenantDao tenantDao;

  public UserDao() {
    super(User.class);
  }

  public ITenantDao getTenantDao() {
    return tenantDao;
  }

  public void setTenantDao(ITenantDao tenantDao) {
    this.tenantDao = tenantDao;
  }

  public User getUserById(Long userId) {
    return (User) getSessionFactory().getCurrentSession().get(User.class, userId);
  }

  @Override
  public User getUserByUsername(String username) {
    Criteria criteria = getSessionFactory().getCurrentSession().createCriteria(User.class);
    criteria.add(Restrictions.eq("username", username));
    criteria.add(Restrictions.eq("enabled", true));
    criteria.add(Restrictions.eq("deleted", false));
    Object result = criteria.uniqueResult();
    return result != null ? (User) result : null;
  }
  
  @Override
  public User getUserByUsernameOrAdNickname(String name) {
    Criteria criteria = getSessionFactory().getCurrentSession().createCriteria(User.class);
    Criterion criterion1 = Restrictions.and(Restrictions.eq("enabled", true), Restrictions.eq("deleted", false));
    Criterion criterion2 = Restrictions.or(Restrictions.eq("username", name), Restrictions.eq("adNickname", name));
    criteria.add(Restrictions.and(criterion1, criterion2));
    Object result = criteria.uniqueResult();
    return result != null ? (User) result : null;
  }

  @Override
  public User getDncSystemUser() {
    Criteria criteria = getSessionFactory().getCurrentSession().createCriteria(User.class);
    criteria.add(Restrictions.eq("firstname", "SYSDNC"));
    criteria.add(Restrictions.eq("enabled", true));
    criteria.add(Restrictions.eq("deleted", false));
    return (User) criteria.uniqueResult();
  }
}
