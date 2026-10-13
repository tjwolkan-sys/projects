package com.powerdialer.dao.impl;

import org.hibernate.Criteria;
import org.springframework.stereotype.Repository;

import com.powerdialer.dao.ITenantDao;
import com.powerdialer.model.Tenant;

import java.util.List;
import java.util.Map;

@Repository("tenantDao")
public class TenantDao extends GenericDao<Tenant> implements ITenantDao {

  public TenantDao() {
    super(Tenant.class);
  }
  
  @Override
  public Tenant getTenantById(long tenantId) {
    return (Tenant) getSessionFactory().getCurrentSession().get(Tenant.class, tenantId);
  }

  public List<Map<String, Object>> getTenantsByCampaignIds(List<Long> campaignIds) {
    //implementation to fetch tenants by campaign IDs
    String hql = "SELECT t.id, t.name FROM Tenant t";
    return getSessionFactory().getCurrentSession()
        .createQuery(hql)
        .setParameterList("campaignIds", campaignIds)
        .setResultTransformer(Criteria.ALIAS_TO_ENTITY_MAP)
        .list();
  }
}