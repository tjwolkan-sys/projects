package com.powerdialer.controller;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import com.powerdialer.dto.ScheduleAutomationFormDTO;
import com.powerdialer.dto.ScheduleAutomationListDTO;
import com.powerdialer.model.ScheduleAutomation;
import com.powerdialer.security.AuthUtil;
import com.powerdialer.security.PermissionUtil;
import com.powerdialer.service.ScheduleAutomationService;
import com.powerdialer.util.Constants;
import com.powerdialer.util.ListDetailed;

@Controller
@RequestMapping("/scheduleAutomation")
public class ScheduleAutomationController extends GenericController<ScheduleAutomation, ScheduleAutomationFormDTO, ScheduleAutomationListDTO> {

  private final String [] authorities = { Constants.ROLE_SUPERADMIN, Constants.ROLE_TENANT_ADMIN, Constants.ROLE_SUPERVISOR};

  @Autowired
  private AuthUtil authUtil;

  @Autowired
  private PermissionUtil permissionUtil;

  @Autowired
  public ScheduleAutomationController(ScheduleAutomationService automationService) {
    super(automationService);
  }

  @Override
  public boolean isPreAuthorized() {
    return authUtil.isUserInRoles(authorities);
  }

  @Override
  @RequestMapping(value = "/list", method = RequestMethod.GET)
  @PreAuthorize("this.isGetAllAllowed(#filters)")
  public ListDetailed<ScheduleAutomationListDTO> getAll(@RequestParam(value = "page", required = false) JSONObject page, @RequestParam(value = "sort", required = false) JSONObject sort,
                                                        @RequestParam(value = "filters", required = false) JSONObject filters) {
    return super.getAll(page, sort, filters);
  }

  public boolean isGetAllAllowed(JSONObject filters) {
    return permissionUtil.isGetAllMethodAllowed(filters, Constants.CAMPAIGN_ID_KEY);
  }

}
