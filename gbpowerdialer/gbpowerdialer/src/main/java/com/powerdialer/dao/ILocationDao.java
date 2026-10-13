package com.powerdialer.dao;

import java.util.List;

import com.powerdialer.model.Location;

public interface ILocationDao extends IGenericDao<Location> {

  List<Location> getLocationsByCampaignId(Long campaignId);

  List<Location> getLocationsWithEnabledCampaigns();
}
