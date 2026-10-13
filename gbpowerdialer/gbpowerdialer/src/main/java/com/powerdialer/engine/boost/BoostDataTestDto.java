package com.powerdialer.engine.boost;

public class BoostDataTestDto {

  private final PeriodicBoostData currentData;
  private final PeriodicBoostData previousData;

  public BoostDataTestDto(CampaignBoostData dynamicBoostData) {
    this.currentData = dynamicBoostData.getCurrentData();
    this.previousData = dynamicBoostData.getPreviousData();
  }

  public PeriodicBoostData getCurrentData() {
    return currentData;
  }

  public PeriodicBoostData getPreviousData() {
    return previousData;
  }

}
