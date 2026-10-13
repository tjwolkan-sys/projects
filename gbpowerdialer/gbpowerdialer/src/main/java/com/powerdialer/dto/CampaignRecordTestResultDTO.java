package com.powerdialer.dto;

import org.json.JSONArray;
/**
 * Data Transfer Object (DTO) for representing the test results of campaign records.
 *
 * This class encapsulates the total count of records and the rows of data in JSON format.
 */
public class CampaignRecordTestResultDTO {
    /**
     * The total count of campaign records.
     */
    private int totalCount;

    /**
     * The rows of campaign record data represented as a JSON array.
     */
    private JSONArray rows;

    /**
     * Gets the total count of campaign records.
     *
     * @return The total count of campaign records.
     */
    public int getTotalCount() {
        return totalCount;
    }

    /**
     * Sets the total count of campaign records.
     *
     * @param totalCount The total count to set.
     */
    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }

    /**
     * Gets the rows of campaign record data.
     *
     * @return The rows of campaign record data as a JSON array.
     */
    public JSONArray getRows() {
        return rows;
    }

    /**
     * Sets the rows of campaign record data.
     *
     * @param rows The rows to set as a JSON array.
     */
    public void setRows(JSONArray rows) {
        this.rows = rows;
    }
}