package com.powerdialer.dao.impl;

import org.springframework.stereotype.Repository;

import com.powerdialer.dao.ITelephonyResultCodeDao;
import com.powerdialer.model.TelephonyResultCode;

@Repository
public class TelephonyResultCodeDao extends GenericDao<TelephonyResultCode> implements ITelephonyResultCodeDao {

  public TelephonyResultCodeDao() {
    super(TelephonyResultCode.class);
  }

}
