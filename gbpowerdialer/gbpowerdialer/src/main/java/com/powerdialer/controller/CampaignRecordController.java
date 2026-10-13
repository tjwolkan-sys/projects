package com.powerdialer.controller;

import com.powerdialer.dto.CampaignRecordFilterDTO;
import com.powerdialer.dto.CampaignRecordsOperationDTO;
import com.powerdialer.dto.CampaignTenantDTO;
import com.powerdialer.model.OperationOutcome;
import com.powerdialer.service.CampaignRecordService;
import com.powerdialer.util.Constants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.powerdialer.exceptions.MissingSelectionWarningException;


import java.sql.SQLException;
import java.util.List;
import java.util.Map;
/**
 * Controller class that lists and updates records for the Campaign Data Manipulation and Update screen.
 */
@RestController
@RequestMapping("/campaign-records")
public class CampaignRecordController {

    private final CampaignRecordService campaignRecordService;

    /**
     * Constructor for the CampaignRecordController.
     *
     * This constructor initializes the controller with the provided CampaignRecordService instance.
     *
     * @param campaignRecordService The service used to handle campaign record operations.
     */
    @Autowired
    public CampaignRecordController(CampaignRecordService campaignRecordService) {
        this.campaignRecordService = campaignRecordService;
    }

    /**
     * Retrieves campaign records for a specific tenant.
     *
     * This method is mapped to a GET request at the endpoint `/campaigns/{tenantId}`.
     * It expects a tenant ID as a path variable and invokes the `getCampaignsByTenantId` method
     * of the `CampaignRecordService` to fetch campaign data for the specified tenant.
     *
     * @param tenantId The ID of the tenant whose campaigns are to be retrieved.
     * @return A list of campaign records as `List<Map<String, Object>>`.
     */
    @RequestMapping(value = "/campaigns/{tenantId}", method = RequestMethod.GET)
    public List<Map<String, Object>> getCampaignsByTenant(@PathVariable Long tenantId) {
        return campaignRecordService.getCampaignsByTenantId(tenantId);
    }

    /**
     * Initializes tenant and campaign data.
     *
     * This method is mapped to a POST request at the endpoint `/initialize-data`.
     * It invokes the `initializeData` method of the `CampaignRecordService` to load tenant and campaign data.
     * A successful operation returns a response with the message "Tenant ve kampanya verileri yüklendi".
     *
     * @return A `ResponseEntity` containing a success message.
     * @throws SQLException If an error occurs during data initialization.
     */
    @RequestMapping(value = "/initialize-data", method = RequestMethod.GET)
    public CampaignTenantDTO initializeData() {
        return campaignRecordService.initializeData();
    }


    /**
     * Fetches records based on the provided filter criteria.
     *
     * This method is mapped to a POST request at the endpoint `/fetch`.
     * It expects a `CampaignRecordFilterDTO` object in the request body, which contains the filter criteria.
     * If the `campaignId` field in the DTO is null, a `MissingSelectionWarningException` is thrown.
     * The method attempts to fetch records using the `campaignRecordService` and handles any `SQLException`
     * by throwing a `RuntimeException`.
     *
     * @param dto The filter criteria encapsulated in a `CampaignRecordFilterDTO` object.
     * @return A list of records as a `List<Map<String, Object>>`.
     * @throws MissingSelectionWarningException If the `campaignId` field in the DTO is null.
     * @throws RuntimeException If a `SQLException` occurs during record fetching.
     */
    @PostMapping("/fetch")
    public ResponseEntity<Map<Object,List<Map<String, Object>>>> fetchRecords(@RequestBody CampaignRecordFilterDTO dto) {

        if (dto.getCampaignId() == null) {
            throw new MissingSelectionWarningException();
        }

        try {
            CampaignRecordService.FetchAllOutcome outcome = campaignRecordService.fetchAllWithTruncation(dto);
            return ResponseEntity
                    .ok()
                    .header("X-Records-Truncated", String.valueOf(outcome.isRecordsTruncated()))
                    .body(outcome.getData());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Retrieves the column names and types for a table associated with a specific campaign.
     *
     * This method is mapped to a GET request at the endpoint `/columns/{campaignId}`.
     * It expects a campaign ID as a path variable and invokes the `getTableColumnsByCampaignId` method
     * of the `CampaignRecordService` to fetch the column details.
     *
     * @param campaignId The ID of the campaign whose table columns are to be retrieved.
     * @return A list of column details as `List<Map<String, String>>`, where each map contains column name and type.
     * @throws SQLException If an error occurs during the database operation.
     */
    @GetMapping("/columns/{campaignId}")
    public List<Map<String, String>> getTableColumns(@PathVariable Long campaignId) throws SQLException {
        return campaignRecordService.getTableColumnsByCampaignId(campaignId);
    }

    /**
     * Tests the filters provided in the request body.
     *
     * This method is mapped to a POST request at the endpoint `/filter-test`.
     * It expects a `CampaignRecordFilterDTO` object in the request body and invokes the `testFilters` method
     * of the `CampaignRecordService` to test the filters.
     *
     * @param dto The filter criteria encapsulated in a `CampaignRecordFilterDTO` object.
     * @return A map containing the results of the filter test as `Map<String, Object>`.
     * @throws SQLException If an error occurs during the database operation.
     */
    @PostMapping("/filter-test")
    public Map<String, Object> testFilters(@RequestBody CampaignRecordFilterDTO dto) throws SQLException {
        return campaignRecordService.testFilters(dto);
    }

    /**
     * Simple health check endpoint.
     *
     * This method is mapped to a GET request at the endpoint `/ping`.
     * It returns a simple string message indicating that the controller is operational.
     *
     * @return A string message "CampaignRecordController is up."
     */
    @GetMapping("/ping")
    public String ping() {
        return "CampaignRecordController is up.";
    }

    @RequestMapping(value = "/{campaign_id}/apply-operation", method = RequestMethod.POST)
    public ResponseEntity<Constants.RECORD_UPDATE_OPERATION_STATUS> applyOperation(
            @RequestBody CampaignRecordsOperationDTO campaignRecordsOperationDTO,
            @PathVariable("campaign_id") Long campaignId) {
        try {
            OperationOutcome<Constants.RECORD_UPDATE_OPERATION_STATUS> outcome =
                    campaignRecordService.applyOperationWithCount(campaignId, campaignRecordsOperationDTO);
            return ResponseEntity
                    .ok()
                    .header("X-Updated-Count", String.valueOf(outcome.getAffectedCount()))
                    .body(outcome.getStatus());
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Constants.RECORD_UPDATE_OPERATION_STATUS.UPDATE_FAILED);
        }
    }
}
