package com.powerdialer.dao.impl;

import com.powerdialer.dao.ICampaignPriorityDao;
import com.powerdialer.dao.ICampaignPriorityFilterDao;
import com.powerdialer.model.CampaignPriority;
import com.powerdialer.model.CampaignPriorityFilter;
import org.springframework.stereotype.Repository;

@Repository
public class CampaignPriorityFilterDao extends GenericDao<CampaignPriorityFilter> implements ICampaignPriorityFilterDao {

  public CampaignPriorityFilterDao() {
    super(CampaignPriorityFilter.class);
  }

}
