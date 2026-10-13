package com.powerdialer.service;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang.ArrayUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.powerdialer.dao.IGroupStatusDao;
import com.powerdialer.dto.GroupStatusFormDTO;
import com.powerdialer.dto.GroupStatusListDTO;
import com.powerdialer.model.GroupPropertyStatus;
import com.powerdialer.model.GroupStatus;
import com.powerdialer.validators.GroupStatusValidator;

@Service("statusService")
@Transactional(readOnly = true)
public class StatusService extends GenericService<GroupStatus, GroupStatusFormDTO, GroupStatusListDTO> {

  @Autowired
  public StatusService(IGroupStatusDao dao) {
    super(dao, GroupStatus.class, GroupStatusFormDTO.class, GroupStatusListDTO.class, new GroupStatusValidator());
  }

  public Set<GroupStatus> getCommonResultStatuses(Long[] groupIds) {
    if (ArrayUtils.isEmpty(groupIds)) {
      return Collections.emptySet();
    }
    Set<GroupStatus> result = null;
    for (Long id : groupIds) {
      Set<GroupStatus> groupStatuses = getActiveStatusesByGroupId(id).stream()
          .map(GroupPropertyStatus::getStatus)
          .collect(Collectors.toSet());
      if (result == null) {
        result = groupStatuses;
      } else {
        result.retainAll(groupStatuses);
      }
    }
    return result;
  }

  private List<GroupPropertyStatus> getActiveStatusesByGroupId(Long groupId) {
    return ((IGroupStatusDao) getDao()).getStatusesByGroupId(groupId, true, false);
  }

}

