package com.powerdialer.controller;

import java.util.List;

import org.apache.commons.beanutils.ConvertUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.powerdialer.dto.ResultCodeFormDTO;
import com.powerdialer.dto.ResultCodeListDTO;
import com.powerdialer.model.ResultCode;
import com.powerdialer.security.AuthUtil;
import com.powerdialer.service.ResultCodeService;
import com.powerdialer.util.Constants;

@Controller
@RequestMapping("/resultCode")
@PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_SUPERADMIN', 'ROLE_TENANT_ADMIN', 'ROLE_SUPERVISOR', 'ROLE_TEAMLEAD')")
public class ResultCodeController extends GenericController<ResultCode, ResultCodeFormDTO, ResultCodeListDTO> {

  @Autowired
  AuthUtil authUtil;

  private final String[] authorities = { Constants.ROLE_SUPERADMIN, Constants.ROLE_TENANT_ADMIN, Constants.ROLE_SUPERVISOR };

  @Autowired
  public ResultCodeController(ResultCodeService resultCodeService) {
    super(resultCodeService);
  }

  @Override
  public boolean isPreAuthorized() {
    return authUtil.isUserInRoles(authorities);
  }

  @RequestMapping(value = "/list/campaign/{id}", method = RequestMethod.GET)
  public @ResponseBody List<ResultCodeListDTO> getResultCodesByCampaignId(@PathVariable long id, @RequestParam boolean nonFinalOnly) {
    return ((ResultCodeService) getService()).getResultCodesByCampaignId(id, nonFinalOnly);
  }

  @RequestMapping(value = "/list/group", method = RequestMethod.GET)
  public @ResponseBody List<ResultCodeListDTO> getResultCodesByCampaignId(@RequestParam(value = "ids") String ids, @RequestParam boolean nonFinalOnly) {
    return ((ResultCodeService) getService()).getResultCodesByGroupIds((Long[]) ConvertUtils.convert(ids.split(","), Long[].class), nonFinalOnly);
  }

}
