package com.powerdialer.service;

import static com.powerdialer.util.Constants.APP_CONFIG;
import static com.powerdialer.util.Constants.APP_CONFIG_REST_FETCH_LIMIT;

import java.sql.Connection;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.stream.Collectors;

import java.io.IOException;
import java.nio.channels.ClosedByInterruptException;
import java.sql.SQLException;
import java.text.ParseException;
import javax.servlet.ServletContext;
import javax.xml.stream.XMLStreamException;

import com.powerdialer.engine.db.DBEngine;
import com.powerdialer.exceptions.InvalidApiTokenException;
import com.powerdialer.util.EnCipherUtils;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;

import com.fasterxml.jackson.core.JsonParseException;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.dozer.Mapper;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StopWatch;
import org.springframework.util.StringUtils;

import com.powerdialer.datamanipulation.DataManipulationStateHolder;
import com.powerdialer.datamanipulation.antiduplication.AntiDuplicationContextFactory;
import com.powerdialer.datamanipulation.antiduplication.IAntiDuplicationContext;
import com.powerdialer.datamanipulation.supplier.DataSupplier;
import com.powerdialer.datamanipulation.supplier.DataSupplierBuilder;
import com.powerdialer.datamanipulation.supplier.EnhancedMapDataSupplier;
import com.powerdialer.datamanipulation.supplier.PushBackDataSupplier;
import com.powerdialer.dto.FieldDTO;
import com.powerdialer.engine.db.CampaignDbOperations;
import com.powerdialer.engine.priority.PriorityRecalculationManager;
import com.powerdialer.exceptions.BadContentException;
import com.powerdialer.exceptions.BadFilterConditionException;
import com.powerdialer.exceptions.BadRequestColumnName;
import com.powerdialer.exceptions.EmptyDataException;
import com.powerdialer.exceptions.FetchLimitExceedException;
import com.powerdialer.exceptions.MissingFieldInFilterException;
import com.powerdialer.exceptions.MultipartFileProcessingException;
import com.powerdialer.exceptions.PreconditionFailedException;
import com.powerdialer.exceptions.PriorityRecalculationStartException;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.Filter;
import com.powerdialer.model.UploadToolContext;
import com.powerdialer.request.DataManipulateRequest;
import com.powerdialer.request.ProcessUploadParameterRequest;
import com.powerdialer.request.StartUploadParameterRequest;
import com.powerdialer.response.UploadProgressResponse;
import com.powerdialer.util.Constants;
import com.powerdialer.util.LoggerDecorator;
import com.powerdialer.validators.MapValuesTypesValidator;
import com.powerdialer.validators.ValidationResult;
import com.powerdialer.validators.Validator;

/**
 * Service for importing/exporting Campaign data
 */
@Service
@Slf4j
public class CampaignDataManipulationService {

    public final static int BULK_SIZE = 500;
    private final LoggerDecorator LOG;

    private ServletContext servletContext;

    private CampaignDbOperations campaignDbOperations;

    private CampaignService campaignService;

    private Mapper mapper;

    private UploadToolFileService uploadToolFileService;

    private final AntiDuplicationContextFactory antiDuplicationContextFactory;

    private final ExecutorService uploadExecutor;

    private final DataManipulationStateHolder dataManipulationStateHolder;

    private final PriorityRecalculationManager sortPriorityRecalculationManager;

    private final TokenCampaignValidatorService tokenCampaignValidatorService;

    private final DBEngine dbEngine;

    private final EnCipherUtils enCipherUtils;

    @Autowired
    public CampaignDataManipulationService(LoggerDecorator loggerDecorator,
                                           UploadToolFileService uploadToolFileService,
                                           Mapper mapper,
                                           CampaignService campaignService,
                                           CampaignDbOperations campaignDbOperations,
                                           ServletContext servletContext,
                                           AntiDuplicationContextFactory antiDuplicationContextFactory,
                                           DataManipulationStateHolder dataManipulationStateHolder,
                                           PriorityRecalculationManager sortPriorityRecalculationManager,
                                           TokenCampaignValidatorService tokenCampaignValidatorService, DBEngine dbEngine, EnCipherUtils enCipherUtils) {
        this.LOG = loggerDecorator.getLogger(CampaignDataManipulationService.class);
        this.uploadToolFileService = uploadToolFileService;
        this.mapper = mapper;
        this.campaignService = campaignService;
        this.campaignDbOperations = campaignDbOperations;
        this.servletContext = servletContext;
        this.antiDuplicationContextFactory = antiDuplicationContextFactory;
        this.dataManipulationStateHolder = dataManipulationStateHolder;
        this.sortPriorityRecalculationManager = sortPriorityRecalculationManager;
        this.tokenCampaignValidatorService = tokenCampaignValidatorService;
        this.dbEngine = dbEngine;
        this.enCipherUtils = enCipherUtils;
        ThreadFactory threadFactory = new BasicThreadFactory.Builder().namingPattern("data-upload-%d")
                .daemon(true)
                .build();
        uploadExecutor = Executors.newFixedThreadPool(Runtime.getRuntime()
                .availableProcessors(), threadFactory);
    }

    /**
     * Insert all add supplied by {@link DataSupplier}
     *
     * @param campaign
     * @param dataSupplier
     */
    @Transactional(readOnly = true)
    public int processInsertDataForCampaign(Campaign campaign, DataSupplier<Map> dataSupplier) throws SQLException {
        int dataInsertedCount = 0;
        dataManipulationStateHolder.addApiUploadProcess(campaign.getId());
        try {
            Hibernate.initialize(campaign);
            List<Map> bulk = new ArrayList<>();
            Map object = dataSupplier.getNextObject();
            while (object != null) {
                bulk.add(object);
                object = dataSupplier.getNextObject();
                if (bulk.size() >= BULK_SIZE) {
                    dataInsertedCount += bulk.size();
                    insertData(campaign, bulk);
                    bulk.clear();
                }
            }
            if (!bulk.isEmpty()) {
                insertData(campaign, bulk);
                dataInsertedCount += bulk.size();
            }
            sortPriorityRecalculationManager.onDataUploaded(campaign.getId());
        } catch (JsonParseException e) {
            throw new BadContentException(e.getMessage(), e);
        } catch (IOException e) {
            if (e.getCause() instanceof XMLStreamException) {
                throw new BadContentException(e.getCause().getMessage(), e.getCause());
            } else {
                throw new MultipartFileProcessingException("", e);
            }
        } finally {
            dataManipulationStateHolder.removeApiUploadProcess(campaign.getId());
        }
        return dataInsertedCount;
    }


    public int processInsertDataExtendedForCampaign(Campaign campaign,
                                                    DataSupplier<Map<String, Object>> dataSupplier) throws SQLException {
        int totalInserted = 0;
        dataManipulationStateHolder.addApiUploadProcess(campaign.getId());
        Connection con = null;

        try {
            con = dbEngine.getDataSource(
                    campaign.getId(),
                    campaign.getDbConnectionString(),
                    campaign.getDbUsername(),
                    enCipherUtils.decryptPassword(campaign.getDbPassword())
            ).getConnection();

            con.setAutoCommit(false);
            Hibernate.initialize(campaign);

            List<Map<String, Object>> bulk = new ArrayList<>(BULK_SIZE);
            Map<String, Object> record = dataSupplier.getNextObject();

            while (record != null) {
                bulk.add(record);

                if (bulk.size() >= BULK_SIZE) {
                    insertDataExtended(campaign, bulk, con);
                    totalInserted += bulk.size();
                    bulk.clear();
                }

                record = dataSupplier.getNextObject();
            }

            if (!bulk.isEmpty()) {
                insertDataExtended(campaign, bulk, con);
                totalInserted += bulk.size();
            }

            con.commit();
            sortPriorityRecalculationManager.onDataUploaded(campaign.getId());
            log.info("✅ Insert completed for campaign {} ({} total rows)", campaign.getName(), totalInserted);
        } catch (SQLException sqlEx) {
            safeRollback(con, campaign);
            log.error("❌ SQL error during bulk insert for {}: {}", campaign.getName(), sqlEx.getMessage(), sqlEx);
            throw sqlEx;
        } catch (Exception ex) {
            safeRollback(con, campaign);
            log.error("❌ Unexpected error while inserting for {}: {}", campaign.getName(), ex.getMessage(), ex);
            throw new RuntimeException("Bulk insert failed for campaign " + campaign.getName(), ex);
        } finally {
            safeClose(con);
            dataManipulationStateHolder.removeApiUploadProcess(campaign.getId());
        }

        return totalInserted;
    }

    private void safeRollback(Connection con, Campaign campaign) {
        if (con != null) {
            try {
                con.rollback();
                log.warn("⚠️ Transaction rolled back for campaign {}", campaign.getName());
            } catch (SQLException e) {
                log.error("❌ Rollback failed for campaign {}: {}", campaign.getName(), e.getMessage(), e);
            }
        }
    }

    private void safeClose(Connection con) {
        if (con != null) {
            try {
                con.close();
            } catch (SQLException e) {
                log.error("❌ Error closing connection: {}", e.getMessage(), e);
            }
        }
    }

    /**
     * Process bulk insert for campaign
     *
     * @param campaign
     * @param bulk
     */
    private void insertData(Campaign campaign, List<Map> bulk) throws SQLException {
        String insertionBulkId = "bulk-" + UUID.randomUUID().toString();
        campaignDbOperations.insertDataForCampaign(campaign, bulk);
        LOG.info("Data inserted for " + campaign + ", Bulk size = " + bulk.size() + ", Bulk id = " + insertionBulkId, Constants.LOG_MODULE.CAMPAIGN, campaign);
    }

    private int insertDataExtended(Campaign campaign, List<Map<String, Object>> bulk, Connection con) throws SQLException {
        if (CollectionUtils.isEmpty(bulk)) return 0;

        String insertionBulkId = "bulk-" + UUID.randomUUID();
        long startTime = System.currentTimeMillis();

        campaignDbOperations.insertDataForCampaignExtended(campaign, bulk, con);

        long duration = System.currentTimeMillis() - startTime;
        log.info("Inserted {} records (bulkId={}) in {} ms", bulk.size(), insertionBulkId, duration);

        return bulk.size();
    }

    /**
     * Read data for specified campaign and using filters
     */
    @Transactional(readOnly = true)
    public List<Map<String, String>> readData(Campaign campaign, List<DataManipulateRequest.Filter> filtersRequest) throws SQLException {
        Hibernate.initialize(campaign);
        List<Filter> filters;
        if (filtersRequest != null) {
            filters = validateAndConvertFilters(filtersRequest);
        } else {
            filters = new ArrayList<>();
        }
        if (campaignDbOperations.checkFilterFields(campaign, filters)) {
            checkQueryLimit(campaign, filters);
            JSONArray jsonArray = campaignDbOperations.fetchDataByFilters(campaign, filters, Constants.FILTER_JOIN_TYPE.AND, 0);
            List<Map<String, String>> result = new ArrayList<>();
            if (jsonArray.length() > 0)
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject jsonObject = jsonArray.getJSONObject(i);
                    Map<String, String> call = new HashMap<>();
                    Iterator<String> keysIterator = jsonObject.keys();
                    while (keysIterator.hasNext()) {
                        String key = keysIterator.next();
                        call.put(key, jsonObject.get(key).toString());
                    }
                    result.add(call);
                }
            return result;
        } else {
            throw new BadRequestColumnName("Filters have non campaign's table's columns. " + campaign);
        }
    }

    /**
     * Check query limit according to application.properties
     *
     * @param campaign
     * @param filters
     * @throws FetchLimitExceedException if limit exceed.
     */
    @SuppressWarnings("unchecked")
    private void checkQueryLimit(Campaign campaign, List<Filter> filters) throws SQLException {
        int count = campaignDbOperations.countDataByFilters(campaign, filters, Constants.FILTER_JOIN_TYPE.AND);
        Map<String, String> appConfig = (Map<String, String>) servletContext.getAttribute(APP_CONFIG);
        int limit = Integer.parseInt(appConfig.get(APP_CONFIG_REST_FETCH_LIMIT));
        if (count > limit) {
            throw new FetchLimitExceedException("Current count is: " + count + " ,limit is: " + limit);
        }
    }

    /**
     * Update campaign data using filters
     *
     * @throws SQLException
     */
    @Transactional(readOnly = false)
    public int updateData(Campaign campaign, List<DataManipulateRequest.Filter> filtersRequest, Map<String, Object> dataToUpdate) throws SQLException, ParseException {
        if (dataToUpdate == null || dataToUpdate.isEmpty())
            throw new EmptyDataException();
        Hibernate.initialize(campaign);
        List<Filter> filters;
        if (filtersRequest != null) {
            filters = validateAndConvertFilters(filtersRequest);
        } else {
            filters = new ArrayList<>();
        }
        return campaignDbOperations.updateCampaignData(campaign, filters, Constants.FILTER_JOIN_TYPE.AND, dataToUpdate);
    }

    private List<Filter> validateAndConvertFilters(List<DataManipulateRequest.Filter> filtersToConvert) {
        List<Filter> filters = new ArrayList<>();
        for (DataManipulateRequest.Filter filterRequest : filtersToConvert) {
            try {
                Filter filter = mapper.map(filterRequest.convertToFilterDto(), Filter.class);
                if (validateFilter(filter)) {
                    filters.add(filter);
                } else {
                    throw new MissingFieldInFilterException("Filter: " + filterRequest + " do not have required fields");
                }
            } catch (ParseException e) {
                throw new BadFilterConditionException(e.getMessage(), e);
            }
        }
        return filters;
    }

    /**
     * Check is filter has expected fields
     *
     * @param filter
     * @return true if filter pass validation
     */
    private boolean validateFilter(Filter filter) {
        if (filter.getField() != null && !StringUtils.isEmpty(filter.getField().getFieldName())) {
            if (filter.getCondition() != null && filter.getCondition().isNullCheck()) {
                return true;
            }
            if (!Constants.FILTER_CONDITION.EQUAL.equals(filter.getCondition()) && !Constants.FILTER_CONDITION.NOTEQUAL.equals(filter
                    .getCondition())) {
                return !StringUtils.isEmpty(filter.getValue());
            }
            return true;
        }
        return false;
    }

    /**
     * Delete campaign data using filters
     *
     * @throws SQLException
     */
    @Transactional(readOnly = false)
    public int deleteData(Campaign campaign, List<DataManipulateRequest.Filter> filtersRequest) throws SQLException {
        campaignService.checkCampaignsAreStopped(Collections.singletonList(campaign.getId()));
        Hibernate.initialize(campaign);
        List<Filter> filters;
        if (filtersRequest != null) {
            filters = validateAndConvertFilters(filtersRequest);
        } else {
            filters = new ArrayList<>();
        }
        if (campaignDbOperations.checkFilterFields(campaign, filters)) {
            return campaignDbOperations.deleteCampaignData(campaign, filters, Constants.FILTER_JOIN_TYPE.AND);
        } else {
            throw new BadRequestColumnName("Filters have non campaign's table's columns. " + campaign);
        }
    }

    private String generateTempFileName(Long campaignId) {
        return UUID.randomUUID().toString() + "-campaign-" + campaignId + ".csv";
    }

    /**
     * Start upload for specified campaign. This method do not upload data to DB but store data in temp file
     *
     * @param campaignId
     * @param startUploadParameters
     * @param originalDataSupplier
     * @return
     * @throws IOException
     * @throws SQLException
     */
    public List<Map> handleStartUpload(Long campaignId,
                                       StartUploadParameterRequest startUploadParameters,
                                       DataSupplier<Map> originalDataSupplier) throws IOException, SQLException {
        if (dataManipulationStateHolder.getUploadToolContext(campaignId) != null) {
            throw new PreconditionFailedException("Upload for campaign with id: " + campaignId + " is already started");
        }
        StopWatch stopWatch = new StopWatch("handleFileUploadStopwatch");
        stopWatch.start();
        Campaign campaign = campaignService.getInitializedCampaign(campaignId);
        String tempFilename = generateTempFileName(campaign.getId());
        List<Map> previewRows;
        try {
            PushBackDataSupplier<Map> pushBackDataSupplier = new PushBackDataSupplier<>(originalDataSupplier);
            Map firstRow = pushBackDataSupplier.getNextObject();
            pushBackDataSupplier.unread(firstRow);
            if (isDbIdColumnShouldBeAdded(firstRow, startUploadParameters)) {
                previewRows = uploadToolFileService.saveAsTempCsvFile(enhanceDataSupplierWithDbIdColumn(pushBackDataSupplier, startUploadParameters, campaign), tempFilename);
            } else {
                previewRows = uploadToolFileService.saveAsTempCsvFile(pushBackDataSupplier, tempFilename);
            }
            UploadToolContext uploadToolContext = dataManipulationStateHolder.getOrCreateUploadToolContext(campaign);
            uploadToolContext.setStartParameters(startUploadParameters);
            uploadToolContext.setStartUploadTempFilename(tempFilename);
            uploadToolContext.setTotalRowsCount(pushBackDataSupplier.getReadCount());
            uploadToolContext.setRowsForPreview(previewRows);
        } catch (Exception e) {
            LOG.error("Upload start failed", e, Constants.LOG_MODULE.CAMPAIGN, campaign);
            dataManipulationStateHolder.removeUploadToolContext(campaignId);
            throw e;
        } finally {
            stopWatch.stop();
            LOG.info("Upload started. Stored temp file: " + tempFilename + ". Millis spent: " + stopWatch.getTotalTimeMillis(), Constants.LOG_MODULE.CAMPAIGN, campaign);
        }
        return previewRows;
    }

    private boolean isDbIdColumnShouldBeAdded(Map firstRow, StartUploadParameterRequest uploadParameters) {
        String dbIdColumnName = uploadParameters.getDbIdColumnName();
        return !StringUtils.isEmpty(dbIdColumnName) && firstRow.containsKey(dbIdColumnName) && StringUtils.isEmpty(firstRow.get(dbIdColumnName));
    }

    private DataSupplier<Map> enhanceDataSupplierWithDbIdColumn(DataSupplier<Map> originalDataSupplier,
                                                                StartUploadParameterRequest uploadParameters,
                                                                Campaign campaign) {
        String dbIdColumnName = uploadParameters.getDbIdColumnName();
        try {
            long dbIdColumnValue = Optional.ofNullable(campaignDbOperations.aggregateCampaignSourceTableColumn(dbIdColumnName, "MAX", campaign))
                    .map(Long::valueOf)
                    .map(value -> value + 1)
                    .orElse(1L);
            return new EnhancedMapDataSupplier(originalDataSupplier, Collections.singletonMap(dbIdColumnName, Long.toString(dbIdColumnValue)));
        } catch (SQLException e) {
            LOG.warn("Can't enhance data supplier with column: " + dbIdColumnName + ". Cause: " + e.getMessage(), Constants.LOG_MODULE.CAMPAIGN, campaign);
            return originalDataSupplier;
        }
    }

    /**
     * Start uploading data from temp file to DB using specified parameters and UploadToolContext
     *
     * @param campaignId
     * @param processUploadParameterRequest
     * @throws SQLException
     * @throws IOException
     */
    public void handleProcessUpload(Long campaignId, ProcessUploadParameterRequest processUploadParameterRequest) throws SQLException, IOException {
        UploadToolContext uploadToolContext = dataManipulationStateHolder.getUploadToolContext(campaignId);
        String tempFilePath = uploadToolFileService.getTempFilePath(uploadToolContext.getStartUploadTempFilename());
        DataSupplier<Map> dataSupplier = DataSupplierBuilder.<Map>getInstance().dataType(Map.class)
                .usingFile(tempFilePath)
                .sourceFirstRowColumnNames(true)
                .build();
        IAntiDuplicationContext antiDuplicationContext = antiDuplicationContextFactory.createContext(uploadToolContext.getCampaign(),
                processUploadParameterRequest
                        .getAntiDuplication());
        Future<?> uploadFuture = uploadExecutor.submit(() -> handleProcessUploadInternal(processUploadParameterRequest, uploadToolContext, dataSupplier, antiDuplicationContext));
        uploadToolContext.setUploadFuture(uploadFuture);
    }

    private void handleProcessUploadInternal(ProcessUploadParameterRequest processUploadParameterRequest,
                                             UploadToolContext uploadToolContext,
                                             DataSupplier<Map> dataSupplier,
                                             IAntiDuplicationContext antiDuplicationContext) {
        int failedRowsCount = 0;
        String failedRowsFilename = generateFailedRowsFileName(uploadToolContext.getCampaign().getId());
        try {
            Map row = dataSupplier.getNextObject();
            uploadToolContext.incrementProcessedRows();
            remapColumnsForPreviewRows(uploadToolContext, processUploadParameterRequest);
            List<String> allColumnNames = new ArrayList<>(processUploadParameterRequest.getRemappedColumns().values());
            while (row != null && !Thread.currentThread().isInterrupted()) {
                row = remapColumns(row, processUploadParameterRequest.getRemappedColumns());
                processUploadParameterRequest.getHiddenColumns().forEach(row::remove);
                Map rowToUpload = antiDuplicationContext.performAntiDuplication(row);
                boolean isSuccessful;
                Exception failCause = null;
                try {
                    isSuccessful = uploadSingleRow(rowToUpload, uploadToolContext);
                } catch (Exception e) {
                    isSuccessful = false;
                    failCause = e;
                }
                if (!isSuccessful) {
                    failedRowsCount++;
                    handleRowUploadFail(uploadToolContext, failedRowsCount, failedRowsFilename, row, failCause, allColumnNames);
                }
                uploadToolContext.setFailedRowsCount(failedRowsCount);
                setFailedRowsAccessLink(uploadToolContext, failedRowsCount, failedRowsFilename);
                uploadToolContext.incrementProcessedRows();
                row = dataSupplier.getNextObject();
            }
        } catch (ClosedByInterruptException e) {
            //no op
        } catch (Exception e) {
            LOG.error("Error occurred while uploading data from temp file. ", e,
                    Constants.LOG_MODULE.CAMPAIGN, uploadToolContext.getCampaign());
        } finally {
            if (failedRowsCount == 0) {
                LOG.info("Upload finished", Constants.LOG_MODULE.CAMPAIGN, uploadToolContext.getCampaign());
            } else {
                LOG.warn("Not all rows loaded in DB. Failed rows available via \"link\":" + uploadToolContext.getFailedRowsFilenameLink(),
                        Constants.LOG_MODULE.CAMPAIGN,
                        uploadToolContext.getCampaign());
            }
        }
    }

    /**
     * Handle upload fail for single row. allColumnNames have same order during one upload session
     *
     * @param uploadToolContext
     * @param failedRowsCount    - count of errors during current upload process
     * @param failedRowsFilename - file with failed rows
     * @param row                - row which uploading failed
     * @param failCause          - Exception which is cause of fail
     * @param allColumnNames     - all provided column names
     * @throws IOException
     */
    private void handleRowUploadFail(UploadToolContext uploadToolContext, int failedRowsCount, String failedRowsFilename, Map row, Exception failCause, List<String> allColumnNames) throws IOException {
        uploadToolFileService.writeFailedRowToFile(row, failedRowsFilename, allColumnNames);
        if (failedRowsCount < Constants.UPLOAD_ERROR_LOG_THRESHOLD) {
            if (failCause == null) {
                LOG.error("Can't upload data: " + row, Constants.LOG_MODULE.CAMPAIGN, uploadToolContext.getCampaign());
            } else {
                LOG.error("Can't upload data: " + row + " Cause:" + failCause + " Reason: " + failCause.getMessage(),
                        Constants.LOG_MODULE.CAMPAIGN,
                        uploadToolContext.getCampaign());
            }
        }
    }

    private boolean uploadSingleRow(Map rowToUpload, UploadToolContext uploadToolContext) throws SQLException {
        boolean isSuccessful = false;
        if (rowToUpload != null) {
            if (uploadToolContext.getStartParameters().isUpdateExistingData()) {
                isSuccessful = updateSingleRow(uploadToolContext.getCampaign(), rowToUpload);
            } else {
                isSuccessful = insertSingleRow(uploadToolContext.getCampaign(), rowToUpload);
            }
        }
        return isSuccessful;
    }

    /**
     * Set failed row access link in cycle because thread can be interrupted at any moment
     *
     * @param uploadToolContext
     * @param failedRowsCount
     * @param failedRowsFilename
     */
    private void setFailedRowsAccessLink(UploadToolContext uploadToolContext, int failedRowsCount, String failedRowsFilename) {
        if (failedRowsCount > 0 && uploadToolContext.getFailedRowsFilenameLink() == null) {
            String fileAccessLink = uploadToolFileService.generateFileAccessLink(failedRowsFilename);
            uploadToolContext.setFailedRowsFilenameLink(fileAccessLink);
        }
    }

    private void remapColumnsForPreviewRows(UploadToolContext uploadToolContext, ProcessUploadParameterRequest processUploadParameterRequest) {
        List<Map> remappedRowsForPreview = new ArrayList<>(uploadToolContext.getRowsForPreview().size());
        for (Map previewRow : uploadToolContext.getRowsForPreview()) {
            Map remappedRow = remapColumns(previewRow, processUploadParameterRequest.getRemappedColumns());
            remappedRowsForPreview.add(remappedRow);
        }
        uploadToolContext.setRowsForPreview(remappedRowsForPreview);
    }

    /**
     * Upload single row to database and save to file to file if insert failed
     *
     * @param campaign
     * @param row
     * @throws SQLException - when insertion failed
     */
    private boolean insertSingleRow(Campaign campaign, Map row) throws SQLException {
        campaignDbOperations.insertDataForCampaignUnsafe(campaign, row);
        return true;
    }

    /**
     * Update single row in database and save to file if update failed
     *
     * @param campaign
     * @param row
     * @return true if update performed successful
     * @throws SQLException when update failed
     */
    private boolean updateSingleRow(Campaign campaign, Map row) throws SQLException {
        if (row.containsKey(campaign.getColUniqueKey())) {
            List<Filter> uniqueKeyFilter = new LinkedList<>();
            uniqueKeyFilter.add(new Filter(campaign.getColUniqueKey(),
                    Constants.FILTER_CONDITION.EQUAL,
                    row.get(campaign.getColUniqueKey()).toString()));
            Map<String, Object> rowWithoutUniqueKey = new HashMap<>(row);
            rowWithoutUniqueKey.remove(campaign.getColUniqueKey());
            int rowUpdated = campaignDbOperations.updateCampaignDataUnsafe(campaign, uniqueKeyFilter, Constants.FILTER_JOIN_TYPE.AND, rowWithoutUniqueKey);
            return rowUpdated >= 1;
        } else {
            return false;
        }
    }

    private String generateFailedRowsFileName(Long campaignId) {
        return UUID.randomUUID().toString() + "-campaign-failed-rows" + campaignId + ".csv";
    }

    private Map<String, String> remapColumns(Map<String, String> originalRow, Map<String, String> columnsToRemap) {
        final Map<String, String> result = new HashMap<>();
        originalRow.forEach((columnName, columnValue) -> {
            if (!StringUtils.isEmpty(columnValue) && columnsToRemap.containsKey(columnName)) {
                String newColumnName = columnsToRemap.get(columnName);
                if (!StringUtils.isEmpty(newColumnName)) {
                    result.put(newColumnName, columnValue);
                }
            }
        });
        return result;
    }

    /**
     * Get upload progress. If upload finished cleanup data
     *
     * @param campaignId
     * @return
     */
    public UploadProgressResponse getUploadProgressAndCleanupFinished(Long campaignId) {
        UploadToolContext uploadToolContext = dataManipulationStateHolder.getUploadToolContext(campaignId);
        if (uploadToolContext != null) {
            UploadProgressResponse uploadProgressResponse = createProgressResponse(uploadToolContext);
            if (uploadToolContext.getTotalRowsCount() != null
                    && uploadToolContext.getTotalRowsCount().equals(uploadToolContext.getProcessedRowsCount())) {
                dataManipulationStateHolder.removeUploadToolContext(campaignId);
                uploadToolFileService.deleteTempFile(uploadToolContext.getStartUploadTempFilename());
                sortPriorityRecalculationManager.onDataUploaded(campaignId);
            }
            return uploadProgressResponse;
        }
        return null;
    }

    public List<Map> getRowsForPreview(Long campaignId) {
        return Optional.ofNullable(dataManipulationStateHolder.getUploadToolContext(campaignId))
                .map(UploadToolContext::getRowsForPreview)
                .orElse(null);
    }

    /**
     * Terminate upload to DB. Note that all uploaded data remains in DB.
     *
     * @param campaignId
     * @return
     */
    public UploadProgressResponse terminateUpload(Long campaignId) {
        UploadProgressResponse response = null;
        UploadToolContext uploadToolContext = dataManipulationStateHolder.removeUploadToolContext(campaignId);
        if (uploadToolContext != null) {
            Future<?> uploadFuture = uploadToolContext.getUploadFuture();
            if (uploadFuture != null) {
                uploadFuture.cancel(true);
            }
            response = createProgressResponse(uploadToolContext);
            uploadToolFileService.deleteTempFile(uploadToolContext.getStartUploadTempFilename());
            LOG.info("Upload for " + uploadToolContext.getCampaign() + " is terminated",
                    Constants.LOG_MODULE.CAMPAIGN,
                    uploadToolContext.getCampaign());
            sortPriorityRecalculationManager.onDataUploaded(campaignId);
        }
        return response;
    }

    private UploadProgressResponse createProgressResponse(UploadToolContext uploadToolContext) {
        UploadProgressResponse uploadProgressResponse = new UploadProgressResponse();
        uploadProgressResponse.setTotal(uploadToolContext.getTotalRowsCount());
        uploadProgressResponse.setProcessed(uploadToolContext.getProcessedRowsCount());
        uploadProgressResponse.setFailed(uploadToolContext.getFailedRowsCount());
        uploadProgressResponse.setFailedRowsFilenameLink(uploadToolContext.getFailedRowsFilenameLink());
        return uploadProgressResponse;
    }

    /**
     * Validate data types in temporary file. Should be performed just before uploading to database
     *
     * @param campaignId
     * @return Map with validation result for rows that not passed validation. Key - row index, value - validation result
     */
    public Map<Long, ValidationResult> validateTempFile(Long campaignId, Map<String, String> columnsToRemap) throws IOException, ParseException {
        UploadToolContext uploadToolContext = dataManipulationStateHolder.getUploadToolContext(campaignId);
        if (uploadToolContext != null) {
            String tempFilePath = uploadToolFileService.getTempFilePath(uploadToolContext.getStartUploadTempFilename());
            DataSupplier<Map> dataSupplier = DataSupplierBuilder.<Map>getInstance().dataType(Map.class)
                    .usingFile(tempFilePath)
                    .sourceFirstRowColumnNames(true)
                    .build();
            List<FieldDTO> sourceTableColumnsWithTypes = campaignService.getSourceTableColumnsWithTypes(campaignId);
            Map<String, Constants.DB_FIELD_TYPES> columnTypes = sourceTableColumnsWithTypes.stream()
                    .collect(Collectors.toMap(FieldDTO::getFieldName, FieldDTO::getFieldType));
            Validator<Map<String, String>> mapValuesTypesValidator = new MapValuesTypesValidator(columnTypes);
            Map<Long, ValidationResult> validationResultMap = new HashMap<>();
            Map row = dataSupplier.getNextObject();
            long i = 1;
            do {
                row = remapColumns(row, columnsToRemap);
                ValidationResult rowValidationResult = mapValuesTypesValidator.validate(row);
                if (!rowValidationResult.getResult()) {
                    validationResultMap.put(i, rowValidationResult);
                }
                row = dataSupplier.getNextObject();
                i++;
            } while (row != null);
            return validationResultMap;
        }
        return null;
    }

    /**
     * Recalculate priorities for specified campaigns using API token
     *
     * @param campaignIds comma separated ids
     * @param apiToken    campaign API token
     * @throws PriorityRecalculationStartException if recalculation can't be started for some campaigns or API token is missing
     */
    public void recalculateCampaignsPriorities(Long[] campaignIds, String apiToken) {
        LOG.info("Got recalculation request for campaigns with ids: " + Arrays.toString(campaignIds));
        List<Long> failedCampaignIds = new ArrayList<>();

        for (Long campaignId : campaignIds) {
            if (checkCampaignReadyForRecalculation(campaignId, apiToken)) {
                sortPriorityRecalculationManager.recalculatePriority(campaignId);
            } else {
                failedCampaignIds.add(campaignId);
            }
        }

        if (!failedCampaignIds.isEmpty()) {
            throw new PriorityRecalculationStartException("Could not start recalculating priorities for campaigns with IDs: " + failedCampaignIds + ", please check logs for details");
        }
    }

    private boolean checkCampaignReadyForRecalculation(Long campaignId, String apiToken) {
        String errorMessage;
        try {
            Campaign campaign = tokenCampaignValidatorService.getVerifiedCampaign(apiToken, String.valueOf(campaignId));
            if (!campaign.isMultipleSort()) {
                errorMessage = "No multiple sort enabled";
            } else return true;
        } catch (InvalidApiTokenException e) {
            errorMessage = e.getMessage();
        }
        LOG.error("Could not start recalculating priorities for campaign with id " + campaignId + ": " + errorMessage);
        return false;
    }

}
