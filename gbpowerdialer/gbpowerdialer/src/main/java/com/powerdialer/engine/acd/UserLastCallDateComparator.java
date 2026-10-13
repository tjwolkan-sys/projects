package com.powerdialer.engine.acd;

import com.powerdialer.model.User;

import java.util.Comparator;
import java.util.Date;

public class UserLastCallDateComparator implements Comparator<User> {

  private final ACDEngine acdEngine;

  public UserLastCallDateComparator(ACDEngine acdEngine) {
    this.acdEngine = acdEngine;
  }

  @Override
  public int compare(User o1, User o2) {
    Date lastCallEndDate1 = acdEngine.getUserLastCallEndDate(o1);
    Date lastCallEndDate2 = acdEngine.getUserLastCallEndDate(o2);
    if (lastCallEndDate1 == null && lastCallEndDate2 == null) {
      return o1.getId().compareTo(o2.getId());
    } else if (lastCallEndDate1 == null) {
      return -1;
    } else if (lastCallEndDate2 == null) {
      return 1;
    } else {
      return lastCallEndDate1.compareTo(lastCallEndDate2);
    }
  }

}
