package com.powerdialer.engine.dialer;

import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.Filter;
import com.powerdialer.model.CampaignFiltersStats;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class CampaignFilterStatsCalculator {

  public CampaignFiltersStats calculateStats(Campaign campaign, Collection<Call> calls, LocalDateTime lastFetchTime) {
    CampaignFiltersStats stats = new CampaignFiltersStats();
    stats.setCallsQueueSize(calls.size());
    stats.setLastFetchTime(lastFetchTime);
    stats.setCacheLimit(campaign.getCacheLimit());

    Set<Long> segmentationFilterIds = extractSegmentationFilterIds(campaign);
    Map<Long, CampaignFiltersStats.FilterStats> statsMap = initializeFilterStatsMap(segmentationFilterIds);

    populateFilterStatsFromCalls(calls, statsMap, segmentationFilterIds);
    calculatePercentages(statsMap, calls.size());

    stats.setFiltersStats(getSegmentationFilterStats(statsMap));

    return stats;
  }

  private Set<Long> extractSegmentationFilterIds(Campaign campaign) {
    return campaign.getEnabledFiltersBySegmentation(true).stream()
        .map(Filter::getId)
        .collect(Collectors.toSet());
  }

  private Map<Long, CampaignFiltersStats.FilterStats> initializeFilterStatsMap(Set<Long> filterIds) {
    Map<Long, CampaignFiltersStats.FilterStats> map = new HashMap<>();
    for (Long id : filterIds) {
      map.put(id, new CampaignFiltersStats.FilterStats(id, 0, 0, 0, 0, 0));
    }
    return map;
  }

  private void populateFilterStatsFromCalls(Collection<Call> calls,
                                            Map<Long, CampaignFiltersStats.FilterStats> statsMap,
                                            Set<Long> segmentationFilterIds) {
    for (Call call : calls) {
      Long filterId = determineFilterId(call, segmentationFilterIds);
      CampaignFiltersStats.FilterStats stat = statsMap.computeIfAbsent(
          filterId, id -> new CampaignFiltersStats.FilterStats(id, 0, 0, 0, 0, 0)
      );
      stat.setFetchedAmount(stat.getFetchedAmount() + 1);
      incrementCallTypeCount(stat, call);
    }
  }

  private Long determineFilterId(Call call, Set<Long> segmentationFilterIds) {
    if (call.getAppliedSegmentationFilter() != null) {
      Long id = call.getAppliedSegmentationFilter().getId();
      if (segmentationFilterIds.contains(id)) {
        return id;
      }
    }
    return -1L;
  }

  private void incrementCallTypeCount(CampaignFiltersStats.FilterStats stat, Call call) {
    switch (call.getCallType()) {
      case CALLBACK:
        stat.setCallbacksAmount(stat.getCallbacksAmount() + 1);
        break;
      case RETRY:
        stat.setRetriesAmount(stat.getRetriesAmount() + 1);
        break;
      case FRESH:
        stat.setFreshCallsAmount(stat.getFreshCallsAmount() + 1);
        break;
      default:
        break;
    }
  }

  private void calculatePercentages(Map<Long, CampaignFiltersStats.FilterStats> statsMap, int totalCalls) {
    for (CampaignFiltersStats.FilterStats stat : statsMap.values()) {
      double percent = totalCalls > 0
          ? (double) stat.getFetchedAmount() * 100 / totalCalls
          : 0;
      stat.setFetchedPercentage(percent);
    }
  }

  private List<CampaignFiltersStats.FilterStats> getSegmentationFilterStats(Map<Long, CampaignFiltersStats.FilterStats> statsMap) {
    return statsMap.entrySet().stream()
        .filter(entry -> entry.getKey() != -1L)
        .map(Map.Entry::getValue)
        .collect(Collectors.toList());
  }

}
