package com.powerdialer.dao.impl;

import org.springframework.stereotype.Repository;

import com.powerdialer.dao.IAdminLogDao;
import com.powerdialer.model.AdminLog;

@Repository
public class AdminLogDao extends GenericDao<AdminLog> implements IAdminLogDao {

  public AdminLogDao() {
    super(AdminLog.class);
  }

}
