package com.powerdialer.dao.impl;

import java.util.List;

import com.powerdialer.dao.ILSCDao;
import com.powerdialer.model.LSC;
import org.hibernate.Criteria;
import org.hibernate.Hibernate;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Restrictions;
import org.springframework.stereotype.Repository;

@Repository("lscDao")
public class LSCDao extends GenericDao<LSC> implements ILSCDao {

  public LSCDao() {
    super(LSC.class);
  }

  @SuppressWarnings("unchecked")
  @Override
  public List<LSC> getLscsForCampaign(Long campaignId, boolean enabled) {
    return getSessionFactory().getCurrentSession()
            .createSQLQuery(
                    "SELECT l.* FROM pd_lsc_service l " +
                            "JOIN pd_campaign_lsc_service cl ON l.id = cl.lsc_service_id " +
                            "WHERE cl.campaign_id = :campaignId " +
                            "AND l.deleted = 0 " +
                            "AND l.enabled = :enabled " +
                            "ORDER BY CASE WHEN cl.priority IS NULL THEN 1 ELSE 0 END, cl.priority ASC"
            )
            .addEntity(LSC.class)
            .setParameter("campaignId", campaignId)
            .setParameter("enabled", enabled ? 1 : 0)
            .list();
  }

  @SuppressWarnings("unchecked")
  @Override
  public List<LSC> getGlobalLscs(boolean enabled) {
    Criteria criteria = getSessionFactory().getCurrentSession().createCriteria(LSC.class);
    criteria.add(Restrictions.eq("global", true));
    criteria.add(Restrictions.eq("deleted", false));
    criteria.add(Restrictions.eq("enabled", enabled));
    return criteria.list();
  }

  @Override
  public LSC getWithInitializedCampaigns(long id) {
    LSC lsc = get(id);
    if (lsc != null) Hibernate.initialize(lsc.getCampaigns());
    return lsc;
  }

  @SuppressWarnings("unchecked")
  @Override
  public List<LSC> getLSCsByCampaignId(Long campaignId) {
    return getSessionFactory().getCurrentSession()
            .createSQLQuery(
                    "SELECT l.* FROM pd_lsc_service l " +
                            "JOIN pd_campaign_lsc_service cl ON l.id = cl.lsc_service_id " +
                            "WHERE cl.campaign_id = :campaignId " +
                            "AND l.deleted = 0 " +
                            "ORDER BY CASE WHEN cl.priority IS NULL THEN 1 ELSE 0 END, cl.priority ASC"
            )
            .addEntity(LSC.class)
            .setParameter("campaignId", campaignId)
            .list();
  }

}
