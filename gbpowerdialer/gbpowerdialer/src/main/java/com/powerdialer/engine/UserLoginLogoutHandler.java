package com.powerdialer.engine;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import java.sql.SQLException;

import com.google.common.cache.Cache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import com.powerdialer.engine.acd.ACDEngine;
import com.powerdialer.engine.asterisk.ami.ManagerConnectionComposition;
import com.powerdialer.engine.asterisk.ami.ManagerConnectionDecorator;
import com.powerdialer.engine.db.CampaignDbOperations;
import com.powerdialer.engine.dialer.CampaignContext;
import com.powerdialer.engine.dialer.DialerFacade;
import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.User;
import com.powerdialer.redis.RedisUtil;
import com.powerdialer.util.Constants;
import com.powerdialer.util.LoggerDecorator;

/**
 * Class for handling agent logout event retrieved from asterisk or redis
 */
@Component
@Lazy(false)
public class UserLoginLogoutHandler {

  private final LoggerDecorator LOG;

  @Autowired
  private ACDEngine acdEngine;

  @Autowired
  private IncallAgentsRefresher incallAgentsRefresher;

  @Autowired
  private CallUpToDateKeeper callUpToDateKeeper;

  @Autowired
  private DialerFacade dialerFacade;

  @Autowired
  private ManagerConnectionComposition connectionComposition;

  @Autowired
  private CampaignDbOperations campaignDbOperations;

  @Autowired
  private CallCleaner callCleaner;

  @Autowired
  private RedisUtil redisUtil;

  @Autowired
  public UserLoginLogoutHandler(LoggerDecorator loggerDecorator) {
    LOG = loggerDecorator.getLogger(ManagerConnectionDecorator.class);
  }

  /**
   * Process agent logout
   * @param user
   */
  public void handleLogout(User user) {
    acdEngine.removeFromIdleUsers(user);
    acdEngine.removeActiveUsers(user);
    callUpToDateKeeper.onUserLogout(user);
    decrementAgentSize(user);
    handleUserNotFinishedCalls(user);
  }

  /**
   * Process agent login
   * @param user
   */
  public void handleLogin(User user) {
    incrementAgentSize(user);
  }

  /**
   * Decrement agent size param for all running campaigns
   * @param user
   */
  private void decrementAgentSize(User user) {
    for (CampaignContext campaignContext : dialerFacade.getCampaignContexts().values()) {
      Campaign campaign = campaignContext.getCampaign();
      if (acdEngine.isUserHasMatchingSkillsAndGroup(user, campaign)) {
        campaign.decrementAgentsSize();
        campaign.setAvailAgentsSize(acdEngine.getAvailAgents(campaign));
        updateIncallAgents(campaign);
      }
    }
  }

  /**
   * Increment agent size param for all running campaigns
   * @param user
   */
  private void incrementAgentSize(User user) {
    for (CampaignContext campaignContext : dialerFacade.getCampaignContexts().values()) {
      Campaign campaign = campaignContext.getCampaign();
      if (acdEngine.isUserHasMatchingSkillsAndGroup(user, campaign)) {
        campaign.incrementAgentsSize();
        campaign.setAvailAgentsSize(acdEngine.getAvailAgents(campaign));
        updateIncallAgents(campaign);
      }
    }
  }

  /**
   * Kampanyanin INCALL agent sayacini yeniden hesap icin isaretler.
   *
   * <p>Task 325654: Bu metot eskiden kampanyanin TUM agent populasyonu icin Redis'ten
   * bulk status okuyordu ve login/logout basina eslesen her kampanya icin cagriliyordu.
   * Artik yalniz dirty isaretlemesi yapilir (IO yok); gercek hesap
   * {@link IncallAgentsRefresher} tarafindan coalesce edilerek yapilir.</p>
   */
  private void updateIncallAgents(Campaign campaign) {
    incallAgentsRefresher.markDirty(campaign);
  }

  /**
   * Handle user not finished calls
   * @param user
   * @throws SQLException
   */
  private void handleUserNotFinishedCalls(User user) {
    getCallsByUser(user).forEach(call -> {
      LOG.info("Handling unfinished call for logout event: " + call);
      try {
        if (isOrphanCandidate(call)) {
          call.setOrphaned(true);
          call.getCallOriginator().getConnectionDecorator().getOrphanedCalls().put(call.getId(), call);
        } else if (!call.getCampaign().isPreviewCampaign())
          return;
        processClearance(call);
      } catch (Exception e) {
        LOG.error("Cannot handle call for logout event: " + call, e);
      }
    });
  }

  private List<Call> getCallsByUser(User user) {
    return connectionComposition.getOpenedConnections().values().stream()
        .map(ManagerConnectionDecorator::getCalls)
        .map(Cache::asMap)
        .map(Map::values)
        .flatMap(Collection::stream)
        // Skip transferred calls: on (blind) transfer the agent already left the bridge, so the
        // call is no longer the agent's to clear on logout even though it still references the agent
        // and lingers in `calls`. It finalizes on its own DialerHangup when the customer/transfer
        // target conversation ends; hanging it up here would cut that live conversation.
        .filter(call -> user.equals(call.getUser()) && !call.isTransferred())
        .collect(Collectors.toList());
  }

  private boolean isOrphanCandidate(Call call) {
    return call.getAnswerDate() != null || isDialedWithPreviewType(call);
  }

  private boolean isDialedWithPreviewType(Call call) {
    return call.getStartDate() != null && call.getCampaign().isPreviewCampaign();
  }

  private void processClearance(Call call) throws SQLException {
    call.cancelPreviewFuture();
    ManagerConnectionDecorator connectionDecorator = call.getCallOriginator().getConnectionDecorator();
    if (call.getTelephonyResultCode() != null) {
      connectionDecorator.processCallResult(call, new Date());
    } else {
      finishCall(call);
      connectionDecorator.clearCall(call);
      connectionDecorator.hangupCall(call);
    }
  }

  private void finishCall(Call call) throws SQLException {
    call.getCampaign().removeCallInProgress(call);
    if (call.getAnswerDate() == null) {
      if (call.getStartDate() != null)
        call.getCampaign().decrementRingingCalls(call);
      campaignDbOperations.removeFetchedDataFlagFromCall(call);
    } else {
      Date completeDate = new Date();
      callCleaner.completeCall(call, completeDate);
      call.getCampaign().decrementActiveCalls(call);
      if(call.isAnsweredByUser()) {
        call.getCampaign().decrementInCallsSize(call);
      }
      call.setTelephonyResultCode(connectionComposition.getItuToTelephonyResultCodeRelations().get(Constants.TELEPHONE_RC_ORPHANED_CALL_TERMINATED));
      campaignDbOperations.insertCallResultToDB(call);
      call.getCampaign().incrementTelephonyResultCode(call.getTelephonyResultCode().getId());
      persistOrphanedFinalCdr(call, completeDate);
      checkStopCondition(call.getCampaign());
    }
  }

  /**
   * FIX-320173 (B1): Logout-orphan finalize'ı orphan sonucu kampanya result tablosuna yazarken
   * pd_cdr'ı initial halde bırakıyordu; sonraki gerçek hangup, telephonyResultCode dolu olduğu
   * için finalize bloğunu atlıyor ve satır kalıcı telephony_rc=NULL kalıyordu. pd_cdr aynı
   * orphan sonucu ve gerçek bitiş zamanıyla persist edilir; webphone'dan gelmiş in-memory user
   * result code, kampanya result tablosu ile tutarlılık için korunur (preserveUserResultCode).
   * Davranış-koruyucu B1 kararı gereği retry değerlendirmesi yapılmaz ve sayaç akışı değişmez.
   */
  private void persistOrphanedFinalCdr(Call call, Date completeDate) {
    if (call.getCallOriginator() == null || call.getCallOriginator().getConnectionDecorator() == null) {
      LOG.warn("Cannot persist orphaned final CDR; call originator/decorator is missing for " + call);
      return;
    }
    call.getCallOriginator().getConnectionDecorator().persistFinalCdr(call, completeDate, true);
  }

  private void checkStopCondition(Campaign campaign) {
    if (connectionComposition.getStopConditionChecker().isStopConditionReached(campaign)) {
      LOG.info(campaign + " has reached stop condition", Constants.LOG_MODULE.CAMPAIGN, campaign);
      connectionComposition.getCampaignService().actionAsync(campaign.getId(), Constants.CAMPAIGN_ACTION.STOP_REGULAR, Constants.CAMPAIGN_STOP_TYPE.STOP_CONDITION);
    }
  }

}
