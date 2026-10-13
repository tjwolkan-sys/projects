package com.powerdialer.dao;

import com.powerdialer.model.AttachedDataItem;

import java.util.List;

public interface IAttachedDataItemDao extends IGenericDao<AttachedDataItem>{
  List<AttachedDataItem> save(List<AttachedDataItem> attachedDataItemList);
}
