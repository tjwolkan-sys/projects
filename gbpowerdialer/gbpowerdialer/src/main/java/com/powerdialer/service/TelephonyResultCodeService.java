package com.powerdialer.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.powerdialer.dao.ITelephonyResultCodeDao;
import com.powerdialer.dto.TelephonyResultCodeFormDTO;
import com.powerdialer.dto.TelephonyResultCodeListDTO;
import com.powerdialer.model.TelephonyResultCode;
import com.powerdialer.validators.TelephonyResultCodeValidator;

@Service
@Transactional(readOnly = true)
public class TelephonyResultCodeService extends GenericService<TelephonyResultCode, TelephonyResultCodeFormDTO, TelephonyResultCodeListDTO> {

  @Autowired
  public TelephonyResultCodeService(ITelephonyResultCodeDao dao) {
    super(dao, TelephonyResultCode.class, TelephonyResultCodeFormDTO.class, TelephonyResultCodeListDTO.class, new TelephonyResultCodeValidator());
  }

}
