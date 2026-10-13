package com.powerdialer.controller;

import com.powerdialer.aop.CampaignAspect;
import com.powerdialer.datamanipulation.supplier.DataSupplier;
import com.powerdialer.datamanipulation.supplier.DataSupplierBuilder;
import com.powerdialer.dto.*;
import com.powerdialer.engine.dialer.DialerFacade;
import com.powerdialer.engine.priority.PriorityRecalculationManager;
import com.powerdialer.exceptions.DuplicatedNameException;
import com.powerdialer.model.Campaign;
import com.powerdialer.request.*;
import com.powerdialer.response.CampaignFormResponse;
import com.powerdialer.response.UploadProgressResponse;
import com.powerdialer.security.PermissionUtil;
import com.powerdialer.service.*;
import com.powerdialer.util.Constants;
import com.powerdialer.util.Constants.CAMPAIGN_ACTION;
import com.powerdialer.util.ListDetailed;
import com.powerdialer.validators.ValidationResult;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.*;

import static com.powerdialer.util.Constants.CAMPAIGN_CONTROLLER_FAILED_ROWS_MAPPING;

@Controller
@RequestMapping(Constants.CAMPAIGN_CONTROLLER_MAPPING)
@PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_SUPERADMIN', 'ROLE_TENANT_ADMIN', 'ROLE_SUPERVISOR', 'ROLE_TEAMLEAD')")
public class CampaignController extends GenericController<Campaign, CampaignFormDTO, CampaignListDTO> {

  private static final Logger LOG = LoggerFactory.getLogger(CampaignController.class);

  private CampaignAspect campaignAspect;

  @Autowired
  private PermissionUtil permissionUtil;

  @Autowired
  private PhoneNumberColumnService phoneNumberColumnService;

  @Autowired
  private CampaignDataManipulationService campaignDataManipulationService;

  @Autowired
  private UploadToolFileService uploadToolFileService;

  @Autowired
  private LocationService locationService;

  @Autowired
  private DNCService dncService;

  @Autowired
  private LSCService lscService;

  @Autowired
  private SkillService skillService;

  @Autowired
  private PriorityRecalculationManager sortPriorityRecalculationManager;

  @Autowired
  private DialerFacade dialerFacade;

  @Autowired
  public CampaignController(CampaignService campaignService, CampaignAspect campaignAspect) {
    super(campaignService);
    this.campaignAspect = campaignAspect;
  }

  @PreAuthorize("@permissionUtil.canViewCampaign(#id)")
  @RequestMapping(value = "/{id}", method = RequestMethod.GET)
  @Override
  public @ResponseBody CampaignFormDTO get(@PathVariable long id) {
    return super.get(id);
  }

  @RequestMapping(value = "/list", method = RequestMethod.GET)
  @Override
  public @ResponseBody ListDetailed<CampaignListDTO> getAll(@RequestParam(value = "page", required = false) JSONObject page,
                                                            @RequestParam(value = "sort", required = false) JSONObject sort,
                                                            @RequestParam(value = "filters", required = false) JSONObject filters) {
    ListDetailed<CampaignListDTO> listDetailed = service.getAll(page, sort, filters);
    ((CampaignService) getService()).restoreCampaignFromMemento(listDetailed);
    return listDetailed;
  }

  @RequestMapping(value = "/realtimeInfo", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
  public @ResponseBody Collection<CampaignListDTO> realtimeInfo(@RequestParam(value = "ids") String ids) {
    return permissionUtil.restrictCampaignList(((CampaignService) getService()).realTimeInfo((Long[]) ConvertUtils.convert(ids.split(","), Long[].class)));
  }

  @RequestMapping(value = "/pollUpdates", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
  public @ResponseBody DeferredResult<List<?>> pollUpdates(HttpSession httpSession) {
    DeferredResult<List<?>> result = new DeferredResult<>(15000L, Collections.emptyList());
    campaignAspect.addSuspendedUpdates(httpSession, result);
    result.onCompletion(() -> campaignAspect.removeSuspendedUpdates(httpSession));
    return result;
  }

  @PreAuthorize("@permissionUtil.canManageCampaign(#campaignId)")
  @RequestMapping(value = "/action", method = RequestMethod.POST)
  public @ResponseBody void action(@RequestParam String campaignId, @RequestParam CAMPAIGN_ACTION type) {
    ((CampaignService) getService()).action(Long.valueOf(campaignId), type, Constants.CAMPAIGN_STOP_TYPE.MANUAL);
  }

  @RequestMapping(value = "/filtersTest", method = RequestMethod.POST)
  public @ResponseBody String[][] filtersTest(@RequestBody CampaignFormDTO campaign) {
    return ((CampaignService) getService()).filtersTest(campaign);
  }

  @RequestMapping(value = "/filterTest", method = RequestMethod.POST)
  public @ResponseBody void filterTest(@RequestBody FilterTestDTO filter) {
    ((CampaignService) getService()).filterTest(filter);
  }

  @RequestMapping(value = "/checkConnection", method = RequestMethod.POST)
  public @ResponseBody boolean checkConnection(@RequestParam Long id, @RequestParam String url, @RequestParam String user, @RequestParam String password) {
    return ((CampaignService) getService()).checkConnection(id, url, user, password);
  }

  @PreAuthorize("@permissionUtil.canEditCampaign(#id)")
  @RequestMapping(value = "/connect", method = RequestMethod.POST)
  public @ResponseBody boolean connect(@RequestParam(required = false) Long id, @RequestParam String url, @RequestParam String user, @RequestParam String password) {
    return ((CampaignService) getService()).connect(id, url, user, password);
  }

  @RequestMapping(value = "/tables", method = RequestMethod.POST)
  public @ResponseBody String[] getTables(@RequestParam(required = false) Long id, @RequestParam String url, @RequestParam String user, @RequestParam String password) {
    return ((CampaignService) getService()).getTables(id, url, user, password);
  }

  @PreAuthorize("@permissionUtil.canEditCampaign(#id)")
  @RequestMapping(value = "/disconnect/{id}", method = RequestMethod.POST)
  public @ResponseBody boolean disconnect(@PathVariable long id) {
    return ((CampaignService) getService()).disconnect(id);
  }

  @RequestMapping(value = "/columns", method = RequestMethod.POST)
  public @ResponseBody List<FieldDTO> getTableColumns(@RequestParam String table, @RequestParam(required = false) Long id, @RequestParam String url, @RequestParam String user,
                                                      @RequestParam String password) throws ParseException {
    return ((CampaignService) getService()).getTableColumnsWithTypes(table, id, url, user, password);
  }

  @RequestMapping(value = "/sourceTable/columns", method = RequestMethod.GET)
  public @ResponseBody
  List<FieldDTO> getTableColumns(@RequestParam Long id) throws ParseException {
    return ((CampaignService) getService()).getSourceTableColumnsWithTypes(id);
  }

  @PreAuthorize("hasAuthority('" + Constants.PERMISSION_CREATE_CAMPAIGN + "')")
  @RequestMapping(method = RequestMethod.POST, value = "/response")
  public @ResponseBody CampaignFormResponse saveCampaign(@RequestBody CampaignFormDTO d) throws DuplicatedNameException {
    CampaignFormDTO campaignFormDTO = service.save(d);
    boolean multipleSort = !StringUtils.isBlank(campaignFormDTO.getDbPriorityTable());
    boolean foreignKeyAdded = ((CampaignService) getService())
        .addForeignKeyToPriorityTableIfNeeded(campaignFormDTO.getId(), false, multipleSort, false);
    boolean foreignKeyAdditionError = ((CampaignService) getService())
        .hasForeignKeyAdditionError(false, multipleSort, false, foreignKeyAdded);
    return new CampaignFormResponse(campaignFormDTO, foreignKeyAdditionError);
  }

  @PreAuthorize("@permissionUtil.canEditCampaign(#request.campaignId)")
  @RequestMapping(method = RequestMethod.POST, value = "/sourceTable")
  public @ResponseBody void createDefaultSourceTable(@RequestBody CreateSourceTableRequest request) throws SQLException, ParseException {
    ((CampaignService) getService()).createDefaultSourceTableForCampaign(request);
  }

  @PreAuthorize("@permissionUtil.canEditCampaign(#request.campaignId)")
  @RequestMapping(method = RequestMethod.PUT, value = "/sourceTable")
  public @ResponseBody void addCustomColumnsToSourceTable(@RequestBody CustomFieldsRequest request) throws SQLException {
    ((CampaignService) getService()).addCustomColumnsToSourceTable(request);
  }

  @PreAuthorize("@permissionUtil.canEditCampaignNonTechProperties(#request.campaignId)")
  @RequestMapping(method = RequestMethod.POST, value = "/sourceTable/phoneNumberColumns")
  public @ResponseBody void createDefaultSourceTable(@RequestBody AddPhoneNumberColumnsRequest request) throws SQLException, ParseException {
    phoneNumberColumnService.createDefaultPhoneNumberColumnsForCampaign(request);
  }

  @PreAuthorize("@permissionUtil.canEditCampaign(#request.campaignId)")
  @RequestMapping(method = RequestMethod.POST, value = "/resultTable")
  public @ResponseBody void createDefaultResultTable(@RequestBody DbTableRequest request) throws SQLException, ParseException {
    ((CampaignService) getService()).createDefaultResultTableForCampaign(request);
  }

  @PreAuthorize("@permissionUtil.canEditCampaign(#request.campaignId)")
  @RequestMapping(method = RequestMethod.PUT, value = "/resultTable")
  public @ResponseBody void addDefaultResultColumns(@RequestBody DbTableRequest request) throws SQLException, ParseException {
    ((CampaignService) getService()).addDefaultResultColumnsToSourceTable(request);
  }

  @RequestMapping(method = RequestMethod.POST, value = "/checkTableName")
  public @ResponseBody Boolean checkTableExistence(@RequestBody DbTableRequest request) throws SQLException {
    return ((CampaignService) getService()).checkTableName(request);
  }

  @PreAuthorize("@permissionUtil.canEditCampaign(#id, #d)")
  @RequestMapping(value = "/{id}/response", method = RequestMethod.PUT)
  public @ResponseBody CampaignFormResponse merge(@PathVariable long id, @RequestBody CampaignFormDTO d) throws DuplicatedNameException {
    // FIX-333607: updateContext tipi ve stratejiyi init etmeden uygular; tip/strateji değişikliği kayıttan önce reddedilir.
    checkRuntimeReinitChangeAllowed(id, d);
    CampaignFormResponse response = mergeCampaign(id, d);
    ((CampaignService) service).updateContext(id);
    return response;
  }

  @PreAuthorize("hasAuthority('" + Constants.PERMISSION_CREATE_CAMPAIGN + "')")
  @RequestMapping(value = "/clone/{id}/response", method = RequestMethod.POST)
  public @ResponseBody CampaignFormResponse cloneCampaign(@PathVariable long id, @RequestBody String campaignName) throws DuplicatedNameException {
    CampaignFormDTO clone = ((CampaignService) getService()).clone(id, campaignName);
    return saveCampaign(clone);
  }

  @PreAuthorize("@permissionUtil.canEditCampaign(#id, #d)")
  @RequestMapping(value = "saveAndRestartRunningCampaign/{id}/response", method = RequestMethod.PUT)
  public @ResponseBody CampaignFormResponse mergeAndRestartRunningCampaign(@PathVariable long id, @RequestBody CampaignFormDTO d) throws DuplicatedNameException {
    CampaignFormResponse response = mergeCampaign(id, d);
    ((CampaignService) getService()).restartRunningCampaign(id);
    return response;
  }

  @PreAuthorize("@permissionUtil.canEditCampaign(#id, #d)")
  @RequestMapping(value = "saveAndReloadRunningCampaign/{id}/response", method = RequestMethod.PUT)
  public @ResponseBody CampaignFormResponse mergeAndReloadRunningCampaign(@PathVariable long id, @RequestBody CampaignFormDTO d) throws DuplicatedNameException {
    // FIX-333607: reload tipi ve stratejiyi init etmez; tip/strateji değişikliği kayıttan önce reddedilir.
    checkRuntimeReinitChangeAllowed(id, d);
    CampaignFormResponse response = mergeCampaign(id, d);
    ((CampaignService) service).updateContext(id);
    ((CampaignService) getService()).action(Long.valueOf(id), CAMPAIGN_ACTION.RELOAD, Constants.CAMPAIGN_STOP_TYPE.MANUAL);
    return response;
  }

  /**
   * FIX-333607: tip yalnız tam düzenleme yetkisiyle gönderilir; Tech/NonTech formunda {@code int} tip
   * 0 gelir ve değişiklik sayılmaz.
   */
  private void checkRuntimeReinitChangeAllowed(long id, CampaignFormDTO d) {
    boolean typeEditable = permissionUtil.hasPermissionForCampaigns(Constants.PERMISSION_EDIT_CAMPAIGN, Collections.singleton(String.valueOf(id)));
    ((CampaignService) getService()).checkCampaignTypeChangeAllowed(id, d, typeEditable);
    ((CampaignService) getService()).checkPredictiveStrategyChangeAllowed(id, d);
  }

  private CampaignFormResponse mergeCampaign(long id, CampaignFormDTO dto) {
    boolean wasMultipleSort = !StringUtils.isBlank(dto.getDbPriorityTable());
    boolean needForeignKeyUpdate = ((CampaignService) getService()).needForeignKeyUpdate(dto);
    CampaignFormDTO campaignFormDTO = service.merge(dto);
    boolean multipleSort = !StringUtils.isBlank(campaignFormDTO.getDbPriorityTable());
    boolean foreignKeyAdded =
        ((CampaignService) getService()).addForeignKeyToPriorityTableIfNeeded(id, wasMultipleSort, multipleSort, needForeignKeyUpdate);
    boolean foreignKeyAdditionError =
        ((CampaignService) getService()).hasForeignKeyAdditionError(wasMultipleSort, multipleSort, needForeignKeyUpdate, foreignKeyAdded);
    return new CampaignFormResponse(campaignFormDTO, foreignKeyAdditionError);
  }

  @PreAuthorize("hasAnyRole('ROLE_SUPERADMIN','ROLE_TENANT_ADMIN')")
  @RequestMapping(value = "/{id}", method = RequestMethod.DELETE)
  @Override
  public Long delete(@PathVariable long id) {
    return super.delete(id);
  }

  @PreAuthorize("hasAnyRole('ROLE_SUPERADMIN','ROLE_TENANT_ADMIN')")
  @RequestMapping(value = "/batch", method = RequestMethod.DELETE)
  @Override
  public List<Long> delete(@RequestParam(value = "ids") String ids) {
    return super.delete(ids);
  }

  @PreAuthorize("@permissionUtil.canEditCampaigns(#ids)")
  @RequestMapping(value = "/batch", method = RequestMethod.PUT)
  @Override
  public List<Long> setEnabled(@RequestParam(value = "ids") String ids, @RequestParam(value = "enabled") boolean enabled) {
    return super.setEnabled(ids, enabled);
  }

  @RequestMapping(value = "/getPermittedFields", method = RequestMethod.GET)
  public @ResponseBody Map<String, Set<String>> getPermittedFields() {
    Map<String, Set<String>> permissionFields = new HashMap<>();
    permissionFields.put("ALLOWED_TECH_FIELDS", Constants.ALLOWED_TECH_FIELDS);
    permissionFields.put("ALLOWED_NON_TECH_FIELDS", Constants.ALLOWED_NON_TECH_FIELDS);
    return permissionFields;
  }

  @PreAuthorize("@permissionUtil.canManageCampaign(#campaignId)")
  @RequestMapping(value = "/upload/start/{campaignId}", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity startUpload(@PathVariable("campaignId") Long campaignId,
                                    @RequestPart(name = "file") MultipartFile file,
                                    @RequestPart(name = "parameters") StartUploadParameterRequest uploadParameters) throws IOException, SQLException {
    DataSupplier<Map> dataSupplier = DataSupplierBuilder.<Map>getInstance().dataType(Map.class)
      .usingMultipartFileSource(file)
      .sourceFirstRowColumnNames(uploadParameters.isFirstRowColumnNames())
      .build();
    if (dataSupplier != null) {
      List<Map> previewRows = campaignDataManipulationService.handleStartUpload(campaignId, uploadParameters, dataSupplier);
      return ResponseEntity.ok(previewRows);
    } else {
      return ResponseEntity.badRequest().build();
    }
  }

  @PreAuthorize("@permissionUtil.canManageCampaign(#campaignId)")
  @RequestMapping(value = "/upload/process/{campaignId}/validate", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity validate(@PathVariable("campaignId") Long campaignId, @RequestBody Map<String, String> remappedColumns) throws IOException, ParseException {
    Map<Long, ValidationResult> validationResultMap = campaignDataManipulationService.validateTempFile(campaignId, remappedColumns);
    if (validationResultMap != null) {
      return ResponseEntity.ok(validationResultMap);
    } else {
      return ResponseEntity.notFound().build();
    }
  }

  @PreAuthorize("@permissionUtil.canManageCampaign(#campaignId)")
  @RequestMapping(value = "/upload/process/{campaignId}", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity processUpload(@PathVariable("campaignId") Long campaignId,
                                      @RequestBody ProcessUploadParameterRequest processUploadParameterRequest) throws SQLException, IOException {
    campaignDataManipulationService.handleProcessUpload(campaignId, processUploadParameterRequest);
    return ResponseEntity.ok().build();
  }

  @PreAuthorize("@permissionUtil.canManageCampaign(#campaignId)")
  @RequestMapping(value = "/upload/process/{campaignId}/progress", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity getUploadProgress(@PathVariable("campaignId") Long campaignId) {
    UploadProgressResponse uploadProgressAndCleanupFinished = campaignDataManipulationService.getUploadProgressAndCleanupFinished(campaignId);
    if (uploadProgressAndCleanupFinished != null) {
      return ResponseEntity.ok(uploadProgressAndCleanupFinished);
    } else {
      return ResponseEntity.noContent().build();
    }
  }

  @PreAuthorize("@permissionUtil.canManageCampaign(#campaignId)")
  @RequestMapping(value = "/upload/process/{campaignId}/previewRows", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity getRowsForPreview(@PathVariable("campaignId") Long campaignId) {
    List<Map> rowsForPreview = campaignDataManipulationService.getRowsForPreview(campaignId);
    if (rowsForPreview != null) {
      return ResponseEntity.ok(rowsForPreview);
    } else {
      return ResponseEntity.noContent().build();
    }
  }

  @RequestMapping(value = CAMPAIGN_CONTROLLER_FAILED_ROWS_MAPPING, method = RequestMethod.GET)
  public void getFailedRows(@PathVariable("filename") String filename, HttpServletResponse response) throws IOException {
    try (InputStream inputStreamForFailedRowsFile = uploadToolFileService.getInputStreamForFailedRowsFile(filename)) {
      if (inputStreamForFailedRowsFile != null) {
        response.setContentType("text/csv");
        IOUtils.copy(inputStreamForFailedRowsFile, response.getOutputStream());
      } else {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
      }
    } catch (FileNotFoundException e) {
      response.setStatus(HttpServletResponse.SC_NOT_FOUND);
    }
  }

  @PreAuthorize("@permissionUtil.canManageCampaign(#campaignId)")
  @RequestMapping(value = "/upload/process/{campaignId}/terminate", method = RequestMethod.POST)
  public ResponseEntity terminateUpload(@PathVariable("campaignId") Long campaignId) {
    UploadProgressResponse uploadProgressResponse = campaignDataManipulationService.terminateUpload(campaignId);
    if (uploadProgressResponse != null) {
      return ResponseEntity.ok(uploadProgressResponse);
    } else {
      return ResponseEntity.notFound().build();
    }
  }

  @PreAuthorize("@permissionUtil.canViewCampaign(#id)")
  @RequestMapping(value = "/{id}/locations", method = RequestMethod.GET)
  @ResponseBody
  public ListDetailed<LocationListDTO> getCampaignsLocations(@PathVariable("id") Long id) {
    return locationService.getLocationsByCampaignId(id);
  }

  @PreAuthorize("@permissionUtil.canViewCampaign(#id)")
  @RequestMapping(value = "/{id}/dncs", method = RequestMethod.GET)
  @ResponseBody
  public ListDetailed<DNCListDTO> getCampaignsDNCs(@PathVariable("id") Long id) {
    return dncService.getDNCsByCampaignId(id);
  }

  @PreAuthorize("@permissionUtil.canViewCampaign(#id)")
  @RequestMapping(value = "/{id}/lscs", method = RequestMethod.GET)
  @ResponseBody
  public ListDetailed<LSCListDTO> getCampaignsLSCs(@PathVariable("id") Long id) {
    return lscService.getLSCsByCampaignId(id);
  }

  @PreAuthorize("@permissionUtil.canViewCampaign(#id)")
  @RequestMapping(value = "/{id}/skills", method = RequestMethod.GET)
  @ResponseBody
  public ListDetailed<SkillListDTO> getCampaignsSkills(@PathVariable("id") Long id) {
    return skillService.getSkillsByCampaignId(id);
  }

  @PreAuthorize("@permissionUtil.canEditCampaign(#id)")
  @RequestMapping(value = "/recalculatePriorities/{id}", method = RequestMethod.POST)
  public @ResponseBody ResponseEntity<Constants.PRIORITY_RECALCULATION_STATE> recalculatePriorities(@PathVariable Long id) {
    try {
      Constants.PRIORITY_RECALCULATION_STATE result = sortPriorityRecalculationManager.recalculatePriority(id);
      return ResponseEntity.ok(result);
    } catch (Exception e) {
      LOG.error("Could not recalculate campaign with id " + id, e);
      throw e;
    }
 }

  @RequestMapping(value = "/{id}/lscs/priority", method = RequestMethod.PUT)
  @ResponseBody
  public ResponseEntity<?> updateLscPriorities(
          @PathVariable Long id,
          @RequestBody List<Long> lscIds) {
    try {
      ((CampaignService) getService()).updateLscPriorities(id, lscIds);
      return ResponseEntity.ok().build();
    } catch (Exception e) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
  }

}
