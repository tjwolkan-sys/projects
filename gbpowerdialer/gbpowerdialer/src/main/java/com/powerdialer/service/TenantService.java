package com.powerdialer.service;

import com.powerdialer.dao.ITenantDao;
import com.powerdialer.dto.TenantFormDTO;
import com.powerdialer.dto.TenantListDTO;
import com.powerdialer.model.Tenant;
import com.powerdialer.model.User;
import com.powerdialer.util.Constants;
import com.powerdialer.util.ListDetailed;
import com.powerdialer.validators.TenantValidator;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class TenantService extends GenericService<Tenant, TenantFormDTO, TenantListDTO> {

  @Autowired
  public TenantService(ITenantDao dao) {
    super(dao,Tenant.class, TenantFormDTO.class, TenantListDTO.class, new TenantValidator());
  }

  @Override
  @Transactional(readOnly = true)
  public ListDetailed<TenantListDTO> getAll(JSONObject page, JSONObject sort, JSONObject filters) {
    filters = addTenantFilters(filters);

    ListDetailed<Tenant> srcList = super.getDao().getAll(page, sort, filters);

    ListDetailed<TenantListDTO> resultList = new ListDetailed<>();
    List<TenantListDTO> resultListData = new ArrayList<>();

    for (Tenant src : srcList.getData()) {
      TenantListDTO dest = mapper.map(src, TenantListDTO.class);
      resultListData.add(dest);
    }

    resultList.setData(resultListData);
    resultList.setTotalItems(srcList.getTotalItems());

    return resultList;
  }

  private JSONObject addTenantFilters(JSONObject filters) {
    if (filters == null) {
      filters = new JSONObject();
    }

    User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

    JSONArray jsonArray = new JSONArray();

    if (!authUtil.isUserInRole(Constants.ROLE_SUPERADMIN)) {
      if (authUtil.isUserInRole(Constants.ROLE_TENANT_ADMIN)) {
        for (Tenant tenant : user.getTenants()) {
          addFilter(jsonArray, Constants.GRID_FILTER_EXACT, tenant.getId().toString());
        }
      } else {
        addFilter(jsonArray, Constants.GRID_FILTER_EXACT, user.getTenant().getId().toString());
      }
    }

    filters.put("id", jsonArray);

    return filters;
  }

  private void addFilter(JSONArray jsonArray, int condition, String term) {
    JSONObject val = new JSONObject();
    val.put("condition", condition);
    val.put("term", term);
    jsonArray.put(val);
  }

  public Tenant getTenantByUser(User user) {
    if (user != null && user.getTenant() != null) {
      return getTenantById(user.getTenant().getId());
    }
    return null;
  }

  public Tenant getTenantById(Long tenantId) {
    return ((ITenantDao) getDao()).getTenantById(tenantId);
  }
}