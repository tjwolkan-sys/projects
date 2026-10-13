package com.powerdialer.redis.event.interactionnotifier.mapper;

import com.powerdialer.model.CDR;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.Server;
import com.powerdialer.model.Tenant;
import com.powerdialer.model.TelephonyResultCode;
import com.powerdialer.model.User;
import com.powerdialer.redis.event.interactionnotifier.MockFactory;
import com.powerdialer.redis.event.interactionnotifier.dto.CdrInDTO;
import com.powerdialer.util.Constants;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link CdrInMapper}.
 *
 * <p>Coverage targets:
 * <ul>
 *   <li>Null CDR — returns null</li>
 *   <li>Full happy-path — all three field groups</li>
 *   <li>Null tenant / campaign / server / telephonyRC / assignedUser / connectedUser — id becomes null</li>
 *   <li>All date fields mapped from CDR</li>
 *   <li>Enum field (cdrOutdialingType) forwarded</li>
 * </ul>
 * </p>
 */
public class CdrInMapperTest {

    private CDR cdr;

    @Before
    public void setUp() {
        cdr = MockFactory.buildCdr();
    }

    // -----------------------------------------------------------------------
    // Null guard
    // -----------------------------------------------------------------------

    /** Null input must return null without throwing. */
    @Test
    public void toDto_nullCdr_returnsNull() {
        assertNull(CdrInMapper.toDto(null));
    }

    // -----------------------------------------------------------------------
    // Identity fields
    // -----------------------------------------------------------------------

    /** All identity fields must be mapped 1-to-1. */
    @Test
    public void toDto_fullCdr_mapsIdentityFields() {
        CdrInDTO dto = CdrInMapper.toDto(cdr);

        assertEquals(Long.valueOf(77L),   dto.getId());
        assertEquals("parent-001",        dto.getParent_id());
        assertEquals("call-001",          dto.getCall_Id());
        assertEquals("internal-001",      dto.getInternalCallId());
        assertEquals("5551234567",        dto.getAni());
        assertEquals("PHONE1",            dto.getContactNumberColumn());
        assertEquals(Integer.valueOf(1),  dto.getContactNumberOrder());
        assertEquals(Integer.valueOf(2),  dto.getRcSequence());
        assertEquals("skill1,skill2",     dto.getSkills());
        assertEquals("ivr-01",            dto.getConnectedIvrNumber());
        assertEquals(Constants.CDR_OUTDIALING_TYPE.NEW, dto.getCdrOutdialingType());
        assertEquals(Long.valueOf(1L),    dto.getCampaignType());
        assertEquals(Long.valueOf(500L),  dto.getContact_id());
        assertEquals(Long.valueOf(10L),   dto.getGroup_id());
    }

    // -----------------------------------------------------------------------
    // Date fields
    // -----------------------------------------------------------------------

    /** All date fields must be non-null when CDR returns non-null Dates. */
    @Test
    public void toDto_fullCdr_mapsAllDates() {
        CdrInDTO dto = CdrInMapper.toDto(cdr);
        assertNotNull(dto.getCallStartTime());
        assertNotNull(dto.getStart_date());
        assertNotNull(dto.getCallAnswerTime());
        assertNotNull(dto.getCallHandlingStartTime());
        assertNotNull(dto.getEnd_date());
    }

    /** Null callStartTime must produce null without NPE. */
    @Test
    public void toDto_nullCallStartTime_dateIsNull() {
        when(cdr.getCallStartTime()).thenReturn(null);
        assertNull(CdrInMapper.toDto(cdr).getCallStartTime());
    }

    // -----------------------------------------------------------------------
    // Relation fields — null branches
    // -----------------------------------------------------------------------

    /** Non-null tenant must produce correct tenantId. */
    @Test
    public void toDto_withTenant_mapsTenantId() {
        assertEquals(Long.valueOf(99L), CdrInMapper.toDto(cdr).getTenant_id());
    }

    /** Null tenant must produce null tenantId without NPE. */
    @Test
    public void toDto_nullTenant_tenantIdIsNull() {
        when(cdr.getTenant()).thenReturn(null);
        assertNull(CdrInMapper.toDto(cdr).getTenant_id());
    }

    /** Non-null campaign must produce correct campaignId. */
    @Test
    public void toDto_withCampaign_mapsCampaignId() {
        assertEquals(Long.valueOf(42L), CdrInMapper.toDto(cdr).getCampaignId());
    }

    /** Null campaign must produce null campaignId without NPE. */
    @Test
    public void toDto_nullCampaign_campaignIdIsNull() {
        when(cdr.getCampaign()).thenReturn(null);
        assertNull(CdrInMapper.toDto(cdr).getCampaignId());
    }

    /** Non-null server must produce correct serverId. */
    @Test
    public void toDto_withServer_mapsServerId() {
        assertEquals(Long.valueOf(3L), CdrInMapper.toDto(cdr).getServerId());
    }

    /** Null server must produce null serverId without NPE. */
    @Test
    public void toDto_nullServer_serverIdIsNull() {
        when(cdr.getServer()).thenReturn(null);
        assertNull(CdrInMapper.toDto(cdr).getServerId());
    }

    /** Non-null telephony RC must produce correct id. */
    @Test
    public void toDto_withTelephonyRc_mapsTelephonyRcId() {
        assertEquals(Long.valueOf(10L), CdrInMapper.toDto(cdr).getTelephony_result_code_id());
    }

    /** Null telephony RC must produce null id without NPE. */
    @Test
    public void toDto_nullTelephonyRc_telephonyRcIdIsNull() {
        when(cdr.getTelephonyRC()).thenReturn(null);
        assertNull(CdrInMapper.toDto(cdr).getTelephony_result_code_id());
    }

    /** Non-null assignedUser must produce correct assignedUserId. */
    @Test
    public void toDto_withAssignedUser_mapsAssignedUserId() {
        assertEquals(Long.valueOf(1L), CdrInMapper.toDto(cdr).getAssignedUserId());
    }

    /** Null assignedUser must produce null assignedUserId without NPE. */
    @Test
    public void toDto_nullAssignedUser_assignedUserIdIsNull() {
        when(cdr.getAssignedUser()).thenReturn(null);
        assertNull(CdrInMapper.toDto(cdr).getAssignedUserId());
    }

    /** Non-null connectedUser must produce correct userId. */
    @Test
    public void toDto_withConnectedUser_mapsConnectedUserId() {
        assertEquals(Long.valueOf(2L), CdrInMapper.toDto(cdr).getUser_id());
    }

    /** Null connectedUser must produce null userId without NPE. */
    @Test
    public void toDto_nullConnectedUser_connectedUserIdIsNull() {
        when(cdr.getConnectedUser()).thenReturn(null);
        assertNull(CdrInMapper.toDto(cdr).getUser_id());
    }

    // -----------------------------------------------------------------------
    // Enum field
    // -----------------------------------------------------------------------

    /** cdrOutdialingType enum must be forwarded as-is. */
    @Test
    public void toDto_cdrOutdialingType_forwarded() {
        assertEquals(Constants.CDR_OUTDIALING_TYPE.NEW,
                CdrInMapper.toDto(cdr).getCdrOutdialingType());
    }

    /** Null cdrOutdialingType must produce null without NPE. */
    @Test
    public void toDto_nullCdrOutdialingType_isNull() {
        when(cdr.getCdrOutdialingType()).thenReturn(null);
        assertNull(CdrInMapper.toDto(cdr).getCdrOutdialingType());
    }
}