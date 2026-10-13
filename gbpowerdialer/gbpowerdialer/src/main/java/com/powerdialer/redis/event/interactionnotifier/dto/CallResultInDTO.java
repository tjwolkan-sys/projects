package com.powerdialer.redis.event.interactionnotifier.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CallResultInDTO {

    private String uniqueKey;
    private String calledNumber;
    private Date startDate;
    private Date answerDate;
    private Date completeDate;

    // Telephony RC
    private Long telephonyResultCodeId;
    private String telephonyResultCodeName;

    // User RC
    private Long userResultCodeId;
    private String userResultCodeName;

    private Integer resultCodeSequence;
    private Long userId;

    private String callbackNumber;
    private Date callbackDate;

    private String retryNumber;
    private List<String> retryHistory;
    private Date retryDate;

    private Date fetchedDataFlag;
    private Set<String> dncNumbers;
    private Integer attempts;
    private String lscResponseValues;

    // Durations (ms)
    private Long durationFromAnswer;
    private Long durationFromOrigination;

    // Call meta
    private String callType;
    private String callbackType;
    private Boolean abandoned;
    private Boolean orphaned;
    private Boolean answeredByUser;

    // Customer info
    private String customerName;
    private String customerSurname;
    private Integer relationType;
    private String relation;

    // ── Dynamic table parameters (rawData's come in everbody)
    private Map<String, Object> dynamicFields;

}