package com.powerdialer.service;

import java.sql.SQLException;
import java.text.ParseException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.powerdialer.dao.IPhoneNumberColumnDao;
import com.powerdialer.dto.PhoneNumberColumnFormDTO;
import com.powerdialer.dto.PhoneNumberColumnListDTO;
import com.powerdialer.engine.db.CampaignDbOperations;
import com.powerdialer.model.PhoneNumberColumn;
import com.powerdialer.request.AddPhoneNumberColumnsRequest;
import com.powerdialer.validators.PhoneNumberColumnValidator;

@Service
public class PhoneNumberColumnService extends GenericService<PhoneNumberColumn, PhoneNumberColumnFormDTO, PhoneNumberColumnListDTO> {

  @Autowired
  private CampaignDbOperations campaignDbOperations;

  @Autowired
  public PhoneNumberColumnService(IPhoneNumberColumnDao dao) {
    super(dao, PhoneNumberColumn.class, PhoneNumberColumnFormDTO.class, PhoneNumberColumnListDTO.class, new PhoneNumberColumnValidator());
  }

  public void createDefaultPhoneNumberColumnsForCampaign(AddPhoneNumberColumnsRequest sourceTableOptions) throws SQLException, ParseException {
    campaignDbOperations.addDefaultPhoneNumberColumns(sourceTableOptions);
  }

}
