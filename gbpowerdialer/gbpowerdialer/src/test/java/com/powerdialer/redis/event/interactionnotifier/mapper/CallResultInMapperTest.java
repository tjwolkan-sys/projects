package com.powerdialer.redis.event.interactionnotifier.mapper;

import com.powerdialer.model.Call;
import com.powerdialer.redis.event.interactionnotifier.MockFactory;
import com.powerdialer.redis.event.interactionnotifier.dto.CallResultInDTO;
import com.powerdialer.util.Constants;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link CallResultInMapper}.
 *
 * <p>Coverage targets:
 * <ul>
 *   <li>Null call — returns null</li>
 *   <li>Full happy-path — all field groups</li>
 *   <li>Null telephonyResultCode / userResultCode branches</li>
 *   <li>Null user — userId not set</li>
 *   <li>toDate() with Double input (valid)</li>
 *   <li>toDate() with null input — field becomes null</li>
 *   <li>toDate() with non-Double (e.g. Long) — field becomes null (current impl)</li>
 *   <li>callType / callbackType null branches</li>
 *   <li>Flag fields: abandoned, orphaned, answeredByUser</li>
 *   <li>Duration fields forwarded as-is</li>
 *   <li>Customer fields forwarded</li>
 *   <li>DNC numbers forwarded</li>
 * </ul>
 * </p>
 */
public class CallResultInMapperTest {

    private Call call;

    @Before
    public void setUp() {
        call = MockFactory.buildCall();
    }

    // -----------------------------------------------------------------------
    // Null guard
    // -----------------------------------------------------------------------

    /** Null input must return null without throwing. */
    @Test
    public void toDto_nullCall_returnsNull() {
        assertNull(CallResultInMapper.toDto(null));
    }

    // -----------------------------------------------------------------------
    // Core fields
    // -----------------------------------------------------------------------

    /** Core identity and date fields must be mapped from Call. */
    @Test
    public void toDto_fullCall_mapsCoreFields() {
        CallResultInDTO dto = CallResultInMapper.toDto(call);
        assertEquals("ukey-001",      dto.getUniqueKey());
        assertEquals("05551234567",   dto.getCalledNumber());
        // dates come from Double stubs — not null after conversion
        assertNotNull(dto.getStartDate());
        assertNotNull(dto.getAnswerDate());
        assertNotNull(dto.getCompleteDate());
    }

    // -----------------------------------------------------------------------
    // Result code fields
    // -----------------------------------------------------------------------

    /** Both telephony and user result codes must be fully mapped. */
    @Test
    public void toDto_withResultCodes_mapsAllRcFields() {
        CallResultInDTO dto = CallResultInMapper.toDto(call);
        assertEquals(Long.valueOf(10L),          dto.getTelephonyResultCodeId());
        assertEquals("ANSWER",                   dto.getTelephonyResultCodeName());
        assertEquals(Long.valueOf(3L),            dto.getUserResultCodeId());
        assertEquals("YENI RANDEVU VERILDI",     dto.getUserResultCodeName());
        assertEquals(Integer.valueOf(1),          dto.getResultCodeSequence());
        assertEquals(Long.valueOf(1L),            dto.getUserId());
    }

    /** Null telephony RC must leave telephony fields unset (null) without NPE. */
    @Test
    public void toDto_nullTelephonyRc_telephonyFieldsNull() {
        when(call.getTelephonyResultCode()).thenReturn(null);
        CallResultInDTO dto = CallResultInMapper.toDto(call);
        assertNull(dto.getTelephonyResultCodeId());
        assertNull(dto.getTelephonyResultCodeName());
    }

    /** Null user RC must leave user rc fields unset without NPE. */
    @Test
    public void toDto_nullUserRc_userRcFieldsNull() {
        when(call.getUserResultCode()).thenReturn(null);
        CallResultInDTO dto = CallResultInMapper.toDto(call);
        assertNull(dto.getUserResultCodeId());
        assertNull(dto.getUserResultCodeName());
    }

    /** Null user must leave userId unset without NPE. */
    @Test
    public void toDto_nullUser_userIdNull() {
        when(call.getUser()).thenReturn(null);
        assertNull(CallResultInMapper.toDto(call).getUserId());
    }

    // -----------------------------------------------------------------------
    // Callback / retry fields
    // -----------------------------------------------------------------------

    /** Callback fields must be forwarded from Call. */
    @Test
    public void toDto_callbackFields_mapped() {
        CallResultInDTO dto = CallResultInMapper.toDto(call);
        assertEquals("05559876543", dto.getCallbackNumber());
        assertNotNull(dto.getCallbackDate()); // Double → Date conversion
    }

    /** Retry fields (number, history) must be forwarded from Call. */
    @Test
    public void toDto_retryFields_mapped() {
        CallResultInDTO dto = CallResultInMapper.toDto(call);
        assertEquals("05551111111", dto.getRetryNumber());
        assertEquals(2,              dto.getRetryHistory().size());
        assertNotNull(dto.getRetryDate());
    }

    // -----------------------------------------------------------------------
    // toDate — private method exercised through field assertions
    // -----------------------------------------------------------------------

    /** When a date field is null (Double null), the DTO date must be null. */
    @Test
    public void toDto_nullDateDouble_producesNullDate() {
        when(call.getStartDate()).thenReturn(null);
        assertNull(CallResultInMapper.toDto(call).getStartDate());
    }

    // -----------------------------------------------------------------------
    // Meta / flag fields
    // -----------------------------------------------------------------------

    /** callType enum name must be set as string. */
    @Test
    public void toDto_callType_mappedAsString() {
        assertEquals(Constants.CALL_TYPES.FRESH.name(),
                CallResultInMapper.toDto(call).getCallType());
    }

    /** callbackType enum name must be set as string. */
    @Test
    public void toDto_callbackType_mappedAsString() {
        assertEquals(Constants.CALLBACKTYPE.ASSIGN_TO_USER_GROUP.name(),
                CallResultInMapper.toDto(call).getCallbackType());
    }

    /** Null callType must NOT set the callType field (no NPE). */
    @Test
    public void toDto_nullCallType_callTypeNotSet() {
        when(call.getCallType()).thenReturn(null);
        // null branch skips builder call — field stays null
        assertNull(CallResultInMapper.toDto(call).getCallType());
    }

    /** Null callbackType must NOT set the callbackType field (no NPE). */
    @Test
    public void toDto_nullCallbackType_callbackTypeNotSet() {
        when(call.getCallbackType()).thenReturn(null);
        assertNull(CallResultInMapper.toDto(call).getCallbackType());
    }

    /** abandoned flag must be forwarded. */
    @Test
    public void toDto_abandonedFalse_isFalse() {
        assertFalse(CallResultInMapper.toDto(call).getAbandoned());
    }

    /** When abandoned is true, dto.abandoned must be true. */
    @Test
    public void toDto_abandonedTrue_isTrue() {
        when(call.isAbandoned()).thenReturn(true);
        assertTrue(CallResultInMapper.toDto(call).getAbandoned());
    }

    /** orphaned flag must be forwarded. */
    @Test
    public void toDto_orphanedFalse_isFalse() {
        assertFalse(CallResultInMapper.toDto(call).getOrphaned());
    }

    /** answeredByUser flag must be forwarded. */
    @Test
    public void toDto_answeredByUserTrue_isTrue() {
        assertTrue(CallResultInMapper.toDto(call).getAnsweredByUser());
    }

    // -----------------------------------------------------------------------
    // Duration fields
    // -----------------------------------------------------------------------

    /** durationFromAnswer must equal the value from Call. */
    @Test
    public void toDto_durationFromAnswer_mapped() {
        assertEquals(Long.valueOf(60_000L),
                CallResultInMapper.toDto(call).getDurationFromAnswer());
    }

    /** durationFromOrigination must equal the value from Call. */
    @Test
    public void toDto_durationFromOrigination_mapped() {
        assertEquals(Long.valueOf(90_000L),
                CallResultInMapper.toDto(call).getDurationFromOrigination());
    }

    // -----------------------------------------------------------------------
    // Customer fields
    // -----------------------------------------------------------------------

    /** Customer name/surname and relation fields must be forwarded. */
    @Test
    public void toDto_customerFields_mapped() {
        CallResultInDTO dto = CallResultInMapper.toDto(call);
        assertEquals("Ahmet",  dto.getCustomerName());
        assertEquals("Yilmaz", dto.getCustomerSurname());
        assertEquals(Integer.valueOf(1), dto.getRelationType());
        assertEquals("SELF",   dto.getRelation());
    }

    // -----------------------------------------------------------------------
    // Flag / misc
    // -----------------------------------------------------------------------

    /** DNC numbers must be forwarded. */
    @Test
    public void toDto_dncNumbers_forwarded() {
        CallResultInDTO dto = CallResultInMapper.toDto(call);
        assertNotNull(dto.getDncNumbers());
        assertTrue(dto.getDncNumbers().contains("05550000001"));
    }

    /** attempts must be forwarded. */
    @Test
    public void toDto_attempts_mapped() {
        assertEquals(Integer.valueOf(2), CallResultInMapper.toDto(call).getAttempts());
    }

    /** lscResponseValues must be forwarded. */
    @Test
    public void toDto_lscResponseValues_mapped() {
        assertEquals("lsc-resp", CallResultInMapper.toDto(call).getLscResponseValues());
    }

    /** fetchedDataFlag must not be null when isFetched is a valid Double. */
    @Test
    public void toDto_isFetched_producesNonNullDate() {
        assertNotNull(CallResultInMapper.toDto(call).getFetchedDataFlag());
    }
}