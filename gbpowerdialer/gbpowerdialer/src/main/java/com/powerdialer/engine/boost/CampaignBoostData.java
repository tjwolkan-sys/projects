package com.powerdialer.engine.boost;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.powerdialer.model.Campaign;

public class CampaignBoostData {

  private final Lock lock = new ReentrantLock(true);
  private ScheduledFuture<?> recalculationFuture;
  private Campaign campaign;
  private PeriodicBoostData currentData;
  private PeriodicBoostData previousData;
  private final List<PeriodicBoostData> dataHistory = new CopyOnWriteArrayList<>(); // TODO remove

  public CampaignBoostData(Campaign campaign) {
    this.campaign = campaign;
  }

  public ScheduledFuture<?> getRecalculationFuture() {
    return recalculationFuture;
  }

  public void setRecalculationFuture(ScheduledFuture<?> scheduledFuture) {
    this.recalculationFuture = scheduledFuture;
  }

  public Campaign getCampaign() {
    return campaign;
  }

  public void setCampaign(Campaign campaign) {
    this.campaign = campaign;
  }

  public PeriodicBoostData getCurrentData() {
    return currentData;
  }

  public void setCurrentData(PeriodicBoostData currentData) {
    this.currentData = currentData;
  }

  public PeriodicBoostData getPreviousData() {
    return previousData;
  }

  public void setPreviousData(PeriodicBoostData previousData) {
    this.previousData = previousData;
  }

  public void restartCalculation(float boostValue) {
    if (currentData != null) {
      previousData = currentData;
      dataHistory.add(previousData);
      if (dataHistory.size() > 500) {
        dataHistory.remove(0);
      }
    }
    currentData = new PeriodicBoostData(boostValue);
  }

  public List<PeriodicBoostData> getDataHistory() {
    return dataHistory;
  }

  public void lock() {
    lock.lock();
  }

  public void unlock() {
    lock.unlock();
  }

  @Override
  public String toString() {
    return "DynamicBoostData{" +
        "currentData=" + currentData +
        ", previousData=" + previousData +
        '}';
  }

}
