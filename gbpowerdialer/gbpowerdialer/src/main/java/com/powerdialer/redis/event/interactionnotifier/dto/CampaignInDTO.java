package com.powerdialer.redis.event.interactionnotifier.dto;

import com.powerdialer.util.Constants.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CampaignInDTO implements Serializable {
    
    private static final long serialVersionUID = 1L;

    private Long campaign_id;
    private Long tenantId;
    private String campaign_name;
    private String description;
    private CAMPAIGN_TYPE type;
    private Boolean enabled;
    private Boolean deleted;

    private Date startDate;
    private Date endDate;
    private Date stoppedDate;
    private Date createdDate;
    private Date updatedDate;
    
    private Long ivrApplicationId;
    private Long ivrApplicationNumberId;
    private String context;
    private String abandonIvr;
    
    private Integer previewTimeout;
    private Integer previewRelax;
    private Integer availableThreshold;
    private Boolean allowCallback;
    private Boolean allowPreviewBeep;
    private Boolean allowCallbackCustom;
    private Boolean callbackToAppointer;
    private Boolean allowOverwriteByWebphone;
    private Boolean allowUrcWithoutMt;
    private Boolean appointerReset;
    private Long appointerResetTimeout;
    private APPOINTER_RESET_STRATEGY appointerResetStrategy;
    private Integer agentCbRescheduleDelayMin;
    private Integer agentCbConvertAfterAttempts;

    private END_LIST_BEHAVIOUR endListBehaviour;
    private Integer lowCacheSelectInterval;
    private DB_SORT_DIRECTION dbListSortDirection;
    
    private RETRY_STRATEGY retryStrategy;
    private Boolean retryScheduleEnabled;
    private Integer retryOveralAttempt;
    private Integer macroInterval;
    private Integer macroMaxAttempt;
    private Integer maxAttemptPerNumber;
    private Integer retryRatio;
    private Double retryDelay;
    private RETRY_CALLER_ID_BEHAVIOR retryCallerIdBehavior;
    private CALLER_ID_BEHAVIOUR callerIdBehaviour;
    private String callerId;
    private Long callerIdPoolId;
    private TIMEZONE_TYPE timezoneType;
    private APPLY_TO applyFilterTo;
    private FILTER_JOIN_TYPE filterJoinType;
    private Integer concurrentLines;
    private Integer callsPerSecond;
    private Integer cacheLimit;
    private Integer ringTimeout;
    private Float dialingBoost;
    private Integer abandonTimeout;
    private Boolean amdEnabled;
    private Float maxAnswerRate;
    private Float maxAbandonRate;
    private Double gammaCorrectionFactor;
    private Double gammaAbandonSensitivity;
    private Integer gammaWindowCapacity;
    private Integer gammaRequiredWindowSize;
    private PREDICTIVE_STRATEGY predictiveStrategy;

    private Boolean scheduleEnabled;
    private Boolean scheduleAutomationEnabled;
    
    private Boolean dncEnabled;
    private Long dncId;
    
    private Boolean lscEnabled;
    private LSC_TIMEOUT_BEHAVIOR lscTimeoutBehavior;
    private Boolean fullContactListCheckEnabled;
    
    private Boolean skillEnabled;

    private Boolean attachedDataLongTermSave;
    
    private String mohClassCallingParty;
    private Boolean startupDbCheck;
    private Long resultCodeTimerStatus;
    private Integer resultCodeTimer;
    
    private Boolean stopConditionEnabled;
    private STOP_CONDITION_PARAMETER stopConditionParameter;
    private Long stopConditionValue;
    private Long stopConditionThreshold;
    private CAMPAIGN_STOP_TYPE stopType;
    private CAMPAIGN_STOP_TYPE stopTypeHistory;
    
    private Boolean allowApiControl;
    private String apiToken;

    private Integer version;

    private CallResultInDTO callResult;
}