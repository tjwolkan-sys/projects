package com.powerdialer.model;

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToOne;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

import com.powerdialer.util.CDRTypeConverter;
import com.powerdialer.util.Constants;

@Entity
@Table(name = "pd_cdr")
public class CDR {

  private Long id;
  private String interactionId;
  private String outgoingCallId;
  private String internalCallId;
  private Date callStartTime;
  private Date callDialTime;
  private Date callAnswerTime;
  private Date callHandlingStartTime;
  private Date callEndTime;
  private Tenant tenant;
  private Campaign campaign;
  private Long campaignType;
  private Long contactId;
  private Long groupId;
  private String contactNumber;
  private String contactNumberColumn;
  private Integer contactNumberOrder;
  private Integer rcSequence;
  private Server server;
  private TelephonyResultCode telephonyRC;
  private String skills;
  private User assignedUser;
  private User connectedUser;
  private String connectedIvrNumber;
  private Constants.CDR_OUTDIALING_TYPE cdrOutdialingType;
  private String filter;
  private Long userResultCodeId;
  private Boolean abandoned;

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO, generator = "baseEntity_seq")
  @SequenceGenerator(name = "baseEntity_seq", sequenceName = "PD_CDR_SERVICE_FCSEQ", allocationSize = 1)
  @Column(name = "id")
  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  @Column(name = "interaction_id")
  public String getInteractionId() {
    return interactionId;
  }

  public void setInteractionId(String interactionId) {
    this.interactionId = interactionId;
  }

  @Column(name = "outgoing_call_id")
  public String getOutgoingCallId() {
    return outgoingCallId;
  }

  public void setOutgoingCallId(String outgoingCallId) {
    this.outgoingCallId = outgoingCallId;
  }

  @Column(name = "internal_call_id")
  public String getInternalCallId() {
    return internalCallId;
  }

  public void setInternalCallId(String internalCallId) {
    this.internalCallId = internalCallId;
  }

  @Column(name = "call_start_time")
  public Date getCallStartTime() {
    return callStartTime;
  }

  public void setCallStartTime(Date callStartTime) {
    this.callStartTime = callStartTime;
  }

  @Column(name = "call_dial_time")
  public Date getCallDialTime() {
    return callDialTime;
  }

  public void setCallDialTime(Date callDialTime) {
    this.callDialTime = callDialTime;
  }

  @Column(name = "call_answer_time")
  public Date getCallAnswerTime() {
    return callAnswerTime;
  }

  public void setCallAnswerTime(Date callAnswerTime) {
    this.callAnswerTime = callAnswerTime;
  }

  @Column(name = "call_handling_start")
  public Date getCallHandlingStartTime() {
    return callHandlingStartTime;
  }

  public void setCallHandlingStartTime(Date callHandlingStartTime) {
    this.callHandlingStartTime = callHandlingStartTime;
  }

  @Column(name = "group_id")
  public Long getGroupId() {
    return groupId;
  }

  public void setGroupId(Long groupId) {
    this.groupId = groupId;
  }

  @Column(name = "call_end_time")
  public Date getCallEndTime() {
    return callEndTime;
  }

  public void setCallEndTime(Date callEndTime) {
    this.callEndTime = callEndTime;
  }

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "tenant_id")
  public Tenant getTenant() {
    return tenant;
  }

  public void setTenant(Tenant tenant) {
    this.tenant = tenant;
  }

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "campaign_id")
  public Campaign getCampaign() {
    return campaign;
  }

  public void setCampaign(Campaign campaign) {
    this.campaign = campaign;
  }

  @Column(name = "campaign_type")
  public Long getCampaignType() {
    return campaignType;
  }

  public void setCampaignType(Long campaignType) {
    this.campaignType = campaignType;
  }

  @Column(name = "contact_id")
  public Long getContactId() {
    return contactId;
  }

  public void setContactId(Long contactId) {
    this.contactId = contactId;
  }

  @Column(name = "contact_number")
  public String getContactNumber() {
    return contactNumber;
  }

  public void setContactNumber(String contactNumber) {
    this.contactNumber = contactNumber;
  }

  @Column(name = "contact_number_column")
  public String getContactNumberColumn() {
    return contactNumberColumn;
  }

  public void setContactNumberColumn(String contactNumberColumn) {
    this.contactNumberColumn = contactNumberColumn;
  }

  @Column(name = "contact_number_order")
  public Integer getContactNumberOrder() {
    return contactNumberOrder;
  }

  public void setContactNumberOrder(Integer contactNumberOrder) {
    this.contactNumberOrder = contactNumberOrder;
  }

  @Column(name = "rc_sequence")
  public Integer getRcSequence() {
    return rcSequence;
  }

  public void setRcSequence(Integer rcSequence) {
    this.rcSequence = rcSequence;
  }

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "server_id")
  public Server getServer() {
    return server;
  }

  public void setServer(Server server) {
    this.server = server;
  }

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "telephony_rc")
  public TelephonyResultCode getTelephonyRC() {
    return telephonyRC;
  }

  public void setTelephonyRC(TelephonyResultCode telephonyRC) {
    this.telephonyRC = telephonyRC;
  }

  @Column(name = "skills")
  public String getSkills() {
    return skills;
  }

  public void setSkills(String skills) {
    this.skills = skills;
  }

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "assigned_user_id")
  public User getAssignedUser() {
    return assignedUser;
  }

  public void setAssignedUser(User assignedUser) {
    this.assignedUser = assignedUser;
  }

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "connected_user_id")
  public User getConnectedUser() {
    return connectedUser;
  }

  public void setConnectedUser(User connectedUser) {
    this.connectedUser = connectedUser;
  }

  @Column(name = "connected_ivr_number")
  public String getConnectedIvrNumber() {
    return connectedIvrNumber;
  }

  public void setConnectedIvrNumber(String connectedIvrNumber) {
    this.connectedIvrNumber = connectedIvrNumber;
  }

  @Convert(converter = CDRTypeConverter.class)
  @Column(name = "outdialing_type_id")
  public Constants.CDR_OUTDIALING_TYPE getCdrOutdialingType() {
    return cdrOutdialingType;
  }

  public void setCdrOutdialingType(Constants.CDR_OUTDIALING_TYPE cdrOutdialingType) {
    this.cdrOutdialingType = cdrOutdialingType;
  }

  @Column(name = "filter", columnDefinition = "NVARCHAR(500)")
  public String getFilter() {
    return filter;
  }

  public void setFilter(String filter) {
    this.filter = filter != null && filter.length() > 500 ? filter.substring(0, 500) : filter;
  }

  @Column(name = "user_result_code_id")
  public Long getUserResultCodeId() {
    return userResultCodeId;
  }

  public void setUserResultCodeId(Long userResultCodeId) {
    this.userResultCodeId = userResultCodeId;
  }

  @Column(name = "abandoned")
  public Boolean getAbandoned() {
    return abandoned;
  }

  public void setAbandoned(Boolean abandoned) {
    this.abandoned = abandoned;
  }

  @Override
  public String toString() {
    return "CDR{" +
        "interactionId='" + interactionId + '\'' +
        '}';
  }
}
