package com.powerdialer.dto;

public class CampaignListDTO extends BaseEntityTenantDTO {

  private int type;
  private int agentsSize;
  private int availAgentsSize;
  private int incallAgents;
  private int totalListSize;
  private int actualListSize;
  private int retryListSize;
  private int callbackListSize;
  private int freshDataListSize;
  private int listRemaining;
  private int contactsToHandleAmount;
  private int callsMade;
  private int activeCalls;
  private int callsInProgressCount;
  private int liveCalls;
  private int uniqueLiveCalls;
  private int ringingCalls;
  private int noConnectionCalls;
  private long totalCallDuration;
  private int retriesMade;
  private int abandonedCallsSize = 0;
  private double abandonedCallsRatio = 0;
  private int cacheSize;
  private int queueSize;
  private long runoutEstimation;
  private int completionPercent;
  private double dbEfficiency;
  private double answerRate;
  private int state;
  private Long version;
  private Long resultCodeTimerStatus;
  private Integer resultCodeTimer;
  private String colSortBy;
  private String dbResultTable;

  public Integer getResultCodeTimer() {
    return resultCodeTimer;
  }

  public void setResultCodeTimer(Integer resultCodeTimer) {
    this.resultCodeTimer = resultCodeTimer;
  }

  public Long getResultCodeTimerStatus() {
    return resultCodeTimerStatus;
  }

  public void setResultCodeTimerStatus(Long resultCodeTimerStatus) {
    this.resultCodeTimerStatus = resultCodeTimerStatus;
  }

  public Long getVersion() {
    return version;
  }

  public void setVersion(Long version) {
    this.version = version;
  }

  public int getState() {
    return state;
  }

  public void setState(int state) {
    this.state = state;
  }

  public int getAgentsSize() {
    return agentsSize;
  }

  public void setAgentsSize(int agentsSize) {
    this.agentsSize = agentsSize;
  }

  public int getAvailAgentsSize() {
    return availAgentsSize;
  }

  public void setAvailAgentsSize(int availAgentsSize) {
    this.availAgentsSize = availAgentsSize;
  }

  public int getIncallAgents() {
    return incallAgents;
  }

  public void setIncallAgents(int incallAgents) {
    this.incallAgents = incallAgents;
  }

  public int getTotalListSize() {
    return totalListSize;
  }

  public void setTotalListSize(int totalListSize) {
    this.totalListSize = totalListSize;
  }

  public int getActualListSize() {
    return actualListSize;
  }

  public void setActualListSize(int actualListSize) {
    this.actualListSize = actualListSize;
  }

  public int getRetryListSize() {
    return retryListSize;
  }

  public void setRetryListSize(int retryListSize) {
    this.retryListSize = retryListSize;
  }

  public int getCallbackListSize() {
    return callbackListSize;
  }

  public void setCallbackListSize(int callbackListSize) {
    this.callbackListSize = callbackListSize;
  }

  public int getFreshDataListSize() {
    return freshDataListSize;
  }

  public void setFreshDataListSize(int freshDataListSize) {
    this.freshDataListSize = freshDataListSize;
  }

  public int getListRemaining() {
    return listRemaining;
  }

  public void setListRemaining(int listRemaining) {
    this.listRemaining = listRemaining;
  }

  public int getContactsToHandleAmount() {
    return contactsToHandleAmount;
  }

  public void setContactsToHandleAmount(int contactsToHandleAmount) {
    this.contactsToHandleAmount = contactsToHandleAmount;
  }

  public int getCallsMade() {
    return callsMade;
  }

  public void setCallsMade(int callsMade) {
    this.callsMade = callsMade;
  }

  public int getActiveCalls() {
    return activeCalls;
  }

  public void setActiveCalls(int activeCalls) {
    this.activeCalls = activeCalls;
  }

  public int getCallsInProgressCount() {
    return callsInProgressCount;
  }

  public void setCallsInProgressCount(int callsInProgressCount) {
    this.callsInProgressCount = callsInProgressCount;
  }

  public int getLiveCalls() {
    return liveCalls;
  }

  public void setLiveCalls(int liveCalls) {
    this.liveCalls = liveCalls;
  }

  public int getUniqueLiveCalls() {
    return uniqueLiveCalls;
  }

  public void setUniqueLiveCalls(int uniqueLiveCalls) {
    this.uniqueLiveCalls = uniqueLiveCalls;
  }

  public int getRingingCalls() {
    return ringingCalls;
  }

  public void setRingingCalls(int ringingCalls) {
    this.ringingCalls = ringingCalls;
  }

  public int getNoConnectionCalls() {
    return noConnectionCalls;
  }

  public void setNoConnectionCalls(int noConnectionCalls) {
    this.noConnectionCalls = noConnectionCalls;
  }

  public long getTotalCallDuration() {
    return totalCallDuration;
  }

  public void setTotalCallDuration(long totalCallDuration) {
    this.totalCallDuration = totalCallDuration;
  }

  public int getRetriesMade() {
    return retriesMade;
  }

  public void setRetriesMade(int retriesMade) {
    this.retriesMade = retriesMade;
  }

  public int getAbandonedCallsSize() {
    return abandonedCallsSize;
  }

  public void setAbandonedCallsSize(int abandonedCallsSize) {
    this.abandonedCallsSize = abandonedCallsSize;
  }

  public double getAbandonedCallsRatio() {
    return abandonedCallsRatio;
  }

  public void setAbandonedCallsRatio(double abandonedCallsRatio) {
    this.abandonedCallsRatio = abandonedCallsRatio;
  }

  public int getCacheSize() {
    return cacheSize;
  }

  public void setCacheSize(int cacheSize) {
    this.cacheSize = cacheSize;
  }

  public int getQueueSize() {
    return queueSize;
  }

  public void setQueueSize(int queueSize) {
    this.queueSize = queueSize;
  }

  public long getRunoutEstimation() {
    return runoutEstimation;
  }

  public void setRunoutEstimation(long runoutEstimation) {
    this.runoutEstimation = runoutEstimation;
  }

  public int getCompletionPercent() {
    return completionPercent;
  }

  public void setCompletionPercent(int completionPercent) {
    this.completionPercent = completionPercent;
  }

  public double getDbEfficiency() { return dbEfficiency; }

  public void setDbEfficiency(double dbEfficiency) {
    this.dbEfficiency = dbEfficiency;
  }

  public double getAnswerRate() { return answerRate; }

  public void setAnswerRate(double answerRate) { this.answerRate = answerRate; }

  public int getType() {
    return type;
  }

  public void setType(int type) {
    this.type = type;
  }

  public String getColSortBy() {
    return colSortBy;
  }

  public void setColSortBy(String colSortBy) {
    this.colSortBy = colSortBy;
  }

  public String getDbResultTable() {
    return dbResultTable;
  }

  public void setDbResultTable(String dbResultTable) {
    this.dbResultTable = dbResultTable;
  }

}
