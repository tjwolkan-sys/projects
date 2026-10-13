package com.powerdialer.dao;

import com.powerdialer.model.CDR;

public interface ICampaignResultDao {
    void updateUserResultCode(CDR cdr, long newResultCodeId);
}
