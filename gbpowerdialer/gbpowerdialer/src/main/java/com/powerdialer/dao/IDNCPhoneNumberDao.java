package com.powerdialer.dao;

import java.util.Date;
import java.util.Collection;
import java.util.List;

import com.powerdialer.model.DNCPhoneNumber;

public interface IDNCPhoneNumberDao extends IGenericDao<DNCPhoneNumber> {

  void disableByDncAndPhoneNumber(Long dncId, List<String> phoneNumbers);

  List<String> getPhoneNumbersByList(Long listId);

  Date findMaxExpireDateByDncId(Long dncId);

  List<DNCPhoneNumber> findByDncListId(Long dncId);

  boolean existsByPhoneNumberAndDncId(String phoneNumber, Long dncId);

  List<String> findActivePhoneNumbersByDncIdsAndPhoneNumbers(Collection<Long> dncIds, Collection<String> phoneNumbers, Date now);
}
