package com.powerdialer.dao.impl;

import org.springframework.stereotype.Repository;

import com.powerdialer.dao.IActionLogDao;
import com.powerdialer.model.ActionLog;

@Repository
public class ActionLogDao extends GenericDao<ActionLog> implements IActionLogDao {

  public ActionLogDao() {
    super(ActionLog.class);
  }

}
