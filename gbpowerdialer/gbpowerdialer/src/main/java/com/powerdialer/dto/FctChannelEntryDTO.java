package com.powerdialer.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Monitor ekranında tek FCT numarasının anlık durumu. Bir numara = bir FCT olduğu için kapasite
 * anahtarı doğrudan {@code phoneNumber}'dır.
 */
public class FctChannelEntryDTO {

  private String phoneNumber;
  private String type;
  private String attempts;
  /** QSS'te tanımlı trunk; boşsa çağrı dialplan varsayılan trunk'ına gider. */
  private String trunk;
  private int capacity;
  private int effectiveCapacity;
  private List<FctInFlightDTO> inFlight = new ArrayList<>();
  private long cooldownUntilMs;
  private long lastEventMs;

  public String getPhoneNumber() {
    return phoneNumber;
  }

  public void setPhoneNumber(String phoneNumber) {
    this.phoneNumber = phoneNumber;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public String getAttempts() {
    return attempts;
  }

  public void setAttempts(String attempts) {
    this.attempts = attempts;
  }

  public String getTrunk() {
    return trunk;
  }

  public void setTrunk(String trunk) {
    this.trunk = trunk;
  }

  public int getCapacity() {
    return capacity;
  }

  public void setCapacity(int capacity) {
    this.capacity = capacity;
  }

  public int getEffectiveCapacity() {
    return effectiveCapacity;
  }

  public void setEffectiveCapacity(int effectiveCapacity) {
    this.effectiveCapacity = effectiveCapacity;
  }

  public List<FctInFlightDTO> getInFlight() {
    return inFlight;
  }

  public void setInFlight(List<FctInFlightDTO> inFlight) {
    this.inFlight = inFlight;
  }

  public long getCooldownUntilMs() {
    return cooldownUntilMs;
  }

  public void setCooldownUntilMs(long cooldownUntilMs) {
    this.cooldownUntilMs = cooldownUntilMs;
  }

  public long getLastEventMs() {
    return lastEventMs;
  }

  public void setLastEventMs(long lastEventMs) {
    this.lastEventMs = lastEventMs;
  }
}
