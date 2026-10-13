package com.powerdialer.redis.event.interactionnotifier.mapper;

import com.google.common.collect.ImmutableSet;
import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.redis.event.interactionnotifier.dto.CallResultInDTO;
import com.powerdialer.util.Utils;
import org.json.JSONObject;

import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class CallResultInMapper {


    private static final Set<String> SYSTEM_COLUMNS = ImmutableSet.of(
            "start_date", "answer_date", "complete_date",
            "called_number", "telephony_result_code", "user_result_code",
            "rc_sequence", "user_id", "callback_number", "callback_date",
            "retry_number", "retry_history", "retry_date",
            "is_fetched", "dnc_numbers", "attempts", "update_date",
            "response_values", "is_disabled", "multiple_sort"
    );

    private CallResultInMapper() {}

    public static CallResultInDTO toDto(Call call) {
        if (call == null) return null;

        CallResultInDTO.CallResultInDTOBuilder builder = CallResultInDTO.builder();

        mapCoreFields(builder, call);
        mapResultCodeFields(builder, call);
        mapCallbackFields(builder, call);
        mapRetryFields(builder, call);
        mapFlagFields(builder, call);
        mapDurationFields(builder, call);
        mapMetaFields(builder, call);
        mapCustomerFields(builder, call);
        mapDynamicFields(builder, call);

        return builder.build();
    }

    // ----------------------------------------------------------------

    private static void mapCoreFields(CallResultInDTO.CallResultInDTOBuilder builder, Call call) {
        builder.uniqueKey(call.getUniqueKey())
                .calledNumber(call.getCalledNumber())
                .startDate(toDate(call.getStartDate()))
                .answerDate(toDate(call.getAnswerDate()))
                .completeDate(toDate(call.getCompleteDate()));
    }

    private static void mapResultCodeFields(CallResultInDTO.CallResultInDTOBuilder builder, Call call) {
        if (call.getTelephonyResultCode() != null) {
            builder.telephonyResultCodeId(call.getTelephonyResultCode().getId())
                    .telephonyResultCodeName(call.getTelephonyResultCode().getName());
        }
        if (call.getUserResultCode() != null) {
            builder.userResultCodeId(call.getUserResultCode().getId())
                    .userResultCodeName(call.getUserResultCode().getName());
        }
        builder.resultCodeSequence(call.getResultCodeSequence());
        if (call.getUser() != null) {
            builder.userId(call.getUser().getId());
        }
    }

    private static void mapCallbackFields(CallResultInDTO.CallResultInDTOBuilder builder, Call call) {
        builder.callbackNumber(call.getCallbackNumber())
                .callbackDate(toDate(call.getCallbackDate()));
    }

    private static void mapRetryFields(CallResultInDTO.CallResultInDTOBuilder builder, Call call) {
        Date createDate = call.getCreateDate();
        builder.retryNumber(call.getRetryNumber())
                .retryHistory(call.getRetryHistory())
                .retryDate(toDate(call.getRetryDate()));
    }

    private static void mapFlagFields(CallResultInDTO.CallResultInDTOBuilder builder, Call call) {
        Date createDate = call.getCreateDate();
        builder.fetchedDataFlag(toDate(call.getIsFetched()))
                .dncNumbers(call.getDNCNumbers())
                .attempts(call.getAttempts())
                .lscResponseValues(call.getLscResponseValues());
    }

    private static void mapDurationFields(CallResultInDTO.CallResultInDTOBuilder builder, Call call) {
        builder.durationFromAnswer(call.getDurationFromAnswer())
                .durationFromOrigination(call.getDurationFromOrigination());
    }

    private static void mapMetaFields(CallResultInDTO.CallResultInDTOBuilder builder, Call call) {
        if (call.getCallType() != null)
            builder.callType(call.getCallType().name());
        if (call.getCallbackType() != null)
            builder.callbackType(call.getCallbackType().name());

        builder.abandoned(call.isAbandoned())
                .orphaned(call.isOrphaned())
                .answeredByUser(call.isAnsweredByUser());
    }

    private static void mapCustomerFields(CallResultInDTO.CallResultInDTOBuilder builder, Call call) {
        builder.customerName(call.getCustomerName())
                .customerSurname(call.getCustomerSurname())
                .relationType(call.getRelationType())
                .relation(call.getRelation());
    }

    // ----------------------------------------------------------------

    private static void mapDynamicFields(CallResultInDTO.CallResultInDTOBuilder builder, Call call) {
        JSONObject rawData = call.getRawData2();
        if (rawData == null) return;


        Map<String, Object> dynamicFields = new LinkedHashMap<>();

        for (String key : rawData.keySet()) {
            if (SYSTEM_COLUMNS.contains(key.toLowerCase())) continue;

            Object value = rawData.isNull(key) ? null : rawData.get(key);
            dynamicFields.put(key, value);
        }

        builder.dynamicFields(dynamicFields);
    }

    // ── Util ─────────────────────────────────────────────────────────────

    private static Date toDate(Object value) {
        if (value == null) return null;
        if (value instanceof Date) return (Date) value;
        if (value instanceof Long) return new Date((Long) value);
        if (value instanceof Double) return new Date(Utils.convertDoubleToLongDate((Double) value));
        return null;
    }

}