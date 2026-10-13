package com.powerdialer.service;

import static org.junit.Assert.assertEquals;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.powerdialer.dao.IDNCPhoneNumberDao;
import com.powerdialer.exceptions.DncExpireDateValidationException;
import com.powerdialer.exceptions.DncPhoneExpiredDateBeforeCreateDateException;
import com.powerdialer.model.DNC;
import com.powerdialer.model.DNCPhoneNumber;
import com.powerdialer.request.DncPnBatchProcessingParameters;

public class DNCPhoneNumberServiceTest {

  private final DNCPhoneNumberService dncPhoneNumberService = new DNCPhoneNumberService(org.mockito.Mockito.mock(IDNCPhoneNumberDao.class));

  @Test(expected = DncPhoneExpiredDateBeforeCreateDateException.class)
  public void createFromRowShouldRejectPhoneExpireDateBeforeImportCreateTime() {
    DNC dnc = new DNC();
    dnc.setExpireDate(new Date(System.currentTimeMillis() + 86_400_000L));

    DncPnBatchProcessingParameters parameters = new DncPnBatchProcessingParameters();
    parameters.setColumnParameter("phone");
    parameters.setExpiredColumnParameter("expired");

    Map<String, Object> row = new HashMap<>();
    row.put("phone", "905551112233");
    row.put("expired", new Date(System.currentTimeMillis() - 86_400_000L));

    ReflectionTestUtils.invokeMethod(dncPhoneNumberService, "createFromRow", row, dnc, parameters);
  }

  @Test(expected = DncExpireDateValidationException.class)
  public void createFromRowShouldRejectPhoneExpireDateAfterListExpireDate() {
    DNC dnc = new DNC();
    dnc.setExpireDate(new Date(System.currentTimeMillis() + 86_400_000L));

    DncPnBatchProcessingParameters parameters = new DncPnBatchProcessingParameters();
    parameters.setColumnParameter("phone");
    parameters.setExpiredColumnParameter("expired");

    Map<String, Object> row = new HashMap<>();
    row.put("phone", "905551112233");
    row.put("expired", new Date(System.currentTimeMillis() + 172_800_000L));

    ReflectionTestUtils.invokeMethod(dncPhoneNumberService, "createFromRow", row, dnc, parameters);
  }

  @Test
  public void createFromRowShouldKeepValidPhoneExpireDate() {
    DNC dnc = new DNC();
    dnc.setExpireDate(new Date(System.currentTimeMillis() + 172_800_000L));

    DncPnBatchProcessingParameters parameters = new DncPnBatchProcessingParameters();
    parameters.setColumnParameter("phone");
    parameters.setExpiredColumnParameter("expired");

    Date validExpireDate = new Date(System.currentTimeMillis() + 86_400_000L);

    Map<String, Object> row = new HashMap<>();
    row.put("phone", "905551112233");
    row.put("expired", validExpireDate);

    DNCPhoneNumber phoneNumber = ReflectionTestUtils.invokeMethod(dncPhoneNumberService, "createFromRow", row, dnc, parameters);

    assertEquals(validExpireDate, phoneNumber.getExpiredDate());
  }
}
