package com.powerdialer.dao.impl;

import com.powerdialer.util.Constants;
import org.hibernate.Session;
import org.hibernate.query.NativeQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import com.powerdialer.dao.ICDRDao;
import com.powerdialer.model.CDR;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.JoinType;
import javax.persistence.criteria.Root;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Repository("cdrDao")
public class CDRDao extends GenericDao<CDR> implements ICDRDao {

  private static final Logger LOG = LoggerFactory.getLogger(CDRDao.class);
  /**
   * FIX-333607: GAMMA ACW sorguları (startup ve runtime yenileme) en fazla 10 sn sürer; timeout'ta
   * WARN basılır ve mevcut 0 fallback'i korunur. Runtime yenileme webphone event thread'inde çalışır.
   */
  private static final int GAMMA_ACW_QUERY_TIMEOUT_SECONDS = 10;
  /**
   * FIX-333607: GAMMA startup profil sorguları campaign start'ı içinde çalışır; sınırsız sürmeleri
   * 02.10.2026'da start'ları dakikalarca bekletti. Timeout'ta WARN basılır ve yalnız o değer
   * startup varsayılanına düşer.
   */
  private static final int GAMMA_STARTUP_QUERY_TIMEOUT_SECONDS = 10;
  /**
   * FIX-333607: son çalışma günü (bugünden önce agent'a bağlanan son çağrının günü). Sargable koşul
   * ile {@code (campaign_id, call_handling_start)} index'inden geriye doğru tek satır okunur; ortalama
   * sorguları günü {@code (campaign_id, call_start_time)} index'ine uyan aralıkla filtreler.
   * Eskiden gün {@code CAST(c.call_dial_time AS DATE) = (alt sorgu)} ile filtreleniyordu;
   * {@code call_dial_time} indexsiz olduğu için campaign'in tüm geçmişi okunuyordu.
   */
  private static final String GAMMA_LAST_WORKING_DAY_JOIN =
          "FROM (" +
          "    SELECT TOP 1 CAST(call_handling_start AS DATE) AS working_day " +
          "    FROM pd_cdr WITH (NOLOCK) " +
          "    WHERE campaign_id = :campaignId " +
          "    AND call_handling_start < CAST(GETDATE() AS DATE) " +
          "    ORDER BY call_handling_start DESC" +
          ") lw " +
          "INNER JOIN pd_cdr c WITH (NOLOCK) " +
          "ON c.campaign_id = :campaignId " +
          "AND c.call_start_time >= lw.working_day " +
          "AND c.call_start_time < DATEADD(day, 1, lw.working_day) ";
  private static final Double GAMMA_ACW_TIMEOUT_FALLBACK = 0.0d;
  private static final int WEBPHONE_RECOVERY_QUERY_TIMEOUT_SECONDS = 10;

  public CDRDao() {
    super(CDR.class);
  }

  @Override
  public CDR getByInteractionId(String interactionId) {
    Session session = getSessionFactory().getCurrentSession();
    CriteriaBuilder cb = session.getCriteriaBuilder();
    CriteriaQuery<CDR> query = cb.createQuery(CDR.class);
    Root<CDR> root = query.from(CDR.class);
    root.fetch("campaign", JoinType.LEFT);
    root.fetch("telephonyRC", JoinType.LEFT);
    root.fetch("assignedUser", JoinType.LEFT);
    root.fetch("connectedUser", JoinType.LEFT);
    query.select(root).where(cb.equal(root.get("interactionId"), interactionId));

    return session.createQuery(query).uniqueResult();
  }

  @Override
  public Date findAgentRegistrarAnswerTime(String parentId) {
    if (parentId == null) {
      return null;
    }
    Session session = getSessionFactory().getCurrentSession();
    String sql = "SELECT TOP 1 time_answer FROM ast_registrar_cdr WITH (NOLOCK) " +
            "WHERE parentid = :parentId AND dcontext = 'from_registrar' AND time_answer IS NOT NULL " +
            "ORDER BY time_answer ASC";
    Object result = session.createNativeQuery(sql)
            .setParameter("parentId", parentId)
            .setTimeout(WEBPHONE_RECOVERY_QUERY_TIMEOUT_SECONDS)
            .uniqueResult();
    return result instanceof Date ? (Date) result : null;
  }

  @Override
  public boolean hasSuccessfulInteractionStatus(String parentId, Long userId) {
    if (parentId == null || userId == null) {
      return false;
    }
    Session session = getSessionFactory().getCurrentSession();
    String sql = "SELECT TOP 1 1 FROM wbp_interaction_status WITH (NOLOCK) " +
            "WHERE parent_id = :parentId AND user_id = :userId " +
            "AND interaction_answer IS NOT NULL AND talk_time > 0";
    Object result = session.createNativeQuery(sql)
            .setParameter("parentId", parentId)
            .setParameter("userId", userId)
            .setTimeout(WEBPHONE_RECOVERY_QUERY_TIMEOUT_SECONDS)
            .uniqueResult();
    return result != null;
  }

  /**
   * Agent'ın webphone'da girdiği result code'un authoritative kaynağıdır: dialer çağrısının
   * (`parentid`) agent bacağı {@code ast_registrar_cdr} üzerinden bulunur ve o bacağa yazılmış
   * {@code wbp_call_result_code} kaydı okunur. Redis result code mesajı dialer'a hiç ulaşmadığında
   * awaiting-timeout finalize'ının son telafi yoludur.
   *
   * <p>{@code userId} filtresi zorunludur: başka bir agent'ın girdiği disposition çağrıya
   * yazılmamalıdır. Aynı bacak için birden fazla kayıt varsa en son girilen değer kullanılır.</p>
   *
   * @param parentId dialer interaction id'si (`dl-...`)
   * @param userId result code'u girmiş olması beklenen agent
   * @return result code id'si veya kayıt yoksa null
   */
  @Override
  public Long findWebphoneUserResultCodeId(String parentId, Long userId) {
    if (parentId == null || userId == null) {
      return null;
    }
    Session session = getSessionFactory().getCurrentSession();
    String sql = "SELECT TOP 1 rc.result_code_id FROM ast_registrar_cdr leg WITH (NOLOCK) " +
            "INNER JOIN wbp_call_result_code rc WITH (NOLOCK) ON leg.uniqueid = rc.call_id " +
            "WHERE leg.parentid = :parentId AND rc.user_id = :userId AND rc.result_code_id IS NOT NULL " +
            "ORDER BY rc.create_date DESC";
    Object result = session.createNativeQuery(sql)
            .setParameter("parentId", parentId)
            .setParameter("userId", userId)
            .setTimeout(WEBPHONE_RECOVERY_QUERY_TIMEOUT_SECONDS)
            .uniqueResult();
    return result instanceof Number ? ((Number) result).longValue() : null;
  }

  @Override
  public Double findGammaStartupAverageTalkTime(long campaignId, int minTalkTimeSec) {
    Session session = getSessionFactory().getCurrentSession();
    // Canli pencere kapisi (PredictiveGammaCallManager) ile AYNI kumeden uretilir; ikisi
    // ayrisirsa T2 kademeli gecisi iki farkli tanimi harmanlar. Bkz. GAMMA_TALK_VALID_RESULT_CODES.
    String talkCodes = Constants.GAMMA_TALK_VALID_RESULT_CODES.stream()
            .map(String::valueOf).collect(Collectors.joining(", "));
    String sql = "SELECT AVG(CAST(DATEDIFF(SECOND, c.call_handling_start, c.call_end_time) AS FLOAT)) " +
            GAMMA_LAST_WORKING_DAY_JOIN +
            "WHERE c.call_dial_time IS NOT NULL " +
            "AND c.call_handling_start IS NOT NULL " +
            "AND c.call_end_time IS NOT NULL " +
            "AND c.telephony_rc IN (" + talkCodes + ") " +
            "AND DATEDIFF(SECOND, c.call_handling_start, c.call_end_time) >= :minTalkTimeSec";

    return executeGammaStartupAverageQuery(session.createNativeQuery(sql)
            .setParameter("campaignId", campaignId)
            .setParameter("minTalkTimeSec", minTalkTimeSec), campaignId, "startup talk time query");
  }

  @Override
  public Double findGammaStartupAverageRingTime(long campaignId) {
    Session session = getSessionFactory().getCurrentSession();
    String ringCodes = Constants.RING_VALID_RESULT_CODES.stream().map(String::valueOf).collect(Collectors.joining(", "));
    String sql = "SELECT AVG(CAST(DATEDIFF(SECOND, c.call_dial_time, COALESCE(c.call_answer_time, c.call_end_time, c.call_dial_time)) AS FLOAT)) " +
            GAMMA_LAST_WORKING_DAY_JOIN +
            "WHERE c.call_dial_time IS NOT NULL " +
            "AND (c.call_answer_time IS NOT NULL OR c.call_end_time IS NOT NULL) " +
            "AND c.telephony_rc IN (" + ringCodes + ")";

    return executeGammaStartupAverageQuery(session.createNativeQuery(sql)
            .setParameter("campaignId", campaignId), campaignId, "startup ring time query");
  }

  /**
   * MADDE 4A — Gamma Plus warm-up profili: YALNIZ cevaplanan çağrıların ring süresi ortalaması.
   *
   * <p>{@link #findGammaStartupAverageRingTime} ile aynı gün penceresini ve aynı indeksi kullanır;
   * iki fark: (1) {@code call_answer_time IS NOT NULL} zorunlu, (2) süre {@code COALESCE} ile
   * değil doğrudan {@code call_dial_time → call_answer_time} ölçülür. Result code filtresi
   * gerekmez — cevaplanmış çağrı tanımı gereği çalmıştır.
   *
   * <p>Sorgu SAYISI artmaz: {@code buildStartupProfile} bayrağa göre bu varyantı VEYA eskisini
   * çağırır, ikisini birden değil.
   */
  @Override
  public Double findGammaStartupAverageRingTimeAnswered(long campaignId) {
    Session session = getSessionFactory().getCurrentSession();
    String sql = "SELECT AVG(CAST(DATEDIFF(SECOND, c.call_dial_time, c.call_answer_time) AS FLOAT)) " +
            GAMMA_LAST_WORKING_DAY_JOIN +
            "WHERE c.call_dial_time IS NOT NULL " +
            "AND c.call_answer_time IS NOT NULL";

    return executeGammaStartupAverageQuery(session.createNativeQuery(sql)
            .setParameter("campaignId", campaignId), campaignId, "startup answered ring time query");
  }

  @Override
  public Double findGammaStartupAverageAcwTime(long campaignId) {
    Session session = getSessionFactory().getCurrentSession();
    String sql = "WITH LastWorkingDay AS ( " +
            "    SELECT TOP 1 CAST(call_handling_start AS DATE) AS working_day " +
            "    FROM pd_cdr WITH (NOLOCK) " +
            "    WHERE campaign_id = :campaignId " +
            "    AND call_handling_start IS NOT NULL " +
            "    AND call_handling_start < CAST(GETDATE() AS DATE) " +
            "    ORDER BY call_handling_start DESC " +
            "), " +
            "LastWorkingDayRange AS ( " +
            "    SELECT working_day AS start_of_day, " +
            "           DATEADD(day, 1, working_day) AS start_of_next_day " +
            "    FROM LastWorkingDay " +
            "), " +
            "TargetStatuses AS ( " +
            "    SELECT id " +
            "    FROM wbp_status WITH (NOLOCK) " +
            "    WHERE status_text LIKE '%WRAP%' " +
            "       OR status_text LIKE '%ACW%' " +
            "), " +
            "CampaignAgents AS ( " +
            "    SELECT DISTINCT [user_id] " +
            "    FROM wbp_interaction_status s WITH (NOLOCK) " +
            "    CROSS JOIN LastWorkingDayRange d " +
            "    WHERE s.queue_campaign_id = :campaignId " +
            "    AND s.interaction_type = 'PROGRESSIVE' " +
            "    AND s.interaction_start >= d.start_of_day " +
            "    AND s.interaction_start < d.start_of_next_day " +
            "), " +
            "RecentAcwRows AS ( " +
            "    SELECT TOP 400 " +
            "           US.[user_id], " +
            "           US.status_session " +
            "    FROM wbp_user_status US WITH (NOLOCK) " +
            "    INNER JOIN TargetStatuses S ON US.[status] = S.[id] " +
            "    INNER JOIN CampaignAgents CA ON US.[user_id] = CA.[user_id] " +
            "    CROSS JOIN LastWorkingDayRange d " +
            "    WHERE US.log_date >= d.start_of_day " +
            "    AND US.log_date < d.start_of_next_day " +
            "    ORDER BY US.log_date DESC " +
            "), " +
            "RecentAcwSessions AS ( " +
            "    SELECT DISTINCT [user_id], status_session " +
            "    FROM RecentAcwRows " +
            "), " +
            "AgentWrapups AS ( " +
            "    SELECT US.[user_id], " +
            "           US.status_session, " +
            "           DATEDIFF(SECOND, " +
            "               MIN(CASE WHEN US.log_type = 0 THEN US.log_date END), " +
            "               ISNULL(MAX(CASE WHEN US.log_type IN (1, 2) THEN US.log_date END), GETDATE()) " +
            "           ) AS wrapup_seconds " +
            "    FROM wbp_user_status US WITH (NOLOCK) " +
            "    INNER JOIN RecentAcwSessions R " +
            "        ON US.[user_id] = R.[user_id] " +
            "       AND US.status_session = R.status_session " +
            "    INNER JOIN TargetStatuses S ON US.[status] = S.[id] " +
            "    CROSS JOIN LastWorkingDayRange d " +
            "    WHERE US.log_date >= d.start_of_day " +
            "    AND US.log_date < d.start_of_next_day " +
            "    GROUP BY US.[user_id], US.status_session " +
            ") " +
            "SELECT AVG(CAST(wrapup_seconds AS FLOAT)) AS avg_wrapup_seconds " +
            "FROM AgentWrapups";

    return executeGammaAcwAverageQuery(session, sql, campaignId, "startup ACW query");
  }

  @Override
  public Double findGammaCurrentAverageAcwTime(long campaignId) {
    Session session = getSessionFactory().getCurrentSession();
    String sql = "WITH TodayRange AS ( " +
            "    SELECT CAST(GETDATE() AS DATE) AS start_of_today, " +
            "           DATEADD(day, 1, CAST(GETDATE() AS DATE)) AS start_of_tomorrow " +
            "), " +
            "TargetStatuses AS ( " +
            "    SELECT id " +
            "    FROM wbp_status WITH (NOLOCK) " +
            "    WHERE status_text LIKE '%WRAP%' " +
            "       OR status_text LIKE '%ACW%' " +
            "), " +
            "CampaignAgents AS ( " +
            "    SELECT DISTINCT [user_id] " +
            "    FROM wbp_interaction_status s WITH (NOLOCK) " +
            "    CROSS JOIN TodayRange d " +
            "    WHERE s.queue_campaign_id = :campaignId " +
            "       AND s.interaction_type = 'PROGRESSIVE' " +
            "       AND s.interaction_start >= d.start_of_today " +
            "       AND s.interaction_start < d.start_of_tomorrow " +
            "), " +
            "RecentAcwRows AS ( " +
            "    SELECT TOP 400 " +
            "           US.[user_id], " +
            "           US.status_session " +
            "    FROM wbp_user_status US WITH (NOLOCK) " +
            "    INNER JOIN TargetStatuses S ON US.[status] = S.[id] " +
            "    INNER JOIN CampaignAgents CA ON US.[user_id] = CA.[user_id] " +
            "    CROSS JOIN TodayRange d " +
            "    WHERE US.log_date >= d.start_of_today " +
            "    AND US.log_date < d.start_of_tomorrow " +
            "    ORDER BY US.log_date DESC " +
            "), " +
            "RecentAcwSessions AS ( " +
            "    SELECT DISTINCT [user_id], status_session " +
            "    FROM RecentAcwRows " +
            "), " +
            "AgentWrapups AS ( " +
            "    SELECT US.[user_id], " +
            "           US.status_session, " +
            "           DATEDIFF(SECOND, MIN(CASE WHEN US.log_type = 0 THEN US.log_date END), " +
            "                            ISNULL(MAX(CASE WHEN US.log_type IN (1,2) THEN US.log_date END), GETDATE()) " +
            "           ) AS wrapup_seconds " +
            "    FROM wbp_user_status US WITH (NOLOCK) " +
            "    INNER JOIN RecentAcwSessions R " +
            "        ON US.[user_id] = R.[user_id] " +
            "       AND US.status_session = R.status_session " +
            "    INNER JOIN TargetStatuses S ON US.[status] = S.[id] " +
            "    CROSS JOIN TodayRange d " +
            "    WHERE US.log_date >= d.start_of_today " +
            "       AND US.log_date < d.start_of_tomorrow " +
            "    GROUP BY US.[user_id], US.status_session " +
            ") " +
            "SELECT AVG(CAST(wrapup_seconds AS FLOAT)) AS avg_wrapup_seconds " +
            "FROM AgentWrapups";

    return executeGammaAcwAverageQuery(session, sql, campaignId, "current ACW query");
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<Object[]> findGammaTodayAnswerStatsByType(long campaignId) {
    Session session = getSessionFactory().getCurrentSession();
    // Yalniz BUGUN: gun-ici stop/start sonrasi startup AR'yi gercek gunluk veriden baslatmak icin.
    // call_start_time her cagrida (no-answer dahil) doludur ve (campaign_id, call_start_time) index'i ile (FIX-333607) seek edilir;
    // bu yuzden denominator dogru (dial edilmis tum cagri) kalir ve sorgu index seek ile calisir.
    // outdialing_type_id ham id ile gruplanir; FRESH/RETRY/CALLBACK eslemesi cagiran tarafta yapilir.
    String sql = "SELECT c.outdialing_type_id, " +
            "       COUNT(*) AS total, " +
            "       SUM(CASE WHEN c.call_answer_time IS NOT NULL THEN 1 ELSE 0 END) AS answered " +
            "FROM pd_cdr c WITH (NOLOCK) " +
            "WHERE c.campaign_id = :campaignId " +
            "AND c.call_start_time >= CAST(GETDATE() AS DATE) " +
            "AND c.call_start_time < DATEADD(day, 1, CAST(GETDATE() AS DATE)) " +
            "GROUP BY c.outdialing_type_id";

    try {
      return session.createNativeQuery(sql)
              .setParameter("campaignId", campaignId)
              .setTimeout(GAMMA_STARTUP_QUERY_TIMEOUT_SECONDS)
              .getResultList();
    } catch (RuntimeException e) {
      if (isQueryTimeout(e)) {
        LOG.warn("[GAMMA] cid={} startup today answer stats query timed out after {} seconds. Falling back to startup answer rate constants.",
                campaignId, GAMMA_STARTUP_QUERY_TIMEOUT_SECONDS);
        return Collections.emptyList();
      }
      throw e;
    }
  }

  private Double executeGammaStartupAverageQuery(NativeQuery<?> query, long campaignId, String queryName) {
    try {
      Object result = query.setTimeout(GAMMA_STARTUP_QUERY_TIMEOUT_SECONDS).getSingleResult();
      return result instanceof Number ? ((Number) result).doubleValue() : null;
    } catch (RuntimeException e) {
      if (isQueryTimeout(e)) {
        LOG.warn("[GAMMA] cid={} {} timed out after {} seconds. Falling back to startup default.",
                campaignId, queryName, GAMMA_STARTUP_QUERY_TIMEOUT_SECONDS);
        return null;
      }
      throw e;
    }
  }

  private Double executeGammaAcwAverageQuery(Session session, String sql, long campaignId, String queryName) {
    try {
      LOG.info("[GAMMA] cid={} {} will run with queryTimeout={} seconds.",
              campaignId, queryName, GAMMA_ACW_QUERY_TIMEOUT_SECONDS);
      Object result = session.createNativeQuery(sql)
              .setParameter("campaignId", campaignId)
              .setTimeout(GAMMA_ACW_QUERY_TIMEOUT_SECONDS)
              .getSingleResult();
      return result instanceof Number ? ((Number) result).doubleValue() : null;
    } catch (RuntimeException e) {
      if (isQueryTimeout(e)) {
        LOG.warn("[GAMMA] cid={} {} timed out after {} seconds. Falling back to avgAcw={}.",
                campaignId, queryName, GAMMA_ACW_QUERY_TIMEOUT_SECONDS, GAMMA_ACW_TIMEOUT_FALLBACK);
        LOG.debug("[GAMMA] cid={} {} timeout details", campaignId, queryName, e);
        return GAMMA_ACW_TIMEOUT_FALLBACK;
      }
      throw e;
    }
  }

  private boolean isQueryTimeout(Throwable throwable) {
    Throwable current = throwable;
    while (current != null) {
      if (current instanceof javax.persistence.QueryTimeoutException
              || current instanceof org.hibernate.QueryTimeoutException) {
        return true;
      }
      current = current.getCause();
    }
    return false;
  }
}
