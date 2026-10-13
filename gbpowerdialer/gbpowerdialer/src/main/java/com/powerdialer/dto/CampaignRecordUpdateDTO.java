package com.powerdialer.dto;

import java.util.List;

/**
 * Data Transfer Object (DTO) for updating campaign records.
 *
 * This class encapsulates the details required to perform operations on campaign records,
 * including the campaign ID, operation type, and a list of selected record IDs.
 */
public class CampaignRecordUpdateDTO {

    /**
     * The unique identifier of the campaign.
     */
    private Long campaignId;

    /**
     * The type of operation to perform on the campaign records.
     * Possible values: ACTIVATE_RECORDS, ADD_PRIORITY, REMOVE_PRIORITY.
     */
    private String operationType;

    /**
     * A list of IDs representing the selected campaign records.
     */
    private List<Long> selectedRecordIds;

    /**
     * Gets the unique identifier of the campaign.
     *
     * @return The campaign ID.
     */
    public Long getCampaignId() {
        return campaignId;
    }

    /**
     * Sets the unique identifier of the campaign.
     *
     * @param campaignId The campaign ID to set.
     */
    public void setCampaignId(Long campaignId) {
        this.campaignId = campaignId;
    }

    /**
     * Gets the type of operation to perform on the campaign records.
     *
     * @return The operation type.
     */
    public String getOperationType() {
        return operationType;
    }

    /**
     * Sets the type of operation to perform on the campaign records.
     *
     * @param operationType The operation type to set.
     */
    public void setOperationType(String operationType) {
        this.operationType = operationType;
    }

    /**
     * Gets the list of IDs representing the selected campaign records.
     *
     * @return The list of selected record IDs.
     */
    public List<Long> getSelectedRecordIds() {
        return selectedRecordIds;
    }

    /**
     * Sets the list of IDs representing the selected campaign records.
     *
     * @param selectedRecordIds The list of selected record IDs to set.
     */
    public void setSelectedRecordIds(List<Long> selectedRecordIds) {
        this.selectedRecordIds = selectedRecordIds;
    }
}