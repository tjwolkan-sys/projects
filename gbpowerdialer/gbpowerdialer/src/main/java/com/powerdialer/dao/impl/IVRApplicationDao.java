package com.powerdialer.dao.impl;

import org.springframework.stereotype.Repository;

import com.powerdialer.dao.IIVRApplicationDao;
import com.powerdialer.model.IVRApplication;

@Repository
public class IVRApplicationDao extends GenericDao<IVRApplication> implements IIVRApplicationDao {

  public IVRApplicationDao() {
    super(IVRApplication.class);
  }

}
