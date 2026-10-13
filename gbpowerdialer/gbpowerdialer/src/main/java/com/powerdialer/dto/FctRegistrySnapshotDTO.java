package com.powerdialer.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code GET /fctChannel/snapshot} yanıtı. Monitor ekranının render katmanı yalnız bu şemayı bilir.
 * {@code serverTimeMs}, istemcinin yaş ve cooldown geri sayımlarını kendi saat sapmasından bağımsız
 * hesaplaması içindir.
 */
public class FctRegistrySnapshotDTO {

  private long serverTimeMs;
  private long ttlMs;
  private long staleWarnMs;
  private long cooldownMs;
  private int headroomPercent;
  private boolean gateEnabled;
  private List<FctChannelEntryDTO> entries = new ArrayList<>();
  private List<FctChannelEventDTO> recentEvents = new ArrayList<>();
  private FctChannelKpiDTO kpi = new FctChannelKpiDTO();

  public long getServerTimeMs() {
    return serverTimeMs;
  }

  public void setServerTimeMs(long serverTimeMs) {
    this.serverTimeMs = serverTimeMs;
  }

  public long getTtlMs() {
    return ttlMs;
  }

  public void setTtlMs(long ttlMs) {
    this.ttlMs = ttlMs;
  }

  public long getStaleWarnMs() {
    return staleWarnMs;
  }

  public void setStaleWarnMs(long staleWarnMs) {
    this.staleWarnMs = staleWarnMs;
  }

  public long getCooldownMs() {
    return cooldownMs;
  }

  public void setCooldownMs(long cooldownMs) {
    this.cooldownMs = cooldownMs;
  }

  public int getHeadroomPercent() {
    return headroomPercent;
  }

  public void setHeadroomPercent(int headroomPercent) {
    this.headroomPercent = headroomPercent;
  }

  public boolean isGateEnabled() {
    return gateEnabled;
  }

  public void setGateEnabled(boolean gateEnabled) {
    this.gateEnabled = gateEnabled;
  }

  public List<FctChannelEntryDTO> getEntries() {
    return entries;
  }

  public void setEntries(List<FctChannelEntryDTO> entries) {
    this.entries = entries;
  }

  public List<FctChannelEventDTO> getRecentEvents() {
    return recentEvents;
  }

  public void setRecentEvents(List<FctChannelEventDTO> recentEvents) {
    this.recentEvents = recentEvents;
  }

  public FctChannelKpiDTO getKpi() {
    return kpi;
  }

  public void setKpi(FctChannelKpiDTO kpi) {
    this.kpi = kpi;
  }
}
