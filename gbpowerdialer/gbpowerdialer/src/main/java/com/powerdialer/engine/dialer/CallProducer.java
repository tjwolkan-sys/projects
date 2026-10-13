package com.powerdialer.engine.dialer;

import static com.powerdialer.util.Constants.CDR_OUTDIALING_TYPE.IMMEDIATE_RETRY;
import static com.powerdialer.util.Constants.CDR_OUTDIALING_TYPE.SCHEDULED_RETRY;
import static com.powerdialer.util.Constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;

import com.powerdialer.model.CampaignFiltersStats;
import com.powerdialer.model.Filter;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import com.powerdialer.engine.acd.ACDEngine;
import com.powerdialer.engine.asterisk.ami.ManagerConnectionComposition;
import com.powerdialer.engine.asterisk.ami.ManagerConnectionDecorator;
import com.powerdialer.engine.db.CampaignDbOperations;
import com.powerdialer.model.AttachedData;
import com.powerdialer.model.CDR;
import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.PhoneNumberColumn;
import com.powerdialer.model.ResultCodeSchedule;
import com.powerdialer.model.ResultCodeScheduleAdvanced;
import com.powerdialer.model.User;
import com.powerdialer.model.base.BaseEntity;
import com.powerdialer.util.Constants;
import com.powerdialer.util.Constants.ATTACHED_DATA_OBJECT;
import com.powerdialer.util.Constants.CAMPAIGN_STATE;
import com.powerdialer.util.Constants.LOG_MODULE;
import com.powerdialer.util.Constants.MANAGER_ACTION_VARIABLE;
import com.powerdialer.util.LoggerDecorator;
import com.powerdialer.util.Utils;

/**
 * Producer that fetches data per campaign from db according to given period.
 * Data is fetched by load factor calculation that determines load of queue.
 */
public class CallProducer extends CallProducerConsumerTemplate {

    private static final int DEFAULT_DATA_FETCH_PERIOD = 5000;
    private static final double DATA_LOAD_FACTOR = 0.5;
    private static final int MAX_LOGGED_SKIPPED_KEYS = 20;

    private final LoggerDecorator LOG;
    private int currentFetchInterval;
    private final Set<String> callsUniqueKeys = new HashSet<>();
    // FIX-330538: fetch anında dataQueue ve consumer callQueue'sunda canlı bir Call ile temsil
    // edilen uniqueKey'ler. Her tick başında runtime state'ten yeniden türetilir; add/remove'lu
    // kalıcı bir set tutulmaz (release noktası kaçırma = satırın bir daha hiç aranamaması riski).
    // callsUniqueKeys ile aynı finally'de temizlenir.
    private final Set<String> activeUniqueKeys = new HashSet<>();
    private final List<String> skippedActiveUniqueKeys = new ArrayList<>();
    private final Comparator<Call> callComparator = CallTypeComparator.getInstance()
            .thenComparing(CallbackCallsIsFetchedComparator.getInstance())
            .thenComparing(Call::getProductionNumber);
    private final CampaignFilterStatsCalculator campaignFilterStatsCalculator;

    private volatile long productionCounter;
    private CampaignFiltersStats lastCampaignFiltersStats;

    public CallProducer(CampaignContext campaignContext, CampaignFilterStatsCalculator campaignFilterStatsCalculator) {
        super(campaignContext);
        this.dataQueue = new PriorityBlockingQueue<>(campaignContext.getCampaign().getCacheLimit() > 0 ? campaignContext.getCampaign().getCacheLimit() : 1, callComparator);
        this.LOG = campaignContext.getDialerFacade().getLoggerDecorator().getLogger(CallProducer.class);
        this.campaignFilterStatsCalculator = campaignFilterStatsCalculator;
    }

    @Override
    public void run() {
        scheduledFuture = createScheduledFuture(DEFAULT_DATA_FETCH_PERIOD);
        currentFetchInterval = DEFAULT_DATA_FETCH_PERIOD;
    }

    private ScheduledFuture<?> createScheduledFuture(Integer fetchInterval) {
        return scheduledExecutorService.scheduleAtFixedRate(this::fetchCalls, 0, fetchInterval, TimeUnit.MILLISECONDS);
    }

    /**
     * Executes the core logic for fetching call data for the managed campaign.
     * <p>
     * This method acts as the main scheduling entry point, checking the campaign's status and
     * current data queue capacity to determine the next action:
     * <ul>
     * <li>If the campaign is **running** and the data queue has **enough space** (below the low load factor),
     * it triggers the {@link #processDataFetchAndRefill(Campaign)} flow to pull new fresh calls,
     * retries, and callbacks from the database.</li>
     * <li>If the campaign is **running** but the data queue is **full** (or above the low load factor),
     * it triggers {@link #processCallbackManagementIfQueueFull(Campaign)} to manage
     * callback prioritization and queue cleaning, ensuring callbacks are not blocked.</li>
     * </ul>
     * The process is wrapped in a robust exception handler to log any errors and prevent the producer from stopping.
     *
     * @implNote This method implicitly relies on {@link #isCampaignRunning(Campaign)}, {@link #enoughSpaceInDataQueue()},
     * {@link #processDataFetchAndRefill(Campaign)}, {@link #processCallbackManagementIfQueueFull(Campaign)},
     * and {@link #logFetchError(Exception, Campaign)} for its clean operation.
     */
    void fetchCalls() {
        Campaign campaign = campaignContext.getCampaign();
        if (!isCampaignRunning(campaign)) {
            return;
        }
        try {
            refreshActiveUniqueKeys();
            if (enoughSpaceInDataQueue()) {
                processDataFetchAndRefill(campaign);
            } else {
                processCallbackManagementIfQueueFull(campaign);
            }
            logSkippedActiveUniqueKeysIfAny(campaign);
        } catch (Exception e) {
            logFetchError(e, campaign);
        } finally {
            // callsUniqueKeys yalnız tek fetch tick'i içinde aynı kaydın iki kez Call'a
            // dönüşmesini engeller ve her tick sonunda sıfırlanmalıdır. Reset eskiden yalnız
            // finalizeFetchCycle (mutlu yol) içinde yapılıyordu; cycle ortasında bir DB hatası
            // (ör. campaign DB bağlantı kaybında reserveRowsForFetch'ün patlaması)
            // finalize'a ulaşılmasını engelleyip key'leri sızdırıyordu. fetched_flag set
            // edilemediği için tekrar SELECT edilen aynı satırlar bir sonraki tick'te "already
            // created" diye elenir, cache bir daha dolmaz ve üretici DB toparlansa bile yalnız
            // stop/start ile düzelir. finally ile her tick'te garanti reset bu sızıntıyı kapatır.
            callsUniqueKeys.clear();
            activeUniqueKeys.clear();
            skippedActiveUniqueKeys.clear();
        }
    }

    /**
     * FIX-330538: dataQueue (cache) ve consumer callQueue'sunda o anda canlı olan Call'ların
     * uniqueKey'lerini toplar. DB'deki {@code is_fetched} rezervasyonu herhangi bir nedenle
     * tutmadığında aynı satır bir sonraki tick'te yeniden SELECT edilir; bu set o satırın ikinci
     * kez Call'a dönüşüp iki kez aranmasını engeller. Set her tick'te sıfırdan türetilir: reload,
     * stop veya sonuç yazımı kopyayı kuyruktan çıkardığı anda satır kendiliğinden yeniden fetch
     * edilebilir olur; ayrı bir release noktası yoktur, sızamaz.
     *
     * <p>Maliyet: producer tick'i başına (5 sn) en fazla {@code cacheLimit + concurrentLines}
     * eleman üzerinde tek geçiş; call/event hot path'inde değildir. Her iki kuyruğun iterator'ı
     * weakly consistent'tır, kilit almaz ve eşzamanlı add/remove ile exception üretmez.
     * {@code Campaign.originatedCalls} bilinçli olarak kapsam dışıdır: expiry'siz bir cache'tir ve
     * oraya takılan tek bir çağrı satırı kalıcı olarak fetch edilemez yapardı.</p>
     */
    private void refreshActiveUniqueKeys() {
        activeUniqueKeys.clear();
        collectUniqueKeys(dataQueue);
        CallConsumer callConsumer = campaignContext.getCallConsumer();
        if (callConsumer != null) {
            collectUniqueKeys(callConsumer.getCallQueue());
        }
        collectInFlightUniqueKeys();
    }

    /**
     * FIX-333599: kuyrukları terk etmiş ama görüşmesi süren (originate edilmiş, URC bekleyen,
     * kurtarma/orphan) Call'ların uniqueKey'leri de guard'a eklenir. Vaka (02.10.2026, contact
     * 326361): rezervasyonu başka kampanyanın cleanup'ında silinen satır, ilk çağrı çalarken yeniden
     * çekildi; bayat kopya dial/answer anında önceki çağrının callback/URC'sini silerek satırı her
     * seferinde yeniden Fresh yaptı ve aynı müşteri 11 dakikada 6 kez arandı. Kaynak bağlantı
     * cache'leridir (hepsinin expiry'si var); expiry'siz {@code Campaign.originatedCalls} bilinçli
     * olarak yine kapsam dışıdır.
     */
    private void collectInFlightUniqueKeys() {
        Long campaignId = campaignContext.getCampaign().getId();
        ManagerConnectionComposition composition = campaignContext.getDialerFacade().getConnectionComposition();
        if (campaignId == null || composition == null || composition.getOpenedConnections() == null) {
            return;
        }
        for (ManagerConnectionDecorator connection : composition.getOpenedConnections().values()) {
            if (connection != null) {
                connection.collectInFlightUniqueKeys(campaignId, activeUniqueKeys);
            }
        }
    }

    private void collectUniqueKeys(Collection<Call> calls) {
        if (calls == null) {
            return;
        }
        for (Call call : calls) {
            String uniqueKey = call.getUniqueKey();
            if (uniqueKey != null) {
                activeUniqueKeys.add(uniqueKey);
            }
        }
    }

    /**
     * Guard'ın devreye girdiği her tick için TEK satır (ve tek ActionLog kaydı) basar: bu olay
     * "DB rezervasyonu kaçtı" sinyalidir ve operasyonun görmesi gerekir; call başına log/insert
     * ise bilinçli olarak yapılmaz.
     */
    private void logSkippedActiveUniqueKeysIfAny(Campaign campaign) {
        if (skippedActiveUniqueKeys.isEmpty()) {
            return;
        }
        int sampleSize = Math.min(skippedActiveUniqueKeys.size(), MAX_LOGGED_SKIPPED_KEYS);
        LOG.warn("[DUP-FETCH-GUARD] " + skippedActiveUniqueKeys.size()
                + " row(s) re-selected by DB while a live Call already waits in cache/callQueue or is in flight; skipped, no new Call created."
                + " Keys=" + skippedActiveUniqueKeys.subList(0, sampleSize)
                + (sampleSize < skippedActiveUniqueKeys.size() ? " (+" + (skippedActiveUniqueKeys.size() - sampleSize) + " more)" : ""),
                LOG_MODULE.CAMPAIGN, campaign);
    }

    private boolean isCampaignRunning(Campaign campaign) {
        return CAMPAIGN_STATE.RUNNING.equals(campaign.getState());
    }

    private void logFetchError(Exception e, Campaign campaign) {
        LOG_MODULE module = (e instanceof SQLException ? LOG_MODULE.DB : LOG_MODULE.CAMPAIGN);
        LOG.error(e, e, module, campaign);
    }

    private void processDataFetchAndRefill(Campaign campaign) throws SQLException {
        List<Call> allCalls = new ArrayList<>();
        List<Call> retries = new ArrayList<>();
        List<Call> freshCalls = new ArrayList<>();

        int remainDataToFetch = calculateRemainingCacheCapacity(campaign);

        remainDataToFetch = fetchCallsFromSource(allCalls, retries, freshCalls, remainDataToFetch);

        adjustFetchInterval(campaign, remainDataToFetch);

        allCalls.addAll(mergeAlternately(retries, freshCalls));

        if (!allCalls.isEmpty()) {
            // Tüm batch'ler (callbacks/retries/fresh) tek seferde fetched işaretlenir, hemen
            // ardından cache'e eklenir. fetched-flag UPDATE'i patlarsa allCalls cache'e hiç
            // girmez ve hiçbiri flag'lenmez (ya hep ya hiç); satırlar sonraki tick'te tekrar
            // SELECT edilir. DNC/expire kendi SQLException'larını yuttuğundan, flag-set ile
            // dataQueue.addAll arasında "flag'li ama cache'siz" kayıp penceresi kalmaz.
            // FIX-330538: rezervasyonu alınamayan satırlar burada batch'ten düşer.
            reserveRowsForFetch(allCalls);
            processAndQueueFetchedCalls(campaign, allCalls);
        }

        finalizeFetchCycle(campaign);
    }

    private int calculateRemainingCacheCapacity(Campaign campaign) {
        return campaign.getCacheLimit() - dataQueue.size();
    }

    private int fetchCallsFromSource(List<Call> allCalls, List<Call> retries, List<Call> freshCalls, int remainDataToFetch) throws SQLException {
        if (hasEnabledSegmentationFilters()) {
            return fetchCallsWithSegmentation(allCalls, retries, freshCalls, remainDataToFetch);
        } else {
            return fetchCallsWithoutSegmentation(allCalls, retries, freshCalls, remainDataToFetch);
        }
    }

    private void adjustFetchInterval(Campaign campaign, int remainDataToFetch) {
        if (remainDataToFetch > 0 && campaign.getLowCacheSelectInterval() != null) {
            changeFetchInterval(campaign.getLowCacheSelectInterval() * 1000);
        } else {
            changeFetchInterval(DEFAULT_DATA_FETCH_PERIOD);
        }
    }

    private void processAndQueueFetchedCalls(Campaign campaign, List<Call> calls) throws SQLException {
        assignProductionNumbers(calls);

        if (campaign.getDncEnabled()) {
            applyDncFilter(campaign, calls);
        }

        if (Boolean.TRUE.equals(campaign.getExpireFilterEnabled())) {
            applyExpireFilter(campaign, calls);
        }

        dataQueue.addAll(calls);
        updateCampaignFilterStats(campaign);
    }

    private void assignProductionNumbers(List<Call> calls) {
        calls.forEach(c -> c.setProductionNumber(productionCounter++));
    }

    private void applyDncFilter(Campaign campaign, List<Call> calls) {
        campaignContext.getDialerFacade().getDncEngine().applyDncToCalls(campaign, calls);
    }

    private void applyExpireFilter(Campaign campaign, List<Call> calls) {
        campaignContext.getDialerFacade().getJitExpireValidator().applyExpireFilterToCalls(campaign, calls);
    }

    private void updateCampaignFilterStats(Campaign campaign) {
        lastCampaignFiltersStats = campaignFilterStatsCalculator.calculateStats(campaign, dataQueue, LocalDateTime.now());
    }

    private void handleNoNewCallsCallback(Campaign campaign) throws SQLException {
        if (isCallbackRefillApplicable(campaign)) {

            if (countCallbackListSize() > 0 && dataQueue.size() <= campaign.getCacheLimit() * DATA_LOAD_FACTOR) {
                refillCallbacksManually(campaign);
            }
        }
    }

    private boolean isCallbackRefillApplicable(Campaign campaign) {
        return ! hasEnabledSegmentationFilters() && campaign.getAllowCallback() && !Constants.CAMPAIGN_TYPE.OUTBOUND_IVR.equals(campaign.getType());
    }

    private void refillCallbacksManually(Campaign campaign) throws SQLException {
        List<Call> dbCallbacks = new ArrayList<>();
        int limit = campaign.getCacheLimit() - dataQueue.size();
        JSONArray rawDataCallbacks = fetchRawDataCallbacks(limit, Optional.empty());

        createAndAddCallsFromRawData(dbCallbacks, rawDataCallbacks, Constants.CALL_TYPES.CALLBACK, Optional.empty());
        if (!dbCallbacks.isEmpty()) {
            reserveRowsForFetch(dbCallbacks);
        }
        assignProductionNumbers(dbCallbacks);
        if (campaign.getDncEnabled()) {
            applyDncFilter(campaign, dbCallbacks);
        }
        dataQueue.addAll(dbCallbacks);

    }

    private void finalizeFetchCycle(Campaign campaign) {
        // callsUniqueKeys reset'i artık fetchCalls() finally bloğunda, her tick için
        // exception-safe biçimde yapılır (bkz. fetchCalls).
        calculateCampaignParameters();

        if (campaign.getActualListSize() == 0) {
            campaignContext.getEndListBehaviour().processEndList();
        }
    }

    private void processCallbackManagementIfQueueFull(Campaign campaign) throws SQLException {
        if (isCallbackRefillApplicable(campaign)) {
            int callbackCountInDb = getCallBacksCount(campaign);

            if (callbackCountInDb > 0) {
                cleanQueueToMakeSpace(campaign, callbackCountInDb);
                fillQueueWithCallbacksIfSpaceAvailable(campaign);
            }
            calculateCampaignParameters();
        }
    }

    private void cleanQueueToMakeSpace(Campaign campaign, int callbackCountInDb) throws SQLException {
        if (dataQueue.size() + callbackCountInDb > campaign.getCacheLimit()) {
            int maxRowsToRemove = calculateMaxRowsToRemove(campaign, callbackCountInDb);

            if (maxRowsToRemove > 0) {
                int removedRows = cleanQueue(dataQueue, maxRowsToRemove, campaign.getCacheLimit());
            }
        }
    }

    private int calculateMaxRowsToRemove(Campaign campaign, int callbackCountInDb) {
        int callBacksInDataQueue = (int) dataQueue.stream().filter(call -> call.getCallType().equals(Constants.CALL_TYPES.CALLBACK)).count();
        int maxRowsToRemove = campaign.getCacheLimit() - callBacksInDataQueue;

        return Math.min(callbackCountInDb, maxRowsToRemove);
    }

    private void fillQueueWithCallbacksIfSpaceAvailable(Campaign campaign) throws SQLException {
        if (dataQueue.size() < campaign.getCacheLimit()) {
            List<Call> dbCallbacks = new ArrayList<>();
            int limit = campaign.getCacheLimit() - dataQueue.size();
            JSONArray rawDataCallbacks = fetchRawDataCallbacks(limit, Optional.empty());

            createAndAddCallsFromRawData(dbCallbacks, rawDataCallbacks, Constants.CALL_TYPES.CALLBACK, Optional.empty());
            if (!dbCallbacks.isEmpty()) {
                reserveRowsForFetch(dbCallbacks);
            }
            assignProductionNumbers(dbCallbacks);
            if (campaign.getDncEnabled()) {
                applyDncFilter(campaign, dbCallbacks);
            }
            dataQueue.addAll(dbCallbacks);

        }
    }

    private int fetchCallsWithSegmentation(
            List<Call> calls,
            List<Call> retries,
            List<Call> freshCalls,
            int remainDataToFetch
    ) throws SQLException {
        Campaign campaign = campaignContext.getCampaign();
        Map<Long, Integer> callsPerFilterAmountMap = getCallsPerFilterAmountMap(dataQueue);
        List<Filter> segmentationFilters = campaign.getEnabledFiltersBySegmentation(true);
        for (Filter segmentationFilter : segmentationFilters) {
            int maxCallsFetched = campaign.getCacheLimit() - callsPerFilterAmountMap.getOrDefault(-1L, 0);
            int filterPortionLimit = (segmentationFilter.getRatio() * maxCallsFetched / 100) -
                    callsPerFilterAmountMap.getOrDefault(segmentationFilter.getId(), 0);

            if (filterPortionLimit <= 0) continue;
            int fetchedCallbacks = 0;
            if (campaign.getAllowCallback() && !Constants.CAMPAIGN_TYPE.OUTBOUND_IVR.equals(campaign.getType())) {
                JSONArray rawDataCallbacks = fetchRawDataCallbacks(filterPortionLimit, Optional.of(segmentationFilter));
                if (rawDataCallbacks != null && rawDataCallbacks.length() > 0) {
                    fetchedCallbacks = createAndAddCallsFromRawData(calls, rawDataCallbacks, Constants.CALL_TYPES.CALLBACK, Optional.of(segmentationFilter));
                    remainDataToFetch -= fetchedCallbacks;
                }
            }
            int retryLimit = (campaign.getRetryRatio() * (filterPortionLimit - fetchedCallbacks)) / 100;
            int fetchedRetries = 0;
            if (retryLimit > 0) {
                JSONArray rawDataRetry = fetchRawDataRetry(retryLimit, Optional.of(segmentationFilter));
                if (rawDataRetry != null && rawDataRetry.length() > 0) {
                    fetchedRetries = createAndAddCallsFromRawData(retries, rawDataRetry, Constants.CALL_TYPES.RETRY, Optional.of(segmentationFilter));
                    remainDataToFetch -= fetchedRetries;
                }
            }
            int freshLimit = filterPortionLimit - fetchedCallbacks - fetchedRetries;
            int fetchedFresh = 0;
            if (freshLimit > 0) {
                JSONArray rawDataFresh = fetchRawDataFresh(freshLimit, Optional.of(segmentationFilter));
                if (rawDataFresh != null && rawDataFresh.length() > 0) {
                    fetchedFresh = createAndAddCallsFromRawData(freshCalls, rawDataFresh, Constants.CALL_TYPES.FRESH, Optional.of(segmentationFilter));
                    remainDataToFetch -= fetchedFresh;
                }
            }
            int totalFetched = fetchedCallbacks + fetchedRetries + fetchedFresh;
            if (totalFetched > 0) {
                callsPerFilterAmountMap.put(segmentationFilter.getId(),
                        callsPerFilterAmountMap.getOrDefault(segmentationFilter.getId(), 0) + totalFetched);
                LOG.info(String.format("Fetched calls for campaign %d using segmentation filter with id %d: Callbacks=%d, Retries=%d, Fresh=%d, Total=%d, filterPortionLimit=%d",
                                campaign.getId(),
                                segmentationFilter.getId(),
                                fetchedCallbacks,
                                fetchedRetries,
                                fetchedFresh,
                                totalFetched,
                                filterPortionLimit),
                        LOG_MODULE.CAMPAIGN,
                        campaign);
            }
        }
        return remainDataToFetch;
    }

    private int fetchCallsWithoutSegmentation(
            List<Call> calls,
            List<Call> retries,
            List<Call> freshCalls,
            int remainDataToFetch
    ) throws SQLException {
        Campaign campaign = campaignContext.getCampaign();
        int fetchedCallbacks = 0;
        if (remainDataToFetch > 0 && campaign.getAllowCallback() && !Constants.CAMPAIGN_TYPE.OUTBOUND_IVR.equals(campaign.getType())) {
            JSONArray rawDataCallbacks = fetchRawDataCallbacks(remainDataToFetch, Optional.empty());
            if (rawDataCallbacks != null && rawDataCallbacks.length() > 0) {
                fetchedCallbacks = createAndAddCallsFromRawData(calls, rawDataCallbacks, Constants.CALL_TYPES.CALLBACK, Optional.empty());
                remainDataToFetch -= fetchedCallbacks;
            }
        }
        int fetchedRetries = 0;
        if (remainDataToFetch > 0) {
            int retryQuantity = (campaign.getRetryRatio() * remainDataToFetch) / 100;
            JSONArray rawDataRetry = fetchRawDataRetry(retryQuantity, Optional.empty());
            if (rawDataRetry != null && rawDataRetry.length() > 0) {
                fetchedRetries = createAndAddCallsFromRawData(retries, rawDataRetry, Constants.CALL_TYPES.RETRY, Optional.empty());
                remainDataToFetch -= fetchedRetries;
            }
        }
        int fetchedFresh = 0;
        if (remainDataToFetch > 0) {
            JSONArray rawDataFresh = fetchRawDataFresh(remainDataToFetch, Optional.empty());
            if (rawDataFresh != null && rawDataFresh.length() > 0) {
                fetchedFresh = createAndAddCallsFromRawData(freshCalls, rawDataFresh, Constants.CALL_TYPES.FRESH, Optional.empty());
                remainDataToFetch -= fetchedFresh;
            }
        }
        int totalFetched = fetchedCallbacks + fetchedRetries + fetchedFresh;
        if (totalFetched > 0) {
            LOG.info(String.format("Fetched calls without segmentation for campaign %d: Callbacks=%d, Retries=%d, Fresh=%d, Total=%d",
                            campaign.getId(),
                            fetchedCallbacks,
                            fetchedRetries,
                            fetchedFresh,
                            totalFetched),
                    LOG_MODULE.CAMPAIGN,
                    campaign);
        }
        return remainDataToFetch;
    }

    private String resolveFilterValue(Campaign campaign) {
        if (campaign == null) {
            return null;
        }

        String nonSegmentationValue = findFilterFieldValue(campaign.getEnabledFiltersBySegmentation(false));
        if (nonSegmentationValue != null) {
            return nonSegmentationValue;
        }

        return findFilterFieldValue(campaign.getEnabledFiltersBySegmentation(true));
    }

    private String findFilterFieldValue(List<Filter> filters) {
        if (filters == null || filters.isEmpty()) {
            return null;
        }
        return filters.stream()
                .filter(f -> f != null
                        && f.getField() != null
                        && Constants.FILTER_FIELD_NAME.equalsIgnoreCase(f.getField().getFieldName()))
                .map(Filter::getValue)
                .findFirst()
                .orElse(null);
    }

    private boolean hasEnabledSegmentationFilters() {
        return !campaignContext.getCampaign().getEnabledFiltersBySegmentation(true).isEmpty();
    }

    private int getCallBacksCount(Campaign campaign) throws SQLException {
        List<Long> usersIds = null;
        // Task 321997: ATTEMPT_COUNT'ta owner aktif-kullanıcı filtresi UYGULANMAZ — owner online/registered
        // olmasa bile (ör. logout/işten çıkmış) vadesi gelen appointer callback'ler sayılır/çekilir; böylece
        // originator deneme sayar ve eşikte havuza devreder. Yalnız TIMER/legacy'de owner filtresi uygulanır.
        if (campaign.getCallbackToAppointer() && !campaign.isAppointerResetByAttempt()) {
            ACDEngine acdEngine = campaignContext.getDialerFacade().getConnectionComposition().getAcdEngine();
            usersIds = acdEngine.getCampaignUsers(campaign).stream().map(User::getId).collect(Collectors.toList());
            if (CollectionUtils.isEmpty(usersIds) && !campaign.getAppointerReset()) {
                return 0;
            }
        }
        return campaignContext.getDialerFacade().getCampaignDbOperations().countCallbacksToLoad(campaign, usersIds);
    }


    private Map<Long, Integer> getCallsPerFilterAmountMap(Collection<Call> calls) {
        Set<Long> segmentationFilterIds = campaignContext.getCampaign()
                .getEnabledFiltersBySegmentation(true)
                .stream()
                .map(Filter::getId)
                .collect(Collectors.toSet());
        Map<Long, Integer> amountMap = new HashMap<>();
        for (Call call : calls) {
            Filter filter = call.getAppliedSegmentationFilter();
            Long filterId = (filter != null && segmentationFilterIds.contains(filter.getId()))
                    ? filter.getId()
                    : -1L;
            amountMap.compute(filterId, (key, value) -> value == null ? 1 : value + 1);
        }
        return amountMap;
    }

    private <T> List<T> mergeAlternately(List<T> list1, List<T> list2) {
        int i = 0, j = 0;
        List<T> result = new ArrayList<>();
        while (i < list1.size() || j < list2.size()) {
            if (i < list1.size())
                result.add(list1.get(i++));
            if (j < list2.size())
                result.add(list2.get(j++));
        }
        return result;
    }

    /**
     * Changes data fetch interval. Interval is changed only if current interval
     * is different from new interval
     *
     * @param newInterval
     */
    private void changeFetchInterval(int newInterval) {
        if (newInterval != currentFetchInterval) {
            scheduledFuture.cancel(false);
            scheduledFuture = createScheduledFuture(newInterval);
            currentFetchInterval = newInterval;
        }
    }

    /**
     * Calculate remaining size in dataQueue to determine whether we can fetch
     * data or not
     *
     * @return true if there is enough space in dataQueue
     */
    private boolean enoughSpaceInDataQueue() {
        return dataQueue.size() <= campaignContext.getCampaign().getCacheLimit() * DATA_LOAD_FACTOR;
    }

    /**
     * Fetch data from database by given queue emptiness
     * @return jsonArray raw data
     * @throws SQLException
     */
    private JSONArray fetchRawDataCallbacks(int rowCount, Optional<Filter> segmentationFilter) throws SQLException {
        Campaign campaign = campaignContext.getCampaign();
        List<Long> usersIds = null;

        Boolean isAppointer = campaign.getCallbackToAppointer();
        // Task 321997: ATTEMPT_COUNT'ta owner aktif-kullanıcı filtresi uygulanmaz (offline owner callback'i
        // de çekilir → originator deneme sayar / eşikte havuza devreder). Yalnız TIMER/legacy'de filtre var.
        if (isAppointer && !campaign.isAppointerResetByAttempt()) {
            ACDEngine acdEngine = campaignContext.getDialerFacade().getConnectionComposition().getAcdEngine();
            usersIds = acdEngine.getCampaignUsers(campaign).stream().map(User::getId).collect(Collectors.toList());
            if (CollectionUtils.isEmpty(usersIds) && !campaign.getAppointerReset())
                return new JSONArray();
        }
        return campaign.getEnabledPhoneNumberColumns()
                .isEmpty() ? new JSONArray() : campaignContext.getDialerFacade().getCampaignDbOperations().fetchDataForProducerCallbacks(campaign, rowCount, usersIds, segmentationFilter);
    }

    /**
     * Fetch data from database by given queue emptiness
     *
     * @return jsonArray raw data
     * @throws SQLException
     */
    private JSONArray fetchRawDataRetry(int rowCount, Optional<Filter> segmentationFilter) throws SQLException {
        return campaignContext.getCampaign().getEnabledPhoneNumberColumns()
                .isEmpty() ? new JSONArray() : campaignContext.getDialerFacade().getCampaignDbOperations().fetchDataForProducerRetries(campaignContext.getCampaign(), rowCount, segmentationFilter);
    }

    /**
     * Fetch data from database by given queue emptiness
     *
     * @return jsonArray raw data
     * @throws SQLException
     */
    private JSONArray fetchRawDataFresh(int rowCount, Optional<Filter> segmentationFilter) throws SQLException {
        return campaignContext.getCampaign().getEnabledPhoneNumberColumns()
                .isEmpty() ? new JSONArray() : campaignContext.getDialerFacade().getCampaignDbOperations().fetchDataForProducerFresh(campaignContext.getCampaign(), rowCount, segmentationFilter);
    }

    /**
     * Create calls objects from raw data
     *
     * @param calls
     * @param rawData
     * @return number of created calls
     * @throws SQLException
     */
    private int createAndAddCallsFromRawData(
            List<Call> calls,
            JSONArray rawData,
            Constants.CALL_TYPES callType,
            Optional<Filter> appliedSegmentationFilter
    ) throws SQLException {
        List<Call> createdCalls = createCallsFromRawData(rawData, callType, appliedSegmentationFilter);
        // fetched-flag artık burada (batch başına) DEĞİL, çağıran tarafında cache'e eklemeyle
        // aynı noktada set edilir (bkz. processDataFetchAndRefill / refillCallbacksManually /
        // fillQueueWithCallbacksIfSpaceAvailable). Böylece çok-batch'li fetch'te cycle ortası
        // DB hatası kısmi "DB'de fetched ama cache'de yok" (kalıcı kayıp) durumu bırakmaz.
        calls.addAll(createdCalls);
        return createdCalls.size();
    }

    /**
     * Create Call objects from rawData
     *
     * @param rawData
     * @return list of calls
     */
    private List<Call> createCallsFromRawData(JSONArray rawData, Constants.CALL_TYPES callType, Optional<Filter> appliedSegmentationFilter) {
        List<Call> calls = new ArrayList<>();
        // Task 325559 / D1: aranamayan kayitlarin key'leri neden bazinda toplanir ve batch olarak
        // kapatilir. Onceki davranista bu kayitlar yalnizca WARN'lanip dusuruluyordu; is_fetched
        // isaretlenmedigi icin her producer tick'inde yeniden SELECT edilip sonsuza kadar
        // loglaniyorlardi.
        Map<String, List<String>> uncallableKeysByReason = new LinkedHashMap<>();
        for (int i = 0; i < rawData.length(); i++) {
            Campaign campaign = campaignContext.getCampaign();
            try {
                JSONObject jsonRow = rawData.getJSONObject(i);
                String uniqueKey = jsonRow.get(campaign.getColUniqueKey()).toString();
                if (activeUniqueKeys.contains(uniqueKey)) {
                    // FIX-330538: satırın canlı bir Call kopyası zaten cache/callQueue'da.
                    // Yeni Call üretilmez ve bu satır için rezervasyon UPDATE'i gönderilmez.
                    skippedActiveUniqueKeys.add(uniqueKey);
                } else if (!callsUniqueKeys.contains(uniqueKey)) {
                    Call call = new Call();
                    call.setId("dl-" + UUID.randomUUID().toString());
                    call.setCampaign(campaign);
                    call.setRawData(jsonRow);
                    call.setAttachedData(populateAttachedData(call));
                    call.setUniqueKey(uniqueKey);

                    if (jsonRow.has(CALLBACK_TYPE.getValue())) {
                        call.setCallbackType(Constants.CALLBACKTYPE.valueOf(jsonRow.get(CALLBACK_TYPE.getValue()).toString()));
                    }

                    if (jsonRow.has(CALLBACK_NUMBER.getValue())) {
                        call.setCallbackNumber(trimPhoneNumber(jsonRow.get(CALLBACK_NUMBER.getValue()).toString()));
                    }
                    if (jsonRow.has(RETRY_NUMBER.getValue())) {
                        call.setRetryNumber(trimPhoneNumber(jsonRow.get(RETRY_NUMBER.getValue()).toString()));
                    }

                    String calledNumber = resolveCalledNumber(call);
                    if (calledNumber == null) {
                        uncallableKeysByReason.computeIfAbsent(resolveUncallableReason(call),
                                reason -> new ArrayList<>()).add(uniqueKey);
                        callsUniqueKeys.add(uniqueKey);
                        continue;
                    }

                    call.setCalledNumber(calledNumber);
                    call.setFilter(resolveFilterValue(call.getCampaign()));
                    call.setCallType(callType);
                    appliedSegmentationFilter.ifPresent(call::setAppliedSegmentationFilter);
                    populateCDR(call, campaign, callType);
                    calls.add(call);
                    callsUniqueKeys.add(uniqueKey);
                } else {
                    LOG.warn("Call with key: " + uniqueKey + " already created", LOG_MODULE.CAMPAIGN, campaign);
                }
            } catch (Exception e) {
                LOG.warn(e, e, LOG_MODULE.CAMPAIGN, campaign);
            }
        }
        closeUncallableRecords(uncallableKeysByReason);
        return calls;
    }

    /**
     * Task 325559 — aranacak numarayi belirler. Oncelik sirasi korunur (callback > retry > telefon
     * kolonlari) ancak iki degisiklik vardir:
     *
     * <ul>
     * <li>Argumanlar TEMBEL degerlendirilir. Eskiden {@code ObjectUtils.firstNonNull(...)} varargs
     * oldugu icin telefon kolonu secimi HER ZAMAN calisiyordu; callback numarasi dolu olsa bile
     * kolonlar bossa istisna atip kaydi dusuruyordu (D1'in genis hali).</li>
     * <li>Numara basina limit aktifse aday numara uygunluk kontrolunden gecer; fresh secimde
     * limitini doldurmus kolonlar ATLANIR (Kabul kriteri 4 ve 8).</li>
     * </ul>
     *
     * @return aranacak numara; kayitta aranabilir numara yoksa {@code null}
     */
    private String resolveCalledNumber(Call call) {
        if (isReactivatedRecord(call)) {
            return resolveReactivatedRecord(call);
        }
        String callbackNumber = trimPhoneNumber(call.getCallbackNumber());
        if (callbackNumber != null) {
            return isNumberDialable(call, callbackNumber) ? callbackNumber : fallBackToEligibleNumber(call);
        }
        String retryNumber = trimPhoneNumber(call.getRetryNumber());
        if (retryNumber != null) {
            return isNumberDialable(call, retryNumber) ? retryNumber : fallBackToEligibleNumber(call);
        }
        return selectFreshNumber(call);
    }

    /**
     * Task 325559 / Faz 8 — kaydin daha once islenmis ama su an bekleyen bir arama plani OLMAYAN
     * bir satir olarak geldigini soyler. Uretimde bu, veri manipulasyon ekranindan (veya Puzzle
     * API'den) AKTIVE edilen kayittir: aktivasyon tarih kolonlarini temizler, satir fresh fetch'ten
     * gelir, arama izi ve {@code retry_number}/{@code callback_number} ise satirda kalir.
     *
     * <p>Yalniz ozellik aktifken gecerlidir; ozellik kapaliyken aktive kayit bugunku gibi bayat
     * {@code retry_number}'dan (halka konumu) aranmaya devam eder.</p>
     */
    private boolean isReactivatedRecord(Call call) {
        return isPerNumberLimitActive(call) && !call.hasPendingDialPlan() && call.hasDialHistory();
    }

    /**
     * Task 325559 / Faz 8 — aktive edilen kaydin aranacak numarasi (K1 + K2).
     *
     * <p><b>K1:</b> satirdaki {@code retry_number}/{@code callback_number} bekleyen bir plan degil,
     * bayat isaretcidir ({@link Call#hasPendingDialPlan()}). Eskiden plan gibi okunuyordu: o numara
     * limitteyken {@link #fallBackToEligibleNumber} yonlendirecek bekleyen plan bulamiyor ve kayit,
     * operatorun ekledigi YENI numara hic aranmadan tekrar kapaniyordu. Artik callback/retry dallari
     * atlanir ve numara fresh semantigiyle secilir: colSort'a gore limitini doldurmamis ilk kolon.</p>
     *
     * <p><b>K2:</b> bu aramanin planlama adimi yoktur; izde karsiligi olmazsa numara basina sayim onu
     * hic gormez ve numara limitinden 1 fazla aranir. Arama {@link Call#recordUnplannedDial} ile izde
     * tek girdiyle kaydedilir. {@code retryNumber} secilen numaraya hizalanir; sonraki halka yuruyusu
     * ({@code Call.findNextNumberToCall}) bu alana gore konumlanir.</p>
     *
     * @return aranacak numara; aranabilir numara yoksa {@code null} (kayit kapatilir)
     */
    private String resolveReactivatedRecord(Call call) {
        String number = selectFreshNumber(call);
        if (number == null) {
            return null;
        }
        call.setRetryNumber(number);
        call.recordUnplannedDial(number);
        return number;
    }

    /**
     * Task 325559 — planlanan numara (retry/callback) limitini doldurmussa kayit KAPATILMAZ;
     * kayittaki diger numaralarin kotasi kontrol edilip ilk uygun numaraya dusulur.
     *
     * <p>Onceki davranis {@code null} donuyordu ve bu, kaydi
     * {@code setClosedWithoutDialing} ile KALICI kapatiyordu — diger numaralarin kalan hakki hic
     * bakilmadan yakiliyordu. Kabul kriteri 3 bunun tersini istiyor: limitli numara "pas gecilir",
     * kayit kalan numaralarla DEVAM eder.</p>
     *
     * <p>Bekleyen plan kaydi FIILEN aranan numaraya yonlendirilir
     * ({@link Call#redirectPendingDialTo(String)}); aksi halde iz "planlanan numara arandi" der, o
     * numaranin sayaci sisirilir ve fiilen aranan numaranin aramasi hic sayilmadigi icin limitinden
     * bir fazla arama alir. Yonlendirme kaydi duzeltilemiyorsa (bozuk satir) arama YAPILMAZ; yanlis
     * sayimla limit asma riski alinmaz.</p>
     *
     * <p>Hangi gecmis listesinin duzeltilecegi {@code call_process} izinin son adimindan
     * turetilir; cagiran "callback mi retry mi" bilgisini artik GECIRMEZ (Faz 7 / P3.2).</p>
     *
     * <p>{@code retryNumber} de secilen numaraya hizalanir; sonraki halka yuruyusu
     * ({@code Call.findNextNumberToCall}) bu alana gore konumlanir.</p>
     *
     * @return uygun numara; kayitta hicbir uygun numara yoksa {@code null}
     */
    private String fallBackToEligibleNumber(Call call) {
        String eligibleNumber = selectFreshNumber(call);
        if (eligibleNumber == null || !call.redirectPendingDialTo(eligibleNumber)) {
            return null;
        }
        call.setRetryNumber(eligibleNumber);
        return eligibleNumber;
    }

    /**
     * Fresh cagride numara secimi: colSort'a gore ilk dolu VE limitini doldurmamis kolon.
     * Ozellik kapaliyken davranis birebir eskisiyle aynidir (ilk dolu kolon).
     */
    private String selectFreshNumber(Call call) {
        Map<String, Integer> dialCounts = perNumberDialCounts(call);
        for (PhoneNumberColumn numberColumn : call.getCampaign().getEnabledPhoneNumberColumns()) {
            JSONObject jsonRow = call.getRawData();
            if (!jsonRow.has(numberColumn.getColNumber())) {
                continue;
            }
            String phoneNumber = trimPhoneNumber(jsonRow.get(numberColumn.getColNumber()).toString());
            if (phoneNumber == null) {
                continue;
            }
            if (!isNumberWithinLimit(call, dialCounts, phoneNumber)) {
                continue;
            }
            return jsonRow.get(numberColumn.getColNumber()).toString();
        }
        return null;
    }

    private boolean isNumberDialable(Call call, String phoneNumber) {
        return isNumberWithinLimit(call, perNumberDialCounts(call), phoneNumber);
    }

    /**
     * Numara limitinin altinda mi? Sorgu anahtari {@link Call#normalizeNumberForLimit(String)} ile
     * normalize edilir; harita anahtarlari da ayni kurali kullanir. Iki taraf ayni kurali
     * uygulamazsa bosluklu veri haritayi iskalatir, sayim 0 kabul edilir ve limit SESSIZCE hic
     * uygulanmaz (Faz 7 / P2.3).
     */
    private boolean isNumberWithinLimit(Call call, Map<String, Integer> dialCounts, String phoneNumber) {
        if (!isPerNumberLimitActive(call)) {
            return true;
        }
        String normalized = Call.normalizeNumberForLimit(phoneNumber);
        if (normalized == null) {
            return true;
        }
        Integer counted = dialCounts.get(normalized);
        return counted == null || counted < call.getCampaign().getMaxAttemptPerNumber();
    }

    /**
     * Task 325559 — numara basina limit bu kampanya + strateji icin uygulanir mi?
     *
     * <p>Karar {@code Campaign.isPerNumberAttemptLimitActive} icinde TEK yerde durur; strateji
     * katmani ({@code RetryStrategy.isPerNumberLimitActive}) da ayni metodu cagirir. Producer'in
     * yalnizca {@code getMaxAttemptPerNumber()}'a bakmasi muafiyeti yarim birakiyordu: muaf
     * stratejilerin (0 ve 1) kampanyasinda alan DB'de dolu kalabiliyor (UI freeze degeri
     * TEMIZLEMIYOR) ve producer limiti uygulayip kaydi kapatiyor ya da plani baska numaraya
     * yonlendiriyordu.</p>
     */
    private boolean isPerNumberLimitActive(Call call) {
        Campaign campaign = call.getCampaign();
        return campaign != null
                && Campaign.isPerNumberAttemptLimitActive(campaign.getMaxAttemptPerNumber(), campaign.getRetryStrategy());
    }

    /**
     * Numara -> TAMAMLANMIS arama sayisi haritasi. Ozellik kapaliyken hic hesaplanmaz; acikken
     * kayit basina BIR KEZ hesaplanir.
     *
     * <p>Kaynak bilincli olarak {@code getDialedNumberSequence()} DEGIL
     * {@link Call#getCompletedDialSequence()}'dir: producer arama ONCESI okur ve izdeki son girdi
     * henuz yapilmamis bir plan olabilir. Ayni diziyi kullanmak numaranin son hakkini yakar ve
     * fiili limiti {@code maxAttemptPerNumber - 1}'e dusururdu.</p>
     */
    private Map<String, Integer> perNumberDialCounts(Call call) {
        if (!isPerNumberLimitActive(call)) {
            return Collections.emptyMap();
        }
        Map<String, Integer> counts = new HashMap<>();
        for (String dialedNumber : call.getCompletedDialSequence()) {
            String normalized = Call.normalizeNumberForLimit(dialedNumber);
            if (normalized != null) {
                Integer current = counts.get(normalized);
                counts.put(normalized, current == null ? 1 : current + 1);
            }
        }
        return counts;
    }

    /**
     * Kaydin neden aranamadigini belirler; deger {@code close_reason} kolonuna yazilir.
     *
     * <p>Aday kontrolu {@link Call#getLimitCandidateNumbers()} uzerinden yapilir (Faz 7 / P2.1b).
     * Onceki hal {@code getNumbersInRawData()} kullaniyordu ve o metot {@code isNotEmpty} ile
     * yalnizca bosluktan olusan bir kolonu da "dolu numara" saydigi icin, gercekte GECERLI
     * NUMARASI OLMAYAN kayit "limit doldu" etiketi aliyordu. Producer o kolonu zaten atliyor,
     * dolayisiyla etiket ile gerceklik celisiyordu.</p>
     *
     * <p>Callback etiketi yalniz vadesi gelmis bir callback PLANI varsa yazilir
     * ({@link Call#isCallback()}: {@code callback_date} dolu) — strateji katmaninin
     * ({@code RetryStrategy.applyExhaustedCloseReason}) kullandigi olcutle ayni (Faz 8). Onceki olcut
     * {@code callback_number}'in doluluguydu; aktive edilen kayitta o kolon bayat kaldigi icin
     * (aktivasyon yalniz {@code callback_date}'i temizler) callback'le ilgisi olmayan bir kapanis
     * {@code max attempt reached on callback} etiketi aliyordu.</p>
     */
    private String resolveUncallableReason(Call call) {
        if (call.isCallback()) {
            return Constants.CLOSE_REASON_MAX_ATTEMPT_ON_CALLBACK;
        }
        return call.getLimitCandidateNumbers().isEmpty()
                ? Constants.CLOSE_REASON_NO_CALLABLE_NUMBER
                : Constants.CLOSE_REASON_NUMBER_LIMIT_REACHED;
    }

    /**
     * Aranamayan kayitlari neden bazinda tek batch UPDATE ile kapatir. Neden basina en fazla bir
     * DB cagrisi yapilir ve yalnizca aranamayan kayit varsa cagrilir.
     */
    private void closeUncallableRecords(Map<String, List<String>> uncallableKeysByReason) {
        if (uncallableKeysByReason.isEmpty()) {
            return;
        }
        Campaign campaign = campaignContext.getCampaign();
        for (Map.Entry<String, List<String>> entry : uncallableKeysByReason.entrySet()) {
            try {
                campaignContext.getDialerFacade().getCampaignDbOperations()
                        .setClosedWithoutDialing(campaign, entry.getValue(), entry.getKey());
                LOG.warn("Closed " + entry.getValue().size() + " record(s) without dialing, reason: "
                        + entry.getKey(), LOG_MODULE.CAMPAIGN, campaign);
            } catch (Exception e) {
                LOG.error(e, e, LOG_MODULE.DB, campaign);
            }
        }
    }

    private void populateCDR(Call call, Campaign campaign, Constants.CALL_TYPES callType) {
        CDR cdr = call.getCdr();
        cdr.setInteractionId(call.getId());
        cdr.setContactNumber(call.getCalledNumber());
        cdr.setCampaign(campaign);
        cdr.setCampaignType((long) campaign.getType().ordinal());
        this.setCDROutdialingType(call, cdr, campaign, callType);
        cdr.setTenant(campaign.getTenant());
        cdr.setContactId(Long.parseLong(call.getUniqueKey()));
        cdr.setCallStartTime(new Date());
        cdr.setFilter(call.getFilter());
        cdr.setUserResultCodeId(call.getUserResultCode() != null ? call.getUserResultCode().getId() : null);
        cdr.setAbandoned(call.isAbandoned());
        if (campaign.isOutboundIvrCampaign()) {
            cdr.setConnectedIvrNumber(campaign.getIvrApplicationNumber().getDigits());
        }
        if (call.getCampaign().getSkillEnabled()) {
            cdr.setSkills(call.getCampaign().getStringifiedSkills());
        }
    }

    private void setCDROutdialingType(Call call, CDR cdr, Campaign campaign, Constants.CALL_TYPES callType) {
        cdr.setCdrOutdialingType(resolveCDROutdialingType(call, campaign, callType));
    }

    private Constants.CDR_OUTDIALING_TYPE resolveCDROutdialingType(Call call, Campaign campaign, Constants.CALL_TYPES callType) {
        if (isCallbackCDR(call, campaign, callType)) {
            return Constants.CDR_OUTDIALING_TYPE.CALLBACK;
        }
        if (isRetryCDR(call, campaign, callType)) {
            return getRetryType(call, campaign);
        }
        return Constants.CDR_OUTDIALING_TYPE.NEW;
    }

    private boolean isCallbackCDR(Call call, Campaign campaign, Constants.CALL_TYPES callType) {
        return Constants.CALL_TYPES.CALLBACK.equals(callType)
                || hasRawValue(call.getRawData(), campaign.getColResultCallbackDate());
    }

    private boolean isRetryCDR(Call call, Campaign campaign, Constants.CALL_TYPES callType) {
        JSONObject rawData = call.getRawData();
        return Constants.CALL_TYPES.RETRY.equals(callType)
                || hasRawValue(rawData, campaign.getColResultRetryDate())
                || hasPositiveRawInt(rawData, campaign.getColResultAttempts())
                || hasPositiveRawInt(rawData, campaign.getColResultRcSequence())
                || hasRawValue(rawData, campaign.getColResultRetryHistory())
                || hasRawValue(rawData, campaign.getColResultUserRc())
                || hasRawValue(rawData, campaign.getColResultTelephonyRc());
    }

    private boolean hasPositiveRawInt(JSONObject rawData, String key) {
        if (!hasRawValue(rawData, key)) {
            return false;
        }
        try {
            return Integer.parseInt(rawData.get(key).toString()) > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean hasRawValue(JSONObject rawData, String key) {
        if (rawData == null || StringUtils.isBlank(key) || !rawData.has(key)) {
            return false;
        }
        Object value = rawData.opt(key);
        return value != null
                && !JSONObject.NULL.equals(value)
                && StringUtils.isNotBlank(value.toString());
    }

    private Constants.CDR_OUTDIALING_TYPE getRetryType(Call call, Campaign campaign) {
        if (Constants.RETRY_STRATEGY.RETRY_RULES_MANAGES_CYCLE.equals(campaign.getRetryStrategy())) {
            Constants.CDR_OUTDIALING_TYPE type = evaluateRetryTypeForRetryRulesManagesCycleStrategy(call, campaign);
            if (type != null) {
                return type;
            }
        }
        Double retryDelay = campaign.getRetryDelay();
        if (retryDelay != null && retryDelay > 0) {
            return SCHEDULED_RETRY;
        } else {
            return IMMEDIATE_RETRY;
        }
    }

    private Constants.CDR_OUTDIALING_TYPE evaluateRetryTypeForRetryRulesManagesCycleStrategy(Call call, Campaign campaign) {
        String userResultCodeKey = campaign.getColResultUserRc();
        JSONObject rawData = call.getRawData();
        if (!rawData.has(userResultCodeKey)) {
            LOG.warn("No result code found from previous call. Cannot determine retry type for " + call);
            return null;
        }
        List<ResultCodeSchedule> retryCycles = campaignContext.getDialerFacade().getResultCodeScheduleService().getByCampaignId(campaign.getId());
        String resultCode = rawData.getString(userResultCodeKey);
        Long resultCodeId = Long.valueOf(resultCode);
        Optional<ResultCodeSchedule> codeSchedule = retryCycles.stream()
                .filter(resultCodeSchedule -> resultCodeSchedule.getResultCode() != null && resultCodeSchedule.getResultCode().getId().equals(resultCodeId))
                .findAny();
        if (codeSchedule.isPresent()) {
            ResultCodeSchedule resultCodeSchedule = codeSchedule.get();
            if (resultCodeSchedule.isAdvancedIntervalEnabled()) {
                Optional<ResultCodeScheduleAdvanced> scheduleAdvanced = resultCodeSchedule.getResultCodeScheduleAdvanceds().stream()
                        .filter(ResultCodeScheduleAdvanced::isEnabled)
                        .filter(rcsa -> rcsa.getDialAttempt() == call.getResultCodeSequence())
                        .findFirst();
                return scheduleAdvanced.map(schedule -> schedule.getRetryInterval() > 0
                                ? SCHEDULED_RETRY
                                : IMMEDIATE_RETRY)
                        .orElse(null);
            } else {
                return resultCodeSchedule.getRetryInterval() > 0
                        ? SCHEDULED_RETRY
                        : IMMEDIATE_RETRY;
            }
        }
        return null;
    }

    /**
     * Extract phone number to call from row
     *
     * @param jsonRow
     * @return phoneNumber to call
     */
    /**
     * Trim value of phoneNumber
     *
     * @param phoneNumber
     * @return trimmed phoneNumber
     */
    private String trimPhoneNumber(String phoneNumber) {
        if (phoneNumber != null) {
            phoneNumber = phoneNumber.trim();
            if (phoneNumber.length() > 0)
                return phoneNumber;
        }
        return null;
    }

    private String populateAttachedData(Call call) {
        try {
            JSONObject attachedDataJSON = new JSONObject();
            for (AttachedData attachedData : call.getCampaign().getEnabledAttachedDatas()) {
                String rawFieldValue = getFieldValueFromRawData(call.getRawData(), attachedData.getField());
                if (rawFieldValue != null)
                    attachedDataJSON.put(attachedData.getFieldName(), createAttachedDataObject(rawFieldValue, attachedData.getPopup(), attachedData.getPopupName()));
            }

            attachedDataJSON.put(Constants.ALLOW_PREVIEW_BEEP, createAttachedDataObject(call.getCampaign().getAllowPreviewBeep(), null, null));
            attachedDataJSON.put(MANAGER_ACTION_VARIABLE.DIALED_NUMBER.getParam(), createAttachedDataObject(call.getCalledNumber(), null, null));

            return Base64.getEncoder().encodeToString(attachedDataJSON.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            LOG.error("", e);
        }
        return null;
    }

    private String getFieldValueFromRawData(JSONObject rawData, String field) {
        if (rawData.has(field))
            return rawData.getString(field);
        return null;
    }

    private JSONObject createAttachedDataObject(Object value, Boolean popup, String popupName) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put(ATTACHED_DATA_OBJECT.VALUE.getFieldName(), value);
        jsonObject.put(ATTACHED_DATA_OBJECT.POPUP.getFieldName(), Boolean.TRUE.equals(popup));
        jsonObject.put(ATTACHED_DATA_OBJECT.POPUP_NAME.getFieldName(), StringUtils.isEmpty(popupName) ? "" : popupName);
        return jsonObject;
    }

    /**
     * Set flag for rows that have already been fetched to avoid re-fetching them.
     *
     * <p>FIX-330538: satırları bu fetch adına rezerve eder. Rezervasyonu SELECT ile UPDATE arasında
     * başka bir fetch/node kapmış satırlar batch'ten düşürülür (Call üretilmez, kuyruğa girmez) ve
     * tick başına tek satırla (tek ActionLog) raporlanır. Kalan Call'lar fetched işaretini alır.</p>
     *
     * @param calls bu tick'te üretilen Call'lar; rezerve edilemeyenler listeden çıkarılır
     * @throws SQLException
     */
    private void reserveRowsForFetch(List<Call> calls) throws SQLException {
        Campaign campaign = campaignContext.getCampaign();
        Set<String> notReserved = campaignContext.getDialerFacade().getCampaignDbOperations().reserveRowsForFetch(campaign, calls);
        if (notReserved != null && !notReserved.isEmpty()) {
            calls.removeIf(call -> notReserved.contains(call.getUniqueKey()));
            List<String> keys = new ArrayList<>(notReserved);
            int sampleSize = Math.min(keys.size(), MAX_LOGGED_SKIPPED_KEYS);
            LOG.warn("[FETCH-RESERVATION] " + keys.size()
                    + " row(s) were reserved by another fetch between SELECT and UPDATE; dropped from this batch, no Call created."
                    + " Keys=" + keys.subList(0, sampleSize)
                    + (sampleSize < keys.size() ? " (+" + (keys.size() - sampleSize) + " more)" : ""),
                    LOG_MODULE.CAMPAIGN, campaign);
        }
        calls.forEach(call -> call.setIsFetched(Utils.convertDateToDouble(new Date())));
    }

    /**
     * Set necessary campaign parameters that we can calculate in this consumer
     *
     * Task 325461: sayaçlar eskiden tek try/catch içinde sırayla yazılıyordu; ilk patlayan count
     * sorgusu (vakada 2. sıradaki countTotalListSize) kendisinden sonraki tüm sayaçların
     * yazılmasını engelliyordu. CampaignMemento freshDataListSize'ı restore etmediği için de
     * ekranda kalıcı "0" görünüyor, veri DB'de olduğu hâlde "fresh data dolmuyor" şikâyeti
     * oluşuyordu. Artık her sayaç kendi hata korumasında yazılır. Türetilmiş sayaçlar
     * (listRemaining, contactsToHandleAmount) girdileri güncellenemediyse bayat değer üzerinden
     * hesaplanmaz — eski davranışta da bu adımlara hiç gelinmiyordu.
     */
    @Override
    protected void calculateCampaignParameters() {
        Campaign campaign = campaignContext.getCampaign();
        CampaignDbOperations campaignDbOperations = campaignContext.getDialerFacade().getCampaignDbOperations();

        boolean actualListSizeUpdated = updateCounter(() -> campaign.setActualListSize(campaignDbOperations.countActualListSize(campaign)));
        updateCounter(() -> campaign.setTotalListSize(campaignDbOperations.countTotalListSize(campaign)));
        updateCounter(() -> campaign.setRetryListSize(campaignDbOperations.countRetryListSize(campaign)));
        updateCounter(() -> campaign.setCallbackListSize(countCallbackListSize()));
        updateCounter(() -> campaign.setFreshDataListSize(campaignDbOperations.countFreshDataListSize(campaign)));

        if (actualListSizeUpdated && updateCounter(() -> campaign.setListRemaining(calculateListRemaining()))) {
            updateCounter(() -> campaign.setContactsToHandleAmount(calculateContactsToHandleAmount()));
        }
    }

    /**
     * Tek bir sayaç güncellemesini izole çalıştırır: hata loglanır ve diğer sayaçlar etkilenmez.
     *
     * @return güncelleme başarılıysa true
     */
    private boolean updateCounter(CounterUpdate counterUpdate) {
        try {
            counterUpdate.run();
            return true;
        } catch (SQLException | NullPointerException e) {
            LOG.error(e, e, LOG_MODULE.DB, campaignContext.getCampaign());
            return false;
        }
    }

    @FunctionalInterface
    private interface CounterUpdate {
        void run() throws SQLException;
    }

    /**
     * Calculate remaining callbacks. Outbound IVR campaign can't have callbacks
     * by design
     *
     * @return
     * @throws SQLException
     */
    private int countCallbackListSize() throws SQLException {
        int callbackListSize;
        if (campaignContext.getCampaign().isOutboundIvrCampaign()) {
            callbackListSize = 0;
        } else {
            callbackListSize = campaignContext.getDialerFacade().getCampaignDbOperations().countCallbackListSize(campaignContext.getCampaign());
        }
        return callbackListSize;
    }

    /**
     * Count rows where complete date exists
     *
     * @return
     * @throws SQLException
     */
    private int calculateListRemaining() throws SQLException {
        return campaignContext.getCampaign().getActualListSize() - campaignContext.getDialerFacade().getCampaignDbOperations().countCompletedCalls(campaignContext.getCampaign());
    }

    /**
     * Calculate contacts to handle amount
     *
     * @return contacts to handle amount
     */
    private int calculateContactsToHandleAmount() {
        Campaign campaign = campaignContext.getCampaign();
        return campaign.getListRemaining().get() - campaign.getRetryListSize() - campaign.getCallbackListSize();
    }

    /**
     * Set fetchedRow flag back to null for dataQueue items
     */
    @Override
    public void setRowFetchedFlagsToNull() {
        try {
            campaignContext.getDialerFacade().getCampaignDbOperations().removeFetchedDataFlagFromCalls(campaignContext.getCampaign(), convertFromBlockingQueueToList(dataQueue));
        } catch (SQLException e) {
            LOG.error(e, e, LOG_MODULE.DB, campaignContext.getCampaign());
        }
    }

    @Override
    protected LoggerDecorator getLog() {
        return LOG;
    }

    public CampaignFiltersStats getLastCampaignFiltersStats() {
        return lastCampaignFiltersStats;
    }

    /**
     * Return calls count is data queue
     */
    public int getCallsCountInQueue() {
        return dataQueue.size();
    }

    @Override
    public String toString() {
        return "CallProducer [campaign=" + campaignContext.getCampaign() + ", dataQueue=" + dataQueue.size() + "]";
    }

    private static class CallTypeComparator implements Comparator<Call> {

        private CallTypeComparator() {
        }

        private static final CallTypeComparator instance = new CallTypeComparator();

        public static CallTypeComparator getInstance() {
            return instance;
        }

        @Override
        public int compare(Call call1, Call call2) {
            if (call1 == null || call2 == null) {
                return 0;
            } else if (Constants.CALL_TYPES.CALLBACK.equals(call1.getCallType()) && !Constants.CALL_TYPES.CALLBACK.equals(call2.getCallType())) {
                return -1;
            } else if (!Constants.CALL_TYPES.CALLBACK.equals(call1.getCallType()) && Constants.CALL_TYPES.CALLBACK.equals(call2.getCallType())) {
                return 1;
            }
            return 0;
        }
    }

    private static class CallbackCallsIsFetchedComparator implements Comparator<Call> {

        private CallbackCallsIsFetchedComparator() {
        }

        private static final CallbackCallsIsFetchedComparator instance = new CallbackCallsIsFetchedComparator();

        public static CallbackCallsIsFetchedComparator getInstance() {
            return instance;
        }

        @Override
        public int compare(Call call1, Call call2) {
            if (call1 == null || call2 == null || call1.getIsFetched() == null || call2.getIsFetched() == null) {
                return 0;
            } else if (Constants.CALL_TYPES.CALLBACK.equals(call1.getCallType()) && Constants.CALL_TYPES.CALLBACK.equals(call2.getCallType()) && call1.getIsFetched() > call2.getIsFetched()) {
                return 1;
            } else if (Constants.CALL_TYPES.CALLBACK.equals(call1.getCallType()) && Constants.CALL_TYPES.CALLBACK.equals(call2.getCallType()) && call1.getIsFetched() < call2.getIsFetched()) {
                return -1;
            }
            return 0;
        }
    }

}
