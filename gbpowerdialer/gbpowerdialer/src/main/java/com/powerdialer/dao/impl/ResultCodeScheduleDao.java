package com.powerdialer.dao.impl;

import java.util.List;

import org.hibernate.Criteria;
import org.hibernate.criterion.Restrictions;

import org.springframework.stereotype.Repository;

import com.powerdialer.dao.IResultCodeScheduleDao;
import com.powerdialer.model.ResultCodeSchedule;

@Repository
public class ResultCodeScheduleDao extends GenericDao<ResultCodeSchedule> implements IResultCodeScheduleDao {

  public ResultCodeScheduleDao() {
    super(ResultCodeSchedule.class);
  }

  @Override
  public List<ResultCodeSchedule> getByCampaignId(Long campaignId) {
    Criteria criteria = getSessionFactory().getCurrentSession().createCriteria(ResultCodeSchedule.class);
    criteria.createAlias("campaign", "c");
    criteria.add(Restrictions.eq("c.id", campaignId));
    criteria.add(Restrictions.eq("deleted", false));
    criteria.add(Restrictions.eq("enabled", true));
    criteria.setResultTransformer(Criteria.DISTINCT_ROOT_ENTITY);
    return criteria.list();
  }
}
