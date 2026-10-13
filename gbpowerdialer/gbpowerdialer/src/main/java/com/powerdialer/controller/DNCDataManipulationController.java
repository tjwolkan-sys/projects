package com.powerdialer.controller;

import java.io.IOException;
import java.sql.SQLException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.powerdialer.datamanipulation.supplier.DataSupplier;
import com.powerdialer.datamanipulation.supplier.DataSupplierBuilder;
import com.powerdialer.model.Campaign;
import com.powerdialer.service.DNCDataManipulationService;
import com.powerdialer.service.TokenCampaignValidatorService;
import com.powerdialer.util.Constants;
import com.powerdialer.util.ResponseUtils;

/**
 * Controller for manipulating DNC's phone numbers
 */
@RestController
@RequestMapping(value = "/data/dnc")
public class DNCDataManipulationController {

  @Autowired
  private DNCDataManipulationService dncDataManipulationService;

  @Autowired
  private TokenCampaignValidatorService tokenCampaignValidatorService;

  /**
   * Add new phone numbers (supplied by multipart file) to default DNC list of
   * specified campaign
   */
  @RequestMapping(value = "/add/{campaignIdOrName}", method = RequestMethod.POST, headers = "content-type=multipart/form-data")
  public ResponseEntity addFromMultipart(@PathVariable("campaignIdOrName") String campaignIdOrName,
                                         @RequestParam(name = "file") MultipartFile file,
                                         @RequestHeader(value = Constants.API_TOKEN_HEADER, required = false) String campaignToken) throws IOException, SQLException {
    DataSupplier<String> dataSupplier = DataSupplierBuilder.<String>getInstance().dataType(String.class)
      .usingMultipartFileSource(file)
      .sourceFirstRowColumnNames(false)
      .build();
    Campaign campaign = tokenCampaignValidatorService.getVerifiedCampaign(campaignToken, campaignIdOrName);
    int insertedDataCount = dncDataManipulationService.processInsertDataForDefaultDnc(campaign.getId(), dataSupplier);
    return ResponseUtils.ok(insertedDataCount);
  }

  /**
   * Add new phone numbers (supplied by request body) to default DNC list of
   * specified campaign
   */
  @RequestMapping(value = "/add/{campaignIdOrName}", method = RequestMethod.POST, headers = { "content-type=text/csv",
      "content-type=application/xml", "content-type=application/json" })
  public ResponseEntity addFromRequestBody(@PathVariable("campaignIdOrName") String campaignIdOrName,
                                           @RequestHeader(value = "Content-Type", defaultValue = "") String contentType,
                                           @RequestBody String requestBody,
                                           @RequestHeader(value = Constants.API_TOKEN_HEADER, required = false) String campaignToken) throws IOException, SQLException {
    DataSupplier<String> dataSupplier = DataSupplierBuilder.<String>getInstance().dataType(String.class)
      .usingStringSource(requestBody, contentType)
      .sourceFirstRowColumnNames(false)
      .build();
    Campaign campaign = tokenCampaignValidatorService.getVerifiedCampaign(campaignToken, campaignIdOrName);
    int insertedDataCount = dncDataManipulationService.processInsertDataForDefaultDnc(campaign.getId(), dataSupplier);
    return ResponseUtils.ok(insertedDataCount);
  }

  /**
   * Add customers (supplied by multipart file of customer_no values) to
   * default DNC list of specified campaign with customer scope: each
   * customer's phone numbers are expanded into PHONE entries and matching
   * queued calls are dropped before originate.
   */
  @RequestMapping(value = "/addCustomer/{campaignIdOrName}", method = RequestMethod.POST, headers = "content-type=multipart/form-data")
  public ResponseEntity addCustomersFromMultipart(@PathVariable("campaignIdOrName") String campaignIdOrName,
                                                  @RequestParam(name = "file") MultipartFile file,
                                                  @RequestHeader(value = Constants.API_TOKEN_HEADER, required = false) String campaignToken) throws IOException, SQLException {
    DataSupplier<String> dataSupplier = DataSupplierBuilder.<String>getInstance().dataType(String.class)
      .usingMultipartFileSource(file)
      .sourceFirstRowColumnNames(false)
      .build();
    Campaign campaign = tokenCampaignValidatorService.getVerifiedCampaign(campaignToken, campaignIdOrName);
    int processedCustomerCount = dncDataManipulationService.processInsertCustomersForDefaultDnc(campaign.getId(), dataSupplier);
    return ResponseUtils.ok(processedCustomerCount);
  }

  /**
   * Add customers (supplied by request body of customer_no values) to default
   * DNC list of specified campaign with customer scope.
   */
  @RequestMapping(value = "/addCustomer/{campaignIdOrName}", method = RequestMethod.POST, headers = { "content-type=text/csv",
      "content-type=application/xml", "content-type=application/json" })
  public ResponseEntity addCustomersFromRequestBody(@PathVariable("campaignIdOrName") String campaignIdOrName,
                                                    @RequestHeader(value = "Content-Type", defaultValue = "") String contentType,
                                                    @RequestBody String requestBody,
                                                    @RequestHeader(value = Constants.API_TOKEN_HEADER, required = false) String campaignToken) throws IOException, SQLException {
    DataSupplier<String> dataSupplier = DataSupplierBuilder.<String>getInstance().dataType(String.class)
      .usingStringSource(requestBody, contentType)
      .sourceFirstRowColumnNames(false)
      .build();
    Campaign campaign = tokenCampaignValidatorService.getVerifiedCampaign(campaignToken, campaignIdOrName);
    int processedCustomerCount = dncDataManipulationService.processInsertCustomersForDefaultDnc(campaign.getId(), dataSupplier);
    return ResponseUtils.ok(processedCustomerCount);
  }

  /**
   * Disable phone numbers (supplied by multipart file) of campaign's default DNC
   * list
   */
  @RequestMapping(value = "/disable/{campaignIdOrName}", method = RequestMethod.POST, headers = "content-type=multipart/form-data")
  public ResponseEntity disableFromMultipart(@PathVariable("campaignIdOrName") String campaignIdOrName,
                                             @RequestParam(name = "file") MultipartFile file,
                                             @RequestHeader(value = Constants.API_TOKEN_HEADER, required = false) String campaignToken) throws IOException, SQLException {
    DataSupplier<String> dataSupplier = DataSupplierBuilder.<String>getInstance().dataType(String.class)
      .usingMultipartFileSource(file)
      .sourceFirstRowColumnNames(false)
      .build();
    Campaign campaign = tokenCampaignValidatorService.getVerifiedCampaign(campaignToken, campaignIdOrName);
    int disabledDataCount = dncDataManipulationService.processDisableDataForDefaultDnc(campaign.getId(), dataSupplier);
    return ResponseUtils.ok(disabledDataCount);
  }

  /**
   * Disable phone numbers (supplied by request body) of campaign's default DNC
   * list
   */
  @RequestMapping(value = "/disable/{campaignIdOrName}", method = RequestMethod.POST, headers = { "content-type=text/csv",
      "content-type=application/xml", "content-type=application/json" })
  public ResponseEntity disableFromRequestBody(@PathVariable("campaignIdOrName") String campaignIdOrName,
                                               @RequestHeader(value = "Content-Type", defaultValue = "") String contentType,
                                               @RequestBody String requestBody,
                                               @RequestHeader(value = Constants.API_TOKEN_HEADER, required = false) String campaignToken) throws IOException, SQLException {
    DataSupplier<String> dataSupplier = DataSupplierBuilder.<String>getInstance().dataType(String.class)
      .usingStringSource(requestBody, contentType)
      .sourceFirstRowColumnNames(false)
      .build();
    Campaign campaign = tokenCampaignValidatorService.getVerifiedCampaign(campaignToken, campaignIdOrName);
    int disabledDataCount = dncDataManipulationService.processDisableDataForDefaultDnc(campaign.getId(), dataSupplier);
    return ResponseUtils.ok(disabledDataCount);
  }

}
