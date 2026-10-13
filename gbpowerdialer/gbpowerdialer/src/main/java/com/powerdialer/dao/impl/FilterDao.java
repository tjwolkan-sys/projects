package com.powerdialer.dao.impl;

import org.springframework.stereotype.Repository;

import com.powerdialer.dao.IFilterDao;
import com.powerdialer.model.Filter;

@Repository
public class FilterDao extends GenericDao<Filter> implements IFilterDao {

  public FilterDao() {
    super(Filter.class);
  }

}
