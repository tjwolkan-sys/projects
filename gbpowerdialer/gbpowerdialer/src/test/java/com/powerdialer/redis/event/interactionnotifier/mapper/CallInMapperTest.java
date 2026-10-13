package com.powerdialer.redis.event.interactionnotifier.mapper;

import com.powerdialer.model.Call;
import com.powerdialer.redis.event.interactionnotifier.MockFactory;
import com.powerdialer.redis.event.interactionnotifier.dto.CallInDTO;
import com.powerdialer.util.Constants;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link CallInMapper}.
 *
 * <p>Coverage targets:
 * <ul>
 *   <li>Null call input</li>
 *   <li>Full happy-path mapping of every field group</li>
 *   <li>DNIS10 stripping of leading "0"</li>
 *   <li>DNIS10 when number does NOT start with "0"</li>
 *   <li>DNIS null guard</li>
 *   <li>Null callOriginator (no NPE)</li>
 *   <li>Null user / campaign / cdr / filter (ID fields become null)</li>
 *   <li>Null user result code — fallback to ARANMADI (id=1)</li>
 *   <li>Null telephony result code — name becomes null</li>
 *   <li>DNC numbers forwarded as-is</li>
 *   <li>Duration fields (call_duration / ring_duration)</li>
 *   <li>Boolean flags (abandoned, orphaned, answeredByUser)</li>
 * </ul>
 * </p>
 */
public class CallInMapperTest {

    private Call call;

    @Before
    public void setUp() {
        // Fully populated mock call — individual tests override specific stubs
        call = MockFactory.buildCall();
    }

    // -----------------------------------------------------------------------
    // Null guard
    // -----------------------------------------------------------------------

    /** Null input must return null without throwing. */
    @Test
    public void toDto_nullCall_returnsNull() {
        assertNull(CallInMapper.toDto(null));
    }

    // -----------------------------------------------------------------------
    // Identity fields
    // -----------------------------------------------------------------------

    /** All identity fields must be mapped 1-to-1. */
    @Test
    public void toDto_fullCall_mapsIdentityFields() {
        CallInDTO dto = CallInMapper.toDto(call);

        assertEquals("call-abc-123",     dto.getId());
        assertEquals("ukey-001",         dto.getUniqueKey());
        assertEquals("05551234567",      dto.getDnis());
        assertEquals("SIP",             dto.getChannel());
        assertEquals(Integer.valueOf(1), dto.getResultCodeSequence());
        assertEquals(Constants.CALL_TYPES.FRESH, dto.getCallType());
        assertEquals("02121234567",      dto.getCallerId());
        assertEquals("05559876543",      dto.getCallbackNumber());
        assertEquals(Constants.CALLBACKTYPE.ASSIGN_TO_USER_GROUP, dto.getCallbackType());
        assertEquals("05551111111",      dto.getRetryNumber());
        assertEquals("{\"key\":\"value\"}", dto.getAttachedData());
        assertEquals("lsc-resp",         dto.getLscResponseValues());
        assertEquals("Ahmet",            dto.getCustomerName());
        assertEquals("Yilmaz",           dto.getCustomerSurname());
        assertEquals(Integer.valueOf(1), dto.getRelationType());
        assertEquals("SELF",             dto.getRelation());
        assertEquals(Integer.valueOf(2), dto.getAttempts());
    }

    // -----------------------------------------------------------------------
    // DNIS10 stripping
    // -----------------------------------------------------------------------

    /** Called number starting with "0" must have the leading zero removed for dnis10. */
    @Test
    public void toDto_calledNumberStartsWithZero_stripsLeadingZeroForDnis10() {
        when(call.getCalledNumber()).thenReturn("05551234567");
        CallInDTO dto = CallInMapper.toDto(call);
        assertEquals("5551234567", dto.getDnis10());
    }

    /** Called number NOT starting with "0" must remain unchanged in dnis10. */
    @Test
    public void toDto_calledNumberNoLeadingZero_dnis10Unchanged() {
        when(call.getCalledNumber()).thenReturn("5551234567");
        CallInDTO dto = CallInMapper.toDto(call);
        assertEquals("5551234567", dto.getDnis10());
    }

    /** Null called number must produce null dnis and null dnis10 without NPE. */
    @Test
    public void toDto_nullCalledNumber_dnisBothNull() {
        when(call.getCalledNumber()).thenReturn(null);
        CallInDTO dto = CallInMapper.toDto(call);
        assertNull(dto.getDnis());
        assertNull(dto.getDnis10());
    }

    // -----------------------------------------------------------------------
    // Date fields
    // -----------------------------------------------------------------------

    /** All date fields must be non-null when Double values are provided. */
    @Test
    public void toDto_validDoubleDates_allDatesNonNull() {
        CallInDTO dto = CallInMapper.toDto(call);
        assertNotNull(dto.getCallbackDate());
        assertNotNull(dto.getRetryDate());
        assertNotNull(dto.getOriginationDate());
        assertNotNull(dto.getStartDate());
        assertNotNull(dto.getAnswerDate());
        assertNotNull(dto.getCompleteDate());
        assertNotNull(dto.getIsFetched());
        assertNotNull(dto.getCreateDate());
    }

    // -----------------------------------------------------------------------
    // Relation fields — IDs extracted via reflection
    // -----------------------------------------------------------------------

    /** User id must be extracted from the User mock. */
    @Test
    public void toDto_withUser_mapsUserId() {
        assertEquals(Long.valueOf(1L), CallInMapper.toDto(call).getUserId());
    }

    /** Campaign id must be extracted from the Campaign mock. */
    @Test
    public void toDto_withCampaign_mapsCampaignId() {
        assertEquals(Long.valueOf(42L), CallInMapper.toDto(call).getCampaignId());
    }

    /** CDR id must be extracted from the CDR mock. */
    @Test
    public void toDto_withCdr_mapsCdrId() {
        assertEquals(Long.valueOf(77L), CallInMapper.toDto(call).getCdrId());
    }

    /** Null user must produce null userId without NPE. */
    @Test
    public void toDto_nullUser_userIdIsNull() {
        when(call.getUser()).thenReturn(null);
        assertNull(CallInMapper.toDto(call).getUserId());
    }

    /** Null campaign must produce null campaignId without NPE. */
    @Test
    public void toDto_nullCampaign_campaignIdIsNull() {
        when(call.getCampaign()).thenReturn(null);
        assertNull(CallInMapper.toDto(call).getCampaignId());
    }

    /** Null CDR must produce null cdrId without NPE. */
    @Test
    public void toDto_nullCdr_cdrIdIsNull() {
        when(call.getCdr()).thenReturn(null);
        assertNull(CallInMapper.toDto(call).getCdrId());
    }

    /** Null segmentation filter must produce null filterId without NPE. */
    @Test
    public void toDto_nullFilter_filterIdIsNull() {
        when(call.getAppliedSegmentationFilter()).thenReturn(null);
        assertNull(CallInMapper.toDto(call).getAppliedSegmentationFilterId());
    }

    // -----------------------------------------------------------------------
    // Result code fields
    // -----------------------------------------------------------------------

    /** User RC name and mapped id must be set; telephony RC name must come from mock. */
    @Test
    public void toDto_withResultCodes_mapsResultCodeFields() {
        CallInDTO dto = CallInMapper.toDto(call);
        assertEquals("YENI RANDEVU VERILDI", dto.getResult_code_name());
        assertEquals(Long.valueOf(3L),        dto.getCall_result_code_id()); // enum id for that text
        assertEquals(Long.valueOf(3L),        dto.getUser_result_code_id());
        assertEquals("ANSWER",               dto.getTelephony_result_code_name());
    }

    /** Null user RC must set result_code_name to null and call_result_code_id to ARANMADI (1). */
    @Test
    public void toDto_nullUserResultCode_fallsBackToAranmadi() {
        when(call.getUserResultCode()).thenReturn(null);
        CallInDTO dto = CallInMapper.toDto(call);
        assertNull(dto.getResult_code_name());
        assertEquals(Long.valueOf(1L), dto.getCall_result_code_id());
        assertNull(dto.getUser_result_code_id());
    }

    /** Null telephony RC must set telephony_result_code_name to null without NPE. */
    @Test
    public void toDto_nullTelephonyResultCode_nameIsNull() {
        when(call.getTelephonyResultCode()).thenReturn(null);
        assertNull(CallInMapper.toDto(call).getTelephony_result_code_name());
    }

    // -----------------------------------------------------------------------
    // Flag / duration fields
    // -----------------------------------------------------------------------

    /** duration fields are mapped in seconds for interaction notifier payload. */
    @Test
    public void toDto_durations_mappedCorrectly() {
        CallInDTO dto = CallInMapper.toDto(call);
        // call_duration = durationFromAnswer / 1000 (seconds)
        assertEquals(60L, dto.getCall_duration());
        // ring_duration = durationFromOrigination / 1000 (seconds)
        assertEquals(90L, dto.getRing_duration());
    }

    /** abandoned_call flag must be forwarded as-is. */
    @Test
    public void toDto_abandonedFalse_flagFalse() {
        assertFalse(CallInMapper.toDto(call).isAbandoned_call());
    }

    /** When abandoned is true the flag must be true. */
    @Test
    public void toDto_abandonedTrue_flagTrue() {
        when(call.isAbandoned()).thenReturn(true);
        assertTrue(CallInMapper.toDto(call).isAbandoned_call());
    }

    /** orphaned flag must be forwarded as-is. */
    @Test
    public void toDto_orphanedFalse_flagFalse() {
        assertFalse(CallInMapper.toDto(call).isOrphaned());
    }

    /** answeredByUser flag must be forwarded as-is. */
    @Test
    public void toDto_answeredByUserTrue_flagTrue() {
        assertTrue(CallInMapper.toDto(call).isAnsweredByUser());
    }

    // -----------------------------------------------------------------------
    // DNC numbers
    // -----------------------------------------------------------------------

    /** DNC number set must be forwarded unchanged. */
    @Test
    public void toDto_dncNumbers_forwarded() {
        CallInDTO dto = CallInMapper.toDto(call);
        assertNotNull(dto.getDncNumbers());
        assertTrue(dto.getDncNumbers().contains("05550000001"));
    }

    /** Null DNC set must produce null in DTO without NPE. */
    @Test
    public void toDto_nullDncNumbers_nullInDto() {
        when(call.getDNCNumbers()).thenReturn(null);
        assertNull(CallInMapper.toDto(call).getDncNumbers());
    }

    // -----------------------------------------------------------------------
    // callOriginatorType
    // -----------------------------------------------------------------------

    /** Null callOriginator must produce null callOriginatorType without NPE. */
    @Test
    public void toDto_nullCallOriginator_originatorTypeNull() {
        when(call.getCallOriginator()).thenReturn(null);
        assertNull(CallInMapper.toDto(call).getCallOriginatorType());
    }
}
