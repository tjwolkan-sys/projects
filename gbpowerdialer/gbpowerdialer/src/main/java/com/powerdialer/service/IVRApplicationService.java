package com.powerdialer.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.powerdialer.dao.impl.IVRApplicationDao;
import com.powerdialer.dto.IVRApplicationFormDTO;
import com.powerdialer.dto.IVRApplicationListDTO;
import com.powerdialer.model.IVRApplication;
import com.powerdialer.validators.IVRApplicationValidator;

@Service("ivrApplicationService")
@Transactional(readOnly = true)
public class IVRApplicationService extends GenericService<IVRApplication, IVRApplicationFormDTO, IVRApplicationListDTO> {

  @Autowired
  public IVRApplicationService(IVRApplicationDao dao) {
    super(dao, IVRApplication.class, IVRApplicationFormDTO.class, IVRApplicationListDTO.class, new IVRApplicationValidator());
  }

}
