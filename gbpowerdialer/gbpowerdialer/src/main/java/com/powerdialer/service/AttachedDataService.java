package com.powerdialer.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.powerdialer.dao.IAttachedDataDao;
import com.powerdialer.dto.AttachedDataFormDTO;
import com.powerdialer.dto.AttachedDataListDTO;
import com.powerdialer.model.AttachedData;
import com.powerdialer.validators.AttachedDataValidator;

@Service
public class AttachedDataService extends GenericService<AttachedData, AttachedDataFormDTO, AttachedDataListDTO> {

  @Autowired
  public AttachedDataService(IAttachedDataDao dao) {
    super(dao, AttachedData.class, AttachedDataFormDTO.class, AttachedDataListDTO.class, new AttachedDataValidator());
  }

}
