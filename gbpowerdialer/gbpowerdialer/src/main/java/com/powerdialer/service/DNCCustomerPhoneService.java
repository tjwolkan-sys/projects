package com.powerdialer.service;

import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.powerdialer.dao.IUserDao;
import com.powerdialer.dao.impl.DNCCustomerPhoneDao;
import com.powerdialer.model.DNC;
import com.powerdialer.model.DNCCustomerPhone;
import com.powerdialer.util.Constants;

/**
 * pd_dnc_customer (customer_no ↔ phone ilişki tablosu) servisi (FEAT-318332
 * refactor). Customer-scope engellemede ilişki buraya yazılır; delta-sync ve
 * fetch-time customer filtresi "hangi müşteri engelli" bilgisini buradan alır.
 * pd_dnc_pn (saf telefon block-list) ayrı tutulur; dial-time engelleme orayı
 * kullanmaya devam eder.
 */
@Service
@Transactional
public class DNCCustomerPhoneService {

  @Autowired
  private DNCCustomerPhoneDao dncCustomerPhoneDao;

  @Autowired
  private IUserDao userDao;

  /**
   * (customer_no, phone) ilişkisini idempotent yazar; zaten varsa atlar.
   */
  public void createIfAbsent(String customerNo, String phoneNumber, Constants.REASON reason, DNC dnc) {
    if (dncCustomerPhoneDao.existsByCustomerNoAndPhoneNumberAndDncId(customerNo, phoneNumber, dnc.getId())) {
      return;
    }
    DNCCustomerPhone relation = new DNCCustomerPhone();
    relation.setDncList(dnc);
    relation.setCustomerNo(customerNo);
    relation.setPhoneNumber(phoneNumber);
    relation.setReason(reason);
    relation.setEnabled(true);
    relation.setCreateUser(userDao.getDncSystemUser());
    relation.setCreateDate(new Date());
    dncCustomerPhoneDao.save(relation);
  }

  @Transactional(readOnly = true)
  public Set<String> findActiveCustomerNosByDncIdsAndCustomerNos(Collection<Long> dncIds, Collection<String> customerNos, Date now) {
    if (dncIds == null || dncIds.isEmpty() || customerNos == null || customerNos.isEmpty()) {
      return Collections.emptySet();
    }
    List<String> activeCustomerNos = dncCustomerPhoneDao.findActiveCustomerNosByDncIdsAndCustomerNos(dncIds, customerNos, now);
    return activeCustomerNos == null ? Collections.emptySet() : new HashSet<>(activeCustomerNos);
  }
}
