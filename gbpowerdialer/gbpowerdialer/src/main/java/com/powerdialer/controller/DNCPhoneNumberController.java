package com.powerdialer.controller;

import java.util.Map;

import java.io.IOException;

import com.powerdialer.engine.DNCEngine;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import com.powerdialer.datamanipulation.supplier.DataSupplier;
import com.powerdialer.datamanipulation.supplier.DataSupplierBuilder;
import com.powerdialer.dto.DNCPhoneNumberFormDTO;
import com.powerdialer.dto.DNCPhoneNumberListDTO;
import com.powerdialer.model.DNC;
import com.powerdialer.model.DNCPhoneNumber;
import com.powerdialer.request.DncPnBatchProcessingParameters;
import com.powerdialer.request.DncPnBatchProcessingRequest;
import com.powerdialer.security.AuthUtil;
import com.powerdialer.service.DNCPhoneNumberService;
import com.powerdialer.service.DNCService;
import com.powerdialer.util.Constants;
import com.powerdialer.util.ListDetailed;

@Controller
@RequestMapping("/dncpn")
public class DNCPhoneNumberController extends GenericController<DNCPhoneNumber, DNCPhoneNumberFormDTO, DNCPhoneNumberListDTO> {

  private final String [] authorities = { Constants.ROLE_SUPERADMIN, Constants.ROLE_TENANT_ADMIN, Constants.ROLE_SUPERVISOR};

  @Autowired
  AuthUtil authUtil;

  @Autowired
  private DNCPhoneNumberService dncPhoneNumberService;

  @Autowired
  DNCService dncService;

  @Autowired
  private DNCEngine dncEngine;

  @Autowired
  public DNCPhoneNumberController(DNCPhoneNumberService dncPhoneNumberService) {
    super(dncPhoneNumberService);
  }

  @PreAuthorize("this.isDNCDefaultForPermittedCampaign(#filters) || this.isPreAuthorized()")
  @RequestMapping(value = "/list", method = RequestMethod.GET)
  public @ResponseBody
  ListDetailed<DNCPhoneNumberListDTO> getAll(@RequestParam(value = "page", required = false) JSONObject page, @RequestParam(value = "sort", required = false) JSONObject sort,
                          @RequestParam(value = "filters", required = false) JSONObject filters) {
    return service.getAll(page, sort, filters);
  }

  @Override
  public boolean isPreAuthorized() {
    return authUtil.isUserInRoles(authorities);
  }

  @PreAuthorize("this.isPreAuthorized() || this.isUserHaveRightsToEditDncList(#id)")
  @RequestMapping(value = "/dncPnBatchProcessing/{id}", method = RequestMethod.PUT)
  public ResponseEntity dncPnBatchPrecessingUpdate(@PathVariable(value = "id") String id, @RequestBody DncPnBatchProcessingRequest dncPnBatchProcessingRequest) {
    try {
      Long dncId = Long.parseLong(id);
      dncPhoneNumberService.updateDncPnBatch(dncId, dncPnBatchProcessingRequest);
      dncEngine.updateDnc(dncId);
    } catch (Exception e) {
      return new ResponseEntity(HttpStatus.BAD_REQUEST);
    }
    return new ResponseEntity(HttpStatus.OK);
  }

  public boolean isDNCDefaultForPermittedCampaign(JSONObject filters) {
    return (filters.has("dncList.id")
        && (filters.getJSONArray("dncList.id").getJSONObject(0) != null)
        && (filters.getJSONArray("dncList.id").length() == 1)
        && (filters.getJSONArray("dncList.id").getJSONObject(0).getString("term") != null)
        && dncService.isDNCDefaultForPermittedCampaign(filters.getJSONArray("dncList.id").getJSONObject(0).getString("term")))
        || !filters.has("dncList.id");
  }

  public boolean isUserHaveRightsToEditDncList(String dncListId) {
    return dncService.isDNCDefaultForPermittedCampaign(dncListId);
  }

  @PreAuthorize("this.isPreAuthorized() || this.isUserHaveRightsToEditDncList(#dncId)")
  @RequestMapping(value = "/dncPnFileImportBatchDelete/{dncId}", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Void> dncPnFileBatchDelete(@PathVariable(value = "dncId") String dncId,
                                                   @RequestPart(value = "file") MultipartFile file,
                                                   @RequestPart(value = "params") DncPnBatchProcessingParameters dncPnBatchProcessingParameters) throws IOException {
    DataSupplier dataSupplier = createDataSupplierForBatch(dncPnBatchProcessingParameters, file);
    if (dataSupplier == null) {
      return ResponseEntity.noContent().build();
    }
    DNC dnc = dncService.getEntity(Long.parseLong(dncId));
    dncPhoneNumberService.dncPnFileBatchDelete(dncPnBatchProcessingParameters, dataSupplier, dnc);
    dncEngine.updateDnc(dnc.getId());
    return ResponseEntity.ok().build();
  }

  @PreAuthorize("this.isPreAuthorized() || this.isUserHaveRightsToEditDncList(#dncId)")
  @RequestMapping(value = "/dncPnFileImportBatchAdd/{dncId}", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Void> dncPnFileBatchAdd(@PathVariable(value = "dncId") String dncId,
                                                @RequestPart(value = "file") MultipartFile file,
                                                @RequestPart(value = "params") DncPnBatchProcessingParameters dncPnBatchProcessingParameters) throws IOException {
    DataSupplier dataSupplier = createDataSupplierForBatch(dncPnBatchProcessingParameters, file);
    if (dataSupplier == null) {
      return ResponseEntity.noContent().build();
    }
    DNC dnc = dncService.getEntity(Long.parseLong(dncId));
    dncPhoneNumberService.dncPnFileBatchAdd(dncPnBatchProcessingParameters, dataSupplier, dnc);
    dncEngine.updateDnc(dnc.getId());
    return ResponseEntity.ok().build();
  }

  private DataSupplier createDataSupplierForBatch(DncPnBatchProcessingParameters dncPnBatchProcessingParameters,
                                                  MultipartFile file) throws IOException {
    return DataSupplierBuilder.<Map>getInstance().dataType(Map.class)
      .usingMultipartFileSource(file)
      .sourceFirstRowColumnNames(dncPnBatchProcessingParameters.isCheckboxResult())
      .build();
  }


}
