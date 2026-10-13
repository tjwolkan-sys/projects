package com.powerdialer.dto;

public class UserDTOExtended extends UserDTO {

  private GroupDTO group;

  public GroupDTO getGroup() {
    return group;
  }

  public void setGroup(GroupDTO group) {
    this.group = group;
  }

  @Override
  public String toString() {
    return "User [id=" + getId() + ", username=" + getUsername() + ", group=" + group + ", status=" + getStatus() + "]";
  }

}
