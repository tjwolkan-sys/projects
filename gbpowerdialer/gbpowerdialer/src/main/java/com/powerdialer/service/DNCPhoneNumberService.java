package com.powerdialer.service;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

import java.io.IOException;

import com.powerdialer.dao.IUserDao;
import com.powerdialer.exceptions.DncExpireDateValidationException;
import com.powerdialer.exceptions.DncPhoneExpiredDateBeforeCreateDateException;
import com.powerdialer.exceptions.DncPnFileImportInvalidCharacterException;
import com.powerdialer.model.User;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.powerdialer.dao.IDNCPhoneNumberDao;
import com.powerdialer.dao.impl.DNCPhoneNumberDao;
import com.powerdialer.datamanipulation.supplier.DataSupplier;
import com.powerdialer.dto.DNCPhoneNumberFormDTO;
import com.powerdialer.dto.DNCPhoneNumberListDTO;
import com.powerdialer.exceptions.DncPnFileImportFailedException;
import com.powerdialer.model.DNC;
import com.powerdialer.model.DNCPhoneNumber;
import com.powerdialer.request.DncPnBatchProcessingParameters;
import com.powerdialer.request.DncPnBatchProcessingRequest;
import com.powerdialer.util.Constants;
import com.powerdialer.validators.DNCPhoneNumberValidator;

@Service
@Transactional
public class DNCPhoneNumberService extends GenericService<DNCPhoneNumber, DNCPhoneNumberFormDTO, DNCPhoneNumberListDTO> {

  @Autowired
  private DNCPhoneNumberDao dncPhoneNumberDao;

  @Autowired
  private IUserDao userDao;

  private static final Logger LOG = LoggerFactory.getLogger(DNCPhoneNumberService.class);

  @Autowired
  public DNCPhoneNumberService(IDNCPhoneNumberDao dao) {
    super(dao, DNCPhoneNumber.class, DNCPhoneNumberFormDTO.class, DNCPhoneNumberListDTO.class, new DNCPhoneNumberValidator());
  }

  /**
   * Disable all specified phone numbers in DNC
   */
  public void disableByDncAndPhoneNumber(Long dncId, List<String> phoneNumbers) {
    dncPhoneNumberDao.disableByDncAndPhoneNumber(dncId, phoneNumbers);
  }

  /**
   * Create DNCPhoneNumber. pd_dnc_pn artık yalnız gerçek telefon numaralarını
   * tutar (saf block-list); müşteri bağı ayrı pd_dnc_customer tablosundadır.
   */
  public DNCPhoneNumber create(String phoneNumber, Constants.REASON reason, DNC dnc) {
    DNCPhoneNumber dncPhoneNumber = new DNCPhoneNumber();
    dncPhoneNumber.setDncList(dnc);
    dncPhoneNumber.setCreateUser(userDao.getDncSystemUser());
    dncPhoneNumber.setCreateDate(new Date());
    dncPhoneNumber.setPhoneNumber(phoneNumber);
    dncPhoneNumber.setReason(reason);
    dncPhoneNumber.setEnabled(true);
    return getDao().save(dncPhoneNumber);
  }

  public void updateDncPnBatch(long id, DncPnBatchProcessingRequest dncPnBatchProcessingRequest) {
    JSONObject filters = new JSONObject(dncPnBatchProcessingRequest.getFilters());
    User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    if (dncPnBatchProcessingRequest.getType().equals(Constants.DNC_PN_BATCH_PROCESSING.ENABLE.getParam())) {
      dncPhoneNumberDao.enableDncPnBatch(id, filters, user);
    }
    if (dncPnBatchProcessingRequest.getType().equals(Constants.DNC_PN_BATCH_PROCESSING.DISABLE.getParam())) {
      dncPhoneNumberDao.disableDncPnBatch(id, filters, user);
    }
    if (dncPnBatchProcessingRequest.getType().equals(Constants.DNC_PN_BATCH_PROCESSING.DELETE.getParam())) {
      dncPhoneNumberDao.deleteDncPnBatch(id, filters, user);
    }
  }

  public void dncPnFileBatchDelete(DncPnBatchProcessingParameters dncPnBatchProcessingParameters, DataSupplier<Map<String,Object>> dataSupplier, DNC dnc) throws IOException {
    dncPnFileBatchProcess(dncPnBatchProcessingParameters, dataSupplier, dnc, (d, rows) -> batchDelete(d, rows, dncPnBatchProcessingParameters));
  }

  public void dncPnFileBatchAdd(DncPnBatchProcessingParameters dncPnBatchProcessingParameters, DataSupplier<Map<String,Object>> dataSupplier, DNC dnc) {
    dncPnFileBatchProcess(dncPnBatchProcessingParameters, dataSupplier, dnc, (d, rows) -> batchInsert(d, rows, dncPnBatchProcessingParameters));
  }

  /**
   * Processes DNC phone number file imports in batches and applies the given operation.
   */
  private void dncPnFileBatchProcess(DncPnBatchProcessingParameters params, DataSupplier<Map<String,Object>> dataSupplier,
                                     DNC dnc, BiConsumer<DNC, List<Map<String, Object>>> operation) {
    try {
      List<Map<String, Object>> rows = new ArrayList<>();
      Map<String, Object> row = dataSupplier.getNextObject();

      while (row != null) {
        if (!rowFromDataSupplierIsValid(row, params)) {
          row = dataSupplier.getNextObject();
          continue;
        }

        rows.add(row);
        row = dataSupplier.getNextObject();

        if (isBatchProcessingNedded(rows.size(), row)) {
          operation.accept(dnc, rows);
          rows.clear();
        }
      }
    } catch (DncPnFileImportInvalidCharacterException | DncExpireDateValidationException e) {
      LOG.error(e.getMessage());
      throw e;
    } catch (Exception e) {
      LOG.error(e.getMessage());
      throw new DncPnFileImportFailedException();
    }
  }

  /**
   * Inserts a batch of DNC phone numbers created from file rows.
   */
  private void batchInsert(DNC dnc, List<Map<String, Object>> rows, DncPnBatchProcessingParameters params) {
    List<DNCPhoneNumber> list = new ArrayList<>();
    for (Map<String, Object> row : rows) {
      list.add(createFromRow(row, dnc, params));
    }
    super.batchInsert(list);
  }

  /**
   * Parses the expired date value from the import file.
   */
  private Date parseExpiredDate(Object val) {
    try {
      if (val instanceof Date) {
        return (Date) val;
      }
      return new SimpleDateFormat("dd.MM.yyyy").parse(val.toString());
    } catch (Exception e) {
      return null;
    }
  }

  /**
   * Deletes a batch of DNC phone numbers extracted from file rows.
   */
  private void batchDelete(DNC dnc, List<Map<String, Object>> rows, DncPnBatchProcessingParameters params) {
    List<String> phoneNumbers = new ArrayList<>();
    for (Map<String, Object> row : rows) {
      Object val = row.get(params.getColumnParameter());
      if (val != null) {
        phoneNumbers.add(val.toString());
      }
    }
    User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    dncPhoneNumberDao.dncPnFileBatchDelete(dnc.getId(), phoneNumbers, user);
  }

  private boolean rowFromDataSupplierIsValid(Map<String,Object> row, DncPnBatchProcessingParameters dncPnBatchProcessingParameters) {
    Object value = row.get(dncPnBatchProcessingParameters.getColumnParameter());
    if (value == null) {
      return false;
    }

    String phone = value.toString().trim();
    if (phone.isEmpty()) {
      return false;
    }

    if (!phone.matches("^[0-9]+$")) {
      throw new DncPnFileImportInvalidCharacterException();
    }

    return true;
  }

  private boolean isBatchProcessingNedded(int currentSize, Object nextRow) {
    return currentSize >= Constants.DNC_PHONE_NUMBER_COUNT_FOR_BATCH_PROCESSING || (nextRow == null && currentSize > 0);
  }

  /**
   * Creates a DNCPhoneNumber entity from a single import row and applies expire date validation.
   */
  private DNCPhoneNumber createFromRow(Map<String, Object> row, DNC dnc, DncPnBatchProcessingParameters params) {
    DNCPhoneNumber pn = new DNCPhoneNumber();

    pn.setDncList(dnc);
    pn.setEnabled(true);
    pn.setDeleted(false);
    pn.setReason(Constants.REASON.ADMINISTRATIVE);

    Object phone = row.get(params.getColumnParameter());
    pn.setPhoneNumber(phone != null ? phone.toString() : null);

    Date listExpiredDate = dnc.getExpireDate();
    Date earliestAllowedExpireDate = truncateTime(new Date());

    if (params.getExpiredColumnParameter() != null) {
      Object exp = row.get(params.getExpiredColumnParameter());
      Date pnExpireDate = exp != null ? parseExpiredDate(exp) : null;

      if (pnExpireDate != null && pnExpireDate.before(earliestAllowedExpireDate)) {
        throw new DncPhoneExpiredDateBeforeCreateDateException();
      }
      if (listExpiredDate != null && pnExpireDate != null && listExpiredDate.before(pnExpireDate)) {
        throw new DncExpireDateValidationException();
      }
      pn.setExpiredDate(pnExpireDate);
    }
    return pn;
  }

  private Date truncateTime(Date date) {
    Calendar calendar = Calendar.getInstance();
    calendar.setTime(date);
    calendar.set(Calendar.HOUR_OF_DAY, 0);
    calendar.set(Calendar.MINUTE, 0);
    calendar.set(Calendar.SECOND, 0);
    calendar.set(Calendar.MILLISECOND, 0);
    return calendar.getTime();
  }

  @Transactional(readOnly = true)
  public List<String> getPhoneNumbersByList(Long listId) {
    return dncPhoneNumberDao.getPhoneNumbersByList(listId);
  }

  public List<DNCPhoneNumber> getByListId(Long dncId) {
    return dncPhoneNumberDao.findByDncListId(dncId);
  }

  public boolean existsByPhoneNumberAndDncId(String phoneNumber, Long dncId) {
    return dncPhoneNumberDao.existsByPhoneNumberAndDncId(phoneNumber, dncId);
  }

  @Transactional(readOnly = true)
  public Set<String> findActivePhoneNumbersByDncIdsAndPhoneNumbers(Collection<Long> dncIds, Collection<String> phoneNumbers, Date now) {
    if (dncIds == null || dncIds.isEmpty() || phoneNumbers == null || phoneNumbers.isEmpty()) {
      return Collections.emptySet();
    }
    List<String> activePhoneNumbers = dncPhoneNumberDao.findActivePhoneNumbersByDncIdsAndPhoneNumbers(dncIds, phoneNumbers, now);
    return activePhoneNumbers == null ? Collections.emptySet() : new HashSet<>(activePhoneNumbers);
  }

}
