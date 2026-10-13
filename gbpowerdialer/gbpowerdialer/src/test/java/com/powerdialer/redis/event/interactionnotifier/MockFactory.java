package com.powerdialer.redis.event.interactionnotifier;

import com.powerdialer.model.*;
import com.powerdialer.util.Constants;

import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Central factory for building fully-populated mock objects used across all unit tests.
 *
 * <p>Each {@code build*()} method returns a Mockito mock configured with realistic
 * values so that mappers can exercise every branch without per-test boilerplate.</p>
 *
 * <p>Where a field is an entity relationship (e.g. {@code User}, {@code Campaign}),
 * a separate lightweight mock is created and wired in — mirroring production object graphs.</p>
 */
public final class MockFactory {

    /** Fixed epoch ms used as a stable "now" across all date assertions. */
    public static final long NOW_MS = 1_700_000_000_000L;

    /** Convenience constant: {@link #NOW_MS} as a {@link Date}. */
    public static final Date NOW_DATE = new Date(NOW_MS);

    /**
     * Double representation of {@link #NOW_MS} — this is how dates arrive from
     * the Redis / Hazelcast layer inside the {@code Call} model.
     */
    public static final Double NOW_DOUBLE = (double) NOW_MS;

    // -----------------------------------------------------------------------
    // User
    // -----------------------------------------------------------------------

    /**
     * Builds a mock {@link User} with id=1, username="agent01",
     * adNickname="nick01", fullname="John Doe".
     *
     * @return configured mock User
     */
    public static User buildUser() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(1L);
        when(user.getUsername()).thenReturn("agent01");
        when(user.getAdNickname()).thenReturn("nick01");
        when(user.getFullname()).thenReturn("John Doe");
        return user;
    }

    // -----------------------------------------------------------------------
    // ResultCode / TelephonyResultCode
    // -----------------------------------------------------------------------

    /**
     * Builds a mock {@link ResultCode} (user result code) with id=3,
     * name="YENI RANDEVU VERILDI" — maps to {@code CallResultCodeEnum} id 3.
     *
     * @return configured mock ResultCode
     */
    public static ResultCode buildUserResultCode() {
        ResultCode rc = mock(ResultCode.class);
        when(rc.getId()).thenReturn(3L);
        when(rc.getName()).thenReturn("YENI RANDEVU VERILDI");
        return rc;
    }

    /**
     * Builds a mock {@link TelephonyResultCode} with id=10, name="ANSWER".
     *
     * @return configured mock TelephonyResultCode
     */
    public static TelephonyResultCode buildTelephonyResultCode() {
        TelephonyResultCode trc = mock(TelephonyResultCode.class);
        when(trc.getId()).thenReturn(10L);
        when(trc.getName()).thenReturn("ANSWER");
        return trc;
    }

    // -----------------------------------------------------------------------
    // Campaign
    // -----------------------------------------------------------------------

    /**
     * Builds a minimal mock {@link Campaign} sufficient for {@code CampaignInMapper}.
     *
     * <p>All nullable nested entities ({@code tenant}, {@code ivrApplication},
     * {@code callerIdPool}, {@code dnc}) are populated with lightweight mocks.</p>
     *
     * @return configured mock Campaign
     */
    public static Campaign buildCampaign() {
        Campaign c = mock(Campaign.class);

        // identity
        when(c.getId()).thenReturn(42L);
        when(c.getName()).thenReturn("TestCampaign");
        when(c.getDescription()).thenReturn("desc");
        when(c.getType()).thenReturn(Constants.CAMPAIGN_TYPE.PREDICTIVE);
        when(c.isEnabled()).thenReturn(true);
        when(c.isDeleted()).thenReturn(false);
        when(c.getContext()).thenReturn("ctx");
        when(c.getAbandonIvr()).thenReturn("abandon_ivr");
        when(c.getVersion()).thenReturn(1);

        // Build nested entity mocks before wiring — avoids UnfinishedStubbingException in Mockito 3.x
        Tenant tenant = mock(Tenant.class);
        when(tenant.getId()).thenReturn(99L);

        IVRApplication ivr = mock(IVRApplication.class);
        when(ivr.getId()).thenReturn(5L);

        when(c.getTenant()).thenReturn(tenant);
        when(c.getIvrApplication()).thenReturn(ivr);

        // dates
        when(c.getStartDate()).thenReturn(NOW_DATE);
        when(c.getEndDate()).thenReturn(NOW_DATE);
        when(c.getStoppedDate()).thenReturn(null);
        when(c.getCreateDate()).thenReturn(NOW_DATE);
        when(c.getUpdateDate()).thenReturn(NOW_DATE);

        // preview
        when(c.getPreviewTimeout()).thenReturn(30);
        when(c.getPreviewRelax()).thenReturn(5);
        when(c.getAvailableThreshold()).thenReturn(2);
        when(c.getAllowCallback()).thenReturn(true);
        when(c.getAllowPreviewBeep()).thenReturn(false);
        when(c.getAllowCallbackCustom()).thenReturn(false);
        when(c.getCallbackToAppointer()).thenReturn(true);
        when(c.getAllowOverwriteByWebphone()).thenReturn(false);
        when(c.getAppointerReset()).thenReturn(false);
        when(c.getAppointerResetTimeout()).thenReturn(0L);
        when(c.getEndListBehaviour()).thenReturn(Constants.END_LIST_BEHAVIOUR.STOP_DISCARDING_RETRIES_AND_CALLBACKS);
        when(c.getLowCacheSelectInterval()).thenReturn(10);

        // retry
        when(c.getRetryStrategy()).thenReturn(Constants.RETRY_STRATEGY.REACH_RETRY_LIMIT_FOR_SINGLE_NUMBER);
        when(c.getRetryScheduleEnabled()).thenReturn(false);
        when(c.getRetryOveralAttempt()).thenReturn(3);
        when(c.getMacroInterval()).thenReturn(60);
        when(c.getMacroMaxAttempt()).thenReturn(5);
        when(c.getMaxAttemptPerNumber()).thenReturn(10);
        when(c.getRetryRatio()).thenReturn(1);
        when(c.getRetryDelay()).thenReturn(0.5);
        when(c.getRetryCallerIdBehavior()).thenReturn(Constants.RETRY_CALLER_ID_BEHAVIOR.SAME);

        // caller id
        when(c.getCallerIdBehaviour()).thenReturn(Constants.CALLER_ID_BEHAVIOUR.FIXED);
        when(c.getCallerId()).thenReturn("02121234567");
        when(c.getCallerIdPool()).thenReturn(null);
        when(c.getTimezoneType()).thenReturn(Constants.TIMEZONE_TYPE.OFF);
        when(c.getApplyFilterTo()).thenReturn(Constants.APPLY_TO.RETRIES);
        when(c.getFilterJoinType()).thenReturn(Constants.FILTER_JOIN_TYPE.AND);

        // dialing
        when(c.getConcurrentLines()).thenReturn(10);
        when(c.getCallsPerSecond()).thenReturn(5);
        when(c.getCacheLimit()).thenReturn(100);
        when(c.getRingTimeout()).thenReturn(30);
        when(c.getDialingBoost()).thenReturn(1.2f);
        when(c.getAbandonTimeout()).thenReturn(5);
        when(c.getAmdEnabled()).thenReturn(false);
        when(c.getMaxAnswerRate()).thenReturn(0.9f);
        when(c.getMaxAbandonRate()).thenReturn(0.05f);
        when(c.getGammaCorrectionFactor()).thenReturn(1.0);
        when(c.getGammaAbandonSensitivity()).thenReturn(0.5);
        when(c.getGammaWindowCapacity()).thenReturn(50);
        when(c.getGammaRequiredWindowSize()).thenReturn(20);
        when(c.getPredictiveStrategy()).thenReturn(Constants.PREDICTIVE_STRATEGY.GAMMA);
        when(c.getMohClassCallingParty()).thenReturn("default");
        when(c.getStartupDbCheck()).thenReturn(true);
        when(c.getResultCodeTimerStatus()).thenReturn(0L);
        when(c.getResultCodeTimer()).thenReturn(0);

        // schedule
        when(c.getScheduleEnabled()).thenReturn(true);
        when(c.getScheduleAutomationEnabled()).thenReturn(false);

        // dnc / lsc
        when(c.getDncEnabled()).thenReturn(false);
        when(c.getDnc()).thenReturn(null);
        when(c.getLscEnabled()).thenReturn(false);
        when(c.getLscTimeoutBehavior()).thenReturn(Constants.LSC_TIMEOUT_BEHAVIOR.DIAL);
        when(c.getFullContactListCheckEnabled()).thenReturn(false);
        when(c.getSkillEnabled()).thenReturn(false);
        when(c.getAttachedDataLongTermSave()).thenReturn(false);

        // stop condition
        when(c.getStopConditionEnabled()).thenReturn(false);
        when(c.getStopConditionParameter()).thenReturn(null);
        when(c.getStopConditionValue()).thenReturn(null);
        when(c.getStopConditionThreshold()).thenReturn(null);
        when(c.getStopType()).thenReturn(null);
        when(c.getStopTypeHistory()).thenReturn(null);

        // api
        when(c.getAllowApiControl()).thenReturn(false);
        when(c.getApiToken()).thenReturn("token123");

        return c;
    }

    // -----------------------------------------------------------------------
    // CDR
    // -----------------------------------------------------------------------

    /**
     * Builds a mock {@link CDR} with all fields populated.
     * Nested entity mocks (tenant, campaign, server, users, telephonyRC) are
     * created inline.
     *
     * @return configured mock CDR
     */
    public static CDR buildCdr() {
        CDR cdr = mock(CDR.class);
        when(cdr.getId()).thenReturn(77L);
        when(cdr.getInteractionId()).thenReturn("parent-001");
        when(cdr.getOutgoingCallId()).thenReturn("call-001");
        when(cdr.getInternalCallId()).thenReturn("internal-001");
        when(cdr.getContactNumber()).thenReturn("5551234567");
        when(cdr.getContactNumberColumn()).thenReturn("PHONE1");
        when(cdr.getContactNumberOrder()).thenReturn(1);
        when(cdr.getRcSequence()).thenReturn(2);
        when(cdr.getSkills()).thenReturn("skill1,skill2");
        when(cdr.getConnectedIvrNumber()).thenReturn("ivr-01");
        when(cdr.getCdrOutdialingType()).thenReturn(Constants.CDR_OUTDIALING_TYPE.NEW);
        when(cdr.getCampaignType()).thenReturn(1L);
        when(cdr.getContactId()).thenReturn(500L);
        when(cdr.getGroupId()).thenReturn(10L);

        // dates
        when(cdr.getCallStartTime()).thenReturn(NOW_DATE);
        when(cdr.getCallDialTime()).thenReturn(NOW_DATE);
        when(cdr.getCallAnswerTime()).thenReturn(NOW_DATE);
        when(cdr.getCallHandlingStartTime()).thenReturn(NOW_DATE);
        when(cdr.getCallEndTime()).thenReturn(NOW_DATE);

        // Build all nested entity mocks first — avoids UnfinishedStubbingException in Mockito 3.x
        Tenant cdrTenant = mock(Tenant.class);
        when(cdrTenant.getId()).thenReturn(99L);

        Campaign cdrCampaign = mock(Campaign.class);
        when(cdrCampaign.getId()).thenReturn(42L);

        Server cdrServer = mock(Server.class);
        when(cdrServer.getId()).thenReturn(3L);

        TelephonyResultCode cdrTrc = mock(TelephonyResultCode.class);
        when(cdrTrc.getId()).thenReturn(10L);

        User assignedUser = mock(User.class);
        when(assignedUser.getId()).thenReturn(1L);

        User connectedUser = mock(User.class);
        when(connectedUser.getId()).thenReturn(2L);

        // Wire nested mocks into CDR
        when(cdr.getTenant()).thenReturn(cdrTenant);
        when(cdr.getCampaign()).thenReturn(cdrCampaign);
        when(cdr.getServer()).thenReturn(cdrServer);
        when(cdr.getTelephonyRC()).thenReturn(cdrTrc);
        when(cdr.getAssignedUser()).thenReturn(assignedUser);
        when(cdr.getConnectedUser()).thenReturn(connectedUser);

        return cdr;
    }

    // -----------------------------------------------------------------------
    // Call (fully populated)
    // -----------------------------------------------------------------------

    /**
     * Builds a fully-populated mock {@link Call} that wires together all
     * sub-mocks: user, campaign, cdr, result codes.
     *
     * <p>Date fields are returned as {@link Double} to mirror the production model
     * and exercise the {@code Utils.convertDoubleToLongDate} path in mappers.</p>
     *
     * @return configured mock Call
     */
    public static Call buildCall() {
        Call call = mock(Call.class);

        // identity
        when(call.getId()).thenReturn("call-abc-123");
        when(call.getUniqueKey()).thenReturn("ukey-001");
        when(call.getCalledNumber()).thenReturn("05551234567");
        when(call.getChannel()).thenReturn("SIP");
        when(call.getResultCodeSequence()).thenReturn(1);
        when(call.getCallOriginator()).thenReturn(null);
        when(call.getCallType()).thenReturn(Constants.CALL_TYPES.FRESH);
        when(call.getCallerId()).thenReturn("02121234567");
        when(call.getCallbackNumber()).thenReturn("05559876543");
        when(call.getCallbackType()).thenReturn(Constants.CALLBACKTYPE.ASSIGN_TO_USER_GROUP);
        when(call.getRetryNumber()).thenReturn("05551111111");
        when(call.getRetryHistory()).thenReturn(Arrays.asList("05551111111", "05552222222"));
        when(call.getAttachedData()).thenReturn("{\"key\":\"value\"}");
        when(call.getLscResponseValues()).thenReturn("lsc-resp");
        when(call.getCustomerName()).thenReturn("Ahmet");
        when(call.getCustomerSurname()).thenReturn("Yilmaz");
        when(call.getRelationType()).thenReturn(1);
        when(call.getRelation()).thenReturn("SELF");
        when(call.getAttempts()).thenReturn(2);

        // dates (Double — as stored in model)
        when(call.getCallbackDate()).thenReturn(NOW_DOUBLE);
        when(call.getRetryDate()).thenReturn(NOW_DOUBLE);
        when(call.getOriginationDate()).thenReturn(NOW_DOUBLE);
        when(call.getStartDate()).thenReturn(NOW_DOUBLE);
        when(call.getAnswerDate()).thenReturn(NOW_DOUBLE);
        when(call.getCompleteDate()).thenReturn(NOW_DOUBLE);
        when(call.getIsFetched()).thenReturn(NOW_DOUBLE);
        when(call.getCreateDate()).thenReturn(NOW_DATE);

        // durations
        when(call.getDurationFromAnswer()).thenReturn(60_000L);
        when(call.getDurationFromOrigination()).thenReturn(90_000L);

        // flags
        when(call.isAbandoned()).thenReturn(false);
        when(call.isOrphaned()).thenReturn(false);
        when(call.isAnsweredByUser()).thenReturn(true);

        // Build sub-mocks BEFORE passing them into when() — Mockito 3.x does not
        // allow nested mock()/when() calls inside a thenReturn() argument.
        User builtUser                = buildUser();
        Campaign builtCampaign        = buildCampaign();
        CDR builtCdr                  = buildCdr();
        ResultCode builtUserRc        = buildUserResultCode();
        TelephonyResultCode builtTeleRc = buildTelephonyResultCode();

        when(call.getUser()).thenReturn(builtUser);
        when(call.getCampaign()).thenReturn(builtCampaign);
        when(call.getCdr()).thenReturn(builtCdr);
        when(call.getUserResultCode()).thenReturn(builtUserRc);
        when(call.getTelephonyResultCode()).thenReturn(builtTeleRc);
        when(call.getDNCNumbers()).thenReturn(new HashSet<>(Arrays.asList("05550000001")));
        when(call.getAppliedSegmentationFilter()).thenReturn(null);

        return call;
    }
}