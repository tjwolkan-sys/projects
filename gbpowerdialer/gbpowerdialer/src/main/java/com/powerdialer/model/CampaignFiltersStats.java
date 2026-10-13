package com.powerdialer.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.powerdialer.util.Constants;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class CampaignFiltersStats {

  private int cacheLimit;
  private int callsQueueSize;
  private LocalDateTime lastFetchTime;
  private List<FilterStats> filtersStats;

  public int getCacheLimit() {
    return cacheLimit;
  }

  public void setCacheLimit(int cacheLimit) {
    this.cacheLimit = cacheLimit;
  }

  public int getCallsQueueSize() {
    return callsQueueSize;
  }

  public void setCallsQueueSize(int callsQueueSize) {
    this.callsQueueSize = callsQueueSize;
  }

  @JsonIgnore
  public LocalDateTime getLastFetchTimeRaw() {
    return lastFetchTime;
  }

  public void setLastFetchTime(LocalDateTime lastFetchTime) {
    this.lastFetchTime = lastFetchTime;
  }

  public String getLastFetchTime() {
    if (lastFetchTime == null) {
      return null;
    }
    return lastFetchTime.format(DateTimeFormatter.ofPattern(Constants.DATA_FORMAT_WITH_DATE));
  }

  public List<FilterStats> getFiltersStats() {
    return filtersStats;
  }

  public void setFiltersStats(List<FilterStats> filtersStats) {
    this.filtersStats = filtersStats;
  }

  public static class FilterStats {

    private Long id;
    private int fetchedAmount;
    private int callbacksAmount;
    private int retriesAmount;
    private int freshCallsAmount;
    private double fetchedPercentage;

    public FilterStats(
        Long id,
        int fetchedAmount,
        int callbacksAmount,
        int retriesAmount,
        int freshCallsAmount,
        double fetchedPercentage
    ) {
      this.id = id;
      this.fetchedAmount = fetchedAmount;
      this.callbacksAmount = callbacksAmount;
      this.retriesAmount = retriesAmount;
      this.freshCallsAmount = freshCallsAmount;
      this.fetchedPercentage = fetchedPercentage;
    }

    public Long getId() {
      return id;
    }

    public void setId(Long id) {
      this.id = id;
    }

    public int getFetchedAmount() {
      return fetchedAmount;
    }

    public void setFetchedAmount(int fetchedAmount) {
      this.fetchedAmount = fetchedAmount;
    }

    public int getCallbacksAmount() {
      return callbacksAmount;
    }

    public void setCallbacksAmount(int callbacksAmount) {
      this.callbacksAmount = callbacksAmount;
    }

    public int getRetriesAmount() {
      return retriesAmount;
    }

    public void setRetriesAmount(int retriesAmount) {
      this.retriesAmount = retriesAmount;
    }

    public int getFreshCallsAmount() {
      return freshCallsAmount;
    }

    public void setFreshCallsAmount(int freshCallsAmount) {
      this.freshCallsAmount = freshCallsAmount;
    }

    public double getFetchedPercentage() {
      return fetchedPercentage;
    }

    public void setFetchedPercentage(double fetchedPercentage) {
      this.fetchedPercentage = fetchedPercentage;
    }

  }

}
