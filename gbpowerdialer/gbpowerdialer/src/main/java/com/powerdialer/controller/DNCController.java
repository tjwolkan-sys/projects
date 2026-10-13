package com.powerdialer.controller;

import java.util.List;

import com.powerdialer.engine.DNCEngine;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.powerdialer.dto.DNCFormDTO;
import com.powerdialer.dto.DNCListDTO;
import com.powerdialer.exceptions.DuplicatedNameException;
import com.powerdialer.model.DNC;
import com.powerdialer.security.AuthUtil;
import com.powerdialer.security.PermissionUtil;
import com.powerdialer.service.DNCService;
import com.powerdialer.util.Constants;
import com.powerdialer.util.ListDetailed;

@Controller
@RequestMapping("/dnc")
public class DNCController extends GenericController<DNC, DNCFormDTO, DNCListDTO> {

  private final String [] authorities = { Constants.ROLE_SUPERADMIN, Constants.ROLE_TENANT_ADMIN, Constants.ROLE_SUPERVISOR};

  @Autowired
  public AuthUtil authUtil;

  @Autowired
  public PermissionUtil permissionUtil;

  @Autowired
  private DNCService dncService;

  @Autowired
  private DNCEngine dncEngine;

  @Autowired
  public DNCController(DNCService dncService) {
    super(dncService);
  }

  @Override
  @PreAuthorize("this.isDNCDefaultForPermittedCampaign(#id) || this.isPreAuthorized()")
  @RequestMapping(value = "/{id}", method = RequestMethod.PUT)
  public @ResponseBody DNCFormDTO save(@PathVariable long id, @RequestBody DNCFormDTO d) throws DuplicatedNameException {
    DNCFormDTO result = super.save(id, d);
    dncEngine.updateDnc(result.getId());
    return result;
  }

  @Override
  @RequestMapping(method = RequestMethod.POST)
  public @ResponseBody DNCFormDTO save(@RequestBody DNCFormDTO d) throws DuplicatedNameException {
    DNCFormDTO result = super.save(d);
    dncEngine.updateDnc(result.getId());
    return result;
  }

  @Override
  @PreAuthorize("this.isDNCDefaultForPermittedCampaign(#id) || this.isPreAuthorized()")
  @RequestMapping(value = "/{id}", method = RequestMethod.GET)
  public @ResponseBody DNCFormDTO get(@PathVariable long id) {
    return service.get(id);
  }

  public boolean isDNCDefaultForPermittedCampaign(String dncId) {
    return dncService.isDNCDefaultForPermittedCampaign(dncId);
  }

  @RequestMapping(value = "/list", method = RequestMethod.GET)
  @Override
  @PreAuthorize("this.isGetAllAllowed(#filters) || this.isPreAuthorized()")
  public ListDetailed<DNCListDTO> getAll(@RequestParam(value = "page", required = false) JSONObject page, @RequestParam(value = "sort", required = false) JSONObject sort,
                                         @RequestParam(value = "filters", required = false) JSONObject filters) {
    return super.getAll(page, sort, filters);
  }

  @Override
  public boolean isPreAuthorized() {
    return authUtil.isUserInRoles(authorities);
  }

  public boolean isGetAllAllowed(JSONObject filters) {
    return permissionUtil.isGetAllMethodAllowed(filters, Constants.CAMPAIGNS_CAMPAIGN_ID_KEY);
  }

  @RequestMapping(value = "/batch", method = RequestMethod.PUT)
  @PreAuthorize("this.isPreAuthorized() || this.isDNCsDefaultToPermittedCampaigns(#ids)")
  @Override
  public @ResponseBody List<Long> setEnabled(@RequestParam(value = "ids") String ids, @RequestParam(value = "enabled") boolean enabled) {
    List<Long> result = super.setEnabled(ids, enabled);
    result.forEach(dncEngine::updateDnc);
    return result;
  }

  @Override
  @RequestMapping(value = "/{id}", method = RequestMethod.DELETE)
  public @ResponseBody Long delete(@PathVariable long id) {
    Long result = super.delete(id);
    dncEngine.updateDnc(result);
    return result;
  }

  @Override
  @RequestMapping(value = "/batch", method = RequestMethod.DELETE)
  public @ResponseBody List<Long> delete(@RequestParam(value = "ids") String ids) {
    List<Long> result = super.delete(ids);
    result.forEach(dncEngine::updateDnc);
    return result;
  }

  public boolean isDNCsDefaultToPermittedCampaigns(String ids) {
    return dncService.isDNCsDefaultForPermittedCampaigns(ids);
  }

}
