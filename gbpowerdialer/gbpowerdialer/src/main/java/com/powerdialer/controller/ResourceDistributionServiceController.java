package com.powerdialer.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/rds")
public class ResourceDistributionServiceController {

  @Value("${rds.popup.url}")
  private String rdsCallerIdManagementPageLink;
  
  @GetMapping(value = "/static/link", produces = MediaType.TEXT_PLAIN_VALUE)
  @ResponseStatus(value = HttpStatus.OK)
  public String getRdsStaticLink() {
    return rdsCallerIdManagementPageLink;
  }
}
