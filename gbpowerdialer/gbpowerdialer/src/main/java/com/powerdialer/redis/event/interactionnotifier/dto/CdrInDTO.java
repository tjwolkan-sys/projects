package com.powerdialer.redis.event.interactionnotifier.dto;

import com.powerdialer.util.Constants;
import lombok.*;
import java.io.Serializable;
import java.util.Date;

/**
 * Data Transfer Object for Call Detail Record (CDR).
 * Optimized for Redis pub/sub, with relational entities reduced to IDs.
 * Implements Serializable for safe network transmission.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CdrInDTO implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private Long id;
    private String parent_id;
    private String call_Id;
    private String internalCallId;
    
    // Timestamps in milliseconds since  (more reliable for JSON serialization)
    private Date callStartTime;
    private Date start_date;
    private Date callAnswerTime;
    private Date callHandlingStartTime;
    private Date end_date;
    
    // Relational entities are reduced to their IDs to prevent object graph complexity
    private Long tenant_id;
    private Long campaignId;
    private Long campaignType;
    private Long contact_id;
    private Long group_id;
    private String ani;
    private String contactNumberColumn;
    private Integer contactNumberOrder;
    private Integer rcSequence;
    private Long serverId;
    private Long telephony_result_code_id; // ID of the TelephonyResultCode entity
    private String skills;
    private Long assignedUserId;
    private Long user_id;
    private String connectedIvrNumber;
    
    // Enum can be safely serialized/deserialized by Jackson
    private Constants.CDR_OUTDIALING_TYPE cdrOutdialingType;
    

}