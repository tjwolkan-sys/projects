package com.powerdialer.dao.impl;

import java.util.*;
import java.util.stream.Collectors;

import com.powerdialer.model.CampaignGroup;
import org.hibernate.Criteria;
import org.hibernate.Session;
import org.hibernate.criterion.Criterion;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;
import org.hibernate.criterion.SimpleExpression;
import org.hibernate.internal.CriteriaImpl;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Repository;

import com.powerdialer.dao.ICampaignDao;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.User;
import com.powerdialer.security.PermissionUtil;
import com.powerdialer.util.Constants;
import com.powerdialer.util.ListDetailed;

@SuppressWarnings("unchecked")
@Repository
public class CampaignDao extends GenericDao<Campaign> implements ICampaignDao {

  @Override
  public Campaign save(Campaign o) {
    Campaign result = super.save(o);
    getSessionFactory().getCurrentSession().flush();
    return result;
  }

  @Override
  public Campaign merge(Campaign o) {
    Campaign result = super.merge(o);
    getSessionFactory().getCurrentSession().flush();
    return result;
  }

  @Autowired
  @Lazy
  private PermissionUtil permissionUtil;

  public CampaignDao() {
    super(Campaign.class);
  }

  @Override
  public void delete(final Long id) {
    super.delete(id);
    getSessionFactory().getCurrentSession().createQuery("update DNC set deleted = :deleted, enabled = :enabled where campaign.id = :id")
        .setParameter("deleted", true)
        .setParameter("enabled", false)
        .setParameter("id", id).executeUpdate();
  }

  @Override
  public void delete(final List<Long> ids) {
    super.delete(ids);
    getSessionFactory().getCurrentSession().createQuery("update DNC set deleted = :deleted, enabled = :enabled where campaign.id in (:ids)")
        .setParameter("deleted", true)
        .setParameter("enabled", false)
        .setParameterList("ids", ids).executeUpdate();
  }

  @Override
  public void updateStoppedDate(Long campaignId, Date stoppedDate) {
    getSessionFactory().getCurrentSession().createQuery("update Campaign set stoppedDate = :stoppedDate where id = :campaignId").setLong(
        "campaignId", campaignId).setTimestamp("stoppedDate", stoppedDate).executeUpdate();
  }

  @Override
  public List<Campaign> getByName(String name, boolean deleted) {
    return getSessionFactory().getCurrentSession().createCriteria(Campaign.class).add(Restrictions.eq("name", name)).add(Restrictions.eq("deleted",
                                                                                                                                         deleted)).list();
  }

  @Override
  public List<Long> getCampaignIdsCreatedByUser(User user, boolean deleted) {
    Criteria criteria = getSessionFactory().getCurrentSession().createCriteria(Campaign.class);
    criteria.add(Restrictions.eq("createUser", user));
    criteria.add(Restrictions.eq("deleted", deleted));
    criteria.setProjection(Projections.property("id"));
    return criteria.list();
  }

  @Override
  public List<Long> getCampaignIdsCreatedByTenants(Collection<Long> tenantsIds, boolean deleted) {
    Criteria criteria = getSessionFactory().getCurrentSession().createCriteria(Campaign.class);
    criteria.add(Restrictions.in("tenant.id", tenantsIds));
    criteria.add(Restrictions.eq("deleted", deleted));
    criteria.setProjection(Projections.property("id"));
    return criteria.list();
  }

  @Override
  public List<Campaign> getAll(Boolean enabled, Boolean deleted, Long[] ids) {
    final Session session = getSessionFactory().getCurrentSession();
    final Criteria crit = session.createCriteria(Campaign.class);
    if (enabled != null)
      crit.add(Restrictions.eq("enabled", enabled));
    if (deleted != null)
      crit.add(Restrictions.eq("deleted", deleted));
    if (ids != null && ids.length > 0)
      crit.add(Restrictions.in("id", ids));

    permissionUtil.addCampaignPermissionFilter(crit);

    return crit.list();
  }

  @Override
  public List<Campaign> getAll(Boolean deleted) {
    final Session session = getSessionFactory().getCurrentSession();
    final Criteria crit = session.createCriteria(Campaign.class);
    if (deleted != null)
      crit.add(Restrictions.eq("deleted", deleted));
    return crit.list();
  }

  @Override
  public void updateStopType(Long campaignId, Constants.CAMPAIGN_STOP_TYPE stopType) {
    if (stopType == null) {
      getSessionFactory().getCurrentSession()
          .createQuery("update Campaign set stopType = :stopType where id = :campaignId")
          .setLong("campaignId", campaignId)
          .setParameter("stopType", stopType)
          .executeUpdate();
    } else {
      getSessionFactory().getCurrentSession()
          .createQuery("update Campaign set stopType = :stopType, stopTypeHistory = :stopTypeHistory where id = :campaignId")
          .setLong("campaignId", campaignId)
          .setParameter("stopType", stopType)
          .setParameter("stopTypeHistory", stopType)
          .executeUpdate();
    }
  }

  @Override
  public ListDetailed<Campaign> getAll(JSONObject page, JSONObject sort, JSONObject filters) {
    ListDetailed<Campaign> listDetailed = new ListDetailed<>();

    Criteria criteria = getSessionFactory().getCurrentSession().createCriteria(Campaign.class);

    applyGridFilters(criteria, filters);

    applyGridPagination(criteria, page, listDetailed);

    applyGridSort(criteria, sort);

    removeTenantRestrictions(criteria);

    permissionUtil.addCampaignPermissionFilter(criteria);

    listDetailed.setData(criteria.setProjection(null).setResultTransformer(Criteria.DISTINCT_ROOT_ENTITY).list());

    return listDetailed;
  }

  /**
   * No need for default restrictions of "tenant.id" field because of permissions. Campaign restrictions should be added by
   * {@link PermissionUtil#addCampaignPermissionFilter(Criteria)}
   * @see com.powerdialer.security.AuthUtil#getAdditionalAuthorities(User)
   * @param criteria
   */
  private void removeTenantRestrictions(Criteria criteria) {
    Iterator<CriteriaImpl.CriterionEntry> criterionEntryIterator = ((CriteriaImpl) criteria).iterateExpressionEntries();
    while (criterionEntryIterator.hasNext()) {
      Criterion criterion = criterionEntryIterator.next().getCriterion();
      if (criterion instanceof SimpleExpression && ((SimpleExpression) criterion).getPropertyName().equals("tenant.id")) {
        criterionEntryIterator.remove();
        break;
      }
    }
  }

  public List<Map<String, Object>> getAllUsedTenants() {
    String hql = "SELECT DISTINCT c.tenant.id AS tenantId, c.tenant.name AS tenantName FROM Campaign c WHERE c.tenant IS NOT NULL";
    return getSessionFactory().getCurrentSession()
            .createQuery(hql)
            .setResultTransformer(Criteria.ALIAS_TO_ENTITY_MAP)
            .list();
  }


  public List<Map<String, Object>> getTenantsOfUser(User user) {
    return user.getTenants().stream()
            .map(t -> {
              Map<String, Object> map = new HashMap<>();
              map.put("tenantId", t.getId());
              map.put("tenantName", t.getName());
              return map;
            })
            .collect(Collectors.toList());
  }

  @Override
  public void updateLscPriority(Long campaignId, Long lscId, int priority) {
    getSessionFactory().getCurrentSession()
            .createSQLQuery(
                    "UPDATE pd_campaign_lsc_service " +
                            "SET priority = :priority " +
                            "WHERE campaign_id = :campaignId " +
                            "AND lsc_service_id = :lscId"
            )
            .setParameter("priority", priority)
            .setParameter("campaignId", campaignId)
            .setParameter("lscId", lscId)
            .executeUpdate();
  }


  @Override
  public void resetLscPriorities(Long campaignId) {
    getSessionFactory().getCurrentSession()
            .createSQLQuery(
                    "UPDATE pd_campaign_lsc_service " +
                            "SET priority = NULL " +
                            "WHERE campaign_id = :campaignId"
            )
            .setParameter("campaignId", campaignId)
            .executeUpdate();
  }

//  @Override
//  public void lockCampaignForUpdate(Long campaignId) {
//    Campaign campaign = (Campaign) getSessionFactory()
//            .getCurrentSession()
//            .createQuery("from Campaign c where c.id = :campaignId")
//            .setParameter("campaignId", campaignId)
//            .setLockMode("c", org.hibernate.LockMode.PESSIMISTIC_WRITE)
//            .uniqueResult();
//
//    if (campaign == null) {
//      throw new IllegalArgumentException("Campaign not found. campaignId=" + campaignId);
//    }
//  }


}
