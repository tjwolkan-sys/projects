package com.powerdialer.dto;

import java.util.Date;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.dozer.Mapping;

import com.powerdialer.util.Constants;

public class CampaignFormDTO extends CampaignListDTO {

  private Date startDate;
  private Date endDate;
  private Date stoppedDate;
  private Long ivrApplication;
  private Long ivrApplicationNumber;
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
  private Integer appointerResetStrategy;
  private Integer agentCbRescheduleDelayMin;
  private Integer agentCbConvertAfterAttempts;
  private Integer endListBehaviour;
  private Integer lowCacheSelectInterval;
  private String dbConnectionString;
  private String dbUsername;
  private String dbPassword;
  private String dbListTable;
  private String colUniqueKey;
  private String colSortBy;
  private Integer dbListSortDirection;
  private String dbResultTable;
  private String colResultCalledNumber;
  private String colResultCallbackDate;
  private String colResultTelephonyRc;
  private String colResultUserRc;
  private String colResultRcSequence;
  private String colResultUserId;
  private String colResultCallbackNumber;
  private String colResultStartDate;
  private String colResultAnswerDate;
  private String colResultCompleteDate;
  private String colResultFetchedDataFlag;
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
  private Integer retryStrategy;
  private Integer callerIdBehaviour;
  private String callerId;
  private Integer timezoneType;
  private String colTimezoneOffset;
  private String colCallFromTime;
  private String colCallToTime;
  private Integer applyFilterTo;
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
  private Boolean startupDbCheck;
  private Boolean dncEnabled;
  private Boolean lscEnabled;
  private Boolean fullContactListCheckEnabled;
  private String colSkill;
  private Boolean skillEnabled;
  private Boolean attachedDataLongTermSave;
  private Set<SkillFormDTO> skills;
  private Set<LocationFormDTO> locations;
  private Set<DNCFormDTO> dncs;
  private Set<LSCFormDTO> lscs;
  private List<PhoneNumberColumnFormDTO> phoneNumberColumns;
  private List<SortFieldFormDTO> sortFields;
  private List<CampaignGroupFormDTO> campaignGroups;
  private List<ResultCodeScheduleFormDTO> resultCodeSchedules;
  private List<ScheduleFormDTO> schedules;
  private List<ScheduleBlackoutAutomationFormDTO> blackoutSchedules;
  private Integer filterJoinType;
  private List<FilterFormDTO> filters;
  private List<AttachedDataFormDTO> attachedDatas;
  private List<PredefinedValueFormDTO> predefinedValues;
  private String mohClassCallingParty;
  private Long cloneId;
  private Boolean stopConditionEnabled;
  private Integer stopConditionParameter;
  private Long stopConditionValue;
  private Long stopConditionThreshold;
  private List<ScheduleAutomationFormDTO> scheduleAutomations;
  private Boolean scheduleAutomationEnabled;
  private Constants.CAMPAIGN_STOP_TYPE stopType;
  private Constants.CAMPAIGN_STOP_TYPE stopTypeHistory;
  private Long callerIdPool;
  private Constants.RETRY_CALLER_ID_BEHAVIOR retryCallerIdBehavior;
  // Campaign entity'sindeki alanla AYNI tip (primitive boolean, fct_routing_enabled NOT NULL).
  // Bu alan DTO'da bulunmazsa CampaignService.merge DTO'yu sifirdan yeni bir Campaign'e map ettigi
  // icin her form save'inde false yazilir ve UI'daki checkbox hicbir zaman kalici olmaz.
  private boolean fctRoutingEnabled;
  private String dbPriorityTable;
  private Constants.LSC_TIMEOUT_BEHAVIOR lscTimeoutBehavior;
  private Boolean allowApiControl;
  private String apiToken;
  private Float maxAnswerRate;
  private Float maxAbandonRate;
  private Double gammaCorrectionFactor;
  private Double gammaAbandonSensitivity;
  private Integer gammaWindowCapacity;
  private Integer gammaRequiredWindowSize;
  // NOT NULL kolona gider; işaretlenmemiş checkbox POST'ta hiç yer almadığı için varsayılan şart.
  private Boolean gammaPlus = Boolean.FALSE;
  private Constants.PREDICTIVE_STRATEGY predictiveStrategy;
  private Boolean countAllCallTypes;

  public Integer getFilterJoinType() {
    if (filterJoinType == null)
      return 0;
    return filterJoinType;
  }

  public void setFilterJoinType(Integer filterJoinType) {
    this.filterJoinType = filterJoinType;
  }
  public List<FilterFormDTO> getFilters() {
    return filters;
  }
  public void setFilters(List<FilterFormDTO> filters) {
    this.filters = filters;
  }

  public List<AttachedDataFormDTO> getAttachedDatas() {
    return attachedDatas;
  }
  public void setAttachedDatas(List<AttachedDataFormDTO> attachedDatas) {
    this.attachedDatas = attachedDatas;
  }

  public String getMohClassCallingParty() {
    return mohClassCallingParty;
  }
  public void setMohClassCallingParty(String mohClassCallingParty) {
    this.mohClassCallingParty = mohClassCallingParty;
  }

  public Date getStartDate() {
    return startDate;
  }
  public void setStartDate(Date startDate) {
    this.startDate = startDate;
  }

  public Date getEndDate() {
    return endDate;
  }
  public void setEndDate(Date endDate) {
    this.endDate = endDate;
  }

  public Date getStoppedDate() {
    return stoppedDate;
  }
  public void setStoppedDate(Date stoppedDate) {
    this.stoppedDate = stoppedDate;
  }

  @Mapping("ivrApplication.id")
  public Long getIvrApplication() {
    return ivrApplication;
  }
  public void setIvrApplication(Long ivrApplication) {
    this.ivrApplication = ivrApplication;
  }

  @Mapping("ivrApplicationNumber.id")
  public Long getIvrApplicationNumber() {
    return ivrApplicationNumber;
  }
  public void setIvrApplicationNumber(Long ivrApplicationNumber) {
    this.ivrApplicationNumber = ivrApplicationNumber;
  }

  public Integer getPreviewTimeout() {
    return previewTimeout;
  }
  public void setPreviewTimeout(Integer previewTimeout) {
    this.previewTimeout = previewTimeout;
  }

  public Integer getPreviewRelax() {
    return previewRelax;
  }
  public void setPreviewRelax(Integer previewRelax) {
    this.previewRelax = previewRelax;
  }

  public Boolean getAllowPreviewBeep() {
    return allowPreviewBeep;
  }
  public void setAllowPreviewBeep(Boolean allowPreviewBeep) {
    this.allowPreviewBeep = allowPreviewBeep;
  }

  public Boolean getAllowCallback() {
    return allowCallback;
  }
  public void setAllowCallback(Boolean allowCallback) {
    this.allowCallback = allowCallback;
  }

  public Boolean getAllowCallbackCustom() {
    return allowCallbackCustom;
  }
  public void setAllowCallbackCustom(Boolean allowCallbackCustom) {
    this.allowCallbackCustom = allowCallbackCustom;
  }

  public Boolean getCallbackToAppointer() {
    return callbackToAppointer;
  }
  public void setCallbackToAppointer(Boolean callbackToAppointer) {
    this.callbackToAppointer = callbackToAppointer;
  }

  public Boolean getAllowUrcWithoutMt() {
    return allowUrcWithoutMt;
  }

  public void setAllowUrcWithoutMt(Boolean allowUrcWithoutMt) {
    this.allowUrcWithoutMt = allowUrcWithoutMt;
  }

  public Boolean getAllowOverwriteByWebphone() {
    return allowOverwriteByWebphone;
  }
  public void setAllowOverwriteByWebphone(Boolean allowOverwriteByWebphone) { this.allowOverwriteByWebphone = allowOverwriteByWebphone; }

  public Boolean getAppointerReset() {
    return appointerReset;
  }
  public void setAppointerReset(Boolean appointerReset) {
    this.appointerReset = appointerReset;
  }

  public Long getAppointerResetTimeout() {
    return appointerResetTimeout;
  }
  public void setAppointerResetTimeout(Long appointerResetTimeout) { this.appointerResetTimeout = appointerResetTimeout; }

  public Integer getAppointerResetStrategy() {
    return appointerResetStrategy;
  }
  public void setAppointerResetStrategy(Integer appointerResetStrategy) { this.appointerResetStrategy = appointerResetStrategy; }

  public Integer getAgentCbRescheduleDelayMin() {
    return agentCbRescheduleDelayMin;
  }
  public void setAgentCbRescheduleDelayMin(Integer agentCbRescheduleDelayMin) { this.agentCbRescheduleDelayMin = agentCbRescheduleDelayMin; }

  public Integer getAgentCbConvertAfterAttempts() {
    return agentCbConvertAfterAttempts;
  }
  public void setAgentCbConvertAfterAttempts(Integer agentCbConvertAfterAttempts) { this.agentCbConvertAfterAttempts = agentCbConvertAfterAttempts; }

  public Integer getEndListBehaviour() {
    return endListBehaviour;
  }
  public void setEndListBehaviour(Integer endListBehaviour) {
    this.endListBehaviour = endListBehaviour;
  }

  public Integer getLowCacheSelectInterval() {
    return lowCacheSelectInterval;
  }
  public void setLowCacheSelectInterval(Integer lowCacheSelectInterval) { this.lowCacheSelectInterval = lowCacheSelectInterval; }

  public String getDbConnectionString() {
    return dbConnectionString;
  }
  public void setDbConnectionString(String dbConnectionString) {
    this.dbConnectionString = dbConnectionString;
  }

  public String getDbUsername() {
    return dbUsername;
  }
  public void setDbUsername(String dbUsername) {
    this.dbUsername = dbUsername;
  }

  public String getDbPassword() {
    return dbPassword;
  }
  public void setDbPassword(String dbPassword) {
    this.dbPassword = dbPassword;
  }

  public String getDbListTable() {
    return dbListTable;
  }
  public void setDbListTable(String dbListTable) {
    this.dbListTable = dbListTable;
  }

  public String getColUniqueKey() {
    return colUniqueKey;
  }
  public void setColUniqueKey(String colUniqueKey) {
    this.colUniqueKey = colUniqueKey;
  }

  public String getColSortBy() {
    return colSortBy;
  }
  public void setColSortBy(String colSortBy) {
    this.colSortBy = colSortBy;
  }

  public String getColResultFetchedDataFlag() {
    return colResultFetchedDataFlag;
  }
  public void setColResultFetchedDataFlag(String colResultFetchedDataFlag) { this.colResultFetchedDataFlag = colResultFetchedDataFlag; }

  public Integer getDbListSortDirection() {
    return dbListSortDirection;
  }
  public void setDbListSortDirection(Integer dbListSortDirection) {
    this.dbListSortDirection = dbListSortDirection;
  }

  public String getDbResultTable() {
    return dbResultTable;
  }
  public void setDbResultTable(String dbResultTable) {
    this.dbResultTable = dbResultTable;
  }

  public String getColResultCalledNumber() {
    return colResultCalledNumber;
  }
  public void setColResultCalledNumber(String colResultCalledNumber) { this.colResultCalledNumber = colResultCalledNumber; }

  public String getColResultCallbackDate() {
    return colResultCallbackDate;
  }
  public void setColResultCallbackDate(String colResultCallbackDate) { this.colResultCallbackDate = colResultCallbackDate; }

  public String getColResultTelephonyRc() {
    return colResultTelephonyRc;
  }
  public void setColResultTelephonyRc(String colResultTelephonyRc) {
    this.colResultTelephonyRc = colResultTelephonyRc;
  }

  public String getColResultUserRc() {
    return colResultUserRc;
  }
  public void setColResultUserRc(String colResultUserRc) {
    this.colResultUserRc = colResultUserRc;
  }

  public String getColResultRcSequence() {
    return colResultRcSequence;
  }
  public void setColResultRcSequence(String colResultRcSequence) {
    this.colResultRcSequence = colResultRcSequence;
  }

  public String getColResultUserId() {
    return colResultUserId;
  }
  public void setColResultUserId(String colResultUserId) {
    this.colResultUserId = colResultUserId;
  }

  public String getColResultCallbackNumber() {
    return colResultCallbackNumber;
  }
  public void setColResultCallbackNumber(String colResultCallbackNumber) { this.colResultCallbackNumber = colResultCallbackNumber; }

  public String getColResultStartDate() {
    return colResultStartDate;
  }
  public void setColResultStartDate(String colResultStartDate) {
    this.colResultStartDate = colResultStartDate;
  }

  public String getColResultAnswerDate() {
    return colResultAnswerDate;
  }
  public void setColResultAnswerDate(String colResultAnswerDate) {
    this.colResultAnswerDate = colResultAnswerDate;
  }

  public String getColResultCompleteDate() {
    return colResultCompleteDate;
  }
  public void setColResultCompleteDate(String colResultCompleteDate) { this.colResultCompleteDate = colResultCompleteDate; }

  public String getColResultRetryNumber() {
    return colResultRetryNumber;
  }
  public void setColResultRetryNumber(String colResultRetryNumber) {
    this.colResultRetryNumber = colResultRetryNumber;
  }

  public String getColResultRetryHistory() {
    return colResultRetryHistory;
  }
  public void setColResultRetryHistory(String colResultRetryHistory) { this.colResultRetryHistory = colResultRetryHistory; }

  public String getColResultCbHistory() {
    return colResultCbHistory;
  }
  public void setColResultCbHistory(String colResultCbHistory) {
    this.colResultCbHistory = colResultCbHistory;
  }

  public String getColResultCallProcess() {
    return colResultCallProcess;
  }
  public void setColResultCallProcess(String colResultCallProcess) {
    this.colResultCallProcess = colResultCallProcess;
  }

  public String getColResultRetryDate() {
    return colResultRetryDate;
  }
  public void setColResultRetryDate(String colResultRetryDate) {
    this.colResultRetryDate = colResultRetryDate;
  }

  public String getColResultUniqueKey() {
    return colResultUniqueKey;
  }
  public void setColResultUniqueKey(String colResultUniqueKey) {
    this.colResultUniqueKey = colResultUniqueKey;
  }

  public String getColResultDNCNumbers() {
    return colResultDNCNumbers;
  }
  public void setColResultDNCNumbers(String colResultDNCNumbers) {
    this.colResultDNCNumbers = colResultDNCNumbers;
  }

  public String getColResultCustomerNo() {
    return colResultCustomerNo;
  }
  public void setColResultCustomerNo(String colResultCustomerNo) {
    this.colResultCustomerNo = colResultCustomerNo;
  }

  public String getColDateEntered() {
    return colDateEntered;
  }
  public void setColDateEntered(String colDateEntered) {
    this.colDateEntered = colDateEntered;
  }

  public String getColResultAttempts() {
    return colResultAttempts;
  }
  public void setColResultAttempts(String colResultAttempts) {
    this.colResultAttempts = colResultAttempts;
  }

  public String getColResultCallbackAttempts() {
    return colResultCallbackAttempts;
  }
  public void setColResultCallbackAttempts(String colResultCallbackAttempts) {
    this.colResultCallbackAttempts = colResultCallbackAttempts;
  }

  public String getColResultUpdateDate() {
    return colResultUpdateDate;
  }
  public void setColResultUpdateDate(String colResultUpdateDate) {
    this.colResultUpdateDate = colResultUpdateDate;
  }

  public Integer getRetryStrategy() {
    return retryStrategy;
  }
  public void setRetryStrategy(Integer retryStrategy) {
    this.retryStrategy = retryStrategy;
  }

  public Integer getCallerIdBehaviour() {
    return callerIdBehaviour;
  }
  public void setCallerIdBehaviour(Integer callerIdBehaviour) {
    this.callerIdBehaviour = callerIdBehaviour;
  }

  public String getCallerId() {
    return callerId;
  }
  public void setCallerId(String callerId) {
    this.callerId = callerId;
  }

  public Integer getTimezoneType() {
    return timezoneType;
  }
  public void setTimezoneType(Integer timezoneType) {
    this.timezoneType = timezoneType;
  }

  public String getColTimezoneOffset() {
    return colTimezoneOffset;
  }
  public void setColTimezoneOffset(String colTimezoneOffset) {
    this.colTimezoneOffset = colTimezoneOffset;
  }

  public String getColCallFromTime() {
    return colCallFromTime;
  }
  public void setColCallFromTime(String colCallFromTime) {
    this.colCallFromTime = colCallFromTime;
  }

  public String getColCallToTime() {
    return colCallToTime;
  }
  public void setColCallToTime(String colCallToTime) {
    this.colCallToTime = colCallToTime;
  }

  public Integer getApplyFilterTo() {
    return applyFilterTo;
  }
  public void setApplyFilterTo(Integer applyFilterTo) {
    this.applyFilterTo = applyFilterTo;
  }

  public Integer getConcurrentLines() {
    return concurrentLines;
  }
  public void setConcurrentLines(Integer concurrentLines) {
    this.concurrentLines = concurrentLines;
  }

  public Integer getCallsPerSecond() {
    return callsPerSecond;
  }
  public void setCallsPerSecond(Integer callsPerSecond) {
    this.callsPerSecond = callsPerSecond;
  }

  public Integer getCacheLimit() {
    return cacheLimit;
  }
  public void setCacheLimit(Integer cacheLimit) {
    this.cacheLimit = cacheLimit;
  }

  public Integer getRingTimeout() {
    return ringTimeout;
  }
  public void setRingTimeout(Integer ringTimeout) {
    this.ringTimeout = ringTimeout;
  }

  public Float getDialingBoost() {
    return dialingBoost;
  }
  public void setDialingBoost(Float dialingBoost) {
    this.dialingBoost = dialingBoost;
  }

  public Integer getAbandonTimeout() {
    return abandonTimeout;
  }
  public void setAbandonTimeout(Integer abandonTimeout) {
    this.abandonTimeout = abandonTimeout;
  }

  public String getAbandonIvr() {
    return abandonIvr;
  }
  public void setAbandonIvr(String abandonIvr) {
    this.abandonIvr = abandonIvr;
  }

  public Boolean getAmdEnabled() {
    return amdEnabled;
  }
  public void setAmdEnabled(Boolean amdEnabled) {
    this.amdEnabled = amdEnabled;
  }

  public String getContext() {
    return context;
  }
  public void setContext(String context) {
    this.context = context;
  }

  public Boolean getRetryScheduleEnabled() {
    return retryScheduleEnabled;
  }
  public void setRetryScheduleEnabled(Boolean retryScheduleEnabled) { this.retryScheduleEnabled = retryScheduleEnabled; }

  public Integer getRetryOveralAttempt() {
    return retryOveralAttempt;
  }
  public void setRetryOveralAttempt(Integer retryOveralAttempt) {
    this.retryOveralAttempt = retryOveralAttempt;
  }

  public Integer getMacroInterval() {
    return macroInterval;
  }
  public void setMacroInterval(Integer macroInterval) {
    this.macroInterval = macroInterval;
  }

  public Integer getMacroMaxAttempt() {
    return macroMaxAttempt;
  }
  public void setMacroMaxAttempt(Integer macroMaxAttempt) {
    this.macroMaxAttempt = macroMaxAttempt;
  }

  public Integer getMaxAttemptPerNumber() {
    return maxAttemptPerNumber;
  }
  public void setMaxAttemptPerNumber(Integer maxAttemptPerNumber) {
    this.maxAttemptPerNumber = maxAttemptPerNumber;
  }

  public Integer getRetryRatio() {
    return retryRatio;
  }
  public void setRetryRatio(Integer retryRatio) {
    this.retryRatio = retryRatio;
  }

  public Double getRetryDelay() {
    return retryDelay;
  }
  public void setRetryDelay(Double retryDelay) {
    this.retryDelay = retryDelay;
  }

  public Boolean getScheduleEnabled() {
    return scheduleEnabled;
  }
  public void setScheduleEnabled(Boolean scheduleEnabled) {
    this.scheduleEnabled = scheduleEnabled;
  }

  public Boolean getDncEnabled() {
    return dncEnabled;
  }
  public void setDncEnabled(Boolean dncEnabled) {
    this.dncEnabled = dncEnabled;
  }

  public Boolean getLscEnabled() {
    return lscEnabled;
  }
  public void setLscEnabled(Boolean lscEnabled) {
    this.lscEnabled = lscEnabled;
  }

  public Boolean getFullContactListCheckEnabled() {
    return fullContactListCheckEnabled;
  }
  public void setFullContactListCheckEnabled(Boolean fullContactListCheckEnabled) { this.fullContactListCheckEnabled = fullContactListCheckEnabled; }

  public Boolean getAttachedDataLongTermSave() {
    return attachedDataLongTermSave;
  }
  public void setAttachedDataLongTermSave(Boolean attachedDataLongTermSave) { this.attachedDataLongTermSave = attachedDataLongTermSave; }

  public String getColSkill() {
    return colSkill;
  }
  public void setColSkill(String colSkill) {
    this.colSkill = colSkill;
  }

  public Boolean getSkillEnabled() {
    return skillEnabled;
  }
  public void setSkillEnabled(Boolean skillEnabled) {
    this.skillEnabled = skillEnabled;
  }

  public Set<SkillFormDTO> getSkills() {
    return skills;
  }
  public void setSkills(Set<SkillFormDTO> skills) {
    this.skills = skills;
  }

  public Set<LocationFormDTO> getLocations() {
    return locations;
  }
  public void setLocations(Set<LocationFormDTO> locations) {
    this.locations = locations;
  }

  public Set<DNCFormDTO> getDncs() {
    return dncs;
  }
  public void setDncs(Set<DNCFormDTO> dncs) {
    this.dncs = dncs;
  }

  public List<PhoneNumberColumnFormDTO> getPhoneNumberColumns() {
    return phoneNumberColumns;
  }
  public void setPhoneNumberColumns(List<PhoneNumberColumnFormDTO> phoneNumberColumns) { this.phoneNumberColumns = phoneNumberColumns; }

  public List<SortFieldFormDTO> getSortFields() {
    return sortFields;
  }
  public void setSortFields(List<SortFieldFormDTO> sortFields) {
    this.sortFields = sortFields;
  }

  public List<CampaignGroupFormDTO> getCampaignGroups() {
    return campaignGroups;
  }
  public void setCampaignGroups(List<CampaignGroupFormDTO> campaignGroups) {
    this.campaignGroups = campaignGroups;
  }

  public List<ResultCodeScheduleFormDTO> getResultCodeSchedules() {
    return resultCodeSchedules;
  }
  public void setResultCodeSchedules(List<ResultCodeScheduleFormDTO> resultCodeSchedules) { this.resultCodeSchedules = resultCodeSchedules; }

  public Set<LSCFormDTO> getLscs() {
    return lscs;
  }
  public void setLscs(Set<LSCFormDTO> lscs) {
    this.lscs = lscs;
  }

  public List<ScheduleBlackoutAutomationFormDTO> getBlackoutSchedules() {
    return blackoutSchedules;
  }
  public void setBlackoutSchedules(List<ScheduleBlackoutAutomationFormDTO> blackoutSchedules) { this.blackoutSchedules = blackoutSchedules; }

  public List<ScheduleFormDTO> getSchedules() {
    return schedules;
  }
  public void setSchedules(List<ScheduleFormDTO> schedules) {
    this.schedules = schedules;
  }

  public Integer getAvailableThreshold() {
    return availableThreshold;
  }
  public void setAvailableThreshold(Integer availableThreshold) { this.availableThreshold = availableThreshold == null ? 0 : availableThreshold; }

  public Long getCloneId() {
    return cloneId;
  }
  public void setCloneId(Long cloneId) {
    this.cloneId = cloneId;
  }

  public Boolean getStartupDbCheck() {
    return startupDbCheck;
  }
  public void setStartupDbCheck(Boolean startupDbCheck) {
    this.startupDbCheck = startupDbCheck;
  }

  public List<PredefinedValueFormDTO> getPredefinedValues() {
    return predefinedValues;
  }
  public void setPredefinedValues(List<PredefinedValueFormDTO> predefinedValues) { this.predefinedValues = predefinedValues; }

  public Boolean getStopConditionEnabled() {
    return stopConditionEnabled;
  }
  public void setStopConditionEnabled(Boolean stopConditionEnabled) { this.stopConditionEnabled = stopConditionEnabled; }

  public Integer getStopConditionParameter() {
    return stopConditionParameter;
  }
  public void setStopConditionParameter(Integer stopConditionParameter) { this.stopConditionParameter = stopConditionParameter; }

  public Long getStopConditionValue() {
    return stopConditionValue;
  }
  public void setStopConditionValue(Long stopConditionValue) {
    this.stopConditionValue = stopConditionValue;
  }

  public Long getStopConditionThreshold() {
    return stopConditionThreshold;
  }
  public void setStopConditionThreshold(Long stopConditionThreshold) { this.stopConditionThreshold = stopConditionThreshold; }

  public List<ScheduleAutomationFormDTO> getScheduleAutomations() {
    return scheduleAutomations;
  }
  public void setScheduleAutomations(List<ScheduleAutomationFormDTO> scheduleAutomations) { this.scheduleAutomations = scheduleAutomations; }

  public Boolean getScheduleAutomationEnabled() {
    return scheduleAutomationEnabled;
  }
  public void setScheduleAutomationEnabled(Boolean scheduleAutomationEnabled) { this.scheduleAutomationEnabled = scheduleAutomationEnabled; }

  public Constants.CAMPAIGN_STOP_TYPE getStopType() {
    return stopType;
  }
  public void setStopType(Constants.CAMPAIGN_STOP_TYPE stopType) {
    this.stopType = stopType;
  }

  public Constants.CAMPAIGN_STOP_TYPE getStopTypeHistory() {
    return stopTypeHistory;
  }
  public void setStopTypeHistory(Constants.CAMPAIGN_STOP_TYPE stopTypeHistory) { this.stopTypeHistory = stopTypeHistory; }

  @Mapping("callerIdPool.id")
  public Long getCallerIdPool() {
    return callerIdPool;
  }
  public void setCallerIdPool(Long callerIdPool) {
    this.callerIdPool = callerIdPool;
  }

  public String getDbPriorityTable() {
    return dbPriorityTable;
  }
  public void setDbPriorityTable(String dbPriorityTable) {
    this.dbPriorityTable = dbPriorityTable;
  }

  public Constants.LSC_TIMEOUT_BEHAVIOR getLscTimeoutBehavior() {
    return lscTimeoutBehavior;
  }
  public void setLscTimeoutBehavior(Constants.LSC_TIMEOUT_BEHAVIOR lscTimeoutBehavior) { this.lscTimeoutBehavior = lscTimeoutBehavior; }

  public Float getMaxAnswerRate() { return maxAnswerRate; }
  public void setMaxAnswerRate(Float maxAnswerRate) {
    this.maxAnswerRate = maxAnswerRate;
  }

  public Float getMaxAbandonRate() { return maxAbandonRate; }
  public void setMaxAbandonRate(Float maxAbandonRate) { this.maxAbandonRate = maxAbandonRate; }

  public Double getGammaCorrectionFactor() { return gammaCorrectionFactor; }
  public void setGammaCorrectionFactor(Double gammaCorrectionFactor) { this.gammaCorrectionFactor = gammaCorrectionFactor; }

  public Double getGammaAbandonSensitivity() { return gammaAbandonSensitivity; }
  public void setGammaAbandonSensitivity(Double gammaAbandonSensitivity) { this.gammaAbandonSensitivity = gammaAbandonSensitivity; }

  public Integer getGammaWindowCapacity() { return gammaWindowCapacity; }
  public void setGammaWindowCapacity(Integer gammaWindowCapacity) { this.gammaWindowCapacity = gammaWindowCapacity; }

  public Integer getGammaRequiredWindowSize() { return gammaRequiredWindowSize; }
  public void setGammaRequiredWindowSize(Integer gammaRequiredWindowSize) { this.gammaRequiredWindowSize = gammaRequiredWindowSize; }

  // Dozer isim bazlı otomatik map'ler; getter adı bilinçli olarak getGammaPlus (Boolean + isX
  // karışımı PropertyUtils/Dozer introspection'ını bozar).
  public Boolean getGammaPlus() { return gammaPlus; }
  public void setGammaPlus(Boolean gammaPlus) { this.gammaPlus = gammaPlus; }

  public Constants.PREDICTIVE_STRATEGY getPredictiveStrategy() {
    return predictiveStrategy;
  }
  public void setPredictiveStrategy(Constants.PREDICTIVE_STRATEGY predictiveStrategy) { this.predictiveStrategy = predictiveStrategy; }

  public Constants.RETRY_CALLER_ID_BEHAVIOR getRetryCallerIdBehavior() {
    return retryCallerIdBehavior;
  }
  public void setRetryCallerIdBehavior(Constants.RETRY_CALLER_ID_BEHAVIOR retryCallerIdBehavior) { this.retryCallerIdBehavior = retryCallerIdBehavior; }

  public boolean isFctRoutingEnabled() {
    return fctRoutingEnabled;
  }

  public void setFctRoutingEnabled(boolean fctRoutingEnabled) {
    this.fctRoutingEnabled = fctRoutingEnabled;
  }

  @Override
  @JsonIgnore
  public int getTotalListSize() {
    return super.getTotalListSize();
  }

  @Override
  @JsonIgnore
  public int getAgentsSize() {
    return super.getAgentsSize();
  }

  @Override
  @JsonIgnore
  public int getActualListSize() {
    return super.getActualListSize();
  }

  @Override
  @JsonIgnore
  public int getRetryListSize() {
    return super.getRetryListSize();
  }

  @Override
  @JsonIgnore
  public int getCallbackListSize() {
    return super.getCallbackListSize();
  }

  @Override
  @JsonIgnore
  public int getListRemaining() {
    return super.getListRemaining();
  }

  @Override
  @JsonIgnore
  public int getContactsToHandleAmount() {
    return super.getContactsToHandleAmount();
  }

  @Override
  @JsonIgnore
  public int getCallsMade() {
    return super.getCallsMade();
  }

  @Override
  @JsonIgnore
  public int getActiveCalls() {
    return super.getActiveCalls();
  }

  @Override
  @JsonIgnore
  public int getLiveCalls() {
    return super.getLiveCalls();
  }

  @Override
  @JsonIgnore
  public int getUniqueLiveCalls() {
    return super.getUniqueLiveCalls();
  }

  @Override
  @JsonIgnore
  public int getRingingCalls() {
    return super.getRingingCalls();
  }

  @Override
  @JsonIgnore
  public int getNoConnectionCalls() {
    return super.getNoConnectionCalls();
  }

  @Override
  @JsonIgnore
  public long getTotalCallDuration() {
    return super.getTotalCallDuration();
  }

  @Override
  @JsonIgnore
  public int getRetriesMade() {
    return super.getRetriesMade();
  }

  @Override
  @JsonIgnore
  public int getAbandonedCallsSize() {
    return super.getAbandonedCallsSize();
  }

  @Override
  @JsonIgnore
  public double getAbandonedCallsRatio() {
    return super.getAbandonedCallsRatio();
  }

  @Override
  @JsonIgnore
  public int getCacheSize() {
    return super.getCacheSize();
  }

  @Override
  @JsonIgnore
  public int getQueueSize() {
    return super.getQueueSize();
  }

  @Override
  @JsonIgnore
  public long getRunoutEstimation() {
    return super.getRunoutEstimation();
  }

  @Override
  @JsonIgnore
  public int getCompletionPercent() {
    return super.getCompletionPercent();
  }

  @Override
  @JsonIgnore
  public double getDbEfficiency() {
    return super.getDbEfficiency();
  }

  @Override
  @JsonIgnore
  public double getAnswerRate() {
    return super.getAnswerRate();
  }

  public Boolean getAllowApiControl() {
    return allowApiControl;
  }
  public void setAllowApiControl(Boolean allowApiControl) {
    this.allowApiControl = allowApiControl;
  }

  public String getApiToken() {
    return apiToken;
  }
  public void setApiToken(String apiToken) {
    this.apiToken = apiToken;
  }

  public String getColResultResponseValues() {
    return colResultResponseValues;
  }
  public void setColResultResponseValues(String colResultResponseValues) { this.colResultResponseValues = colResultResponseValues; }

  public String getColResultCloseReason() {
    return colResultCloseReason;
  }
  public void setColResultCloseReason(String colResultCloseReason) { this.colResultCloseReason = colResultCloseReason; }

  public Boolean getCountAllCallTypes() {
    return countAllCallTypes;
  }

  public void setCountAllCallTypes(Boolean countAllCallTypes) {
    this.countAllCallTypes = countAllCallTypes;
  }
}
