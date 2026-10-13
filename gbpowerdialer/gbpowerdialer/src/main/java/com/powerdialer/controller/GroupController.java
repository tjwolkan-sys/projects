package com.powerdialer.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import com.powerdialer.dto.GroupFormDTO;
import com.powerdialer.dto.GroupListDTO;
import com.powerdialer.model.Group;
import com.powerdialer.service.GroupService;

@Controller
@RequestMapping("/group")
public class GroupController extends GenericController<Group, GroupFormDTO, GroupListDTO> {

  @Autowired
  public GroupController(GroupService groupService) {
    super(groupService);
  }

}
