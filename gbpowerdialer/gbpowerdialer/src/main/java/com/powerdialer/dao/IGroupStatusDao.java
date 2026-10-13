package com.powerdialer.dao;

import java.util.List;

import com.powerdialer.model.GroupPropertyStatus;
import com.powerdialer.model.GroupStatus;

public interface IGroupStatusDao extends IGenericDao<GroupStatus> {

  List<GroupPropertyStatus> getStatusesByGroupId(Long groupId, Boolean enabled, Boolean deleted);

}
