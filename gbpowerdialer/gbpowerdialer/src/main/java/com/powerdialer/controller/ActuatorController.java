package com.powerdialer.controller;

import java.util.Date;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.powerdialer.response.ActuatorResponse;
import com.powerdialer.util.ActuatorUtil;

@RestController
@RequestMapping("/actuator")
public class ActuatorController {

  @Autowired
  private ActuatorUtil actuatorUtil;

  @RequestMapping(value = "/info", method = RequestMethod.GET, produces = "application/json")
  public ActuatorResponse actuatorInfo() {
    return new ActuatorResponse(actuatorUtil.getProjectVersion(), new Date(actuatorUtil.getProjectBuildDateTimestamp()));
  }


}
