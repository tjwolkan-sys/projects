package com.powerdialer.engine;

import com.powerdialer.engine.acd.ACDEngine;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.User;
import com.powerdialer.redis.RedisUtil;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Task 325654: {@code Campaign.incallAgents} sayacinin coalesce edilmis (debounce'lu)
 * yenileyicisi.
 *
 * <p><b>Sorun.</b> Onceden {@code UserSubscriber.handleStatusChange} ve
 * {@code UserLoginLogoutHandler} her agent status/login/logout olayinda TUM campaign
 * context'lerini dolasiyor, her eslesen kampanya icin o kampanyanin butun agent
 * populasyonunu Redis'ten bulk okuyordu. Maliyet "olay x eslesen kampanya x agent"
 * seklinde carpiliyordu; 30.07.2026 uretim kaydinda burst aninda Redis inbound
 * 10 MB/s tepeye vuruyor, CPU %91'e cikip sonra toparliyordu.</p>
 *
 * <p><b>Cozum.</b> Olay artik yalniz kampanyayi "dirty" isaretler: Redis cagrisi yok,
 * koleksiyon taramasi yok, sadece bir {@code ConcurrentHashMap.put}. Gercek yeniden
 * hesap sabit araliklarla ve dirty kampanya basina en fazla bir kez yapilir.</p>
 *
 * <p><b>Neden kendi executor'i.</b> {@code applicationContext.xml} icindeki
 * {@code task:annotation-driven} tanimi {@code scheduler} belirtmez; bu nedenle tum
 * {@code @Scheduled} metotlari tek bir paylasilan thread uzerinde calisir
 * ({@code SharedGammaSnapshotProvider} 2 sn, {@code UserSynchronizer} 20 sn,
 * {@code AgentSizeConsistencyChecker} 30 sn). 500 ms'lik bir gorevi o thread'e eklemek
 * bu isleri ac birakabilirdi. Bu yuzden {@code RedisUtil.pingScheduledExecutorService}
 * ile ayni desen kullanilarak izole, tek thread'li ve daemon bir executor acilir.
 * {@code scheduleWithFixedDelay} kullanilir; boylece yavas bir tur kuyruk birikmesine
 * yol acmaz.</p>
 *
 * <p><b>Bayatlamaya karsi garanti.</b> {@code incallAgents} sayacinin periyodik bir
 * reconciler'i yoktur ({@code AgentSizeConsistencyChecker} yalniz {@code agentsSize}'a
 * bakar ve sadece log atar). Bu nedenle debounce hicbir guncellemeyi dusurmemelidir:
 * dirty set hesaptan ONCE bosaltilir, dolayisiyla hesap sirasinda gelen yeni bir olay
 * kampanyayi tekrar dirty yapar ve bir sonraki turda mutlaka yeniden hesaplanir.</p>
 */
@Component
@Lazy(false)
public class IncallAgentsRefresher {

  private static final Logger LOG = LoggerFactory.getLogger(IncallAgentsRefresher.class);

  /** Kampanya basina en fazla bu aralikta bir yeniden hesap yapilir. */
  private static final long REFRESH_DELAY_MS = 500L;

  private final Map<Long, Campaign> dirtyCampaignsById = new ConcurrentHashMap<>();

  private ScheduledExecutorService refreshExecutor;

  @Autowired
  private ACDEngine acdEngine;

  @Autowired
  private RedisUtil redisUtil;

  @PostConstruct
  public void init() {
    ThreadFactory threadFactory = new BasicThreadFactory.Builder()
        .namingPattern("incall-agents-refresher-%d").daemon(true).build();
    refreshExecutor = Executors.newSingleThreadScheduledExecutor(threadFactory);
    refreshExecutor.scheduleWithFixedDelay(this::runRefreshCycle,
        REFRESH_DELAY_MS, REFRESH_DELAY_MS, TimeUnit.MILLISECONDS);
  }

  @PreDestroy
  public void shutdown() {
    if (refreshExecutor != null) {
      refreshExecutor.shutdownNow();
    }
  }

  /**
   * Kampanyayi yeniden hesap icin isaretler. Bu metot cagri/status hot path'inde
   * calistigi icin hicbir IO, lock veya koleksiyon taramasi yapmaz.
   */
  public void markDirty(Campaign campaign) {
    if (campaign == null || campaign.getId() == null) {
      return;
    }
    dirtyCampaignsById.put(campaign.getId(), campaign);
  }

  /**
   * {@code scheduleWithFixedDelay} gorevden exception cikarsa gorevi KALICI olarak iptal
   * eder. Dongunun olmemesi icin burada her sey yakalanir.
   */
  private void runRefreshCycle() {
    try {
      refreshDirtyCampaigns();
    } catch (Throwable t) {
      LOG.warn("[INCALL-REFRESH] Refresh cycle failed: {}", t.getMessage());
    }
  }

  /**
   * Dirty kampanyalari bosaltip her biri icin {@code incallAgents} degerini yeniden
   * hesaplar. Registered-user snapshot'i tur basina bir kez alinir.
   */
  public void refreshDirtyCampaigns() {
    List<Campaign> dueCampaigns = drainDirtyCampaigns();
    if (dueCampaigns.isEmpty()) {
      return;
    }
    Map<Long, List<User>> activeUsersByCampaignId =
        acdEngine.getCampaignAllActiveUsersByCampaignId(dueCampaigns);
    for (Campaign campaign : dueCampaigns) {
      applyIncallAgents(campaign, activeUsersByCampaignId.get(campaign.getId()));
    }
  }

  /**
   * Dirty seti hesaptan ONCE bosaltir. Hesap sirasinda gelen yeni bir olay kampanyayi
   * tekrar dirty yapar; boylece hicbir guncelleme dusmez.
   */
  private List<Campaign> drainDirtyCampaigns() {
    if (dirtyCampaignsById.isEmpty()) {
      return Collections.emptyList();
    }
    List<Campaign> drained = new ArrayList<>(dirtyCampaignsById.size());
    for (Long campaignId : new ArrayList<>(dirtyCampaignsById.keySet())) {
      Campaign campaign = dirtyCampaignsById.remove(campaignId);
      if (campaign != null) {
        drained.add(campaign);
      }
    }
    return drained;
  }

  private void applyIncallAgents(Campaign campaign, List<User> activeUsers) {
    try {
      Set<Long> userIds = activeUsers == null
          ? Collections.<Long>emptySet()
          : activeUsers.stream().map(User::getId).filter(Objects::nonNull).collect(Collectors.toSet());
      campaign.setIncallAgents((int) redisUtil.getUsersStatusBulk(userIds).getIncallAgents());
    } catch (Exception e) {
      LOG.warn("[INCALL-REFRESH] Could not update incall agents for campaign {}: {}",
          campaign.getId(), e.getMessage());
    }
  }
}
