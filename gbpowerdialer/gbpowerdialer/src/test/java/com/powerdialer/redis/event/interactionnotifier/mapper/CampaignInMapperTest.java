package com.powerdialer.redis.event.interactionnotifier.mapper;

import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.redis.event.interactionnotifier.MockFactory;
import com.powerdialer.redis.event.interactionnotifier.dto.CampaignInDTO;
import com.powerdialer.util.Constants;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link CampaignInMapper}.
 *
 * <p>Coverage targets:
 * <ul>
 *   <li>Null campaign — returns null</li>
 *   <li>Full happy-path across all field groups</li>
 *   <li>Null tenant — tenantId null</li>
 *   <li>Null ivrApplication — ivrApplicationId null</li>
 *   <li>Null callerIdPool — callerIdPoolId null</li>
 *   <li>Null dnc — dncId null</li>
 *   <li>callResult section populated via CallResultInMapper</li>
 *   <li>Null call — callResult null without NPE</li>
 *   <li>All optional stop-condition fields (null-safe)</li>
 * </ul>
 * </p>
 */
public class CampaignInMapperTest {

    private Campaign campaign;
    private Call call;

    @Before
    public void setUp() {
        campaign = MockFactory.buildCampaign();
        call = MockFactory.buildCall();
    }

    // -----------------------------------------------------------------------
    // Null guard
    // -----------------------------------------------------------------------

    /** Null campaign must return null (call is irrelevant). */
    @Test
    public void toDto_nullCampaign_returnsNull() {
        assertNull(CampaignInMapper.toDto(null, call));
    }

    // -----------------------------------------------------------------------
    // Basic fields
    // -----------------------------------------------------------------------

    /** All basic / identity fields must be mapped correctly. */
    @Test
    public void toDto_fullCampaign_mapsBasicFields() {
        CampaignInDTO dto = CampaignInMapper.toDto(campaign, call);

        assertEquals(Long.valueOf(42L),              dto.getCampaign_id());
        assertEquals(Long.valueOf(99L),              dto.getTenantId());
        assertEquals("TestCampaign",                 dto.getCampaign_name());
        assertEquals("desc",                         dto.getDescription());
        assertEquals(Constants.CAMPAIGN_TYPE.PREDICTIVE, dto.getType());
        assertTrue(dto.getEnabled());
        assertFalse(dto.getDeleted());
        assertEquals("ctx",                          dto.getContext());
        assertEquals("abandon_ivr",                  dto.getAbandonIvr());
        assertEquals(Long.valueOf(5L),               dto.getIvrApplicationId());
        assertEquals(Integer.valueOf(1),             dto.getVersion());
    }

    // -----------------------------------------------------------------------
    // Date fields
    // -----------------------------------------------------------------------

    /** startDate, endDate, createdDate, updatedDate must be forwarded. */
    @Test
    public void toDto_fullCampaign_mapsDateFields() {
        CampaignInDTO dto = CampaignInMapper.toDto(campaign, call);
        assertNotNull(dto.getStartDate());
        assertNotNull(dto.getEndDate());
        assertNull(dto.getStoppedDate());
        assertNotNull(dto.getCreatedDate());
        assertNotNull(dto.getUpdatedDate());
    }

    // -----------------------------------------------------------------------
    // Preview fields
    // -----------------------------------------------------------------------

    /** Preview-related fields must be forwarded. */
    @Test
    public void toDto_fullCampaign_mapsPreviewFields() {
        CampaignInDTO dto = CampaignInMapper.toDto(campaign, call);
        assertEquals(Integer.valueOf(30), dto.getPreviewTimeout());
        assertEquals(Integer.valueOf(5),  dto.getPreviewRelax());
        assertEquals(Integer.valueOf(2),  dto.getAvailableThreshold());
        assertTrue(dto.getAllowCallback());
        assertFalse(dto.getAllowPreviewBeep());
        assertEquals(Constants.END_LIST_BEHAVIOUR.STOP_DISCARDING_RETRIES_AND_CALLBACKS, dto.getEndListBehaviour());
    }

    // -----------------------------------------------------------------------
    // Retry fields
    // -----------------------------------------------------------------------

    /** Retry-related fields must be forwarded. */
    @Test
    public void toDto_fullCampaign_mapsRetryFields() {
        CampaignInDTO dto = CampaignInMapper.toDto(campaign, call);
        assertEquals(Constants.RETRY_STRATEGY.REACH_RETRY_LIMIT_FOR_SINGLE_NUMBER, dto.getRetryStrategy());
        assertFalse(dto.getRetryScheduleEnabled());
        assertEquals(Integer.valueOf(3),   dto.getRetryOveralAttempt());
        assertEquals(Integer.valueOf(60),  dto.getMacroInterval());
        assertEquals(Integer.valueOf(5),   dto.getMacroMaxAttempt());
        // Task 325559: additive alan; mevcut alanlar korunur.
        assertEquals(Integer.valueOf(10),  dto.getMaxAttemptPerNumber());
        assertEquals(Double.valueOf(0.5),  dto.getRetryDelay());
        assertEquals(Constants.RETRY_CALLER_ID_BEHAVIOR.SAME, dto.getRetryCallerIdBehavior());
    }

    // -----------------------------------------------------------------------
    // Caller ID fields
    // -----------------------------------------------------------------------

    /** Caller ID fields must be forwarded. */
    @Test
    public void toDto_fullCampaign_mapsCallerIdFields() {
        CampaignInDTO dto = CampaignInMapper.toDto(campaign, call);
        assertEquals(Constants.CALLER_ID_BEHAVIOUR.FIXED, dto.getCallerIdBehaviour());
        assertEquals("02121234567", dto.getCallerId());
        assertNull(dto.getCallerIdPoolId()); // callerIdPool is null in mock
        assertEquals(Constants.TIMEZONE_TYPE.OFF, dto.getTimezoneType());
        assertEquals(Constants.APPLY_TO.RETRIES, dto.getApplyFilterTo());
        assertEquals(Constants.FILTER_JOIN_TYPE.AND, dto.getFilterJoinType());
    }

    // -----------------------------------------------------------------------
    // Dialing fields
    // -----------------------------------------------------------------------

    /** Dialing configuration fields must be forwarded. */
    @Test
    public void toDto_fullCampaign_mapsDialingFields() {
        CampaignInDTO dto = CampaignInMapper.toDto(campaign, call);
        assertEquals(Integer.valueOf(10),    dto.getConcurrentLines());
        assertEquals(Integer.valueOf(5),     dto.getCallsPerSecond());
        assertEquals(Integer.valueOf(30),    dto.getRingTimeout());
        assertEquals(Float.valueOf(1.2f),    dto.getDialingBoost());
        assertFalse(dto.getAmdEnabled());
        assertEquals(Constants.PREDICTIVE_STRATEGY.GAMMA, dto.getPredictiveStrategy());
        assertEquals("default",              dto.getMohClassCallingParty());
        assertTrue(dto.getStartupDbCheck());
    }

    // -----------------------------------------------------------------------
    // Schedule fields
    // -----------------------------------------------------------------------

    /** Schedule flags must be forwarded. */
    @Test
    public void toDto_fullCampaign_mapsScheduleFields() {
        CampaignInDTO dto = CampaignInMapper.toDto(campaign, call);
        assertTrue(dto.getScheduleEnabled());
        assertFalse(dto.getScheduleAutomationEnabled());
    }

    // -----------------------------------------------------------------------
    // DNC / LSC fields
    // -----------------------------------------------------------------------

    /** DNC and LSC fields must be forwarded; dncId null when dnc entity is null. */
    @Test
    public void toDto_fullCampaign_mapsDncLscFields() {
        CampaignInDTO dto = CampaignInMapper.toDto(campaign, call);
        assertFalse(dto.getDncEnabled());
        assertNull(dto.getDncId());
        assertFalse(dto.getLscEnabled());
        assertEquals(Constants.LSC_TIMEOUT_BEHAVIOR.DIAL, dto.getLscTimeoutBehavior());
        assertFalse(dto.getFullContactListCheckEnabled());
        assertFalse(dto.getSkillEnabled());
    }

    // -----------------------------------------------------------------------
    // Stop condition fields
    // -----------------------------------------------------------------------

    /** Null stop condition fields must be null in DTO. */
    @Test
    public void toDto_nullStopConditions_fieldsNull() {
        CampaignInDTO dto = CampaignInMapper.toDto(campaign, call);
        assertFalse(dto.getStopConditionEnabled());
        assertNull(dto.getStopConditionParameter());
        assertNull(dto.getStopConditionValue());
        assertNull(dto.getStopConditionThreshold());
        assertNull(dto.getStopType());
        assertNull(dto.getStopTypeHistory());
    }

    // -----------------------------------------------------------------------
    // API fields
    // -----------------------------------------------------------------------

    /** API control fields must be forwarded. */
    @Test
    public void toDto_fullCampaign_mapsApiFields() {
        CampaignInDTO dto = CampaignInMapper.toDto(campaign, call);
        assertFalse(dto.getAllowApiControl());
        assertEquals("token123", dto.getApiToken());
    }

    // -----------------------------------------------------------------------
    // callResult section (delegates to CallResultInMapper)
    // -----------------------------------------------------------------------

    /** callResult must be non-null when a valid Call is provided. */
    @Test
    public void toDto_withCall_callResultPopulated() {
        CampaignInDTO dto = CampaignInMapper.toDto(campaign, call);
        assertNotNull(dto.getCallResult());
        assertEquals("ukey-001", dto.getCallResult().getUniqueKey());
    }

    /** callResult must be null when Call is null (CallResultInMapper returns null). */
    @Test
    public void toDto_nullCall_callResultIsNull() {
        CampaignInDTO dto = CampaignInMapper.toDto(campaign, null);
        assertNull(dto.getCallResult());
    }

    // -----------------------------------------------------------------------
    // Null nested entity branches
    // -----------------------------------------------------------------------

    /** Null tenant must produce null tenantId without NPE. */
    @Test
    public void toDto_nullTenant_tenantIdNull() {
        when(campaign.getTenant()).thenReturn(null);
        assertNull(CampaignInMapper.toDto(campaign, call).getTenantId());
    }

    /** Null ivrApplication must produce null ivrApplicationId without NPE. */
    @Test
    public void toDto_nullIvrApplication_ivrApplicationIdNull() {
        when(campaign.getIvrApplication()).thenReturn(null);
        assertNull(CampaignInMapper.toDto(campaign, call).getIvrApplicationId());
    }
}