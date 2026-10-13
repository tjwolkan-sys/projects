package com.powerdialer.service;

import com.powerdialer.dao.impl.CampaignDao;
import com.powerdialer.dao.impl.TenantDao;
import com.powerdialer.dao.impl.CampaignRecordDao;
import com.powerdialer.dto.*;
import com.powerdialer.engine.db.CampaignDbOperations;
import com.powerdialer.engine.db.DBEngine;
import com.powerdialer.model.User;
import com.powerdialer.model.Filter;
import com.powerdialer.model.Tenant;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.CampaignPriority;
import com.powerdialer.model.CampaignPriorityFilter;
import com.powerdialer.model.OperationOutcome;
import com.powerdialer.util.Constants;
import com.powerdialer.util.EnCipherUtils;
import com.powerdialer.util.LoggerDecorator;

import java.lang.Number;
import java.sql.Connection;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.apache.commons.lang3.StringUtils;

import javax.mail.Session;
import java.sql.SQLException;
import java.util.*;

import static com.powerdialer.util.Constants.CAMPAIGN_RECORD_OPERATION.*;
import static com.powerdialer.util.Constants.RECORD_UPDATE_OPERATION_STATUS.*;

/**
 * Service class for managing campaign records.
 *
 * This class provides methods to interact with campaign records, including retrieving tenants,
 * initializing data, fetching table columns, testing filters, and fetching records.
 */
@Service
public class CampaignRecordService {

    private static final Logger log = LoggerFactory.getLogger(CampaignRecordService.class);
    /**
     * DAO for accessing campaign data.
     */
    private final CampaignDao campaignDao;

    /**
     * DAO for accessing tenant data.
     */
    private final TenantDao tenantDao;

    /**
     * A map of campaign IDs to campaign objects.
     */
    private final Map<Long, Campaign> campaignMap = new HashMap<>();

    /**
     * A map of tenant IDs to lists of campaign data represented as maps.
     */
    private Map<Long, List<Map<String, Object>>> campaignMapByTenant = new HashMap<>();

    /**
     * Utility class for encryption and decryption operations.
     */
    private final EnCipherUtils enCipherUtils;

    @Autowired
    private DBEngine dbEngine;

    /**
     * Task 327143: DataManipulation fetch'inin tek istekte belege alabilecegi
     * satir tavani. 0 veya negatif deger sinirsiz (bugunku davranis) demektir;
     * tavan asilirsa sonuc kesilir ve caller'a truncated bilgisi doner.
     */
    @Value("${campaignRecords.fetch.maxRows:500000}")
    private int fetchRecordsMaxRows;

    /**
     * Task 327143: fetchRecords'un gruplamasi ve {@link #classifyRecord} yalnizca
     * bu kolonlari okur; SELECT * yerine bu projeksiyon kullanilir. Kolon adlari
     * bilinen sistem kolonlaridir; SELECT listesine tablo metadata'sindaki
     * declared adiyla yazilirlar ki result-set anahtarlari SELECT * ile birebir
     * ayni kalsin (JSONObject anahtarlari case-sensitive'dir).
     */
    private static final List<String> FETCH_CLASSIFICATION_COLUMNS = Collections.unmodifiableList(Arrays.asList(
            "id", "ListID", "complete_date", "is_disabled", "start_date", "is_fetched",
            "retry_date", "callback_date", "updated_date", "telephony_result_code", "UpdatedUser", "ATTEMPTS"));

    /**
     * Operations for interacting with campaign database tables.
     */
    @Autowired
    private CampaignDbOperations campaignDbOperations;

    /**
     * Service for managing campaigns.
     */
    @Autowired
    private CampaignService campaignService;

    @Autowired
    private CampaignPriorityService campaignPriorityService;

    @Autowired
    private CampaignPriorityFilterService campaignPriorityFilterService;

    private LoggerDecorator LOG;

    private final CampaignRecordDao campaignRecordDao;

    private  User user;

    /**
     * Small holder for a parametrized SQL clause.
     */
    private static final class SqlClause {
        private final String sql;
        private final List<Object> params;
        private SqlClause(String sql, List<Object> params) {
            this.sql = sql;
            this.params = params;
        }
    }
    /**
     * Constructor for initializing the CampaignRecordService.
     *
     * @param campaignDao DAO for campaign data.
     * @param tenantDao DAO for tenant data.
     * @param enCipherUtils Utility for encryption and decryption.
     */
    public CampaignRecordService(LoggerDecorator loggerDecorator,
                                 CampaignDao campaignDao,
                                 TenantDao tenantDao,
                                 EnCipherUtils enCipherUtils,
                                 CampaignRecordDao campaignRecordDao) {
        this.campaignDao = campaignDao;
        this.tenantDao = tenantDao;
        this.enCipherUtils = enCipherUtils;
        this.campaignRecordDao = campaignRecordDao;
        LOG = loggerDecorator.getLogger(CampaignRecordService.class);
    }

    /**
     * Retrieves all tenants used in campaigns from the CampaignDao.
     *
     * @return A list of tenant data represented as maps.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getAllTenantsFromUserDao() {
        return campaignDao.getTenantsOfUser(user);


    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getAllTenantsFromCampaignDao() {
        return campaignDao.getAllUsedTenants();
    }




    /**
     * Retrieves campaigns associated with a specific tenant ID.
     *
     * @param tenantId The ID of the tenant.
     * @return A list of campaign data represented as maps.
     */
    public List<Map<String, Object>> getCampaignsByTenantId(Long tenantId) {
        return campaignMapByTenant.getOrDefault(tenantId, Collections.emptyList());
    }

    /**
     * Initializes campaign and tenant data by loading campaigns and grouping them by tenant.
     *
     */
    @Transactional(readOnly = true)
    public CampaignTenantDTO initializeData() {
     user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        final boolean superAdmin = isSuperAdmin(user);


        final List<Campaign> campaigns = campaignDao.getAll();
        final Set<String> priorityCampaignNames = campaignPriorityService.getAllPriorityCampaignNames();
        final Set<Long> allowedTenantIds = superAdmin
                ? null
                : getCurrentUserTenantId().stream()
                .map(Tenant::getId)
                .collect(Collectors.toSet());


        final Map<Long, List<Map<String, Object>>> campaignsByTenant = campaigns.stream()
                .filter(c -> c.getTenant() != null)
                .filter(c -> allowedTenantIds == null || allowedTenantIds.contains(c.getTenant().getId()))
                .collect(Collectors.groupingBy(
                        c -> c.getTenant().getId(),
                        Collectors.mapping(c -> toCampaignSummary(c, priorityCampaignNames), Collectors.toList())
                ));


        campaignMap.clear();
        campaigns.forEach(c -> campaignMap.put(c.getId(), c));

        CampaignTenantDTO dto = new CampaignTenantDTO();
        dto.setCampaignsByTenantList(campaignsByTenant);
        dto.setTenantsList(superAdmin ? getAllTenantsFromCampaignDao() : getAllTenantsFromUserDao());
        return dto;
    }

    private boolean isSuperAdmin(User user) {
        return user != null && user.getRolesNames().stream()
                .anyMatch(r -> Constants.ROLE_SUPERADMIN.equalsIgnoreCase(r));
    }


    private Map<String, Object> toCampaignSummary(Campaign c, Set<String> priorityCampaignNames) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", c.getId());
        m.put("name", c.getName());
        m.put("filters", convertToFilterRows(c.getFilters()));
        boolean isPriority = priorityCampaignNames.contains(c.getName());
        m.put("is_priority", isPriority);
        return m;
    }

    private Set<Tenant> getCurrentUserTenantId() {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return user.getTenants();
    }
    /**
     * Builds a parametrized WHERE clause for priority operations (safe for PreparedStatement usage).
     * Supports operators like =, <, >, <=, >=, <>, LIKE, IN, NOT IN etc.
     */
    private SqlClause buildWhereClauseWithParams(List<CampaignPriorityFilterFormDTO> filters, String logicOp) {
        List<String> parts = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        if (filters != null) {
            for (CampaignPriorityFilterFormDTO f : filters) {
                String field = f.getField();
                Constants.FILTER_CONDITION condition = f.getCondition();
                String operator = condition.toString();
                String value = f.getValue();
                if (condition.isNullCheck()) {
                    parts.add(field + " " + condition.condition());
                } else if ("in".equalsIgnoreCase(operator) || "not in".equalsIgnoreCase(operator)) {
                    String[] values = value.split("\\s*,\\s*");
                    String placeholders = String.join(", ", Collections.nCopies(values.length, "?"));
                    parts.add(field + " " + operator.toUpperCase() + " (" + placeholders + ")");
                    params.addAll(Arrays.asList(values));
                } else if ("like".equalsIgnoreCase(operator)) {
                    parts.add(field + " LIKE ?");
                    params.add(value);
                } else {
                    parts.add(field + " " + operator + " ?");
                    params.add(value);
                }
            }
        }
        String clause = parts.isEmpty() ? "1=1" : String.join(" " + logicOp + " ", parts);
        return new SqlClause(clause, params);
    }
    /**
     * Retrieves the columns and their types for a specific campaign's database table.
     *
     * @param campaignId The ID of the campaign.
     * @return A list of column data represented as maps with "name" and "type" keys.
     * @throws SQLException If a database error occurs.
     */
    public List<Map<String, String>> getTableColumnsByCampaignId(Long campaignId) throws SQLException {
        Campaign campaign = campaignMap.get(campaignId);
        if (campaign == null) {
            throw new IllegalArgumentException("Campaign not found for ID: " + campaignId);
        }

        String tableName = campaign.getDbListTable();
        String[][] columnsWithTypes = campaignDbOperations.getTableColumnsWithTypes(
                tableName,
                campaign.getId(),
                campaign.getDbConnectionString(),
                campaign.getDbUsername(),
                enCipherUtils.decryptPassword(campaign.getDbPassword())
        );

        List<Map<String, String>> columnList = new ArrayList<>();
        for (String[] col : columnsWithTypes) {
            Map<String, String> colMap = new HashMap<>();
            colMap.put("name", col[0]);
            colMap.put("type", col[1]);
            columnList.add(colMap);
        }

        return columnList;
    }


    /**
     * Tests filters applied to campaign records and retrieves filtered data.
     *
     * This method constructs SQL queries based on the filter criteria provided in the DTO,
     * executes the queries, and returns the total count of matching records along with the filtered rows.
     *
     * @param dto The filter criteria encapsulated in a CampaignRecordFilterDTO object.
     * @return A map containing the total count of matching records and the filtered rows.
     * @throws SQLException If a database error occurs during query execution.
     */
    public Map<String, Object> testFilters(CampaignRecordFilterDTO dto) throws SQLException {
        Long campaignId = dto.getCampaignId();
        Campaign campaign = campaignMap.get(campaignId);
        if (campaign == null) {
            throw new IllegalArgumentException("Campaign not found for id: " + campaignId);
        }

        String tableName = campaign.getDbListTable();

        String whereClause = "";
        if (dto.getFilterRows() != null && !dto.getFilterRows().isEmpty()) {
            List<String> conditions = new ArrayList<>();
            for (CampaignRecordFilterDTO.FilterRow row : dto.getFilterRows()) {
                String value = row.getValue();
                String operator = row.getCondition().toString();
                String field = row.getField();
                String fieldType = row.getFieldType();

                prepareConditionFilters(value, operator, field, fieldType, conditions);            }
            whereClause = " WHERE " + String.join(" " + dto.getLogicOperator() + " ", conditions);
        }

        String countQuery = "SELECT COUNT(*) AS total FROM " + tableName + whereClause;
        JSONArray countResult = dbEngine.executeQuery(
                countQuery,
                campaign.getId(),
                1,
                campaign.getDbConnectionString(),
                campaign.getDbUsername(),
                enCipherUtils.decryptPassword(campaign.getDbPassword())
        );
        int totalCount = countResult.getJSONObject(0).getInt("total");

        String dataQuery = "SELECT * FROM " + tableName + whereClause;
        JSONArray rows = dbEngine.executeQuery(
                dataQuery,
                campaign.getId(),
                10,
                campaign.getDbConnectionString(),
                campaign.getDbUsername(),
                enCipherUtils.decryptPassword(campaign.getDbPassword())
        );

        List<Map<String, Object>> convertedRows = new ArrayList<>();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.getJSONObject(i);
            Map<String, Object> rowMap = new HashMap<>();
            for (String key : row.keySet()) {
                rowMap.put(key, row.get(key));
            }
            convertedRows.add(rowMap);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("totalCount", totalCount);
        result.put("rows", convertedRows);
        return result;
    }

    /**
     * Builds a fully qualified table reference for SQL queries in the format:
     * <pre>[database].[schema].[table]</pre>
     *
     * <p>This method ensures that the schema and table names are correctly enclosed
     * in square brackets to prevent conflicts with reserved keywords or special characters.
     * It does not perform any SQL injection sanitization and assumes the inputs are trusted.</p>
     *
     * @param db     The database name. Must not be null or empty.
     * @param schema The schema name (typically "dbo" in MSSQL).
     * @param table  The table name within the schema.
     * @return A string representing the fully qualified SQL table name.
     * @throws IllegalArgumentException if any of the parameters are null or empty.
     */
    private static String prepareSchema(String db, String schema, String table) {
        if (StringUtils.isEmpty(db)) throw new IllegalArgumentException("db is null/empty");
        if (StringUtils.isEmpty(schema)) throw new IllegalArgumentException("schema is null/empty");
        if (StringUtils.isEmpty(table)) throw new IllegalArgumentException("table is null/empty");
        return "[" + db + "].[" + schema + "].[" + table + "]";
    }

    /**
     * Fetches campaign records based on filter criteria and groups them by status.
     *
     * This method constructs SQL queries based on the filter criteria provided in the DTO,
     * executes the queries, and returns a list of unique records grouped by their status.
     *
     * @param dto The filter criteria encapsulated in a CampaignRecordFilterDTO object.
     * @return A list of unique campaign records grouped by their status.
     * @throws SQLException If a database error occurs during query execution.
     */
    public List<Map<String, Object>> fetchRecords(CampaignRecordFilterDTO dto) throws SQLException {
        return fetchRecordsWithTruncation(dto).groups;
    }

    /**
     * Task 327143: fetch artik SELECT * ile tum tabloyu JSONArray olarak belege
     * almaz. (1) SELECT listesi {@link #FETCH_CLASSIFICATION_COLUMNS} ile tablo
     * metadata'sinin kesisimidir; (2) satirlar {@link DBEngine} streaming yoluyla
     * geldigi anda gruplanir, ara koleksiyon olusmaz; (3) konfigurable satir
     * tavani asilirsa okuma kesilir ve truncated=true doner. Gruplama ve
     * classifyRecord davranisi (Task 325469 kilidi dahil) birebir korunur.
     */
    private FetchRecordsOutcome fetchRecordsWithTruncation(CampaignRecordFilterDTO dto) throws SQLException {
        Objects.requireNonNull(dto, "dto");
        Long campaignId = dto.getCampaignId();
        final Campaign campaign = campaignMap.get(campaignId);
        if (campaign == null) {
            throw new IllegalArgumentException("Campaign not found for id: " + campaignId);
        }

        String tableName = campaign.getDbListTable();
        StringBuilder query = new StringBuilder("SELECT ")
                .append(buildFetchProjection(campaign))
                .append(" FROM ").append(tableName);

        if (dto.getFilterRows() != null && !dto.getFilterRows().isEmpty()) {
            query.append(" WHERE ");
            List<String> conditions = new ArrayList<>();
            for (CampaignRecordFilterDTO.FilterRow row : dto.getFilterRows()) {
                String value = row.getValue();
                String operator = row.getCondition().toString();
                String field = row.getField();
                String fieldType = row.getFieldType();
                prepareConditionFilters(value, operator, field, fieldType, conditions);
            }
            query.append(String.join(" " + dto.getLogicOperator() + " ", conditions));
        }

        int rowCap = fetchRecordsMaxRows > 0 ? fetchRecordsMaxRows : Integer.MAX_VALUE;
        final Map<String, Map<String, Object>> uniqueMap = new HashMap<>();
        boolean truncated = dbEngine.executeQueryStreaming(
                query.toString(),
                campaign.getId(),
                rowCap,
                campaign.getDbConnectionString(),
                campaign.getDbUsername(),
                enCipherUtils.decryptPassword(campaign.getDbPassword()),
                row -> accumulateRecordGroup(row, campaign, uniqueMap)
        );

        return new FetchRecordsOutcome(new ArrayList<>(uniqueMap.values()), truncated);
    }

    /**
     * Tek satiri gruplara isler. Task 325469 (A4-1) sozlesmesi korunur: her
     * satirin Map kopyasi (classifyRecord contract'i) yapilir yapilmaz gruplanip
     * birakilir; ara listeye biriktirilmez.
     */
    private void accumulateRecordGroup(JSONObject obj, Campaign campaign, Map<String, Map<String, Object>> uniqueMap) {
        final Map<String, Object> record = new HashMap<>();
        for (String key : obj.keySet()) {
            record.put(key, obj.get(key));
        }
        String listId = String.valueOf(record.get("ListID"));
        Constants.CampaignRecordStatus status = classifyRecord(record, campaign);
        String statue = status.getMessageKey();
        String key = listId + "-" + statue;
        uniqueMap.compute(key, (k, v) -> {
            if (v == null) {
                Map<String, Object> item = new HashMap<>();
                item.put("ListID", listId);
                item.put("Statue", statue);
                item.put("Record Count", 1L);
                List<String> ids = new ArrayList<>();
                ids.add(record.get("id").toString());
                item.put("Record Ids", ids);
                return item;
            } else {
                v.put("Record Count", (Long) v.get("Record Count") + 1);
                List<String> ids = (List<String>) v.get("Record Ids");
                ids.add(record.get("id").toString());
                v.put("Record Ids", ids);
                return v;
            }
        });
    }

    /**
     * SELECT listesini tablo metadata'siyla kesiserek kurar.
     *
     * <p>Kurallar: (1) Kolonlar SELECT'e metadata'daki declared adiyla yazilir;
     * boylece result-set anahtarlari SELECT * davranisiyla birebir ayni kalir.
     * (2) Kolon adlari quote'lanmaz cunku kampanya datasource'u MSSQL disinda
     * Oracle/MySQL/Postgre de olabilir ve adaylar zaten bilinen sistem
     * kolonlariyla sinirlidir. (3) Metadata alinamaz veya bos donerse davranisi
     * korumak icin SELECT * fallback'i kullanilir — fetch, metadata sorunu
     * yuzunden ASLA kirilmaz.</p>
     */
    private String buildFetchProjection(Campaign campaign) {
        try {
            String[][] columnsWithTypes = campaignDbOperations.getTableColumnsWithTypes(
                    campaign.getDbListTable(),
                    campaign.getId(),
                    campaign.getDbConnectionString(),
                    campaign.getDbUsername(),
                    enCipherUtils.decryptPassword(campaign.getDbPassword())
            );
            if (columnsWithTypes == null || columnsWithTypes.length == 0) {
                return "*";
            }
            Map<String, String> declaredByLowerName = new HashMap<>();
            for (String[] column : columnsWithTypes) {
                if (column != null && column.length > 0 && column[0] != null) {
                    declaredByLowerName.putIfAbsent(column[0].toLowerCase(Locale.ROOT), column[0]);
                }
            }
            List<String> projection = new ArrayList<>();
            for (String needed : FETCH_CLASSIFICATION_COLUMNS) {
                String declared = declaredByLowerName.get(needed.toLowerCase(Locale.ROOT));
                if (declared != null) {
                    projection.add(declared);
                }
            }
            if (projection.isEmpty()) {
                return "*";
            }
            return String.join(", ", projection);
        } catch (Exception e) {
            log.warn("Could not resolve list table columns for campaign " + campaign.getId()
                    + ", falling back to SELECT *", e);
            return "*";
        }
    }

    /** fetchRecords'un gruplanmis sonucu + tavan kesme bilgisi. */
    private static final class FetchRecordsOutcome {
        private final List<Map<String, Object>> groups;
        private final boolean truncated;

        private FetchRecordsOutcome(List<Map<String, Object>> groups, boolean truncated) {
            this.groups = groups;
            this.truncated = truncated;
        }
    }



    /**
     * Retrieves a comprehensive set of campaign data, including:
     * <ul>
     *     <li>Filtered and grouped campaign records</li>
     *     <li>User result code statistics</li>
     *     <li>Telephony result code statistics</li>
     * </ul>
     *
     * <p>This is a convenience aggregator that invokes
     * {@link #fetchRecords(CampaignRecordFilterDTO)},
     * {@link #fetchUserResultRecords(CampaignRecordFilterDTO)},
     * and {@link #fetchTelephonyResultRecords(CampaignRecordFilterDTO)} sequentially,
     * returning all results in a single composite map.</p>
     *
     * @param dto The filter configuration for fetching campaign-related data.
     * @return A map containing three keys:
     *         <ul>
     *             <li>{@code Constants.RECORDS} → list of grouped record maps</li>
     *             <li>{@code Constants.UserRC} → user result code statistics</li>
     *             <li>{@code Constants.TelephonyRC} → telephony result code statistics</li>
     *         </ul>
     * @throws SQLException If any of the internal data retrieval operations fail due to database errors.
     */
    public Map<Object,List<Map<String, Object>>> fetchAll(CampaignRecordFilterDTO dto) throws SQLException {
        return fetchAllWithTruncation(dto).getData();
    }

    /**
     * Task 327143: {@link #fetchAll(CampaignRecordFilterDTO)} ile ayni veri
     * seklini dondurur; ek olarak kayit listesinin satir tavanina takilip
     * takilmadigini tasir. Controller bu bilgiyi X-Records-Truncated header'i
     * olarak yayinlar; JSON body sozlesmesi degismez.
     */
    public FetchAllOutcome fetchAllWithTruncation(CampaignRecordFilterDTO dto) throws SQLException {
        Objects.requireNonNull(dto, "dto");
        FetchRecordsOutcome recordsOutcome = fetchRecordsWithTruncation(dto);
        List<Map<String, Object>> userRC = fetchUserResultRecords(dto);
        List<Map<String, Object>> telephonyRC = fetchTelephonyResultRecords(dto);

        final Map<Object,List<Map<String, Object>>> fetchMap = new HashMap<>(3);
        fetchMap.put(Constants.RECORDS, recordsOutcome.groups);
        fetchMap.put(Constants.UserRC, userRC);
        fetchMap.put(Constants.TelephonyRC, telephonyRC);

        return new FetchAllOutcome(fetchMap, recordsOutcome.truncated);
    }

    /** fetchAll veri sekli + kayit listesinin kesilme bilgisi. */
    public static final class FetchAllOutcome {
        private final Map<Object, List<Map<String, Object>>> data;
        private final boolean recordsTruncated;

        public FetchAllOutcome(Map<Object, List<Map<String, Object>>> data, boolean recordsTruncated) {
            this.data = data;
            this.recordsTruncated = recordsTruncated;
        }

        public Map<Object, List<Map<String, Object>>> getData() {
            return data;
        }

        public boolean isRecordsTruncated() {
            return recordsTruncated;
        }
    }


    /**
     * Fetches and aggregates user result code statistics for a specific campaign.
     *
     * <p>This method performs a SQL query that joins the campaign’s list table with
     * the result code tables (<code>wbp_result_code</code> and <code>wbp_result_code_status_criteria</code>),
     * grouping by result code and status criteria. It then maps localized status codes
     * to generic categories (e.g., SUCCESS, FAILURE, SCHEDULE, OUTOFCRITERIA) and
     * calculates per-status and summary record counts.</p>
     *
     * <p>The SQL uses a Common Table Expression (CTE) with three layers:
     * <ul>
     *     <li><b>base</b> – raw join between the list and result tables</li>
     *     <li><b>map</b> – static mapping from Turkish status labels to English categories</li>
     *     <li><b>combined</b> – aggregated totals per category</li>
     * </ul></p>
     *
     * @param dto The filter DTO containing campaign ID, logical operator, and filter rows.
     * @return A list of maps where each map includes:
     *         <ul>
     *             <li><b>result_code_text</b> – the result code name</li>
     *             <li><b>statusCode</b> – the mapped status category</li>
     *             <li><b>record_count</b> – the number of records matching that status</li>
     *         </ul>
     *         Returns an empty list if the query fails or the campaign cannot be found.
     */

    private List<Map<String, Object>> fetchUserResultRecords(CampaignRecordFilterDTO dto) {
        final List<Map<String, Object>> out = new ArrayList<>();
        try {
            Objects.requireNonNull(dto, "dto");

            final Long campaignId = dto.getCampaignId();
            if (campaignId == null) throw new IllegalArgumentException("campaignId is null");

            final Campaign campaign = campaignMap.get(campaignId);
            if (campaign == null) throw new IllegalArgumentException("Campaign not found for id: " + campaignId);


            final String listTable       = campaign.getDbListTable();
            final String rcTable         = "dbo.wbp_result_code_local";
            final String rccTable        = "dbo.wbp_result_code_status_criteria_local";
            final String statusMapTable  = "dbo.status_map";


            String whereClause = "";
            if (dto.getFilterRows() != null && !dto.getFilterRows().isEmpty()) {
                List<String> conditions = new ArrayList<>();
                for (CampaignRecordFilterDTO.FilterRow row : dto.getFilterRows()) {
                    String value     = row.getValue();
                    String operator  = row.getCondition() != null ? row.getCondition().toString() : null;
                    String field     = row.getField();
                    String fieldType = row.getFieldType();
                    prepareConditionFilters(value, operator, field, fieldType, conditions);
                }
                if (!conditions.isEmpty()) {
                    whereClause = " WHERE " + String.join(" " + dto.getLogicOperator() + " ", conditions);
                }
            }


            StringBuilder sb = new StringBuilder(640);
            sb.append(";WITH base AS (")
                    .append("  SELECT rc.result_code_text, rcc.statusCode, COUNT(*) AS record_count")
                    .append("    FROM ").append(listTable).append(" cm")
                    .append("    JOIN ").append(rcTable).append(" rc ON rc.id = cm.user_result_code")
                    .append("    JOIN ").append(rccTable).append(" rcc ON rcc.result_code_id = cm.user_result_code  and  rcc.group_id=cm.called_group_id ");


            if (!whereClause.isEmpty()) sb.append(' ').append(whereClause);
            sb.append("   GROUP BY cm.user_result_code, rc.result_code_text, rcc.statusCode")
                    .append(")")
                    .append(" SELECT t.result_code_text, t.statusCode, t.status_code_description, t.record_count")
                    .append("   FROM (")

                    .append("         SELECT b.result_code_text,")
                    .append("                b.statusCode,")
                    .append("                COALESCE(m.status_group, b.statusCode) AS status_code_description,")
                    .append("                SUM(b.record_count) AS record_count,")
                    .append("                0 AS is_total")
                    .append("           FROM base b")
                    .append("       JOIN ").append(statusMapTable).append(" m ON m.statusCode = b.statusCode")
                    .append("       GROUP BY b.result_code_text, b.statusCode, COALESCE(m.status_group, b.statusCode)")
                    .append("         UNION ALL ")
                    .append("         SELECT CONCAT(N'SUM ', COALESCE(m2.status_group, b2.statusCode)) AS result_code_text,")
                    .append("                NULL AS statusCode,")
                    .append("                COALESCE(m2.status_group, b2.statusCode) AS status_code_description,")
                    .append("                SUM(b2.record_count) AS record_count,")
                    .append("                1 AS is_total")
                    .append("           FROM base b2")
                    .append("       JOIN ").append(statusMapTable).append(" m2 ON m2.statusCode = b2.statusCode")
                    .append("       GROUP BY COALESCE(m2.status_group, b2.statusCode)")
                    .append("        ) AS t")
                    .append("  ORDER BY t.statusCode DESC");

            JSONArray rows = dbEngine.executeQuery(
                    sb.toString(),
                    campaign.getId(),
                    Integer.MAX_VALUE,
                    campaign.getDbConnectionString(),
                    campaign.getDbUsername(),
                    enCipherUtils.decryptPassword(campaign.getDbPassword())
            );

            for (int i = 0; i < rows.length(); i++) {
                JSONObject r = rows.getJSONObject(i);
                Map<String, Object> m = new HashMap<>();
                m.put("result_code_text", r.opt("result_code_text"));
                m.put("statusCode", r.opt("status_code_description"));
                Object cnt = r.opt("record_count");
                m.put("record_count", (cnt instanceof Number) ? ((Number) cnt).longValue() : parseLongSafe(cnt));
                out.add(m);
            }
        } catch (Exception e) {
            LOG.error("fetchUserResultRecords failed: {}", e.getMessage(), Constants.LOG_MODULE.PUZZLE, e.getMessage());
            return Collections.emptyList();
        }
        return out;
    }




    /**
     * Fetches and aggregates telephony result code statistics for a specific campaign.
     *
     * <p>This method executes a SQL query similar to {@link #fetchUserResultRecords(CampaignRecordFilterDTO)},
     * but joins with the telephony result code table (<code>pd_telephony_rc</code>)
     * instead of the user result code table. It aggregates record counts by status code
     * and maps them into logical categories such as SUCCESS, FAILURE, or SCHEDULE.</p>
     *
     * <p>The query uses Common Table Expressions (CTEs) to first gather base data,
     * then normalize localized status codes to broader groups, and finally return
     * both detailed and summarized totals.</p>
     *
     * @param dto The filter configuration containing the campaign ID, filters, and logic operator.
     * @return A list of maps with each entry containing:
     *         <ul>
     *             <li><b>result_code_text</b> – telephony result code name</li>
     *             <li><b>statusCode</b> – normalized status group (e.g., SUCCESS, FAILURE)</li>
     *             <li><b>record_count</b> – total number of records in that group</li>
     *         </ul>
     *         Returns an empty list if an error occurs or the campaign is not found.
     */
    private List<Map<String, Object>> fetchTelephonyResultRecords(CampaignRecordFilterDTO dto) {
        final String rcTable        = "dbo.pd_telephony_rc_local";                 // id, name
        final String rccTable       = "dbo.wbp_result_code_status_criteria_local"; // id, result_code_id, statusCode
        final String statusMapTable = "dbo.status_map";                            // statusCode, status_group

        final List<Map<String, Object>> out = new ArrayList<>();
        try {
            Objects.requireNonNull(dto, "dto");
            final Long campaignId = dto.getCampaignId();
            if (campaignId == null) throw new IllegalArgumentException("campaignId is null");

            final Campaign campaign = campaignMap.get(campaignId);
            if (campaign == null) throw new IllegalArgumentException("Campaign not found for id: " + campaignId);

            final String listTable = campaign.getDbListTable();

            String whereClause = "";
            if (dto.getFilterRows() != null && !dto.getFilterRows().isEmpty()) {
                List<String> conditions = new ArrayList<>();
                for (CampaignRecordFilterDTO.FilterRow row : dto.getFilterRows()) {
                    String value     = row.getValue();
                    String operator  = row.getCondition() != null ? row.getCondition().toString() : null;
                    String field     = row.getField();
                    String fieldType = row.getFieldType();
                    prepareConditionFilters(value, operator, field, fieldType, conditions);
                }
                if (!conditions.isEmpty()) {
                    whereClause = " WHERE " + String.join(" " + dto.getLogicOperator() + " ", conditions);
                }
            }

            StringBuilder sb = new StringBuilder(800);
            sb.append(";WITH base AS (")
                    .append("  SELECT rc.name AS result_code_text,")
                    .append("         rcc.statusCode,")
                    .append("         COUNT(*) AS record_count")
                    .append("    FROM ").append(listTable).append(" cm")
                    .append("    JOIN ").append(rcTable).append(" rc")
                    .append("      ON rc.id = cm.telephony_result_code")
                    .append("     JOIN ").append(rccTable).append(" rcc")
                    .append("      ON rcc.telephony_result_code_id = cm.telephony_result_code");
            if (!whereClause.isEmpty()) sb.append(' ').append(whereClause);
            sb.append("   GROUP BY cm.telephony_result_code, rc.name, rcc.statusCode")
                    .append(")")
                    .append(" SELECT t.result_code_text,")
                    .append("        t.statusCode,")
                    .append("        t.status_code_description,")
                    .append("        t.record_count")
                    .append("   FROM (")
                    .append("         SELECT b.result_code_text,")
                    .append("                b.statusCode,")
                    .append("                COALESCE(m.status_group, b.statusCode) AS status_code_description,")
                    .append("                SUM(b.record_count) AS record_count,")
                    .append("                0 AS is_total")
                    .append("           FROM base b")
                    .append("       JOIN ").append(statusMapTable).append(" m")
                    .append("             ON m.statusCode = b.statusCode")
                    .append("       GROUP BY b.result_code_text, b.statusCode, COALESCE(m.status_group, b.statusCode)")
                    .append("         UNION ALL ")
                    .append("         SELECT CONCAT(N'SUM ', COALESCE(m2.status_group, b2.statusCode)) AS result_code_text,")
                    .append("                NULL AS statusCode,")
                    .append("                COALESCE(m2.status_group, b2.statusCode) AS status_code_description,")
                    .append("                SUM(b2.record_count) AS record_count,")
                    .append("                1 AS is_total")
                    .append("           FROM base b2")
                    .append("       JOIN ").append(statusMapTable).append(" m2")
                    .append("             ON m2.statusCode = b2.statusCode")
                    .append("       GROUP BY COALESCE(m2.status_group, b2.statusCode)")
                    .append("        ) AS t")
                    .append("  ORDER BY t.is_total, t.status_code_description");

            JSONArray rows = dbEngine.executeQuery(
                    sb.toString(),
                    campaign.getId(),
                    Integer.MAX_VALUE,
                    campaign.getDbConnectionString(),
                    campaign.getDbUsername(),
                    enCipherUtils.decryptPassword(campaign.getDbPassword())
            );

            for (int i = 0; i < rows.length(); i++) {
                JSONObject r = rows.getJSONObject(i);
                Map<String, Object> m = new HashMap<>();
                m.put("result_code_text", r.opt("result_code_text"));
                m.put("statusCode", r.opt("status_code_description"));
                Object cnt = r.opt("record_count");
                m.put("record_count", (cnt instanceof Number) ? ((Number) cnt).longValue() : parseLongSafe(cnt));
                out.add(m);
            }
        } catch (Exception e) {
            LOG.error("fetchTelephonyResultRecords failed: {}", e.getMessage(), Constants.LOG_MODULE.PUZZLE, e.getMessage());
            return Collections.emptyList();
        }
        return out;
    }




    /**
     * Safely parses an {@link Object} into a {@code long} value.
     *
     * <p>This method attempts to convert the provided object to a string and then parse it as a long.
     * If parsing fails (for example, due to nulls, non-numeric strings, or exceptions),
     * it returns {@code 0L} instead of throwing an exception.</p>
     *
     * @param o The object to parse into a long. May be {@code null} or non-numeric.
     * @return The parsed long value, or {@code 0L} if parsing fails.
     */
    private static long parseLongSafe(Object o) {
        try {
            return Long.parseLong(String.valueOf(o));
        } catch (Exception ignore){
            return 0L;
        }
    }

    private static Map<String, Object> userRc(String result_code_text, String statusCode, int record_count) {
        Map<String, Object> m = new HashMap<>();
        m.put("result_code_text", result_code_text);
        m.put("statusCode", statusCode);
        m.put("record_count", record_count);
        return m;
    }


    /**
     * Classifies a campaign record based on its attributes and campaign settings.
     *
     * This method determines the status of a campaign record by evaluating its attributes
     * and comparing them against the campaign's settings.
     *
     * @param record The campaign record represented as a map of attributes.
     * @param campaign The campaign object containing settings and configurations.
     * @return A string representing the status of the campaign record.
     */
    private Constants.CampaignRecordStatus classifyRecord(Map<String, Object> record, Campaign campaign) {
        Object completeDate = record.get("complete_date");
        Object isDisabled = record.get("is_disabled");
        Object startDate = record.get("start_date");
        Object isFetched = record.get("is_fetched");
        Object retryDate = record.get("retry_date");
        Object callbackDate = record.get("callback_date");
        Object updatedDate = record.get("updated_date");
        Object telephonyRc = record.get("telephony_result_code");
        Object updatedUser = record.get("UpdatedUser");

        Integer campaignAttemptLimit = campaign.getRetryOveralAttempt();

        Object attemptValue = record.get("ATTEMPTS");

        //314529 is_disabled bug'ı kapsamında düzenlendi
        if (completeDate != null) {
            if (hasAttemptLimitReached(attemptValue, campaignAttemptLimit)) {
                return Constants.CampaignRecordStatus.ATTEMPTS_LIMIT_REACHED;
            }
            return Constants.CampaignRecordStatus.COMPLETED;
        }

        if (isCallableConditionMet(completeDate, startDate, isDisabled, retryDate, callbackDate)) {
            return Constants.CampaignRecordStatus.CALLABLE;
        }

        if (isEliminatedConditionMet(isDisabled, updatedDate, updatedUser)) {
            return Constants.CampaignRecordStatus.ELIMINATED;
        }

        if (isInProcessConditionMet(isFetched, startDate)) {
            return Constants.CampaignRecordStatus.IN_PROCESS;
        }

        if (isRetryConditionMet(retryDate, completeDate, isDisabled)) {
            return Constants.CampaignRecordStatus.RETRY;
        }

        if (isCallbackConditionMet(callbackDate, completeDate, isDisabled)) {
            return Constants.CampaignRecordStatus.CALLBACK;
        }

        if (isExcludedConditionMet(isDisabled, updatedUser)) {
            return Constants.CampaignRecordStatus.EXCLUDED;
        }

        if (isDncConditionMet(telephonyRc, isDisabled)) {
            return Constants.CampaignRecordStatus.DNC;
        }

        return Constants.CampaignRecordStatus.UNPROCESSED;
    }

    private OperationOutcome<Constants.RECORD_UPDATE_OPERATION_STATUS> activateRecordReturningCount(
            Campaign campaign, CampaignRecordsOperationDTO dto) {
        List<String> ids = dto.getRecordIds() == null ? Collections.emptyList() : dto.getRecordIds();
        if (ids.isEmpty()) {
            return new OperationOutcome<>(UPDATE_SUCCESSFUL, 0);
        }
        String sql = "UPDATE " + campaign.getDbResultTable()
                + " SET is_disabled = 0, complete_date = NULL, is_fetched = NULL, retry_date = NULL, callback_date = NULL,"
                + " start_date = NULL, answer_date = NULL, telephony_result_code = NULL"
                + " WHERE id IN (" + String.join(", ", dto.getRecordIds()) + ")";
        try {
            int affected = campaignRecordDao.executeUpdate(campaign, sql, new ArrayList<>());
            clearCloseReasonOfActivatedRecords(campaign, ids);
            LOG.info(getLogMessageBase(campaign.getName(), String.join(", ", ids), ACTIVATE_RECORD.name(), null),
                    Constants.LOG_MODULE.PUZZLE);
            return new OperationOutcome<>(UPDATE_SUCCESSFUL, affected);
        } catch (SQLException e) {
            LOG.error(getLogMessageBase(campaign.getName(), String.join(", ", ids), ACTIVATE_RECORD.name(), e.getMessage()),
                    Constants.LOG_MODULE.PUZZLE, e.getMessage());
            return new OperationOutcome<>(UPDATE_FAILED, 0);
        }
    }


    /**
     * Task 325559 / Faz 8 (K3) — aktivasyon kaydin kapanis durumunu geri alir; numara basina limitle
     * eklenen kapanis durumu kolonu {@code close_reason} de bu sozlesmenin parcasidir. Temizlenmezse
     * aktive edilen kayit sonra baska bir sebeple kapandiginda (basarili gorusme, genel limit)
     * kolonda onceki kapanisin metni kalir.
     *
     * <p>Aktivasyon UPDATE'ine eklenmez, AYRI ve izole calisir: {@code close_reason} opsiyonel bir
     * binding'dir ve musterinin kendi tablosunda NOT NULL bir {@code close_reason} kolonu olabilir.
     * NULL yazimi orada reddedilir; ayni UPDATE icinde olsaydi o musteride aktivasyonun TAMAMI
     * duserdi. Hata WARN olarak loglanir ve aktivasyon sonucunu degistirmez.</p>
     */
    private void clearCloseReasonOfActivatedRecords(Campaign campaign, List<String> ids) {
        String closeReasonColumn = campaign.getColResultCloseReason();
        if (StringUtils.isBlank(closeReasonColumn)) {
            return;
        }
        String sql = "UPDATE " + campaign.getDbResultTable() + " SET " + closeReasonColumn + " = NULL"
                + " WHERE id IN (" + String.join(", ", ids) + ")";
        try {
            campaignRecordDao.executeUpdate(campaign, sql, new ArrayList<>());
        } catch (SQLException e) {
            log.warn("Close reason could not be cleared on record activation. campaign={}, ids={}, reason={}",
                    campaign.getName(), ids, e.getMessage());
        }
    }

    private OperationOutcome<Constants.RECORD_UPDATE_OPERATION_STATUS> addCallAsPriorityReturningCount(
            Campaign campaign, CampaignRecordsOperationDTO dto) {
        if (campaignPriorityService.existsByCampaignName(campaign.getName())) {
            return new OperationOutcome<>(CAMPAIGN_IS_ALREADY_PRIORITY, 0);
        }
        String logicOp = dto.getLogicOperator().toString();
        SqlClause clause = buildWhereClauseWithParams(dto.getFilterListDTO(), logicOp);
        String updateSql = "UPDATE " + campaign.getDbResultTable()
                + " SET Arama_Oncelik = ? WHERE " + clause.sql;
        List<Object> updateParams = new ArrayList<>();
        updateParams.add(1);
        updateParams.addAll(clause.params);
        try {
            int affected = campaignRecordDao.executeUpdate(campaign, updateSql, updateParams);
            saveCampaignPriority(campaign.getName(), dto);
            LOG.info(getLogMessageBase(campaign.getName(), clause.sql, ADD_CALL_AS_PRIORITY.name(), null),
                    Constants.LOG_MODULE.PUZZLE);
            return new OperationOutcome<>(UPDATE_SUCCESSFUL, affected);
        } catch (SQLException e) {
            LOG.error(getLogMessageBase(campaign.getName(), clause.sql, ADD_CALL_AS_PRIORITY.name(), e.getMessage()),
                    Constants.LOG_MODULE.PUZZLE, e.getMessage());
            return new OperationOutcome<>(UPDATE_FAILED, 0);
        }
    }




    private OperationOutcome<Constants.RECORD_UPDATE_OPERATION_STATUS> removeCallFromPriorityReturningCount(
            Campaign campaign, CampaignRecordsOperationDTO dto) {
        String logicOp = dto.getLogicOperator().toString();
        SqlClause clause = buildWhereClauseWithParams(dto.getFilterListDTO(), logicOp);
        String updateSql = "UPDATE " + campaign.getDbResultTable()
                + " SET Arama_Oncelik = ? WHERE " + clause.sql;
        List<Object> updateParams = new ArrayList<>();
        updateParams.add(0);
        updateParams.addAll(clause.params);
        try {
            int affected = campaignRecordDao.executeUpdate(campaign, updateSql, updateParams);
            deleteCampaignPriority(campaign.getName());
            LOG.info(getLogMessageBase(campaign.getName(), clause.sql, REMOVE_CALL_FROM_PRIORITY.name(), null),
                    Constants.LOG_MODULE.PUZZLE);
            return new OperationOutcome<>(UPDATE_SUCCESSFUL, affected);
        } catch (SQLException e) {
            LOG.error(getLogMessageBase(campaign.getName(), clause.sql, REMOVE_CALL_FROM_PRIORITY.name(), e.getMessage()),
                    Constants.LOG_MODULE.PUZZLE, e.getMessage());
            return new OperationOutcome<>(UPDATE_FAILED, 0);
        }
    }



    /** Parametrized variant that returns affected row count. */
    private OperationOutcome<Constants.RECORD_UPDATE_OPERATION_STATUS> bulkUpdateContactListReturningCount(
            Campaign campaign, CampaignRecordsOperationDTO dto) {
        List<String> ids = dto.getRecordIds() == null ? Collections.emptyList() : dto.getRecordIds();
        if (ids.isEmpty()) {
            return new OperationOutcome<>(UPDATE_SUCCESSFUL, 0);
        }

        String sql = "UPDATE " + campaign.getDbResultTable()
                + " SET " + dto.getField() + " = ?"
                + " WHERE id IN (" + String.join(", ", dto.getRecordIds()) + ")";
        List<Object> params = new ArrayList<>();
        params.add(dto.getValue());

        try {
            int affected = campaignRecordDao.executeUpdate(campaign, sql, params);
            LOG.info(getLogMessageBase(campaign.getName(), String.join(", ", ids), CONTACT_LIST_BULK_UPDATE.name(), null),
                    Constants.LOG_MODULE.PUZZLE);
            return new OperationOutcome<>(UPDATE_SUCCESSFUL, affected);
        } catch (SQLException e) {
            LOG.error(getLogMessageBase(campaign.getName(), String.join(", ", ids), CONTACT_LIST_BULK_UPDATE.name(), e.getMessage()),
                    Constants.LOG_MODULE.PUZZLE, e.getMessage());
            return new OperationOutcome<>(UPDATE_FAILED, 0);
        }
    }


    /** Parametrized variant that returns affected row count. */
    private OperationOutcome<Constants.RECORD_UPDATE_OPERATION_STATUS> deleteRecordReturningCount(
            Campaign campaign, CampaignRecordsOperationDTO dto) {
        List<String> ids = dto.getRecordIds() == null ? Collections.emptyList() : dto.getRecordIds();
        if (ids.isEmpty()) {
            return new OperationOutcome<>(UPDATE_SUCCESSFUL, 0);
        }

        String sql = "UPDATE " + campaign.getDbResultTable()
                + " SET is_disabled = 1, ELENME_NEDENI = ? WHERE id IN (" + String.join(", ", dto.getRecordIds()) + ")";
        List<Object> params = new ArrayList<>();
        params.add(Optional.ofNullable(dto.getReasonForDeletion()).map(Object::toString).orElse(null));

        try {
            int affected = campaignRecordDao.executeUpdate(campaign, sql, params);
            LOG.info(getLogMessageBase(campaign.getName(), String.join(", ", ids), DELETE_RECORD.name(), null),
                    Constants.LOG_MODULE.PUZZLE);
            return new OperationOutcome<>(UPDATE_SUCCESSFUL, affected);
        } catch (SQLException e) {
            LOG.error(getLogMessageBase(campaign.getName(), String.join(", ", ids), DELETE_RECORD.name(), e.getMessage()),
                    Constants.LOG_MODULE.PUZZLE, e.getMessage());
            return new OperationOutcome<>(UPDATE_FAILED, 0);
        }
    }

    private void saveCampaignPriority(String campaignName, CampaignRecordsOperationDTO campaignRecordsOperationDTO) {
        CampaignPriorityFormDTO campaignPriorityFormDTO = new CampaignPriorityFormDTO();
        campaignPriorityFormDTO.setCampaignName(campaignName);
        campaignPriorityFormDTO.setCampaignPriorityFilters(campaignRecordsOperationDTO.getFilterListDTO());
        campaignPriorityService.save(campaignPriorityFormDTO);
    }

    private void deleteCampaignPriority(String campaignName) {
        List<Long> ids = campaignPriorityService.getAllByCampaignName(campaignName);
        campaignPriorityService.delete(ids);
    }

    @Scheduled(cron = "0 */5 8-20 * * *")
    public void addRecordToPriority() {
        List<CampaignPriority> campaignPriorityList = campaignPriorityService.getAll(false, false);
        for (CampaignPriority campaignPriority : campaignPriorityList) {
            List<CampaignPriorityFilter> filters = campaignPriorityFilterService.getByCampaignPriorityId(campaignPriority.getId());
            if (filters.isEmpty()) {
                continue;
            }

            Campaign campaign = campaignService.getCampaignByNameOrId(campaignPriority.getCampaignName());
            String tableName = campaign.getDbResultTable();
            String logicOperator = filters.get(0).getLogicOperator().toString();
            List<String> conditions = new ArrayList<>();

            for (CampaignPriorityFilter filter : filters) {
                String field = filter.getField();
                String operator = filter.getCondition().toString();
                String value = filter.getValue();
                String fieldType = filter.getDb_field_type();

                prepareConditionFilters(value, operator, field, fieldType, conditions);
            }

            String whereClause = String.join(" " + logicOperator + " ", conditions);
            String selectQuery = String.format("SELECT id FROM %s WHERE complete_date IS NULL AND (%s)", tableName, whereClause);
            String updateQuery = String.format("UPDATE %s SET Arama_Oncelik = 1 WHERE id IN (%s)", tableName, selectQuery);

            try {
                dbEngine.executeUpdate(
                        updateQuery,
                        campaign.getId(),
                        campaign.getDbConnectionString(),
                        campaign.getDbUsername(),
                        enCipherUtils.decryptPassword(campaign.getDbPassword()));
            } catch (SQLException e) {
                String message = String.format("JOB failed to Add Records to priority for campaign %s with filter %s, reason: %s",
                        campaign.getName(), whereClause, e.getMessage());
                LOG.error(message, Constants.LOG_MODULE.PUZZLE, e.getMessage());
            }

            String message = String.format("JOB successfully to Added Records to priority for campaign %s with filter %s",
                    campaign.getName(), whereClause);
            LOG.info(message, Constants.LOG_MODULE.PUZZLE);
        }
    }

    private List<CampaignRecordFilterDTO.FilterRow> convertToFilterRows(List<Filter> filters) {
        if (filters == null) return Collections.emptyList();
        return filters.stream()
                .filter(f -> f.getType() == Constants.FILTER_TYPE.PREDEFINED && Boolean.FALSE.equals(f.getTechnical()))
                .map(f -> {
                    CampaignRecordFilterDTO.FilterRow row = new CampaignRecordFilterDTO.FilterRow();
                    row.setField(f.getField().getFieldName());
                    row.setCondition(f.getCondition());
                    row.setValue(f.getValue());
                    row.setFieldType(f.getField().getFieldType() != null ? f.getField().getFieldType().name() : null);
                    return row;
                })
                .collect(Collectors.toList());
    }


    /**
     * Type-aware predicate builder.
     * Uses declared DB field type to decide quoting, so digit-only strings on NVARCHAR/VARCHAR
     * are still quoted (e.g., ListID = '12345'). Falls back to STRING behavior when unknown.
     */
    private void prepareConditionFilters(String value, String operator, String field, String fieldType, List<String> conditions) {
        final String opRaw = operator == null ? "" : operator.trim();
        final String normalizedOp = "<>".equals(opRaw) ? "!=" : opRaw;
        final String val = value == null ? "" : value.trim();

        final com.powerdialer.util.Constants.FILTER_CONDITION cond;
        try {
            cond = com.powerdialer.util.Constants.FILTER_CONDITION.parse(normalizedOp.toLowerCase(Locale.ROOT));
        } catch (ParseException e) {
            return;
        }

        final boolean quoteType = needsQuote(fieldType);

        switch (cond) {
            case ISNULL:
            case ISNOTNULL: {
                conditions.add(field + " " + cond.condition());
                break;
            }

            case IN:
            case NOTIN: {
                if (val.isEmpty()) return;
                List<String> tokens = splitCsv(val);
                if (tokens.isEmpty()) return;
                String joined = tokens.stream()
                        .map(t -> quoteType ? quote(t) : (isNumericLiteral(t) ? t : null))
                        .filter(Objects::nonNull)
                        .collect(Collectors.joining(", "));
                if (joined.isEmpty()) return;
                conditions.add(field + " " + cond.condition() + " (" + joined + ")");
                break;
            }

            case LIKE:
            case NOTLIKE: {
                if (val.isEmpty()) return;
                conditions.add(field + " " + cond.condition() + " " + quote(val));
                break;
            }

            case EQUAL:
            case NOTEQUAL:
            case GREATER:
            case LESS:
            case GREATEROREQUAL:
            case LESSOREQUAL: {
                if (val.isEmpty()) return;
                final String rhs = quoteType ? quote(val) : (isNumericLiteral(val) ? val : null);
                if (rhs == null) return;
                conditions.add(field + " " + cond.condition() + " " + rhs);
                break;
            }

            default:
                break;
        }
    }

    private String getLogMessageBase(String campaignName, String idList, String action, String errorMessage) {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        String targetLabel = (ADD_CALL_AS_PRIORITY.name().equals(action) || REMOVE_CALL_FROM_PRIORITY.name().equals(action)) ? "Filters" : "Records ID";
        String status = (errorMessage != null) ? "Failed" : "Success";
        String reason = (errorMessage != null) ? ", Reason: " + errorMessage : "";

        return String.format(
                "Date: %s, User: %s, Roles: %s, Campaign: %s, %s: %s, Action: %s, Status: %s%s",
                new Date(), user.getUsername(), user.getRoles(), campaignName, targetLabel, idList, action, status, reason
        );
    }


    private static boolean needsQuote(String fieldType) {
        if (fieldType == null) return true;
        String t = fieldType.trim().toUpperCase(Locale.ROOT);
        if (t.contains("INT") || t.contains("DECIMAL") || t.contains("NUMERIC") ||
                t.contains("FLOAT") || t.contains("DOUBLE") || t.contains("REAL") ||
                "BIT".equals(t) || "BOOLEAN".equals(t)) {
            return false;
        }
        return true;
    }

    private static String quote(String raw) {
        return "'" + raw.replace("'", "''") + "'";
    }

    private static boolean isNumericLiteral(String s) {
        return s != null && s.matches("-?\\d+(?:\\.\\d+)?");
    }

  private static List<String> splitCsv(String s) {
    if (s == null || s.trim().isEmpty()) return Collections.emptyList();
        String[] arr = s.split(",");
        List<String> out = new ArrayList<>(arr.length);
        for (String a : arr) {
            String v = a.trim();
            if (!v.isEmpty()) out.add(v);
        }
        return out;
    }





    /**
          * Executes the requested record operation and returns both status and the exact affected row count (from DB).
          *
          * <ul>
          *   <li>Guards against RUNNING campaigns.</li>
          *   <li>Builds parametrized SQL (PreparedStatement) via DAO.</li>
          *   <li>Row count is sourced from JDBC {@code executeUpdate()}.</li>
          * </ul>
          *
          * @param campaignId target campaign id
          * @param dto operation payload
          * @return {@code OperationOutcome<RECORD_UPDATE_OPERATION_STATUS>}
          * @throws IllegalArgumentException if campaign does not exist
          */
    public OperationOutcome<Constants.RECORD_UPDATE_OPERATION_STATUS> applyOperationWithCount(
            Long campaignId, CampaignRecordsOperationDTO dto) {
        Campaign campaign = campaignMap.get(campaignId);
        if (campaign == null) {
            throw new IllegalArgumentException("Campaign not found for id: " + campaignId);
        }
        if (Constants.CAMPAIGN_STATE.RUNNING.equals(campaign.getState())) {
            return new OperationOutcome<>(Constants.RECORD_UPDATE_OPERATION_STATUS.CAMPAIGN_IS_RUNNING_CANT_UPDATE, 0);
        }
        switch (dto.getOperation()) {
            case ACTIVATE_RECORD:
                return activateRecordReturningCount(campaign, dto);
            case CONTACT_LIST_BULK_UPDATE:
                return bulkUpdateContactListReturningCount(campaign, dto);
            case DELETE_RECORD:
                return deleteRecordReturningCount(campaign, dto);
            case ADD_CALL_AS_PRIORITY:
                return addCallAsPriorityReturningCount(campaign, dto);
            case REMOVE_CALL_FROM_PRIORITY:
                return removeCallFromPriorityReturningCount(campaign, dto);
            default:
                return new OperationOutcome<>(UPDATE_FAILED, 0);
        }
    }


    /**
          * Backward compatible facade returning only status.
          * Delegates to {@link #applyOperationWithCount(Long, CampaignRecordsOperationDTO)} and discards the count.
          */
    public Constants.RECORD_UPDATE_OPERATION_STATUS applyOperation(Long campaignId, CampaignRecordsOperationDTO dto) {
        return applyOperationWithCount(campaignId, dto).getStatus();
    }


/*  314529 is_disabled bug'ı kapsamında kaldırıldı.
    private boolean isCompletedAndEnabled(Object completeDate, Object isDisabled) {
        return completeDate != null && isEnabled(isDisabled);
    }
*/

    private boolean hasAttemptLimitReached(Object attemptValue, Integer campaignAttemptLimit) {
        if (attemptValue == null || campaignAttemptLimit == null) return false;
        try {
            int att = Integer.parseInt(attemptValue.toString());
            return att >= campaignAttemptLimit;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private boolean isCallableConditionMet(Object completeDate,
                                           Object startDate,
                                           Object isDisabled,
                                           Object retryDate,
                                           Object callbackDate) {
        return completeDate == null
                && startDate != null
                && isEnabled(isDisabled)
                && retryDate == null
                && callbackDate == null;
    }

    private boolean isEliminatedConditionMet(Object isDisabled, Object updatedDate, Object updatedUser) {
        return isDisabled(isDisabled) && updatedDate == null && updatedUser == null;
    }

    private boolean isInProcessConditionMet(Object isFetched, Object startDate) {
        return isFetched != null && startDate == null;
    }

    private boolean isRetryConditionMet(Object retryDate, Object completeDate, Object isDisabled) {
        return retryDate != null && completeDate == null && isEnabled(isDisabled);
    }

    private boolean isCallbackConditionMet(Object callbackDate, Object completeDate, Object isDisabled) {
        return callbackDate != null && completeDate == null && isEnabled(isDisabled);
    }

    private boolean isExcludedConditionMet(Object isDisabled, Object updatedUser) {
        return isDisabled(isDisabled) && updatedUser != null;
    }

    private boolean isDncConditionMet(Object telephonyRc, Object isDisabled) {
        return telephonyRc != null
                && "76".equals(telephonyRc.toString())
                && isEnabled(isDisabled);
    }

    private boolean isEnabled(Object isDisabledFlag) {
        return "0".equals(stringVal(isDisabledFlag));
    }

    private boolean isDisabled(Object isDisabledFlag) {
        return "1".equals(stringVal(isDisabledFlag));
    }

    private String stringVal(Object o) {
        return o == null ? null : o.toString();
    }

}
