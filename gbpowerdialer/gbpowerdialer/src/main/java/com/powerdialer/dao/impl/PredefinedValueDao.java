package com.powerdialer.dao.impl;

import java.util.List;

import org.hibernate.Criteria;
import org.hibernate.criterion.Restrictions;

import org.springframework.stereotype.Repository;

import com.powerdialer.dao.IPredefinedValueDao;
import com.powerdialer.model.PredefinedValue;

@Repository
public class PredefinedValueDao extends GenericDao<PredefinedValue> implements IPredefinedValueDao {

  public PredefinedValueDao() {
    super(PredefinedValue.class);
  }

  @Override
  public List<PredefinedValue> getPredefinedValuesByCampaignId(Long campaignId) {
    Criteria criteria = getSessionFactory().getCurrentSession().createCriteria(PredefinedValue.class);
    criteria.add(Restrictions.eq("campaign.id", campaignId));
    criteria.add(Restrictions.eq("deleted", false));
    return criteria.setProjection(null).setResultTransformer(Criteria.DISTINCT_ROOT_ENTITY).list();
  }
}
