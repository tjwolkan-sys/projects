package com.powerdialer.dao;

import java.util.Collection;
import java.util.Date;
import java.util.List;

import com.powerdialer.model.DNCCustomerPhone;

public interface IDNCCustomerPhoneDao extends IGenericDao<DNCCustomerPhone> {

  boolean existsByCustomerNoAndPhoneNumberAndDncId(String customerNo, String phoneNumber, Long dncId);

  List<String> findActiveCustomerNosByDncIdsAndCustomerNos(Collection<Long> dncIds, Collection<String> customerNos, Date now);
}
