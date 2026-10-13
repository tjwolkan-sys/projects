package com.powerdialer.dao.impl;

import java.util.Date;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

import com.powerdialer.model.User;
import org.hibernate.Criteria;
import org.hibernate.Query;
import org.hibernate.ScrollableResults;
import org.hibernate.Session;
import org.hibernate.criterion.Restrictions;

import org.json.JSONObject;
import org.springframework.stereotype.Repository;

import com.powerdialer.dao.IDNCPhoneNumberDao;
import com.powerdialer.model.DNCPhoneNumber;
import com.powerdialer.util.Constants;

@Repository("dncPNDao")
public class DNCPhoneNumberDao extends GenericDao<DNCPhoneNumber> implements IDNCPhoneNumberDao {

  public DNCPhoneNumberDao() {
    super(DNCPhoneNumber.class);
  }

  @Override
  public void disableByDncAndPhoneNumber(Long dncId, List<String> phoneNumbers) {
    Session currentSession = getSessionFactory().getCurrentSession();
    Query query = currentSession.createQuery("UPDATE DNCPhoneNumber set enabled = :enabled WHERE dncList.id = :dncId AND phoneNumber in :phoneNumbers");
    query.setParameter("enabled", false);
    query.setParameter("dncId", dncId);
    query.setParameterList("phoneNumbers", phoneNumbers);
    query.executeUpdate();
  }

  public void enableDncPnBatch(long dncId, JSONObject filters, User user) {
    batchOperation(dncId, filters, user, (dncPhoneNumber) -> dncPhoneNumber.setEnabled(true));
  }

  public void disableDncPnBatch(long dncId, JSONObject filters, User user) {
    batchOperation(dncId, filters, user, (dncPhoneNumber) -> dncPhoneNumber.setEnabled(false));
  }

  public void deleteDncPnBatch(long dncId, JSONObject filters, User user) {
    batchOperation(dncId, filters, user, (dncPhoneNumber) -> dncPhoneNumber.setDeleted(true));
  }

  private void batchOperation(long dncId, JSONObject filters, User user, Consumer<DNCPhoneNumber> operation) {
    Criteria criteria = getSessionFactory().getCurrentSession().createCriteria(DNCPhoneNumber.class);
    criteria.add(Restrictions.eq("dncList.id", dncId));
    applyGridFilters(criteria, filters);
    ScrollableResults scrollableResults = criteria.scroll();
    while (scrollableResults.next()) {
      DNCPhoneNumber dncPhoneNumber = (DNCPhoneNumber) scrollableResults.get(0);
      dncPhoneNumber.setUpdateUser(user);
      dncPhoneNumber.setUpdateDate(new Date());
      operation.accept(dncPhoneNumber);
      save(dncPhoneNumber);
    }
    getSessionFactory().getCurrentSession().flush();
  }

  public void dncPnFileBatchDelete(long dncId, List<String> phoneNumbers, User user) {
    Session currentSession = getSessionFactory().getCurrentSession();
    Query query = currentSession.createQuery("update DNCPhoneNumber set deleted = :deleted, updateUser = :updateUser, " +
            "updateDate = :updateDate WHERE dncList.id = :dncId AND phoneNumber in :phoneNumbers AND deleted = false");
    query.setParameter("deleted", true);
    query.setParameter("updateUser", user);
    query.setParameter("updateDate", new Date());
    query.setParameter("dncId", dncId);
    query.setParameterList("phoneNumbers", phoneNumbers);
    query.executeUpdate();
  }

  @Override
  public List<String> getPhoneNumbersByList(Long listId) {
    Session currentSession = getSessionFactory().getCurrentSession();
    return currentSession.createQuery("select n.phoneNumber from DNCPhoneNumber n WHERE n.dncList.id = :listId and n.enabled = true and n.deleted = false")
        .setParameter("listId", listId)
        .list();
  }

  @Override
  public Date findMaxExpireDateByDncId(Long dncId) {
    Session currentSession = getSessionFactory().getCurrentSession();
    return (Date) currentSession.createQuery("select max(p.expiredDate) from DNCPhoneNumber p where p.dncList.id = :dncId and p.expiredDate is not null and p.deleted = false")
            .setParameter("dncId", dncId).uniqueResult();
  }

  @Override
  public List<DNCPhoneNumber> findByDncListId(Long dncId) {
    Session currentSession = getSessionFactory().getCurrentSession();
    return currentSession.createQuery("from DNCPhoneNumber pn where pn.dncList.id = :id and pn.deleted = false", DNCPhoneNumber.class)
            .setParameter("id", dncId).list();
  }

  @Override
  public boolean existsByPhoneNumberAndDncId(String phoneNumber, Long dncId) {
    Session currentSession = getSessionFactory().getCurrentSession();
    Long count = (Long) currentSession.createQuery("select count(p) from DNCPhoneNumber p where p.dncList.id = :dncId and p.phoneNumber = :phoneNumber and p.deleted = false")
            .setParameter("dncId", dncId).setParameter("phoneNumber", phoneNumber).uniqueResult();
    return count != null && count > 0;
  }

  @Override
  public List<String> findActivePhoneNumbersByDncIdsAndPhoneNumbers(Collection<Long> dncIds, Collection<String> phoneNumbers, Date now) {
    Session currentSession = getSessionFactory().getCurrentSession();
    return currentSession.createQuery(
            "select distinct p.phoneNumber from DNCPhoneNumber p join p.dncList d " +
                    "where d.id in (:dncIds) " +
                    "and p.phoneNumber in (:phoneNumbers) " +
                    "and p.phoneNumber is not null " +
                    "and p.enabled = true " +
                    "and p.deleted = false " +
                    "and d.enabled = true " +
                    "and d.deleted = false " +
                    "and (p.expiredDate >= :now or (p.expiredDate is null and (d.expireDate is null or d.expireDate >= :now)))")
            .setParameterList("dncIds", dncIds)
            .setParameterList("phoneNumbers", phoneNumbers)
            .setParameter("now", now)
            .list();
  }
}
