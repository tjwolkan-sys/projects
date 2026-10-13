package com.powerdialer.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.sql.Connection;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections4.queue.CircularFifoQueue;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.powerdialer.annotation.OptionalIsolationLevel;
import com.powerdialer.dao.IActionLogDao;
import com.powerdialer.dto.ActionLogDTO;
import com.powerdialer.model.ActionLog;
import com.powerdialer.util.ListDetailed;
import com.powerdialer.validators.ActionLogValidator;

/**
 * Service layer for ActionLog. Holds cache for latest n items in memory per
 * Tenant and in general.
 */
@Service
public class ActionLogService extends GenericService<ActionLog, ActionLogDTO, ActionLogDTO> {

  private final int CACHE_LOG_SIZE = 100;
  private Collection<ActionLog> cachedActionLogs = CollectionUtils.synchronizedCollection(new CircularFifoQueue<>(CACHE_LOG_SIZE));
  private Map<Long, Collection<ActionLog>> cachedActionLogsByTenant = new HashMap<>();

  @Autowired
  public ActionLogService(IActionLogDao dao) {
    super(dao, ActionLog.class, ActionLogDTO.class, ActionLogDTO.class, new ActionLogValidator());
  }

  @Transactional(readOnly = true)
  @OptionalIsolationLevel(level = Connection.TRANSACTION_READ_UNCOMMITTED)
  @Override
  public List<ActionLog> getAll() {
    return super.getAll();
  }

  @Transactional(readOnly = true)
  @OptionalIsolationLevel(level = Connection.TRANSACTION_READ_UNCOMMITTED)
  @Override
  public List<ActionLog> getAll(Boolean enabled, Boolean deleted) {
    return super.getAll(enabled, deleted);
  }

  @Transactional(readOnly = true)
  @OptionalIsolationLevel(level = Connection.TRANSACTION_READ_UNCOMMITTED)
  @Override
  public ListDetailed<ActionLogDTO> getAll(JSONObject page, JSONObject sort, JSONObject filters) {
    return super.getAll(page, sort, filters);
  }

  /**
   * Add action log to cache and save to db
   */
  @Async
  @Transactional(readOnly = false)
  public void addActionLog(ActionLog log) {
    addActionLogToCache(log);
    getDao().save(log);
  }

  /**
   * Add action log to cache queue of tenant or to all if log has no tenant
   * @param log
   */
  private void addActionLogToCache(ActionLog log) {
    if (log.getTenant() != null) {
      getCacheQueueByTenantId(log.getTenant().getId()).add(log);
    } else {
      for (Collection<ActionLog> cache : cachedActionLogsByTenant.values()) {
        cache.add(log);
      }
    }
    cachedActionLogs.add(log);
  }

  /**
   * Get cache queue by tenant
   * @param tenantId
   * @return Queue
   */
  @Transactional(propagation = Propagation.SUPPORTS)
  public Collection<ActionLog> getCacheQueueByTenantId(Long tenantId) {
    if (cachedActionLogsByTenant.containsKey(tenantId))
      return cachedActionLogsByTenant.get(tenantId);

    Collection<ActionLog> cache = CollectionUtils.synchronizedCollection(new CircularFifoQueue<>(CACHE_LOG_SIZE));
    cachedActionLogsByTenant.put(tenantId, cache);
    return cache;
  }

  /**
   * Get cached action logs regardless of tenant
   * @return Collection<ActionLog>
   */
  @Transactional(propagation = Propagation.SUPPORTS)
  public Collection<ActionLog> getCachedActionLogs() {
    return cachedActionLogs;
  }

  /**
   * Add custom filters
   */
  @Override
  public JSONObject addCustomFilters(JSONObject filters) {
    filters = super.addCustomFilters(filters);
    return filters;
  }
}
