package com.powerdialer.dto;

import com.powerdialer.model.CampaignPriorityFilter;

import com.powerdialer.util.Constants;

import java.util.List;
/**
 * Data Transfer Object (DTO) for filtering campaign records.
 *
 * This class encapsulates the filter criteria used to retrieve campaign records.
 * It includes the campaign ID, logical operator, and a list of filter rows.
 */
public class CampaignRecordFilterDTO {

    /**
     * The unique identifier of the campaign.
     */
    private Long campaignId;

    /**
     * The logical operator used to combine filter rows (e.g., AND / OR).
     */
    private String logicOperator;

    /**
     * A list of filter rows containing field, operator, value, and field type.
     */
    private List<FilterRow> filterRows;

    /**
     * Inner class representing a filter row.
     *
     * Each filter row specifies a field, operator, value, and field type
     * for filtering campaign records.
     */
    public static class FilterRow {
        /**
         * The name of the field to filter on.
         */
        private String field;

        /**
         * The condition used for filtering (e.g., '=', '>', '<').
         */
        private Constants.FILTER_CONDITION condition;

        /**
         * The value to filter the field against.
         */
        private String value;

        /**
         * The type of the field (e.g., "STRING", "NUMBER", "DATE").
         */
        private String fieldType; // "STRING", "NUMBER", "DATE", etc.

        // Getter - Setter

        /**
         * Gets the field name.
         *
         * @return The field name.
         */
        public String getField() { return field; }

        /**
         * Sets the field name.
         *
         * @param field The field name to set.
         */
        public void setField(String field) { this.field = field; }

        /**
         * Gets the operator.
         *
         * @return The operator.
         */
        public Constants.FILTER_CONDITION getCondition() { return condition; }

        /**
         * Sets the operator.
         *
         * @param condition The operator to set.
         */
        public void setCondition(Constants.FILTER_CONDITION condition) { this.condition = condition; }

        /**
         * Gets the value.
         *
         * @return The value.
         */
        public String getValue() { return value; }

        /**
         * Sets the value.
         *
         * @param value The value to set.
         */
        public void setValue(String value) { this.value = value; }

        /**
         * Gets the field type.
         *
         * @return The field type.
         */
        public String getFieldType() { return fieldType; }

        /**
         * Sets the field type.
         *
         * @param fieldType The field type to set.
         */
        public void setFieldType(String fieldType) { this.fieldType = fieldType; }
    }

    // Getter - Setter

    /**
     * Gets the campaign ID.
     *
     * @return The campaign ID.
     */
    public Long getCampaignId() { return campaignId; }

    /**
     * Sets the campaign ID.
     *
     * @param campaignId The campaign ID to set.
     */
    public void setCampaignId(Long campaignId) { this.campaignId = campaignId; }

    /**
     * Gets the logical operator.
     *
     * @return The logical operator.
     */
    public String getLogicOperator() { return logicOperator; }

    /**
     * Sets the logical operator.
     *
     * @param logicOperator The logical operator to set.
     */
    public void setLogicOperator(String logicOperator) { this.logicOperator = logicOperator; }

    /**
     * Gets the list of filter rows.
     *
     * @return The list of filter rows.
     */
    public List<FilterRow> getFilterRows() { return filterRows; }

    /**
     * Sets the list of filter rows.
     *
     * @param filterRows The list of filter rows to set.
     */
    public void setFilterRows(List<FilterRow> filterRows) { this.filterRows = filterRows; }

    /**
     * Returns a string representation of the CampaignRecordFilterDTO.
     *
     * @return A string representation of the object.
     */
    @Override
    public String toString() {
        return "CampaignRecordFilterDTO{" +
                "campaignId=" + campaignId +
                ", logicOperator='" + logicOperator + '\'' +
                ", filterRows=" + filterRows +
                '}';
    }
}