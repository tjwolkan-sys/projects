package com.powerdialer.dto;

import java.util.Date;

/**
 * Data Transfer Object (DTO) for representing campaign records.
 *
 * This class encapsulates the details of a campaign record, including its ID, status, record count,
 * selection status, start and answer dates, telephony result code, and fetched data flag.
 */
public class CampaignRecordDTO {

    private Long id;
    private String status;
    private Integer recordCount;


    private Boolean selected;

    private Date startDate;
    private Date answerDate;
    private String telephonyResultCode;
    private String fetchedDataFlag;

    /**
     * Gets the unique identifier of the campaign record.
     *
     * @return The ID of the campaign record.
     */
    public Long getId() {
        return id;
    }

    /**
     * Sets the unique identifier of the campaign record.
     *
     * @param id The ID to set for the campaign record.
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * Gets the status of the campaign record.
     *
     * @return The status of the campaign record.
     */
    public String getStatus() {
        return status;
    }

    /**
     * Sets the status of the campaign record.
     *
     * @param status The status to set for the campaign record.
     */
    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * Gets the count of records associated with the campaign.
     *
     * @return The record count of the campaign.
     */
    public Integer getRecordCount() {
        return recordCount;
    }

    /**
     * Sets the count of records associated with the campaign.
     *
     * @param recordCount The record count to set for the campaign.
     */
    public void setRecordCount(Integer recordCount) {
        this.recordCount = recordCount;
    }

    /**
     * Gets the selection status of the campaign record.
     *
     * @return True if the campaign record is selected, otherwise false.
     */
    public Boolean getSelected() {
        return selected;
    }

    /**
     * Sets the selection status of the campaign record.
     *
     * @param selected The selection status to set for the campaign record.
     */
    public void setSelected(Boolean selected) {
        this.selected = selected;
    }

    /**
     * Gets the start date of the campaign record.
     *
     * @return The start date of the campaign record.
     */
    public Date getStartDate() {
        return startDate;
    }

    /**
     * Sets the start date of the campaign record.
     *
     * @param startDate The start date to set for the campaign record.
     */
    public void setStartDate(Date startDate) {
        this.startDate = startDate;
    }

    /**
     * Gets the answer date of the campaign record.
     *
     * @return The answer date of the campaign record.
     */
    public Date getAnswerDate() {
        return answerDate;
    }

    /**
     * Sets the answer date of the campaign record.
     *
     * @param answerDate The answer date to set for the campaign record.
     */
    public void setAnswerDate(Date answerDate) {
        this.answerDate = answerDate;
    }

    /**
     * Gets the telephony result code associated with the campaign record.
     *
     * @return The telephony result code of the campaign record.
     */
    public String getTelephonyResultCode() {
        return telephonyResultCode;
    }

    /**
     * Sets the telephony result code associated with the campaign record.
     *
     * @param telephonyResultCode The telephony result code to set for the campaign record.
     */
    public void setTelephonyResultCode(String telephonyResultCode) {
        this.telephonyResultCode = telephonyResultCode;
    }

    /**
     * Gets the fetched data flag of the campaign record.
     *
     * @return The fetched data flag of the campaign record.
     */
    public String getFetchedDataFlag() {
        return fetchedDataFlag;
    }

    /**
     * Sets the fetched data flag of the campaign record.
     *
     * @param fetchedDataFlag The fetched data flag to set for the campaign record.
     */
    public void setFetchedDataFlag(String fetchedDataFlag) {
        this.fetchedDataFlag = fetchedDataFlag;
    }
}
