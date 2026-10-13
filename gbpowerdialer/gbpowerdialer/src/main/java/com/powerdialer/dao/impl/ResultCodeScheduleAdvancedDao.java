package com.powerdialer.dao.impl;

import org.springframework.stereotype.Repository;

import com.powerdialer.dao.IResultCodeScheduleAdvancedDao;
import com.powerdialer.model.ResultCodeScheduleAdvanced;

@Repository
public class ResultCodeScheduleAdvancedDao extends GenericDao<ResultCodeScheduleAdvanced> implements IResultCodeScheduleAdvancedDao {

  public ResultCodeScheduleAdvancedDao() {
    super(ResultCodeScheduleAdvanced.class);
  }

}
