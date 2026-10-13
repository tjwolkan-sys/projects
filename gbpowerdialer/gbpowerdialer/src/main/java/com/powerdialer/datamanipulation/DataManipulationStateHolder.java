package com.powerdialer.datamanipulation;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.powerdialer.model.Campaign;
import com.powerdialer.model.UploadToolContext;

@Component
public class DataManipulationStateHolder {

  private static final Logger LOG = LoggerFactory.getLogger(DataManipulationStateHolder.class);

  private final Map<Long, UploadToolContext> uploadToolContexts = new ConcurrentHashMap<>();
  private final Map<Long, AtomicInteger> apiUploadProcesses = new ConcurrentHashMap<>();

  public void addApiUploadProcess(Long campaignId) {
    int processesCount = apiUploadProcesses.computeIfAbsent(campaignId, v -> new AtomicInteger(0)).incrementAndGet();
    LOG.info("Started data insertion process for campaign " + campaignId + ". Processes total: " + processesCount);
  }

  public void removeApiUploadProcess(Long campaignId) {
    int processesCount = apiUploadProcesses.get(campaignId).decrementAndGet();
    LOG.info("Stopped data insertion process for campaign " + campaignId + ". Processes total: " + processesCount);
  }

  public UploadToolContext getOrCreateUploadToolContext(Campaign campaign) {
    return uploadToolContexts.computeIfAbsent(campaign.getId(), v -> {
      LOG.info("Created upload tool context for campaign " + campaign.getId());
      return new UploadToolContext(campaign);
    });
  }

  public UploadToolContext getUploadToolContext(Long campaignId) {
    return uploadToolContexts.get(campaignId);
  }

  public UploadToolContext removeUploadToolContext(Long campaignId) {
    UploadToolContext result = uploadToolContexts.remove(campaignId);
    if (result != null) {
      LOG.info("Removed upload tool context for campaign " + campaignId);
    } else {
      LOG.warn("Could not find upload tool context to remove for campaign " + campaignId);
    }
    return result;
  }

  public boolean isDataUploading(Long campaignId) {
    return uploadToolContexts.containsKey(campaignId) || apiUploadProcesses.getOrDefault(campaignId, new AtomicInteger(0)).get() > 0;
  }

}
