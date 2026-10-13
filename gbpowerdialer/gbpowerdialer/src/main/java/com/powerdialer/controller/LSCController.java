package com.powerdialer.controller;

import com.powerdialer.dto.LSCFormDTO;
import com.powerdialer.dto.LSCListDTO;
import com.powerdialer.engine.LSCEngine;
import com.powerdialer.exceptions.DuplicatedNameException;
import com.powerdialer.model.LSC;
import com.powerdialer.security.AuthUtil;
import com.powerdialer.security.PermissionUtil;
import com.powerdialer.service.LSCService;
import com.powerdialer.util.Constants;
import com.powerdialer.util.ListDetailed;
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

import java.util.List;

@Controller
@RequestMapping("/lsc")
public class LSCController extends GenericController<LSC, LSCFormDTO, LSCListDTO> {

  private final String [] authorities = { Constants.ROLE_SUPERADMIN, Constants.ROLE_TENANT_ADMIN, Constants.ROLE_SUPERVISOR};

  @Autowired
  public AuthUtil authUtil;

  @Autowired
  public PermissionUtil permissionUtil;

  @Autowired
  private LSCEngine lscEngine;

  @Autowired
  public LSCController(LSCService lscService) {
    super(lscService);
  }

  @Override
  @RequestMapping(value = "/list", method = RequestMethod.GET)
  @PreAuthorize("this.isGetAllAllowed(#filters) || hasAuthority('" + Constants.PERMISSION_CREATE_CAMPAIGN + "')")
  public ListDetailed<LSCListDTO> getAll(@RequestParam(value = "page", required = false) JSONObject page, @RequestParam(value = "sort", required = false) JSONObject sort,
                                         @RequestParam(value = "filters", required = false) JSONObject filters) {
    return super.getAll(page, sort, filters);
  }

  @Override
  public boolean isPreAuthorized() {
    return authUtil.isUserInRoles(authorities);
  }

  @Override
  @RequestMapping(method = RequestMethod.POST)
  public @ResponseBody LSCFormDTO save(@RequestBody LSCFormDTO lscFormDTO) throws DuplicatedNameException {
    LSCFormDTO result = getService().save(lscFormDTO);
    lscEngine.updateLSC(result.getId());
    return result;
  }

  @Override
  @RequestMapping(value = "/{id}", method = RequestMethod.PUT)
  public @ResponseBody LSCFormDTO save(@PathVariable long id, @RequestBody LSCFormDTO lscFormDTO) throws DuplicatedNameException {
    LSCFormDTO result = getService().save(lscFormDTO);
    lscEngine.updateLSC(id);
    return result;
  }

  @Override
  @RequestMapping(value = "/batch", method = RequestMethod.PUT)
  public @ResponseBody List<Long> setEnabled(@RequestParam(value = "ids") String ids, @RequestParam(value = "enabled") boolean enabled) {
    List<Long> result = super.setEnabled(ids, enabled);
    result.forEach(lscEngine::updateLSC);
    return result;
  }

  @Override
  @RequestMapping(value = "/{id}", method = RequestMethod.DELETE)
  public @ResponseBody Long delete(@PathVariable long id) {
    Long result = super.delete(id);
    lscEngine.updateLSC(id);
    return result;
  }

  @Override
  @RequestMapping(value = "/batch", method = RequestMethod.DELETE)
  public @ResponseBody List<Long> delete(@RequestParam(value = "ids") String ids) {
    List<Long> result = super.delete(ids);
    result.forEach(lscEngine::updateLSC);
    return result;
  }

  public boolean isGetAllAllowed(JSONObject filters) {
    return permissionUtil.isGetAllMethodAllowed(filters, Constants.CAMPAIGNS_CAMPAIGN_ID_KEY);
  }

}
