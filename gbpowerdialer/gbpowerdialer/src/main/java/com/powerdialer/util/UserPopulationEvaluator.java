package com.powerdialer.util;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import com.powerdialer.model.CDR;
import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.TelephonyResultCode;
import com.powerdialer.model.User;

/**
 * Evaluates whether user identifier fields should remain populated
 * in campaign/result table and PD-CDR records according to campaign
 * strategy and telephony result code business rules.
 *
 * <p>Business rules:</p>
 *
 * <p>CAMP / RESULT TABLE:</p>
 * <ul>
 *   <li>PREVIEW_FREE: always keep userId</li>
 *   <li>PREVIEW_STRAIGHT: always keep userId</li>
 *   <li>PROGRESSIVE_RESERVED: always keep userId</li>
 *   <li>PROGRESSIVE_RELEASED: keep userId only for successful connection cases</li>
 *   <li>PREDICTIVE: keep userId only for successful connection cases</li>
 *   <li>OUTBOUND_IVR: always keep userId as NULL</li>
 * </ul>
 *
 * <p>PD-CDR TABLE (assignedUserId):</p>
 * <ul>
 *   <li>PREVIEW_FREE: always keep assignedUserId</li>
 *   <li>PREVIEW_STRAIGHT: always keep assignedUserId</li>
 *   <li>PROGRESSIVE_RESERVED: always keep assignedUserId</li>
 *   <li>PROGRESSIVE_RELEASED: keep assignedUserId only for successful connection cases</li>
 *   <li>PREDICTIVE: keep assignedUserId only for successful connection cases</li>
 *   <li>OUTBOUND_IVR: always keep assignedUserId as NULL</li>
 * </ul>
 *
 * <p>PD-CDR TABLE (connectedUserId):</p>
 * <ul>
 *   <li>All campaign types keep connectedUserId only for successful connection cases</li>
 * </ul>
 *
 * <p>Successful connection definition:</p>
 * <ul>
 *   <li>Telephony result code must be 10 or 24</li>
 *   <li>Customer must have answered (answerDate != null)</li>
 *   <li>Agent/MT handling must have started (callHandlingStartTime != null)</li>
 * </ul>
 *
 * <p>Explicit failure overrides (nullify regardless of campaign type):</p>
 * <ul>
 *   <li>TRC 84: customer answered but agent/MT connection failed</li>
 *   <li>TRC 10 or 24 with answerDate present but callHandlingStartTime absent:
 *       defensive fallback for incomplete connections</li>
 * </ul>
 *
 * <p>Callback rule:</p>
 * <ul>
 *   <li>If a call is marked as callback but no dialable number exists,
 *       user population is considered invalid.</li>
 * </ul>
 */
public class UserPopulationEvaluator {

  /**
   * Telephony result codes considered as successful customer-agent connection.
   * Only calls carrying one of these codes may qualify as successful connection.
   * 85 = Belirsiz Kayıt (327734): görüşme gerçekleşmiş, agent sahipliği korunur.
   */
  private static final Set<Long> SUCCESS_TELEPHONY_RESULT_CODES =
          new HashSet<Long>(Arrays.asList(10L,24L,85L));


  private static final Set<Long> BLOCK_TELEPHONY_RESULT_CODES =
          new HashSet<Long>(Arrays.asList(76L,77L));

  private static final long TRC_ABANDONED = 78L;
  private static final long TRC_UNHANDLED = 10L;

  /**
   * Campaign types where campaign/result table userId must always remain populated,
   * regardless of telephony result code.
   */
  private static final EnumSet<Constants.CAMPAIGN_TYPE> CAMP_ALWAYS_KEEP_USER_TYPES =
          EnumSet.of(
                  Constants.CAMPAIGN_TYPE.PREVIEW_FREE,
                  Constants.CAMPAIGN_TYPE.PREVIEW_STRAIGHT,
                  Constants.CAMPAIGN_TYPE.PROGRESSIVE_RESERVED
          );

  /**
   * Campaign types where PD-CDR assignedUserId must always remain populated,
   * regardless of telephony result code.
   */
  private static final EnumSet<Constants.CAMPAIGN_TYPE> CDR_ASSIGNED_ALWAYS_KEEP_USER_TYPES =
          EnumSet.of(
                  Constants.CAMPAIGN_TYPE.PREVIEW_FREE,
                  Constants.CAMPAIGN_TYPE.PREVIEW_STRAIGHT,
                  Constants.CAMPAIGN_TYPE.PROGRESSIVE_RESERVED
          );

  /**
   * Campaign types where user field population depends on successful connection outcome.
   * For these types, user fields are populated only when connection is fully established.
   */
  private static final EnumSet<Constants.CAMPAIGN_TYPE> SUCCESS_BASED_USER_TYPES =
          EnumSet.of(
                  Constants.CAMPAIGN_TYPE.PROGRESSIVE_RELEASED,
                  Constants.CAMPAIGN_TYPE.PREDICTIVE
          );

  /**
   * Validates whether call contains minimum required data
   * before user population decision is evaluated.
   *
   * @param call call entity to validate
   * @return true if call is invalid for evaluation, false if evaluation can proceed
   */
  private static boolean isInvalidCallForUserPopulation(Call call) {
    if (call == null) {
      return true;
    }

    Campaign campaign = call.getCampaign();
    CDR cdr = call.getCdr();
    TelephonyResultCode trc = call.getTelephonyResultCode();

    // Mandatory references must exist before any evaluation
    if (campaign == null || cdr == null || trc == null) {
      return true;
    }

    // Campaign type must be present to determine strategy-based rules
    if (campaign.getType() == null) {
      return true;
    }

    // Callback records must still have a number that was actually dialed. Some callback
    // attempts reuse the regular called number when no custom callback number exists.
    if (isCallbackWithoutDialableNumber(call) && !BLOCK_TELEPHONY_RESULT_CODES.contains(call.getTelephonyResultCode().getId())) {
      return true;
    }

    // Telephony result code id must exist to apply business rules
    return trc.getId() == null;
  }

  private static boolean isCallbackWithoutDialableNumber(Call call) {
    return call.isCallback()
            && !hasUsableText(call.getCallbackNumber())
            && !hasUsableText(call.getCalledNumber());
  }

  private static boolean hasUsableText(String value) {
    if (value == null) {
      return false;
    }

    String trimmedValue = value.trim();
    return trimmedValue.length() > 0 && !"null".equalsIgnoreCase(trimmedValue);
  }

  /**
   * Determines whether the call must be treated as an explicit unsuccessful ownership case,
   * overriding any campaign-type-based always-keep rules.
   *
   * <p>Explicit failure cases:</p>
   * <ul>
   *   <li>TRC 84: customer answered but agent/MT connection never completed.</li>
   *   <li>TRC 10 or 24 with answerDate present but callHandlingStartTime absent:
   *       defensive fallback — even a success code does not guarantee MT connection
   *       if handling was never started.</li>
   * </ul>
   *
   * <p>Note: This method is applied in {@link #shouldPopulateCampUserId} and
   * {@link #shouldPopulateCdrAssignedUserId} to override always-keep rules for
   * preview and progressive reserved campaign types when an explicit failure is detected.</p>
   *
   * @param call call entity to evaluate
   * @return true if user ownership must be treated as unsuccessful regardless of campaign type
   */
  private static boolean isExplicitFailureOverride(Call call) {
    if (call == null
            || call.getTelephonyResultCode() == null
            || call.getTelephonyResultCode().getId() == null) {
      return false;
    }

    Long trcId = call.getTelephonyResultCode().getId();

    // TRC 84 explicitly means customer answered but MT/agent connection never completed
    if (Long.valueOf(Constants.TELEPHONY_RC_ANSWERED_BUT_NO_MT_CONNECTION).equals(trcId)) {
      return true;
    }

    // Defensive fallback: if TRC is not a success code, no override needed here —
    // isSuccessfulConnection will already return false for non-success TRCs
    if (!SUCCESS_TELEPHONY_RESULT_CODES.contains(trcId)) {
      return false;
    }

    // TRC is 10 or 24 but handling was never started — customer answered but MT failed
    CDR cdr = call.getCdr();
    return call.getAnswerDate() != null
            && cdr != null
            && cdr.getCallHandlingStartTime() == null;
  }

  /**
   * Determines whether call reached a successful customer-agent connection.
   *
   * <p>Success criteria (all must be satisfied):</p>
   * <ul>
   *   <li>No explicit failure override must be present</li>
   *   <li>Telephony result code must be one of the successful codes (10, 24, 85)</li>
   *   <li>Customer must have answered (answerDate must be present)</li>
   *   <li>Agent/MT handling must have started (callHandlingStartTime must be present)</li>
   * </ul>
   *
   * @param call call entity to evaluate
   * @return true if successful customer-agent connection exists, false otherwise
   */
  private static boolean isSuccessfulConnection(Call call) {
    if (call == null
            || call.getTelephonyResultCode() == null
            || call.getTelephonyResultCode().getId() == null) {
      return false;
    }

    // Explicit failure overrides take priority over all other conditions
    if (isExplicitFailureOverride(call)) {
      return false;
    }

    Long trcId = call.getTelephonyResultCode().getId();

    // Only pre-defined success codes (10, 24, 85) can qualify as successful connection
    if (!SUCCESS_TELEPHONY_RESULT_CODES.contains(trcId)) {
      return false;
    }

    CDR cdr = call.getCdr();
    if (cdr == null) {
      return false;
    }

    // Both customer answer and agent/MT handling start must be present
    // to confirm a complete and successful customer-agent connection
    return call.getAnswerDate() != null && cdr.getCallHandlingStartTime() != null;
  }

  /**
   * Determines whether campaign/result table userId should remain populated.
   *
   * <p>Rules:</p>
   * <ul>
   *   <li>If call data is invalid for evaluation, returns false</li>
   *   <li>If explicit failure override is detected (TRC 84 or answered-but-no-handling),
   *       returns false regardless of campaign type</li>
   *   <li>Always-keep campaign types (PREVIEW_FREE, PREVIEW_STRAIGHT, PROGRESSIVE_RESERVED)
   *       return true</li>
   *   <li>Success-based campaign types (PROGRESSIVE_RELEASED, PREDICTIVE)
   *       return true only for successful connection cases</li>
   *   <li>OUTBOUND_IVR always returns false</li>
   *   <li>Unknown campaign types return false</li>
   * </ul>
   *
   * @param call call entity to evaluate
   * @return true if campaign/result table userId should remain populated,
   *         false if it should be nullified
   */
  public static boolean shouldPopulateCampUserId(Call call) {
    // Invalid call data — cannot make a population decision
    if (isInvalidCallForUserPopulation(call)) {
      return false;
    }

    // Explicit failure override applies to all campaign types including always-keep types
    if (isExplicitFailureOverride(call)) {
      return false;
    }

    Constants.CAMPAIGN_TYPE type = call.getCampaign().getType();

    // Preview and progressive reserved campaigns always keep userId
    if (CAMP_ALWAYS_KEEP_USER_TYPES.contains(type)) {
      if (BLOCK_TELEPHONY_RESULT_CODES.contains(call.getTelephonyResultCode().getId())) {
        return false;
      }
      else{
        return true;
      }
    }

    // Progressive released and predictive campaigns keep userId only on successful connection
    if (SUCCESS_BASED_USER_TYPES.contains(type)) {
      return isSuccessfulConnection(call);
    }

    // OUTBOUND_IVR and any unknown campaign type must not populate userId
    return false;
  }


  public static boolean shouldPopulateCampUserResultCode(Call call) {
    if (call == null || call.getCampaign() == null) {
      return false;
    }

    // allowUrcWithoutMt enabled — never nullify URC
    if (Boolean.TRUE.equals(call.getCampaign().getAllowUrcWithoutMt())) {
      return true;
    }

    if (isInvalidCallForUserPopulation(call)) {
      return false;
    }

    if (isExplicitFailureOverride(call)) {
      return false;
    }

    // TRC 10 with answered + dial time but no handling = abandoned call
    if (isAbandonedWithSuccessTrc(call)) {
      return false;
    }

    return isSuccessfulConnection(call);
  }

  private static boolean isAbandonedWithSuccessTrc(Call call) {
    if (call == null
            || call.getTelephonyResultCode() == null
            || call.getTelephonyResultCode().getId() == null
            || call.getCdr() == null) {
      return false;
    }

    Long trcId = call.getTelephonyResultCode().getId();
    CDR cdr = call.getCdr();

    return Long.valueOf(10L).equals(trcId)
            && call.getAnswerDate() != null
            && cdr.getCallDialTime() != null
            && cdr.getCallHandlingStartTime() == null;
  }

  public static boolean shouldPopulateCdrUserResultCode(Call call) {
    return shouldPopulateCampUserResultCode(call);
  }

  /**
   * Determines whether PD-CDR assignedUserId should remain populated.
   *
   * <p>Rules:</p>
   * <ul>
   *   <li>If call data is invalid for evaluation, returns false</li>
   *   <li>TRC 78 (abandoned) and TRC 84 (customer answered but MT connection failed):
   *       agent was assigned to the call — assignedUserId is kept for all agent-based
   *       campaign types (only OUTBOUND_IVR excluded)</li>
   *   <li>If explicit failure override is detected for answered-but-no-handling (TRC 10/24
   *       without callHandlingStartTime), returns false regardless of campaign type</li>
   *   <li>Always-keep campaign types (PREVIEW_FREE, PREVIEW_STRAIGHT, PROGRESSIVE_RESERVED)
   *       return true</li>
   *   <li>Success-based campaign types (PROGRESSIVE_RELEASED, PREDICTIVE)
   *       return true only for successful connection cases</li>
   *   <li>OUTBOUND_IVR always returns false</li>
   *   <li>Unknown campaign types return false</li>
   * </ul>
   *
   * @param call call entity to evaluate
   * @return true if PD-CDR assignedUserId should remain populated,
   *         false if it should be nullified
   */
  public static boolean shouldPopulateCdrAssignedUserId(Call call) {
    // Invalid call data — cannot make a population decision
    if (isInvalidCallForUserPopulation(call)) {
      return false;
    }

    Long trcId = call.getTelephonyResultCode().getId();
    Constants.CAMPAIGN_TYPE type = call.getCampaign().getType();

    // TRC 78 (abandoned) and TRC 84 (customer answered but MT connection failed):
    // Agent was assigned to the call — keep assignedUserId for all agent-based campaign types.
    if (Long.valueOf(TRC_ABANDONED).equals(trcId)
            || Long.valueOf(Constants.TELEPHONY_RC_ANSWERED_BUT_NO_MT_CONNECTION).equals(trcId)) {
      // OUTBOUND_IVR has no agent concept — assignedUserId must remain null
      return type != Constants.CAMPAIGN_TYPE.OUTBOUND_IVR;
    }

    // Explicit failure override (answered-but-no-handling for TRC 10/24)
    // applies to all campaign types including always-keep types
    if (isExplicitFailureOverride(call)) {
      return false;
    }

    // Preview and progressive reserved campaigns always keep assignedUserId
    if (CDR_ASSIGNED_ALWAYS_KEEP_USER_TYPES.contains(type)) {
      return true;
    }

    // Progressive released and predictive campaigns keep assignedUserId only on successful connection
    if (SUCCESS_BASED_USER_TYPES.contains(type)) {
      return isSuccessfulConnection(call);
    }

    // OUTBOUND_IVR and any unknown campaign type must not populate assignedUserId
    return false;
  }

  /**
   * Determines whether PD-CDR connectedUserId should remain populated.
   *
   * <p>Rules:</p>
   * <ul>
   *   <li>connectedUserId population is independent from campaign type</li>
   *   <li>connectedUserId is kept only when customer-agent connection is fully successful</li>
   *   <li>TRC 84 or missing handling start time always results in nullification</li>
   * </ul>
   *
   * @param call call entity to evaluate
   * @return true if PD-CDR connectedUserId should remain populated,
   *         false if it should be nullified
   */
  public static boolean shouldPopulateCdrConnectedUserId(Call call) {
    // Invalid call data — cannot make a population decision
    if (isInvalidCallForUserPopulation(call)) {
      return false;
    }

    // Connected user is kept only when customer-agent connection is fully successful
    return isSuccessfulConnection(call);
  }

  /**
   * Applies all CDR user ownership field assignments for the given call in a single operation.
   *
   * <p>Sets the following CDR fields based on campaign strategy and connection outcome:</p>
   * <ul>
   *   <li>groupId: populated when user and group are present, nullified otherwise</li>
   *   <li>assignedUser: populated or nullified based on {@link #shouldPopulateCdrAssignedUserId}</li>
   *   <li>connectedUser: populated or nullified based on {@link #shouldPopulateCdrConnectedUserId}</li>
   * </ul>
   *
   * <p>This method centralizes all CDR user ownership set operations that were previously
   * duplicated across {@code ManagerConnectionDecorator}, {@code CallOriginator},
   * and {@code DNCEngine}. Callers only need to invoke this single method.</p>
   *
   * <p>If the call is null or CDR is not accessible, the method returns without any action.</p>
   *
   * @param call call entity containing user, CDR, campaign, and telephony result code references
   */
  public static void populateCdrUserOwnership(Call call) {
    if (call == null || call.getCdr() == null) {
      return;
    }

    CDR cdr = call.getCdr();
    User user = call.getUser();

    // Set groupId when user and group references are present.
    // If user is null but assignedUser should be kept, preserve existing groupId.
    if (user != null && user.getGroup() != null) {
      cdr.setGroupId(user.getGroup().getId());
    } else if (!shouldPopulateCdrAssignedUserId(call)) {
      cdr.setGroupId(null);
    }

    // Set assignedUser based on campaign strategy and connection outcome.
    // When population is allowed, prefer the call-level user; if absent, preserve
    // the value already persisted on the CDR (e.g. from the initial answer save)
    // so that a late hangup processing with a cleared call.user does not erase it.
    if (shouldPopulateCdrAssignedUserId(call)) {
      if (user != null) {
        cdr.setAssignedUser(user);
      }
      // else: keep existing cdr.assignedUser untouched
    } else {
      cdr.setAssignedUser(null);
    }

    // Set connectedUser based on connection outcome only (independent of campaign type).
    // When population is allowed, prefer the call-level user; if absent, preserve the value
    // already persisted on the CDR (e.g. from the delayed hangup snapshot) so that a late
    // result-code finalize with a cleared call.user (ASSIGN_TO_USER_GROUP callback reassignment)
    // does not erase the connected user of a successful agent handling. Mirrors the assignedUser
    // preserve-existing guard above.
    if (shouldPopulateCdrConnectedUserId(call)) {
      if (user != null) {
        cdr.setConnectedUser(user);
      }
      // else: keep existing cdr.connectedUser untouched
    } else {
      cdr.setConnectedUser(null);
    }

    if(!shouldPopulateCdrUserResultCode(call)){
      cdr.setUserResultCodeId(null);
    }

    evaluateAbandoned(call);
  }


  /**
   * Determines whether the call should be marked as abandoned.
   *
   * <p>Abandoned cases:</p>
   * <ul>
   *   <li>TRC 78: explicitly abandoned call</li>
   *   <li>TRC 84: customer hangup before agent connection</li>
   *   <li>TRC 10 with start, dial, answer present but handling absent:
   *       customer answered but agent connection was never completed</li>
   * </ul>
   *
   * @param call call entity to evaluate
   */
  public static void evaluateAbandoned(Call call) {
    if (call == null
            || call.getTelephonyResultCode() == null
            || call.getTelephonyResultCode().getId() == null
            || call.getCdr() == null) {
      return;
    }

    Long trcId = call.getTelephonyResultCode().getId();
    CDR cdr = call.getCdr();


    if (trcId == TRC_ABANDONED) {
      call.setAbandoned(true);
      return;
    }

    if (Long.valueOf(Constants.TELEPHONY_RC_ANSWERED_BUT_NO_MT_CONNECTION).equals(trcId)) {
      call.setAbandoned(true);
      return;
    }

    // TRC 10 with start, dial, answer present but handling absent
    if (trcId == TRC_UNHANDLED
            && cdr.getCallStartTime() != null
            && cdr.getCallDialTime() != null
            && cdr.getCallAnswerTime() != null
            && cdr.getCallHandlingStartTime() == null) {
      call.setAbandoned(true);
      return;
    }

    call.setAbandoned(false);
  }
}
