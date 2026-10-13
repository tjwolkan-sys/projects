package com.powerdialer.model;

import java.util.Map;

import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonIgnore;

import com.powerdialer.dto.CampaignListDTO;
import com.powerdialer.model.base.BaseObject;
import com.powerdialer.util.MapConverter;

@Entity
@Table(name = "pd_campaign_memento")
public class CampaignMemento extends BaseObject {

  private static final long serialVersionUID = 706049899077350920L;
  private Long id;
  private Campaign campaign;
  private int agentsSize;
  private int availAgentsSize;
  private int totalListSize;
  private int actualListSize;
  private int retryListSize;
  private int callbackListSize;
  private int listRemaining;
  private int contactsToHandleAmount;
  private int callsMade;
  private int liveCalls;
  private int uniqueLiveCalls;
  private int noConnectionCalls;
  private int retriesMade;
  private int abandonedCallsSize;
  private double abandonedCallsRatio;
  private long runoutEstimation;
  private int completionPercent;
  private double dbEfficiency;
  private double answerRate;
  private long totalCallDuration;
  private Long touchedContacts;
  private Long completedContacts;
  private Map<Long, Long> callsByUserResultCode;
  private Map<Long, Long> callsByTelephonyResultCode;

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO, generator = "baseEntity_seq")
  @SequenceGenerator(name = "baseEntity_seq", sequenceName = "PD_CAMPAIGN_MEMENTO_FCSEQ", allocationSize = 1)
  @Column(name = "id")
  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  @JsonIgnore
  @ManyToOne(cascade = { CascadeType.PERSIST }, fetch = FetchType.LAZY)
  @JoinColumn(name = "campaign_id", nullable = false)
  public Campaign getCampaign() {
    return campaign;
  }

  public void setCampaign(Campaign campaign) {
    this.campaign = campaign;
  }

  @Column(name = "agents_size", nullable = false)
  public int getAgentsSize() {
    return agentsSize;
  }

  public void setAgentsSize(int agentsSize) {
    this.agentsSize = agentsSize;
  }

  @Column(name = "avail_agents_size", nullable = false)
  public int getAvailAgentsSize() {
    return availAgentsSize;
  }

  public void setAvailAgentsSize(int availAgentsSize) {
    this.availAgentsSize = availAgentsSize;
  }

  @Column(name = "total_list_size", nullable = false)
  public int getTotalListSize() {
    return totalListSize;
  }

  public void setTotalListSize(int totalListSize) {
    this.totalListSize = totalListSize;
  }

  @Column(name = "actual_list_size", nullable = false)
  public int getActualListSize() {
    return actualListSize;
  }

  public void setActualListSize(int actualListSize) {
    this.actualListSize = actualListSize;
  }

  @Column(name = "retry_list_size", nullable = false)
  public int getRetryListSize() {
    return retryListSize;
  }

  public void setRetryListSize(int retryListSize) {
    this.retryListSize = retryListSize;
  }

  @Column(name = "list_remaining", nullable = false)
  public int getListRemaining() {
    return listRemaining;
  }

  public void setListRemaining(int listRemaining) {
    this.listRemaining = listRemaining;
  }

  @Column(name = "callback_list_size")
  public int getCallbackListSize() {
    return callbackListSize;
  }

  public void setCallbackListSize(int callbackListSize) {
    this.callbackListSize = callbackListSize;
  }

  @Column(name = "contacts_to_handle_amount", nullable = false)
  public int getContactsToHandleAmount() {
    return contactsToHandleAmount;
  }

  public void setContactsToHandleAmount(int contactsToHandleAmount) {
    this.contactsToHandleAmount = contactsToHandleAmount;
  }

  @Column(name = "calls_made", nullable = false)
  public int getCallsMade() {
    return callsMade;
  }

  public void setCallsMade(int callsMade) {
    this.callsMade = callsMade;
  }

  @Column(name = "live_calls", nullable = false)
  public int getLiveCalls() {
    return liveCalls;
  }

  public void setLiveCalls(int liveCalls) {
    this.liveCalls = liveCalls;
  }

  @Column(name = "no_connection_calls", nullable = false)
  public int getNoConnectionCalls() {
    return noConnectionCalls;
  }

  public void setNoConnectionCalls(int noConnectionCalls) {
    this.noConnectionCalls = noConnectionCalls;
  }

  @Column(name = "retries_made", nullable = false)
  public int getRetriesMade() {
    return retriesMade;
  }

  public void setRetriesMade(int retriesMade) {
    this.retriesMade = retriesMade;
  }

  @Column(name = "abandoned_calls_size", nullable = false)
  public int getAbandonedCallsSize() {
    return abandonedCallsSize;
  }

  public void setAbandonedCallsSize(int abandonedCallsCount) {
    this.abandonedCallsSize = abandonedCallsCount;
  }

  @Column(name = "abandoned_calls_ratio", nullable = false)
  public double getAbandonedCallsRatio() {
    return abandonedCallsRatio;
  }

  public void setAbandonedCallsRatio(double abandonedCallsRatio) {
    this.abandonedCallsRatio = abandonedCallsRatio;
  }

  @Column(name = "runout_estimation")
  public long getRunoutEstimation() {
    return runoutEstimation;
  }

  public void setRunoutEstimation(long runoutEstimation) {
    this.runoutEstimation = runoutEstimation;
  }

  @Column(name = "completion_percent", nullable = false)
  public int getCompletionPercent() {
    return completionPercent;
  }

  public void setCompletionPercent(int completionPercent) {
    this.completionPercent = completionPercent;
  }

  @Column(name = "db_efficiency", nullable = false)
  public double getDbEfficiency() { return dbEfficiency; }

  public void setDbEfficiency(double dbEfficiency) {
    this.dbEfficiency = dbEfficiency;
  }

  @Column(name = "answer_rate", nullable = false)
  public double getAnswerRate() { return answerRate; }

  public void setAnswerRate(double answerRate) { this.answerRate = answerRate; }

  @Column(name = "total_call_duration", nullable = false)
  public long getTotalCallDuration() {
    return totalCallDuration;
  }

  public void setTotalCallDuration(long totalCallDuration) {
    this.totalCallDuration = totalCallDuration;
  }

  @Column(name = "unique_live_calls", nullable = false)
  public int getUniqueLiveCalls() {
    return uniqueLiveCalls;
  }

  public void setUniqueLiveCalls(int uniqueLiveCalls) {
    this.uniqueLiveCalls = uniqueLiveCalls;
  }

  @Column(name = "touched_contacts")
  public Long getTouchedContacts() {
    return touchedContacts;
  }

  public void setTouchedContacts(Long touchedContacts) {
    this.touchedContacts = touchedContacts;
  }

  @Column(name = "completed_contacts")
  public Long getCompletedContacts() {
    return completedContacts;
  }

  public void setCompletedContacts(Long completedContacts) {
    this.completedContacts = completedContacts;
  }

  @Column(name = "calls_by_user_rc")
  @Convert(converter = MapConverter.class)
  public Map<Long, Long> getCallsByUserResultCode() {
    return callsByUserResultCode;
  }

  public void setCallsByUserResultCode(Map<Long, Long> callsByUserResultCode) {
    this.callsByUserResultCode = callsByUserResultCode;
  }

  @Column(name = "calls_by_telephony_rc")
  @Convert(converter = MapConverter.class)
  public Map<Long, Long> getCallsByTelephonyResultCode() {
    return callsByTelephonyResultCode;
  }

  public void setCallsByTelephonyResultCode(Map<Long, Long> callsByTelephonyResultCode) {
    this.callsByTelephonyResultCode = callsByTelephonyResultCode;
  }

  public void restore(Campaign campaign) {
    campaign.setActualListSize(actualListSize);
    campaign.setAgentsSize(agentsSize);
    campaign.setAvailAgentsSize(availAgentsSize);
    campaign.setCallsMade(callsMade);
    campaign.setCompletionPercent(completionPercent);
    campaign.setDbEfficiency(dbEfficiency);
    campaign.setAnswerRate(answerRate);
    campaign.setListRemaining(listRemaining);
    campaign.setContactsToHandleAmount(contactsToHandleAmount);
    campaign.setLiveCalls(liveCalls);
    campaign.setUniqueLiveCalls(uniqueLiveCalls);
    campaign.setNoConnectionCalls(noConnectionCalls);
    campaign.setRetriesMade(retriesMade);
    campaign.setAbandonedCallsSize(abandonedCallsSize);
    campaign.setAbandonedCallsRatio(abandonedCallsRatio);
    campaign.setRunoutEstimation(runoutEstimation);
    campaign.setTotalListSize(totalListSize);
    campaign.setTotalCallDuration(totalCallDuration);
    campaign.setTouchedContacts(touchedContacts);
    campaign.setCompletedContacts(completedContacts);
    campaign.setCallsByUserResultCode(callsByUserResultCode);
    campaign.setCallsByTelephonyResultCode(callsByTelephonyResultCode);
  }

  public void restore(CampaignListDTO campaign) {
    campaign.setActualListSize(actualListSize);
    campaign.setAgentsSize(agentsSize);
    campaign.setAvailAgentsSize(availAgentsSize);
    campaign.setCallsMade(callsMade);
    campaign.setCompletionPercent(completionPercent);
    campaign.setDbEfficiency(dbEfficiency);
    campaign.setAnswerRate(answerRate);
    campaign.setListRemaining(listRemaining);
    campaign.setContactsToHandleAmount(contactsToHandleAmount);
    campaign.setLiveCalls(liveCalls);
    campaign.setUniqueLiveCalls(uniqueLiveCalls);
    campaign.setNoConnectionCalls(noConnectionCalls);
    campaign.setRetriesMade(retriesMade);
    campaign.setAbandonedCallsSize(abandonedCallsSize);
    campaign.setAbandonedCallsRatio(abandonedCallsRatio);
    campaign.setTotalListSize(totalListSize);
    campaign.setTotalCallDuration(totalCallDuration);
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((id == null) ? 0 : id.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    CampaignMemento other = (CampaignMemento) obj;
    if (id == null) {
      return other.id == null;
    } else return id.equals(other.id);
  }

  @Override
  public String toString() {
    return "CampaignMemento [id=" + id + ", campaign=" + campaign.getId() + "]";
  }

}
