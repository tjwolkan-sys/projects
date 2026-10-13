package com.powerdialer.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import com.powerdialer.dto.FilterFormDTO;
import com.powerdialer.dto.FilterListDTO;
import com.powerdialer.model.Filter;
import com.powerdialer.security.AuthUtil;
import com.powerdialer.service.FilterService;
import com.powerdialer.util.Constants;

@Controller
@RequestMapping("/filter")
public class FilterController extends GenericController<Filter, FilterFormDTO, FilterListDTO> {

  private final String [] authorities = { Constants.ROLE_SUPERADMIN, Constants.ROLE_TENANT_ADMIN, Constants.ROLE_SUPERVISOR, Constants.ROLE_USER, Constants.ROLE_TEAMLEAD};

  @Autowired
  AuthUtil authUtil;

  @Autowired
  public FilterController(FilterService FilterService) {
    super(FilterService);
  }

  @Override
  public boolean isPreAuthorized() {
    return authUtil.isUserInRoles(authorities);
  }
}
