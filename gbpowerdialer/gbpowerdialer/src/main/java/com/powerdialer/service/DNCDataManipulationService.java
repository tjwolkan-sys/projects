package com.powerdialer.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import java.io.IOException;
import java.sql.SQLException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.powerdialer.datamanipulation.supplier.DataSupplier;
import com.powerdialer.engine.CustomerDncService;
import com.powerdialer.engine.DNCEngine;
import com.powerdialer.exceptions.DNCNotFoundException;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.DNC;
import com.powerdialer.model.DNCPhoneNumber;
import com.powerdialer.util.Constants;

@Service
public class DNCDataManipulationService {

  private final static int BULK_SIZE = 20;

  @Autowired
  private DNCService dncService;

  @Autowired
  private DNCPhoneNumberService dncPhoneNumberService;

  @Autowired
  private DNCEngine dncEngine;

  @Autowired
  private CustomerDncService customerDncService;

  @Autowired
  private CampaignService campaignService;

  public int processInsertDataForDefaultDnc(Long campaignId, DataSupplier<String> dataSupplier) throws IOException, SQLException {
    int insertedDataCount = 0;
    DNC defaultDnc = getDefaultDNCByCampaignId(campaignId);
    List<DNCPhoneNumber> bulk = new ArrayList<>();
    String dncPhoneNumberToCreate = dataSupplier.getNextObject();
    while (dncPhoneNumberToCreate != null) {
      DNCPhoneNumber dncPhoneNumber = new DNCPhoneNumber();
      dncPhoneNumber.setEnabled(true);
      dncPhoneNumber.setDeleted(false);
      dncPhoneNumber.setReason(Constants.REASON.ADMINISTRATIVE);
      dncPhoneNumber.setDncList(defaultDnc);
      dncPhoneNumber.setPhoneNumber(dncPhoneNumberToCreate);
      bulk.add(dncPhoneNumber);
      if (bulk.size() >= BULK_SIZE) {
        dncPhoneNumberService.batchInsert(bulk);
        insertedDataCount += bulk.size();
        bulk.clear();
      }
      dncPhoneNumberToCreate = dataSupplier.getNextObject();
    }
    if (!bulk.isEmpty()) {
      dncPhoneNumberService.batchInsert(bulk);
      insertedDataCount += bulk.size();
    }
    dncEngine.updateDnc(defaultDnc.getId());
    return insertedDataCount;
  }

  public int processDisableDataForDefaultDnc(Long campaignId, DataSupplier<String> dataSupplier) throws IOException, SQLException {
    int disabledDataCount = 0;
    DNC defaultDnc = getDefaultDNCByCampaignId(campaignId);
    String dncPhoneNumberToDisable = dataSupplier.getNextObject();
    List<String> phoneNumbersToDisable = new ArrayList<>();
    while (dncPhoneNumberToDisable != null) {
      phoneNumbersToDisable.add(dncPhoneNumberToDisable);
      if (phoneNumbersToDisable.size() >= 100) {
        dncPhoneNumberService.disableByDncAndPhoneNumber(defaultDnc.getId(), phoneNumbersToDisable);
        disabledDataCount += phoneNumbersToDisable.size();
        phoneNumbersToDisable.clear();
      }
      dncPhoneNumberToDisable = dataSupplier.getNextObject();
    }
    if (!phoneNumbersToDisable.isEmpty()) {
      dncPhoneNumberService.disableByDncAndPhoneNumber(defaultDnc.getId(), phoneNumbersToDisable);
      disabledDataCount += phoneNumbersToDisable.size();
    }
    dncEngine.updateDnc(defaultDnc.getId());
    return disabledDataCount;
  }

  /**
   * Verilen customer_no listesini kampanyanın default DNC listesine müşteri
   * kapsamıyla işler: her customer_no için müşterinin tüm telefon numaraları
   * PHONE kayıtlarına genişletilir (CustomerDncService) ve sonda tüm
   * engellenen numaraların union setiyle TEK canlı kuyruk hot-drop'u yapılır.
   *
   * @return işlenen customer_no sayısı
   */
  public int processInsertCustomersForDefaultDnc(Long campaignId, DataSupplier<String> dataSupplier) throws IOException, SQLException {
    DNC defaultDnc = getDefaultDNCByCampaignId(campaignId);
    Campaign campaign = campaignService.getCampaignWithPhoneNumberColumnsByNameOrId(String.valueOf(campaignId));
    Set<String> allBlockedNumbers = new LinkedHashSet<>();
    int processedCustomerCount = 0;
    String customerNo = dataSupplier.getNextObject();
    while (customerNo != null) {
      String trimmedCustomerNo = customerNo.trim();
      if (!trimmedCustomerNo.isEmpty()) {
        allBlockedNumbers.addAll(customerDncService.blockByCustomerNo(trimmedCustomerNo, campaign, defaultDnc));
        processedCustomerCount++;
      }
      customerNo = dataSupplier.getNextObject();
    }
    dncEngine.dropQueuedCallsForNumbers(defaultDnc, allBlockedNumbers);
    return processedCustomerCount;
  }

  private DNC getDefaultDNCByCampaignId(Long campaignId) {
    DNC dnc;
    dnc = dncService.getDefaultDNCByCampaignId(campaignId);
    if (dnc == null)
      throw new DNCNotFoundException("default DNC for campaign with id: " + campaignId + " can't be found");
    return dnc;
  }
}
