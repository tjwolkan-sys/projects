package com.powerdialer.dao.impl;

import org.hibernate.Criteria;
import org.hibernate.Query;
import org.hibernate.criterion.Restrictions;

import org.springframework.stereotype.Repository;

import com.powerdialer.dao.ICampaignMementoDao;
import com.powerdialer.model.CampaignMemento;

import java.util.List;

@Repository
public class CampaignMementoDao extends GenericDao<CampaignMemento> implements ICampaignMementoDao {

  public CampaignMementoDao() {
    super(CampaignMemento.class);
  }

  @Override
  public CampaignMemento getCampaignMementoByCampaignId(Long campaignId) {
    Criteria criteria = getSessionFactory().getCurrentSession().createCriteria(CampaignMemento.class);
    criteria.add(Restrictions.eq("campaign.id", campaignId));
    return (CampaignMemento) criteria.uniqueResult();
  }

  public List<CampaignMemento> getCampaignMementosByCampaignIds(List<Long> campaignIds) {
    Criteria criteria = getSessionFactory().getCurrentSession().createCriteria(CampaignMemento.class);
    criteria.add(Restrictions.in("campaign.id", campaignIds));
    return criteria.list();
  }

  @Override
  public void resetCampaignMementoByCampaignId(Long campaignId) {
    String hqlQuery = "UPDATE CampaignMemento cm SET cm.agentsSize = 0, cm.availAgentsSize = 0, cm.totalListSize = 0, cm.actualListSize = 0" +
                      ", cm.retryListSize = 0, cm.listRemaining = 0, cm.contactsToHandleAmount = 0, cm.callsMade = 0, cm.liveCalls = 0, uniqueLiveCalls = 0" +
                      ", cm.noConnectionCalls = 0, cm.retriesMade = 0, cm.dbEfficiency = 0, cm.answerRate = 0" +
                      ", cm.runoutEstimation = 0, cm.completionPercent = 0, cm.abandonedCallsSize = 0, cm.abandonedCallsRatio = 0" +
                      ", cm.totalCallDuration = 0, cm.callsByUserResultCode = null, cm.callsByTelephonyResultCode = null" +
                      ", cm.touchedContacts = 0, cm.completedContacts = 0 WHERE cm.campaign.id = :campaignId";
    Query query = getSessionFactory().getCurrentSession().createQuery(hqlQuery);
    query.setLong("campaignId", campaignId).executeUpdate();
  }

}
