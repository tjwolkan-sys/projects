package com.powerdialer.dao.impl;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.Criteria;
import org.hibernate.criterion.Restrictions;

import org.springframework.stereotype.Repository;

import com.powerdialer.dao.IScheduleAutomationDao;
import com.powerdialer.model.ScheduleAutomation;

@Repository("scheduleAutomationDao")
public class ScheduleAutomationDao extends GenericDao<ScheduleAutomation> implements IScheduleAutomationDao {

  public ScheduleAutomationDao() {
    super(ScheduleAutomation.class);
  }

  @Override
  public List<ScheduleAutomation> loadScheduleAutomationByCampaignId(Long campaignId) {
    if (campaignId == null) {
      return new ArrayList<>();
    }
    Criteria criteria = getSessionFactory().getCurrentSession().createCriteria(ScheduleAutomation.class);
    criteria.createAlias("campaign", "campaign");
    criteria.add(Restrictions.eq("campaign.id", campaignId));
    return criteria.list();
  }
}
