package com.powerdialer.dto;

import java.util.List;

import org.dozer.Mapping;

import com.powerdialer.util.Constants;

public class ScheduleAutomationListDTO extends BaseEntityDTO {

  private List<GroupFormDTO> groups;
  private Constants.SCHEDULE_ACTION_TYPE actionType;
  private String formula;
  private int threshold;
  private Long campaign;
  private Constants.COMPARISON_STRATEGY_TYPE comparisonType = Constants.COMPARISON_STRATEGY_TYPE.ONE_BY_ONE;


  public List<GroupFormDTO> getGroups() {
    return groups;
  }

  public void setGroups(List<GroupFormDTO> groups) {
    this.groups = groups;
  }

  public Constants.SCHEDULE_ACTION_TYPE getActionType() {
    return actionType;
  }

  public void setActionType(Constants.SCHEDULE_ACTION_TYPE actionType) {
    this.actionType = actionType;
  }

  public String getFormula() {
    return formula;
  }

  public void setFormula(String formula) {
    this.formula = formula;
  }

  public int getThreshold() {
    return threshold;
  }

  public void setThreshold(int threshold) {
    this.threshold = threshold;
  }

  @Mapping("campaign.id")
  public Long getCampaign() {
    return campaign;
  }

  public void setCampaign(Long campaign) {
    this.campaign = campaign;
  }

  public Constants.COMPARISON_STRATEGY_TYPE getComparisonType() { return comparisonType; }

  public void setComparisonType(Constants.COMPARISON_STRATEGY_TYPE comparisonType) { this.comparisonType = comparisonType; }
}
