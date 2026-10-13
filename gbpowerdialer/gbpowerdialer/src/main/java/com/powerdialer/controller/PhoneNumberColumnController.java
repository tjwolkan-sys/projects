package com.powerdialer.controller;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.powerdialer.dto.PhoneNumberColumnFormDTO;
import com.powerdialer.dto.PhoneNumberColumnListDTO;
import com.powerdialer.model.PhoneNumberColumn;
import com.powerdialer.security.AuthUtil;
import com.powerdialer.security.PermissionUtil;
import com.powerdialer.service.PhoneNumberColumnService;
import com.powerdialer.util.Constants;
import com.powerdialer.util.ListDetailed;

@Controller
@RequestMapping("/phoneNumberColumn")
public class PhoneNumberColumnController extends GenericController<PhoneNumberColumn, PhoneNumberColumnFormDTO, PhoneNumberColumnListDTO> {

  @Autowired
  public PermissionUtil permissionUtil;

  @Autowired
  public AuthUtil authUtil;

  private final String [] authorities = { Constants.ROLE_SUPERVISOR, Constants.ROLE_SUPERADMIN, Constants.ROLE_TENANT_ADMIN };

  @Autowired
  public PhoneNumberColumnController(PhoneNumberColumnService phoneNumberColumnService) {
    super(phoneNumberColumnService);
  }

  @RequestMapping(value = "/list", method = RequestMethod.GET)
  @PreAuthorize("this.isGetAllAllowed(#filters)")
  public @ResponseBody ListDetailed<PhoneNumberColumnListDTO> getAll(@RequestParam(value = "page", required = false) JSONObject page, @RequestParam(value = "sort", required = false) JSONObject sort,
                          @RequestParam(value = "filters", required = false) JSONObject filters) {
    return service.getAll(page, sort, filters);
  }

  @Override
  public boolean isPreAuthorized() {
    return authUtil.isUserInRoles(authorities);
  }

  public boolean isGetAllAllowed(JSONObject filters) {
    return permissionUtil.isGetAllMethodAllowed(filters, Constants.CAMPAIGN_ID_KEY);
  }

}
