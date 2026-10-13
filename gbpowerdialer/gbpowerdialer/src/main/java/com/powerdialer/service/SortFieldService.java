package com.powerdialer.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.powerdialer.dao.ISortFieldDao;
import com.powerdialer.dto.SortFieldFormDTO;
import com.powerdialer.dto.SortFieldListDTO;
import com.powerdialer.model.SortField;
import com.powerdialer.validators.SortFieldValidator;

@Service
public class SortFieldService extends GenericService<SortField, SortFieldFormDTO, SortFieldListDTO> {

  @Autowired
  public SortFieldService(ISortFieldDao dao) {
    super(dao, SortField.class, SortFieldFormDTO.class, SortFieldListDTO.class, new SortFieldValidator());
  }

}
