package com.powerdialer.service;

import com.powerdialer.model.Tenant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.powerdialer.dao.IAdminLogDao;
import com.powerdialer.dto.AdminLogFormDTO;
import com.powerdialer.dto.AdminLogListDTO;
import com.powerdialer.exceptions.DuplicatedNameException;
import com.powerdialer.model.AdminLog;
import com.powerdialer.model.AdminLogDetails;
import com.powerdialer.model.User;
import com.powerdialer.validators.AdminLogValidator;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminLogService extends GenericService<AdminLog, AdminLogFormDTO, AdminLogListDTO> {

  @Autowired
  public AdminLogService(IAdminLogDao adminLogDao) {
    super(adminLogDao, AdminLog.class, AdminLogFormDTO.class, AdminLogListDTO.class, new AdminLogValidator());
  }

  @Override
  @Transactional
  public AdminLogFormDTO save(AdminLogFormDTO item) throws DuplicatedNameException {
    User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    AdminLog entity = mapper.map(item, AdminLog.class);

    entity.setUser(user);
    if (item.getTenant() != null) {
      Tenant tenant = mapper.map(item.getTenant(), Tenant.class);
      entity.setTenant(tenant);
    } else {
      entity.setTenant(user.getTenant());
    }
    if (entity.getAdminLogDetailsList() != null) {
      for (AdminLogDetails ald : entity.getAdminLogDetailsList()) {
        ald.setAdminLog(entity);
      }
    }
    entity = getDao().save(entity);
    return mapper.map(entity, AdminLogFormDTO.class);
  }

}
