package com.powerdialer.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.powerdialer.dao.ISortFieldValueDao;
import com.powerdialer.dto.SortFieldValueFormDTO;
import com.powerdialer.dto.SortFieldValueListDTO;
import com.powerdialer.model.SortFieldValue;
import com.powerdialer.validators.SortFieldValueValidator;

@Service
public class SortFieldValueService extends GenericService<SortFieldValue, SortFieldValueFormDTO, SortFieldValueListDTO> {

  @Autowired
  public SortFieldValueService(ISortFieldValueDao dao) {
    super(dao, SortFieldValue.class, SortFieldValueFormDTO.class, SortFieldValueListDTO.class,
          new SortFieldValueValidator());
  }

}
