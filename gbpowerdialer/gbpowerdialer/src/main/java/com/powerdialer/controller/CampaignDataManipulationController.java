package com.powerdialer.controller;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.io.IOException;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.UUID;
import java.util.concurrent.Executor;

import com.powerdialer.model.Campaign;
import com.powerdialer.service.*;
import com.powerdialer.util.Constants;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.beanutils.ConvertUtils;
import org.hibernate.validator.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.CollectionUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.powerdialer.datamanipulation.supplier.DataSupplier;
import com.powerdialer.datamanipulation.supplier.DataSupplierBuilder;
import com.powerdialer.exceptions.EmptyFiltersWarningException;
import com.powerdialer.request.DataManipulateRequest;
import com.powerdialer.util.ResponseUtils;

import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.Pattern;

/**
 * Controller for importing/exporting Campaign data.
 *
 * This controller provides endpoints for creating, reading, updating, and deleting campaign data,
 * as well as recalculating campaign priorities. It uses services to validate campaign tokens and
 * manipulate campaign data.
 */
@RestController
@RequestMapping(value = "/data/campaign")
@Validated
@Slf4j
public class CampaignDataManipulationController {
  
  @Autowired
  private CampaignDataManipulationService campaignDataManipulationService;

  @Autowired
  private TokenCampaignValidatorService tokenCampaignValidatorService;

  @Autowired
  private TokenCampaignValidatorServiceExtended tokenCampaignValidatorServiceExtended;

  @Autowired
  private CampaignRecordService campaignRecordService;

  @Autowired
  @Qualifier("uploadExecutor")
  private Executor uploadExecutor;

  @Autowired
  private UploadStatusStore uploadStatusStore;


    /**
   * Inserts data supplied via a multipart file for a specified campaign.
   *
   * @param campaignIdOrName The ID or name of the campaign.
   * @param file The multipart file containing the data to be inserted.
   * @param campaignToken The API token for campaign validation (optional).
   * @return A `ResponseEntity` containing the count of inserted records.
   * @throws SQLException If a database error occurs.
   * @throws IOException If an error occurs while processing the file.
   */
  @RequestMapping(value = "/create/{campaignIdOrName}", method = RequestMethod.POST, headers = "content-type=multipart/form-data")
  public ResponseEntity createFromMultipart(@PathVariable("campaignIdOrName") String campaignIdOrName,
                                            @RequestParam(name = "file") MultipartFile file,
                                            @RequestHeader(value = Constants.API_TOKEN_HEADER, required = false) String campaignToken) throws SQLException, IOException {
    DataSupplier<Map> dataSupplier = DataSupplierBuilder.<Map>getInstance().dataType(Map.class)
      .usingMultipartFileSource(file)
      .sourceFirstRowColumnNames(true)
      .build();
    Campaign campaign = tokenCampaignValidatorService.getVerifiedCampaign(campaignToken, campaignIdOrName);
    int insertedDataCount = campaignDataManipulationService.processInsertDataForCampaign(campaign, dataSupplier);
    return ResponseUtils.ok(insertedDataCount);
  }

    @PostMapping(value = "/create/streaming/{campaignIdOrName}", headers = "content-type=multipart/form-data")
    public ResponseEntity createFromMultipartStreaming(
            @PathVariable("campaignIdOrName") String campaignIdOrName,
            @RequestParam(name = "file") MultipartFile file,
            @RequestHeader(value = Constants.API_TOKEN_HEADER, required = false) String campaignToken)
            throws SQLException, IOException {

        long start = System.currentTimeMillis();
        log.info("[STREAM-UPLOAD] Started createFromMultipartStreaming for campaign: {}", campaignIdOrName);

        long fileSizeMB = file.getSize() / (1024 * 1024);
        log.info("[STREAM-UPLOAD] File received: {} MB", fileSizeMB);

        long t1 = System.currentTimeMillis();

        DataSupplier<Map<String, Object>> dataSupplier = DataSupplierBuilder.<Map<String, Object>>getInstance()
                .dataType((Class<Map<String, Object>>) (Class<?>) Map.class)
                .usingMultipartFileSource(file)
                .sourceFirstRowColumnNames(true)
                .build();

        long t2 = System.currentTimeMillis();
        log.info("[STREAM-UPLOAD] DataSupplier creation took {} ms", (t2 - t1));

        long t3 = System.currentTimeMillis();
        Campaign campaign = tokenCampaignValidatorService.getVerifiedCampaign(campaignToken, campaignIdOrName);
        long t4 = System.currentTimeMillis();
        log.info("[STREAM-UPLOAD] Campaign validation took {} ms", (t4 - t3));

        long t5 = System.currentTimeMillis();
        int insertedDataCount = campaignDataManipulationService.processInsertDataExtendedForCampaign(campaign, dataSupplier);
        long t6 = System.currentTimeMillis();
        log.info("[STREAM-UPLOAD] Data insertion took {} ms", (t6 - t5));

        long total = System.currentTimeMillis() - start;
        log.info("[STREAM-UPLOAD] Total processing time: {} ms (≈ {} seconds)", total, total / 1000.0);

        return ResponseUtils.ok(insertedDataCount);
    }


    @RequestMapping(value = "/create/{campaignIdOrName}", method = RequestMethod.POST, headers = { "content-type=text/csv",
            "content-type=application/xml", "content-type=application/json" })
    public ResponseEntity createFromRequestBody(@PathVariable("campaignIdOrName") String campaignIdOrName,
                                                @RequestHeader(value = "Content-Type", defaultValue = "") String contentType,
                                                HttpServletRequest request,
                                                @RequestHeader(value = Constants.API_TOKEN_HEADER, required = false) String campaignToken) throws IOException, SQLException {

        long start = System.currentTimeMillis();
        log.info("Started createFromRequestBody for campaign: {}", campaignIdOrName);

        InputStream inputStream = request.getInputStream();

        DataSupplier<Map<String, Object>> dataSupplier = DataSupplierBuilder.<Map<String, Object>>getInstance()
                .dataType((Class<Map<String, Object>>) (Class<?>) Map.class)
                .usingInputStreamSource(inputStream, contentType)
                .sourceFirstRowColumnNames(true)
                .build();


        long t1 = System.currentTimeMillis();
        log.info("createFromRequestBody DataSupplier creation took {} ms", (t1 - start));

        Campaign campaign = tokenCampaignValidatorServiceExtended.getVerifiedCampaign(campaignToken, campaignIdOrName);

        long t2 = System.currentTimeMillis();
        int insertedDataCount = campaignDataManipulationService.processInsertDataExtendedForCampaign(campaign, dataSupplier);
        long t3 = System.currentTimeMillis();
        log.info("createFromRequestBody Data insertion took {} ms", (t3 - t2));

        long total = System.currentTimeMillis() - start;
        log.info("createFromRequestBody Total processing time: {} ms (≈ {} seconds) inserted data count: {}", total, total / 1000.0, insertedDataCount);
        return ResponseUtils.ok(insertedDataCount);
    }

//    /**
//   * Inserts data supplied via the request body for a specified campaign.
//   *
//   * @param campaignIdOrName The ID or name of the campaign.
//   * @param contentType The content type of the request body (e.g., text/csv, application/xml, application/json).
//   * @param requestBody The request body containing the data to be inserted.
//   * @param campaignToken The API token for campaign validation (optional).
//   * @return A `ResponseEntity` containing the count of inserted records.
//   * @throws IOException If an error occurs while processing the request body.
//   * @throws SQLException If a database error occurs.
//   */
//  @RequestMapping(value = "/create/{campaignIdOrName}", method = RequestMethod.POST, headers = { "content-type=text/csv",
//      "content-type=application/xml", "content-type=application/json" })
//  public ResponseEntity createFromRequestBody(@PathVariable("campaignIdOrName") String campaignIdOrName,
//                                              @RequestHeader(value = "Content-Type", defaultValue = "") String contentType,
//                                              @RequestBody String requestBody,
//                                              @RequestHeader(value = Constants.API_TOKEN_HEADER, required = false) String campaignToken) throws IOException, SQLException {
//
//
//      long start = System.currentTimeMillis();
//      log.info("Started createFromRequestBody for campaign: {}", campaignIdOrName);
//
//      DataSupplier<Map<String, Object>> dataSupplier = DataSupplierBuilder.<Map<String, Object>>getInstance()
//              .dataType((Class<Map<String, Object>>) (Class<?>) Map.class)
//      .usingStringSource(requestBody, contentType)
//      .sourceFirstRowColumnNames(true)
//      .build();
//
//      long t1 = System.currentTimeMillis();
//      log.info("createFromRequestBody DataSupplier creation took {} ms", (start - t1));
//
//      Campaign campaign = tokenCampaignValidatorService.getVerifiedCampaign(campaignToken, campaignIdOrName);
//
//      long t2 = System.currentTimeMillis();
//      int insertedDataCount = campaignDataManipulationService.processInsertDataExtendedForCampaign(campaign, dataSupplier);
//      long t3 = System.currentTimeMillis();
//      log.info("createFromRequestBody Data insertion took {} ms", (t3 - t2));
//
//      long total = System.currentTimeMillis() - start;
//      log.info("createFromRequestBody Total processing time: {} ms (≈ {} seconds) inserted data count: {}", total, total / 1000.0, insertedDataCount);
//
//      return ResponseUtils.ok(insertedDataCount);
//  }
//


    @PostMapping(
            value = "/create/{campaignIdOrName}",
            consumes = { MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE, "text/csv" },
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<?> createAsync(
            @PathVariable String campaignIdOrName,
            @RequestHeader(value = Constants.API_TOKEN_HEADER, required = false) String campaignToken,
            @RequestHeader("Content-Type") String contentType,
            @RequestBody String requestBody
    ) {

        String uploadId = UUID.randomUUID().toString();
        uploadStatusStore.markQueued(uploadId);

        Campaign campaign =
                tokenCampaignValidatorService.getVerifiedCampaign(campaignToken, campaignIdOrName);

        uploadExecutor.execute(() -> {
            try {
                uploadStatusStore.markRunning(uploadId);

                DataSupplier<Map<String,Object>> supplier =
                        DataSupplierBuilder.<Map<String,Object>>getInstance()
                                .dataType((Class<Map<String,Object>>) (Class<?>) Map.class)
                                .usingStringSource(requestBody, contentType)
                                .sourceFirstRowColumnNames(true)
                                .build();

                int inserted = campaignDataManipulationService
                        .processInsertDataExtendedForCampaign(campaign, supplier);

                uploadStatusStore.markSuccess(uploadId, inserted);
            } catch (Exception ex) {
                uploadStatusStore.markFailed(uploadId, ex.getMessage());
            }
        });

        Map<String, Object> response = new HashMap<>();
        response.put("uploadId", uploadId);
        response.put("statusUrl", "/api/uploads/" + uploadId + "/status");

        return ResponseEntity.accepted().body(response);
    }

    @GetMapping("/api/uploads/{uploadId}/status")
    public ResponseEntity<?> getStatus(@PathVariable String uploadId) {
        return ResponseEntity.ok(uploadStatusStore.get(uploadId)); // queued | running | success | failed + counts
    }

    /**
   * Imports data from a specified campaign using filters.
   *
   * @param campaignIdOrName The ID or name of the campaign.
   * @param dataManipulateRequest The request containing filter criteria.
   * @param campaignToken The API token for campaign validation (optional).
   * @return A `ResponseEntity` containing the imported data.
   * @throws SQLException If a database error occurs.
   * @throws EmptyFiltersWarningException If no filters are provided.
   */
  @RequestMapping(value = "/read/{campaignIdOrName}", method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity read(@PathVariable("campaignIdOrName") String campaignIdOrName,
                             @RequestBody DataManipulateRequest dataManipulateRequest,
                             @RequestHeader(value = Constants.API_TOKEN_HEADER, required = false) String campaignToken) throws SQLException {
    Campaign campaign = tokenCampaignValidatorService.getVerifiedCampaign(campaignToken, campaignIdOrName);
    List<Map<String, String>> data = campaignDataManipulationService.readData(campaign, dataManipulateRequest.getFilters());
    if (CollectionUtils.isEmpty(dataManipulateRequest.getFilters())) {
      throw new EmptyFiltersWarningException(data);
    } else {
      return ResponseUtils.ok(data);
    }
  }

  /**
   * Updates data for a specified campaign using filters.
   *
   * @param campaignIdOrName The ID or name of the campaign.
   * @param dataManipulateRequest The request containing filter criteria and data to be updated.
   * @param campaignToken The API token for campaign validation (optional).
   * @return A `ResponseEntity` containing the count of updated records.
   * @throws SQLException If a database error occurs.
   * @throws ParseException If an error occurs while parsing data.
   * @throws EmptyFiltersWarningException If no filters are provided.
   */
  @RequestMapping(value = "/update/{campaignIdOrName}", method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity update(@PathVariable("campaignIdOrName") String campaignIdOrName,
                               @RequestBody DataManipulateRequest dataManipulateRequest,
                               @RequestHeader(value = Constants.API_TOKEN_HEADER, required = false) String campaignToken) throws SQLException, ParseException {
    Campaign campaign = tokenCampaignValidatorService.getVerifiedCampaign(campaignToken, campaignIdOrName);
    int updatedDataCount = campaignDataManipulationService.updateData(campaign, dataManipulateRequest.getFilters(), dataManipulateRequest.getData());
    if (CollectionUtils.isEmpty(dataManipulateRequest.getFilters())) {
      throw new EmptyFiltersWarningException(updatedDataCount);
    } else {
      return ResponseUtils.ok(updatedDataCount);
    }
  }

  /**
   * Deletes data from a specified campaign using filters.
   *
   * @param campaignIdOrName The ID or name of the campaign.
   * @param dataManipulateRequest The request containing filter criteria.
   * @param campaignToken The API token for campaign validation (optional).
   * @return A `ResponseEntity` containing the count of deleted records.
   * @throws SQLException If a database error occurs.
   * @throws EmptyFiltersWarningException If no filters are provided.
   */
  @RequestMapping(value = "/delete/{campaignIdOrName}", method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity delete(@PathVariable("campaignIdOrName") String campaignIdOrName,
                               @RequestBody DataManipulateRequest dataManipulateRequest,
                               @RequestHeader(value = Constants.API_TOKEN_HEADER, required = false) String campaignToken) throws SQLException {
    Campaign campaign = tokenCampaignValidatorService.getVerifiedCampaign(campaignToken, campaignIdOrName);
    int deletedDataCount = campaignDataManipulationService.deleteData(campaign, dataManipulateRequest.getFilters());
    if (CollectionUtils.isEmpty(dataManipulateRequest.getFilters())) {
      throw new EmptyFiltersWarningException(deletedDataCount);
    } else {
      return ResponseUtils.ok(deletedDataCount);
    }
  }

  /**
    * Recalculate priorities for specified campaigns using API token
    * @param ids comma separated ids
    * @param campaignToken campaign API token
   */
  @RequestMapping(value = "/recalculatePriorities", method = RequestMethod.POST)
  @ResponseBody
  public ResponseEntity<Void> recalculatePriorities(
      @RequestParam(value = "ids")
      @NotBlank(message = "ids must not be blank")
      @Pattern(regexp = Constants.COMMA_SEPARATED_NUMBERS_PATTERN, message = "ids must be numbers separated by commas")
      String ids,

      @RequestHeader(value = Constants.API_TOKEN_HEADER, required = false)
      String campaignToken
  ) {
    campaignDataManipulationService.recalculateCampaignsPriorities((Long[]) ConvertUtils.convert(ids.split(","), Long[].class), campaignToken);
    return ResponseEntity.ok().build();
  }


}
