package com.powerdialer.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.powerdialer.dao.IAdminLogDetailsDao;
import com.powerdialer.dto.AdminLogDetailsFormDTO;
import com.powerdialer.dto.AdminLogDetailsListDTO;
import com.powerdialer.model.AdminLogDetails;
import com.powerdialer.validators.AdminLogDetailsValidator;

@Service
public class AdminLogDetailsService extends GenericService<AdminLogDetails, AdminLogDetailsFormDTO, AdminLogDetailsListDTO> {
  
  @Autowired
  public AdminLogDetailsService(IAdminLogDetailsDao adminLogDetailsDao) {
    super(adminLogDetailsDao, AdminLogDetails.class, AdminLogDetailsFormDTO.class, AdminLogDetailsListDTO.class, new AdminLogDetailsValidator());
  }

}
