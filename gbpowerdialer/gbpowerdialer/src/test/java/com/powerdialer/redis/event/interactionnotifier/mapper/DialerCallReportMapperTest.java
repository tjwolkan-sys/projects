package com.powerdialer.redis.event.interactionnotifier.mapper;

import com.powerdialer.model.Call;
import com.powerdialer.redis.event.interactionnotifier.MockFactory;
import com.powerdialer.redis.event.interactionnotifier.dto.DialerCallReportDTO;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DialerCallReportMapper}.
 *
 * <p>Coverage targets:
 * <ul>
 *   <li>Null call — returns null</li>
 *   <li>Full happy-path — all five top-level sections assembled</li>
 *   <li>Agent section: username / nickname / fullname populated</li>
 *   <li>Agent section: null user — all agent fields null without NPE</li>
 *   <li>Agent section: null adNickname — nickname null</li>
 *   <li>Agent section: null fullname — fullname null</li>
 *   <li>Call section delegates to CallInMapper (non-null, id verified)</li>
 *   <li>PdCdr section delegates to CdrInMapper (non-null, id verified)</li>
 *   <li>Campaign section delegates to CampaignInMapper (non-null, id verified)</li>
 *   <li>Additional section: interaction_type always "OUTBOUND_DIALER"</li>
 *   <li>Null CDR on call — pdCdr is null without NPE</li>
 *   <li>Null campaign on call — campaign section is null without NPE</li>
 * </ul>
 * </p>
 */
public class DialerCallReportMapperTest {

    private Call call;

    @Before
    public void setUp() {
        call = MockFactory.buildCall();
    }

    // -----------------------------------------------------------------------
    // Null guard
    // -----------------------------------------------------------------------

    /** Null call must return null without throwing. */
    @Test
    public void toDto_nullCall_returnsNull() {
        assertNull(DialerCallReportMapper.toDto(null));
    }

    // -----------------------------------------------------------------------
    // Top-level structure
    // -----------------------------------------------------------------------

    /** toDto must return a non-null DTO for a valid Call. */
    @Test
    public void toDto_validCall_returnsDtoNotNull() {
        assertNotNull(DialerCallReportMapper.toDto(call));
    }

    /** All five top-level sections must be non-null for a fully populated Call. */
    @Test
    public void toDto_fullCall_allSectionsPresent() {
        DialerCallReportDTO dto = DialerCallReportMapper.toDto(call);
        assertNotNull(dto.getAgent());
        assertNotNull(dto.getCall());
        assertNotNull(dto.getPdCdr());
        assertNotNull(dto.getCampaign());
        assertNotNull(dto.getAdditional());
    }

    // -----------------------------------------------------------------------
    // Agent section
    // -----------------------------------------------------------------------

    /** Agent username / nickname / fullname must come from the User mock. */
    @Test
    public void toDto_fullCall_agentFieldsPopulated() {
        DialerCallReportDTO.AgentInfo agent = DialerCallReportMapper.toDto(call).getAgent();
        assertEquals("agent01",  agent.getUsername());
        assertEquals("nick01",   agent.getNickname());
        assertEquals("John Doe", agent.getFullname());
    }

    /** Null user must produce an AgentInfo with all null fields — no NPE. */
    @Test
    public void toDto_nullUser_agentFieldsNull() {
        when(call.getUser()).thenReturn(null);
        DialerCallReportDTO.AgentInfo agent = DialerCallReportMapper.toDto(call).getAgent();
        assertNotNull(agent); // AgentInfo is still built
        assertNull(agent.getUsername());
        assertNull(agent.getNickname());
        assertNull(agent.getFullname());
    }

    /** Null adNickname must produce null nickname in AgentInfo. */
    @Test
    public void toDto_nullAdNickname_nicknameIsNull() {
        when(call.getUser().getAdNickname()).thenReturn(null);
        assertNull(DialerCallReportMapper.toDto(call).getAgent().getNickname());
    }

    /** Null fullname must produce null fullname in AgentInfo. */
    @Test
    public void toDto_nullFullname_fullnameIsNull() {
        when(call.getUser().getFullname()).thenReturn(null);
        assertNull(DialerCallReportMapper.toDto(call).getAgent().getFullname());
    }

    // -----------------------------------------------------------------------
    // Call section
    // -----------------------------------------------------------------------

    /** Call section id must match the Call mock's id. */
    @Test
    public void toDto_callSection_idMatches() {
        assertEquals("call-abc-123",
                DialerCallReportMapper.toDto(call).getCall().getId());
    }

    // -----------------------------------------------------------------------
    // PdCdr section
    // -----------------------------------------------------------------------

    /** pdCdr id must match the CDR mock's id. */
    @Test
    public void toDto_pdCdrSection_idMatches() {
        assertEquals(Long.valueOf(77L),
                DialerCallReportMapper.toDto(call).getPdCdr().getId());
    }

    /** Null CDR on call must produce null pdCdr section without NPE. */
    @Test
    public void toDto_nullCdr_pdCdrIsNull() {
        when(call.getCdr()).thenReturn(null);
        assertNull(DialerCallReportMapper.toDto(call).getPdCdr());
    }

    // -----------------------------------------------------------------------
    // Campaign section
    // -----------------------------------------------------------------------

    /** Campaign section id must match the Campaign mock's id. */
    @Test
    public void toDto_campaignSection_idMatches() {
        assertEquals(Long.valueOf(42L),
                DialerCallReportMapper.toDto(call).getCampaign().getCampaign_id());
    }

    /** Null campaign on call must produce null campaign section without NPE. */
    @Test
    public void toDto_nullCampaign_campaignSectionIsNull() {
        when(call.getCampaign()).thenReturn(null);
        assertNull(DialerCallReportMapper.toDto(call).getCampaign());
    }

    // -----------------------------------------------------------------------
    // Additional section
    // -----------------------------------------------------------------------

    /** interaction_type must always be "OUTBOUND_DIALER". */
    @Test
    public void toDto_additional_interactionTypeIsOutboundDialer() {
        assertEquals("OUTBOUND_DIALER",
                DialerCallReportMapper.toDto(call).getAdditional().getInteraction_type());
    }
}