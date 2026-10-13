package com.powerdialer.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.powerdialer.dao.IResultCodeDao;
import com.powerdialer.dto.ResultCodeFormDTO;
import com.powerdialer.dto.ResultCodeListDTO;
import com.powerdialer.model.ResultCode;
import com.powerdialer.validators.ResultCodeValidator;

@Service("resultCodeService")
@Transactional(readOnly = true)
public class ResultCodeService extends GenericService<ResultCode, ResultCodeFormDTO, ResultCodeListDTO> {

  @Autowired
  public ResultCodeService(IResultCodeDao dao) {
    super(dao, ResultCode.class, ResultCodeFormDTO.class, ResultCodeListDTO.class, new ResultCodeValidator());
  }

  public List<ResultCodeListDTO> getResultCodesByCampaignId(Long id, boolean nonFinalOnly) {
    List<ResultCodeListDTO> codeListDTOs = new ArrayList<>();
    for (ResultCode resultCode : ((IResultCodeDao) getDao()).getResultCodesByCampaignId(id, nonFinalOnly)) {
      codeListDTOs.add(mapper.map(resultCode, ResultCodeListDTO.class));
    }
    return codeListDTOs;
  }

  public List<ResultCodeListDTO> getResultCodesByGroupIds(Long[] groupIds, boolean nonFinalOnly) {
    List<ResultCodeListDTO> codeListDTOs = new ArrayList<>();
    for (ResultCode resultCode : ((IResultCodeDao) getDao()).getResultCodesByGroupIds(groupIds, nonFinalOnly)) {
      codeListDTOs.add(mapper.map(resultCode, ResultCodeListDTO.class));
    }
    return codeListDTOs;
  }

}
