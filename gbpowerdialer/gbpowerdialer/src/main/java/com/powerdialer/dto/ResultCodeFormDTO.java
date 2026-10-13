package com.powerdialer.dto;

import java.util.Set;

import com.powerdialer.model.Group;

public class ResultCodeFormDTO extends ResultCodeListDTO {

  private Set<Group> groups;

  public Set<Group> getGroups() {
    return groups;
  }

  public void setGroups(Set<Group> groups) {
    this.groups = groups;
  }

}
