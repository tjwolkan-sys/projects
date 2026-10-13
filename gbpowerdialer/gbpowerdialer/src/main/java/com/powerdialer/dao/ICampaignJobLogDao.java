package com.powerdialer.dao;

import com.powerdialer.model.CampaignJobLog;
import com.powerdialer.model.User;
import com.powerdialer.util.Constants;
import java.util.Date;


public interface ICampaignJobLogDao extends IGenericDao<CampaignJobLog> {
    CampaignJobLog getCampaignLogByCampaignId(Long campaignId);
    void updateLog(CampaignJobLog log, Constants.CAMPAIGN_JOB_RESULT_STATUS status, String errorMessage);
    CampaignJobLog createLog(Long jobConfigId, Long campaignId, String campaignName, Date startDate, Constants.CAMPAIGN_JOB_RESULT_STATUS status, String errorMessage, User user);
}
