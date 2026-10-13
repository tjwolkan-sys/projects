package com.powerdialer.engine.priority;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import java.sql.SQLException;
import javax.annotation.PreDestroy;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import com.powerdialer.engine.db.CampaignDbOperations;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.Filter;
import com.powerdialer.model.SortField;
import com.powerdialer.model.SortFieldValue;
import com.powerdialer.publish.CampaignMergedEvent;
import com.powerdialer.service.CampaignService;
import com.powerdialer.util.Constants;

@Component
public class PriorityRecalculationManager {

  private static final Logger LOG = LoggerFactory.getLogger(PriorityRecalculationManager.class);
  private static final int MAX_CONCURRENT_RECALCULATIONS = 50;
  private static final long RECALCULATION_TIMEOUT_MIN = 120L;

  private final ExecutorService executorService = createExecutor();

  @Autowired
  private PriorityService sortPriorityService;

  @Autowired
  private CampaignService campaignService;

  @Autowired
  private PriorityRecalculationStateHolder priorityStateHolder;

  @Autowired
  private CampaignDbOperations campaignDbOperations;

  private ExecutorService createExecutor() {
    ThreadFactory threadFactory = new ThreadFactoryBuilder().setDaemon(true)
        .setNameFormat("priority-recalculation-%d")
        .build();
    return new ThreadPoolExecutor(1, MAX_CONCURRENT_RECALCULATIONS, RECALCULATION_TIMEOUT_MIN,
        TimeUnit.MINUTES, new SynchronousQueue<>(true), threadFactory);
  }

  @PreDestroy
  private void cleanUp() {
    executorService.shutdownNow();
  }

  @EventListener(CampaignMergedEvent.class)
  public void onCampaignMerged(CampaignMergedEvent event) {
    if (shouldRecalculatePrioritiesOnCampaignChange(event.getOldCampaign(), event.getNewCampaign())) {
      recalculatePriority(event.getNewCampaign());
    }
  }

  private boolean shouldRecalculatePrioritiesOnCampaignChange(Campaign oldCampaign, Campaign newCampaign) {
    if (!newCampaign.isMultipleSort()) {
      return false;
    }
    return oldCampaign == null || !oldCampaign.isMultipleSort()
        || areSortFieldsChanged(newCampaign, oldCampaign) || areFiltersChanged(newCampaign, oldCampaign);
  }

  private boolean areSortFieldsChanged(Campaign newCampaign, Campaign oldCampaign) {
    List<SortField> newCampaignSortFields = sortPriorityService.getEnabledFieldsStream(newCampaign).collect(Collectors.toList());
    List<SortField> oldCampaignSortFields = sortPriorityService.getEnabledFieldsStream(oldCampaign).collect(Collectors.toList());
    return newCampaignSortFields.size() != oldCampaignSortFields.size() || areSortFieldsChanged(newCampaignSortFields, oldCampaignSortFields);
  }

  private boolean areSortFieldsChanged(List<SortField> newCampaignSortFields, List<SortField> oldCampaignSortFields) {
    for (SortField oldCampaignSortField : oldCampaignSortFields) {
      Optional<SortField> fieldOptional = newCampaignSortFields.stream().filter(field -> oldCampaignSortField.getId().equals(field.getId()))
          .findAny();
      if (!fieldOptional.isPresent() || areSortFieldsChanged(fieldOptional.get(), oldCampaignSortField)) {
        return true;
      }
    }
    return false;
  }

  private boolean areSortFieldsChanged(SortField newSortField, SortField oldSortField) {
    List<SortFieldValue> newSortFieldValues = sortPriorityService.getEnabledValuesStream(newSortField.getValues()).collect(Collectors.toList());
    List<SortFieldValue> oldSortFieldValues = sortPriorityService.getEnabledValuesStream(oldSortField.getValues()).collect(Collectors.toList());
    return newSortField.getPriority() != oldSortField.getPriority()
        || newSortFieldValues.size() != oldSortFieldValues.size()
        || areSortFieldValuesChanged(newSortFieldValues, oldSortFieldValues);
  }

  private boolean areSortFieldValuesChanged(List<SortFieldValue> newSortFieldValues, List<SortFieldValue> oldSortFieldValues) {
    for (SortFieldValue oldCampaignSortValue : oldSortFieldValues) {
      Optional<SortFieldValue> valueOptional = newSortFieldValues.stream().filter(field -> oldCampaignSortValue.getId().equals(field.getId()))
          .findAny();
      if (!valueOptional.isPresent() || !valueOptional.get().getValue().equals(oldCampaignSortValue.getValue())) {
        return true;
      }
    }
    return false;
  }

  private boolean areFiltersChanged(Campaign newCampaign, Campaign oldCampaign) {
    List<Filter> newCampaignFilters = newCampaign.getEnabledNotDeletedFilters();
    List<Filter> oldCampaignFilters = oldCampaign.getEnabledNotDeletedFilters();
    return newCampaign.getFilterJoinType() != oldCampaign.getFilterJoinType()
        || newCampaignFilters.size() != oldCampaignFilters.size()
        || areFiltersChanged(newCampaignFilters, oldCampaignFilters);
  }

  private boolean areFiltersChanged(List<Filter> newCampaignFilters, List<Filter> oldCampaignFilters) {
    for (Filter oldCampaignFilter : oldCampaignFilters) {
      Optional<Filter> filterOptional = newCampaignFilters.stream().filter(field -> oldCampaignFilter.getId().equals(field.getId())).findAny();
      if (!filterOptional.isPresent() || isFilterChanged(filterOptional.get(), oldCampaignFilter)) {
        return true;
      }
    }
    return false;
  }

  private boolean isFilterChanged(Filter newFilter, Filter oldFilter) {
    return newFilter.getCondition() != oldFilter.getCondition()
        || newFilter.getType() != oldFilter.getType()
        || newFilter.getTechnical() != oldFilter.getTechnical()
        || !newFilter.getValue().equals(oldFilter.getValue())
        || !newFilter.getField().equals(oldFilter.getField());
  }

  public void onDataUploaded(Long campaignId) {
    Campaign campaign = campaignService.getInitializedCampaign(campaignId);
    if (campaign != null) {
      if (campaign.isMultipleSort() && StringUtils.isNotBlank(campaign.getDbPriorityTable())) {
        recalculatePriority(campaign);
      }
    } else {
      LOG.warn("Could not find campaign with id " + campaignId);
    }
  }

  public Constants.PRIORITY_RECALCULATION_STATE recalculatePriority(Long campaignId) {
    LOG.info("Got recalculation request for campaign with id " + campaignId);
    Constants.PRIORITY_RECALCULATION_STATE newState = updateRecalculationStateOnStart(campaignId);
    if (newState == Constants.PRIORITY_RECALCULATION_STATE.EXECUTING) {
      executorService.submit(() -> recalculate(campaignId));
    }
    return newState;
  }

  private void recalculatePriority(Campaign campaign) {
    LOG.info("Got recalculation request for " + campaign);
    Constants.PRIORITY_RECALCULATION_STATE newState = updateRecalculationStateOnStart(campaign.getId());
    if (newState == Constants.PRIORITY_RECALCULATION_STATE.EXECUTING) {
      executorService.submit(() -> recalculate(campaign));
    }
  }

  private synchronized Constants.PRIORITY_RECALCULATION_STATE updateRecalculationStateOnStart(Long campaignId) {
    Constants.PRIORITY_RECALCULATION_STATE currentState = priorityStateHolder.get(campaignId);
    if (!checkForConcurrentRecalculationsCount()) {
      return Constants.PRIORITY_RECALCULATION_STATE.DECLINED;
    } else if (currentState == Constants.PRIORITY_RECALCULATION_STATE.EXPECTING) {
      LOG.info("Ignoring recalculation request for campaign " + campaignId + ": already expecting recalculation while performing one");
      return Constants.PRIORITY_RECALCULATION_STATE.EXPECTING;
    } else if (currentState == Constants.PRIORITY_RECALCULATION_STATE.EXECUTING) {
      LOG.info("New recalculation will be performed after the current one for campaign " + campaignId);
      priorityStateHolder.put(campaignId, Constants.PRIORITY_RECALCULATION_STATE.EXPECTING);
      return Constants.PRIORITY_RECALCULATION_STATE.EXPECTING;
    } else if (currentState == null) {
      priorityStateHolder.put(campaignId, Constants.PRIORITY_RECALCULATION_STATE.EXECUTING);
      return Constants.PRIORITY_RECALCULATION_STATE.EXECUTING;
    } else {
      throw new IllegalStateException("Unknown recalculation state for campaign " + campaignId + ": " + currentState);
    }
  }

  private void recalculate(Long campaignId) {
    try {
      Campaign campaign = campaignService.getInitializedCampaign(campaignId);
      if (campaign == null) {
        LOG.warn("Could not recalculate: campaign with id " + campaignId + " is not found");
      } else {
        doRecalculation(campaign);
      }
    } catch (Exception e) {
      LOG.error("Could not recalculate campaign " + campaignId, e);
    } finally {
      onRecalculationFinish(campaignId);
    }
  }

  private void recalculate(Campaign campaign) {
    try {
      doRecalculation(campaign);
    } catch (Exception e) {
      LOG.error("Could not recalculate campaign " + campaign, e);
    } finally {
      onRecalculationFinish(campaign.getId());
    }
  }

  private void doRecalculation(Campaign campaign) throws SQLException {
    LOG.info("Recalculating priorities: " + campaign);
    checkForCampaignSettings(campaign);
    long startTime = System.currentTimeMillis();
    List<SortField> sortFields = sortPriorityService.getOrderedSortFields(campaign);
    LOG.info("Clearing priority table for " + campaign);
    campaignDbOperations.clearPriorityTable(campaign);
    if (CollectionUtils.isEmpty(sortFields)) {
      LOG.info("Could not recalculate priorities for " + campaign + ": no active sort fields");
      return;
    }
    LOG.info("Creating empty priority records for " + campaign);
    campaignDbOperations.createEmptyPriorityRecords(campaign);
    LOG.info("Performing priority recalculation request for " + campaign);
    campaignDbOperations.updatePriorities(campaign, sortFields);
    long now = System.currentTimeMillis();
    LOG.info("Priorities recalculated in " + (now - startTime) / 1000 + "s for " + campaign);
  }

  private void checkForCampaignSettings(Campaign campaign) {
    if (!campaign.isMultipleSort())
      throw new IllegalStateException("Could not recalculate " + campaign + ": wrong 'Sort By' option (" + campaign.getColSortBy() + ")");
  }

  private void onRecalculationFinish(Long campaignId) {
    Constants.PRIORITY_RECALCULATION_STATE currentState = updateRecalculationStateOnFinish(campaignId);
    if (currentState == Constants.PRIORITY_RECALCULATION_STATE.EXPECTING) {
      LOG.info("Recalculating once again for campaign " + campaignId);
      recalculate(campaignId);
    }
  }

  private synchronized Constants.PRIORITY_RECALCULATION_STATE updateRecalculationStateOnFinish(Long campaignId) {
    Constants.PRIORITY_RECALCULATION_STATE currentState = priorityStateHolder.get(campaignId);
    if (currentState == Constants.PRIORITY_RECALCULATION_STATE.EXECUTING) {
      priorityStateHolder.remove(campaignId);
    } else if (currentState == Constants.PRIORITY_RECALCULATION_STATE.EXPECTING) {
      priorityStateHolder.put(campaignId, Constants.PRIORITY_RECALCULATION_STATE.EXECUTING);
    } else {
      LOG.warn("Wrong recalculation state for " + campaignId + " on recalculation finish: " + currentState);
    }
    return currentState;
  }

  private boolean checkForConcurrentRecalculationsCount() {
    int count = priorityStateHolder.size();
    if (count >= MAX_CONCURRENT_RECALCULATIONS) {
      LOG.warn("Too many concurrent recalculations: " + count);
      return false;
    }
    return true;
  }

}
