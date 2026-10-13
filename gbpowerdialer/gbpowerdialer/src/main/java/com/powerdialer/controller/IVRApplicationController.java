package com.powerdialer.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import com.powerdialer.dto.IVRApplicationFormDTO;
import com.powerdialer.dto.IVRApplicationListDTO;
import com.powerdialer.model.IVRApplication;
import com.powerdialer.service.IVRApplicationService;

@Controller
@RequestMapping("/ivrApplication")
public class IVRApplicationController extends GenericController<IVRApplication, IVRApplicationFormDTO, IVRApplicationListDTO> {

  @Autowired
  public IVRApplicationController(IVRApplicationService ivrApplicationService) {
    super(ivrApplicationService);
  }

}
