package com.powerdialer.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import com.powerdialer.dto.AdminLogDetailsFormDTO;
import com.powerdialer.dto.AdminLogDetailsListDTO;
import com.powerdialer.model.AdminLogDetails;
import com.powerdialer.service.AdminLogDetailsService;

@Controller
@RequestMapping("/adminLogDetails")
public class AdminLogDetailsController extends GenericController<AdminLogDetails, AdminLogDetailsFormDTO, AdminLogDetailsListDTO> {

  @Autowired
  public AdminLogDetailsController(AdminLogDetailsService adminLogDetailsService) {
    super(adminLogDetailsService);
  }

}
