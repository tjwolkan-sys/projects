package com.powerdialer.dao.impl;

import org.springframework.stereotype.Repository;

import com.powerdialer.dao.IServerDao;
import com.powerdialer.model.Server;

@Repository("serverDao")
public class ServerDao extends GenericDao<Server> implements IServerDao {

  public ServerDao() {
    super(Server.class);
  }

}
