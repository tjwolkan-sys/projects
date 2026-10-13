package com.powerdialer.dao.impl;

import com.powerdialer.dao.IAttachedDataItemDao;
import com.powerdialer.model.AttachedDataItem;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class AttachedDataItemDao extends GenericDao<AttachedDataItem> implements IAttachedDataItemDao {
  public AttachedDataItemDao() {
    super(AttachedDataItem.class);
  }

  @Override
  public List<AttachedDataItem> save(List<AttachedDataItem> attachedDataItemList) {
    for (AttachedDataItem attachedDataItem : attachedDataItemList) {
      getSessionFactory().getCurrentSession().merge(attachedDataItem);
    }
    return attachedDataItemList;
  }
}
