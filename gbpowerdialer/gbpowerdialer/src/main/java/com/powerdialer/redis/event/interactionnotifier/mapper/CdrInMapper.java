package com.powerdialer.redis.event.interactionnotifier.mapper;

import com.powerdialer.model.CDR;
import com.powerdialer.redis.event.interactionnotifier.dto.CdrInDTO;

public final class CdrInMapper {

    private CdrInMapper() {}

    public static CdrInDTO toDto(CDR cdr) {
        if (cdr == null) return null;

        CdrInDTO.CdrInDTOBuilder builder = CdrInDTO.builder();

        mapIdentityFields(builder, cdr);
        mapDateFields(builder, cdr);
        mapRelationFields(builder, cdr);

        return builder.build();
    }

    private static void mapIdentityFields(CdrInDTO.CdrInDTOBuilder builder, CDR cdr) {
        builder.id(cdr.getId())
                .parent_id(cdr.getInteractionId())
                .call_Id(cdr.getOutgoingCallId())
                .internalCallId(cdr.getInternalCallId())
                .ani(cdr.getContactNumber())
                .contactNumberColumn(cdr.getContactNumberColumn())
                .contactNumberOrder(cdr.getContactNumberOrder())
                .rcSequence(cdr.getRcSequence())
                .skills(cdr.getSkills())
                .connectedIvrNumber(cdr.getConnectedIvrNumber())
                .cdrOutdialingType(cdr.getCdrOutdialingType())
                .campaignType(cdr.getCampaignType())
                .contact_id(cdr.getContactId())
                .group_id(cdr.getGroupId());
    }

    private static void mapDateFields(CdrInDTO.CdrInDTOBuilder builder, CDR cdr) {
        builder.callStartTime(cdr.getCallStartTime())
                .start_date(cdr.getCallDialTime())
                .callAnswerTime(cdr.getCallAnswerTime())
                .callHandlingStartTime(cdr.getCallHandlingStartTime())
                .end_date(cdr.getCallEndTime());
    }

    private static void mapRelationFields(CdrInDTO.CdrInDTOBuilder builder, CDR cdr) {
        builder.tenant_id(cdr.getTenant() != null ? cdr.getTenant().getId() : null)
                .campaignId(cdr.getCampaign() != null ? cdr.getCampaign().getId() : null)
                .serverId(cdr.getServer() != null ? cdr.getServer().getId() : null)
                .telephony_result_code_id(cdr.getTelephonyRC() != null ? cdr.getTelephonyRC().getId() : null)
                .assignedUserId(cdr.getAssignedUser() != null ? cdr.getAssignedUser().getId() : null)
                .user_id(cdr.getConnectedUser() != null ? cdr.getConnectedUser().getId() : null);
    }
}