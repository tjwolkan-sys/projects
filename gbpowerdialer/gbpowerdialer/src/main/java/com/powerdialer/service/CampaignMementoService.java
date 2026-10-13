package com.powerdialer.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.powerdialer.dao.impl.CampaignMementoDao;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.CampaignMemento;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class CampaignMementoService {

  @Autowired
  private CampaignMementoDao campaignMementoDao;

  public CampaignMemento getCampaignMementoByCampaignId(Long campaignId) {
    return campaignMementoDao.getCampaignMementoByCampaignId(campaignId);
  }

  public List<CampaignMemento> getCampaignMementosByCampaignIds(List<Long> campaignIds) {
    return campaignMementoDao.getCampaignMementosByCampaignIds(campaignIds);
  }

  @Transactional(readOnly = false)
  public void saveCampaignMementoByCampaign(CampaignMemento campaignMemento, Campaign campaign) {
    convertCampaignToMemento(campaign, campaignMemento);
    campaignMementoDao.saveOrUpdate(campaignMemento);
  }

  /**
   * Convert campaign to campaignMemento
   * @param campaign
   * @return converted campaignMemento
   */
  private CampaignMemento convertCampaignToMemento(Campaign campaign, CampaignMemento campaignMemento) {
    campaignMemento.setActualListSize(campaign.getActualListSize());
    campaignMemento.setAgentsSize(campaign.getAgentsSize().get());
    campaignMemento.setAvailAgentsSize(campaign.getAvailAgentsSize().get());
    campaignMemento.setCallsMade(campaign.getCallsMade().get());
    campaignMemento.setCampaign(campaign);
    campaignMemento.setCompletionPercent(campaign.getCompletionPercent());
    campaignMemento.setDbEfficiency(campaign.getDbEfficiency());
    campaignMemento.setAnswerRate(campaign.getAnswerRate());
    campaignMemento.setListRemaining(campaign.getListRemaining().get());
    campaignMemento.setContactsToHandleAmount(campaign.getContactsToHandleAmount());
    campaignMemento.setLiveCalls(campaign.getLiveCalls().get());
    campaignMemento.setUniqueLiveCalls(campaign.getUniqueLiveCalls().get());
    campaignMemento.setNoConnectionCalls(campaign.getNoConnectionCalls().get());
    campaignMemento.setRetriesMade(campaign.getRetriesMade().get());
    campaignMemento.setAbandonedCallsSize(campaign.getAbandonedCallsSize().get());
    campaignMemento.setAbandonedCallsRatio(campaign.getAbandonedCallsRatio());
    campaignMemento.setRetryListSize(campaign.getRetryListSize());
    campaignMemento.setCallbackListSize(campaign.getCallbackListSize());
    campaignMemento.setRunoutEstimation(campaign.getRunoutEstimation());
    campaignMemento.setTotalListSize(campaign.getTotalListSize());
    campaignMemento.setTotalCallDuration(campaign.getTotalCallDuration().get());
    campaignMemento.setCallsByUserResultCode(campaign.getCallsByUserResultCode());
    campaignMemento.setCallsByTelephonyResultCode(campaign.getCallsByTelephonyResultCode());
    campaignMemento.setTouchedContacts(campaign.getTouchedContacts());
    campaignMemento.setCompletedContacts(campaign.getCompletedContacts());
    return campaignMemento;
  }

  @Transactional(readOnly = false)
  public void resetCampaignMemento(Long campaignId) {
    campaignMementoDao.resetCampaignMementoByCampaignId(campaignId);
  }

}
