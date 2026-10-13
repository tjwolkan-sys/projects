package com.powerdialer.dao.impl;

import java.util.List;

import org.hibernate.Criteria;
import org.hibernate.criterion.CriteriaSpecification;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Restrictions;
import org.springframework.stereotype.Repository;

import com.powerdialer.dao.IResultCodeDao;
import com.powerdialer.model.ResultCode;

@Repository
public class ResultCodeDao extends GenericDao<ResultCode> implements IResultCodeDao {

  public ResultCodeDao() {
    super(ResultCode.class);
  }

  @Override
  public List<ResultCode> getResultCodesByCampaignId(Long id, boolean nonFinalOnly) {
    Criteria criteria = getSessionFactory().getCurrentSession().createCriteria(ResultCode.class);
    criteria.createAlias("groups", "group");
    criteria.createAlias("group.campaignGroups", "campaignGroup");
    criteria.add(Restrictions.eq("campaignGroup.deleted", false));
    criteria.add(Restrictions.eq("campaignGroup.campaign.id", id));
    criteria.add(Restrictions.eq("enabled", true));
    criteria.add(Restrictions.eq("deleted", false));
    if (nonFinalOnly)
      criteria.add(Restrictions.eq("finalStatus", false));
    criteria.addOrder(Order.asc("name"));
    return criteria.setResultTransformer(CriteriaSpecification.DISTINCT_ROOT_ENTITY).list();
  }

  @Override
  public List<ResultCode> getResultCodesByGroupIds(Long[] groupIds, boolean nonFinalOnly) {
    Criteria criteria = getSessionFactory().getCurrentSession().createCriteria(ResultCode.class);
    criteria.createAlias("groups", "group");
    criteria.add(Restrictions.eq("group.deleted", false));
    criteria.add(Restrictions.in("group.id", groupIds));
    criteria.add(Restrictions.eq("enabled", true));
    criteria.add(Restrictions.eq("deleted", false));
    if (nonFinalOnly)
      criteria.add(Restrictions.eq("finalStatus", false));
    criteria.addOrder(Order.asc("name"));
    return criteria.setResultTransformer(CriteriaSpecification.DISTINCT_ROOT_ENTITY).list();
  }

}
