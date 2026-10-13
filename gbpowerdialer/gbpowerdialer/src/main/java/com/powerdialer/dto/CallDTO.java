package com.powerdialer.dto;

import java.util.List;
import java.util.Set;

import org.dozer.Mapping;

public class CallDTO {

  private String id;
  private String uniqueKey;
  private String calledNumber;
  private String channel;
  private String callBackNumber;
  private Double callBackDate;
  private String retryNumber;
  private List<String> retryHistory;
  private Double retryDate;
  private Double originationDate;
  private Double startDate;
  private Double answerDate;
  private Double completeDate;
  private String adCardId;
  private long durationFromAnswer;
  private long durationFromOrigination;
  private Integer resultCodeSequence;
  private ResultCodeDTO userResultCode;
  private TelephonyResultCodeDTO telephonyResultCode;
  private UserDTO user;
  private CampaignDTO campaign;
  private ServerDTO server;
  private Set<String> DNCNumbers;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getUniqueKey() {
    return uniqueKey;
  }

  public void setUniqueKey(String uniqueKey) {
    this.uniqueKey = uniqueKey;
  }

  public String getCalledNumber() {
    return calledNumber;
  }

  public void setCalledNumber(String calledNumber) {
    this.calledNumber = calledNumber;
  }

  public String getChannel() {
    return channel;
  }

  public void setChannel(String channel) {
    this.channel = channel;
  }

  public String getCallBackNumber() {
    return callBackNumber;
  }

  public void setCallBackNumber(String callBackNumber) {
    this.callBackNumber = callBackNumber;
  }

  public Double getCallBackDate() {
    return callBackDate;
  }

  public void setCallBackDate(Double callBackDate) {
    this.callBackDate = callBackDate;
  }

  public String getRetryNumber() {
    return retryNumber;
  }

  public void setRetryNumber(String retryNumber) {
    this.retryNumber = retryNumber;
  }

  public List<String> getRetryHistory() {
    return retryHistory;
  }

  public void setRetryHistory(List<String> retryHistory) {
    this.retryHistory = retryHistory;
  }

  public Double getRetryDate() {
    return retryDate;
  }

  public void setRetryDate(Double retryDate) {
    this.retryDate = retryDate;
  }

  public Double getOriginationDate() {
    return originationDate;
  }

  public void setOriginationDate(Double originationDate) {
    this.originationDate = originationDate;
  }

  public Double getStartDate() {
    return startDate;
  }

  public void setStartDate(Double startDate) {
    this.startDate = startDate;
  }

  public Double getAnswerDate() {
    return answerDate;
  }

  public void setAnswerDate(Double answerDate) {
    this.answerDate = answerDate;
  }

  public Double getCompleteDate() {
    return completeDate;
  }

  public void setCompleteDate(Double completeDate) {
    this.completeDate = completeDate;
  }

  public String getAdCardId() {
    return adCardId;
  }

  public void setAdCardId(String adCardId) {
    this.adCardId = adCardId;
  }

  public long getDurationFromAnswer() {
    return durationFromAnswer;
  }

  public void setDurationFromAnswer(long durationFromAnswer) {
    this.durationFromAnswer = durationFromAnswer;
  }

  public long getDurationFromOrigination() {
    return durationFromOrigination;
  }

  public void setDurationFromOrigination(long durationFromOrigination) {
    this.durationFromOrigination = durationFromOrigination;
  }

  public Integer getResultCodeSequence() {
    return resultCodeSequence;
  }

  public void setResultCodeSequence(Integer resultCodeSequence) {
    this.resultCodeSequence = resultCodeSequence;
  }

  public ResultCodeDTO getUserResultCode() {
    return userResultCode;
  }

  public void setUserResultCode(ResultCodeDTO userResultCode) {
    this.userResultCode = userResultCode;
  }

  public TelephonyResultCodeDTO getTelephonyResultCode() {
    return telephonyResultCode;
  }

  public void setTelephonyResultCode(TelephonyResultCodeDTO telephonyResultCode) {
    this.telephonyResultCode = telephonyResultCode;
  }

  public CampaignDTO getCampaign() {
    return campaign;
  }

  public void setCampaign(CampaignDTO campaign) {
    this.campaign = campaign;
  }

  public UserDTO getUser() {
    return user;
  }

  public void setUser(UserDTO user) {
    this.user = user;
  }

  @Mapping("callOriginator.connectionDecorator.server")
  public ServerDTO getServer() {
    return server;
  }

  public void setServer(ServerDTO server) {
    this.server = server;
  }

  public Set<String> getDNCNumbers() {
    return DNCNumbers;
  }

  public void setDNCNumbers(Set<String> dNCNumbers) {
    DNCNumbers = dNCNumbers;
  }

}
