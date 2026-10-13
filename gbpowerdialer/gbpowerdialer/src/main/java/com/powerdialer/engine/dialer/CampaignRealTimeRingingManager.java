package com.powerdialer.engine.dialer;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.powerdialer.dto.RingingMetricsDTO;
import com.powerdialer.engine.predictive.gamma.model.GammaRingAgeWeight;
import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.util.Utils;

import java.util.Date;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Generic manager to track real-time ringing calls per campaign using a self-healing time-bound Cache.
 *
 * <p>İki ayrı cache tutar:
 * <ul>
 *   <li><b>realTimeRingingCallsMap</b>: Asterisk VarSetEvent'i alınmış, çaldığı doğrulanmış çağrılar.</li>
 *   <li><b>pendingRingingCallsMap</b>: Originate edilmiş ancak VarSetEvent'i henüz alınmamış
 *       "uçuştaki" çağrılar. Gamma predictive hesabının AMI roundtrip gecikmesi sırasında
 *       over-dial yapmasını engeller. TTL kısa tutulur; AMI hiç dönmezse otomatik düşer.</li>
 * </ul>
 * Bir çağrı aynı anda yalnız tek cache'te bulunur: VarSet geldiğinde {@link #addRingingCall(Call)}
 * pending'den invalidate eder ve gerçek cache'e taşır.
 */
@Component
public class CampaignRealTimeRingingManager {

    private static final Logger log = LoggerFactory.getLogger(CampaignRealTimeRingingManager.class);

    private static final double SECONDS_PER_DAY = 86400.0d;
    private static final int DEFAULT_PENDING_RINGING_TTL_SEC = 35;
    private static final int PENDING_RINGING_TTL_BUFFER_SEC = 5;

    private final Map<Long, Cache<String, Call>> realTimeRingingCallsMap = new ConcurrentHashMap<>();
    private final Map<Long, Cache<String, PendingRingingEntry>> pendingRingingCallsMap = new ConcurrentHashMap<>();

    public void onCampaignStart(Campaign campaign) {
        if (campaign != null) {
            int timeout = (campaign.getRingTimeout() != null && campaign.getRingTimeout() > 0)
                          ? campaign.getRingTimeout() + 30 // Add buffer for network latencies
                          : 120;
            int pendingTtl = (campaign.getRingTimeout() != null && campaign.getRingTimeout() > 0)
                             ? campaign.getRingTimeout() + PENDING_RINGING_TTL_BUFFER_SEC
                             : DEFAULT_PENDING_RINGING_TTL_SEC;
            realTimeRingingCallsMap.put(campaign.getId(), CacheBuilder.newBuilder().expireAfterWrite(timeout, TimeUnit.SECONDS).build());
            pendingRingingCallsMap.put(campaign.getId(), CacheBuilder.newBuilder().<String, PendingRingingEntry>expireAfterWrite(pendingTtl, TimeUnit.SECONDS).build());
            log.info("Initialized realtime ringing cache for campaign: {} with timeout: {}s", campaign.getId(), timeout);
            log.debug("Initialized pending ringing buffer for campaign: {} with TTL: {}s", campaign.getId(), pendingTtl);
        }
    }

    public void onCampaignStop(Campaign campaign) {
        if (campaign != null) {
            realTimeRingingCallsMap.remove(campaign.getId());
            pendingRingingCallsMap.remove(campaign.getId());
            log.info("Cleared realtime ringing cache for campaign: {}", campaign.getId());
        }
    }

    public void addRingingCall(Call call) {
        if (call == null || call.getCampaign() == null) return;
        Cache<String, Call> cache = realTimeRingingCallsMap.get(call.getCampaign().getId());
        if (cache != null) {
            cache.put(call.getId(), call);
        }
        Cache<String, PendingRingingEntry> pendingCache = pendingRingingCallsMap.get(call.getCampaign().getId());
        if (pendingCache != null) {
            pendingCache.invalidate(call.getId());
        }
    }

    public void removeRingingCall(Call call) {
        if (call == null || call.getCampaign() == null) return;
        Cache<String, Call> cache = realTimeRingingCallsMap.get(call.getCampaign().getId());
        if (cache != null) {
            cache.invalidate(call.getId());
        }
    }

    /**
     * Originate Asterisk'e gönderildikten hemen sonra çağrılır.
     * Asterisk VarSetEvent'i dönene kadar Gamma'nın çağrıyı görmesini sağlar.
     * {@link #addRingingCall(Call)} pending'i otomatik invalidate eder; ayrıca
     * cache TTL'i AMI hiç dönmezse self-healing temizlik yapar.
     */
    public void addPendingRingingCall(Call call) {
        if (call == null || call.getCampaign() == null) return;
        Cache<String, PendingRingingEntry> pendingCache = pendingRingingCallsMap.get(call.getCampaign().getId());
        if (pendingCache != null) {
            pendingCache.put(call.getId(), new PendingRingingEntry(call, System.currentTimeMillis()));
        }
    }

    /**
     * Originate fail, AMI exception veya final cleanup yollarında pending kaydı temizler.
     * Cache'te olmayan id sessizce yutulur (Guava invalidate davranışı).
     */
    public void removePendingRingingCall(Call call) {
        if (call == null || call.getCampaign() == null) return;
        Cache<String, PendingRingingEntry> pendingCache = pendingRingingCallsMap.get(call.getCampaign().getId());
        if (pendingCache != null) {
            pendingCache.invalidate(call.getId());
        }
    }

    /**
     * Retrieve the exact number of active ringing calls for a campaign at this moment.
     * Gerçek (Asterisk onaylı) ve pending (AMI bekleyen) cache'leri birlikte sayar.
     * <p>Pilot doğrulama ve TTL ayarı için ek olarak {@link RingingMetricsDTO#getPendingTotalRinging()}
     * ve {@link RingingMetricsDTO#getPendingMaxAgeMs()} alanları doldurulur.
     */
    public RingingMetricsDTO getRealTimeRingingMetrics(Campaign campaign) {
        RingingMetricsDTO metrics = new RingingMetricsDTO();
        if (campaign == null) return metrics;

        // MADDE 2B: yaş ağırlıkları YALNIZ Gamma Plus açıkken hesaplanır. Bayrak kapalıyken
        // tek bir ek çarpma bile yapılmaz; çıktı bugünküyle bit-bit aynı kalır.
        boolean ageWeighted = campaign.isGammaPlusEnabled();
        // Yaş, Call.originationDate ile AYNI eksende (Excel serial gün) ölçülür. "Şimdi"yi bu
        // eksene tick başına BİR kez çeviririz: ters yön (Utils.convertDoubleToLongDate) POI
        // DateUtil.getJavaDate üzerinden her çağrıda Calendar+Date allocate ediyor ve bu bir
        // tick hot path'i (kampanya başına 2 sn'de bir, çalan hat sayısı kadar).
        double nowSerial = ageWeighted ? Utils.convertDateToDouble(new Date()) : 0.0;

        Cache<String, Call> cache = realTimeRingingCallsMap.get(campaign.getId());
        if (cache != null) {
            countActiveRinging(cache, metrics, ageWeighted, nowSerial);
        }

        Cache<String, PendingRingingEntry> pendingCache = pendingRingingCallsMap.get(campaign.getId());
        if (pendingCache != null) {
            countActivePendingRinging(pendingCache, metrics, ageWeighted, nowSerial);
        }
        return metrics;
    }

    private void countActiveRinging(Cache<String, Call> cache, RingingMetricsDTO metrics, boolean ageWeighted, double nowSerial) {
        for (Call call : cache.asMap().values()) {
            if (call.getAnswerDate() == null && call.getCompleteDate() == null) {
                accumulate(call, metrics, ageWeighted, nowSerial);
            }
        }
    }

    private void countActivePendingRinging(Cache<String, PendingRingingEntry> cache, RingingMetricsDTO metrics, boolean ageWeighted, double nowSerial) {
        long now = System.currentTimeMillis();
        long maxAge = metrics.getPendingMaxAgeMs();
        for (PendingRingingEntry entry : cache.asMap().values()) {
            Call call = entry.getCall();
            if (call.getAnswerDate() == null && call.getCompleteDate() == null) {
                accumulate(call, metrics, ageWeighted, nowSerial);
                metrics.setPendingTotalRinging(metrics.getPendingTotalRinging() + 1);
                long age = now - entry.getAddedAtMs();
                if (age > maxAge) maxAge = age;
            }
        }
        metrics.setPendingMaxAgeMs(maxAge);
    }

    private void accumulate(Call call, RingingMetricsDTO metrics, boolean ageWeighted, double nowSerial) {
        metrics.setTotalRinging(metrics.getTotalRinging() + 1);
        if (call.getCallType() != null) {
            double w = ageWeighted ? GammaRingAgeWeight.weight(ringAgeSeconds(call, nowSerial)) : 0.0;
            switch (call.getCallType()) {
                case CALLBACK:
                    metrics.setCbRinging(metrics.getCbRinging() + 1);
                    if (ageWeighted) {
                        metrics.setCbRingingWeight(metrics.getCbRingingWeight() + w);
                        metrics.setCbRingingWeightSq(metrics.getCbRingingWeightSq() + w * w);
                    }
                    break;
                case RETRY:
                    metrics.setRetryRinging(metrics.getRetryRinging() + 1);
                    if (ageWeighted) {
                        metrics.setRetryRingingWeight(metrics.getRetryRingingWeight() + w);
                        metrics.setRetryRingingWeightSq(metrics.getRetryRingingWeightSq() + w * w);
                    }
                    break;
                case FRESH:
                    metrics.setFreshRinging(metrics.getFreshRinging() + 1);
                    if (ageWeighted) {
                        metrics.setFreshRingingWeight(metrics.getFreshRingingWeight() + w);
                        metrics.setFreshRingingWeightSq(metrics.getFreshRingingWeightSq() + w * w);
                    }
                    break;
                default:
                    break;
            }
        }
    }

    /**
     * Çağrının kaç saniyedir çaldığı. Eksen olarak {@code originationDate} kullanılır; bu değer
     * {@code CallOriginator.processOrigination} içinde {@code cdr.callDialTime} ile AYNI anda ve
     * aynı {@code Date}'ten set edilir, dolayısıyla geçmiş {@code ringTime} ölçümüyle (dial → answer/end)
     * birebir aynı eksendedir.
     *
     * <p>FCT routing retry'de {@code originationDate} geçici olarak null'a çekilir; bu durumda 0 döner
     * ve ağırlık 1.0 olur — bilgi yokluğunda fren zayıflatılmaz.
     */
    private double ringAgeSeconds(Call call, double nowSerial) {
        Double origination = call.getOriginationDate();
        if (origination == null) return 0.0;
        // Her iki değer de Utils.convertDateToDouble ekseninde (gün cinsinden Excel serial),
        // dolayısıyla fark x 86400 = saniye. Allocation ve POI çağrısı yok.
        double ageSeconds = (nowSerial - origination) * SECONDS_PER_DAY;
        if (!Double.isFinite(ageSeconds)) return 0.0;
        return Math.max(0.0, ageSeconds);
    }

    /** Pending cache değer holder'ı: çağrı + originate'e ne kadar zaman önce yazıldığını taşır. */
    private static final class PendingRingingEntry {
        private final Call call;
        private final long addedAtMs;

        PendingRingingEntry(Call call, long addedAtMs) {
            this.call = call;
            this.addedAtMs = addedAtMs;
        }

        Call getCall() {
            return call;
        }

        long getAddedAtMs() {
            return addedAtMs;
        }
    }
}
