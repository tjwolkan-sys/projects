package com.powerdialer.service;

import com.powerdialer.dao.ICampaignPriorityDao;
import com.powerdialer.dto.*;
import com.powerdialer.exceptions.DuplicatedNameException;
import com.powerdialer.model.CampaignPriority;
import com.powerdialer.model.CampaignPriorityFilter;
import com.powerdialer.model.User;
import com.powerdialer.validators.CampaignPriorityValidator;
import org.hibernate.SessionFactory;
import org.hibernate.StaleObjectStateException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class CampaignPriorityService extends GenericService<CampaignPriority, CampaignPriorityFormDTO, CampaignPriorityListDTO> {

  @Autowired
  private SessionFactory sessionFactory;

  private final ICampaignPriorityDao campaignPriorityDao;

  @Autowired
  public CampaignPriorityService(ICampaignPriorityDao dao) {
    super(dao, CampaignPriority.class, CampaignPriorityFormDTO.class, CampaignPriorityListDTO.class, new CampaignPriorityValidator());
    this.campaignPriorityDao = dao;
  }

  @Override
  @Transactional(readOnly = false)
  public CampaignPriorityFormDTO save(final CampaignPriorityFormDTO item) throws DuplicatedNameException {
    User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    CampaignPriority campaignPriority = mapper.map(item, CampaignPriority.class);

    if (getValidator().validate(campaignPriority).getResult()) {

      for (CampaignPriorityFilter campaignPriorityFilter : campaignPriority.getCampaignPriorityFilters()) {
        campaignPriorityFilter.setCampaignPriority(campaignPriority);
        campaignPriorityFilter.setCreateDate(new Date());
        campaignPriorityFilter.setCreateUser(user);
      }

      Date now = new Date();
      campaignPriority.setCreateDate(now);
      campaignPriority.setCreateUser(user);
      campaignPriority = getDao().save(campaignPriority);

    } else {
      return item;
    }

    return mapper.map(campaignPriority, CampaignPriorityFormDTO.class);
  }

  @Override
  @Transactional(readOnly = false)
  public CampaignPriorityFormDTO merge(final CampaignPriorityFormDTO item) throws DuplicatedNameException, StaleObjectStateException {
    User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    CampaignPriority campaignPriority = mapper.map(item, CampaignPriority.class);

    if (getValidator().validate(campaignPriority).getResult()) {

      for (CampaignPriorityFilter campaignPriorityFilter : campaignPriority.getCampaignPriorityFilters()) {
        campaignPriorityFilter.setCampaignPriority(campaignPriority);
        campaignPriorityFilter.setUpdateDate(new Date());
        campaignPriorityFilter.setUpdateUser(user);
      }

      campaignPriority.setUpdateDate(new Date());
      campaignPriority.setUpdateUser(user);

      campaignPriority = getDao().merge(campaignPriority);
    } else {
      return item;
    }

    return mapper.map(campaignPriority, CampaignPriorityFormDTO.class);
  }

  public List<Long> getAllByCampaignName(String campaignName) {
    return sessionFactory.getCurrentSession()
            .createQuery("SELECT e.id FROM CampaignPriority e WHERE e.campaignName = :campaignName")
            .setParameter("campaignName", campaignName)
            .getResultList();
  }

  public boolean existsByCampaignName(String campaignName) {
    Long count = sessionFactory.getCurrentSession()
            .createQuery("SELECT COUNT(e.id) FROM CampaignPriority e WHERE e.deleted != true and e.campaignName = :campaignName", Long.class)
            .setParameter("campaignName", campaignName)
            .getSingleResult();

    return count > 0;
  }


  @Transactional(readOnly = true)
  public Set<String> getAllPriorityCampaignNames() {
    return new HashSet<>(sessionFactory.getCurrentSession()
            .createQuery("SELECT e.campaignName FROM CampaignPriority e WHERE e.deleted != true", String.class)
            .getResultList());
  }

}
