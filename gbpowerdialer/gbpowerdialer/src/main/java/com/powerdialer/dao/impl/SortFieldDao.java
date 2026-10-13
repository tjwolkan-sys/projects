package com.powerdialer.dao.impl;

import org.springframework.stereotype.Repository;

import com.powerdialer.dao.ISortFieldDao;
import com.powerdialer.model.SortField;

@Repository
public class SortFieldDao extends GenericDao<SortField> implements ISortFieldDao {

  public SortFieldDao() {
    super(SortField.class);
  }

}
