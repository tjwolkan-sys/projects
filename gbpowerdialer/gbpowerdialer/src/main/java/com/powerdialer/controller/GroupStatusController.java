package com.powerdialer.controller;

import java.util.Set;

import org.apache.commons.beanutils.ConvertUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.powerdialer.model.GroupStatus;
import com.powerdialer.service.CampaignGroupService;
import com.powerdialer.service.StatusService;

@Controller
@RequestMapping("/groupStatus")
public class GroupStatusController {

  @Autowired
  CampaignGroupService groupService;

  @Autowired
  StatusService statusService;

  @RequestMapping(value = "/commonResultStatuses", method = RequestMethod.GET)
  @ResponseBody
  public Set<GroupStatus> getCommonResultStatuses(@RequestParam(value = "ids", required = true) String ids) {
    if (ids != null) {
      return statusService.getCommonResultStatuses((Long[]) ConvertUtils.convert(ids.split(","), Long[].class));
    } else
      return null;
  }

}
