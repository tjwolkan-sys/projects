package com.powerdialer.dao.impl;

import org.springframework.stereotype.Repository;

import com.powerdialer.dao.ICampaignGroupDao;
import com.powerdialer.model.CampaignGroup;

@Repository
public class CampaignGroupDao extends GenericDao<CampaignGroup> implements ICampaignGroupDao {

  public CampaignGroupDao() {
    super(CampaignGroup.class);
  }

}
