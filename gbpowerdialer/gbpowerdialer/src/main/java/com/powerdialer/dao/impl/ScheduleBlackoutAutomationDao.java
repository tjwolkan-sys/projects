package com.powerdialer.dao.impl;

import com.powerdialer.dao.IScheduleBlackoutAutomationDao;
import com.powerdialer.model.ScheduleAutomation;
import com.powerdialer.model.ScheduleBlackoutAutomation;
import org.hibernate.Criteria;
import org.hibernate.criterion.Restrictions;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository("scheduleBlackoutAutomationDao")
public class ScheduleBlackoutAutomationDao extends GenericDao<ScheduleBlackoutAutomation> implements IScheduleBlackoutAutomationDao {

  public ScheduleBlackoutAutomationDao() {
    super(ScheduleBlackoutAutomation.class);
  }

  @Override
  public List<ScheduleBlackoutAutomation> loadScheduleBlackoutAutomationByCampaignId(Long campaignId) {
    if (campaignId == null) {
      return new ArrayList<>();
    }
    Criteria criteria = getSessionFactory().getCurrentSession().createCriteria(ScheduleBlackoutAutomation.class);
    criteria.createAlias("campaign", "campaign");
    criteria.add(Restrictions.eq("campaign.id", campaignId));
    return criteria.list();
  }
}
