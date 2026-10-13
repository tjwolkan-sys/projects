package com.powerdialer.security;

import com.powerdialer.dto.ActionLogDTO;
import com.powerdialer.dto.CampaignFormDTO;
import com.powerdialer.dto.CampaignListDTO;
import com.powerdialer.publish.CampaignCreatedEvent;
import com.powerdialer.publish.CampaignMergedEvent;
import com.powerdialer.service.CampaignService;
import com.powerdialer.util.Constants;
import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.collections.CollectionUtils;
import org.hibernate.Criteria;
import org.hibernate.criterion.Disjunction;
import org.hibernate.criterion.Restrictions;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component("permissionUtil")
@Lazy(false)
public class PermissionUtil {

  private static final Logger LOG = LoggerFactory.getLogger(PermissionUtil.class);
  private static final String [] GET_ALL_METHOD_AUTHORITIES = { Constants.ROLE_SUPERADMIN, Constants.ROLE_TENANT_ADMIN, Constants.ROLE_SUPERVISOR};

  private final Map<String, Long> campaignIdToTenantId = new ConcurrentHashMap<>();

  @Autowired
  private AuthUtil authUtil;

  @Autowired
  private CampaignService campaignService;

  public AuthUtil getAuthUtil() {
    return authUtil;
  }

  @PostConstruct
  private void init() {
    campaignService.getAll(false)
            .forEach(c -> campaignIdToTenantId.put(c.getId().toString(), c.getTenant().getId()));
  }

  @EventListener
  public void onCampaignCreated(CampaignCreatedEvent event) {
    campaignIdToTenantId.put(event.getCampaign().getId().toString(), event.getCampaign().getTenant().getId());
  }

  @EventListener
  public void onCampaignMerged(CampaignMergedEvent event) {
    campaignIdToTenantId.put(event.getNewCampaign().getId().toString(), event.getNewCampaign().getTenant().getId());
  }

  /**
   * Check if {@param authentication} has permission for viewing campaign
   * @param campaignId
   * @param authentication
   * @return
   */
  public boolean canViewCampaign(String campaignId, Authentication authentication) {
    return hasPermissionForCampaigns(Constants.PERMISSION_VIEW_CAMPAIGN, Collections.singleton(campaignId), authentication);
  }

  /**
   * Check if authenticated user has permission for viewing campaign
   * @param campaignId
   * @return
   */
  public boolean canViewCampaign(String campaignId) {
    return hasPermissionForCampaigns(Constants.PERMISSION_VIEW_CAMPAIGN, Collections.singleton(campaignId));
  }

  /**
   * Check if authenticated user has permission for editing campaigns
   * @param campaignIds
   * @return
   */
  public boolean canEditCampaigns(String campaignIds) {
    return hasPermissionForCampaigns(Constants.PERMISSION_EDIT_CAMPAIGN, new HashSet<>(Arrays.asList(campaignIds.split(","))));
  }

  /**
   * Check if authenticated user has permission for editing campaign
   * @param campaignId
   * @return
   */
  public boolean canEditCampaignNonTechProperties(String campaignId) {
    return campaignId == null ? authUtil.isUserInRole(Constants.PERMISSION_CREATE_CAMPAIGN)
                              : hasPermissionForCampaigns(Constants.PERMISSION_EDIT_CAMPAIGN_NON_TECH, Collections.singleton(campaignId));
  }

  public boolean canEditCampaign(String campaignId) {
    return campaignId == null ? authUtil.isUserInRole(Constants.PERMISSION_CREATE_CAMPAIGN)
                              : hasPermissionForCampaigns(Constants.PERMISSION_EDIT_CAMPAIGN, Collections.singleton(campaignId));
  }

  public boolean canEditCampaign(String campaignId, CampaignFormDTO d) {
    if (userHasCreateOrUpdatePermissionsForCampaign(campaignId)) {
      if (userHasTechPermissionForCampaign(campaignId) && !campaignContainsForbiddenFieldsForPermission(d, Constants.ALLOWED_TECH_FIELDS)) {
        return true;
      } else if (userHasNonTechPermissionForCampaign(campaignId) && !campaignContainsForbiddenFieldsForPermission(d, Constants.ALLOWED_NON_TECH_FIELDS)) {
        return true;
      } else return userHasFullPermissionsForCampaign(campaignId);
    }
    return false;
  }

  private boolean userHasTechPermissionForCampaign(String campaignId) {
    return hasPermissionForCampaigns(Constants.PERMISSION_EDIT_CAMPAIGN_TECH, Collections.singleton(campaignId))
        && !hasPermissionForCampaigns(Constants.PERMISSION_EDIT_CAMPAIGN, Collections.singleton(campaignId));
  }

  private boolean campaignContainsForbiddenFieldsForPermission(CampaignFormDTO d, Set<String> allowedFields) {
    try {
      Map<String, Object> props = PropertyUtils.describe(d);
      for (Map.Entry<String, Object> e : props.entrySet()) {
        if ("class".equals(e.getKey())) {
          continue;
        }
        if (!allowedFields.contains(e.getKey()) && e.getValue() != null) {
          return true;
        }
      }
    } catch (Exception e) {
      LOG.error("Impossible to check if campaign contains forbidden fields due to following exception: " + e.getMessage());
    }
    return false;
  }

  private boolean userHasNonTechPermissionForCampaign(String campaignId) {
    return hasPermissionForCampaigns(Constants.PERMISSION_EDIT_CAMPAIGN_NON_TECH, Collections.singleton(campaignId))
        && !hasPermissionForCampaigns(Constants.PERMISSION_EDIT_CAMPAIGN_TECH, Collections.singleton(campaignId));
  }

  private boolean userHasFullPermissionsForCampaign(String campaignId) {
    return hasPermissionForCampaigns(Constants.PERMISSION_EDIT_CAMPAIGN, Collections.singleton(campaignId));
  }

  private boolean userHasCreateOrUpdatePermissionsForCampaign(String campaignId) {
    return campaignId == null ? authUtil.isUserInRole(Constants.PERMISSION_CREATE_CAMPAIGN)
                              : hasPermissionForCampaigns(Constants.PERMISSION_EDIT_CAMPAIGN_NON_TECH, Collections.singleton(campaignId));
  }


  /**
   * Check if authenticated user has permission for managing campaign
   * @param campaignId
   * @return
   */
  public boolean canManageCampaign(String campaignId) {
    return hasPermissionForCampaigns(Constants.PERMISSION_MANAGE_CAMPAIGN, Collections.singleton(campaignId));
  }

  /**
   * Check if authenticated user has specified permission for campaigns
   * @param permission
   * @param campaignIds
   * @return
   */
  public boolean hasPermissionForCampaigns(String permission, Set<String> campaignIds) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null)
      return false;
    return hasPermissionForCampaigns(permission, campaignIds, authentication);
  }

  /**
   * Check if {@param authentication} has specified permission for campaigns
   * @param permission
   * @param campaignIds
   * @return
   */
  private boolean hasPermissionForCampaigns(String permission, Set<String> campaignIds, Authentication authentication) {
    if (authentication == null) {
      return false;
    } if (authUtil.isInRole(authentication, Constants.ROLE_SUPERADMIN)) {
      return true;
    }
    Set<String> authoritiesValues = new HashSet<>();
    for (GrantedAuthorityWithValue authority : authUtil.getPermissions(permission, authentication.getAuthorities())) {
      authoritiesValues.add(authority.getValue());
      if (authority.getValue().equals(Constants.PERMISSION_VALUE_ALL)) {
        return true;
      }
    }
    for (String campaignId : campaignIds) {
      if (!authoritiesValues.contains(campaignId) && !authoritiesValues.contains(Constants.PERMISSION_VALUE_ALL + campaignIdToTenantId.get(campaignId))) {
        return false;
      }
    }
    return true;
  }

  /**
   * Add user permissions as restrictions to criteria
   * @param criteria
   */
  public void addCampaignPermissionFilter(Criteria criteria) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && !authUtil.isUserInRole(Constants.ROLE_SUPERADMIN)) {
      Set<GrantedAuthorityWithValue> permissions = authUtil.getPermissions(Constants.PERMISSION_VIEW_CAMPAIGN, authentication.getAuthorities());
      if (!CollectionUtils.isEmpty(permissions)) {
        Set<String> permissionValues = permissions.stream().map(GrantedAuthorityWithValue::getValue).collect(Collectors.toSet());
        if (permissionValues.contains(Constants.PERMISSION_VALUE_ALL))
          return;
        List<Long> campaignIds = getPermittedCampaignIds(permissionValues);
        List<Long> tenantIds = getPermittedTenantIds(permissionValues);
        Disjunction disjunction = Restrictions.disjunction();
        if (CollectionUtils.isNotEmpty(campaignIds)) {
          disjunction.add(Restrictions.in("id", campaignIds));
        }
        if (CollectionUtils.isNotEmpty(tenantIds)) {
          disjunction.add(Restrictions.in("tenant.id", tenantIds));
        }
        if (CollectionUtils.isNotEmpty(campaignIds) || CollectionUtils.isNotEmpty(tenantIds)) {
          criteria.add(disjunction);
        }
      } else {
        criteria.add(Restrictions.eq("id", -1L));
      }
    }
  }

  private List<Long> getPermittedCampaignIds(Set<String> permissionValues) {
    return permissionValues.stream()
        .filter(permission -> !permission.isEmpty() && !permission.startsWith(Constants.PERMISSION_VALUE_ALL))
        .map(Long::valueOf)
        .collect(Collectors.toList());
  }

  private List<Long> getPermittedTenantIds(Set<String> permissionValues) {
    return permissionValues.stream()
        .filter(permission -> permission.startsWith(Constants.PERMISSION_VALUE_ALL))
        .map(p -> p.substring(Constants.PERMISSION_VALUE_ALL.length()))
        .map(Long::valueOf)
        .collect(Collectors.toList());
  }

  /**
   * Filter action logs using user's permissions
   * @param logs
   * @return
   */
  public Collection<ActionLogDTO> restrictActionLog(Collection<ActionLogDTO> logs) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && !authUtil.isUserInRole(Constants.ROLE_SUPERADMIN)) {
      Set<GrantedAuthorityWithValue> permissions = authUtil.getPermissions(Constants.PERMISSION_VIEW_CAMPAIGN, authentication.getAuthorities());
      Set<String> permissionValues = permissions.stream().map(GrantedAuthorityWithValue::getValue).collect(Collectors.toSet());
      if (permissionValues.contains(Constants.PERMISSION_VALUE_ALL)) {
        return logs;
      }
      List<Long> campaignIds = getPermittedCampaignIds(permissionValues);
      List<Long> tenantIds = getPermittedTenantIds(permissionValues);
      return logs.stream()
          .filter(log -> log.getCampaign() != null)
          .filter(log -> campaignIds.contains(log.getCampaign().getId()) || tenantIds.contains(log.getTenant().getId()))
          .collect(Collectors.toList());
    }
    return logs;
  }

  /**
   * Filter campaigns using user's permissions
   */
  public <T extends CampaignListDTO> Collection<T> restrictCampaignList(Collection<T> campaignListDTOs) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && !authUtil.isUserInRole(Constants.ROLE_SUPERADMIN)) {
      Set<GrantedAuthorityWithValue> permissions = authUtil.getPermissions(Constants.PERMISSION_VIEW_CAMPAIGN, authentication.getAuthorities());
      Set<String> permissionValues = permissions.stream().map(GrantedAuthorityWithValue::getValue).collect(Collectors.toSet());
      if (permissionValues.contains(Constants.PERMISSION_VALUE_ALL)) {
        return campaignListDTOs;
      }
      List<Long> campaignIds = getPermittedCampaignIds(permissionValues);
      List<Long> tenantIds = getPermittedTenantIds(permissionValues);
      return campaignListDTOs.stream()
          .filter(dto -> campaignIds.contains(dto.getId()) || tenantIds.contains(dto.getTenant()))
          .collect(Collectors.toList());
    }
    return campaignListDTOs;
  }

  public boolean isGetAllMethodAllowed(JSONObject filters, String campaignIdKey) {
    return authUtil.isUserInRoles(GET_ALL_METHOD_AUTHORITIES) || (filters.getJSONArray(campaignIdKey) != null)
        && (filters.getJSONArray(campaignIdKey).getJSONObject(0) != null)
        && (filters.getJSONArray(campaignIdKey).length() == 1)
        && (filters.getJSONArray(campaignIdKey).getJSONObject(0).getString("term") != null)
        && canEditCampaignNonTechProperties(filters.getJSONArray(campaignIdKey).getJSONObject(0).getString("term"));
  }

}
