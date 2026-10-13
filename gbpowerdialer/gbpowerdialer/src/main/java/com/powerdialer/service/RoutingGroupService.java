package com.powerdialer.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.powerdialer.dao.IRoutingGroupDao;
import com.powerdialer.dto.RoutingGroupDTO;
import com.powerdialer.model.RoutingGroup;

@Service
@Transactional(readOnly = true)
public class RoutingGroupService extends GenericService<RoutingGroup, RoutingGroupDTO, RoutingGroupDTO> {

  @Autowired
  public RoutingGroupService(IRoutingGroupDao dao) {
    super(dao, RoutingGroup.class, RoutingGroupDTO.class, RoutingGroupDTO.class, null);
  }

  public RoutingGroup getRoutingGroup(Long id) {
    return getDao().get(id);
  }

}
