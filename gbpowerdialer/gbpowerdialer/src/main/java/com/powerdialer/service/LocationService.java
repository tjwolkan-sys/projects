package com.powerdialer.service;

import com.powerdialer.dao.ILocationDao;
import com.powerdialer.dto.LocationFormDTO;
import com.powerdialer.dto.LocationListDTO;
import com.powerdialer.model.Location;
import com.powerdialer.model.Tenant;
import com.powerdialer.model.User;
import com.powerdialer.util.Constants;
import com.powerdialer.util.ListDetailed;
import com.powerdialer.validators.LocationValidator;
import org.hibernate.Hibernate;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.powerdialer.util.Constants.GRID_FILTER_EXACT;

@Service("locationService")
public class LocationService extends GenericService<Location, LocationFormDTO, LocationListDTO> {

  private ILocationDao locationDao;

  @Autowired
  public LocationService(ILocationDao dao) {
    super(dao, Location.class, LocationFormDTO.class, LocationListDTO.class, new LocationValidator());
    this.locationDao = dao;
  }

  public ListDetailed<LocationListDTO> getLocationWithServersAndCampaigns(JSONObject page, JSONObject sort, JSONObject filters) {
    ListDetailed<LocationListDTO> locationListDetailed = new ListDetailed<>();

    User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

    JSONArray jsonArray = new JSONArray();
    if (authUtil.isUserInRole(Constants.ROLE_TENANT_ADMIN) && !authUtil.isUserInRole(Constants.ROLE_SUPERADMIN)) {
      for (Tenant tenant : user.getTenants()) {
        addFilter(jsonArray, Constants.GRID_FILTER_EXACT, tenant.getId().toString());
      }
    }

    filters.put("tenant.id", jsonArray);
    filters.put("enabled", new JSONArray().put(new JSONObject("{condition:'" + GRID_FILTER_EXACT + "', term:'true'}")));
    filters.put("deleted", new JSONArray().put(new JSONObject("{condition:'" + GRID_FILTER_EXACT + "', term:'false'}")));

    ListDetailed<Location> listDetailed = getDao().getAll(page, sort, filters);
    for (Object object : listDetailed.getData()) {
      Location location = (Location) object;
      Hibernate.initialize(location.getServers());
      Hibernate.initialize(location.getCampaigns());
    }

    locationListDetailed.setData(convertEntitiesToDTO(listDetailed.getData()));
    locationListDetailed.setTotalItems(listDetailed.getTotalItems());

    return locationListDetailed;
  }

  private void addFilter(JSONArray jsonArray, int condition, String term) {
    JSONObject val = new JSONObject();
    val.put("condition", condition);
    val.put("term", term);
    jsonArray.put(val);
  }

  public ListDetailed<LocationListDTO> getLocationsByCampaignId(Long campaignId) {
    List<Location> campaignsLocations = locationDao.getLocationsByCampaignId(campaignId);
    ListDetailed<LocationListDTO> locationListDetailed = new ListDetailed<>();
    locationListDetailed.setData(convertEntitiesToDTO(campaignsLocations));
    locationListDetailed.setTotalItems(campaignsLocations.size());
    return locationListDetailed;
  }

  public List<Location> getLocationsWithEnabledCampaigns() {
    return locationDao.getLocationsWithEnabledCampaigns();
  }

}
