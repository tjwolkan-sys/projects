package com.powerdialer.dto;

import java.util.List;

public class AdminLogFormDTO extends AdminLogListDTO {

  private List<AdminLogDetailsListDTO> adminLogDetailsList;

  public List<AdminLogDetailsListDTO> getAdminLogDetailsList() {
    return adminLogDetailsList;
  }

  public void setAdminLogDetailsList(List<AdminLogDetailsListDTO> adminLogDetailsList) {
    this.adminLogDetailsList = adminLogDetailsList;
  }

}
