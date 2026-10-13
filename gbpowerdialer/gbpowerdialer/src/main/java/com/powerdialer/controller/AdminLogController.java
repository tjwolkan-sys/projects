package com.powerdialer.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import com.powerdialer.dto.AdminLogFormDTO;
import com.powerdialer.dto.AdminLogListDTO;
import com.powerdialer.model.AdminLog;
import com.powerdialer.service.AdminLogService;

@Controller
@RequestMapping("/adminLogs")
public class AdminLogController extends GenericController<AdminLog, AdminLogFormDTO, AdminLogListDTO> {

  @Autowired
  public AdminLogController(AdminLogService adminLogService) {
    super(adminLogService);
  }

}
