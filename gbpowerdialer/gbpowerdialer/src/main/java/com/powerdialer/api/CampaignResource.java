package com.powerdialer.api;

import java.util.Collection;
import java.util.Optional;

import com.powerdialer.model.CampaignFiltersStats;
import com.powerdialer.service.TokenCampaignValidatorService;
import com.powerdialer.util.Constants;
import org.apache.commons.beanutils.ConvertUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.powerdialer.dto.CampaignFormDTO;
import com.powerdialer.dto.CampaignListDTO;
import com.powerdialer.security.PermissionUtil;
import com.powerdialer.service.CampaignService;

/**
 * Rest controller providing campaigns information
 */
@RestController
@RequestMapping(value = "/api/campaigns", produces = MediaType.APPLICATION_JSON_VALUE)
public class CampaignResource {

  @Autowired
  private CampaignService campaignService;

  @Autowired
  private PermissionUtil permissionUtil;

  @Autowired
  private TokenCampaignValidatorService tokenCampaignValidatorService;

  /**
   * Get campaigns data by ids
   * @param ids comma separated ids
   */
  @RequestMapping(method = RequestMethod.GET)
  public Collection<CampaignFormDTO> getAll(@RequestParam(value = "ids", required = false, defaultValue = ",") String ids) {
    return permissionUtil.restrictCampaignList(campaignService.getAll(null, false, (Long[]) ConvertUtils.convert(ids.split(","), Long[].class)));
  }

  /**
   * Get campaigns realtime info by ids
   * @param ids comma separated ids
   */
  @RequestMapping(value = "/realtimeInfo", method = RequestMethod.GET)
  public Collection<CampaignListDTO> realtimeInfo(@RequestParam(value = "ids", required = false, defaultValue = ",") String ids) {
    return permissionUtil.restrictCampaignList(campaignService.realTimeInfo((Long[]) ConvertUtils.convert(ids.split(","), Long[].class)));
  }

  /**
   * Get campaigns info by ids including non-running (idle) campaigns.
   * Running campaigns are served from the realtime context; idle campaigns are built from the
   * persisted list snapshot (DB entity + memento). Empty ids returns only running campaigns.
   * @param ids comma separated ids
   */
  @RequestMapping(value = "/info", method = RequestMethod.GET)
  public Collection<CampaignListDTO> info(@RequestParam(value = "ids", required = false, defaultValue = ",") String ids) {
    return permissionUtil.restrictCampaignList(campaignService.campaignInfo((Long[]) ConvertUtils.convert(ids.split(","), Long[].class)));
  }

  @RequestMapping(value = "/{id}/filterStats", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
  public @ResponseBody ResponseEntity<CampaignFiltersStats> getCampaignFiltersStatsById(@PathVariable Long id,
                                                                                        @RequestHeader(value = Constants.API_TOKEN_HEADER, required = false) String campaignToken) {
    tokenCampaignValidatorService.validateCampaignToken(campaignToken, String.valueOf(id));
    Optional<CampaignFiltersStats> campaignFilterStats = campaignService.getCampaignFiltersStatsById(id);
    return campaignFilterStats.map(ResponseEntity::ok)
        .orElseGet(() -> new ResponseEntity<>(HttpStatus.NOT_FOUND));
  }
}
