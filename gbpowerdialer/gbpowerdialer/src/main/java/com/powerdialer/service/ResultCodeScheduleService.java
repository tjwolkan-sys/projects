package com.powerdialer.service;

import java.util.List;

import org.hibernate.Hibernate;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.powerdialer.dao.IResultCodeScheduleDao;
import com.powerdialer.dto.ResultCodeScheduleFormDTO;
import com.powerdialer.dto.ResultCodeScheduleListDTO;
import com.powerdialer.model.ResultCodeSchedule;
import com.powerdialer.validators.ResultCodeScheduleValidator;

@Service
public class ResultCodeScheduleService extends GenericService<ResultCodeSchedule, ResultCodeScheduleFormDTO, ResultCodeScheduleListDTO> {

  private IResultCodeScheduleDao resultCodeScheduleDao;

  @Autowired
  public ResultCodeScheduleService(IResultCodeScheduleDao dao) {
    super(dao, ResultCodeSchedule.class, ResultCodeScheduleFormDTO.class, ResultCodeScheduleListDTO.class, new ResultCodeScheduleValidator());
    this.resultCodeScheduleDao = dao;
  }

  @Transactional(readOnly = true)
  public List<ResultCodeSchedule> getByCampaignId(Long campaignId) {
    List<ResultCodeSchedule> resultCodeSchedules = resultCodeScheduleDao.getByCampaignId(campaignId);
    for (ResultCodeSchedule schedule : resultCodeSchedules) {
      Hibernate.initialize(schedule.getResultCode());
      if (schedule.isAdvancedIntervalEnabled()) {
        Hibernate.initialize(schedule.getResultCodeScheduleAdvanceds());
      }
    }
    return resultCodeSchedules;
  }

}
