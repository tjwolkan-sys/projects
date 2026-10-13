package com.powerdialer.dao;

import com.powerdialer.model.CampaignJobLog;
import com.powerdialer.model.CampaignJobResultLog;
import java.util.Collection;
import java.util.Map;

public interface ICampaignJobResultLogDao extends IGenericDao<CampaignJobResultLog> {

     int createLogsBulk(CampaignJobLog campaignJobLog, Collection<Long> campaignIds) ;
}
