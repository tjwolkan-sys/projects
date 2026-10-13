package com.powerdialer.controller;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.powerdialer.dto.RoutingGroupDTO;
import com.powerdialer.model.RoutingGroup;
import com.powerdialer.security.AuthUtil;
import com.powerdialer.security.PermissionUtil;
import com.powerdialer.service.RoutingGroupService;
import com.powerdialer.util.Constants;
import com.powerdialer.util.ListDetailed;

@Controller
@RequestMapping("/routingGroup")
public class RoutingGroupController extends GenericController<RoutingGroup, RoutingGroupDTO, RoutingGroupDTO> {

  private final String [] authorities = { Constants.ROLE_SUPERADMIN, Constants.ROLE_TENANT_ADMIN, Constants.ROLE_SUPERVISOR};

  @Autowired
  public AuthUtil authUtil;

  @Autowired
  public PermissionUtil permissionUtil;

  @Autowired
  public RoutingGroupController(RoutingGroupService routingGroupService) {
    super(routingGroupService);
  }

  @RequestMapping(value = "/list", method = RequestMethod.GET)
  @PreAuthorize("this.isGetAllAllowed(#filters) || hasAuthority('\" + Constants.PERMISSION_CREATE_CAMPAIGN + \"')")
  @ResponseBody
  public ListDetailed<RoutingGroupDTO> getAll(@RequestParam(value = "page", required = false) JSONObject page,
                                              @RequestParam(value = "sort", required = false) JSONObject sort,
                                              @RequestParam(value = "filters", required = false) JSONObject filters) {
    return service.getAll(page, sort, filters);
  }

  @Override
  public boolean isPreAuthorized() {
    return authUtil.isUserInRoles(authorities);
  }

  public boolean isGetAllAllowed(JSONObject filters) {
    return permissionUtil.isGetAllMethodAllowed(filters, Constants.CAMPAIGNS_CAMPAIGN_ID_KEY);
  }

}
