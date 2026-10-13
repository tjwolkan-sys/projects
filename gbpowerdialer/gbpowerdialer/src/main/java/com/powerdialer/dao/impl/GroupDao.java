package com.powerdialer.dao.impl;

import org.springframework.stereotype.Repository;

import com.powerdialer.dao.IGroupDao;
import com.powerdialer.model.Group;

@Repository
public class GroupDao extends GenericDao<Group> implements IGroupDao {

  public GroupDao() {
    super(Group.class);
  }

}
