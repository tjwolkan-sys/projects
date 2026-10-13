package com.powerdialer.service;

import com.powerdialer.dao.IScheduleBlackoutAutomationDao;
import com.powerdialer.dto.ScheduleBlackoutAutomationFormDTO;
import com.powerdialer.dto.ScheduleBlackoutAutomationListDTO;
import com.powerdialer.model.ScheduleBlackoutAutomation;
import com.powerdialer.validators.ScheduleBlackoutAutomationValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ScheduleBlackoutService extends GenericService<ScheduleBlackoutAutomation, ScheduleBlackoutAutomationFormDTO, ScheduleBlackoutAutomationListDTO> {

  @Autowired
  public ScheduleBlackoutService(IScheduleBlackoutAutomationDao dao) {
    super(dao, ScheduleBlackoutAutomation.class, ScheduleBlackoutAutomationFormDTO.class, ScheduleBlackoutAutomationListDTO.class, new ScheduleBlackoutAutomationValidator());
  }

}
