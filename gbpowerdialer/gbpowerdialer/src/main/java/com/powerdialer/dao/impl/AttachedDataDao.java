package com.powerdialer.dao.impl;

import org.springframework.stereotype.Repository;

import com.powerdialer.dao.IAttachedDataDao;
import com.powerdialer.model.AttachedData;

@Repository
public class AttachedDataDao extends GenericDao<AttachedData> implements IAttachedDataDao {

  public AttachedDataDao() {
    super(AttachedData.class);
  }

}
