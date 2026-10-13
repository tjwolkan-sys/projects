package com.powerdialer.dao.impl;

import java.util.Collection;
import java.util.Date;
import java.util.List;

import org.hibernate.Session;
import org.springframework.stereotype.Repository;

import com.powerdialer.dao.IDNCCustomerPhoneDao;
import com.powerdialer.model.DNCCustomerPhone;

@Repository("dncCustomerPhoneDao")
public class DNCCustomerPhoneDao extends GenericDao<DNCCustomerPhone> implements IDNCCustomerPhoneDao {

  public DNCCustomerPhoneDao() {
    super(DNCCustomerPhone.class);
  }

  @Override
  public boolean existsByCustomerNoAndPhoneNumberAndDncId(String customerNo, String phoneNumber, Long dncId) {
    Session currentSession = getSessionFactory().getCurrentSession();
    Long count = (Long) currentSession.createQuery(
            "select count(c) from DNCCustomerPhone c where c.dncList.id = :dncId "
                + "and c.customerNo = :customerNo and c.phoneNumber = :phoneNumber and c.deleted = false")
        .setParameter("dncId", dncId)
        .setParameter("customerNo", customerNo)
        .setParameter("phoneNumber", phoneNumber)
        .uniqueResult();
    return count != null && count > 0;
  }

  @Override
  public List<String> findActiveCustomerNosByDncIdsAndCustomerNos(Collection<Long> dncIds, Collection<String> customerNos, Date now) {
    Session currentSession = getSessionFactory().getCurrentSession();
    return currentSession.createQuery(
            "select distinct c.customerNo from DNCCustomerPhone c join c.dncList d "
                + "where d.id in (:dncIds) "
                + "and c.customerNo in (:customerNos) "
                + "and c.enabled = true "
                + "and c.deleted = false "
                + "and d.enabled = true "
                + "and d.deleted = false "
                + "and (d.expireDate is null or d.expireDate >= :now)")
        .setParameterList("dncIds", dncIds)
        .setParameterList("customerNos", customerNos)
        .setParameter("now", now)
        .list();
  }
}
