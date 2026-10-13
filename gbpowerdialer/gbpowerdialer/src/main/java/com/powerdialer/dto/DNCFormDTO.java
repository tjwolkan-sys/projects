package com.powerdialer.dto;

import java.util.List;

public class DNCFormDTO extends DNCListDTO {

  private List<DNCPhoneNumberListDTO> phoneNumbers;

  public List<DNCPhoneNumberListDTO> getPhoneNumbers() {
    return phoneNumbers;
  }

  public void setPhoneNumbers(List<DNCPhoneNumberListDTO> phoneNumbers) {
    this.phoneNumbers = phoneNumbers;
  }

}
