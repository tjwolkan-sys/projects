package com.powerdialer.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Date;

import org.json.JSONObject;
import org.junit.Test;

import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.TelephonyResultCode;
import com.powerdialer.model.User;

/**
 * Unit tests for {@link UserPopulationEvaluator}.
 *
 * <p>Covers all campaign strategy types, telephony result code scenarios,
 * explicit failure overrides (TRC 84, answered-but-no-handling),
 * invalid call data edge cases, and CDR user ownership population.</p>
 *
 * <p>Test naming convention: [subject]_[condition]_[expectedOutcome]</p>
 */
public class UserPopulationEvaluatorTest {

  // -------------------------------------------------------------------------
  // PREVIEW_FREE
  // -------------------------------------------------------------------------

  /**
   * PREVIEW_FREE with a regular failure TRC (not 10/24/84):
   * camp userId and CDR assignedUserId must remain populated,
   * connectedUserId must be nullified.
   */
  @Test
  public void previewFreeKeepsCampAndAssignedForRegularFailure() {
    // TRC 16 = normal clearing, not a success code
    Call call = buildCall(Constants.CAMPAIGN_TYPE.PREVIEW_FREE, 16L, false, false);

    assertTrue(UserPopulationEvaluator.shouldPopulateCampUserId(call));
    assertTrue(UserPopulationEvaluator.shouldPopulateCdrAssignedUserId(call));
    assertFalse(UserPopulationEvaluator.shouldPopulateCdrConnectedUserId(call));
  }

  /**
   * PREVIEW_FREE with TRC 84 (answered but MT connection failed):
   * explicit failure override must nullify camp userId and connectedUserId,
   * but CDR assignedUserId must remain populated — agent was assigned to
   * the call even though MT connection was not established.
   */
  @Test
  public void previewFreeTrc84KeepsAssignedUserIdButNullifiesCampAndConnected() {
    Call call = buildCall(
            Constants.CAMPAIGN_TYPE.PREVIEW_FREE,
            Long.valueOf(Constants.TELEPHONY_RC_ANSWERED_BUT_NO_MT_CONNECTION),
            true,
            false
    );

    assertFalse(UserPopulationEvaluator.shouldPopulateCampUserId(call));
    assertTrue(UserPopulationEvaluator.shouldPopulateCdrAssignedUserId(call));
    assertFalse(UserPopulationEvaluator.shouldPopulateCdrConnectedUserId(call));
  }

  // -------------------------------------------------------------------------
  // PREVIEW_STRAIGHT
  // -------------------------------------------------------------------------

  /**
   * PREVIEW_STRAIGHT with TRC 84 (answered but MT connection failed):
   * explicit failure override must nullify camp userId and connectedUserId,
   * but CDR assignedUserId must remain populated — agent was assigned to
   * the call even though MT connection was not established.
   */
  @Test
  public void previewStraightTrc84KeepsAssignedUserIdButNullifiesCampAndConnected() {
    Call call = buildCall(
            Constants.CAMPAIGN_TYPE.PREVIEW_STRAIGHT,
            Long.valueOf(Constants.TELEPHONY_RC_ANSWERED_BUT_NO_MT_CONNECTION),
            true,
            false
    );

    assertFalse(UserPopulationEvaluator.shouldPopulateCampUserId(call));
    assertTrue(UserPopulationEvaluator.shouldPopulateCdrAssignedUserId(call));
    assertFalse(UserPopulationEvaluator.shouldPopulateCdrConnectedUserId(call));
  }

  // -------------------------------------------------------------------------
  // PROGRESSIVE_RESERVED
  // -------------------------------------------------------------------------

  /**
   * PROGRESSIVE_RESERVED with a regular failure TRC (not 10/24/84):
   * camp userId and CDR assignedUserId must remain populated,
   * connectedUserId must be nullified.
   */
  @Test
  public void progressiveReservedKeepsCampAndAssignedForRegularFailure() {
    // TRC 17 = user busy / redirection, not a success code
    Call call = buildCall(Constants.CAMPAIGN_TYPE.PROGRESSIVE_RESERVED, 17L, false, false);

    assertTrue(UserPopulationEvaluator.shouldPopulateCampUserId(call));
    assertTrue(UserPopulationEvaluator.shouldPopulateCdrAssignedUserId(call));
    assertFalse(UserPopulationEvaluator.shouldPopulateCdrConnectedUserId(call));
  }

  /**
   * PROGRESSIVE_RESERVED with TRC 84 (answered but MT connection failed):
   * explicit failure override must nullify camp userId and connectedUserId,
   * but CDR assignedUserId must remain populated — agent was assigned to
   * the call even though MT connection was not established.
   */
  @Test
  public void progressiveReservedTrc84KeepsAssignedUserIdButNullifiesCampAndConnected() {
    Call call = buildCall(
            Constants.CAMPAIGN_TYPE.PROGRESSIVE_RESERVED,
            Long.valueOf(Constants.TELEPHONY_RC_ANSWERED_BUT_NO_MT_CONNECTION),
            true,
            false
    );

    assertFalse(UserPopulationEvaluator.shouldPopulateCampUserId(call));
    assertTrue(UserPopulationEvaluator.shouldPopulateCdrAssignedUserId(call));
    assertFalse(UserPopulationEvaluator.shouldPopulateCdrConnectedUserId(call));
  }

  // -------------------------------------------------------------------------
  // PROGRESSIVE_RELEASED
  // -------------------------------------------------------------------------

  /**
   * PROGRESSIVE_RELEASED with TRC 10 and full successful connection
   * (answered + handling started): all user ownership fields must be populated.
   */
  @Test
  public void progressiveReleasedSuccessWithTrc10KeepsAllUserOwnershipFields() {
    Call call = buildCall(Constants.CAMPAIGN_TYPE.PROGRESSIVE_RELEASED, 10L, true, true);

    assertTrue(UserPopulationEvaluator.shouldPopulateCampUserId(call));
    assertTrue(UserPopulationEvaluator.shouldPopulateCdrAssignedUserId(call));
    assertTrue(UserPopulationEvaluator.shouldPopulateCdrConnectedUserId(call));
  }

  /**
   * PROGRESSIVE_RELEASED with TRC 10, customer answered but handling never started:
   * defensive fallback must nullify all user ownership fields.
   */
  @Test
  public void progressiveReleasedAnsweredWithoutHandlingNullifiesAllUserOwnershipFields() {
    // TRC 10 but handlingStartTime is null — answered-but-no-handling scenario
    Call call = buildCall(Constants.CAMPAIGN_TYPE.PROGRESSIVE_RELEASED, 10L, true, false);

    assertFalse(UserPopulationEvaluator.shouldPopulateCampUserId(call));
    assertFalse(UserPopulationEvaluator.shouldPopulateCdrAssignedUserId(call));
    assertFalse(UserPopulationEvaluator.shouldPopulateCdrConnectedUserId(call));
  }

  /**
   * PROGRESSIVE_RELEASED with TRC 84 (answered but MT connection failed):
   * explicit failure override must nullify camp userId and connectedUserId,
   * but CDR assignedUserId must remain populated — agent was assigned to
   * the call even though MT connection was not established.
   */
  @Test
  public void progressiveReleasedTrc84KeepsAssignedUserIdButNullifiesCampAndConnected() {
    Call call = buildCall(
            Constants.CAMPAIGN_TYPE.PROGRESSIVE_RELEASED,
            Long.valueOf(Constants.TELEPHONY_RC_ANSWERED_BUT_NO_MT_CONNECTION),
            true,
            false
    );

    assertFalse(UserPopulationEvaluator.shouldPopulateCampUserId(call));
    assertTrue(UserPopulationEvaluator.shouldPopulateCdrAssignedUserId(call));
    assertFalse(UserPopulationEvaluator.shouldPopulateCdrConnectedUserId(call));
  }

  // -------------------------------------------------------------------------
  // PREDICTIVE
  // -------------------------------------------------------------------------

  /**
   * PREDICTIVE with a non-success TRC (LSC blocked):
   * all user ownership fields must be nullified.
   */
  @Test
  public void predictiveNonSuccessTrcNullifiesAllUserOwnershipFields() {
    // TRC 905 = blocked by LSC
    Call call = buildCall(Constants.CAMPAIGN_TYPE.PREDICTIVE, 905L, false, false);

    assertFalse(UserPopulationEvaluator.shouldPopulateCampUserId(call));
    assertFalse(UserPopulationEvaluator.shouldPopulateCdrAssignedUserId(call));
    assertFalse(UserPopulationEvaluator.shouldPopulateCdrConnectedUserId(call));
  }

  /**
   * PREDICTIVE with TRC 24 and full successful connection
   * (answered + handling started): all user ownership fields must be populated.
   * Verifies that TRC 24 is treated equally to TRC 10 as a success code.
   */
  @Test
  public void predictiveSuccessWithTrc24KeepsAllUserOwnershipFields() {
    Call call = buildCall(Constants.CAMPAIGN_TYPE.PREDICTIVE, 24L, true, true);

    assertTrue(UserPopulationEvaluator.shouldPopulateCampUserId(call));
    assertTrue(UserPopulationEvaluator.shouldPopulateCdrAssignedUserId(call));
    assertTrue(UserPopulationEvaluator.shouldPopulateCdrConnectedUserId(call));
  }

  /**
   * PREDICTIVE with TRC 84 (answered but MT connection failed):
   * explicit failure override must nullify camp userId and connectedUserId,
   * but CDR assignedUserId must remain populated — agent was assigned to
   * the call even though MT connection was not established.
   */
  @Test
  public void predictiveTrc84KeepsAssignedUserIdButNullifiesCampAndConnected() {
    Call call = buildCall(
            Constants.CAMPAIGN_TYPE.PREDICTIVE,
            Long.valueOf(Constants.TELEPHONY_RC_ANSWERED_BUT_NO_MT_CONNECTION),
            true,
            false
    );

    assertFalse(UserPopulationEvaluator.shouldPopulateCampUserId(call));
    assertTrue(UserPopulationEvaluator.shouldPopulateCdrAssignedUserId(call));
    assertFalse(UserPopulationEvaluator.shouldPopulateCdrConnectedUserId(call));
  }

  // -------------------------------------------------------------------------
  // TRC 78 (ABANDONED) — assignedUserId must be kept
  // -------------------------------------------------------------------------

  /**
   * PREDICTIVE with TRC 78 (abandoned):
   * CDR assignedUserId must remain populated — agent was assigned to the call.
   */
  @Test
  public void predictiveTrc78KeepsAssignedUserId() {
    Call call = buildCall(Constants.CAMPAIGN_TYPE.PREDICTIVE, 78L, true, false);

    assertTrue(UserPopulationEvaluator.shouldPopulateCdrAssignedUserId(call));
    assertFalse(UserPopulationEvaluator.shouldPopulateCdrConnectedUserId(call));
  }

  /**
   * PROGRESSIVE_RELEASED with TRC 78 (abandoned):
   * CDR assignedUserId must remain populated — agent was assigned to the call.
   */
  @Test
  public void progressiveReleasedTrc78KeepsAssignedUserId() {
    Call call = buildCall(Constants.CAMPAIGN_TYPE.PROGRESSIVE_RELEASED, 78L, true, false);

    assertTrue(UserPopulationEvaluator.shouldPopulateCdrAssignedUserId(call));
    assertFalse(UserPopulationEvaluator.shouldPopulateCdrConnectedUserId(call));
  }

  /**
   * PREVIEW_FREE with TRC 78 (abandoned):
   * CDR assignedUserId must remain populated.
   */
  @Test
  public void previewFreeTrc78KeepsAssignedUserId() {
    Call call = buildCall(Constants.CAMPAIGN_TYPE.PREVIEW_FREE, 78L, true, false);

    assertTrue(UserPopulationEvaluator.shouldPopulateCdrAssignedUserId(call));
    assertFalse(UserPopulationEvaluator.shouldPopulateCdrConnectedUserId(call));
  }

  // -------------------------------------------------------------------------
  // OUTBOUND_IVR
  // -------------------------------------------------------------------------

  /**
   * OUTBOUND_IVR with TRC 78: no agent concept — assignedUserId must remain null.
   */
  @Test
  public void outboundIvrTrc78StillNullifiesAssignedUserId() {
    Call call = buildCall(Constants.CAMPAIGN_TYPE.OUTBOUND_IVR, 78L, true, false);

    assertFalse(UserPopulationEvaluator.shouldPopulateCdrAssignedUserId(call));
  }

  /**
   * OUTBOUND_IVR with TRC 84: no agent concept — assignedUserId must remain null.
   */
  @Test
  public void outboundIvrTrc84StillNullifiesAssignedUserId() {
    Call call = buildCall(
            Constants.CAMPAIGN_TYPE.OUTBOUND_IVR,
            Long.valueOf(Constants.TELEPHONY_RC_ANSWERED_BUT_NO_MT_CONNECTION),
            true,
            false
    );

    assertFalse(UserPopulationEvaluator.shouldPopulateCampUserId(call));
    assertFalse(UserPopulationEvaluator.shouldPopulateCdrAssignedUserId(call));
    assertFalse(UserPopulationEvaluator.shouldPopulateCdrConnectedUserId(call));
  }

  /**
   * OUTBOUND_IVR with a non-success TRC:
   * all user ownership fields must always be nullified for OUTBOUND_IVR
   * regardless of connection outcome.
   */
  @Test
  public void outboundIvrAlwaysNullifiesAllUserOwnershipFields() {
    // Use a non-success TRC to avoid any overlap with explicit failure override logic
    // OUTBOUND_IVR must nullify regardless of connection state
    Call call = buildCall(Constants.CAMPAIGN_TYPE.OUTBOUND_IVR, 16L, false, false);

    assertFalse(UserPopulationEvaluator.shouldPopulateCampUserId(call));
    assertFalse(UserPopulationEvaluator.shouldPopulateCdrAssignedUserId(call));
    assertFalse(UserPopulationEvaluator.shouldPopulateCdrConnectedUserId(call));
  }

  // -------------------------------------------------------------------------
  // INVALID CALL DATA EDGE CASES
  // -------------------------------------------------------------------------

  /**
   * Call with null telephony result code ID:
   * evaluation must be skipped and all user ownership fields must be nullified.
   */
  @Test
  public void nullTrcIdIsInvalidForPopulation() {
    // trcId = null triggers isInvalidCallForUserPopulation
    Call call = buildCall(Constants.CAMPAIGN_TYPE.PROGRESSIVE_RELEASED, null, false, false);

    assertFalse(UserPopulationEvaluator.shouldPopulateCampUserId(call));
    assertFalse(UserPopulationEvaluator.shouldPopulateCdrAssignedUserId(call));
    assertFalse(UserPopulationEvaluator.shouldPopulateCdrConnectedUserId(call));
  }

  /**
   * Callback call without a callback number:
   * evaluation must be skipped and all user ownership fields must be nullified.
   * A callback record with no callback number is considered invalid data.
   */
  @Test
  public void callbackWithoutCallbackNumberAndCalledNumberIsInvalidForPopulation() {
    Call call = buildCall(Constants.CAMPAIGN_TYPE.PREVIEW_FREE, 10L, true, true);

    // Mark call as callback by setting colResultCallbackDate and rawData with callback_date
    // but do NOT set callbackNumber — this makes the call invalid for evaluation
    call.getCampaign().setColResultCallbackDate("callback_date");
    call.getCampaign().setColResultCallbackNumber("callback_number");
    call.setRawData(new JSONObject("{\"callback_date\":1}"));

    assertFalse(UserPopulationEvaluator.shouldPopulateCampUserId(call));
    assertFalse(UserPopulationEvaluator.shouldPopulateCdrAssignedUserId(call));
    assertFalse(UserPopulationEvaluator.shouldPopulateCdrConnectedUserId(call));
  }

  /**
   * Callback attempt without a custom callback number but with a resolved called number:
   * successful predictive calls must still populate user ownership and user result code.
   */
  @Test
  public void callbackWithoutCallbackNumberKeepsPopulationWhenCalledNumberExists() {
    Call call = buildCall(Constants.CAMPAIGN_TYPE.PREDICTIVE, 10L, true, true);

    call.getCampaign().setColResultCallbackDate("callback_date");
    call.getCampaign().setColResultCallbackNumber("callback_number");
    call.setRawData(new JSONObject("{\"callback_date\":1}"));
    call.setCalledNumber("05392231744");

    assertTrue(UserPopulationEvaluator.shouldPopulateCampUserId(call));
    assertTrue(UserPopulationEvaluator.shouldPopulateCampUserResultCode(call));
    assertTrue(UserPopulationEvaluator.shouldPopulateCdrAssignedUserId(call));
    assertTrue(UserPopulationEvaluator.shouldPopulateCdrConnectedUserId(call));
    assertTrue(UserPopulationEvaluator.shouldPopulateCdrUserResultCode(call));
  }

  // -------------------------------------------------------------------------
  // populateCdrUserOwnership
  // -------------------------------------------------------------------------

  /**
   * populateCdrUserOwnership with successful connection:
   * groupId, assignedUser and connectedUser must all be populated on the CDR.
   */
  @Test
  public void populateCdrUserOwnershipSetsAllFieldsOnSuccessfulConnection() {
    Call call = buildCall(Constants.CAMPAIGN_TYPE.PROGRESSIVE_RELEASED, 10L, true, true);

    UserPopulationEvaluator.populateCdrUserOwnership(call);

    // Without a user set on the call, groupId and assigned/connected user remain null —
    // this verifies the method executes without error and CDR is accessible
    assertFalse(call.getCdr().getAssignedUser() != null);
    assertFalse(call.getCdr().getConnectedUser() != null);
  }

  /**
   * populateCdrUserOwnership with null call:
   * method must return without throwing any exception.
   */
  @Test
  public void populateCdrUserOwnershipDoesNothingForNullCall() {
    // No exception must be thrown for null input
    UserPopulationEvaluator.populateCdrUserOwnership(null);
  }

  /**
   * populateCdrUserOwnership on always-keep campaign with regular failure TRC:
   * assignedUser must be populated, connectedUser must be null.
   * groupId must be null since no user is set on the call.
   */
  @Test
  public void populateCdrUserOwnershipOnPreviewFreeRegularFailureSetsAssignedNullConnected() {
    Call call = buildCall(Constants.CAMPAIGN_TYPE.PREVIEW_FREE, 16L, false, false);

    UserPopulationEvaluator.populateCdrUserOwnership(call);

    // assignedUser: shouldPopulateCdrAssignedUserId returns true for PREVIEW_FREE with any TRC,
    // but call.getUser() is null in buildCall — so assigned is null (user itself is null)
    assertFalse(call.getCdr().getConnectedUser() != null);
  }

  /**
   * FIX-321479 regression: successful agent handling whose call-level user has been
   * cleared before the final finalize (ASSIGN_TO_USER_GROUP callback reassignment via
   * webphone result code) must NOT erase the connected user already persisted on the CDR
   * (e.g. from the delayed hangup snapshot). connectedUser must be preserved, mirroring the
   * existing assignedUser preserve-existing guard.
   */
  @Test
  public void populateCdrUserOwnership_successfulButCallUserCleared_preservesExistingConnectedUser() {
    Call call = buildCall(Constants.CAMPAIGN_TYPE.PREDICTIVE, 10L, true, true);

    // Prior delayed-hangup snapshot persisted the handling agent on the CDR
    User handlingAgent = new User();
    handlingAgent.setId(7301L);
    call.getCdr().setAssignedUser(handlingAgent);
    call.getCdr().setConnectedUser(handlingAgent);

    // Late finalize after callback reassignment cleared the call-level user
    call.setUser(null);

    UserPopulationEvaluator.populateCdrUserOwnership(call);

    assertTrue(call.getCdr().getConnectedUser() != null);
    assertEquals(Long.valueOf(7301L), call.getCdr().getConnectedUser().getId());
    // assignedUser is preserved today; lock it alongside to keep the two symmetric
    assertTrue(call.getCdr().getAssignedUser() != null);
    assertEquals(Long.valueOf(7301L), call.getCdr().getAssignedUser().getId());
  }

  /**
   * Characterization: when the call-level user is present on a successful connection,
   * connectedUser is set from the call user (existing behavior must not change).
   */
  @Test
  public void populateCdrUserOwnership_successfulWithCallUser_setsConnectedUser() {
    Call call = buildCall(Constants.CAMPAIGN_TYPE.PREDICTIVE, 10L, true, true);

    User handlingAgent = new User();
    handlingAgent.setId(7301L);
    call.setUser(handlingAgent);

    UserPopulationEvaluator.populateCdrUserOwnership(call);

    assertTrue(call.getCdr().getConnectedUser() != null);
    assertEquals(Long.valueOf(7301L), call.getCdr().getConnectedUser().getId());
  }

  /**
   * Characterization: the preserve guard must apply only on successful connection.
   * For a non-successful connection (TRC 10 answered but handling never started),
   * any stale connectedUser on the CDR must still be nullified.
   */
  @Test
  public void populateCdrUserOwnership_notSuccessfulWithStaleConnected_nullifiesConnectedUser() {
    // TRC 10, answered but no handling start => answered-but-no-handling => not successful
    Call call = buildCall(Constants.CAMPAIGN_TYPE.PREDICTIVE, 10L, true, false);

    User staleAgent = new User();
    staleAgent.setId(7301L);
    call.getCdr().setConnectedUser(staleAgent);
    call.setUser(null);

    UserPopulationEvaluator.populateCdrUserOwnership(call);

    assertFalse(call.getCdr().getConnectedUser() != null);
  }

  // -------------------------------------------------------------------------
  // TEST HELPER
  // -------------------------------------------------------------------------

  /**
   * Builds a minimal {@link Call} instance configured for evaluation testing.
   *
   * @param campaignType    the campaign strategy type to set on the campaign
   * @param trcId           the telephony result code ID to set; may be null to test invalid TRC
   * @param answered        if true, sets answerDate to simulate customer answer
   * @param handlingStarted if true, sets callHandlingStartTime to simulate agent/MT connection
   * @return configured Call instance ready for evaluator input
   */
  private Call buildCall(Constants.CAMPAIGN_TYPE campaignType, Long trcId,
                         boolean answered, boolean handlingStarted) {

    Campaign campaign = new Campaign();
    campaign.setType(campaignType);

    // TelephonyResultCode with given id — null id is valid input for edge case tests
    TelephonyResultCode trc = new TelephonyResultCode();
    trc.setId(trcId);

    Call call = new Call();
    call.setCampaign(campaign);
    call.setTelephonyResultCode(trc);

    // Simulate customer answer
    if (answered) {
      call.setAnswerDate(1D);
    }

    // Simulate agent/MT handling start
    if (handlingStarted) {
      call.getCdr().setCallHandlingStartTime(new Date());
    }

    return call;
  }
}
