package com.powerdialer.dao.impl;

import java.util.List;

import org.hibernate.Criteria;
import org.hibernate.criterion.Restrictions;

import org.springframework.stereotype.Repository;

import com.powerdialer.dao.ILocationDao;
import com.powerdialer.model.Location;

@Repository("locationDao")
public class LocationDao extends GenericDao<Location> implements ILocationDao {

  public LocationDao() {
    super(Location.class);
  }

  public List<Location> getLocationsByCampaignId(Long campaignId) {
    Criteria criteria = getSessionFactory().getCurrentSession().createCriteria(Location.class);
    criteria.createAlias("campaigns", "c");
    criteria.add(Restrictions.eq("c.id", campaignId));
    criteria.add(Restrictions.eq("deleted", false));
    criteria.setResultTransformer(Criteria.DISTINCT_ROOT_ENTITY);
    return criteria.list();
  }

  public List<Location> getLocationsWithEnabledCampaigns() {
    Criteria criteria = getSessionFactory().getCurrentSession().createCriteria(Location.class);
    criteria.createAlias("campaigns", "c");
    criteria.add(Restrictions.eq("c.enabled", true));
    criteria.add(Restrictions.eq("c.deleted", false));
    criteria.add(Restrictions.eq("deleted", false));
    return criteria.list();
  }
}