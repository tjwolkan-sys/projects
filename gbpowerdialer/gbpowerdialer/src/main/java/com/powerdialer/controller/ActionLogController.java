package com.powerdialer.controller;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.dozer.Mapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import com.powerdialer.dto.ActionLogDTO;
import com.powerdialer.model.ActionLog;
import com.powerdialer.model.User;
import com.powerdialer.security.PermissionUtil;
import com.powerdialer.service.ActionLogService;
import com.powerdialer.util.Constants;

@Controller
@RequestMapping("/actionLog")
public class ActionLogController extends GenericController<ActionLog, ActionLogDTO, ActionLogDTO> {

  @Autowired
  private Mapper mapper;

  @Autowired
  private PermissionUtil permissionUtil;

  @Autowired
  public ActionLogController(ActionLogService actionLogService) {
    super(actionLogService);
  }

  @RequestMapping(value = "/cache", method = RequestMethod.GET, produces = "application/json")
  public @ResponseBody Collection<ActionLogDTO> getCachedActionLogs() {
    List<ActionLogDTO> actionLogDTOs = new ArrayList<>();
    Collection<ActionLog> actionLogEntities;
    User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

    actionLogEntities = permissionUtil.getAuthUtil().isUserInRole(Constants.ROLE_SUPERADMIN) ? ((ActionLogService) service).getCachedActionLogs()
                                                                                             : ((ActionLogService) service).getCacheQueueByTenantId(user.getTenant().getId());

    for (ActionLog src : actionLogEntities) {
      ActionLogDTO dest = mapper.map(src, ActionLogDTO.class);
      actionLogDTOs.add(dest);
    }

    return permissionUtil.restrictActionLog(actionLogDTOs);
  }

}
