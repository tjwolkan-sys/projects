package com.powerdialer.service;

import com.powerdialer.exceptions.InvalidApiTokenException;
import com.powerdialer.model.Campaign;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TokenCampaignValidatorServiceExtended {

    @Value("${requireTokenForRestAPI:false}")
    private boolean requireTokenForRestAPI;

    private final CampaignService campaignService;

    public Campaign getVerifiedCampaign(String token, String campaignIdOrName) {
        Campaign campaign = campaignService.getCampaignByNameOrId(campaignIdOrName);

        if (requireTokenForRestAPI) {
            if (Boolean.FALSE.equals(campaign.getAllowApiControl())) {
                throw new InvalidApiTokenException("API Control is not allowed for this campaign");
            }
            if (token == null || token.trim().isEmpty()) {
                throw new InvalidApiTokenException("Campaign token must not be blank");
            }
            if (campaign.getApiToken() == null || !campaign.getApiToken().equals(token)) {
                throw new InvalidApiTokenException("Campaign token is invalid");
            }
        }

        return campaign;
    }
}