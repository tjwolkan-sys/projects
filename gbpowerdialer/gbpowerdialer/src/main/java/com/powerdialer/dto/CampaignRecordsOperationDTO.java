package com.powerdialer.dto;

import com.powerdialer.util.Constants;

import java.util.List;

public class CampaignRecordsOperationDTO {

    private Constants.CAMPAIGN_RECORD_OPERATION operation;
    private Constants.RECORD_DELETION_REASON reasonForDeletion;
    private String field;
    private String value;
    private List<String> recordIds;
    private List<CampaignPriorityFilterFormDTO> filterListDTO;
    private Constants.FILTER_JOIN_TYPE logicOperator;

    public Constants.CAMPAIGN_RECORD_OPERATION getOperation() {
        return operation;
    }

    public void setOperation(Constants.CAMPAIGN_RECORD_OPERATION operation) {
        this.operation = operation;
    }

    public Constants.RECORD_DELETION_REASON getReasonForDeletion() {
        return reasonForDeletion;
    }

    public void setReasonForDeletion(Constants.RECORD_DELETION_REASON reasonForDeletion) {
        this.reasonForDeletion = reasonForDeletion;
    }

    public Constants.FILTER_JOIN_TYPE getLogicOperator() {
        return logicOperator;
    }

    public void setLogicOperator(Constants.FILTER_JOIN_TYPE logicOperator) {
        this.logicOperator = logicOperator;
    }

    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public List<String> getRecordIds() {
        return recordIds;
    }

    public void setRecordIds(List<String> recordIds) {
        this.recordIds = recordIds;
    }

    public List<CampaignPriorityFilterFormDTO> getFilterListDTO() {
        return filterListDTO;
    }

    public void setFilterListDTO(List<CampaignPriorityFilterFormDTO> filterListDTO) {
        this.filterListDTO = filterListDTO;
    }
}
