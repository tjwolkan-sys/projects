package com.powerdialer.redis.event.interactionnotifier.mapper;

import com.powerdialer.model.Call;
import com.powerdialer.model.ResultCode;
import com.powerdialer.model.TelephonyResultCode;
import com.powerdialer.redis.event.interactionnotifier.dto.CallInDTO;
import com.powerdialer.redis.event.interactionnotifier.dto.CallResultCodeEnum;
import com.powerdialer.util.Utils;

import java.lang.reflect.Method;
import java.util.Date;

public final class CallInMapper {

    private CallInMapper() {}


    public static CallInDTO toDto(Call call) {
        if (call == null) return null;

        CallInDTO.CallInDTOBuilder builder = CallInDTO.builder();

        mapIdentityFields(builder, call);
        mapDateFields(builder, call);
        mapRelationFields(builder, call);
        mapFlagFields(builder, call);
        mapResultCodeFields(builder, call);

        return builder.build();
    }

    private static void mapIdentityFields(CallInDTO.CallInDTOBuilder builder, Call call) {
        builder.id(call.getId())
                .uniqueKey(call.getUniqueKey())
                .dnis(call.getCalledNumber())
                .dnis10(call.getCalledNumber() != null && call.getCalledNumber().startsWith("0")
                        ? call.getCalledNumber().substring(1) : call.getCalledNumber())
                .channel(call.getChannel())
                .resultCodeSequence(call.getResultCodeSequence())
                .callOriginatorType(call.getCallOriginator() != null ? call.getCallOriginator().getClass().getSimpleName() : null)
                .callType(call.getCallType())
                .callerId(call.getCallerId())
                .callbackNumber(call.getCallbackNumber())
                .callbackType(call.getCallbackType())
                .retryNumber(call.getRetryNumber())
                .retryHistory(call.getRetryHistory())
                .attachedData(call.getAttachedData())
                .lscResponseValues(call.getLscResponseValues())
                .customerName(call.getCustomerName())
                .customerSurname(call.getCustomerSurname())
                .relationType(call.getRelationType())
                .relation(call.getRelation())
                .attempts(call.getAttempts());
    }

    private static void mapDateFields(CallInDTO.CallInDTOBuilder builder, Call call) {
        builder.callbackDate(toDate(call.getCallbackDate()))
                .retryDate(toDate(call.getRetryDate()))
                .originationDate(toDate(call.getOriginationDate()))
                .startDate(toDate(call.getStartDate()))
                .answerDate(toDate(call.getAnswerDate()))
                .completeDate(toDate(call.getCompleteDate()))
                .isFetched(toDate(call.getIsFetched()))
                .createDate(call.getCreateDate());
    }

    private static void mapRelationFields(CallInDTO.CallInDTOBuilder builder, Call call) {
        builder.userId(getIdFromEntity(call.getUser()))
                .campaignId(getIdFromEntity(call.getCampaign()))
                .cdrId(getIdFromEntity(call.getCdr()))
                .appliedSegmentationFilterId(getIdFromEntity(call.getAppliedSegmentationFilter()))
                .dncNumbers(call.getDNCNumbers());
    }

    private static void mapFlagFields(CallInDTO.CallInDTOBuilder builder, Call call) {
        builder.call_duration(Math.round(call.getDurationFromAnswer() / 1000.0))
                .ring_duration(Math.round(call.getDurationFromOrigination() / 1000.0))
                .abandoned_call(call.isAbandoned())
                .orphaned(call.isOrphaned())
                .answeredByUser(call.isAnsweredByUser());
    }

    private static void mapResultCodeFields(CallInDTO.CallInDTOBuilder builder, Call call) {

        // Extract result code entities from the Call object
        ResultCode userRc = call.getUserResultCode();           // Agent-assigned result code (wbp_result_code)
        TelephonyResultCode teleRc = call.getTelephonyResultCode(); // System-level telephony result code

// result_code_text value used for both display and call_result_code_id mapping
        String userValue = userRc != null ? userRc.getName() : null;

        builder
                // Human-readable result code text (maps to wbp_result_code.result_code_text)
                .result_code_name(userValue)

                // Hardcoded sequential ID derived from result_code_text via CASE/WHEN mapping
                // (NOT the wbp_result_code PK — see CallResultCodeEnum for full mapping)
                // Defaults to ARANMADI(1) when no user result code is present
                .call_result_code_id(userValue != null ? (long) CallResultCodeEnum.getIdFromText(userValue) : (long) CallResultCodeEnum.ARANMADI.getId())

                // The actual wbp_result_code table PK (wcr.result_code_id in the stored procedure)
                // Distinct from call_result_code_id which is a derived sequential number
                .user_result_code_id(getIdFromEntity(userRc))

                // Telephony-layer result code name (e.g. BUSY, NO ANSWER) — system-generated, not agent-assigned
                .telephony_result_code_name(teleRc != null ? teleRc.getName() : null);
    }

    private static Long getIdFromEntity(Object entity) {
        if (entity == null) return null;
        try {
            Method getId = entity.getClass().getMethod("getId");
            Object id = getId.invoke(entity);
            return id instanceof Long ? (Long) id : id != null ? Long.valueOf(id.toString()) : null;
        } catch (Exception e) {
            return null;
        }
    }


    /**
     * Converts a raw value to a {@link Date} object.
     *
     * <p>Supports the following input types:
     * <ul>
     *   <li>{@link Date} - returned as-is</li>
     *   <li>{@link Long} - treated as epoch milliseconds</li>
     *   <li>{@link Double} - treated as epoch seconds if >= 1_000_000_000, otherwise as day offset seconds combined with createDate</li>
     * </ul>
     * </p>
     *
     * @param value      the raw value to convert, may be null
     * @return converted {@link Date}, or null if value is null or unsupported type
     */
    private static Date toDate(Object value) {
        if (value == null) return null;
        if (value instanceof Date) return (Date) value;
        if (value instanceof Long) return new Date((Long) value);
        if (value instanceof Double) return new Date(Utils.convertDoubleToLongDate((Double) value));
        return null;
    }
}