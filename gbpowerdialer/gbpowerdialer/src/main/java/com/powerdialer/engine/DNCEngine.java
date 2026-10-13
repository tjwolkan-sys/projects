package com.powerdialer.engine;

import static com.powerdialer.util.Constants.TELEPHONY_RC_ID_FILTERED_BY_DNC;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import java.sql.SQLException;
import javax.annotation.PostConstruct;
import javax.servlet.ServletContext;

import org.hibernate.Hibernate;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import com.powerdialer.engine.db.CampaignDbOperations;
import com.powerdialer.engine.dialer.CampaignContext;
import com.powerdialer.engine.dialer.DialerFacade;
import com.powerdialer.model.CDR;
import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.DNC;
import com.powerdialer.model.DNCPhoneNumber;
import com.powerdialer.model.PhoneNumberColumn;
import com.powerdialer.model.TelephonyResultCode;
import com.powerdialer.model.Tenant;
import com.powerdialer.service.CDRService;
import com.powerdialer.service.CampaignService;
import com.powerdialer.service.DNCCustomerPhoneService;
import com.powerdialer.service.DNCPhoneNumberService;
import com.powerdialer.service.DNCService;
import com.powerdialer.util.Constants;
import com.powerdialer.util.LoggerDecorator;
import com.powerdialer.util.UserPopulationEvaluator;

/**
 * Engine for applying DNC lists to Calls. It store (in memory) DNC info for
 * running campaigns and filter Calls lists before put it into
 * {@link com.powerdialer.engine.dialer.CallProducer}'s queue
 */
@Component("dncEngine")
@Lazy(false)
public class DNCEngine {

  private static final long DNC_CACHE_REFRESH_INTERVAL_MS = 60_000L;
  private static final Long GLOBAL_DNC_CACHE_REFRESH_KEY = Long.MIN_VALUE;

  private LoggerDecorator LOG;
  private Map<Campaign, Map<DNC, Map<String, Long>>> campaignDncMap = new ConcurrentHashMap<>();
  private volatile Map<Tenant, Map<DNC, Map<String, Long>>> globalDncMap = new ConcurrentHashMap<>();
  private Map<Long, Long> dncCacheRefreshTimestamps = new ConcurrentHashMap<>();
  private Set<Campaign> runningCampaigns = Collections.synchronizedSet(new HashSet<>());

  @Autowired
  private DNCService dncService;

  @Autowired
  private DNCPhoneNumberService dncPhoneNumberService;

  @Autowired
  private DNCCustomerPhoneService dncCustomerPhoneService;

  @Autowired
  private CampaignDbOperations campaignDbOperations;

  @Autowired
  private StopConditionChecker stopConditionChecker;

  @Autowired
  private CampaignService campaignService;

  @Autowired
  private LoggerDecorator loggerDecorator;

  @Autowired
  private CDRService cdrService;

  @Autowired
  private ServletContext servletContext;

  @Autowired
  private ApplicationContext applicationContext;

  // DialerFacade -> DNCEngine yönlü bean bağımlılığıyla çevrim oluşmaması
  // için lazy lookup yapılır (DialerFacade.getDncEngine ile aynı pattern).
  private volatile DialerFacade dialerFacade;

  @PostConstruct
  public void init() {
    LOG = loggerDecorator.getLogger(this.getClass());
  }

  /**
   * Add store in memory running campaign and load DNC list for it
   */
  public boolean addRunningCampaign(Campaign campaign) {
    if (runningCampaigns.isEmpty())
      loadGlobalDNCs();
    loadDNCForCampaign(campaign);
    return runningCampaigns.add(campaign);
  }

  /**
   * Load all enabled global DNC lists in memory
   */
  private void loadGlobalDNCs() {
    List<DNC> globalDnc = dncService.getGlobalDnc(true);
    Map<Tenant, Map<DNC, Map<String, Long>>> refreshedGlobalDncMap = new ConcurrentHashMap<>();
    if (!CollectionUtils.isEmpty(globalDnc)) {
      for (DNC dnc : globalDnc) {
        putTenantDNC(refreshedGlobalDncMap, dnc);
      }
    }
    globalDncMap = refreshedGlobalDncMap;
    markDncCacheRefreshed(GLOBAL_DNC_CACHE_REFRESH_KEY);
  }

  /**
   * Load all campaign DNCs into memory (if dnc enabled for campaign)
   * @param campaign
   */
  public void loadDNCForCampaign(Campaign campaign) {
    LOG.info("Loading DNC lists for " + campaign, Constants.LOG_MODULE.CAMPAIGN, campaign);
    if (campaign.getDncEnabled()) {
      Map<DNC, Map<String, Long>> newCampaignDncNumbers = new ConcurrentHashMap<>();
      List<DNC> dncByCampaign = dncService.getDncForCampaign(campaign.getId());
      for (DNC dnc : dncByCampaign) {
        Map<String, Long> dncPhoneNumbers = getPhoneNumbersWithExpire(dnc);
        newCampaignDncNumbers.put(dnc, dncPhoneNumbers);
      }
      campaignDncMap.put(campaign, newCampaignDncNumbers);
      markDncCacheRefreshed(campaign.getId());
    } else {
      campaignDncMap.remove(campaign);
      if (campaign.getId() != null) {
        dncCacheRefreshTimestamps.remove(campaign.getId());
      }
    }
  }

  /**
   * Put dnc with key as tenant
   * @param dnc
   */
  private void putTenantDNC(DNC dnc) {
    putTenantDNC(globalDncMap, dnc);
  }

  private void putTenantDNC(Map<Tenant, Map<DNC, Map<String, Long>>> targetGlobalDncMap, DNC dnc) {
    Map<DNC, Map<String, Long>> tenantDncMap = targetGlobalDncMap.computeIfAbsent(dnc.getTenant(), k -> new ConcurrentHashMap<>());
    tenantDncMap.put(dnc, getPhoneNumbersWithExpire(dnc));
  }

  /**
   * Builds a map of active phone numbers with their effective expiration timestamps for the given DNC.
   * Disabled or deleted phone numbers are excluded. CUSTOMER tipli marker
   * kayıtları telefon cache'ine alınmaz; aksi hâlde customer_no değeri
   * telefon numarası gibi eşleşip yanlış çağrı engeller.
   */
  private Map<String, Long> getPhoneNumbersWithExpire(DNC dnc) {
    Map<String, Long> map = new ConcurrentHashMap<>();
    List<DNCPhoneNumber> numbers = dncPhoneNumberService.getByListId(dnc.getId());
    if (numbers == null)
      return map;
    for (DNCPhoneNumber pn : numbers) {
      if (!pn.isEnabled() || pn.isDeleted())
        continue;
      if (pn.getPhoneNumber() == null)
        continue;

      map.put(pn.getPhoneNumber(), resolveEffectiveExpireDate(pn, dnc));
    }
    return map;
  }

  /**
   * Remove running campaign and clear it's DNC list
   */
  public boolean removeRunningCampaign(Campaign campaign) {
    boolean removeResult = runningCampaigns.remove(campaign);
    clearDNCForCampaign(campaign);
    if (runningCampaigns.isEmpty())
      clearGlobalDNCs();
    return removeResult;
  }

  /**
   * Remove DNC for specified campaign
   * @param campaign
   */
  private void clearDNCForCampaign(Campaign campaign) {
    campaignDncMap.remove(campaign);
    if (campaign.getId() != null) {
      dncCacheRefreshTimestamps.remove(campaign.getId());
    }
  }

  /**
   * Free global DNC cache
   */
  private void clearGlobalDNCs() {
    globalDncMap.clear();
    dncCacheRefreshTimestamps.remove(GLOBAL_DNC_CACHE_REFRESH_KEY);
  }

  /**
   * Update {@link DNC} maps
   */
  @Async
  public void updateDnc(Long dncId) {
    DNC dnc = dncService.getWithInitializedPhoneNumbers(dncId);
    updateGlobalDncMap(dnc);
    updateCampaignDncMap(dnc);
  }

  /**
   * Update specified DNC in global DNC map
   */
  private void updateGlobalDncMap(DNC dnc) {
    LOG.info("Removing DNC from the global cache: " + dnc);
    globalDncMap.forEach((tenant, dncMap) -> {
      if (dnc.isDeleted() || !dnc.isEnabled() || !dnc.getGlobal() || !dnc.getTenant().equals(tenant)) {
        dncMap.remove(dnc);
      }
    });
    if (!dnc.isDeleted() && dnc.isEnabled() && dnc.getGlobal()) {
      LOG.info("Adding DNC to the global cache: " + dnc);
      putTenantDNC(dnc);
    }
  }

  /**
   * Update specified DNC in campaign DNC map
   */
  private void updateCampaignDncMap(DNC dnc) {
    LOG.info("Updating DNC in the campaign cache: " + dnc);
    Iterator<Map.Entry<Campaign, Map<DNC, Map<String, Long>>>> campaignMapIterator = campaignDncMap.entrySet().iterator();
    while (campaignMapIterator.hasNext()) {
      Map.Entry<Campaign, Map<DNC, Map<String, Long>>> campaignDncMapEntry = campaignMapIterator.next();
      Campaign campaign = campaignDncMapEntry.getKey();
      Map<DNC, Map<String, Long>> dncCaches = campaignDncMapEntry.getValue();
      if (dncCaches != null) {
        updateDncForCampaign(campaign, dnc);
      } else {
        campaignMapIterator.remove();
      }
    }
  }

  /**
   * Update DNC list for campaign
   * @param campaign
   * @param dncToUpdate DNC which need to be updated
   */
  private void updateDncForCampaign(Campaign campaign, DNC dncToUpdate) {
    if (dncToUpdate.isEnabled() && !dncToUpdate.isDeleted()) {
      loadDNCForCampaign(campaign);
    } else {
      Map<DNC, Map<String, Long>> dncSetMap = campaignDncMap.get(campaign);
      if (dncSetMap != null) {
        dncSetMap.remove(dncToUpdate);
      }
    }
  }

  /**
   * Update one {@link DNCPhoneNumber} in specified {@link DNC}
   */
  @Async
  public void updatePhoneNumberInDnc(DNCPhoneNumber dncPhoneNumber) {
    if (dncPhoneNumber == null)
      return;
    DNC dnc = Hibernate.isInitialized(dncPhoneNumber.getDncList()) && dncPhoneNumber.getDncList() != null
        && Hibernate.isInitialized(dncPhoneNumber.getDncList().getTenant()) ? dncPhoneNumber.getDncList()
                                                                            : dncService.getDNCByDncPhoneNumberIdWithInitializedTenant(dncPhoneNumber.getId());
    if (dnc == null)
      return;
    updateCampaignDncPhoneNumber(dncPhoneNumber, dnc);
    updateGlobalDncPhoneNumber(dncPhoneNumber, dnc);
  }

  /**
   * Update one {@link DNCPhoneNumber} in specified for campaign {@link DNC}
   * @return true if any DNC was updated
   */
  private boolean updateCampaignDncPhoneNumber(DNCPhoneNumber dncPhoneNumber, DNC dnc) {
    boolean updatedFlag = false;
    for (Map<DNC, Map<String, Long>> dncSetMap : campaignDncMap.values()) {
      Map<String, Long> campaignPhoneNumbers = dncSetMap.get(dnc);
      if (campaignPhoneNumbers != null) {
        updatePhoneNumber(campaignPhoneNumbers, dncPhoneNumber, dnc);
        updatedFlag = true;
      }
    }
    return updatedFlag;
  }

  /**
   * Update one {@link DNCPhoneNumber} in specified for global {@link DNC}
   * @return true if any DNC was updated
   */
  private boolean updateGlobalDncPhoneNumber(DNCPhoneNumber dncPhoneNumber, DNC dnc) {
    if (dnc == null)
      return false;
    boolean updatedFlag = false;
    Map<DNC, Map<String, Long>> tenantDncMap = globalDncMap.get(dnc.getTenant());
    if (tenantDncMap != null) {
      Map<String, Long> tenantDncPhoneNumbers = tenantDncMap.get(dnc);
      if (tenantDncPhoneNumbers != null) {
        updatePhoneNumber(tenantDncPhoneNumbers, dncPhoneNumber, dnc);
        updatedFlag = true;
      }
    }
    return updatedFlag;
  }

  /**
   * Update specified dncPhoneNumber in numbers set. CUSTOMER tipli marker
   * kayıtları telefon cache'ini etkilemez; null phone_number ile
   * ConcurrentHashMap NPE riski de bu guard ile engellenir.
   */
  private void updatePhoneNumber(Map<String, Long> phoneNumbers, DNCPhoneNumber dncPnToUpdate, DNC dnc) {
    if (dncPnToUpdate.getPhoneNumber() == null) {
      return;
    }
    if (dncPnToUpdate.isEnabled() && !dncPnToUpdate.isDeleted()) {
      phoneNumbers.put(dncPnToUpdate.getPhoneNumber(), resolveEffectiveExpireDate(dncPnToUpdate, dnc));
    } else {
      phoneNumbers.remove(dncPnToUpdate.getPhoneNumber());
    }
  }

  private long resolveEffectiveExpireDate(DNCPhoneNumber dncPhoneNumber, DNC dnc) {
    Date expireDate = dncPhoneNumber.getExpiredDate();
    if (expireDate == null && dnc != null) {
      expireDate = dnc.getExpireDate();
    }
    return expireDate != null ? expireDate.getTime() : Long.MAX_VALUE;
  }

  /**
   * Filter {@link Call} list with {@link DNC} for specified {@link Campaign}
   */
  public void applyDncToCalls(Campaign campaign, Collection<Call> calls) {
    if (calls.isEmpty())
      return;
    refreshStaleDncCaches(campaign);
    DncLookupSnapshot dncLookupSnapshot = collectDncLookupSnapshot(campaign);
    Set<String> candidateDncNumbers = collectCandidateDncNumbers(calls, dncLookupSnapshot.phoneNumbers);
    Set<String> activeDncNumbers = revalidateActiveDncNumbers(dncLookupSnapshot.dncListIds, candidateDncNumbers);
    pruneInactiveDncNumbers(calls, candidateDncNumbers, activeDncNumbers);
    try {
      filterCallsByDncs(campaign, calls, activeDncNumbers);
    } catch (SQLException e) {
      LOG.error("", e);
    }
    applyCustomerDncToCalls(campaign, calls, dncLookupSnapshot.dncListIds);
  }

  /**
   * Müşteri bazlı DNC'nin fetch-time emniyeti (müşteri kararı 03.06.2026):
   * aynı customer_no ile sonradan yüklenen data yeni bir telefonla gelirse
   * delta-sync cron'u beklenmeden producer fetch'inde yakalanır. Sayfadaki
   * distinct customer_no'lar TEK batch sorguyla aktif CUSTOMER marker'lara
   * karşı kontrol edilir; eşleşen çağrılar telefon filtresiyle aynı
   * semantikle (TRC=76 + CDR) düşürülür.
   *
   * Performans: customer_no mapping'i olmayan kampanyada sıfır maliyet;
   * mapping'li kampanyada fetch sayfası başına 1 indexed IN-sorgusu (call
   * başına değil — mevcut DNC revalidation/JIT expire ile aynı sınıf).
   * Sorgu hatasında fail-open: çağrılar düşürülmez (telefon filtresi
   * birincil koruma, delta-sync gece telafi eder).
   */
  private void applyCustomerDncToCalls(Campaign campaign, Collection<Call> calls, Set<Long> dncListIds) {
    if (calls.isEmpty() || CollectionUtils.isEmpty(dncListIds)) {
      return;
    }
    String customerNoColumn = campaign.getColResultCustomerNo();
    if (customerNoColumn == null || customerNoColumn.trim().isEmpty()) {
      return;
    }

    Set<String> candidateCustomerNos = new HashSet<>();
    for (Call call : calls) {
      String customerNo = extractCustomerNo(call, customerNoColumn);
      if (customerNo != null) {
        candidateCustomerNos.add(customerNo);
      }
    }
    if (candidateCustomerNos.isEmpty()) {
      return;
    }

    Set<String> activeCustomerNos;
    try {
      activeCustomerNos = dncCustomerPhoneService.findActiveCustomerNosByDncIdsAndCustomerNos(
          dncListIds, candidateCustomerNos, new Date());
    } catch (Exception e) {
      LOG.error("Cannot validate customer DNC markers against DB. Customer-scope fetch filter skipped; "
          + "delta-sync will compensate.", e);
      return;
    }
    if (CollectionUtils.isEmpty(activeCustomerNos)) {
      return;
    }

    Set<Call> filteredByCustomerDnc = new HashSet<>();
    Iterator<Call> callsIterator = calls.iterator();
    while (callsIterator.hasNext()) {
      Call call = callsIterator.next();
      String customerNo = extractCustomerNo(call, customerNoColumn);
      if (customerNo != null && activeCustomerNos.contains(customerNo)) {
        LOG.info("Call has filtered by customer DNC marker: " + call);
        filteredByCustomerDnc.add(call);
        campaign.incrementTelephonyResultCode((long) TELEPHONY_RC_ID_FILTERED_BY_DNC);
        call.getCdr().setCallEndTime(new Date());
        callsIterator.remove();
      }
    }
    if (CollectionUtils.isEmpty(filteredByCustomerDnc)) {
      return;
    }
    try {
      campaignDbOperations.setFilteredByDncListStatus(campaign, filteredByCustomerDnc);
    } catch (SQLException e) {
      LOG.error("Could not persist customer DNC filtered status", e);
    }
    checkStopCondition(campaign);
    saveCDR(filteredByCustomerDnc);
  }

  private String extractCustomerNo(Call call, String customerNoColumn) {
    if (call.getRawData() == null || !call.getRawData().has(customerNoColumn)) {
      return null;
    }
    Object value = call.getRawData().get(customerNoColumn);
    if (value == null || org.json.JSONObject.NULL.equals(value)) {
      return null;
    }
    String customerNo = value.toString().trim();
    return customerNo.isEmpty() ? null : customerNo;
  }

  private void refreshStaleDncCaches(Campaign campaign) {
    long now = System.currentTimeMillis();
    if (campaign != null && campaign.getId() != null && shouldRefreshDncCache(campaign.getId(), now)) {
      try {
        loadDNCForCampaign(campaign);
      } catch (Exception e) {
        LOG.error("Cannot refresh campaign DNC cache for " + campaign + ". Current cache will be used.", e);
      }
    }
    if (shouldRefreshDncCache(GLOBAL_DNC_CACHE_REFRESH_KEY, now)) {
      try {
        loadGlobalDNCs();
      } catch (Exception e) {
        LOG.error("Cannot refresh global DNC cache. Current cache will be used.", e);
      }
    }
  }

  private boolean shouldRefreshDncCache(Long cacheKey, long now) {
    Long lastRefresh = dncCacheRefreshTimestamps.get(cacheKey);
    if (lastRefresh != null && now - lastRefresh < DNC_CACHE_REFRESH_INTERVAL_MS) {
      return false;
    }
    dncCacheRefreshTimestamps.put(cacheKey, now);
    return true;
  }

  private void markDncCacheRefreshed(Long cacheKey) {
    if (cacheKey != null) {
      dncCacheRefreshTimestamps.put(cacheKey, System.currentTimeMillis());
    }
  }

  private DncLookupSnapshot collectDncLookupSnapshot(Campaign campaign) {
    DncLookupSnapshot snapshot = new DncLookupSnapshot();
    Map<DNC, Map<String, Long>> campaignDncCaches = campaignDncMap.get(campaign);
    if (!CollectionUtils.isEmpty(campaignDncCaches)) {
      collectValidDncNumbers(campaignDncCaches.entrySet().iterator(), snapshot);
    }
    if (!CollectionUtils.isEmpty(globalDncMap)) {
      Map<DNC, Map<String, Long>> tenantDncMap = globalDncMap.get(campaign.getTenant());
      if (!CollectionUtils.isEmpty(tenantDncMap))
        collectValidDncNumbers(tenantDncMap.entrySet().iterator(), snapshot);
    }
    return snapshot;
  }

  private Set<String> collectCandidateDncNumbers(Collection<Call> calls, Set<String> cachedDncPhoneNumbers) {
    Set<String> candidateDncNumbers = new HashSet<>();
    for (Call call : calls) {
      String calledNumber = call.getCalledNumber();
      if (calledNumber == null) {
        continue;
      }
      if (cachedDncPhoneNumbers.contains(calledNumber) || call.getDNCNumbers().contains(calledNumber)) {
        candidateDncNumbers.add(calledNumber);
      }
    }
    return candidateDncNumbers;
  }

  private Set<String> revalidateActiveDncNumbers(Set<Long> dncListIds, Set<String> candidateDncNumbers) {
    if (CollectionUtils.isEmpty(candidateDncNumbers) || CollectionUtils.isEmpty(dncListIds)) {
      return Collections.emptySet();
    }
    try {
      Set<String> activeDncNumbers = dncPhoneNumberService.findActivePhoneNumbersByDncIdsAndPhoneNumbers(
          dncListIds, candidateDncNumbers, new Date());
      return activeDncNumbers == null ? Collections.emptySet() : activeDncNumbers;
    } catch (Exception e) {
      LOG.error("Cannot revalidate DNC candidates against DB. Cached DNC decision will be used.", e);
      return new HashSet<>(candidateDncNumbers);
    }
  }

  private void pruneInactiveDncNumbers(Collection<Call> calls, Set<String> candidateDncNumbers, Set<String> activeDncNumbers) {
    if (CollectionUtils.isEmpty(candidateDncNumbers)) {
      return;
    }
    Set<String> inactiveDncNumbers = new HashSet<>(candidateDncNumbers);
    inactiveDncNumbers.removeAll(activeDncNumbers);
    if (inactiveDncNumbers.isEmpty()) {
      return;
    }

    for (Call call : calls) {
      Set<String> dncNumbers = call.getDNCNumbers();
      if (!CollectionUtils.isEmpty(dncNumbers) && !Collections.disjoint(dncNumbers, inactiveDncNumbers)) {
        Set<String> prunedDncNumbers = new HashSet<>(dncNumbers);
        prunedDncNumbers.removeAll(inactiveDncNumbers);
        call.setDNCNumbers(prunedDncNumbers);
      }
    }
    pruneInactiveDncNumbersFromCache(inactiveDncNumbers);
  }

  private void pruneInactiveDncNumbersFromCache(Set<String> inactiveDncNumbers) {
    for (Map<DNC, Map<String, Long>> dncCaches : campaignDncMap.values()) {
      pruneInactiveDncNumbersFromDncCaches(dncCaches, inactiveDncNumbers);
    }
    for (Map<DNC, Map<String, Long>> dncCaches : globalDncMap.values()) {
      pruneInactiveDncNumbersFromDncCaches(dncCaches, inactiveDncNumbers);
    }
  }

  private void pruneInactiveDncNumbersFromDncCaches(Map<DNC, Map<String, Long>> dncCaches, Set<String> inactiveDncNumbers) {
    if (CollectionUtils.isEmpty(dncCaches)) {
      return;
    }
    for (Map<String, Long> numbers : dncCaches.values()) {
      if (numbers != null) {
        for (String inactiveDncNumber : inactiveDncNumbers) {
          numbers.remove(inactiveDncNumber);
        }
      }
    }
  }

  /**
   * Collects valid (non-expired) DNC phone numbers from the cache.
   * Removes expired phone numbers during iteration.
   */
  private void collectValidDncNumbers(Iterator<Map.Entry<DNC, Map<String, Long>>> dncCacheIterator, DncLookupSnapshot result) {
    long now = System.currentTimeMillis();

    while (dncCacheIterator.hasNext()) {
      Map.Entry<DNC, Map<String, Long>> entry = dncCacheIterator.next();
      DNC dnc = entry.getKey();
      if (dnc != null && dnc.getId() != null) {
        result.dncListIds.add(dnc.getId());
      }
      Map<String, Long> numbers = entry.getValue();

      numbers.entrySet().removeIf(e -> e.getValue() < now);

      result.phoneNumbers.addAll(numbers.keySet());
    }
  }

  /**
   * Set telephony result code for rows that filtered by dnc list and delete
   * from origin collection
   * @param calls
   */
  private void filterCallsByDncs(Campaign campaign, Collection<Call> calls, Set<String> dncPhoneNumbers) throws SQLException {
    Set<Call> filteredByDnc = new HashSet<>();
    Iterator<Call> callsIterator = calls.iterator();
    while (callsIterator.hasNext()) {
      Call call = callsIterator.next();
      if (isCalledNumberInDNC(call, dncPhoneNumbers)) {
        LOG.info("Call has filtered by DNC: " + call);
        filteredByDnc.add(call);
        campaign.incrementTelephonyResultCode((long) TELEPHONY_RC_ID_FILTERED_BY_DNC);
        call.getCdr().setCallEndTime(new Date());
        callsIterator.remove();
      }
    }
    if (!CollectionUtils.isEmpty(filteredByDnc)) {
      campaignDbOperations.setFilteredByDncListStatus(campaign, filteredByDnc);
      checkStopCondition(campaign);
      saveCDR(filteredByDnc);
    }
  }

  private void saveCDR(Collection<Call> calls) {
    TelephonyResultCode DNCResultCode = new TelephonyResultCode();
    DNCResultCode.setId((long) TELEPHONY_RC_ID_FILTERED_BY_DNC);
    DNCResultCode.setCode(TELEPHONY_RC_ID_FILTERED_BY_DNC);
    DNCResultCode.setName("TELEPHONY_RC_ID_FILTERED_BY_DNC");
    for (Call call : calls) {
      call.setTelephonyResultCode(DNCResultCode);
      CDR cdr = call.getCdr();
      cdr.setTelephonyRC(DNCResultCode);
      // Apply all CDR user ownership fields in a single operation
      // DNC filtered calls always result in null for all user fields
      cdr.setUserResultCodeId(call.getUserResultCode() != null ? call.getUserResultCode().getId() : null);

        UserPopulationEvaluator.populateCdrUserOwnership(call);


      cdr.setRcSequence(call.getResultCodeSequence());
      cdr.setFilter(call.getFilter());
      cdr.setAbandoned(call.isAbandoned());
      PhoneNumberColumn actualPhoneNumberColumn = call.getActualPhoneNumberColumn();
      int phoneNumberColumnOrder = call.getCampaign().getPhoneNumberColumnOrder(actualPhoneNumberColumn);
      cdr.setContactNumberOrder(phoneNumberColumnOrder);
      cdr.setContactNumberColumn(actualPhoneNumberColumn.getColNumber());
      try {
        cdrService.save(cdr);
      } catch (Exception e) {
        LOG.error("Cannot save " + cdr, e);
      }
    }
  }

  private boolean isCalledNumberInDNC(Call call, Set<String> dncPhoneNumbers) {
    return call.getDNCNumbers().contains(call.getCalledNumber()) || dncPhoneNumbers.contains(call.getCalledNumber());
  }

  /**
   * Müşteri bazlı DNC tetiklendiği anda (hot-drop, Edge Case 2): verilen DNC
   * listesini fiilen kullanan kampanyaların producer dataQueue ve consumer
   * callQueue kuyruklarında bekleyen eşleşen çağrıları originate edilmeden
   * düşürür ve mevcut DNC filtre semantiğiyle (TRC=76 + CDR + stop-condition)
   * kapatır. is_fetched flag'i RESETLENMEZ; satırlar setFilteredByDncListStatus
   * ile kapatılır.
   *
   * Performans: nadir event (DNC tetiklenmesi) başına bounded kuyruk taraması
   * (dataQueue ≤ cacheLimit, callQueue ≤ concurrentLines); dial hot path'ine
   * maliyet eklemez.
   */
  public void dropQueuedCallsForNumbers(DNC dnc, Set<String> blockedNumbers) {
    if (dnc == null || CollectionUtils.isEmpty(blockedNumbers)) {
      return;
    }
    DialerFacade facade = getDialerFacade();
    if (facade == null) {
      return;
    }
    for (CampaignContext campaignContext : facade.getCampaignContexts().values()) {
      Campaign campaign = campaignContext.getCampaign();
      if (campaign == null || !campaignUsesDnc(campaign, dnc)) {
        continue;
      }
      Set<Call> droppedCalls = new HashSet<>();
      if (campaignContext.getCallProducer() != null) {
        collectAndRemoveMatchingCalls(campaignContext.getCallProducer().getDataQueue(), blockedNumbers, droppedCalls);
      }
      if (campaignContext.getCallConsumer() != null) {
        collectAndRemoveMatchingCalls(campaignContext.getCallConsumer().getCallQueue(), blockedNumbers, droppedCalls);
      }
      if (droppedCalls.isEmpty()) {
        continue;
      }
      LOG.info("Dropped " + droppedCalls.size() + " queued call(s) by DNC trigger for " + campaign,
          Constants.LOG_MODULE.CAMPAIGN, campaign);
      for (Call droppedCall : droppedCalls) {
        campaign.incrementTelephonyResultCode((long) TELEPHONY_RC_ID_FILTERED_BY_DNC);
        if (droppedCall.getCdr() != null) {
          droppedCall.getCdr().setCallEndTime(new Date());
        }
      }
      try {
        campaignDbOperations.setFilteredByDncListStatus(campaign, droppedCalls);
      } catch (SQLException e) {
        LOG.error("Could not persist DNC filtered status for dropped queued calls of " + campaign, e);
      }
      checkStopCondition(campaign);
      saveCDR(droppedCalls);
    }
  }

  /**
   * Kuyruktaki eşleşen çağrıları toplayıp remove(Object) ile çıkarır.
   * remove yarış-güvenlidir: consumer/originator çağrıyı bu arada aldıysa
   * false döner ve çağrıya dokunulmaz (originate edilmiş çağrı kapsam dışı).
   */
  private void collectAndRemoveMatchingCalls(Collection<Call> queue, Set<String> blockedNumbers, Set<Call> droppedCalls) {
    if (queue == null) {
      return;
    }
    List<Call> matchingCalls = new ArrayList<>();
    for (Call call : queue) {
      if (call != null && call.getCalledNumber() != null && blockedNumbers.contains(call.getCalledNumber())) {
        matchingCalls.add(call);
      }
    }
    for (Call matchingCall : matchingCalls) {
      if (queue.remove(matchingCall)) {
        droppedCalls.add(matchingCall);
      }
    }
  }

  /**
   * Kampanyanın verilen DNC listesini fiilen kullanıp kullanmadığını
   * in-memory cache üzerinden belirler (lazy entity ilişkisine dokunmadan):
   * kampanya cache'inde veya tenant'ın global cache'inde DNC anahtarı varsa
   * kampanya bu listeyi kullanıyordur.
   */
  private boolean campaignUsesDnc(Campaign campaign, DNC dnc) {
    Map<DNC, Map<String, Long>> campaignDncCaches = campaignDncMap.get(campaign);
    if (campaignDncCaches != null && campaignDncCaches.containsKey(dnc)) {
      return true;
    }
    if (campaign.getTenant() == null) {
      return false;
    }
    Map<DNC, Map<String, Long>> tenantDncCaches = globalDncMap.get(campaign.getTenant());
    return tenantDncCaches != null && tenantDncCaches.containsKey(dnc);
  }

  private DialerFacade getDialerFacade() {
    if (dialerFacade == null) {
      try {
        dialerFacade = applicationContext.getBean(DialerFacade.class);
      } catch (Exception e) {
        LOG.error("DialerFacade could not be resolved for DNC queue drop", e);
      }
    }
    return dialerFacade;
  }

  private static class DncLookupSnapshot {
    private final Set<String> phoneNumbers = new HashSet<>();
    private final Set<Long> dncListIds = new HashSet<>();
  }

  private void checkStopCondition(Campaign campaign) {
    if (stopConditionChecker.isStopConditionReached(campaign)) {
      LOG.info(campaign + " has reached stop condition", Constants.LOG_MODULE.CAMPAIGN, campaign);
      campaignService.actionAsync(campaign.getId(), Constants.CAMPAIGN_ACTION.STOP_REGULAR, Constants.CAMPAIGN_STOP_TYPE.STOP_CONDITION);
    }
  }
}
