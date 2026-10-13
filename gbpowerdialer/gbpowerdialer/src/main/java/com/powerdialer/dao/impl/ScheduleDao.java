package com.powerdialer.dao.impl;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.Criteria;
import org.hibernate.criterion.Restrictions;
import org.springframework.stereotype.Repository;

import com.powerdialer.dao.IScheduleDao;
import com.powerdialer.model.Schedule;

@Repository("scheduleDao")
public class ScheduleDao extends GenericDao<Schedule> implements IScheduleDao {

  public ScheduleDao() {
    super(Schedule.class);
  }

  @Override
  public List<Schedule> loadScheduleByCampaign(Long campaignId) {
    if (campaignId == null) {
      return new ArrayList<>();
    }
    Criteria criteria = getSessionFactory().getCurrentSession().createCriteria(Schedule.class);
    criteria.createAlias("campaign", "campaign");
    criteria.add(Restrictions.eq("campaign.id", campaignId));
    return criteria.list();
  }

}
