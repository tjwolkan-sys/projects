package com.powerdialer.service;


import com.powerdialer.exceptions.InvalidApiTokenException;
import org.hibernate.Hibernate;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.powerdialer.model.Campaign;

@Service
public class TokenCampaignValidatorService {

  @Value("${requireTokenForRestAPI:false}")
  private boolean requireTokenForRestAPI;

  private final CampaignService campaignService;

  @Autowired
  public TokenCampaignValidatorService(CampaignService campaignService) {
    this.campaignService = campaignService;
  }

  @Transactional(readOnly = true)
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
    Hibernate.initialize(campaign.getTenant());
    return campaign;
  }

  @Transactional(readOnly = true)
  public void validateCampaignToken(String token, String campaignIdOrName) {
    getVerifiedCampaign(token, campaignIdOrName);
  }
}