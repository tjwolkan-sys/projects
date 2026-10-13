package com.powerdialer.controller;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.powerdialer.dto.CampaignGroupFormDTO;
import com.powerdialer.dto.CampaignGroupListDTO;
import com.powerdialer.model.CampaignGroup;
import com.powerdialer.security.AuthUtil;
import com.powerdialer.security.PermissionUtil;
import com.powerdialer.service.CampaignGroupService;
import com.powerdialer.util.Constants;
import com.powerdialer.util.ListDetailed;

import java.util.List;

@Controller
@RequestMapping("/campaignGroup")
public class CampaignGroupController extends GenericController<CampaignGroup, CampaignGroupFormDTO, CampaignGroupListDTO> {

  private final String [] authorities = { Constants.ROLE_SUPERADMIN, Constants.ROLE_TENANT_ADMIN, Constants.ROLE_SUPERVISOR};

  @Autowired
  PermissionUtil permissionUtil;

  @Autowired
  AuthUtil authUtil;

  @Autowired
  public CampaignGroupController(CampaignGroupService campaignGroupService) {
    super(campaignGroupService);
  }

  @RequestMapping(value = "/list", method = RequestMethod.GET)
  @PreAuthorize("this.isGetAllAllowed(#filters)")
  @Override
  public @ResponseBody ListDetailed<CampaignGroupListDTO> getAll(@RequestParam(value = "page", required = false) JSONObject page, @RequestParam(value = "sort", required = false) JSONObject sort,
                                                @RequestParam(value = "filters", required = false) JSONObject filters) {
    return super.getAll(page, sort, filters);
  }

  @Override
  public boolean isPreAuthorized() {
    return authUtil.isUserInRoles(authorities);
  }

  @GetMapping
  @PreAuthorize("@permissionUtil.canEditCampaigns(#campaignId)")
  public @ResponseBody List<CampaignGroupListDTO> getByCampaign(@RequestParam Long campaignId) {
    return ((CampaignGroupService)getService()).findByCampaignId(campaignId);
  }

  public boolean isGetAllAllowed(JSONObject filters) {
    return permissionUtil.isGetAllMethodAllowed(filters, Constants.CAMPAIGN_ID_KEY);
  }

}
