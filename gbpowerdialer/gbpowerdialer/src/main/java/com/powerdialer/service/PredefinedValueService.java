package com.powerdialer.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.powerdialer.dao.IPredefinedValueDao;
import com.powerdialer.dto.PredefinedValueDTO;
import com.powerdialer.model.PredefinedValue;
import com.powerdialer.validators.CampaignPredefinedValueValidator;

@Service
public class PredefinedValueService extends GenericService<PredefinedValue, PredefinedValueDTO, PredefinedValueDTO> {

  IPredefinedValueDao predefinedValueDao;

  @Autowired
  public PredefinedValueService(IPredefinedValueDao dao) {
    super(dao, PredefinedValue.class, PredefinedValueDTO.class, PredefinedValueDTO.class, new CampaignPredefinedValueValidator());
    predefinedValueDao = dao;
  }

  @Transactional
  public List<PredefinedValueDTO> getPredefinedValuesByCampaign(String campaignId) {
    List<PredefinedValue> predefinedValues = predefinedValueDao.getPredefinedValuesByCampaignId(Long.parseLong(campaignId));
    return super.convertEntitiesToDTO(predefinedValues);
  }

}
