package com.powerdialer.batch.scheduler;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.powerdialer.engine.CustomerDncService;
import com.powerdialer.engine.DNCEngine;
import com.powerdialer.engine.db.CampaignDbOperations;
import com.powerdialer.engine.db.DBEngine;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.DNC;
import com.powerdialer.model.ParametrizedQuery;
import com.powerdialer.model.PhoneNumberColumn;
import com.powerdialer.service.CampaignService;
import com.powerdialer.service.DNCCustomerPhoneService;
import com.powerdialer.service.DNCPhoneNumberService;
import com.powerdialer.service.DNCService;

/**
 * Akıllı DNC delta-sync scheduler'ı (FEAT-318332).
 *
 * Günlük/aylık yüklenen yeni dataları date_entered watermark'ıyla algılar:
 * son başarılı senkronun MAX(date_entered) değerinden yeni satır yoksa süreç
 * ucuzca pas geçilir (GETDATE() karşılaştırması YAPILMAZ — Edge Case 3).
 * Yeni satırlar keyset sayfalamasıyla çekilir; telefonları veya customer_no
 * değeri aktif DNC havuzunda eşleşen satırlar mevcut DNC işaretleme
 * semantiğiyle (TRC=76) kapatılır, customer eşleşmelerinde müşterinin tüm
 * numaraları PHONE kayıtlarına genişletilir ve union set canlı kuyruk
 * hot-drop'una verilir.
 *
 * Kampanya data tabloları PD DB'sinden farklı sunucuda olabilir; kampanya
 * DB sorguları pd_dnc_pn'e JOIN yapmaz — DNC kontrolü PD tarafında
 * IN-chunk'larla yapılır. Watermark'lar pd_dnc_sync_log'da tutulur; hata
 * durumunda watermark İLERLEMEZ (retry güvenliği).
 *
 * Not (03.06.2026 müşteri kararı): DNC'den çıkan kayıtlar için eski
 * satırların geri açılması ("cleanup") TALEP DEĞİLDİR ve kapsam dışıdır;
 * expire süresi dolan numaralar yalnız yeni yüklenen datalarda normal
 * şekilde aranır hâle gelir.
 */
@Component
@EnableScheduling
@Lazy(false)
public class DncDeltaSyncScheduler {

  private static final Logger LOG = LoggerFactory.getLogger(DncDeltaSyncScheduler.class);
  private static final int IN_CHUNK_SIZE = 1000;
  private static final int MAX_ERROR_LENGTH = 500;

  private final AtomicBoolean running = new AtomicBoolean(false);

  private final DBEngine dbEngine;
  private final DNCService dncService;
  private final DNCPhoneNumberService dncPhoneNumberService;
  private final DNCCustomerPhoneService dncCustomerPhoneService;
  private final CustomerDncService customerDncService;
  private final CampaignDbOperations campaignDbOperations;
  private final DNCEngine dncEngine;
  private final CampaignService campaignService;

  private final String srcUrl;
  private final String srcUser;
  private final String srcPass;

  @Value("${jobs.dnc-delta-sync.batch.size:500}")
  private int batchSize;

  public DncDeltaSyncScheduler(DBEngine dbEngine,
                               DNCService dncService,
                               DNCPhoneNumberService dncPhoneNumberService,
                               DNCCustomerPhoneService dncCustomerPhoneService,
                               CustomerDncService customerDncService,
                               CampaignDbOperations campaignDbOperations,
                               DNCEngine dncEngine,
                               CampaignService campaignService) {
    this.dbEngine = dbEngine;
    this.dncService = dncService;
    this.dncPhoneNumberService = dncPhoneNumberService;
    this.dncCustomerPhoneService = dncCustomerPhoneService;
    this.customerDncService = customerDncService;
    this.campaignDbOperations = campaignDbOperations;
    this.dncEngine = dncEngine;
    this.campaignService = campaignService;
    this.srcUrl = dbEngine.getMainDatabaseUrl();
    this.srcUser = dbEngine.getMainDatabaseUsername();
    this.srcPass = dbEngine.getMainDatabasePassword();
  }

  @Scheduled(cron = "${jobs.dnc-delta-sync.cron}")
  public void runDeltaSync() {
    if (!running.compareAndSet(false, true)) {
      LOG.warn("[DNC-DELTA-SYNC] Already running, skipping this tick.");
      return;
    }
    try {
      long startedAt = System.currentTimeMillis();
      List<Long> campaignIds = loadEligibleCampaignIds();
      if (campaignIds.isEmpty()) {
        LOG.info("[DNC-DELTA-SYNC] No eligible campaign (dnc_enabled + col_date_entered). Skipping.");
        return;
      }
      for (Long campaignId : campaignIds) {
        try {
          syncCampaign(campaignId);
        } catch (Exception e) {
          LOG.error("[DNC-DELTA-SYNC] Campaign sync failed unexpectedly. campaignId=" + campaignId, e);
        }
      }
      LOG.info("[DNC-DELTA-SYNC] Finished for {} campaign(s), took={} ms",
          campaignIds.size(), (System.currentTimeMillis() - startedAt));
    } catch (Exception e) {
      LOG.error("[DNC-DELTA-SYNC] Failed to start", e);
    } finally {
      running.set(false);
    }
  }

  private void syncCampaign(Long campaignId) {
    Campaign campaign = campaignService.getCampaignWithPhoneNumberColumnsByNameOrId(String.valueOf(campaignId));
    String lastWatermark = readLastWatermark(campaignId);

    String windowMax;
    try {
      windowMax = campaignDbOperations.findMaxDateEnteredAfter(campaign, lastWatermark);
    } catch (Exception e) {
      LOG.error("[DNC-DELTA-SYNC] Max date_entered probe failed. campaignId=" + campaignId, e);
      return;
    }

    if (windowMax == null) {
      LOG.info("[DNC-DELTA-SYNC] No new data since watermark, skipping cheaply. campaignId={}, watermark={}",
          campaignId, lastWatermark);
      return;
    }

    DNC defaultDnc = dncService.getDefaultDNCByCampaignId(campaignId);
    Set<Long> dncIds = resolveActiveDncIds(campaignId, campaign);
    if (dncIds.isEmpty()) {
      LOG.warn("[DNC-DELTA-SYNC] Campaign has no active DNC list, skipping. campaignId={}", campaignId);
      return;
    }
    if (defaultDnc == null) {
      LOG.warn("[DNC-DELTA-SYNC] Campaign has no default DNC; customer expansion will be skipped. campaignId={}", campaignId);
    }

    Timestamp startedAt = new Timestamp(System.currentTimeMillis());
    int rowsBlocked = 0;
    Set<String> expandedNumbers = new LinkedHashSet<>();
    try {
      rowsBlocked = processNewDataWindow(campaign, dncIds, defaultDnc, lastWatermark, windowMax, expandedNumbers);
      if (defaultDnc != null && !expandedNumbers.isEmpty()) {
        dncEngine.dropQueuedCallsForNumbers(defaultDnc, expandedNumbers);
      }
      writeSyncLog(campaignId, windowMax, startedAt, "SUCCESS", rowsBlocked, null);
      LOG.info("[DNC-DELTA-SYNC] Campaign sync done. campaignId={}, rowsBlocked={}, watermark={}",
          campaignId, rowsBlocked, windowMax);
    } catch (Exception e) {
      // Watermark ilerletilmez (max_date_entered NULL) — sonraki koşu aynı
      // pencereyi yeniden dener.
      writeSyncLog(campaignId, null, startedAt, "FAILED", rowsBlocked, safeMessage(e));
      LOG.error("[DNC-DELTA-SYNC] Campaign sync FAILED, watermark not advanced. campaignId=" + campaignId, e);
    }
  }

  /**
   * (lastWatermark, windowMax] penceresindeki yeni satırları sayfalı işler;
   * aktif DNC havuzuyla (telefon veya customer_no) eşleşen satırları kapatır
   * ve eşleşen müşterileri PHONE kayıtlarına genişletir.
   */
  private int processNewDataWindow(Campaign campaign, Set<Long> dncIds, DNC defaultDnc,
                                   String lastWatermark, String windowMax, Set<String> expandedNumbers) throws Exception {
    List<PhoneNumberColumn> phoneNumberColumns = campaign.getEnabledPhoneNumberColumns();
    String customerNoColumn = campaign.getColResultCustomerNo();
    int rowsBlocked = 0;
    String lastUniqueKey = null;

    while (true) {
      JSONArray page = campaignDbOperations.fetchRowsEnteredAfter(campaign, lastWatermark, windowMax, lastUniqueKey, batchSize);
      if (page == null || page.length() == 0) {
        break;
      }

      Set<String> pagePhones = new LinkedHashSet<>();
      Set<String> pageCustomers = new LinkedHashSet<>();
      for (int i = 0; i < page.length(); i++) {
        JSONObject row = page.optJSONObject(i);
        if (row == null) {
          continue;
        }
        pagePhones.addAll(extractPhones(row, phoneNumberColumns));
        String customerNo = extractValue(row, customerNoColumn);
        if (customerNo != null) {
          pageCustomers.add(customerNo);
        }
      }

      Set<String> activePhones = findActivePhonesChunked(dncIds, pagePhones);
      Set<String> activeCustomers = findActiveCustomersChunked(dncIds, pageCustomers);

      Set<String> matchedUniqueKeys = new LinkedHashSet<>();
      Set<String> matchedCustomers = new LinkedHashSet<>();
      for (int i = 0; i < page.length(); i++) {
        JSONObject row = page.optJSONObject(i);
        if (row == null) {
          continue;
        }
        String uniqueKey = extractValue(row, campaign.getColUniqueKey());
        if (uniqueKey == null) {
          continue;
        }
        lastUniqueKey = uniqueKey;
        boolean phoneMatched = !Collections.disjoint(extractPhones(row, phoneNumberColumns), activePhones);
        String customerNo = extractValue(row, customerNoColumn);
        boolean customerMatched = customerNo != null && activeCustomers.contains(customerNo);
        if (phoneMatched || customerMatched) {
          matchedUniqueKeys.add(uniqueKey);
        }
        if (customerMatched) {
          matchedCustomers.add(customerNo);
        }
      }

      if (!matchedUniqueKeys.isEmpty()) {
        campaignDbOperations.setFilteredByDncListStatusByUniqueKeys(campaign, matchedUniqueKeys);
        rowsBlocked += matchedUniqueKeys.size();
      }
      if (defaultDnc != null) {
        for (String customerNo : matchedCustomers) {
          Set<String> blocked = customerDncService.blockByCustomerNo(customerNo, campaign, defaultDnc);
          if (blocked != null) {
            expandedNumbers.addAll(blocked);
          }
        }
      }

      if (page.length() < batchSize) {
        break;
      }
    }
    return rowsBlocked;
  }

  private List<Long> loadEligibleCampaignIds() throws Exception {
    String sql = "SELECT id FROM dbo.pd_campaign "
        + "WHERE dnc_enabled = 1 AND col_date_entered IS NOT NULL "
        + "AND deleted = 0 AND enabled = 1";
    JSONArray rows = dbEngine.executeQuery(sql, null, null, srcUrl, srcUser, srcPass);
    List<Long> campaignIds = new ArrayList<>();
    if (rows == null) {
      return campaignIds;
    }
    for (int i = 0; i < rows.length(); i++) {
      JSONObject row = rows.optJSONObject(i);
      if (row != null && row.has("id") && !row.isNull("id")) {
        campaignIds.add(row.getLong("id"));
      }
    }
    return campaignIds;
  }

  private String readLastWatermark(Long campaignId) {
    try {
      String sql = "SELECT MAX(max_date_entered) AS \"watermark\" FROM dbo.pd_dnc_sync_log "
          + "WHERE campaign_id = " + campaignId + " AND status = N'SUCCESS'";
      JSONArray rows = dbEngine.executeQuery(sql, null, null, srcUrl, srcUser, srcPass);
      if (rows == null || rows.length() == 0) {
        return null;
      }
      JSONObject row = rows.optJSONObject(0);
      if (row == null || !row.has("watermark") || row.isNull("watermark")) {
        return null;
      }
      String watermark = row.get("watermark").toString().trim();
      return watermark.isEmpty() ? null : watermark;
    } catch (Exception e) {
      LOG.error("[DNC-DELTA-SYNC] Watermark read failed; treating as first run. campaignId=" + campaignId, e);
      return null;
    }
  }

  private void writeSyncLog(Long campaignId, String maxDateEntered, Timestamp startedAt,
                            String status, int rowsBlocked, String errorMessage) {
    try {
      dbEngine.executeBatchParametrizedUpdate(
          new ParametrizedQuery(
              "INSERT INTO dbo.pd_dnc_sync_log "
                  + "(campaign_id, max_date_entered, started_at, finished_at, status, rows_blocked, error_message) "
                  + "VALUES (?, ?, ?, SYSDATETIME(), ?, ?, ?)",
              Arrays.<Object>asList(campaignId, maxDateEntered, startedAt, status, rowsBlocked,
                  left(errorMessage, MAX_ERROR_LENGTH))),
          null, srcUrl, srcUser, srcPass);
    } catch (Exception e) {
      LOG.error("[DNC-DELTA-SYNC] Sync log write failed. campaignId=" + campaignId + ", status=" + status, e);
    }
  }

  private Set<Long> resolveActiveDncIds(Long campaignId, Campaign campaign) {
    Set<Long> dncIds = new LinkedHashSet<>();
    List<DNC> campaignDncs = dncService.getDncForCampaign(campaignId);
    if (campaignDncs != null) {
      for (DNC dnc : campaignDncs) {
        if (dnc != null && dnc.getId() != null) {
          dncIds.add(dnc.getId());
        }
      }
    }
    Long tenantId = campaign.getTenant() != null ? campaign.getTenant().getId() : null;
    List<DNC> globalDncs = dncService.getGlobalDnc(true);
    if (globalDncs != null && tenantId != null) {
      for (DNC dnc : globalDncs) {
        if (dnc != null && dnc.getId() != null && dnc.getTenant() != null && tenantId.equals(dnc.getTenant().getId())) {
          dncIds.add(dnc.getId());
        }
      }
    }
    return dncIds;
  }

  private Set<String> findActivePhonesChunked(Set<Long> dncIds, Set<String> phones) {
    if (phones.isEmpty()) {
      return Collections.emptySet();
    }
    Set<String> activePhones = new LinkedHashSet<>();
    for (List<String> chunk : partition(phones, IN_CHUNK_SIZE)) {
      Set<String> chunkResult = dncPhoneNumberService.findActivePhoneNumbersByDncIdsAndPhoneNumbers(dncIds, chunk, new Date());
      if (chunkResult != null) {
        activePhones.addAll(chunkResult);
      }
    }
    return activePhones;
  }

  private Set<String> findActiveCustomersChunked(Set<Long> dncIds, Set<String> customerNos) {
    if (customerNos.isEmpty()) {
      return Collections.emptySet();
    }
    Set<String> activeCustomers = new LinkedHashSet<>();
    for (List<String> chunk : partition(customerNos, IN_CHUNK_SIZE)) {
      Set<String> chunkResult = dncCustomerPhoneService.findActiveCustomerNosByDncIdsAndCustomerNos(dncIds, chunk, new Date());
      if (chunkResult != null) {
        activeCustomers.addAll(chunkResult);
      }
    }
    return activeCustomers;
  }

  private Set<String> extractPhones(JSONObject row, List<PhoneNumberColumn> phoneNumberColumns) {
    Set<String> phones = new LinkedHashSet<>();
    for (PhoneNumberColumn phoneNumberColumn : phoneNumberColumns) {
      String phone = extractValue(row, phoneNumberColumn.getColNumber());
      if (phone != null) {
        phones.add(phone);
      }
    }
    return phones;
  }

  private String extractValue(JSONObject row, String columnName) {
    if (columnName == null || row == null || !row.has(columnName) || row.isNull(columnName)) {
      return null;
    }
    String value = row.get(columnName).toString().trim();
    return value.isEmpty() ? null : value;
  }

  private List<List<String>> partition(Collection<String> values, int chunkSize) {
    List<String> valueList = new ArrayList<>(values);
    List<List<String>> chunks = new ArrayList<>();
    for (int from = 0; from < valueList.size(); from += chunkSize) {
      chunks.add(valueList.subList(from, Math.min(from + chunkSize, valueList.size())));
    }
    return chunks;
  }

  private static String left(String value, int maxLength) {
    if (value == null) {
      return null;
    }
    return value.length() <= maxLength ? value : value.substring(0, maxLength);
  }

  private static String safeMessage(Throwable t) {
    String message = t == null ? "unknown" : t.getMessage();
    return message == null ? t.getClass().getSimpleName() : message.replaceAll("[\\r\\n]+", " ");
  }
}
