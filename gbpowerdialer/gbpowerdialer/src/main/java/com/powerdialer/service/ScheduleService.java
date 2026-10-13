package com.powerdialer.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.powerdialer.dao.IScheduleDao;
import com.powerdialer.dto.ScheduleFormDTO;
import com.powerdialer.dto.ScheduleListDTO;
import com.powerdialer.model.Schedule;
import com.powerdialer.validators.ScheduleValidator;

@Service
public class ScheduleService extends GenericService<Schedule, ScheduleFormDTO, ScheduleListDTO> {

  @Autowired
  public ScheduleService(IScheduleDao dao) {
    super(dao, Schedule.class, ScheduleFormDTO.class, ScheduleListDTO.class, new ScheduleValidator());
  }

}
