package com.powerdialer.service;

import com.powerdialer.dao.IAttachedDataItemDao;
import com.powerdialer.model.AttachedDataItem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class AttachedDataItemService{
  private final IAttachedDataItemDao attachedDataItemDao;

  @Autowired
  public AttachedDataItemService(IAttachedDataItemDao attachedDataItemDao) {
    this.attachedDataItemDao = attachedDataItemDao;
  }

  @Transactional(readOnly = false)
  public List<AttachedDataItem> save(List<AttachedDataItem> attachedDataItemList) {
    return attachedDataItemDao.save(attachedDataItemList);
  }
}
