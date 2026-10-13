package com.powerdialer.dao.impl;

import java.util.Collections;
import java.util.List;

import org.hibernate.Criteria;
import org.hibernate.Hibernate;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;
import org.hibernate.sql.JoinType;

import org.json.JSONObject;
import org.springframework.stereotype.Repository;

import com.powerdialer.dao.IDNCDao;
import com.powerdialer.model.DNC;
import com.powerdialer.util.ListDetailed;

@Repository("dncDao")
public class DNCDao extends GenericDao<DNC> implements IDNCDao {

  public DNCDao() {
    super(DNC.class);
  }

  @SuppressWarnings("unchecked")
  @Override
  public List<DNC> getDNCForCampaign(Long campaignId, boolean enabled) {
    Criteria criteria = getSessionFactory().getCurrentSession().createCriteria(DNC.class);
    criteria.createAlias("campaigns", "c", JoinType.LEFT_OUTER_JOIN);
    criteria.add(Restrictions.or(Restrictions.eq("campaign.id", campaignId), Restrictions.eq("c.id", campaignId)));
    criteria.add(Restrictions.eq("enabled", enabled));
    criteria.add(Restrictions.eq("deleted", false));
    return criteria.list();
  }

  @SuppressWarnings("unchecked")
  @Override
  public List<DNC> getGlobalDNC(boolean enabled) {
    Criteria criteria = getSessionFactory().getCurrentSession().createCriteria(DNC.class);
    criteria.add(Restrictions.eq("enabled", enabled));
    criteria.add(Restrictions.eq("deleted", false));
    criteria.add(Restrictions.eq("global", true));
    return criteria.list();
  }

  @SuppressWarnings("unchecked")
  @Override
  public ListDetailed<DNC> getDNCListsByCampaignsIds(JSONObject page, JSONObject sort, JSONObject filters, Long[] campaignIds, boolean all) {
    ListDetailed<DNC> listDetailed = new ListDetailed<>();
    if (campaignIds.length == 0 && !all) {
      return ListDetailed.empty();
    }
    Criteria criteria = getSessionFactory().getCurrentSession().createCriteria(DNC.class);
    if (!all)
      criteria.add(Restrictions.in("campaign.id", campaignIds));
    applyGridFilters(criteria, filters);
    applyGridPagination(criteria, page, listDetailed);
    applyGridSort(criteria, sort);
    List list = criteria.setProjection(null).setResultTransformer(Criteria.DISTINCT_ROOT_ENTITY).list();
    listDetailed.setData(list != null ? list : Collections.emptyList());
    return listDetailed;
  }

  @SuppressWarnings("unchecked")
  @Override
  public List<DNC> getDNCListsByIds(Long[] dncIds) {
    Criteria criteria =  getSessionFactory().getCurrentSession().createCriteria(DNC.class);
    if (dncIds.length > 0)
      criteria.add(Restrictions.in("id", dncIds));
    return criteria.setProjection(null).setResultTransformer(Criteria.DISTINCT_ROOT_ENTITY).list();
  }

  @Override
  public DNC getDNCByDNCPhoneNumberId(Long dncPhoneNumberId) {
    Criteria criteria = getSessionFactory().getCurrentSession().createCriteria(DNC.class);
    criteria.createAlias("phoneNumbers", "pn");
    criteria.add(Restrictions.eq("pn.id", dncPhoneNumberId));
    return (DNC) criteria.uniqueResult();
  }

  @Override
  public DNC getWithInitializedPhoneNumbers(Long id) {
    DNC dnc = get(id);
    Hibernate.initialize(dnc.getPhoneNumbers());
    Hibernate.initialize(dnc.getTenant());
    return dnc;
  }

  @Override
  public DNC getDefaultDNCByCampaignId(Long campaignId) {
    Criteria criteria = getSessionFactory().getCurrentSession().createCriteria(DNC.class);
    criteria.add(Restrictions.eq("campaign.id", campaignId));
    criteria.add(Restrictions.eq("deleted", false));
    return ((DNC) criteria.uniqueResult());
  }

  @Override
  public DNC getDefaultDNCByCampaignName(String campaignName) {
    Criteria criteria = getSessionFactory().getCurrentSession().createCriteria(DNC.class);
    criteria.add(Restrictions.eq("campaign.name", campaignName));
    criteria.add(Restrictions.eq("deleted", false));
    return ((DNC) criteria.uniqueResult());
  }

  @Override
  public long getTenantDNCCount(Long tenantId, boolean isCountGlobal, boolean includeDisabled, Long... excludedIds) {
    Criteria criteria = getSessionFactory().getCurrentSession().createCriteria(DNC.class);
    criteria.add(Restrictions.eq("tenant.id", tenantId));
    if (!includeDisabled) {
      criteria.add(Restrictions.eq("enabled", true));
    }
    criteria.add(Restrictions.eq("global", isCountGlobal));
    criteria.add(Restrictions.eq("deleted", false));
    if (excludedIds != null && excludedIds.length > 0) {
      criteria.add(Restrictions.not(Restrictions.in("id", excludedIds)));
    }
    criteria.setProjection(Projections.rowCount());
    return ((Number) criteria.uniqueResult()).longValue();
  }

  @Override
  public DNC getTenantDNC(Long tenantId, boolean isCountGlobal, boolean includeDisabled) {
    Criteria criteria = getSessionFactory().getCurrentSession().createCriteria(DNC.class);
    criteria.add(Restrictions.eq("tenant.id", tenantId));
    if (!includeDisabled) {
      criteria.add(Restrictions.eq("enabled", true));
    }
    criteria.add(Restrictions.eq("global", isCountGlobal));
    criteria.add(Restrictions.eq("deleted", false));
    return (DNC) criteria.uniqueResult();
  }

  @Override
  public List<DNC> getDNCsByCampaignId(Long campaignId) {
    Criteria criteria = getSessionFactory().getCurrentSession().createCriteria(DNC.class);
    criteria.createAlias("campaigns", "c", JoinType.LEFT_OUTER_JOIN);
    criteria.add(Restrictions.or(Restrictions.eq("campaign.id", campaignId), Restrictions.eq("c.id", campaignId)));
    criteria.add(Restrictions.eq("deleted", false));
    criteria.setResultTransformer(Criteria.DISTINCT_ROOT_ENTITY);
    return criteria.list();
  }

}
