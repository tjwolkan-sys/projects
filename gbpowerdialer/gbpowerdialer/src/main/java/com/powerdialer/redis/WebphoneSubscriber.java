package com.powerdialer.redis;

import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import java.io.IOException;
import javax.annotation.PostConstruct;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powerdialer.model.*;
import org.apache.commons.lang3.StringUtils;
import org.asteriskjava.manager.AuthenticationFailedException;
import org.asteriskjava.manager.TimeoutException;
import org.dozer.Mapper;
import org.redisson.api.listener.MessageListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

import com.powerdialer.dto.UserDTOExtended;
import com.powerdialer.engine.CallUpToDateKeeper;
import com.powerdialer.engine.CustomerDncService;
import com.powerdialer.engine.DNCEngine;
import com.powerdialer.engine.acd.ACDEngine;
import com.powerdialer.engine.asterisk.ami.ManagerConnectionComposition;
import com.powerdialer.engine.asterisk.ami.ManagerConnectionDecorator;
import com.powerdialer.engine.dialer.DialerFacade;
import com.powerdialer.redis.message.RedisMessage;
import com.powerdialer.service.CDRService;
import com.powerdialer.service.CampaignResultService;
import com.powerdialer.service.DNCPhoneNumberService;
import com.powerdialer.service.DNCService;
import com.powerdialer.service.UserService;
import com.powerdialer.util.Constants;
import com.powerdialer.util.Constants.MSG_ACTION_TYPE;
import com.powerdialer.util.Constants.SERVER_TYPE;
import com.powerdialer.util.LoggerDecorator;
import com.powerdialer.util.UserPopulationEvaluator;
import com.powerdialer.util.Utils;

/**
 * Subscriber thread to redis, handles messages sent to webphone channel
 */
@Component
@Lazy(false)
public class WebphoneSubscriber implements MessageListener<String> {

  private static final Logger LOG = LoggerFactory.getLogger(WebphoneSubscriber.class);
  private static final String COULD_NOT_SET_RESULT_CODE_MESSAGE = "Could not set result code '{}' for call '{}': ";

  private final ObjectMapper objectMapper = new ObjectMapper().configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true);
  private LoggerDecorator campaignLogger;

  @Autowired
  private LoggerDecorator loggerDecorator;

  @Autowired
  private ManagerConnectionComposition managerConnectionComposition;

  @Autowired
  private ACDEngine acdEngine;

  @Autowired
  private DialerFacade dialerFacade;

  @Autowired
  private DNCService dncService;

  @Autowired
  private DNCPhoneNumberService dncPhoneNumberService;

  @Autowired
  private DNCEngine dncEngine;

  @Autowired
  private CustomerDncService customerDncService;

  @Autowired
  private CallUpToDateKeeper callUpToDateKeeper;

  @Autowired
  private Mapper mapper;

  @Autowired
  private RedisUtil redisUtil;

  @Autowired
  private CampaignResultService campaignResultService;

  @Autowired
  private UserService userService;

  @Autowired
  @Qualifier("webphoneEventExecutor")
  private TaskExecutor webphoneEventExecutor;

  @PostConstruct
  private void init() {
    campaignLogger = loggerDecorator.getLogger(WebphoneSubscriber.class);
    redisUtil.subscribe(Constants.REDIS_CHANNEL.channelGBWebPhone.toString(), this);
  }

  @Override
  public void onMessage(CharSequence channel, String message) {
    if (Constants.REDIS_PING_MESSAGE.equals(message))
      return;
    webphoneEventExecutor.execute(() -> {
      try {
        RedisMessage redisMsg = objectMapper.readValue(message, RedisMessage.class);
        LOG.info("Got message: " + message);
        if (redisMsg.getEntityType() == Constants.MSG_ENTITY_TYPE.USER) {
          handleUserEntityMessage(redisMsg);
        } else if (redisMsg.getEntityType() == Constants.MSG_ENTITY_TYPE.RESULT_CODE) {
          handleResultCodeEntityMessage(redisMsg);
        } else if (redisMsg.getEntityType() == Constants.MSG_ENTITY_TYPE.SERVER) {
          handleServerEntityMessage(redisMsg);
        } else if (redisMsg.getEntityType() == Constants.MSG_ENTITY_TYPE.OTHER) {
          handleOriginateActionErrorMessage(redisMsg);
        } else if (redisMsg.getEntityType() == Constants.MSG_ENTITY_TYPE.CONTACT_ANOMALY_REPORT) {
          handleAnomalyReportEntityMessage(redisMsg);
        }
      } catch (Exception e) {
        LOG.error("Could not handle entity message " + message, e);
      }
    });
  }

  /**
   * Handle redis messages with entity type Other
   * @param redisMsg
   */
  private void handleOriginateActionErrorMessage(RedisMessage redisMsg) {
    Date redisMessageReceivingDate = new Date();
    if (redisMsg.getType() == MSG_ACTION_TYPE.ORIGINATE_ACTION_ERROR) {
      Map errorMessage = (Map) redisMsg.getSerializableObject();
      Map originateAction = (Map) errorMessage.get("originateAction");
      String uniqueId = (String) ((Map) originateAction.get("call")).get("uniqueId");
      String message = (String) errorMessage.get("message");
      Object server = originateAction.get("server");
      for (ManagerConnectionDecorator connectionDecorator : managerConnectionComposition.getOpenedConnections().values()) {
        Call call = connectionDecorator.getCalls().getIfPresent(uniqueId);
        if (call != null) {
          TelephonyResultCode telephonyResultCode = managerConnectionComposition.getItuToTelephonyResultCodeRelations().get(Constants.TELEPHONY_RC_ORIGINATE_ACTION_ERROR);
          call.setTelephonyResultCode(telephonyResultCode);
          if (!call.getCampaign().isPreviewCampaign()) {
            connectionDecorator.processCallResult(call, redisMessageReceivingDate);
          }
          campaignLogger.warn(message + " for " + call + " on Server: " + server, Constants.LOG_MODULE.ASTERISK, call.getCampaign());
          break;
        }
      }
    }
  }

  /**
   * Handle redis messages with entity type User
   * @param redisMsg
   */
  private void handleUserEntityMessage(RedisMessage redisMsg) {
    User user = objectMapper.convertValue(redisMsg.getSerializableObject(), User.class);
    if (redisMsg.getType() == MSG_ACTION_TYPE.EDIT) {
      handleUserUpdate(user);
    }
    LOG.info(redisMsg.getType() + " received for " + mapper.map(user, UserDTOExtended.class));
  }

  /**
   * Handle user parameters update. Just update skills of registered user
   * @param user
   */
  private void handleUserUpdate(User user) {
    for (User registeredUser : callUpToDateKeeper.getAllRegisteredUsers()) {
      if (registeredUser.equals(user)) {
        registeredUser.setSkillWeightPair(user.getSkillWeightPair());
      }
    }
    for (User idleUser : acdEngine.getAllIdleUsers()) {
      if (idleUser.equals(user)) {
        idleUser.setSkillWeightPair(user.getSkillWeightPair());
      }
    }
  }


  private void handleAnomalyReportEntityMessage(RedisMessage redisMsg) {

    if (redisMsg.getType() == MSG_ACTION_TYPE.ADD) {
      AnamolyReportResult anomalyReportResult = objectMapper.convertValue(redisMsg.getSerializableObject(), AnamolyReportResult.class);
      if (anomalyReportResult != null) {
        LOG.info(anomalyReportResult + " is received");
        if (callIsGeneratedByDialer(anomalyReportResult.getParentId())) {
          for (ManagerConnectionDecorator connectionDecorator : managerConnectionComposition.getOpenedConnections().values()) {

            Call call = connectionDecorator.getCalls().getIfPresent(anomalyReportResult.getParentId());
            if (call != null) {
              if (anomalyReportResult.getRelation() != null) {
                call.setRelation(anomalyReportResult.getRelation().getDescription());
                call.setRelationType(anomalyReportResult.getRelation().getType());
              }
              if (anomalyReportResult.getCustomer() != null) {
                call.setCustomerName(anomalyReportResult.getCustomer().getName());
                call.setCustomerSurname(anomalyReportResult.getCustomer().getSurname());
              }
              connectionDecorator.insertCallResultToDB(call);
            }
            else{
              LOG.warn("Call not found in current connection cache. parentId={}", anomalyReportResult.getParentId());
            }
          }

        }

      }
      else{
        LOG.warn("Redis message parsing returned null AnamolyReportResult. type=ADD.");
      }
    }


  }



  /**
   * Handle redis messages with entity type ResultCode
   * @param redisMsg
   */
  private void handleResultCodeEntityMessage(RedisMessage redisMsg) {
    if (redisMsg.getType() == MSG_ACTION_TYPE.ADD) {
      CallResultCode callResultCode = objectMapper.convertValue(redisMsg.getSerializableObject(), CallResultCode.class);
      LOG.info(callResultCode + " is received");
      if (callIsGeneratedByDialer(callResultCode.getParentId())) {
        processCallWithResultCodeAndUpdateDNC(callResultCode);
        LOG.info(callResultCode + " was processed");
      }
    }
  }

  /**
   * Check if call is generated by dialer
   * @param parentId
   * @return true if dialer generated, else false
   */
  private boolean callIsGeneratedByDialer(String parentId) {
    return StringUtils.isNotBlank(parentId) && parentId.startsWith("dl");
  }

  /**
   * Process call with result code
   * @param callResultCode
   */
  private void processCallWithResultCodeAndUpdateDNC(CallResultCode callResultCode) {
    for (ManagerConnectionDecorator connectionDecorator : managerConnectionComposition.getOpenedConnections().values()) {
      Call call = findActiveOrRecoverableCall(connectionDecorator, callResultCode.getParentId());
      if (call != null) {
        if (call.getUserResultCode() != null) {
          // In-memory call zaten userResultCode'a sahip (duplicate event veya yarış sonrası ek mesaj);
          // durable fallback denenmez.
          LOG.info(COULD_NOT_SET_RESULT_CODE_MESSAGE + "call is already has a result code", callResultCode, call);
          return;
        }
        if (shouldSetResultCode(call, callResultCode)) {
          processActiveCallResultCode(connectionDecorator, call, callResultCode);
          return;
        }
        // call.getUser() == null veya user mismatch: durable fallback denenir;
        // fallback'ın kendi user-match guard'ı (assigned-first, sonra connected) yanlış
        // agent'a yazımı engeller.
        break;
      }
    }

    CDR cdr = persistDurableResultCodeFallback(callResultCode, null, false);
    if (callResultCode.getResultCode().isDncIdentifier()) {
      updateDNCByCdrFallback(callResultCode, cdr);
    }
  }

  private Call findActiveOrRecoverableCall(ManagerConnectionDecorator connectionDecorator, String parentId) {
    Call call = connectionDecorator.getCalls().getIfPresent(parentId);
    if (call != null) {
      return call;
    }
    if (connectionDecorator.getAwaitingUserResultCodeCalls() != null) {
      call = connectionDecorator.getAwaitingUserResultCodeCalls().getIfPresent(parentId);
    }
    if (call != null || connectionDecorator.getRecoverableConnectionLossCalls() == null) {
      return call;
    }
    return connectionDecorator.getRecoverableConnectionLossCalls().getIfPresent(parentId);
  }

  private void processActiveCallResultCode(ManagerConnectionDecorator connectionDecorator, Call call, CallResultCode callResultCode) {
    call.setUserResultCode(callResultCode.getResultCode());
    if (call.getCdr() != null) {
      call.getCdr().setUserResultCodeId(callResultCode.getResultCode().getId());
    }

    if (callResultCode.getResultCode().getCallbackDate() != null && call.getCompleteDate() == null) {
      Campaign campaign = call.getCampaign();

      if (campaign != null && Boolean.TRUE.equals(campaign.getCountAllCallTypes())
              && call.getAttempts() >= campaign.getRetryOveralAttempt()) {

        call.setCallbackDate(null);
        call.setCallbackNumber(null);
        call.setCallbackType(null);
        call.setRetryDate(null);

      } else {
        manageCallback(callResultCode, call);
      }
    }

    updateDNCByResultCode(call, callResultCode.getDncScope());

    try {
      if ((call.getTelephonyResultCode() != null || (call.getCampaign().isPreviewCampaign() &&
              call.getStartDate() == null)) && call.getCompleteDate() == null) {
        connectionDecorator.processCallResult(call, new Date());
      } else if (call.getCompleteDate() != null) {
        connectionDecorator.insertCallResultToDB(call);
      }
    } catch (Exception e) {
      persistDurableResultCodeFallback(callResultCode, call.getCdr(), true);
      LOG.error("Could not finalize call after result code, durable fallback applied for " + call, e);
    }

    LOG.info("ResultCode has been received and set for " + call);
  }

  private CDR persistDurableResultCodeFallback(CallResultCode callResultCode, CDR cdr, boolean forceCdrPersist) {
    Long resultCodeId = getResultCodeId(callResultCode);
    if (resultCodeId == null) {
      LOG.warn("Skipping durable result code fallback. Missing resultCode id for {}", callResultCode);
      return cdr;
    }

    CDR targetCdr = cdr != null ? cdr : getCdrByInteractionId(callResultCode.getParentId());
    if (targetCdr == null) {
      LOG.warn("Skipping durable result code fallback. CDR not found for parentId={}", callResultCode.getParentId());
      return null;
    }

    if (isWebphoneResultCodeRecoveryCandidate(targetCdr, callResultCode)
        && promoteCdrToSuccessFromWebphoneResultCode(targetCdr, callResultCode, resultCodeId)) {
      return targetCdr;
    }

    boolean forcefullyStoppedCampaignFallback = isForcefullyStoppedCampaignResultFallback(targetCdr);

    if (!forcefullyStoppedCampaignFallback && !isSuccessfulCdrForUserResultCode(targetCdr)) {
      LOG.warn("Skipping durable result code fallback. CDR is not successful for parentId={}", callResultCode.getParentId());
      return targetCdr;
    }

    if (!forcefullyStoppedCampaignFallback && !isResultCodeUserMatchesCdr(callResultCode, targetCdr)) {
      LOG.warn("Skipping durable result code fallback. User mismatch for parentId={}, userId={}", callResultCode.getParentId(), callResultCode.getUserId());
      return targetCdr;
    }

    if (targetCdr.getUserResultCodeId() != null && !forceCdrPersist && !forcefullyStoppedCampaignFallback) {
      LOG.info("Skipping durable result code fallback. CDR already has userResultCodeId={} for parentId={}",
          targetCdr.getUserResultCodeId(), callResultCode.getParentId());
      return targetCdr;
    }

    targetCdr.setUserResultCodeId(resultCodeId);
    CDRService cdrService = managerConnectionComposition.getCdrService();
    if (cdrService != null) {
      cdrService.saveOrUpdate(targetCdr);
    } else {
      LOG.warn("Could not persist CDR user result code fallback. CDRService is null for parentId={}", callResultCode.getParentId());
    }

    updateCampaignResultCodeFallback(targetCdr, resultCodeId, callResultCode.getParentId());

    LOG.info("Durable result code fallback applied. parentId={}, resultCodeId={}", callResultCode.getParentId(), resultCodeId);
    return targetCdr;
  }

  private void updateCampaignResultCodeFallback(CDR targetCdr, Long resultCodeId, String parentId) {
    if (campaignResultService != null) {
      campaignResultService.updateUserResultCode(targetCdr, resultCodeId);
    } else {
      LOG.warn("Could not persist campaign result user code fallback. CampaignResultService is null for parentId={}", parentId);
    }
  }

  private Long getResultCodeId(CallResultCode callResultCode) {
    return callResultCode != null && callResultCode.getResultCode() != null ? callResultCode.getResultCode().getId() : null;
  }

  private CDR getCdrByInteractionId(String parentId) {
    CDRService cdrService = managerConnectionComposition.getCdrService();
    return cdrService != null ? cdrService.getByInteractionId(parentId) : null;
  }

  private boolean isSuccessfulCdrForUserResultCode(CDR cdr) {
    if (cdr == null || cdr.getTelephonyRC() == null || cdr.getTelephonyRC().getId() == null) {
      return false;
    }
    Long telephonyResultCodeId = cdr.getTelephonyRC().getId();
    return (Long.valueOf(10L).equals(telephonyResultCodeId) || Long.valueOf(24L).equals(telephonyResultCodeId))
        && cdr.getCallHandlingStartTime() != null;
  }

  private boolean isForcefullyStoppedCampaignResultFallback(CDR cdr) {
    return cdr != null
        && cdr.getCampaign() != null
        && !cdr.getCampaign().isPreviewCampaign()
        && cdr.getCallHandlingStartTime() != null
        && cdr.getTelephonyRC() != null
        && Constants.TELEPHONE_RC_CAMPAIGN_IS_STOPPED_FORCEFULLY.equals(cdr.getTelephonyRC().getCode());
  }

  private boolean isResultCodeUserMatchesCdr(CallResultCode callResultCode, CDR cdr) {
    if (callResultCode == null || callResultCode.getUserId() == null || cdr == null) {
      return false;
    }
    if (cdr.getAssignedUser() != null) {
      return callResultCode.getUserId().equals(cdr.getAssignedUser().getId());
    }
    return cdr.getConnectedUser() != null && callResultCode.getUserId().equals(cdr.getConnectedUser().getId());
  }

  /**
   * Webphone'dan result code event geldiğinde, PD-side {@link Constants#WEBPHONE_RESULT_CODE_RECOVERABLE_TRC_IDS}
   * listesindeki TRC id'lerinden biriyle yanlış pozitif başarısız sayılmış CDR'lar için recovery
   * adayı kontrolüdür. Promote, durable fallback'in normal başarılı CDR guard'larından önce çalışır;
   * Asterisk ({@code ast_registrar_cdr}) ve webphone ({@code wbp_interaction_status}) tarafında
   * authoritative bridge kanıtı doğrulandığında yapılır.
   */
  private boolean isWebphoneResultCodeRecoveryCandidate(CDR cdr, CallResultCode callResultCode) {
    if (cdr == null || callResultCode == null) {
      return false;
    }
    if (cdr.getUserResultCodeId() != null) {
      return false;
    }
    // NOTE: cdr.callAnswerTime is intentionally NOT required here. Under AMI event-worker lag PD's own
    // customer-answer event can be processed late, leaving callAnswerTime null on the persisted CDR
    // even though the call was answered and handled by an agent. The authoritative answer evidence is
    // verified in promoteCdrToSuccessFromWebphoneResultCode via the Asterisk registrar agent-answer leg
    // and the webphone interaction status, neither of which is subject to PD's worker lag.
    if (callResultCode.getUserId() == null) {
      return false;
    }
    TelephonyResultCode trc = cdr.getTelephonyRC();
    if (trc == null || trc.getId() == null) {
      return false;
    }
    return Constants.WEBPHONE_RESULT_CODE_RECOVERABLE_TRC_IDS.contains(trc.getId());
  }

  /**
   * Recovery aday CDR'ı, iki bağımsız authoritative kanıt (Asterisk {@code from_registrar} agent answer
   * legi + webphone {@code wbp_interaction_status} agent görüşme kaydı) doğrulandığında mevcut başarılı
   * kapatma persist akışı ({@link UserPopulationEvaluator#populateCdrUserOwnership},
   * {@link CDRService#saveOrUpdate}) ile {@code trc=10} olarak promote eder. Counter, retry ve user idle
   * gibi tarafları cleanup zaten işlemiştir. Campaign liste tablosu güncellenmez (FIX-333607).
   *
   * <p>DB sorgu çağrıları try/catch içinde tutulur; timeout veya başka bir hata durumunda promote
   * skip edilir ve normal akış devam eder (üretim akışı kırılmaz).</p>
   *
   * @return true persist tamamlandıysa, false promote koşulu sağlanmadıysa veya hata aldıysak
   */
  private boolean promoteCdrToSuccessFromWebphoneResultCode(CDR cdr, CallResultCode callResultCode, Long resultCodeId) {
    CDRService cdrService = managerConnectionComposition.getCdrService();
    if (cdrService == null) {
      LOG.warn("Skipping webphone result code recovery. CDRService is null for parentId={}", callResultCode.getParentId());
      return false;
    }

    if (cdr.getContactId() == null) {
      // contactId, synthetic call uniqueKey'i için tek kaynaktır. Yoksa
      // CampaignDbOperations.rowExistsInResultTableByCallUniqueKey false döner ve
      // campaign result tablosunda duplicate kayıt açılır.
      LOG.warn("Skipping webphone result code recovery. CDR contactId is null for parentId={}", callResultCode.getParentId());
      return false;
    }

    Date agentAnswerTime;
    try {
      agentAnswerTime = cdrService.findAgentRegistrarAnswerTime(callResultCode.getParentId());
    } catch (Exception e) {
      LOG.warn("Skipping webphone result code recovery. Asterisk registrar lookup failed for parentId=" + callResultCode.getParentId(), e);
      return false;
    }
    if (agentAnswerTime == null) {
      LOG.warn("Skipping webphone result code recovery. No registrar agent answer leg for parentId={}", callResultCode.getParentId());
      return false;
    }

    boolean hasInteractionStatus;
    try {
      hasInteractionStatus = cdrService.hasSuccessfulInteractionStatus(callResultCode.getParentId(), callResultCode.getUserId());
    } catch (Exception e) {
      LOG.warn("Skipping webphone result code recovery. wbp_interaction_status lookup failed for parentId=" + callResultCode.getParentId(), e);
      return false;
    }
    if (!hasInteractionStatus) {
      LOG.warn("Skipping webphone result code recovery. No webphone interaction status for parentId={} userId={}",
          callResultCode.getParentId(), callResultCode.getUserId());
      return false;
    }

    TelephonyResultCode normalClearing = resolveNormalClearingTrc();
    if (normalClearing == null) {
      LOG.warn("Skipping webphone result code recovery. Normal-clearing TRC unavailable for parentId={}", callResultCode.getParentId());
      return false;
    }

    User agent;
    try {
      agent = userService != null ? userService.getUserByIdWithGroup(callResultCode.getUserId()) : null;
    } catch (Exception e) {
      LOG.warn("Skipping webphone result code recovery. User lookup failed for userId=" + callResultCode.getUserId(), e);
      return false;
    }
    if (agent == null) {
      LOG.warn("Skipping webphone result code recovery. User not resolved for userId={} parentId={}", callResultCode.getUserId(), callResultCode.getParentId());
      return false;
    }

    ManagerConnectionDecorator connectionDecorator = findAnyOpenedConnection();
    if (connectionDecorator == null) {
      LOG.warn("Skipping webphone result code recovery. No opened connection for parentId={}", callResultCode.getParentId());
      return false;
    }

    Long originalTrcId = cdr.getTelephonyRC() != null ? cdr.getTelephonyRC().getId() : null;
    cdr.setUserResultCodeId(resultCodeId);
    cdr.setTelephonyRC(normalClearing);
    cdr.setCallHandlingStartTime(agentAnswerTime);

    Call syntheticCall = buildSyntheticSuccessCall(cdr, agent, callResultCode.getResultCode(), normalClearing, agentAnswerTime);

    try {
      UserPopulationEvaluator.populateCdrUserOwnership(syntheticCall);
      cdrService.saveOrUpdate(cdr);
    } catch (Exception e) {
      LOG.error("Webphone result code recovery: CDR persist failed for parentId=" + callResultCode.getParentId(), e);
      return false;
    }

    // FIX-333607: synthetic call liste satırının durumunu (rawData: deneme, retry/callback, tamamlanma)
    // taşımaz; satır bu çağrıyla yazılırsa bozulur ve kayıt yeniden aranır. Liste tablosu güncellenmez.
    LOG.warn("Webphone result code recovery: campaign list row is not updated for synthetic call. parentId={}, uniqueKey={}",
        callResultCode.getParentId(), syntheticCall.getUniqueKey());

    LOG.info("Webphone result code recovery applied. parentId={}, userId={}, resultCodeId={}, originalTrcId={}",
        callResultCode.getParentId(), callResultCode.getUserId(), resultCodeId, originalTrcId);
    return true;
  }

  private Call buildSyntheticSuccessCall(CDR cdr, User user, ResultCode resultCode, TelephonyResultCode trc, Date handlingStart) {
    Call call = new Call();
    call.setId(cdr.getInteractionId());
    call.setCampaign(cdr.getCampaign());
    call.setUser(user);
    call.setTelephonyResultCode(trc);
    call.setUserResultCode(resultCode);
    // Customer answer instant marks this as a successful connection for UserPopulationEvaluator. Under
    // event-worker lag PD may not have persisted callAnswerTime yet; fall back to the authoritative
    // agent-answer time (Asterisk registrar) so the promoted CDR is treated as a successful connection
    // and the user result code is preserved (not nulled by populateCdrUserOwnership).
    Date answerInstant = cdr.getCallAnswerTime() != null ? cdr.getCallAnswerTime() : handlingStart;
    if (answerInstant != null) {
      call.setAnswerDate(Utils.convertDateToDouble(answerInstant));
    }
    // Campaign result tablosunda rowExists check uniqueKey üzerinden yapılır
    // (CampaignDbOperations.rowExistsInResultTableByCallUniqueKey). Synthetic call'da
    // uniqueKey set edilmezse rowExists false döner ve INSERT yapılır → duplicate.
    // cdr.contactId, CallProducer.populateInitialCdr içinde Long.parseLong(call.getUniqueKey())
    // ile yazılır; ters dönüşüm String.valueOf(contactId) ile aynı kayda işaret eder.
    if (cdr.getContactId() != null) {
      call.setUniqueKey(String.valueOf(cdr.getContactId()));
    }
    call.setCdr(cdr);
    return call;
  }

  private TelephonyResultCode resolveNormalClearingTrc() {
    Map<Integer, TelephonyResultCode> ituToTrc = managerConnectionComposition.getItuToTelephonyResultCodeRelations();
    return ituToTrc != null ? ituToTrc.get(Constants.TELEPHONY_RC_NORMAL_CLEARING) : null;
  }

  private ManagerConnectionDecorator findAnyOpenedConnection() {
    Map<String, ManagerConnectionDecorator> opened = managerConnectionComposition.getOpenedConnections();
    if (opened == null || opened.isEmpty()) {
      return null;
    }
    return opened.values().iterator().next();
  }

  /**
   * Checks if the result code has to be set for a call.
   * We expect only the first result code from exactly the agent who has been assigned to this call.
   * @param call to check
   * @param resultCode to check
   * @return true if the given result code should be set for the given call
   */
  private boolean shouldSetResultCode(Call call, CallResultCode resultCode) {
    String message;
    if (call.getUserResultCode() != null)
      message = "call is already has a result code";
    else if (call.getUser() == null)
      message = "call has no assigned user";
    else if (!call.getUser().getId().equals(resultCode.getUserId()))
      message = "result code is not set by the user assigned to this call";
    else
      return true;
    LOG.info(COULD_NOT_SET_RESULT_CODE_MESSAGE + message, resultCode, call);
    return false;
  }

  /**
   * Create callback based on call result code
   * @param callResultCode
   * @param call
   */
  private void manageCallback(CallResultCode callResultCode, Call call) {
    call.setCallbackDate(Utils.convertDateToDouble(callResultCode.getResultCode().getCallbackDate()));

    String callbackNumber = callResultCode.getResultCode().getCallbackNumber();

    if (callResultCode.getResultCode() != null && callResultCode.getResultCode().getCallbackType() != null) {
      call.setCallbackType(callResultCode.getResultCode().getCallbackType());

      if (call.getCampaign().getAllowOverwriteByWebphone() && Constants.CALLBACKTYPE.ASSIGN_TO_USER_GROUP.equals(callResultCode.getResultCode().getCallbackType())) {
        call.setUser(null);
      }
    }

    if (!StringUtils.isEmpty(callbackNumber)) {
      PhoneNumberColumn phoneNumberColumn = getColumnToUpdateByCallbackNumber(call, callbackNumber);
      if (phoneNumberColumn != null)
        try {
          managerConnectionComposition.getCampaignDbOperations().updateCallField(call, phoneNumberColumn.getColNumber(), callbackNumber);
        } catch (Exception e) {
          LOG.error("Could not update callback number field for call " + call, e);
        }
      call.setCallbackNumber(callbackNumber);
    } else {
      call.setCallbackNumber(call.getCalledNumber());
    }
    call.addCallbackHistoryEntry(call.getCallbackNumber());
  }

  /**
   * Check whether callback number exists in user's current phone numbers.
   * Only updates if the number already exists - does NOT write to empty columns.
   *
   * @param call
   * @param callbackNumber
   * @return return {@link PhoneNumberColumn} which already contains the callback number,
   *         or null if callback number is not in user's existing phone numbers
   */
  private PhoneNumberColumn getColumnToUpdateByCallbackNumber(Call call, String callbackNumber) {
    for (PhoneNumberColumn phoneNumberColumn : call.getCampaign().getEnabledPhoneNumberColumns()) {
      if (call.getRawData().has(phoneNumberColumn.getColNumber())) {
        String number = String.valueOf(call.getRawData().get(phoneNumberColumn.getColNumber()));
        if (!StringUtils.isEmpty(number) && number.equals(callbackNumber)) {
          return phoneNumberColumn;
        }
      }
    }
    return null;
  }

  private void updateDNCByResultCode(Call call, Constants.DNC_SCOPE dncScope) {
    if (call.getUserResultCode() == null) return;

    DNC dnc = null;
    if (call.getUserResultCode().isDncIdentifier()) {
      dnc = call.getCampaign().getDnc();
    } else if (checkIsNeedDNCUpdate(call)) {
      dnc = getDNCBySystemResultCode(call);
    }

    if (dnc == null) {
      LOG.warn(call.getCampaign() + " does not have a default DNC list");
      return;
    }

    Set<String> dncNumbers = call.getDNCNumbers();
    if (dncNumbers == null)
      dncNumbers = new HashSet<>();
    dncNumbers.add(call.getCalledNumber());
    call.setDNCNumbers(dncNumbers);

    if (Constants.DNC_SCOPE.CUSTOMER.equals(dncScope)) {
      // Müşteri bazlı engelleme: müşterinin tüm numaraları PHONE kayıtlarına
      // genişletilir ve canlı kuyruklarda bekleyen eşleşen çağrılar düşürülür.
      Set<String> blockedNumbers = customerDncService.blockByCustomerScope(call, call.getCampaign(), dnc);
      dncEngine.dropQueuedCallsForNumbers(dnc, blockedNumbers);
      LOG.info("[CUSTOMER-DNC] Customer scope DNC update completed. callId={}, dncId={}, blockedCount={}",
          call.getId(), dnc.getId(), blockedNumbers != null ? blockedNumbers.size() : 0);
      return;
    }

    if (dncPhoneNumberService.existsByPhoneNumberAndDncId(call.getCalledNumber(), dnc.getId())) {
      LOG.info("[DEFAULT-DNC] Phone number already exists in DNC, skipping. phone={}, dncId={}", call.getCalledNumber(), dnc.getId());
      return;
    }

    DNCPhoneNumber dncPhoneNumber = dncPhoneNumberService.create(call.getCalledNumber(), Constants.REASON.WRAPUP_CODE, dnc);
    dncEngine.updatePhoneNumberInDnc(dncPhoneNumber);
    dncEngine.updateDnc(dnc.getId());
    LOG.info("[DEFAULT-DNC] DNC update completed. callId={}, phoneNumber={}, dncId={}", call.getId(), call.getCalledNumber(), dnc.getId());
  }


  /**
   * Fallback mechanism for DNC update when call is not found in memory.
   * v1 sınırı: CUSTOMER scope bu yolda PHONE'a degrade olur (yalnız
   * contactNumber eklenir); Redis subscriber thread'inde kampanya DB
   * lookup'ı yapılmaz, müşterinin kalan numaralarını delta-sync telafi eder.
   */
  private void updateDNCByCdrFallback(CallResultCode callResultCode, CDR cdr) {
    if(Objects.isNull(cdr) || Objects.isNull(cdr.getCampaign())) return;

    DNC dnc = cdr.getCampaign().getDnc();
    if (dnc == null) {
      LOG.warn(cdr.getCampaign() + " does not have a default DNC list");
      return;
    }

    if (Constants.DNC_SCOPE.CUSTOMER.equals(callResultCode.getDncScope())) {
      LOG.warn("[CUSTOMER-DNC] Call not in memory; customer scope degraded to PHONE for parentId={}. "
          + "Remaining customer numbers will be covered by delta-sync.", callResultCode.getParentId());
    }

    if (dncPhoneNumberService.existsByPhoneNumberAndDncId(cdr.getContactNumber(), dnc.getId())) {
      LOG.info("[DEFAULT-DNC] Phone number already exists in DNC (CDR fallback), skipping. phone={}, dncId={}", cdr.getContactNumber(), dnc.getId());
      return;
    }

    DNCPhoneNumber pn = dncPhoneNumberService.create(cdr.getContactNumber(), Constants.REASON.WRAPUP_CODE, dnc);
    dncEngine.updatePhoneNumberInDnc(pn);
    dncEngine.updateDnc(dnc.getId());
    LOG.info("[DEFAULT-DNC] DNC updated (CDR fallback). parentId={} number={}", callResultCode.getParentId(), cdr.getContactNumber());
  }

  /**
   * Check is need update call's campaign default DNC list.
   * @return true if call has any
   *         {@link com.powerdialer.util.Constants.SYSTEM_RESULT_CODE} otherwise
   *         return false
   */
  private boolean checkIsNeedDNCUpdate(Call call) {
    return call.getUserResultCode() != null && Stream.of(Constants.SYSTEM_RESULT_CODE.values())
        .anyMatch(rc -> rc.getId().equals(call.getUserResultCode().getId()));
  }

  /**
   * Get DNC by call's system result code. Either tenant global DNC or campaign
   * DNC
   */
  private DNC getDNCBySystemResultCode(Call call) {
    if (Constants.SYSTEM_RESULT_CODE.DONT_CALL_CAMPAIGN.getId().equals(call.getUserResultCode().getId())) {
      return call.getCampaign().getDnc();
    } else if (Constants.SYSTEM_RESULT_CODE.DONT_CALL_TENANT.getId().equals(call.getUserResultCode().getId())) {
      return dncService.getTenantDNC(call.getCampaign().getTenant().getId(), true, false);
    } else {
      return null;
    }
  }

  /**
   * Handles Redis message with entity type {@link Constants.MSG_ENTITY_TYPE#SERVER}.
   * Restarts or stops the AMI connection and call originator if needed.
   * @param redisMessage the message to handle
   */
  private void handleServerEntityMessage(RedisMessage redisMessage) throws AuthenticationFailedException, TimeoutException, IOException {
    Server updatedServer = objectMapper.convertValue(redisMessage.getSerializableObject(), Server.class);
    ManagerConnectionDecorator managerConnectionDecorator = getExistentConnection(updatedServer.getId());
    if (redisMessage.getType() == Constants.MSG_ACTION_TYPE.ADD) {
      handleAddServerMessage(updatedServer, managerConnectionDecorator);
    } else if (redisMessage.getType() == Constants.MSG_ACTION_TYPE.EDIT) {
      handleEditServerMessage(updatedServer, managerConnectionDecorator);
    } else if (redisMessage.getType() == MSG_ACTION_TYPE.DELETE) {
      handleDeleteServerMessage(managerConnectionDecorator);
    } else {
      LOG.error("Unknown server message action type: " + redisMessage.getType());
    }
  }

  private void handleAddServerMessage(Server updatedServer, ManagerConnectionDecorator existingConnection) throws AuthenticationFailedException, TimeoutException, IOException {
    if (existingConnection != null) {
      throw new IllegalStateException("There is established connection to the same IP already. Could not handle server adding");
    }
    if (updatedServer.getType() == SERVER_TYPE.REGISTRAR && updatedServer.isEnabled()) {
      managerConnectionComposition.openManagerConnection(updatedServer);
      if (updatedServer.isInUse()) {
        dialerFacade.startCallOriginatorForServer(updatedServer);
      }
    }
  }

  private void handleEditServerMessage(Server updatedServer, ManagerConnectionDecorator existingConnection) throws AuthenticationFailedException, TimeoutException, IOException {
    if (existingConnection == null) {
      if (updatedServer.getType() == SERVER_TYPE.REGISTRAR && updatedServer.isEnabled()) {
        managerConnectionComposition.openManagerConnection(updatedServer);
        if (updatedServer.isInUse()) {
          dialerFacade.startCallOriginatorForServer(updatedServer);
        }
      }
    } else {
      Server existingServer = existingConnection.getServer();
      if (updatedServer.getType() != SERVER_TYPE.REGISTRAR || !updatedServer.isEnabled()) {
        managerConnectionComposition.stopManagerConnection(existingServer.getIp());
        dialerFacade.stopCallOriginatorForServer(existingServer);
      } else if (serverConnectionPropertyIsChanged(existingServer, updatedServer)) {
        managerConnectionComposition.stopManagerConnection(existingServer.getIp());
        managerConnectionComposition.openManagerConnection(updatedServer);
      } else {
        existingConnection.setServer(updatedServer);
        if (existingServer.isInUse() && !updatedServer.isInUse()) {
          dialerFacade.stopCallOriginatorForServer(existingServer);
        } else if (!existingServer.isInUse() && updatedServer.isInUse()) {
          dialerFacade.startCallOriginatorForServer(updatedServer);
        }
      }
    }
  }

  private void handleDeleteServerMessage(ManagerConnectionDecorator existingConnection) {
    if (existingConnection != null) {
      Server existingServer = existingConnection.getServer();
      managerConnectionComposition.stopManagerConnection(existingServer.getIp());
      dialerFacade.stopCallOriginatorForServer(existingServer);
    }
  }

  /**
   * Gets existing server by id if there is corresponding AMI connection in the composition.
   * @param id the id of server to find.
   * @return server or null if there is no such connection.
   */
  private ManagerConnectionDecorator getExistentConnection(long id) {
    return managerConnectionComposition.getOpenedConnections().values()
        .stream()
        .filter(m -> m.getServer().getId().equals(id))
        .findAny()
        .orElse(null);
  }

  /**
   * Checks if either type, ip, port, username password or ami connection timeout of server is changed.
   * @param existingServer
   * @param server
   * @return true if there was a change in connection properties, else false
   */
  private boolean serverConnectionPropertyIsChanged(Server existingServer, Server server) {
    return !existingServer.getIp().equals(server.getIp())
        || !existingServer.getType().equals(server.getType())
        || isPropertyChanged(server, existingServer, Constants.SERVER_PROPERTY.AMI_PORT)
        || isPropertyChanged(server, existingServer, Constants.SERVER_PROPERTY.AMI_USERNAME)
        || isPropertyChanged(server, existingServer, Constants.SERVER_PROPERTY.AMI_PASSWORD)
        || isPropertyChanged(server, existingServer, Constants.SERVER_PROPERTY.AMI_CONNECTION_TIMEOUT);
  }

  private boolean isPropertyChanged(Server existingServer, Server server, Constants.SERVER_PROPERTY property) {
    String newProperty = server.getProperty(property.getName());
    String oldProperty = existingServer.getProperty(property.getName());
    if (newProperty == null)
      return oldProperty != null;
    else
      return !newProperty.equals(oldProperty);
  }

}
