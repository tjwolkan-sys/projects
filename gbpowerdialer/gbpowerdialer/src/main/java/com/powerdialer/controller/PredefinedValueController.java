package com.powerdialer.controller;


import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import com.powerdialer.dto.PredefinedValueDTO;
import com.powerdialer.security.AuthUtil;
import com.powerdialer.security.PermissionUtil;
import com.powerdialer.service.PredefinedValueService;
import com.powerdialer.util.Constants;


@Controller
@RequestMapping("/predefinedValues")
public class PredefinedValueController {

  @Autowired
  PredefinedValueService predefinedValueService;

  @Autowired
  AuthUtil authUtil;

  @Autowired
  PermissionUtil permissionUtil;

  private final String [] authorities = { Constants.ROLE_SUPERADMIN, Constants.ROLE_TENANT_ADMIN, Constants.ROLE_SUPERVISOR};

  @RequestMapping(value = "/list/{campaignId}", method = RequestMethod.GET)
  @PreAuthorize("this.isPredefinedValuesByCampaignIdAllowed(#campaignId)")
  public @ResponseBody List<PredefinedValueDTO> getPredefinedValuesByCampaignId(@PathVariable String campaignId) {
    return predefinedValueService.getPredefinedValuesByCampaign(campaignId);
  }

  public boolean isPredefinedValuesByCampaignIdAllowed(String campaignId) {
    return authUtil.isUserInRoles(authorities) || permissionUtil.canEditCampaignNonTechProperties(campaignId);
  }
}
