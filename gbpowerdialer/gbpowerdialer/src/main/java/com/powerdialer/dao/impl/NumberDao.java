package com.powerdialer.dao.impl;

import org.springframework.stereotype.Repository;

import com.powerdialer.dao.INumberDao;
import com.powerdialer.model.Number;

@Repository
public class NumberDao extends GenericDao<Number> implements INumberDao {

  public NumberDao() {
    super(Number.class);
  }

}
