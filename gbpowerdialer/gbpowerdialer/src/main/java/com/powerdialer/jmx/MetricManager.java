package com.powerdialer.jmx;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Component for accessing and collecting metrics. Collected metrics is exposed
 * via JMX
 */
public class MetricManager {

  private static final MetricManager instance = new MetricManager();

  public static MetricManager getInstance() {
    return instance;
  }

  private Map<String, Long> campaignDatasourceLastQueryTimeMs = new ConcurrentHashMap<>();

  private Map<String, Long> serverLastOriginationTimeMs = new ConcurrentHashMap<>();

  /**
   * Save query time of specific campaign. Query time must be in milliseconds
   * @param campaignId - id of campaign
   * @param queryTimeMs - time spend on query execution
   */
  public void saveCampaignDatasourceQueryTime(Long campaignId, Long queryTimeMs) {
    if (campaignId != null) {
      campaignDatasourceLastQueryTimeMs.put(campaignId.toString(), queryTimeMs);
    }
  }

  /**
   * Get last query time of all campaigns.
   */
  public Map<String, Long> getCampaignDatasourceLastRequestTime() {
    return campaignDatasourceLastQueryTimeMs;
  }

  /**
   * Save origination time to specific server. Origination time must be in
   * milliseconds
   * @param serverIp - ip of sever
   * @param originationTimeMs - time spend on origination is milliseconds
   */
  public void saveOriginationTimeForServer(String serverIp, Long originationTimeMs) {
    if (serverIp != null) {
      serverLastOriginationTimeMs.put(serverIp, originationTimeMs);
    }
  }

  /**
   * Get last origination time to all servers
   */
  public Map<String, Long> getServersLastOriginationTime() {
    return serverLastOriginationTimeMs;
  }

}
