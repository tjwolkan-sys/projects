package com.powerdialer.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import com.powerdialer.dto.ResultCodeScheduleAdvancedFormDTO;
import com.powerdialer.dto.ResultCodeScheduleAdvancedListDTO;
import com.powerdialer.model.ResultCodeScheduleAdvanced;
import com.powerdialer.service.ResultCodeScheduleAdvancedService;

@Controller
@RequestMapping("/resultCodeScheduleAdvanced")
public class ResultCodeScheduleAdvancedController extends GenericController<ResultCodeScheduleAdvanced, ResultCodeScheduleAdvancedFormDTO, ResultCodeScheduleAdvancedListDTO> {

  @Autowired
  public ResultCodeScheduleAdvancedController(ResultCodeScheduleAdvancedService resultCodeScheduleService) {
    super(resultCodeScheduleService);
  }

}
