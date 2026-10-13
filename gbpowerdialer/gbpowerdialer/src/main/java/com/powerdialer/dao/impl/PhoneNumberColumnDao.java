package com.powerdialer.dao.impl;

import org.springframework.stereotype.Repository;

import com.powerdialer.dao.IPhoneNumberColumnDao;
import com.powerdialer.model.PhoneNumberColumn;

@Repository
public class PhoneNumberColumnDao extends GenericDao<PhoneNumberColumn> implements IPhoneNumberColumnDao {

  public PhoneNumberColumnDao() {
    super(PhoneNumberColumn.class);
  }

}
