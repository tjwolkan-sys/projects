package com.powerdialer.engine;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.powerdialer.dao.IJobConfigDao;
import com.powerdialer.dto.JobConfigCampaignDTO;
import com.powerdialer.dto.JobConfigFilterDTO;
import com.powerdialer.engine.db.CampaignDbOperations;
import com.powerdialer.engine.db.DBEngine;
import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.ParametrizedQuery;
import com.powerdialer.service.CompositeWhereClauseBuilder;
import com.powerdialer.service.SqlWhitelist;
import com.powerdialer.util.EnCipherUtils;
import com.powerdialer.util.ParamSql;

/**
 * Just-In-Time expire validator.
 *
 * Runs during dialer fetch (after DNC, before queueing). For each fetched Call
 * in an expire-enabled campaign, evaluates the configured expire criteria
 * per JobConfig and persists the disabled state using that JobConfig's
 * result_code_id / expire_user_id (mirrors CampaignResultJobService#getSetSql).
 *
 * Pattern mirrors {@link DNCEngine#applyDncToCalls(Campaign, java.util.Collection)}.
 *
 * Performance: a single batch SELECT per JobConfig per fetch. Filter SQL and
 * connection info are cached per campaign with 60s TTL (DNC parallel).
 */
@Component("jitExpireValidator")
@Lazy(false)
public class JITExpireValidator {

  private static final Logger LOG = LoggerFactory.getLogger(JITExpireValidator.class);
  private static final long FILTER_CACHE_TTL_MS = 60_000L;

  @Autowired
  private CampaignDbOperations campaignDbOperations;

  @Autowired
  private IJobConfigDao jobConfigDao;

  @Autowired
  private DBEngine dbEngine;

  @Autowired
  private EnCipherUtils enCipherUtils;

  @Autowired
  private PlatformTransactionManager transactionManager;

  private TransactionTemplate transactionTemplate;

  private final ConcurrentMap<Long, CachedFilter> filterCache = new ConcurrentHashMap<>();

  @PostConstruct
  public void init() {
    this.transactionTemplate = new TransactionTemplate(transactionManager);
    this.transactionTemplate.setReadOnly(true);
  }

  /**
   * Filter the given candidate list, removing rows that match expire criteria
   * (per JobConfig) and persisting their disabled state.
   *
   * Safe to call with calls = null/empty. No-op if campaign flag is off.
   */
  public void applyExpireFilterToCalls(Campaign campaign, Collection<Call> calls) {
    if (campaign == null || calls == null || calls.isEmpty()) return;
    if (!Boolean.TRUE.equals(campaign.getExpireFilterEnabled())) return;

    CachedFilter cf;
    try {
      cf = loadOrRefreshFilter(campaign);
    } catch (Exception e) {
      LOG.error("JIT expire load failed for campaign {}", campaign.getId(), e);
      return;
    }
    if (cf == null || cf.slices.isEmpty()) return;

    for (JobConfigSlice slice : cf.slices) {
      if (slice.where == null || slice.where.getSql() == null || slice.where.getSql().isEmpty()) continue;

      Set<String> expiredIds;
      try {
        expiredIds = findExpiredRowIdsForSlice(campaign, calls, cf, slice);
      } catch (Exception e) {
        LOG.error("JIT expire check failed for campaign {} jobConfig {}", campaign.getId(), slice.jobConfigId, e);
        continue;
      }
      if (expiredIds.isEmpty()) continue;

      Set<Call> filtered = new HashSet<>();
      Iterator<Call> it = calls.iterator();
      while (it.hasNext()) {
        Call c = it.next();
        if (c.getUniqueKey() != null && expiredIds.contains(c.getUniqueKey())) {
          filtered.add(c);
          it.remove();
        }
      }

      if (!filtered.isEmpty()) {
        try {
          campaignDbOperations.setFilteredByExpireStatus(campaign, filtered,
                  slice.resultCodeId, slice.expireUserId);
        } catch (SQLException e) {
          LOG.error("Failed to persist expire status for campaign {} jobConfig {}",
                  campaign.getId(), slice.jobConfigId, e);
        }
      }
    }
  }

  /**
   * SELECT id FROM <table> WHERE id IN (uniqueKeys) AND (<slice.where>)
   * Single round-trip per JobConfig slice.
   */
  private Set<String> findExpiredRowIdsForSlice(Campaign campaign, Collection<Call> calls,
                                                CachedFilter cf, JobConfigSlice slice) throws Exception {
    List<Object> params = new ArrayList<>(calls.size() + slice.where.getParams().size());
    StringBuilder placeholders = new StringBuilder();
    boolean first = true;
    for (Call c : calls) {
      String uk = c.getUniqueKey();
      if (uk == null || uk.isEmpty()) continue;
      if (!first) placeholders.append(',');
      placeholders.append('?');
      params.add(uk);
      first = false;
    }
    if (params.isEmpty()) return Collections.emptySet();

    params.addAll(slice.where.getParams());

    String sql = "SELECT id FROM " + cf.table +
            " WHERE id IN (" + placeholders + ")" +
            " AND (" + slice.where.getSql() + ")";

    JSONArray rs = dbEngine.executeParametrizedQuery(
            new ParametrizedQuery(sql, params),
            campaign.getId(),
            Integer.MAX_VALUE,
            cf.connUrl, cf.connUser, cf.connPwd);

    if (rs == null || rs.length() == 0) return Collections.emptySet();

    Set<String> ids = new HashSet<>(rs.length());
    for (int i = 0; i < rs.length(); i++) {
      JSONObject row = rs.getJSONObject(i);
      String id = row.optString("id", null);
      if (id != null && !id.isEmpty()) ids.add(id);
    }
    return ids;
  }

  private CachedFilter loadOrRefreshFilter(Campaign campaign) throws Exception {
    Long cid = campaign.getId();
    long now = System.currentTimeMillis();
    CachedFilter existing = filterCache.get(cid);
    if (existing != null && (now - existing.builtAtMs) < FILTER_CACHE_TTL_MS) {
      return existing;
    }

    String connUrl = trimToNull(campaign.getDbConnectionString());
    String connUser = trimToNull(campaign.getDbUsername());
    String encPw = trimToNull(campaign.getDbPassword());
    String table = trimToNull(campaign.getDbResultTable());
    if (connUrl == null || connUser == null || table == null) return null;

    String connPwd = (encPw == null) ? null : enCipherUtils.decryptPassword(encPw);

    List<JobConfigCampaignDTO> jobConfigs = transactionTemplate.execute(status ->
            jobConfigDao.findActiveJobConfigsForCampaign(cid));
    if (jobConfigs == null || jobConfigs.isEmpty()) {
      CachedFilter empty = new CachedFilter(now, table, connUrl, connUser, connPwd,
              Collections.emptyList());
      filterCache.put(cid, empty);
      return empty;
    }

    List<Long> jcIds = jobConfigs.stream()
            .map(JobConfigCampaignDTO::getJobConfigId)
            .collect(Collectors.toList());

    Map<Long, List<JobConfigFilterDTO>> filtersByJc = transactionTemplate.execute(status ->
            jobConfigDao.findFiltersByJobConfigIds(jcIds).stream()
                    .collect(Collectors.groupingBy(JobConfigFilterDTO::getJobConfigId)));

    SqlWhitelist wl = SqlWhitelist.load(dbEngine, cid, connUrl, connUser, connPwd, table);
    CompositeWhereClauseBuilder builder = new CompositeWhereClauseBuilder(wl);

    List<JobConfigSlice> slices = new ArrayList<>(jobConfigs.size());
    for (JobConfigCampaignDTO jc : jobConfigs) {
      List<JobConfigFilterDTO> fl = filtersByJc.getOrDefault(jc.getJobConfigId(), Collections.emptyList());
      ParamSql where = builder.build(fl);
      slices.add(new JobConfigSlice(jc.getJobConfigId(), jc.getResultCodeId(), jc.getExpireUserId(), where));
    }

    CachedFilter cf = new CachedFilter(now, wl.table, connUrl, connUser, connPwd, slices);
    filterCache.put(cid, cf);
    return cf;
  }

  private static String trimToNull(String s) {
    if (s == null) return null;
    String t = s.trim();
    return t.isEmpty() ? null : t;
  }

  private static final class CachedFilter {
    final long builtAtMs;
    final String table;
    final String connUrl;
    final String connUser;
    final String connPwd;
    final List<JobConfigSlice> slices;

    CachedFilter(long builtAtMs, String table, String connUrl, String connUser, String connPwd,
                 List<JobConfigSlice> slices) {
      this.builtAtMs = builtAtMs;
      this.table = table;
      this.connUrl = connUrl;
      this.connUser = connUser;
      this.connPwd = connPwd;
      this.slices = slices;
    }
  }

  private static final class JobConfigSlice {
    final Long jobConfigId;
    final Long resultCodeId;
    final Long expireUserId;
    final ParamSql where;

    JobConfigSlice(Long jobConfigId, Long resultCodeId, Long expireUserId, ParamSql where) {
      this.jobConfigId = jobConfigId;
      this.resultCodeId = resultCodeId;
      this.expireUserId = expireUserId;
      this.where = where;
    }
  }
}
