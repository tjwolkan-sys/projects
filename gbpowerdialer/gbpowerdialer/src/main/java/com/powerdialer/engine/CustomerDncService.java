package com.powerdialer.engine;

import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import com.powerdialer.engine.db.CampaignDbOperations;
import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.DNC;
import com.powerdialer.model.PhoneNumberColumn;
import com.powerdialer.service.DNCCustomerPhoneService;
import com.powerdialer.service.DNCPhoneNumberService;
import com.powerdialer.util.Constants;

/**
 * Müşteri bazlı DNC çekirdeği (customer-scope expansion).
 *
 * CUSTOMER scope tetiklendiğinde müşterinin ana tablodaki tüm satırlarından
 * enabled telefon kolon değerleri toplanır; pd_dnc_pn'e bir adet CUSTOMER
 * marker + her numara için PHONE kaydı yazılır (idempotent) ve müşterinin
 * henüz tamamlanmamış sibling satırları mevcut DNC işaretleme semantiğiyle
 * (TRC=76) kapatılır. Runtime dial-time DNC filtresi telefon bazlı kalır;
 * bu sınıf hot path dışında, tetik başına (wrap-up/API/delta-sync) çalışır.
 *
 * Edge Case 1: customer_no mapping'i yoksa veya satırda değer boşsa hata
 * üretilmez; mevcut satırın telefon kolonlarındaki numaralar PHONE olarak
 * engellenir (CUSTOMER marker ve sibling işaretleme yapılmaz).
 */
@Component("customerDncService")
@Lazy(false)
public class CustomerDncService {

  private static final Logger LOG = LoggerFactory.getLogger(CustomerDncService.class);

  @Autowired
  private CampaignDbOperations campaignDbOperations;

  @Autowired
  private DNCPhoneNumberService dncPhoneNumberService;

  @Autowired
  private DNCEngine dncEngine;

  @Autowired
  private DNCCustomerPhoneService dncCustomerPhoneService;

  /**
   * Agent wrap-up yolu: scope=CUSTOMER seçildiğinde çağrının satırından
   * customer_no çözülür ve müşteri genişletmesi yapılır; customer_no yoksa
   * satır bazlı telefon fallback'i uygulanır (E1).
   *
   * @return engellenen telefon numaraları (hot-drop girdisi)
   */
  public Set<String> blockByCustomerScope(Call call, Campaign campaign, DNC dnc) {
    Set<String> seedNumbers = new LinkedHashSet<>();
    if (call.getCalledNumber() != null) {
      seedNumbers.add(call.getCalledNumber());
    }

    String customerNo = resolveCustomerNo(call, campaign);
    if (customerNo == null) {
      seedNumbers.addAll(call.getNumbersInRawData());
      persistPhoneNumbers(dnc, Constants.REASON.WRAPUP_CODE, seedNumbers);
      dncEngine.updateDnc(dnc.getId());
      LOG.info("[CUSTOMER-DNC] customer_no unavailable, fell back to row numbers. callId={}, campaignId={}, blockedCount={}",
          call.getId(), campaign.getId(), seedNumbers.size());
      return seedNumbers;
    }

    return blockCustomer(customerNo, campaign, dnc, Constants.REASON.WRAPUP_CODE, call.getUniqueKey(), seedNumbers);
  }

  /**
   * API/batch yolu: doğrudan customer_no ile müşteri genişletmesi yapar
   * (Puzzle endpoint'i ve delta-sync kullanır).
   *
   * @return engellenen telefon numaraları (hot-drop girdisi)
   */
  public Set<String> blockByCustomerNo(String customerNo, Campaign campaign, DNC dnc) {
    return blockCustomer(customerNo, campaign, dnc, Constants.REASON.ADMINISTRATIVE, null, new LinkedHashSet<>());
  }

  private Set<String> blockCustomer(String customerNo, Campaign campaign, DNC dnc, Constants.REASON reason,
                                    String excludeUniqueKey, Set<String> seedNumbers) {
    Set<String> blockedNumbers = new LinkedHashSet<>(seedNumbers);
    blockedNumbers.addAll(collectCustomerPhoneNumbers(campaign, customerNo));

    persistPhoneNumbers(dnc, reason, blockedNumbers);
    persistCustomerRelations(dnc, customerNo, blockedNumbers, reason);
    dncEngine.updateDnc(dnc.getId());

    try {
      campaignDbOperations.setFilteredByDncListStatusByCustomerNo(campaign, customerNo, excludeUniqueKey);
    } catch (Exception e) {
      LOG.error("[CUSTOMER-DNC] Could not mark sibling rows for customerNo=" + customerNo
          + " campaignId=" + campaign.getId(), e);
    }

    LOG.info("[CUSTOMER-DNC] Customer scope block completed. customerNo={}, campaignId={}, dncId={}, blockedCount={}",
        customerNo, campaign.getId(), dnc.getId(), blockedNumbers.size());
    return blockedNumbers;
  }

  private String resolveCustomerNo(Call call, Campaign campaign) {
    String customerNoColumn = campaign.getColResultCustomerNo();
    if (StringUtils.isBlank(customerNoColumn) || call.getRawData() == null || !call.getRawData().has(customerNoColumn)) {
      return null;
    }
    Object value = call.getRawData().get(customerNoColumn);
    if (value == null || JSONObject.NULL.equals(value)) {
      return null;
    }
    String customerNo = value.toString().trim();
    return customerNo.isEmpty() ? null : customerNo;
  }

  /**
   * Müşterinin tüm satırlarındaki enabled telefon kolon değerlerini toplar.
   * SELECT hatasında boş döner: marker + seed numaralar yine yazılır, kalan
   * numaraları delta-sync telafi eder.
   */
  private Set<String> collectCustomerPhoneNumbers(Campaign campaign, String customerNo) {
    Set<String> numbers = new LinkedHashSet<>();
    JSONArray rows;
    try {
      rows = campaignDbOperations.fetchRowsByCustomerNo(campaign, customerNo);
    } catch (Exception e) {
      LOG.error("[CUSTOMER-DNC] Could not fetch rows for customerNo=" + customerNo
          + " campaignId=" + campaign.getId(), e);
      return numbers;
    }
    if (rows == null) {
      return numbers;
    }
    List<PhoneNumberColumn> phoneNumberColumns = campaign.getEnabledPhoneNumberColumns();
    for (int i = 0; i < rows.length(); i++) {
      JSONObject row = rows.optJSONObject(i);
      if (row == null) {
        continue;
      }
      for (PhoneNumberColumn phoneNumberColumn : phoneNumberColumns) {
        String colNumber = phoneNumberColumn.getColNumber();
        if (colNumber == null || !row.has(colNumber)) {
          continue;
        }
        Object value = row.get(colNumber);
        if (value == null || JSONObject.NULL.equals(value)) {
          continue;
        }
        String phoneNumber = value.toString().trim();
        if (!phoneNumber.isEmpty()) {
          numbers.add(phoneNumber);
        }
      }
    }
    return numbers;
  }

  /**
   * pd_dnc_pn'e saf PHONE kaydı yazar (customer_no taşımaz). Zaten kayıtlı
   * numaralar atlanır. Dial-time DNC filtresi yalnız bu satırları kullanır.
   */
  private void persistPhoneNumbers(DNC dnc, Constants.REASON reason, Set<String> phoneNumbers) {
    for (String phoneNumber : phoneNumbers) {
      if (dncPhoneNumberService.existsByPhoneNumberAndDncId(phoneNumber, dnc.getId())) {
        LOG.info("[CUSTOMER-DNC] Phone number already exists in DNC, skipping. phone={}, dncId={}", phoneNumber, dnc.getId());
        continue;
      }
      dncPhoneNumberService.create(phoneNumber, reason, dnc);
    }
  }

  /**
   * customer_no ↔ phone ilişkisini pd_dnc_customer'a idempotent yazar
   * (raporlama + delta-sync yeniden-genişletme kaynağı). pd_dnc_pn saf
   * telefon listesi kalır; bu tablo müşteri bağını ayrı tutar.
   */
  private void persistCustomerRelations(DNC dnc, String customerNo, Set<String> phoneNumbers, Constants.REASON reason) {
    for (String phoneNumber : phoneNumbers) {
      dncCustomerPhoneService.createIfAbsent(customerNo, phoneNumber, reason, dnc);
    }
  }
}
