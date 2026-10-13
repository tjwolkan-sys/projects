package com.powerdialer.dao.impl;

import com.powerdialer.dao.ICampaignPriorityDao;
import com.powerdialer.dao.ILSCAuthDao;
import com.powerdialer.model.CampaignPriority;
import com.powerdialer.model.LSCAuthorization;
import org.springframework.stereotype.Repository;

@Repository
public class CampaignPriorityDao extends GenericDao<CampaignPriority> implements ICampaignPriorityDao {

  public CampaignPriorityDao() {
    super(CampaignPriority.class);
  }

}
