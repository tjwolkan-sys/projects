package com.powerdialer.controller;

import com.powerdialer.dto.ScheduleBlackoutAutomationFormDTO;
import com.powerdialer.dto.ScheduleBlackoutAutomationListDTO;
import com.powerdialer.dto.ScheduleFormDTO;
import com.powerdialer.dto.ScheduleListDTO;
import com.powerdialer.model.Schedule;
import com.powerdialer.model.ScheduleBlackoutAutomation;
import com.powerdialer.security.AuthUtil;
import com.powerdialer.security.PermissionUtil;
import com.powerdialer.service.ScheduleBlackoutService;
import com.powerdialer.service.ScheduleService;
import com.powerdialer.util.Constants;
import com.powerdialer.util.ListDetailed;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/scheduleBlackoutAutomation")
public class ScheduleBlackoutAutomationController extends GenericController<ScheduleBlackoutAutomation, ScheduleBlackoutAutomationFormDTO, ScheduleBlackoutAutomationListDTO> {

  private final String [] authorities = { Constants.ROLE_SUPERADMIN, Constants.ROLE_TENANT_ADMIN, Constants.ROLE_SUPERVISOR};

  @Autowired
  public AuthUtil authUtil;

  @Autowired
  public PermissionUtil permissionUtil;

  @Autowired
  public ScheduleBlackoutAutomationController(ScheduleBlackoutService scheduleService) {
    super(scheduleService);
  }

  @Override
  @RequestMapping(value = "/list", method = RequestMethod.GET)
  @PreAuthorize("this.isGetAllAllowed(#filters)")
  public ListDetailed<ScheduleBlackoutAutomationListDTO> getAll(@RequestParam(value = "page", required = false) JSONObject page, @RequestParam(value = "sort", required = false) JSONObject sort,
                                                        @RequestParam(value = "filters", required = false) JSONObject filters) {
    return super.getAll(page, sort, filters);
  }

  @Override
  public boolean isPreAuthorized() {
    return authUtil.isUserInRoles(authorities);
  }

  public boolean isGetAllAllowed(JSONObject filters) {
    return permissionUtil.isGetAllMethodAllowed(filters, Constants.CAMPAIGN_ID_KEY);
  }

}
