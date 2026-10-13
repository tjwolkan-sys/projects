package com.powerdialer.service;

import static com.powerdialer.util.Constants.GRID_FILTER_IS_NOT_NULL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.powerdialer.dao.IDNCPhoneNumberDao;
import com.powerdialer.exceptions.DncExpireDateValidationException;
import com.powerdialer.exceptions.DncPhoneExpiredDateBeforeCreateDateException;
import com.powerdialer.model.Campaign;
import org.hibernate.Hibernate;

import org.apache.commons.beanutils.ConvertUtils;
import org.hibernate.SessionFactory;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.powerdialer.dao.IDNCDao;
import com.powerdialer.dao.ITenantDao;
import com.powerdialer.dao.impl.DNCDao;
import com.powerdialer.dto.DNCFormDTO;
import com.powerdialer.dto.DNCListDTO;
import com.powerdialer.exceptions.DuplicatedNameException;
import com.powerdialer.model.DNC;
import com.powerdialer.model.DNCPhoneNumber;
import com.powerdialer.model.User;
import com.powerdialer.security.GrantedAuthorityWithValue;
import com.powerdialer.security.PermissionUtil;
import com.powerdialer.util.Constants;
import com.powerdialer.util.ListDetailed;
import com.powerdialer.validators.DNCValidator;

@Service
public class DNCService extends GenericService<DNC, DNCFormDTO, DNCListDTO> {

  private ITenantDao tenantDao;
  private IDNCDao dncDao;
  private IDNCPhoneNumberDao dncPhoneNumberDao;
  private PermissionUtil permissionUtil;

  @Autowired
  private SessionFactory sessionFactory;

  @Autowired
  public DNCService(IDNCDao dao, IDNCPhoneNumberDao dncPhoneNumberDao, ITenantDao tenantDao, PermissionUtil permissionUtil) {
    super(dao, DNC.class, DNCFormDTO.class, DNCListDTO.class, new DNCValidator());
    ((DNCValidator) getValidator()).setDncService(this);
    this.tenantDao = tenantDao;
    this.dncDao = dao;
    this.permissionUtil = permissionUtil;
    this.dncPhoneNumberDao = dncPhoneNumberDao;
  }

  @Transactional(readOnly = false)
  public DNCFormDTO save(final DNCFormDTO item) throws DuplicatedNameException {
    DNC entity = mapper.map(item, DNC.class);
    if (getValidator().validate(entity).getResult()) {
      User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
      //setCreateUpdateFields(entity.getPhoneNumbers(), user);    createDate is already populated on the FE
      if (entity.getPhoneNumbers() != null) {
        entity.getPhoneNumbers().forEach(p -> {
          if (p.getId() == null) {
            p.setCreateUser(user);
          } else {
            p.setUpdateUser(user);
            p.setUpdateDate(new Date());
          }
        });
      }

      setDNC(entity, entity.getPhoneNumbers());

      entity.setCreateDate(new Date());
      entity.setCreateUser(user);

      if (!authUtil.isUserInRole(Constants.ROLE_SUPERADMIN)) {
        if (!userHasTenant(user, entity.getTenant().getId())) {
          entity.setTenant(user.getTenant());
        }
      }

      List<DNCPhoneNumber> persistedPhoneNumbers = getPersistedPhoneNumbers(entity.getId());
      validatePhoneExpireDatesInMemory(entity, persistedPhoneNumbers);
      validateListExpireDateInMemory(entity, persistedPhoneNumbers);
      entity = getDao().save(entity);
      return mapper.map(entity, DNCFormDTO.class);
    } else {
      return item;
    }
  }

  private void setDNC(DNC dnc, List<DNCPhoneNumber> phoneNumbers) {
    if (phoneNumbers == null)
      return;

    for (DNCPhoneNumber phoneNumber : phoneNumbers) {
      phoneNumber.setDncList(dnc);
    }
  }

  @Transactional(readOnly = false)
  public DNCFormDTO merge(final DNCFormDTO item) throws DuplicatedNameException {
    DNC entity = mapper.map(item, DNC.class);

    // Campaign from db because the circular reference
    if (entity.getCampaign() != null && entity.getCampaign().getId() != null) {
      Campaign managedCampaign = sessionFactory.getCurrentSession().load(Campaign.class, entity.getCampaign().getId());
      entity.setCampaign(managedCampaign);
    }

    if (getValidator().validate(entity).getResult()) {
      User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
      //setCreateUpdateFields(entity.getPhoneNumbers(), user);    createDate is already populated on the FE
      if (entity.getPhoneNumbers() != null) {
        entity.getPhoneNumbers().forEach(p -> {
          if (p.getId() == null) {
            p.setCreateUser(user);
          } else {
            p.setUpdateUser(user);
            p.setUpdateDate(new Date());
          }
        });
      }

      setDNC(entity, entity.getPhoneNumbers());

      entity.setUpdateDate(new Date());
      entity.setUpdateUser(user);

      if (!authUtil.isUserInRole(Constants.ROLE_SUPERADMIN)) {
        if (!userHasTenant(user, entity.getTenant().getId())) {
          entity.setTenant(user.getTenant());
        }
      }

      List<DNCPhoneNumber> persistedPhoneNumbers = getPersistedPhoneNumbers(entity.getId());
      validatePhoneExpireDatesInMemory(entity, persistedPhoneNumbers);
      validateListExpireDateInMemory(entity, persistedPhoneNumbers);
      entity = getDao().merge(entity);
      return mapper.map(entity, DNCFormDTO.class);
    } else {
      return item;
    }
  }

  private boolean userHasTenant(User user, Long tenantId) {
    return user.getTenants().stream()
        .anyMatch(tenant -> tenant.getId().equals(tenantId));
  }

  /**
   * Get {@link DNC} with initialized {@link DNCPhoneNumber} list
   */
  public DNC getWithInitializedPhoneNumbers(Long id) {
    return ((IDNCDao) getDao()).getWithInitializedPhoneNumbers(id);
  }

  /**
   * Load DNCs for specified campaign id
   */
  public List<DNC> getDncForCampaign(Long campaignId) {
    List<DNC> dncForCampaign = ((DNCDao) getDao()).getDNCForCampaign(campaignId, true);
    for (DNC dnc : dncForCampaign) {
      Hibernate.initialize(dnc.getTenant());
    }
    return dncForCampaign;
  }

  public List<DNC> getGlobalDnc(boolean enabled) {
    return ((IDNCDao) getDao()).getGlobalDNC(enabled);
  }

  public DNC getDNCByDncPhoneNumberIdWithInitializedTenant(Long dncPhoneNumberId) {
    DNC dncByDNCPhoneNumberId = ((IDNCDao) getDao()).getDNCByDNCPhoneNumberId(dncPhoneNumberId);
    Hibernate.initialize(dncByDNCPhoneNumberId.getTenant());
    return dncByDNCPhoneNumberId;
  }

  public DNC getDefaultDNCByCampaignId(Long campaignId) {
    return ((IDNCDao) getDao()).getDefaultDNCByCampaignId(campaignId);
  }

  public DNC getDefaultDNCByCampaignName(String campaignName) {
    return ((IDNCDao) getDao()).getDefaultDNCByCampaignName(campaignName);
  }

  public long getTenantDNCCount(Long tenantId, boolean isCountGlobal, boolean includeDisabled, Long... excludedIds) {
    return ((IDNCDao) getDao()).getTenantDNCCount(tenantId, isCountGlobal, includeDisabled, excludedIds);
  }

  public DNC getTenantDNC(Long tenantId, boolean isCountGlobal, boolean includeDisabled) {
    return ((IDNCDao) getDao()).getTenantDNC(tenantId, isCountGlobal, includeDisabled);
  }

  @Override
  public ListDetailed<DNCListDTO> getAll(JSONObject page, JSONObject sort, JSONObject filters) {
    if (authUtil.isUserInRole(Constants.ROLE_SUPERADMIN) || authUtil.isUserInRole(Constants.ROLE_TENANT_ADMIN) || authUtil.isUserInRole(Constants.ROLE_SUPERVISOR))
      return super.getAll(page, sort, filters);
    return getDefaultDNCListsForPermittedCampaigns(page, sort, filters);
  }

  public ListDetailed<DNCListDTO> getDefaultDNCListsForPermittedCampaigns(JSONObject page, JSONObject sort, JSONObject filters) {
    filters = addCustomFilters(filters);
    addFilter(filters, "campaign.id", GRID_FILTER_IS_NOT_NULL, null);
    boolean all = hasAllPermission();
    Long[] campaignIds = all ? new Long[0] : getCampaignsIdsPermittedForUser();
    ListDetailed<DNC> srcList = ((IDNCDao) getDao()).getDNCListsByCampaignsIds(page, sort, filters, campaignIds, all);
    if (srcList.getData() == null) {
      return ListDetailed.empty();
    }
    ListDetailed<DNCListDTO> resultList = new ListDetailed<>();
    List<DNCListDTO> resultListData = new ArrayList<>();
    for (DNC src : srcList.getData()) {
      DNCListDTO dest = mapper.map(src, DNCListDTO.class);
      resultListData.add(dest);
    }
    resultList.setData(resultListData);
    resultList.setTotalItems(srcList.getTotalItems());
    return  resultList;
  }

  private boolean hasAllPermission() {
    Collection<? extends GrantedAuthority> authorities = SecurityContextHolder.getContext().getAuthentication().getAuthorities();
    for (GrantedAuthority authority : authorities) {
      if (authority instanceof GrantedAuthorityWithValue && authority.getAuthority().equals(Constants.PERMISSION_EDIT_CAMPAIGN_NON_TECH)) {
        String value = ((GrantedAuthorityWithValue) authority).getValue();
        if (value.contains(Constants.PERMISSION_VALUE_ALL)) {
          return true;
        }
      }
    }
    return false;
  }

  private Long[] getCampaignsIdsPermittedForUser() {
    ArrayList<Long> campaignIds = new ArrayList<>();
    for (GrantedAuthority authority : SecurityContextHolder.getContext().getAuthentication().getAuthorities()) {
      if (authority instanceof GrantedAuthorityWithValue && authority.getAuthority().equals(Constants.PERMISSION_EDIT_CAMPAIGN_NON_TECH)) {
        campaignIds.add(Long.parseLong(((GrantedAuthorityWithValue) authority).getValue()));
      }
    }
    Long[] resultArray = new Long[campaignIds.size()];
    return campaignIds.toArray(resultArray);
  }

  public boolean isDNCDefaultForPermittedCampaign(String dncId) {
    DNC dncToInteract =  dncDao.get(Long.parseLong(dncId));
    if (dncToInteract != null && dncToInteract.getCampaign() != null) {
      return permissionUtil.canEditCampaignNonTechProperties(String.valueOf(dncToInteract.getCampaign().getId()));
    }
    return false;
  }

  public boolean isDNCsDefaultForPermittedCampaigns(String dncIds) {
    List<Long> idList = Arrays.asList((Long[]) ConvertUtils.convert(dncIds.split(","), Long[].class));
    Long[] resultArray = new Long[idList.size()];
    List<DNC> dncsToEnable =  dncDao.getDNCListsByIds(idList.toArray(resultArray));
    for (DNC dnc: dncsToEnable)
    if (dnc == null || dnc.getCampaign() == null || !permissionUtil.canEditCampaignNonTechProperties(String.valueOf(dnc.getCampaign().getId()))) {
      return false;
    }
    return true;
  }

  public ListDetailed<DNCListDTO> getDNCsByCampaignId(Long campaignId) {
    List<DNC> campaignsLocations = dncDao.getDNCsByCampaignId(campaignId);
    ListDetailed<DNCListDTO> locationListDetailed = new ListDetailed<>();
    List<DNCListDTO> result = new ArrayList<>();
    for (DNC dnc : campaignsLocations) {
      DNCListDTO dto = mapper.map(dnc, DNCListDTO.class);
      dto.setCampaignDefault(isCampaignDefaultDnc(dnc, campaignId));
      result.add(dto);
    }
    locationListDetailed.setData(result);
    locationListDetailed.setTotalItems(campaignsLocations.size());
    return locationListDetailed;
  }

  private boolean isCampaignDefaultDnc(DNC dnc, Long campaignId) {
    return dnc != null
        && dnc.getCampaign() != null
        && Objects.equals(dnc.getCampaign().getId(), campaignId);
  }

  /**
   * Ensures that the DNC list expire date is not earlier than any related phone number expire date.
   */
  private void validateListExpireDateInMemory(DNC dnc, List<DNCPhoneNumber> persistedPhoneNumbers) {
    Date listExpiredDate = dnc.getExpireDate();

    if (listExpiredDate == null) {
      return;
    }

    Date maxPnExpiredDate = null;

    for (DNCPhoneNumber phoneNumber : mergePhoneNumbersForValidation(dnc.getPhoneNumbers(), persistedPhoneNumbers)) {
      Date expiredDate = phoneNumber.getExpiredDate();
      if (expiredDate != null && (maxPnExpiredDate == null || expiredDate.after(maxPnExpiredDate))) {
        maxPnExpiredDate = expiredDate;
      }
    }

    if (maxPnExpiredDate != null && maxPnExpiredDate.after(listExpiredDate)) {
      throw new DncExpireDateValidationException();
    }
  }

  private void validatePhoneExpireDatesInMemory(DNC dnc, List<DNCPhoneNumber> persistedPhoneNumbers) {
    if (dnc.getPhoneNumbers() == null || dnc.getPhoneNumbers().isEmpty()) {
      return;
    }

    Map<Long, DNCPhoneNumber> persistedPhoneNumbersById = mapPhoneNumbersById(persistedPhoneNumbers);

    for (DNCPhoneNumber phoneNumber : dnc.getPhoneNumbers()) {
      if (phoneNumber.isDeleted() || phoneNumber.getExpiredDate() == null) {
        continue;
      }

      Date effectiveCreateDate = resolveEffectiveCreateDate(phoneNumber, persistedPhoneNumbersById);
      if (effectiveCreateDate != null && phoneNumber.getExpiredDate().before(effectiveCreateDate)) {
        throw new DncPhoneExpiredDateBeforeCreateDateException();
      }
    }
  }

  private Date resolveEffectiveCreateDate(DNCPhoneNumber phoneNumber, Map<Long, DNCPhoneNumber> persistedPhoneNumbersById) {
    if (phoneNumber.getId() != null) {
      DNCPhoneNumber persistedPhoneNumber = persistedPhoneNumbersById.get(phoneNumber.getId());
      if (persistedPhoneNumber != null && persistedPhoneNumber.getCreateDate() != null) {
        return persistedPhoneNumber.getCreateDate();
      }
    }

    if (phoneNumber.getCreateDate() != null) {
      return phoneNumber.getCreateDate();
    }

    return new Date();
  }

  private List<DNCPhoneNumber> mergePhoneNumbersForValidation(List<DNCPhoneNumber> requestPhoneNumbers, List<DNCPhoneNumber> persistedPhoneNumbers) {
    List<DNCPhoneNumber> mergedPhoneNumbers = new ArrayList<>();
    Map<Long, DNCPhoneNumber> requestPhoneNumbersById = new HashMap<>();

    if (requestPhoneNumbers != null) {
      for (DNCPhoneNumber requestPhoneNumber : requestPhoneNumbers) {
        if (requestPhoneNumber.getId() == null) {
          if (!requestPhoneNumber.isDeleted()) {
            mergedPhoneNumbers.add(requestPhoneNumber);
          }
          continue;
        }
        requestPhoneNumbersById.put(requestPhoneNumber.getId(), requestPhoneNumber);
      }
    }

    for (DNCPhoneNumber persistedPhoneNumber : persistedPhoneNumbers) {
      DNCPhoneNumber requestPhoneNumber = requestPhoneNumbersById.remove(persistedPhoneNumber.getId());
      if (requestPhoneNumber == null) {
        mergedPhoneNumbers.add(persistedPhoneNumber);
        continue;
      }

      if (!requestPhoneNumber.isDeleted()) {
        mergedPhoneNumbers.add(requestPhoneNumber);
      }
    }

    for (DNCPhoneNumber requestPhoneNumber : requestPhoneNumbersById.values()) {
      if (!requestPhoneNumber.isDeleted()) {
        mergedPhoneNumbers.add(requestPhoneNumber);
      }
    }

    return mergedPhoneNumbers;
  }

  private Map<Long, DNCPhoneNumber> mapPhoneNumbersById(List<DNCPhoneNumber> phoneNumbers) {
    Map<Long, DNCPhoneNumber> phoneNumbersById = new HashMap<>();
    for (DNCPhoneNumber phoneNumber : phoneNumbers) {
      if (phoneNumber.getId() != null) {
        phoneNumbersById.put(phoneNumber.getId(), phoneNumber);
      }
    }
    return phoneNumbersById;
  }

  private List<DNCPhoneNumber> getPersistedPhoneNumbers(Long dncId) {
    if (dncId == null) {
      return Collections.emptyList();
    }

    List<DNCPhoneNumber> persistedPhoneNumbers = dncPhoneNumberDao.findByDncListId(dncId);
    return persistedPhoneNumbers != null ? persistedPhoneNumbers : Collections.emptyList();
  }
}
