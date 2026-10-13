package com.powerdialer.model;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import javax.persistence.*;
import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.annotations.Where;
import org.hibernate.validator.constraints.NotBlank;
import org.hibernate.validator.constraints.Range;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.apache.commons.lang3.SerializationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

import com.powerdialer.engine.dialer.CampaignStopJob;
import com.powerdialer.model.base.BaseEntity;
import com.powerdialer.model.base.BaseEntityTenant;
import com.powerdialer.serialization.CampaignSerializer;
import com.powerdialer.util.Constants;
import com.powerdialer.util.Constants.APPLY_TO;
import com.powerdialer.util.Constants.APPOINTER_RESET_STRATEGY;
import com.powerdialer.util.Constants.CALLER_ID_BEHAVIOUR;
import com.powerdialer.util.Constants.CAMPAIGN_STATE;
import com.powerdialer.util.Constants.CAMPAIGN_TYPE;
import com.powerdialer.util.Constants.DB_SORT_DIRECTION;
import com.powerdialer.util.Constants.END_LIST_BEHAVIOUR;
import com.powerdialer.util.Constants.FILTER_JOIN_TYPE;
import com.powerdialer.util.Constants.RETRY_STRATEGY;
import com.powerdialer.util.Constants.TIMEZONE_TYPE;

@Entity
@Table(name = "pd_campaign")
@DynamicUpdate
@JsonSerialize(using = CampaignSerializer.class)
public class Campaign extends BaseEntityTenant implements Cloneable {

  private static final Logger LOG = LoggerFactory.getLogger(Campaign.class);
  private static final long serialVersionUID = -7034636914560743187L;
  private Long id;
  private String name;
  private String description;
  private CAMPAIGN_TYPE type;
  private Date startDate;
  private Date startedDate;
  private int liveCallsFromLastStarting;
  private Date endDate;
  private Date stoppedDate;
  private IVRApplication ivrApplication;
  private Number ivrApplicationNumber;
  private Integer previewTimeout;
  private Integer previewRelax;
  private Integer availableThreshold;
  private Boolean allowCallback;
  private Boolean allowPreviewBeep;
  private Boolean allowCallbackCustom;
  private Boolean callbackToAppointer;
  private Boolean allowUrcWithoutMt;
  private Boolean allowOverwriteByWebphone;
  private Boolean appointerReset;
  private Long appointerResetTimeout;
  private APPOINTER_RESET_STRATEGY appointerResetStrategy;
  private Integer agentCbRescheduleDelayMin;
  private Integer agentCbConvertAfterAttempts;
  private END_LIST_BEHAVIOUR endListBehaviour;
  private Integer lowCacheSelectInterval;
  private String dbConnectionString;
  private String dbUsername;
  private String dbPassword;
  private String dbListTable;
  private String colUniqueKey;
  private String colSortBy;
  private String colResultFetchedDataFlag;
  private DB_SORT_DIRECTION dbListSortDirection;
  private String dbResultTable;
  private String colResultCalledNumber;
  private String colResultTelephonyRc;
  private String colResultUserRc;
  private String colResultRcSequence;
  private String colResultUserId;
  private String colResultCallbackNumber;
  private String colResultCallbackDate;
  private String colResultStartDate;
  private String colResultAnswerDate;
  private String colResultCompleteDate;
  private String colResultRetryNumber;
  private String colResultRetryHistory;
  private String colResultCbHistory;
  private String colResultCallProcess;
  private String colResultRetryDate;
  private String colResultUniqueKey;
  private String colResultDNCNumbers;
  private String colResultCustomerNo;
  private String colDateEntered;
  private String colResultAttempts;
  private String colResultCallbackAttempts;
  private String colResultUpdateDate;
  private String colResultResponseValues;
  private String colResultCloseReason;
  private RETRY_STRATEGY retryStrategy;
  private CALLER_ID_BEHAVIOUR callerIdBehaviour;
  private String callerId;
  private TIMEZONE_TYPE timezoneType = TIMEZONE_TYPE.OFF;
  private String colTimezoneOffset;
  private String colCallFromTime;
  private String colCallToTime;
  private APPLY_TO applyFilterTo;
  private Integer concurrentLines;
  private Integer callsPerSecond;
  private Integer cacheLimit;
  private Integer ringTimeout;
  private Float dialingBoost;
  private Integer abandonTimeout;
  private String abandonIvr;
  private String context;
  private Boolean amdEnabled;
  private Boolean retryScheduleEnabled;
  private Integer retryOveralAttempt;
  private Integer macroInterval;
  private Integer macroMaxAttempt;
  private Integer maxAttemptPerNumber;
  private Integer retryRatio;
  private Double retryDelay;
  private Boolean scheduleEnabled;
  private Boolean dncEnabled;
  private Boolean lscEnabled;
  private Boolean fullContactListCheckEnabled = Boolean.FALSE;
  private Boolean expireFilterEnabled = Boolean.FALSE;
  private String colSkill;
  private Boolean skillEnabled;
  private Set<Skill> skills = new HashSet<>();
  private Set<Location> locations = new HashSet<>();
  private Set<DNC> dncs = new HashSet<>();
  private DNC dnc;
  private List<LSC> lscs = new ArrayList<>();
  private List<PhoneNumberColumn> phoneNumberColumns = new ArrayList<>();
  private List<SortField> sortFields = new ArrayList<>();
  private List<CampaignGroup> campaignGroups = new ArrayList<>();
  private List<ResultCodeSchedule> resultCodeSchedules = new ArrayList<>();
  private List<Schedule> schedules = new ArrayList<>();
  private List<ScheduleBlackoutAutomation> blackoutSchedules = new ArrayList<>();
  private List<Filter> filters = new ArrayList<>();
  private List<AttachedData> attachedDatas = new ArrayList<>();
  private Boolean attachedDataLongTermSave;
  private FILTER_JOIN_TYPE filterJoinType = FILTER_JOIN_TYPE.AND;
  private Integer version;
  private AtomicInteger agentsSize = new AtomicInteger();
  private AtomicInteger availAgentsSize = new AtomicInteger();
  private int totalListSize;
  private int actualListSize;
  private int retryListSize;
  private int callbackListSize;
  private int freshDataListSize;
  private int contactsToHandleAmount;
  private AtomicInteger abandonedCallsSize = new AtomicInteger();
  private double abandonedCallsRatio = 0d;
  private int cacheSize;
  private int queueSize;
  private int completionPercent;
  private double dbEfficiency;
  private double answerRate;
  private long runoutEstimation;
  private AtomicInteger listRemaining = new AtomicInteger();
  private AtomicInteger callsMade = new AtomicInteger();
  private AtomicInteger activeCalls = new AtomicInteger();
  private AtomicInteger incallAgents = new AtomicInteger();
  private AtomicInteger liveCalls = new AtomicInteger();
  private AtomicInteger uniqueLiveCalls = new AtomicInteger();
  private AtomicInteger ringingCalls = new AtomicInteger();
  private AtomicInteger ringingCbCalls = new AtomicInteger();
  private AtomicInteger ringingRetryCalls = new AtomicInteger();
  private AtomicInteger ringingFreshCalls = new AtomicInteger();
  private AtomicInteger noConnectionCalls = new AtomicInteger();
  private AtomicLong totalCallDuration = new AtomicLong();
  private AtomicInteger retriesMade = new AtomicInteger();
  private AtomicInteger inCalls = new AtomicInteger();
  private CAMPAIGN_STATE state;
  private boolean manuallyPaused;
  private CampaignStopJob campaignStopJob;
  private Cache<String, Call> originatedCalls = CacheBuilder.newBuilder().build();
  private String mohClassCallingParty;
  private Boolean startupDbCheck;
  private Long resultCodeTimerStatus;
  private Integer resultCodeTimer;
  private List<PredefinedValue> predefinedValues = new ArrayList<>();
  private Map<Long, Long> callsByUserResultCode = new ConcurrentHashMap<>();
  private Map<Long, Long> callsByTelephonyResultCode = new ConcurrentHashMap<>();
  private AtomicLong touchedContacts = new AtomicLong();
  private AtomicLong completedContacts = new AtomicLong();
  private Boolean stopConditionEnabled;
  private Constants.STOP_CONDITION_PARAMETER stopConditionParameter;
  private Long stopConditionValue;
  private Long stopConditionThreshold;
  private List<ScheduleAutomation> scheduleAutomations = new ArrayList<>();
  private Boolean scheduleAutomationEnabled;
  private Constants.CAMPAIGN_STOP_TYPE stopType;
  private Constants.CAMPAIGN_STOP_TYPE stopTypeHistory;
  private RoutingGroup callerIdPool;
  private Constants.RETRY_CALLER_ID_BEHAVIOR retryCallerIdBehavior;
  private boolean fctRoutingEnabled;
  private String dbPriorityTable;
  private Constants.LSC_TIMEOUT_BEHAVIOR lscTimeoutBehavior;
  private Boolean allowApiControl;
  private String apiToken;
  private final Set<String> callsInProgress = ConcurrentHashMap.newKeySet();
  private Float maxAnswerRate;
  private Float maxAbandonRate;
  private Double gammaCorrectionFactor;
  private Double gammaAbandonSensitivity;
  private Integer gammaWindowCapacity;
  private Integer gammaRequiredWindowSize;
  // gamma_plus NOT NULL kolonuna map'lenir; alan başlatması olmadan yeni kampanya insert'i patlar.
  private Boolean gammaPlus = Boolean.FALSE;
  private Constants.PREDICTIVE_STRATEGY predictiveStrategy;
  private Integer maxLines;
  private long availableUsersCount = 0L;
  private Boolean countAllCallTypes;

  @Transient
  public Integer getMaxLines() {
    return maxLines;
  }

  public void setMaxLines(Integer maxLines) {
    this.maxLines = maxLines;
  }

  @Transient
  public long getAvailableUsersCount() {
    return availableUsersCount;
  }

  public void setAvailableUsersCount(long availableUsersCount) {
    this.availableUsersCount = availableUsersCount;
  }

  /**
   * Runtime-only işaret: kampanya operatör tarafından manuel pause edildiyse true.
   * Scheduler, schedule penceresi içinde manuel paused kampanyayı otomatik START
   * etmemek için bu işareti kullanır. Otomasyon (schedule automation) kaynaklı
   * pause'ta false kalır ve auto-resume korunur. Kalıcı değildir (transient);
   * JVM restart'ta paused state zaten kaybolduğu için DB'ye yazılmaz.
   */
  @Transient
  public boolean isManuallyPaused() {
    return manuallyPaused;
  }

  public void setManuallyPaused(boolean manuallyPaused) {
    this.manuallyPaused = manuallyPaused;
  }

  @Column(name = "result_code_timer")
  public Integer getResultCodeTimer() {
    return resultCodeTimer;
  }

  public void setResultCodeTimer(Integer resultCodeTimer) {
    this.resultCodeTimer = resultCodeTimer;
  }

  @Column(name = "result_code_timer_status_id")
  public Long getResultCodeTimerStatus() {
    return resultCodeTimerStatus;
  }

  public void setResultCodeTimerStatus(Long resultCodeTimerStatus) {
    this.resultCodeTimerStatus = resultCodeTimerStatus;
  }

  @Version
  @Column(name = "version")
  public Integer getVersion() {
    return version;
  }

  public void setVersion(Integer version) {
    this.version = version;
  }

  @Valid
  @OneToMany(cascade = { CascadeType.ALL }, fetch = FetchType.EAGER, mappedBy = "campaign")
  @Fetch(FetchMode.SUBSELECT)
  @Where(clause = "deleted = 0")
  public List<Filter> getFilters() {
    return filters;
  }

  public void setFilters(List<Filter> filters) {
    this.filters = filters;
  }

  @Transient
  public List<Filter> getEnabledFilters() {
    return filters != null ? filters.stream().filter(BaseEntity::isEnabled).collect(Collectors.toList()) : Collections.emptyList();
  }

  @Transient
  public List<Filter> getEnabledFiltersBySegmentation(boolean segmentation) {
    return filters != null
            ? filters.stream()
            .filter(filter -> filter.isEnabled() && filter.getSegmentation() == segmentation)
            .collect(Collectors.toList())
            : Collections.emptyList();
  }

  @Transient
  public List<Filter> getEnabledNotDeletedFilters() {
    return filters != null
        ? filters.stream()
          .filter(filter -> filter.isEnabled() && !filter.isDeleted())
          .collect(Collectors.toList())
        : Collections.emptyList();
  }

  @OneToMany(cascade = { CascadeType.ALL }, fetch = FetchType.LAZY, mappedBy = "campaign")
  @Fetch(FetchMode.SUBSELECT)
  @Where(clause = "deleted = 0")
  public List<AttachedData> getAttachedDatas() {
    return attachedDatas;
  }

  public void setAttachedDatas(List<AttachedData> attachedDatas) {
    this.attachedDatas = attachedDatas;
  }

  @Column(name = "attached_data_long_term_save")
  public Boolean getAttachedDataLongTermSave() {
    return attachedDataLongTermSave;
  }

  public void setAttachedDataLongTermSave(Boolean attachedDataLongTermSave) {
    this.attachedDataLongTermSave = attachedDataLongTermSave;
  }

  @Transient
  public List<AttachedData> getEnabledAttachedDatas() {
    return attachedDatas.stream().filter(BaseEntity::isEnabled).collect(Collectors.toList());
  }

  @Enumerated(EnumType.ORDINAL)
  @Column(name = "filter_join_type")
  public FILTER_JOIN_TYPE getFilterJoinType() {
    return filterJoinType;
  }

  public void setFilterJoinType(FILTER_JOIN_TYPE filterJoinType) {
    this.filterJoinType = filterJoinType;
  }

  public Campaign() {
    super();
  }

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO, generator = "baseEntity_seq")
  @SequenceGenerator(name = "baseEntity_seq", sequenceName = "PD_CAMPAIGN_FCSEQ", allocationSize = 1)
  @Column(name = "id")
  public Long getId() {
    return this.id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  @NotBlank(message = "{campaign.name.blank}")
  @Column(name = "name", nullable = false, columnDefinition = "NVARCHAR(255)")
  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  @Size(message = "{campaign.description.range}", max = 255)
  @Column(name = "description", columnDefinition = "NVARCHAR(255)")
  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  @NotNull(message = "{campaign.type.blank}")
  @Enumerated(EnumType.ORDINAL)
  @Column(name = "type", nullable = false)
  public CAMPAIGN_TYPE getType() {
    return type;
  }

  public void setType(CAMPAIGN_TYPE type) {
    this.type = type;
  }

  @NotNull(message = "{campaign.start_date.blank}")
  @Column(name = "start_date")
  public Date getStartDate() {
    return startDate;
  }

  @Transient
  public Date getStartedDate() {
    return startedDate;
  }

  public void setStartDate(Date startDate) {
    this.startDate = startDate;
  }

  public void setStartedDate(Date startedDate) {
    this.startedDate = startedDate;
  }

  public void setLiveCallsFromLastStarting(int liveCalls) {
    this.liveCallsFromLastStarting = liveCalls;
  }

  @NotNull(message = "{campaign.end_date.blank}")
  @Column(name = "end_date")
  public Date getEndDate() {
    return endDate;
  }

  public void setEndDate(Date endDate) {
    this.endDate = endDate;
  }

  @Column(name = "stopped_date")
  public Date getStoppedDate() {
    return stoppedDate;
  }

  public void setStoppedDate(Date stoppedDate) {
    this.stoppedDate = stoppedDate;
  }

  @JsonIgnore
  @ManyToOne(cascade = { CascadeType.PERSIST }, fetch = FetchType.LAZY)
  @JoinColumn(name = "ivr_application_id")
  public IVRApplication getIvrApplication() {
    return ivrApplication;
  }

  public void setIvrApplication(IVRApplication ivrApplication) {
    this.ivrApplication = ivrApplication;
  }

  @JsonIgnore
  @ManyToOne(cascade = { CascadeType.PERSIST }, fetch = FetchType.LAZY)
  @JoinColumn(name = "ivr_application_number_id")
  public Number getIvrApplicationNumber() {
    return ivrApplicationNumber;
  }

  public void setIvrApplicationNumber(Number ivrApplicationNumber) {
    this.ivrApplicationNumber = ivrApplicationNumber;
  }

  @Column(name = "preview_timeout")
  public Integer getPreviewTimeout() {
    return previewTimeout;
  }

  public void setPreviewTimeout(Integer previewTimeout) {
    this.previewTimeout = previewTimeout;
  }

  @Column(name = "preview_relax")
  public Integer getPreviewRelax() {
    return previewRelax;
  }

  public void setPreviewRelax(Integer previewRelax) {
    this.previewRelax = previewRelax;
  }

  @Column(name = "available_threshold", nullable = false)
  @ColumnDefault("0")
  public Integer getAvailableThreshold() {
    return availableThreshold;
  }

  public void setAvailableThreshold(Integer availableThreshold) {
    this.availableThreshold = availableThreshold;
  }

  @Column(name = "allow_callback", nullable = false)
  public Boolean getAllowCallback() {
    return allowCallback;
  }

  public void setAllowCallback(Boolean allowCallback) {
    this.allowCallback = allowCallback;
  }

  @Column(name = "allow_preview_beep", nullable = false)
  public Boolean getAllowPreviewBeep() {
    return allowPreviewBeep;
  }

  public void setAllowPreviewBeep(Boolean allowPreviewBeep) {
    this.allowPreviewBeep = allowPreviewBeep;
  }

  @Column(name = "allow_callback_custom", nullable = false)
  public Boolean getAllowCallbackCustom() {
    return allowCallbackCustom;
  }

  public void setAllowCallbackCustom(Boolean allowCallbackCustom) {
    this.allowCallbackCustom = allowCallbackCustom;
  }

  @Column(name = "callback_to_appointer", nullable = false)
  public Boolean getCallbackToAppointer() {
    return callbackToAppointer;
  }

  public void setCallbackToAppointer(Boolean callbackToAppointer) {
    this.callbackToAppointer = callbackToAppointer;
  }

  @Column(name = "allow_urc_without_mt", nullable = false, columnDefinition = "BIT DEFAULT 0")
  public Boolean getAllowUrcWithoutMt() {
    return allowUrcWithoutMt;
  }

  public void setAllowUrcWithoutMt(Boolean allowUrcWithoutMt) {
    this.allowUrcWithoutMt = allowUrcWithoutMt;
  }

  @Column(name = "allow_overwrite_by_webphone", nullable = false, columnDefinition = "BIT DEFAULT 0")
  public Boolean getAllowOverwriteByWebphone() {
    return allowOverwriteByWebphone;
  }

  public void setAllowOverwriteByWebphone(Boolean allowOverwriteByWebphone) {
    this.allowOverwriteByWebphone = allowOverwriteByWebphone;
  }

  @Column(name = "appointer_reset", nullable = false)
  public Boolean getAppointerReset() {
    return appointerReset;
  }

  public void setAppointerReset(Boolean resetAppointer) {
    this.appointerReset = resetAppointer;
  }

  @Column(name = "appointer_reset_timeout")
  public Long getAppointerResetTimeout() {
    return appointerResetTimeout;
  }

  public void setAppointerResetTimeout(Long appointerResetTimeout) {
    this.appointerResetTimeout = appointerResetTimeout;
  }

  /**
   * Callback to Appointer sıfırlama stratejisi (task 321997). null/TIMER = legacy zaman temelli
   * davranış; ATTEMPT_COUNT = reschedule + deneme sayacı + limitte campaign-wide devir.
   */
  @Enumerated(EnumType.ORDINAL)
  @Column(name = "appointer_reset_strategy")
  public APPOINTER_RESET_STRATEGY getAppointerResetStrategy() {
    return appointerResetStrategy;
  }

  public void setAppointerResetStrategy(APPOINTER_RESET_STRATEGY appointerResetStrategy) {
    this.appointerResetStrategy = appointerResetStrategy;
  }

  /**
   * Strateji ATTEMPT_COUNT mı? null (legacy) ve TIMER için false döner; runtime dallanma bunu kullanır.
   */
  @Transient
  public boolean isAppointerResetByAttempt() {
    return APPOINTER_RESET_STRATEGY.ATTEMPT_COUNT.equals(appointerResetStrategy);
  }

  @Column(name = "agent_cb_reschedule_delay_min")
  public Integer getAgentCbRescheduleDelayMin() {
    return agentCbRescheduleDelayMin;
  }

  public void setAgentCbRescheduleDelayMin(Integer agentCbRescheduleDelayMin) {
    this.agentCbRescheduleDelayMin = agentCbRescheduleDelayMin;
  }

  @Column(name = "agent_cb_convert_after_attempts")
  public Integer getAgentCbConvertAfterAttempts() {
    return agentCbConvertAfterAttempts;
  }

  public void setAgentCbConvertAfterAttempts(Integer agentCbConvertAfterAttempts) {
    this.agentCbConvertAfterAttempts = agentCbConvertAfterAttempts;
  }

  @NotNull(message = "{campaign.end_list_behaviour.blank}")
  @Enumerated(EnumType.ORDINAL)
  @Column(name = "end_list_behaviour", nullable = false)
  public END_LIST_BEHAVIOUR getEndListBehaviour() {
    return endListBehaviour;
  }

  public void setEndListBehaviour(END_LIST_BEHAVIOUR endListBehaviour) {
    this.endListBehaviour = endListBehaviour;
  }

  @Min(value = 5, message = "{campaign.low_cache_select_interval.min}")
  @Column(name = "low_cache_select_interval")
  public Integer getLowCacheSelectInterval() {
    return lowCacheSelectInterval;
  }

  public void setLowCacheSelectInterval(Integer lowCacheSelectInterval) {
    this.lowCacheSelectInterval = lowCacheSelectInterval;
  }

  @Size(message = "{campaign.db_connection_string.size}", max = 255)
  @NotBlank(message = "{campaign.db_connection_string.blank}")
  @Column(name = "db_connection_string", nullable = false, columnDefinition = "NVARCHAR(255)")
  public String getDbConnectionString() {
    return dbConnectionString;
  }

  public void setDbConnectionString(String dbConnectionString) {
    this.dbConnectionString = dbConnectionString;
  }

  @Column(name = "db_username", columnDefinition = "NVARCHAR(255)")
  public String getDbUsername() {
    return dbUsername;
  }

  public void setDbUsername(String dbUsername) {
    this.dbUsername = dbUsername;
  }

  @Column(name = "db_password", columnDefinition = "NVARCHAR(255)")
  public String getDbPassword() {
    return dbPassword;
  }

  public void setDbPassword(String dbPassword) {
    this.dbPassword = dbPassword;
  }

  @Column(name = "db_list_table", nullable = false, columnDefinition = "NVARCHAR(255)")
  public String getDbListTable() {
    return dbListTable;
  }

  public void setDbListTable(String dbListTable) {
    this.dbListTable = dbListTable;
  }

  @Column(name = "col_unique_key", nullable = false, columnDefinition = "NVARCHAR(255)")
  public String getColUniqueKey() {
    return colUniqueKey;
  }

  public void setColUniqueKey(String colUniqueKey) {
    this.colUniqueKey = colUniqueKey;
  }

  @Column(name = "col_sort_by", columnDefinition = "NVARCHAR(255)")
  public String getColSortBy() {
    return colSortBy;
  }

  public void setColSortBy(String colSortBy) {
    this.colSortBy = colSortBy;
  }

  @Transient
  public boolean isMultipleSort() {
    return Constants.SORT_BY_MULTIPLE.equals(colSortBy);
  }

  @Enumerated(EnumType.ORDINAL)
  @Column(name = "db_list_sort_direction")
  public DB_SORT_DIRECTION getDbListSortDirection() {
    return dbListSortDirection;
  }

  public void setDbListSortDirection(DB_SORT_DIRECTION dbListSortDirection) {
    this.dbListSortDirection = dbListSortDirection;
  }

  @NotBlank(message = "{campaign.db_result_table.blank}")
  @Column(name = "db_result_table", columnDefinition = "NVARCHAR(255)")
  public String getDbResultTable() {
    return dbResultTable;
  }

  public void setDbResultTable(String dbResultTable) {
    this.dbResultTable = dbResultTable;
  }

  @Column(name = "col_result_fetched_data_flag", nullable = false, columnDefinition = "NVARCHAR(255)")
  public String getColResultFetchedDataFlag() {
    return colResultFetchedDataFlag;
  }

  public void setColResultFetchedDataFlag(String colResultFetchedDataFlag) {
    this.colResultFetchedDataFlag = colResultFetchedDataFlag;
  }

  @NotBlank(message = "{campaign.db_result_column.blank}")
  @Column(name = "col_result_called_number", columnDefinition = "NVARCHAR(255)")
  public String getColResultCalledNumber() {
    return colResultCalledNumber;
  }

  public void setColResultCalledNumber(String colResultCalledNumber) {
    this.colResultCalledNumber = colResultCalledNumber;
  }

  @NotBlank(message = "{campaign.db_result_column.blank}")
  @Column(name = "col_result_telephony_rc", columnDefinition = "NVARCHAR(255)")
  public String getColResultTelephonyRc() {
    return colResultTelephonyRc;
  }

  public void setColResultTelephonyRc(String colResultTelephonyRc) {
    this.colResultTelephonyRc = colResultTelephonyRc;
  }

  @Column(name = "col_result_user_rc", columnDefinition = "NVARCHAR(255)")
  public String getColResultUserRc() {
    return colResultUserRc;
  }

  public void setColResultUserRc(String colResultUserRc) {
    this.colResultUserRc = colResultUserRc;
  }

  @Column(name = "col_result_rc_sequence", columnDefinition = "NVARCHAR(255)")
  public String getColResultRcSequence() {
    return colResultRcSequence;
  }

  public void setColResultRcSequence(String colResultRcSequence) {
    this.colResultRcSequence = colResultRcSequence;
  }

  @Column(name = "col_result_user_id", columnDefinition = "NVARCHAR(255)")
  public String getColResultUserId() {
    return colResultUserId;
  }

  public void setColResultUserId(String colResultUserId) {
    this.colResultUserId = colResultUserId;
  }

  @Column(name = "col_result_callback_number", columnDefinition = "NVARCHAR(255)")
  public String getColResultCallbackNumber() {
    return colResultCallbackNumber;
  }

  public void setColResultCallbackNumber(String colResultCallbackNumber) {
    this.colResultCallbackNumber = colResultCallbackNumber;
  }

  @Column(name = "col_result_callback_date", columnDefinition = "NVARCHAR(255)")
  public String getColResultCallbackDate() {
    return colResultCallbackDate;
  }

  public void setColResultCallbackDate(String colResultCallbackDate) {
    this.colResultCallbackDate = colResultCallbackDate;
  }

  @NotBlank(message = "{campaign.db_result_column.blank}")
  @Column(name = "col_result_start_date", columnDefinition = "NVARCHAR(255)")
  public String getColResultStartDate() {
    return colResultStartDate;
  }

  public void setColResultStartDate(String colResultStartDate) {
    this.colResultStartDate = colResultStartDate;
  }

  @NotBlank(message = "{campaign.db_result_column.blank}")
  @Column(name = "col_result_answer_date", columnDefinition = "NVARCHAR(255)")
  public String getColResultAnswerDate() {
    return colResultAnswerDate;
  }

  public void setColResultAnswerDate(String colResultAnswerDate) {
    this.colResultAnswerDate = colResultAnswerDate;
  }

  @NotBlank(message = "{campaign.db_result_column.blank}")
  @Column(name = "col_result_complete_date", columnDefinition = "NVARCHAR(255)")
  public String getColResultCompleteDate() {
    return colResultCompleteDate;
  }

  public void setColResultCompleteDate(String colResultCompleteDate) {
    this.colResultCompleteDate = colResultCompleteDate;
  }

  @NotBlank(message = "{campaign.db_result_column.blank}")
  @Column(name = "col_result_retry_number", columnDefinition = "NVARCHAR(255)")
  public String getColResultRetryNumber() {
    return colResultRetryNumber;
  }

  public void setColResultRetryNumber(String colResultRetryNumber) {
    this.colResultRetryNumber = colResultRetryNumber;
  }

  @NotBlank(message = "{campaign.db_result_column.blank}")
  @Column(name = "col_result_retry_history", columnDefinition = "NVARCHAR(MAX)")
  public String getColResultRetryHistory() {
    return colResultRetryHistory;
  }

  public void setColResultRetryHistory(String colResultRetryHistory) {
    this.colResultRetryHistory = colResultRetryHistory;
  }

  @Column(name = "col_result_cb_history", columnDefinition = "NVARCHAR(255)")
  public String getColResultCbHistory() {
    return colResultCbHistory;
  }

  public void setColResultCbHistory(String colResultCbHistory) {
    this.colResultCbHistory = colResultCbHistory;
  }

  @Column(name = "col_result_call_process", columnDefinition = "NVARCHAR(255)")
  public String getColResultCallProcess() {
    return colResultCallProcess;
  }

  public void setColResultCallProcess(String colResultCallProcess) {
    this.colResultCallProcess = colResultCallProcess;
  }

  @NotBlank(message = "{campaign.db_result_column.blank}")
  @Column(name = "col_result_retry_date", columnDefinition = "NVARCHAR(255)")
  public String getColResultRetryDate() {
    return colResultRetryDate;
  }

  public void setColResultRetryDate(String colResultRetryDate) {
    this.colResultRetryDate = colResultRetryDate;
  }

  @Column(name = "col_result_unique_key", columnDefinition = "NVARCHAR(255)")
  public String getColResultUniqueKey() {
    return colResultUniqueKey;
  }

  public void setColResultUniqueKey(String colResultUniqueKey) {
    this.colResultUniqueKey = colResultUniqueKey;
  }

  @Column(name = "col_result_dnc_numbers", columnDefinition = "NVARCHAR(255)")
  public String getColResultDNCNumbers() {
    return colResultDNCNumbers;
  }

  public void setColResultDNCNumbers(String colResultDNCNumbers) {
    this.colResultDNCNumbers = colResultDNCNumbers;
  }

  /**
   * Müşteri bazlı DNC için opsiyonel customer_no kolon mapping'i. Boşsa
   * customer-scope akışları satır bazlı telefon fallback'iyle çalışır.
   */
  @Column(name = "col_result_customer_no", columnDefinition = "NVARCHAR(255)")
  public String getColResultCustomerNo() {
    return colResultCustomerNo;
  }

  public void setColResultCustomerNo(String colResultCustomerNo) {
    this.colResultCustomerNo = colResultCustomerNo;
  }

  /**
   * DNC delta-sync için opsiyonel date_entered kolon mapping'i. Boşsa
   * kampanya delta-sync kapsamına girmez.
   */
  @Column(name = "col_date_entered", columnDefinition = "NVARCHAR(255)")
  public String getColDateEntered() {
    return colDateEntered;
  }

  public void setColDateEntered(String colDateEntered) {
    this.colDateEntered = colDateEntered;
  }

  @Column(name = "col_result_attempts", columnDefinition = "NVARCHAR(255)", nullable = true)
  public String getColResultAttempts() {
    return colResultAttempts;
  }

  public void setColResultAttempts(String colResultAttempts) {
    this.colResultAttempts = colResultAttempts;
  }

  @Column(name = "col_result_callback_attempts", columnDefinition = "NVARCHAR(255)", nullable = true)
  public String getColResultCallbackAttempts() {
    return colResultCallbackAttempts;
  }

  public void setColResultCallbackAttempts(String colResultCallbackAttempts) {
    this.colResultCallbackAttempts = colResultCallbackAttempts;
  }

  @Column(name = "col_result_update_date", columnDefinition = "NVARCHAR(255)")
  public String getColResultUpdateDate() {
    return colResultUpdateDate;
  }

  public void setColResultUpdateDate(String colResultUpdateDate) {
    this.colResultUpdateDate = colResultUpdateDate;
  }

  @Column(name = "col_result_response_values", columnDefinition = "NVARCHAR(4000)")
  public String getColResultResponseValues() {
    return colResultResponseValues;
  }

  public void setColResultResponseValues(String colResultResponseValues) {
    this.colResultResponseValues = colResultResponseValues;
  }

  /**
   * Task 325559 — kaydın hangi nedenle kapatıldığını tutan result tablosu kolonunun fiziksel
   * adı (binding). Değer {@code Constants.CLOSE_REASON_*} sabitlerinden biridir ve yalnız
   * numara başına limit kaynaklı kapanışlarda yazılır. Kolon eşli değilse özellik çalışmaya
   * devam eder, yalnız kapanış nedeni kaydedilmez.
   */
  @Column(name = "col_result_close_reason")
  public String getColResultCloseReason() {
    return colResultCloseReason;
  }

  public void setColResultCloseReason(String colResultCloseReason) {
    this.colResultCloseReason = colResultCloseReason;
  }

  @Enumerated(EnumType.ORDINAL)
  @Column(name = "retry_strategy")
  public RETRY_STRATEGY getRetryStrategy() {
    return retryStrategy;
  }

  public void setRetryStrategy(RETRY_STRATEGY retryStrategy) {
    this.retryStrategy = retryStrategy;
  }

  @NotNull(message = "{campaign.caller_id_behaviour.blank}")
  @Enumerated(EnumType.ORDINAL)
  @Column(name = "caller_id_behaviour", nullable = false)
  public CALLER_ID_BEHAVIOUR getCallerIdBehaviour() {
    return callerIdBehaviour;
  }

  public void setCallerIdBehaviour(CALLER_ID_BEHAVIOUR callerIdBehaviour) {
    this.callerIdBehaviour = callerIdBehaviour;
  }

  @Size(message = "{campaign.caller_id.too_long}", max = 255)
  @Column(name = "caller_id", columnDefinition = "NVARCHAR(255)")
  public String getCallerId() {
    return callerId;
  }

  public void setCallerId(String callerId) {
    this.callerId = callerId;
  }

  @NotNull(message = "{campaign.timezone_type.blank}")
  @Enumerated(EnumType.ORDINAL)
  @Column(name = "timezone_type", nullable = false)
  public TIMEZONE_TYPE getTimezoneType() {
    return timezoneType;
  }

  public void setTimezoneType(TIMEZONE_TYPE timezoneType) {
    this.timezoneType = timezoneType;
  }

  @Column(name = "col_timezone_offset", columnDefinition = "NVARCHAR(255)")
  public String getColTimezoneOffset() {
    return colTimezoneOffset;
  }

  public void setColTimezoneOffset(String colTimezoneOffset) {
    this.colTimezoneOffset = colTimezoneOffset;
  }

  @Column(name = "col_call_from_time", columnDefinition = "NVARCHAR(255)")
  public String getColCallFromTime() {
    return colCallFromTime;
  }

  public void setColCallFromTime(String colCallFromTime) {
    this.colCallFromTime = colCallFromTime;
  }

  @Column(name = "col_call_to_time", columnDefinition = "NVARCHAR(255)")
  public String getColCallToTime() {
    return colCallToTime;
  }

  public void setColCallToTime(String colCallToTime) {
    this.colCallToTime = colCallToTime;
  }

  @Enumerated(EnumType.ORDINAL)
  @Column(name = "apply_filter_to")
  public APPLY_TO getApplyFilterTo() {
    return applyFilterTo;
  }

  public void setApplyFilterTo(APPLY_TO applyFilterTo) {
    this.applyFilterTo = applyFilterTo;
  }

  @Transient
  public boolean isApplyFilterRetries() {
    return APPLY_TO.RETRIES.equals(getApplyFilterTo()) || APPLY_TO.RETRIES_AND_CALLBACKS.equals(getApplyFilterTo());
  }

  @Transient
  public boolean isApplyFilterCallbacks() {
    return APPLY_TO.CALLBACKS.equals(getApplyFilterTo()) || APPLY_TO.RETRIES_AND_CALLBACKS.equals(getApplyFilterTo());
  }

  @Min(message = "{campaign.concurrent_lines.min}", value = 0)
  @Column(name = "concurrent_lines", nullable = false)
  public Integer getConcurrentLines() {
    return concurrentLines;
  }

  public void setConcurrentLines(Integer concurrentLines) {
    this.concurrentLines = concurrentLines;
  }

  @Min(message = "{campaign.calls_per_second.min}", value = 0)
  @Column(name = "calls_per_second", nullable = false)
  public Integer getCallsPerSecond() {
    return callsPerSecond;
  }

  public void setCallsPerSecond(Integer callsPerSecond) {
    this.callsPerSecond = callsPerSecond;
  }

  @Min(message = "{campaign.cache_limit.min}", value = 0)
  @Column(name = "cache_limit", nullable = false)
  public Integer getCacheLimit() {
    return cacheLimit;
  }

  public void setCacheLimit(Integer cacheLimit) {
    this.cacheLimit = cacheLimit;
  }

  @Min(message = "{campaign.ring_timeout.min}", value = 0)
  @Column(name = "ring_timeout", nullable = false)
  public Integer getRingTimeout() {
    return ringTimeout;
  }

  public void setRingTimeout(Integer ringTimeout) {
    this.ringTimeout = ringTimeout;
  }

  @Min(message = "{campaign.dialing_boost.min}", value = 0)
  @Max(message = "{campaign.dialing_boost.max}", value = 100)
  @Column(name = "dialing_boost", nullable = false)
  public Float getDialingBoost() {
    return dialingBoost;
  }

  public void setDialingBoost(Float dialingBoost) {
    this.dialingBoost = dialingBoost;
  }

  @Min(message = "{campaign.abandon_timeout.min}", value = 0)
  @Column(name = "abandon_timeout", nullable = false)
  public Integer getAbandonTimeout() {
    return abandonTimeout;
  }

  public void setAbandonTimeout(Integer abandonTimeout) {
    this.abandonTimeout = abandonTimeout;
  }

  @Size(message = "{campaign.abandon_ivr.size}", max = 255)
  @Column(name = "abandon_ivr", columnDefinition = "NVARCHAR(255)")
  public String getAbandonIvr() {
    return abandonIvr;
  }

  public void setAbandonIvr(String abandonIvr) {
    this.abandonIvr = abandonIvr;
  }

  @NotBlank(message = "{campaign.context.blank}")
  @Column(name = "context", nullable = false, columnDefinition = "NVARCHAR(255)")
  public String getContext() {
    return context;
  }

  public void setContext(String context) {
    this.context = context;
  }

  @Column(name = "amd_enabled", nullable = false)
  public Boolean getAmdEnabled() {
    return amdEnabled;
  }

  public void setAmdEnabled(Boolean amdEnabled) {
    this.amdEnabled = amdEnabled;
  }

  @Column(name = "retry_schedule_enabled", nullable = false)
  public Boolean getRetryScheduleEnabled() {
    return retryScheduleEnabled;
  }

  public void setRetryScheduleEnabled(Boolean retryScheduleEnabled) {
    this.retryScheduleEnabled = retryScheduleEnabled;
  }

  @Min(message = "{campaign.retry_overal_attempt.min}", value = 0)
  @Column(name = "retry_overal_attempt")
  public Integer getRetryOveralAttempt() {
    return retryOveralAttempt;
  }

  public void setRetryOveralAttempt(Integer retryOveralAttempt) {
    this.retryOveralAttempt = retryOveralAttempt;
  }

  @Min(message = "{campaign.macro_interval.min}", value = 0)
  @Column(name = "macro_interval")
  public Integer getMacroInterval() {
    return macroInterval;
  }

  public void setMacroInterval(Integer macroInterval) {
    this.macroInterval = macroInterval;
  }

  @Min(message = "{campaign.macro_max_attempt.min}", value = 0)
  @Column(name = "macro_max_attempt")
  public Integer getMacroMaxAttempt() {
    return macroMaxAttempt;
  }

  public void setMacroMaxAttempt(Integer macroMaxAttempt) {
    this.macroMaxAttempt = macroMaxAttempt;
  }

  /**
   * Task 325559 — numara başına azami arama limiti. Kayıttaki her bir telefon numarasının
   * kampanya süresince alabileceği mutlak azami arama sayısı. Genel deneme limiti
   * ({@link #getRetryOveralAttempt()}) ve macro aralık limitinden BAĞIMSIZ değerlendirilir;
   * üçü birlikte uygulanır ve kayıt hangisi önce dolarsa durur.
   *
   * <p>{@code null} veya {@code <= 0} ise özellik kapalıdır ve mevcut davranış birebir korunur.
   * {@code REACH_RETRY_LIMIT_BEFORE_NEXT_NUMBER} ve {@code REACH_RETRY_LIMIT_FOR_SINGLE_NUMBER}
   * stratejilerinde bu alan YOK SAYILIR; iki strateji de numara başına sınırı zaten
   * {@code retryOveralAttempt} ile uyguluyor.</p>
   */
  @Min(message = "{campaign.max_attempt_per_number.min}", value = 1)
  @Column(name = "max_attempt_per_number")
  public Integer getMaxAttemptPerNumber() {
    return maxAttemptPerNumber;
  }

  public void setMaxAttemptPerNumber(Integer maxAttemptPerNumber) {
    this.maxAttemptPerNumber = maxAttemptPerNumber;
  }

  /**
   * Task 325559 — numara başına limit bu kampanya + strateji için uygulanır mı? Kararın TEK
   * kaynağıdır; hem retry stratejileri ({@code RetryStrategy.isPerNumberLimitActive}) hem
   * {@code CallProducer.isNumberWithinLimit} bunu çağırır, mantık ikinci kez yazılmaz.
   *
   * <p>Alan {@code null} veya {@code <= 0} ise özellik kapalıdır. İki strateji muaftır ve alan
   * onlarda YOK SAYILIR:</p>
   *
   * <ul>
   * <li>{@code REACH_RETRY_LIMIT_BEFORE_NEXT_NUMBER} (K2): numara başına limiti zaten
   * {@code retryOveralAttempt} ile uyguluyor.</li>
   * <li>{@code REACH_RETRY_LIMIT_FOR_SINGLE_NUMBER} (karar 29.09.2026): tek numarayı arar;
   * {@code retryOveralAttempt} o numaranın sınırıdır. Limit uygulansaydı strateji, numara limitini
   * doldurunca diğer numaralara geçerek "tek numara" davranışını bozardı.</li>
   * </ul>
   *
   * <p>UI bu stratejilerde alanı freeze eder. Task 333220 ile UI, strateji 0 veya 1 seçildiğinde
   * ya da bu stratejideki bir kampanya formda açıldığında alanın değerini de temizler. Ancak
   * kopyalama gibi backend akışları ve formda yeniden kaydedilmemiş eski kayıtlar nedeniyle
   * değer DB'de kalabilir. Bu yüzden muafiyet UI'a bırakılmaz, burada uygulanır.</p>
   *
   * <p>Bilinçli olarak {@code static}: kararı iki katman da kampanyanın alanlarından okuyarak
   * sorar, böylece tek bir yerde durur ve entity yüzeyine (Hibernate/JSON) yeni bir property
   * eklemez.</p>
   *
   * @param maxAttemptPerNumber kampanyanın numara başına limiti
   * @param retryStrategy kampanyanın retry stratejisi
   */
  public static boolean isPerNumberAttemptLimitActive(Integer maxAttemptPerNumber, RETRY_STRATEGY retryStrategy) {
    if (maxAttemptPerNumber == null || maxAttemptPerNumber <= 0) {
      return false;
    }
    return !RETRY_STRATEGY.REACH_RETRY_LIMIT_BEFORE_NEXT_NUMBER.equals(retryStrategy)
        && !RETRY_STRATEGY.REACH_RETRY_LIMIT_FOR_SINGLE_NUMBER.equals(retryStrategy);
  }

  @Range(message = "{campaign.retry_ratio.range}", min = 0, max = 100)
  @Column(name = "retry_ratio")
  public Integer getRetryRatio() {
    return retryRatio;
  }

  public void setRetryRatio(Integer retryRatio) {
    this.retryRatio = retryRatio;
  }

  @Min(message = "{campaign.retry_delay.min}", value = 0)
  @Column(name = "retry_delay")
  public Double getRetryDelay() {
    return retryDelay;
  }

  public void setRetryDelay(Double retryDelay) {
    this.retryDelay = retryDelay;
  }

  @Column(name = "schedule_enabled", nullable = false)
  public Boolean getScheduleEnabled() {
    return scheduleEnabled;
  }

  public void setScheduleEnabled(Boolean scheduleEnabled) {
    this.scheduleEnabled = scheduleEnabled;
  }

  @Column(name = "dnc_enabled", nullable = false)
  public Boolean getDncEnabled() {
    return dncEnabled;
  }

  public void setDncEnabled(Boolean dncEnabled) {
    this.dncEnabled = dncEnabled;
  }

  @Column(name = "expire_filter_enabled", nullable = false, columnDefinition = "BIT DEFAULT 0")
  public Boolean getExpireFilterEnabled() {
    return expireFilterEnabled;
  }

  public void setExpireFilterEnabled(Boolean expireFilterEnabled) {
    this.expireFilterEnabled = expireFilterEnabled;
  }

  @Column(name = "lsc_enabled", nullable = false)
  public Boolean getLscEnabled() {
    return lscEnabled;
  }

  public void setLscEnabled(Boolean lscEnabled) {
    this.lscEnabled = lscEnabled;
  }

  @Column(name = "full_contact_list_check_enabled", nullable = false, columnDefinition = "BIT DEFAULT 0")
  public Boolean getFullContactListCheckEnabled() {
    return fullContactListCheckEnabled;
  }

  public void setFullContactListCheckEnabled(Boolean fullContactListCheckEnabled) {
    this.fullContactListCheckEnabled = fullContactListCheckEnabled;
  }

  @Column(name = "col_skill", columnDefinition = "NVARCHAR(255)")
  public String getColSkill() {
    return colSkill;
  }

  public void setColSkill(String colSkill) {
    this.colSkill = colSkill;
  }

  @Column(name = "skill_enabled", nullable = false)
  public Boolean getSkillEnabled() {
    return skillEnabled;
  }

  public void setSkillEnabled(Boolean skillEnabled) {
    this.skillEnabled = skillEnabled;
  }

  @Column(name = "startup_db_check")
  public Boolean getStartupDbCheck() {
    return startupDbCheck;
  }

  public void setStartupDbCheck(Boolean startupDbCheck) {
    this.startupDbCheck = startupDbCheck;
  }

  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(name = "pd_campaign_skill", joinColumns = {
      @JoinColumn(name = "campaign_id", nullable = false) }, inverseJoinColumns = @JoinColumn(name = "skill_id", nullable = false))
  @Fetch(FetchMode.SUBSELECT)
  @Where(clause = "deleted = 0")
  public Set<Skill> getSkills() {
    return skills;
  }

  public void setSkills(Set<Skill> skills) {
    this.skills = skills;
  }

  @Transient
  public String getStringifiedSkills() {
    if (!CollectionUtils.isEmpty(getSkills())) {
      return getSkills().stream().map(Skill::getName).collect(Collectors.joining(","));
    }
    return null;
  }

  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(name = "pd_campaign_location", joinColumns = {
      @JoinColumn(name = "campaign_id", nullable = false) }, inverseJoinColumns = @JoinColumn(name = "location_id", nullable = false))
  @Fetch(FetchMode.SUBSELECT)
  @Where(clause = "deleted = 0")
  public Set<Location> getLocations() {
    return locations;
  }

  public void setLocations(Set<Location> locations) {
    this.locations = locations;
  }

  @Valid
  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(name = "pd_campaign_dnc_list", joinColumns = {
      @JoinColumn(name = "campaign_id", nullable = false) }, inverseJoinColumns = @JoinColumn(name = "dnc_list_id", nullable = false))
  @Fetch(FetchMode.SUBSELECT)
  @Where(clause = "deleted = 0")
  public Set<DNC> getDncs() {
    return dncs;
  }

  public void setDncs(Set<DNC> dncs) {
    this.dncs = dncs;
  }

  @OneToOne(cascade = { CascadeType.ALL }, fetch = FetchType.EAGER, mappedBy = "campaign", optional = false)
  public DNC getDnc() {
    return dnc;
  }

  public void setDnc(DNC dnc) {
    this.dnc = dnc;
  }

  @Valid
  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(name = "pd_campaign_lsc_service", joinColumns = {
  @JoinColumn(name = "campaign_id", nullable = false) }, inverseJoinColumns = @JoinColumn(name = "lsc_service_id", nullable = false))
  @Fetch(FetchMode.SUBSELECT)
  public List<LSC> getLscs() {
    return lscs;
  }

  public void setLscs(List<LSC> lscs) {
    this.lscs = lscs;
  }

  @Valid
  @OneToMany(cascade = { CascadeType.ALL }, fetch = FetchType.LAZY, mappedBy = "campaign")
  @Fetch(FetchMode.SUBSELECT)
  @Where(clause = "deleted = 0")
  public List<PhoneNumberColumn> getPhoneNumberColumns() {
    return phoneNumberColumns;
  }

  @Transient
  public List<PhoneNumberColumn> getEnabledPhoneNumberColumns() {
    return phoneNumberColumns.stream()
        .filter(BaseEntity::isEnabled)
        .sorted(Comparator.comparingInt(PhoneNumberColumn::getColSort))
        .collect(Collectors.toList());
  }

  @Transient
  public int getPhoneNumberColumnOrder(PhoneNumberColumn numberColumn) {
    for (int i = 0; i < phoneNumberColumns.size(); i++) {
      if (phoneNumberColumns.get(i).equals(numberColumn))
        return i;
    }
    return -1;
  }

  public void setPhoneNumberColumns(List<PhoneNumberColumn> phoneNumberColumns) {
    this.phoneNumberColumns = phoneNumberColumns;
  }

  @Valid
  @OneToMany(cascade = { CascadeType.ALL }, fetch = FetchType.LAZY, mappedBy = "campaign")
  @Fetch(FetchMode.SUBSELECT)
  @Where(clause = "deleted = 0")
  public List<SortField> getSortFields() {
    return sortFields;
  }

  public void setSortFields(List<SortField> sortFields) {
    this.sortFields = sortFields;
  }

  @OneToMany(cascade = { CascadeType.ALL }, fetch = FetchType.LAZY, mappedBy = "campaign")
  @Fetch(FetchMode.SUBSELECT)
  @Where(clause = "deleted = 0")
  public List<CampaignGroup> getCampaignGroups() {
    return campaignGroups;
  }

  @Transient
  public List<CampaignGroup> getEnabledCampaignGroups() {
    return campaignGroups.stream()
        .filter(BaseEntity::isEnabled)
        .collect(Collectors.toList());
  }

  public void setCampaignGroups(List<CampaignGroup> campaignGroups) {
    this.campaignGroups = campaignGroups;
  }

  @Valid
  @OneToMany(cascade = { CascadeType.ALL }, fetch = FetchType.LAZY, mappedBy = "campaign")
  @Fetch(FetchMode.SUBSELECT)
  @Where(clause = "deleted = 0")
  public List<ResultCodeSchedule> getResultCodeSchedules() {
    return resultCodeSchedules;
  }

  public void setResultCodeSchedules(List<ResultCodeSchedule> resultCodeSchedules) {
    this.resultCodeSchedules = resultCodeSchedules;
  }

  @OneToMany(cascade = { CascadeType.ALL }, fetch = FetchType.LAZY, mappedBy = "campaign")
  @Fetch(FetchMode.SUBSELECT)
  @Where(clause = "deleted = 0")
  public List<Schedule> getSchedules() {
    return schedules;
  }
  @OneToMany(cascade = { CascadeType.ALL }, fetch = FetchType.LAZY, mappedBy = "campaign")
  @Fetch(FetchMode.SUBSELECT)
  @Where(clause = "deleted = 0")
  public List<ScheduleBlackoutAutomation> getBlackoutSchedules() {
    return blackoutSchedules;
  }

  public void setBlackoutSchedules(List<ScheduleBlackoutAutomation> schedulesBlackoutService) {
    this.blackoutSchedules = schedulesBlackoutService;
  }

  @Transient
  public List<Schedule> getEnabledSchedules() {
    return schedules.stream()
        .filter(BaseEntity::isEnabled)
        .collect(Collectors.toList());
  }

  public void setSchedules(List<Schedule> schedules) {
    this.schedules = schedules;
  }

  @OneToMany(cascade = { CascadeType.ALL }, fetch = FetchType.LAZY, mappedBy = "campaign")
  @Fetch(FetchMode.SUBSELECT)
  @Where(clause = "deleted = 0")
  public List<PredefinedValue> getPredefinedValues() {
    return predefinedValues;
  }

  public void setPredefinedValues(List<PredefinedValue> predefinedValues) {
    this.predefinedValues = predefinedValues;
  }

  @Column(name = "stop_condition_enabled")
  public Boolean getStopConditionEnabled() {
    return stopConditionEnabled;
  }

  public void setStopConditionEnabled(Boolean stopConditionEnabled) {
    this.stopConditionEnabled = stopConditionEnabled;
  }

  @Column(name = "stop_condition_parameter")
  @Enumerated(EnumType.ORDINAL)
  public Constants.STOP_CONDITION_PARAMETER getStopConditionParameter() {
    return stopConditionParameter;
  }

  public void setStopConditionParameter(Constants.STOP_CONDITION_PARAMETER stopConditionParameter) {
    this.stopConditionParameter = stopConditionParameter;
  }

  @Column(name = "stop_condition_value")
  public Long getStopConditionValue() {
    return stopConditionValue;
  }

  public void setStopConditionValue(Long stopConditionValue) {
    this.stopConditionValue = stopConditionValue;
  }

  @Column(name = "stop_condition_threshold")
  public Long getStopConditionThreshold() {
    return stopConditionThreshold;
  }

  public void setStopConditionThreshold(Long stopConditionThreshold) {
    this.stopConditionThreshold = stopConditionThreshold;
  }

  @Column(name = "max_answer_rate")
  public Float getMaxAnswerRate() {
    return maxAnswerRate;
  }
  public void setMaxAnswerRate(Float maxAnswerRate) { this.maxAnswerRate = maxAnswerRate; }

  @Column(name = "max_abandon_rate")
  public Float getMaxAbandonRate() {
    return maxAbandonRate;
  }
  public void setMaxAbandonRate(Float maxAbandonRate) {
    this.maxAbandonRate = maxAbandonRate;
  }

  @Column(name = "gamma_correction_factor")
  public Double getGammaCorrectionFactor() { return gammaCorrectionFactor; }
  public void setGammaCorrectionFactor(Double gammaCorrectionFactor) { this.gammaCorrectionFactor = gammaCorrectionFactor; }

  @Column(name = "gamma_abandon_sensitivity")
  public Double getGammaAbandonSensitivity() { return gammaAbandonSensitivity; }
  public void setGammaAbandonSensitivity(Double gammaAbandonSensitivity) { this.gammaAbandonSensitivity = gammaAbandonSensitivity; }

  @Column(name = "gamma_window_capacity")
  public Integer getGammaWindowCapacity() { return gammaWindowCapacity; }
  public void setGammaWindowCapacity(Integer gammaWindowCapacity) { this.gammaWindowCapacity = gammaWindowCapacity; }

  @Column(name = "gamma_required_window_size")
  public Integer getGammaRequiredWindowSize() { return gammaRequiredWindowSize; }
  public void setGammaRequiredWindowSize(Integer gammaRequiredWindowSize) { this.gammaRequiredWindowSize = gammaRequiredWindowSize; }

  @Column(name = "gamma_plus", columnDefinition = "BIT DEFAULT 0")
  public Boolean getGammaPlus() { return gammaPlus; }
  public void setGammaPlus(Boolean gammaPlus) { this.gammaPlus = gammaPlus; }

  @Transient
  public AtomicInteger getInCalls() {
    return inCalls;
  }

  public void setInCalls(AtomicInteger inCalls) {
    this.inCalls = inCalls;
  }

  private String toInCallEntry(Call call) {
    return "callId=" + call.getId() + " calledNumber=" + call.getCalledNumber();
  }

  public void incrementInCallsSize(Call call) {
    this.inCalls.incrementAndGet();
    LOG.info("In call size incremented by call: {} inCallSize: {} ", call, this.inCalls.get());
  }

  public void decrementInCallsSize(Call call) {
    if (this.inCalls.get() > 0) {
      this.inCalls.decrementAndGet();
      LOG.info("In call size decremented by call: {} inCallSize: {} ", call, this.inCalls.get());
    }
  }

  @Enumerated(EnumType.ORDINAL)
  @Column(name = "predictive_strategy", nullable = true)
  public Constants.PREDICTIVE_STRATEGY getPredictiveStrategy() {
    return predictiveStrategy;
  }


  public void setPredictiveStrategy(Constants.PREDICTIVE_STRATEGY predictiveStrategy) {
    this.predictiveStrategy = predictiveStrategy;
  }

  @Transient
  public CAMPAIGN_STATE getState() {
    return state;
  }

  public void setState(CAMPAIGN_STATE state) {
    this.state = state;
  }

  @Transient
  public AtomicInteger getAgentsSize() {
    return agentsSize;
  }

  public void setAgentsSize(int agentsSize) {
    this.agentsSize.set(agentsSize);
  }

  public void decrementAgentsSize() {
    if (this.agentsSize.get() > 0)
      this.agentsSize.decrementAndGet();
  }

  public void incrementAgentsSize() {
    this.agentsSize.incrementAndGet();
  }

  @Transient
  public AtomicInteger getAvailAgentsSize() {
    return availAgentsSize;
  }

  public void setAvailAgentsSize(int availAgentsSize) {
    this.availAgentsSize.set(availAgentsSize);
  }

  public void decrementAvailAgentsSize() {
    if (this.availAgentsSize.get() > 0)
      this.availAgentsSize.decrementAndGet();
  }

  public void incrementAvailAgentsSize() {
    this.availAgentsSize.incrementAndGet();
  }

  @Transient
  public AtomicInteger getIncallAgents() {
    return incallAgents;
  }

  public void setIncallAgents(int incallAgents) {
    this.incallAgents.set(incallAgents);
  }

  @Transient
  public int getTotalListSize() {
    return totalListSize;
  }

  public void setTotalListSize(int totalListSize) {
    this.totalListSize = totalListSize;
  }

  @Transient
  public int getActualListSize() {
    return actualListSize;
  }

  public void setActualListSize(int actualListSize) {
    this.actualListSize = actualListSize;
  }

  @Transient
  public int getRetryListSize() {
    return retryListSize;
  }

  public void setRetryListSize(int retryListSize) {
    this.retryListSize = retryListSize;
  }

  @Transient
  public int getCallbackListSize() {
    return callbackListSize;
  }

  public void setCallbackListSize(int callbackListSize) {
    this.callbackListSize = callbackListSize;
  }

  @Transient
  public int getFreshDataListSize() {
    return freshDataListSize;
  }

  public void setFreshDataListSize(int freshDataListSize) {
    this.freshDataListSize = freshDataListSize;
  }

  @Transient
  public int getContactsToHandleAmount() {
    return contactsToHandleAmount;
  }

  public void setContactsToHandleAmount(int contactsToHandleAmount) {
    this.contactsToHandleAmount = contactsToHandleAmount;
  }

  @Transient
  public AtomicInteger getAbandonedCallsSize() {
    return abandonedCallsSize;
  }

  public void setAbandonedCallsSize(int abandonedCallsSize) {
    this.abandonedCallsSize.set(abandonedCallsSize);
  }

  public void incrementAbandonedCallsSize() {
    this.abandonedCallsSize.incrementAndGet();
  }

  @Transient
  public double getAbandonedCallsRatio() {
    abandonedCallsRatio = getCallsMade().get() == 0 ? 0d : getAbandonedCallsSize().doubleValue() * 100d / getCallsMade().get();
    return abandonedCallsRatio;
  }

  public void setAbandonedCallsRatio(double abandonedCallsRatio) {
    this.abandonedCallsRatio = abandonedCallsRatio;
  }

  @Transient
  public AtomicInteger getListRemaining() {
    return listRemaining;
  }

  public void setListRemaining(int listRemaining) {
    this.listRemaining.set(listRemaining);
  }

  public void decrementListRemaining() {
    if (listRemaining.get() > 0)
      listRemaining.decrementAndGet();
  }

  @Transient
  public AtomicInteger getCallsMade() {
    return callsMade;
  }

  public void setCallsMade(int callsMade) {
    this.callsMade.set(callsMade);
  }

  public void incrementCallsMade() {
    callsMade.incrementAndGet();
  }

  @Transient
  public AtomicInteger getActiveCalls() {
    return activeCalls;
  }

  public void setActiveCalls(int activeCalls) {
    this.activeCalls.set(activeCalls);
  }

  public void incrementActiveCalls(Call call) {
    activeCalls.incrementAndGet();
    LOG.info("Active calls incremented by " + call);
  }

  public void decrementActiveCalls(Call call) {
    if (activeCalls.get() > 0) {
      activeCalls.decrementAndGet();
      LOG.info("Active calls decremented by " + call);
    }

    notifyWhenThereIsNoCallsInProgress();
  }

  @Transient
  public AtomicInteger getLiveCalls() {
    return liveCalls;
  }

  public void setLiveCalls(int liveCalls) {
    this.liveCalls.set(liveCalls);
  }

  public void incrementLiveCalls() {
    liveCalls.incrementAndGet();
  }

  @Transient
  public AtomicInteger getUniqueLiveCalls() {
    return uniqueLiveCalls;
  }

  public void setUniqueLiveCalls(int uniqueLiveCalls) {
    this.uniqueLiveCalls.set(uniqueLiveCalls);
  }

  public void incrementUniqueLiveCalls() {
    this.uniqueLiveCalls.getAndIncrement();
  }

  @Transient
  public AtomicInteger getRingingCalls() {
    return ringingCalls;
  }

  @Transient
  public AtomicInteger getRingingCbCalls() { return ringingCbCalls; }

  @Transient
  public AtomicInteger getRingingRetryCalls() { return ringingRetryCalls; }

  @Transient
  public AtomicInteger getRingingFreshCalls() { return ringingFreshCalls; }

  public void setRingingCalls(int ringingCalls) {
    this.ringingCalls.set(ringingCalls);
  }

  public void incrementRingingCalls(Call call) {
    ringingCalls.incrementAndGet();
    incrementRingingCallsByType(call);
    LOG.info("Ringing calls incremented by " + call);
  }

  public void decrementRingingCalls(Call call) {
    if (ringingCalls.get() > 0) {
      ringingCalls.decrementAndGet();
      decrementRingingCallsByType(call);
      LOG.info("Ringing calls decremented by " + call);
    }

    notifyWhenThereIsNoCallsInProgress();
  }

  private void incrementRingingCallsByType(Call call) {
    if (call == null || call.getCallType() == null) return;
    switch (call.getCallType()) {
      case CALLBACK: ringingCbCalls.incrementAndGet(); break;
      case RETRY: ringingRetryCalls.incrementAndGet(); break;
      case FRESH: ringingFreshCalls.incrementAndGet(); break;
      default: break;
    }
  }

  private void decrementRingingCallsByType(Call call) {
    if (call == null || call.getCallType() == null) return;
    switch (call.getCallType()) {
      case CALLBACK: if (ringingCbCalls.get() > 0) ringingCbCalls.decrementAndGet(); break;
      case RETRY: if (ringingRetryCalls.get() > 0) ringingRetryCalls.decrementAndGet(); break;
      case FRESH: if (ringingFreshCalls.get() > 0) ringingFreshCalls.decrementAndGet(); break;
      default: break;
    }
  }

  @Transient
  public AtomicInteger getNoConnectionCalls() {
    return noConnectionCalls;
  }

  public void setNoConnectionCalls(int noConnectionCalls) {
    this.noConnectionCalls.set(noConnectionCalls);
  }

  public void incrementNoConnectionCalls() {
    noConnectionCalls.incrementAndGet();
  }

  @Transient
  public AtomicLong getTotalCallDuration() {
    return totalCallDuration;
  }

  public void setTotalCallDuration(long totalCallDuration) {
    this.totalCallDuration.set(totalCallDuration);
  }

  public void incrementTotalCallDuration(long callDuration) {
    totalCallDuration.addAndGet(callDuration);
  }

  @Transient
  public Set<String> getCallsInProgress() {
    return callsInProgress;
  }

  @Transient
  public int getCallsInProgressCount() {
    return callsInProgress.size();
  }

  public void addCallInProgress(Call call) {
    callsInProgress.add(call.getId());
    LOG.info("Call in progress added: {}. Total: {}", call, getCallsInProgressCount());
  }

  public void removeCallInProgress(Call call) {
    callsInProgress.remove(call.getId());
    LOG.info("Call in progress removed: {}. Total: {}", call, getCallsInProgressCount());
  }

  public void clearCallsInProgress() {
    callsInProgress.clear();
    LOG.info("Call in progress cleaned. Total: {}", getCallsInProgressCount());
  }

  @Transient
  public long getRunoutEstimation() {
    int listRemainingCurrent = listRemaining.get();
    int liveCallsCurrent = liveCalls.get() - liveCallsFromLastStarting;
    int ringingCallsCurrent = ringingCalls.get();
    int activeCallsCurrent = activeCalls.get();
    if (startedDate == null) {
      return 0L;
    }
    long totalCampaignDurationCurrent = new Date().getTime() - startedDate.getTime();

    if (listRemainingCurrent > 0 && totalCampaignDurationCurrent > 0 && liveCallsCurrent > 0) {
      long averageCallDuration = totalCampaignDurationCurrent / liveCallsCurrent;
      runoutEstimation = averageCallDuration * (state == CAMPAIGN_STATE.STOPPING ? (ringingCallsCurrent + activeCallsCurrent) : listRemainingCurrent);
      return runoutEstimation;
    }
    return 0L;
  }

  public void setRunoutEstimation(long runoutEstimation) {
    this.runoutEstimation = runoutEstimation;
  }

  @Transient
  public AtomicInteger getRetriesMade() {
    return retriesMade;
  }

  public void setRetriesMade(int retriesMade) {
    this.retriesMade = new AtomicInteger(retriesMade);
  }

  public void incrementRetriesMade() {
    retriesMade.incrementAndGet();
  }

  @Transient
  public int getCacheSize() {
    return cacheSize;
  }

  public void setCacheSize(int cacheSize) {
    this.cacheSize = cacheSize;
  }

  @Transient
  public int getQueueSize() {
    return queueSize;
  }

  public void setQueueSize(int queueSize) {
    this.queueSize = queueSize;
  }

  @Transient
  public int getCompletionPercent() {
    int listRemainingCurrent = listRemaining.get();
    completionPercent = listRemainingCurrent > 0 && actualListSize > 0 ? Double.valueOf(Math.floor(100F - ((listRemainingCurrent * 100F) / actualListSize))).intValue() : 100;
    return completionPercent;
  }

  public void setCompletionPercent(int completionPercent) {
    this.completionPercent = completionPercent;
  }

  @Transient
  public double getDbEfficiency() {
    dbEfficiency = actualListSize > 0 ? uniqueLiveCalls.doubleValue() * 100d / actualListSize : 0d;
    return dbEfficiency;
  }

  public void setDbEfficiency(double dbEfficiency) {
    this.dbEfficiency = dbEfficiency;
  }

  @Transient
  public double getAnswerRate() {
    double liveCallCurrent = liveCalls.get();
    double callsMadeCurrent = callsMade.get();
    answerRate = liveCallCurrent > 0 && callsMadeCurrent > 0 ? liveCallCurrent * 100d / callsMadeCurrent : 0d;
    return answerRate;
  }

  public void setAnswerRate(double answerRate) {
    this.answerRate = answerRate;
  }

  @Transient
  public CampaignStopJob getCampaignStopJob() {
    return campaignStopJob;
  }

  public void setCampaignStopJob(CampaignStopJob campaignStopJob) {
    this.campaignStopJob = campaignStopJob;
  }

  @Transient
  public Cache<String, Call> getOriginatedCalls() {
    return originatedCalls;
  }

  public void addToOriginatedCalls(Call call) {
    originatedCalls.put(call.getId(), call);
  }

  @Column(name = "moh_class_calling_party", columnDefinition = "NVARCHAR(255)")
  public String getMohClassCallingParty() {
    return mohClassCallingParty;
  }

  public void setMohClassCallingParty(String mohClassCallingParty) {
    this.mohClassCallingParty = mohClassCallingParty;
  }

  @Transient
  public Map<Long, Long> getCallsByUserResultCode() {
    return callsByUserResultCode;
  }

  public void setCallsByUserResultCode(Map<Long, Long> callsByUserResultCode) {
    this.callsByUserResultCode = callsByUserResultCode;
  }

  @Transient
  public Map<Long, Long> getCallsByTelephonyResultCode() {
    return callsByTelephonyResultCode;
  }

  public void setCallsByTelephonyResultCode(Map<Long, Long> callsByTelephonyResultCode) {
    this.callsByTelephonyResultCode = callsByTelephonyResultCode;
  }

  @Transient
  public Long getTouchedContacts() {
    return touchedContacts.get();
  }

  public void setTouchedContacts(Long touchedContacts) {
    this.touchedContacts.set(touchedContacts != null ? touchedContacts : 0L);
  }

  public void incrementTouchedContacts() {
    touchedContacts.incrementAndGet();
  }

  @Transient
  public Long getCompletedContacts() {
    return completedContacts.get();
  }

  public void setCompletedContacts(Long completedContacts) {
    this.completedContacts.set(completedContacts != null ? completedContacts : 0L);
  }

  @OneToMany(cascade = { CascadeType.ALL }, fetch = FetchType.LAZY, mappedBy = "campaign")
  @Fetch(FetchMode.SUBSELECT)
  @Where(clause = "deleted = 0")
  public List<ScheduleAutomation> getScheduleAutomations() {
    return scheduleAutomations;
  }

  public void setScheduleAutomations(List<ScheduleAutomation> scheduleAutomations) {
    this.scheduleAutomations = scheduleAutomations;
  }

  @Column(name = "automation_enabled", nullable = false)
  public Boolean getScheduleAutomationEnabled() {
    return scheduleAutomationEnabled;
  }

  public void setScheduleAutomationEnabled(Boolean scheduleAutomationEnabled) {
    this.scheduleAutomationEnabled = scheduleAutomationEnabled;
  }

  @Enumerated(EnumType.ORDINAL)
  @Column(name = "stop_type")
  public Constants.CAMPAIGN_STOP_TYPE getStopType() {
    return stopType;
  }

  public void setStopType(Constants.CAMPAIGN_STOP_TYPE stopType) {
    this.stopType = stopType;
  }


  @Enumerated(EnumType.ORDINAL)
  @Column(name = "stop_type_history")
  public Constants.CAMPAIGN_STOP_TYPE getStopTypeHistory() {
    return stopTypeHistory;
  }

  public void setStopTypeHistory(Constants.CAMPAIGN_STOP_TYPE stopTypeHistory) {
    this.stopTypeHistory = stopTypeHistory;
  }

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "callerid_pool_id")
  public RoutingGroup getCallerIdPool() {
    return callerIdPool;
  }

  public void setCallerIdPool(RoutingGroup callerIdPool) {
    this.callerIdPool = callerIdPool;
  }

  @JsonIgnore
  @Column(name = "db_priority_table")
  public String getDbPriorityTable() {
    return dbPriorityTable;
  }

  public void setDbPriorityTable(String dbPriorityTable) {
    this.dbPriorityTable = dbPriorityTable;
  }

  @Column(name = "allow_api_control", nullable = false)
  public Boolean getAllowApiControl() {
    return allowApiControl;
  }

  public void setAllowApiControl(Boolean allowApiControl) {
    this.allowApiControl = allowApiControl;
  }

  @Column(name = "count_all_call_types", columnDefinition = "BIT DEFAULT 0")
  public Boolean getCountAllCallTypes() {
    return countAllCallTypes;
  }

  public void setCountAllCallTypes(Boolean countAllCallTypes) {
    this.countAllCallTypes = countAllCallTypes;
  }

  @Column(name = "api_token", columnDefinition = "NVARCHAR(36)")
  public String getApiToken() {
    return apiToken;
  }

  public void setApiToken(String apiToken) {
    this.apiToken = apiToken;
  }

  @Column(name = "retry_caller_id_behavior", nullable = true)
  @Enumerated(EnumType.ORDINAL)
  public Constants.RETRY_CALLER_ID_BEHAVIOR getRetryCallerIdBehavior() {
    return retryCallerIdBehavior;
  }

  public void setRetryCallerIdBehavior(Constants.RETRY_CALLER_ID_BEHAVIOR retryCallerIdBehavior) {
    this.retryCallerIdBehavior = retryCallerIdBehavior;
  }

  @Column(name = "fct_routing_enabled", nullable = false)
  public boolean isFctRoutingEnabled() {
    return fctRoutingEnabled;
  }

  public void setFctRoutingEnabled(boolean fctRoutingEnabled) {
    this.fctRoutingEnabled = fctRoutingEnabled;
  }

  public void incrementCompletedContacts() {
    completedContacts.incrementAndGet();
  }

  public void incrementUserResultCode(Long resultCodeId) {
    if (resultCodeId != null)
      callsByUserResultCode.merge(resultCodeId, 1L, Long::sum);
  }

  public void incrementTelephonyResultCode(Long resultCodeId) {
    if (resultCodeId != null)
      callsByTelephonyResultCode.merge(resultCodeId, 1L, Long::sum);
  }

  public void clearCall(Call call) {
    originatedCalls.invalidate(call.getId());
  }

  public boolean hasCallsInProgress() {
    return originatedCalls.size() > 0;
  }

  private void notifyWhenThereIsNoCallsInProgress() {
    if (state == CAMPAIGN_STATE.STOPPING && !hasCallsInProgress() && campaignStopJob != null)
      synchronized (campaignStopJob) {
        campaignStopJob.notify();
      }
  }

  public void reset() {
    this.agentsSize.set(0);
    this.availAgentsSize.set(0);
    this.abandonedCallsRatio = 0d;
    this.abandonedCallsSize.set(0);
    this.retryListSize = 0;
    this.callbackListSize = 0;
    this.freshDataListSize = 0;
    this.cacheSize = 0;
    this.queueSize = 0;
    this.completionPercent = 0;
    this.dbEfficiency = 0;
    this.answerRate = 0;
    this.runoutEstimation = 0L;
    this.listRemaining = new AtomicInteger();
    this.callsMade = new AtomicInteger();
    this.activeCalls = new AtomicInteger();
    this.incallAgents = new AtomicInteger();
    this.liveCalls = new AtomicInteger();
    this.liveCallsFromLastStarting = 0;
    this.startedDate = new Date();
    this.ringingCalls = new AtomicInteger();
    this.ringingCbCalls = new AtomicInteger();
    this.ringingRetryCalls = new AtomicInteger();
    this.ringingFreshCalls = new AtomicInteger();
    this.noConnectionCalls = new AtomicInteger();
    this.retriesMade = new AtomicInteger();
    this.uniqueLiveCalls = new AtomicInteger();
    this.callsByUserResultCode = new ConcurrentHashMap<>();
    this.callsByTelephonyResultCode = new ConcurrentHashMap<>();
    this.touchedContacts = new AtomicLong();
    this.completedContacts = new AtomicLong();
  }

  public boolean canOriginateMoreCalls() {
    return getCallsInProgressCount() < concurrentLines;
  }

  @Transient
  public boolean isPreviewCampaign() {
    return Constants.CAMPAIGN_TYPE.PREVIEW_FREE.equals(getType()) || Constants.CAMPAIGN_TYPE.PREVIEW_STRAIGHT.equals(getType());
  }

  @Transient
  public boolean isPredictiveCampaign() {
    return CAMPAIGN_TYPE.PREDICTIVE.equals(getType());
  }

  @Transient
  public boolean isGammaPredictiveCampaign() {
    return isPredictiveCampaign() && Constants.PREDICTIVE_STRATEGY.GAMMA.equals(getPredictiveStrategy());
  }

  /**
   * Gamma Plus (bulgular paketi) davranışlarının bu kampanyada devrede olup olmadığı.
   *
   * <p>Bayrak KAPALI iken gamma hesabının tek satırı bile değişmez; açıkken MADDE 0-5
   * düzeltmeleri devreye girer. Bayrak kampanya çalışırken değiştirilmez, stop/start'ta
   * okunur (tick periyodu da consumer başlarken bir kez okunuyor).
   *
   * <p>Savunmacı okuma: kolon NOT NULL olsa da eski satır/mock/DTO yolunda null gelebilir;
   * null daima "kapalı" sayılır.
   */
  @Transient
  public boolean isGammaPlusEnabled() {
    return Boolean.TRUE.equals(getGammaPlus());
  }

  @Transient
  public boolean isAlphaPredictiveCampaign() {
    return isPredictiveCampaign() && Constants.PREDICTIVE_STRATEGY.ALPHA.equals(getPredictiveStrategy());
  }

  @Transient
  public boolean isProgressiveReleasedCampaign() {
    return CAMPAIGN_TYPE.PROGRESSIVE_RELEASED.equals(getType());
  }

  @Transient
  public boolean isOutboundIvrCampaign() {
    return CAMPAIGN_TYPE.OUTBOUND_IVR.equals(getType());
  }

  @Transient
  public ScheduleAutomation getScheduleAutomationByType(Constants.SCHEDULE_ACTION_TYPE actionType) {
    return getScheduleAutomations().stream()
        .filter(ScheduleAutomation::isEnabled)
        .filter(scheduleAutomation -> scheduleAutomation.getActionType() == actionType)
        .findFirst().orElse(null);
  }

  @Transient
  public boolean hasSameSourceAndResultTable() {
    return dbListTable != null && dbListTable.equals(dbResultTable);
  }

  @Transient
  public String getColPriorityUniqueKey() {
    return Constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.UNIQUE_KEY.getValue();
  }

  @Transient
  public String getColPriorityField(int i) {
    return Constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.PRIORITY.getValue() + i;
  }

  /**
   * This method is used to obtain escaped column name, regarding the DB type.
   * See {@link com.powerdialer.aop.EscapeSqlNamesAnnotationProcessor}
   * @param sortField the sort field to get name from
   * @return column name escaped if needed
   */
  @Transient
  public String getColSortField(SortField sortField) {
    return sortField.getName();
  }

  @Enumerated(EnumType.ORDINAL)
  @Column(name = "lsc_timeout_behaviour")
  public Constants.LSC_TIMEOUT_BEHAVIOR getLscTimeoutBehavior() {
    return lscTimeoutBehavior;
  }

  public void setLscTimeoutBehavior(Constants.LSC_TIMEOUT_BEHAVIOR lscTimeoutBehavior) {
    this.lscTimeoutBehavior = lscTimeoutBehavior;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof Campaign)) {
      return false;
    }
    final Campaign obj = (Campaign) o;
    if (getId() == null && obj.getId() == null)
      return false;
    return generateEquals(getId(), obj.getId());
  }

  @Override
  public int hashCode() {
    return generateHash(31, 42, getId());
  }

  @Override
  public String toString() {
    return "Campaign [id=" + id + ", name=" + name + "]";
  }

  @Override
  public Campaign clone() {
    return SerializationUtils.clone(this);
  }

}
