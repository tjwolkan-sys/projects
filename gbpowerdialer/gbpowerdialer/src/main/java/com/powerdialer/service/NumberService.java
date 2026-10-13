package com.powerdialer.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.powerdialer.dao.impl.NumberDao;
import com.powerdialer.dto.NumberFormDTO;
import com.powerdialer.dto.NumberListDTO;
import com.powerdialer.model.Number;
import com.powerdialer.validators.NumberValidator;

@Service("numberService")
@Transactional(readOnly = true)
public class NumberService extends GenericService<Number, NumberFormDTO, NumberListDTO> {

  @Autowired
  public NumberService(NumberDao dao) {
    super(dao, Number.class, NumberFormDTO.class, NumberListDTO.class, new NumberValidator());
  }

}
