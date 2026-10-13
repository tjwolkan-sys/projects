package com.powerdialer.redis.event.interactionnotifier.dto;

import com.powerdialer.util.Constants;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * Data Transfer Object for Call entity.
 * Designed for serialization and efficient transmission via Redis.
 * All complex nested objects are replaced with their IDs or simple types.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CallInDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;
    private String uniqueKey;
    private String dnis;
    private String dnis10;
    private String channel;

    // Use IDs instead of full entity objects for efficiency
    private String telephony_result_code_name;
    private Long call_result_code_id;
    private Long user_result_code_id;
    private String result_code_name;
    private Integer resultCodeSequence;

    private Long userId; // Replaces the full User object
    private String callbackNumber;
    private Date callbackDate;
    private String retryNumber;
    private List<String> retryHistory;
    private Date retryDate;
    private Date originationDate;
    private Date startDate;
    private Date answerDate;
    private Date completeDate;

    private Long campaignId; // Replaces the full Campaign object
    private String attachedData;

    // Use a simple type for the originator enum
    private String callOriginatorType;

    private long call_duration;
    private long ring_duration;
    private boolean abandoned_call;
    private boolean orphaned;

    // Use epoch timestamps for creation date
    private Date createDate;

    private Set<String> dncNumbers;
    private Constants.CALL_TYPES callType;
    private boolean answeredByUser;
    private Date isFetched;
    private String callerId;

    // Simplified CDR as a separate DTO field if needed, or just store its ID
    private Long cdrId;

    private Integer attempts;

    // Store filter ID or name if applicable
    private Long appliedSegmentationFilterId;

    private String lscResponseValues;
    private Constants.CALLBACKTYPE callbackType;
    private String customerName;
    private String customerSurname;
    private Integer relationType;
    private String relation;

}