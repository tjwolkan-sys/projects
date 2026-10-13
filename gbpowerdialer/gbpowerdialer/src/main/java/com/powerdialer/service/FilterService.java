package com.powerdialer.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.powerdialer.dao.IFilterDao;
import com.powerdialer.dto.FilterFormDTO;
import com.powerdialer.dto.FilterListDTO;
import com.powerdialer.model.Filter;
import com.powerdialer.validators.FilterValidator;

@Service
public class FilterService extends GenericService<Filter, FilterFormDTO, FilterListDTO> {

  @Autowired
  public FilterService(IFilterDao dao) {
    super(dao, Filter.class, FilterFormDTO.class, FilterListDTO.class, new FilterValidator());
  }

}
