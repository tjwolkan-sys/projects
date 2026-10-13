package com.powerdialer.controller;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.powerdialer.dto.TenantFormDTO;
import com.powerdialer.dto.TenantListDTO;
import com.powerdialer.model.Tenant;
import com.powerdialer.security.AuthUtil;
import com.powerdialer.service.TenantService;
import com.powerdialer.util.Constants;
import com.powerdialer.util.ListDetailed;

@Controller
@RequestMapping("/tenant")
public class TenantController extends GenericController<Tenant, TenantFormDTO, TenantListDTO> {

  @Autowired
  private AuthUtil authUtil;

  private final String [] authorities = { Constants.ROLE_USER, Constants.ROLE_SUPERADMIN, Constants.ROLE_TENANT_ADMIN, Constants.ROLE_SUPERVISOR, Constants.ROLE_TEAMLEAD};

  @Autowired
  public TenantController(TenantService tenantService) {
    super(tenantService);
  }

  @Override
  public boolean isPreAuthorized() {
    return authUtil.isUserInRoles(authorities);
  }

  @Override
  @RequestMapping(value = "/list", method = RequestMethod.GET)
  public @ResponseBody ListDetailed<TenantListDTO> getAll(@RequestParam(value = "page", required = false) JSONObject page, @RequestParam(value = "sort", required = false) JSONObject sort,
                                                          @RequestParam(value = "filters", required = false) JSONObject filters) {
   return super.getAll(page, sort, filters);
  }

}
