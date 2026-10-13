package com.powerdialer.dao.impl;

import org.springframework.stereotype.Repository;

import com.powerdialer.dao.IAdminLogDetailsDao;
import com.powerdialer.model.AdminLogDetails;

@Repository
public class AdminLogDetailsDao extends GenericDao<AdminLogDetails> implements IAdminLogDetailsDao {

  public AdminLogDetailsDao() {
    super(AdminLogDetails.class);
  }

}
