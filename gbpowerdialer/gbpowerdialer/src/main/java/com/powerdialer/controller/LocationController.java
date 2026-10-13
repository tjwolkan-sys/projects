package com.powerdialer.controller;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.powerdialer.dto.LocationFormDTO;
import com.powerdialer.dto.LocationListDTO;
import com.powerdialer.model.Location;
import com.powerdialer.security.AuthUtil;
import com.powerdialer.security.PermissionUtil;
import com.powerdialer.service.LocationService;
import com.powerdialer.util.Constants;
import com.powerdialer.util.ListDetailed;

@Controller
@RequestMapping("/location")
public class LocationController extends GenericController<Location, LocationFormDTO, LocationListDTO> {

  private final String [] authorities = { Constants.ROLE_SUPERADMIN, Constants.ROLE_TENANT_ADMIN, Constants.ROLE_SUPERVISOR};

  @Autowired
  public AuthUtil authUtil;

  @Autowired
  public PermissionUtil permissionUtil;

  @Autowired
  public LocationController(LocationService locationService) {
    super(locationService);
  }


  @RequestMapping(value = "/list", method = RequestMethod.GET)
  @PreAuthorize("this.isGetAllAllowed(#filters) || hasAuthority('\" + Constants.PERMISSION_CREATE_CAMPAIGN + \"')")
  @ResponseBody
  public ListDetailed<LocationListDTO> getAll(@RequestParam(value = "page", required = false) JSONObject page,
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

  @PreAuthorize("hasAnyRole('ROLE_SUPERADMIN','ROLE_TENANT_ADMIN','ROLE_SUPERVISOR')")
  @RequestMapping(value = "/list/initializedProxies", method = RequestMethod.GET)
  @ResponseBody
  public ListDetailed<LocationListDTO> getLocationWithServersAndCampaigns(@RequestParam(value = "page", required = false) JSONObject page,
                                                                          @RequestParam(value = "sort", required = false) JSONObject sort,
                                                                          @RequestParam(value = "filters", required = false) JSONObject filters) {
    return ((LocationService) getService()).getLocationWithServersAndCampaigns(page, sort, filters);
  }

}
