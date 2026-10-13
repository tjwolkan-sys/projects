package com.powerdialer.dto;

import java.util.List;

import org.dozer.Mapping;

public class SortFieldListDTO extends BaseEntityDTO {

  private String name;
  private int priority;
  private Long campaign;
  private List<SortFieldValueFormDTO> values;
  private Integer sortDirection;
  private Integer sortMethod;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public int getPriority() {
    return priority;
  }

  public void setPriority(int priority) {
    this.priority = priority;
  }

  @Mapping("campaign.id")
  public Long getCampaign() {
    return campaign;
  }

  public void setCampaign(Long campaign) {
    this.campaign = campaign;
  }

  public List<SortFieldValueFormDTO> getValues() {
    return values;
  }

  public void setValues(List<SortFieldValueFormDTO> values) {
    this.values = values;
  }

  public Integer getSortDirection() {
    return sortDirection;
  }

  public void setSortDirection(Integer sortDirection) {
    this.sortDirection = sortDirection;
  }

  public Integer getSortMethod() {
    return sortMethod;
  }

  public void setSortMethod(Integer sortMethod) {
    this.sortMethod = sortMethod;
  }

}
