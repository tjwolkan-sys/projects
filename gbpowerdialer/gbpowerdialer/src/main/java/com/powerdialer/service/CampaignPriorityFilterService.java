package com.powerdialer.service;

import com.powerdialer.dao.ICampaignPriorityDao;
import com.powerdialer.dao.ICampaignPriorityFilterDao;
import com.powerdialer.dto.CampaignPriorityFilterFormDTO;
import com.powerdialer.dto.CampaignPriorityFilterListDTO;
import com.powerdialer.model.CampaignPriority;
import com.powerdialer.model.CampaignPriorityFilter;
import com.powerdialer.validators.CampaignPriorityFilterValidator;
import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CampaignPriorityFilterService extends GenericService<CampaignPriorityFilter, CampaignPriorityFilterFormDTO, CampaignPriorityFilterListDTO> {

  private final ICampaignPriorityFilterDao campaignPriorityFilterDao;

  @Autowired
  private SessionFactory sessionFactory;

  @Autowired
  public CampaignPriorityFilterService(ICampaignPriorityFilterDao dao) {
    super(dao, CampaignPriorityFilter.class, CampaignPriorityFilterFormDTO.class, CampaignPriorityFilterListDTO.class, new CampaignPriorityFilterValidator());
    this.campaignPriorityFilterDao = dao;
  }

  public List<CampaignPriorityFilter> getByCampaignPriorityId(Long campaignPriorityId) {
    return sessionFactory.getCurrentSession()
            .createQuery("FROM CampaignPriorityFilter e WHERE e.campaignPriority.id = :campaignPriorityId", CampaignPriorityFilter.class)
            .setParameter("campaignPriorityId", campaignPriorityId)
            .getResultList();
  }

}
