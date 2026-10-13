package com.powerdialer.util;

import static java.util.Collections.emptyList;
import java.util.List;

public class ListDetailed<T> {

  private List<T> data;
  private Integer totalItems;

  public List<T> getData() {
    return data;
  }

  public void setData(List<T> data) {
    this.data = data;
  }

  public Integer getTotalItems() {
    return totalItems;
  }

  public void setTotalItems(Integer totalItems) {
    this.totalItems = totalItems;
  }

  public static <T> ListDetailed<T> empty() {
    ListDetailed<T> list = new ListDetailed<>();
    list.setData(emptyList());
    list.setTotalItems(0);
    return list;
  }

}
