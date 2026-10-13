package com.powerdialer.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.powerdialer.dao.IGroupDao;
import com.powerdialer.dto.GroupFormDTO;
import com.powerdialer.dto.GroupListDTO;
import com.powerdialer.model.Group;
import com.powerdialer.validators.GroupValidator;

@Service("groupService")
@Transactional(readOnly = true)
public class GroupService extends GenericService<Group, GroupFormDTO, GroupListDTO> {

  @Autowired
  public GroupService(IGroupDao dao) {
    super(dao, Group.class, GroupFormDTO.class, GroupListDTO.class, new GroupValidator());
  }

}
