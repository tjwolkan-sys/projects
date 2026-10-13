package com.powerdialer.dao.impl;

import org.springframework.stereotype.Repository;

import com.powerdialer.dao.ISortFieldValueDao;
import com.powerdialer.model.SortFieldValue;

@Repository
public class SortFieldValueDao extends GenericDao<SortFieldValue> implements ISortFieldValueDao {

  public SortFieldValueDao() {
    super(SortFieldValue.class);
  }

}
