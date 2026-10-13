package com.powerdialer.service;

import com.powerdialer.dao.ICampaignResultDao;
import com.powerdialer.model.CDR;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class CampaignResultService {
    private final ICampaignResultDao campaignResultDao;

    @Autowired
    public CampaignResultService(ICampaignResultDao campaignResultDao) {
        this.campaignResultDao = campaignResultDao;
    }

    /**
     * Updates the user_result_code for a specific cdr.
     *
     * @param cdr The call record containing campaign and contact info.
     * @param newResultCodeId The new result code ID (e.g., -2 for “forcefully stop”)
     */
    public void updateUserResultCode(CDR cdr, long newResultCodeId) {
        campaignResultDao.updateUserResultCode(cdr, newResultCodeId);
    }
}
