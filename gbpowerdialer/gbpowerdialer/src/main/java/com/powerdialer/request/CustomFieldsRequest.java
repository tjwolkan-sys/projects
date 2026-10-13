package com.powerdialer.request;

import com.powerdialer.dto.FieldDTO;

import java.util.List;

public class CustomFieldsRequest extends DbTableRequest {

  private List<FieldDTO> customFields;

  public List<FieldDTO> getCustomFields() {
    return customFields;
  }

  public void setCustomFields(List<FieldDTO> customFields) {
    this.customFields = customFields;
  }

}
