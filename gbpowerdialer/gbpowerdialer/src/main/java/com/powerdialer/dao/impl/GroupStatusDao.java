package com.powerdialer.dao.impl;

import java.util.List;

import org.hibernate.Criteria;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Restrictions;

import org.springframework.stereotype.Repository;

import com.powerdialer.dao.IGroupStatusDao;
import com.powerdialer.model.GroupPropertyStatus;
import com.powerdialer.model.GroupStatus;

@Repository
public class GroupStatusDao extends GenericDao<GroupStatus> implements IGroupStatusDao {

  public GroupStatusDao() {
    super(GroupStatus.class);
  }

  @Override
  public List<GroupPropertyStatus> getStatusesByGroupId(Long groupId, Boolean enabled, Boolean deleted) {
    Criteria criteria = getSessionFactory().getCurrentSession().createCriteria(GroupPropertyStatus.class);
    if (enabled != null) {
      criteria.add(Restrictions.eq("enabled", enabled));
    }
    if (deleted != null) {
      criteria.add(Restrictions.eq("deleted", deleted));
    }
    if (groupId != null) {
      criteria.add(Restrictions.eq("group.id", groupId));
    }
    criteria.addOrder(Order.asc("sort"));
    return criteria.list();
  }

}
