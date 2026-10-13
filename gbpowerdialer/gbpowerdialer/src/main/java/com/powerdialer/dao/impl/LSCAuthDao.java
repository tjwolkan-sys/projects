package com.powerdialer.dao.impl;

import com.powerdialer.dao.ILSCAuthDao;
import com.powerdialer.model.LSCAuthorization;
import org.springframework.stereotype.Repository;

@Repository
public class LSCAuthDao extends GenericDao<LSCAuthorization> implements ILSCAuthDao {

  public LSCAuthDao() {
    super(LSCAuthorization.class);
  }

}
