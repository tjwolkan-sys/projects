package com.powerdialer.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.powerdialer.util.Constants;

@RestController
@RequestMapping(value = "/serviceStatuses")
public class ServiceStatusesController {

  @RequestMapping(value = "/list", method = RequestMethod.GET)
  @PreAuthorize("hasAuthority('" + Constants.PERMISSION_DIALER_ADMINISTRATION + "')")
  public Constants.SERVICE_STATUS[] getAll() {
    return Constants.SERVICE_STATUS.values();
  }

}
