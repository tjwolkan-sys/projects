package com.powerdialer.util;

import com.powerdialer.model.Campaign;

public class CampaignUtil {

    /**
     * Checks if the campaign is valid for processing.
     *
     * @param campaign the campaign to be checked
     * @return {@code true} if the campaign is valid; {@code false} otherwise
     */
    public static boolean isValidPredictiveCampaign(Campaign campaign) {
        return campaign != null && !campaign.isDeleted() && campaign.getType() == Constants.CAMPAIGN_TYPE.PREDICTIVE;
    }

}
