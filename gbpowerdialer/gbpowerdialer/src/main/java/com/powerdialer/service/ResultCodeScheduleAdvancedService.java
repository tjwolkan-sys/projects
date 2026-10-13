package com.powerdialer.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.powerdialer.dao.IResultCodeScheduleAdvancedDao;
import com.powerdialer.dto.ResultCodeScheduleAdvancedFormDTO;
import com.powerdialer.dto.ResultCodeScheduleAdvancedListDTO;
import com.powerdialer.model.ResultCodeScheduleAdvanced;
import com.powerdialer.validators.ResultCodeScheduleAdvancedValidator;

@Service
public class ResultCodeScheduleAdvancedService extends GenericService<ResultCodeScheduleAdvanced, ResultCodeScheduleAdvancedFormDTO, ResultCodeScheduleAdvancedListDTO> {

  @Autowired
  public ResultCodeScheduleAdvancedService(IResultCodeScheduleAdvancedDao dao) {
    super(dao, ResultCodeScheduleAdvanced.class, ResultCodeScheduleAdvancedFormDTO.class, ResultCodeScheduleAdvancedListDTO.class,
          new ResultCodeScheduleAdvancedValidator());
  }

}
