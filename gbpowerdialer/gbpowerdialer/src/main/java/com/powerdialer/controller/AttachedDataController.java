package com.powerdialer.controller;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.powerdialer.dto.AttachedDataFormDTO;
import com.powerdialer.dto.AttachedDataListDTO;
import com.powerdialer.model.AttachedData;
import com.powerdialer.security.AuthUtil;
import com.powerdialer.security.PermissionUtil;
import com.powerdialer.service.AttachedDataService;
import com.powerdialer.util.Constants;
import com.powerdialer.util.ListDetailed;

@Controller
@RequestMapping("/attachedData")
public class AttachedDataController extends GenericController<AttachedData, AttachedDataFormDTO, AttachedDataListDTO> {

  @Autowired
  public PermissionUtil permissionUtil;

  @Autowired
  AuthUtil authUtil;

  private final String [] authorities = { Constants.ROLE_SUPERADMIN, Constants.ROLE_TENANT_ADMIN, Constants.ROLE_SUPERVISOR};

  @Autowired
  public AttachedDataController(AttachedDataService AttachedDataService) {
    super(AttachedDataService);
  }

  @Override
  public boolean isPreAuthorized() {
    return authUtil.isUserInRoles(authorities);
  }

  @RequestMapping(value = "/list", method = RequestMethod.GET)
  @PreAuthorize("this.isGetAllAllowed(#filters)")
  public @ResponseBody
  ListDetailed<AttachedDataListDTO> getAll(@RequestParam(value = "page", required = false) JSONObject page, @RequestParam(value = "sort", required = false) JSONObject sort,
                                                @RequestParam(value = "filters", required = false) JSONObject filters) {
    return service.getAll(page, sort, filters);
  }

  public boolean isGetAllAllowed(JSONObject filters) {
    return permissionUtil.isGetAllMethodAllowed(filters, Constants.CAMPAIGN_ID_KEY);
  }

}
