package com.powerdialer.engine.db;

import static com.powerdialer.engine.db.FilterUtils.addFetchedFilter;
import static com.powerdialer.engine.db.FilterUtils.createWhereFromFilters;
import static com.powerdialer.engine.db.FilterUtils.createWhereFromFiltersForPreparedStatement;
import static com.powerdialer.util.Constants.APPLY_TO.CALLBACKS;
import static com.powerdialer.util.Constants.APPLY_TO.RETRIES;
import static com.powerdialer.util.Constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import java.sql.SQLException;

import static com.powerdialer.util.Constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.CUSTOMER_NAME;
import static com.powerdialer.util.Constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.CUSTOMER_SURNAME;
import static com.powerdialer.util.Constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.RELATION;
import static com.powerdialer.util.Constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.RELATION_TYPE;
import static com.powerdialer.util.Constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.CALLED_GROUP_ID;

import com.powerdialer.util.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalTime;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import com.powerdialer.annotation.EscapeSqlNames;
import com.powerdialer.engine.acd.ACDEngine;
import com.powerdialer.engine.priority.PriorityService;
import com.powerdialer.exceptions.BadRequestColumnName;
import com.powerdialer.exceptions.IllegalValueException;
import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.Filter;
import com.powerdialer.model.ParametrizedQuery;
import com.powerdialer.model.PhoneNumberColumn;
import com.powerdialer.model.Skill;
import com.powerdialer.model.SortField;
import com.powerdialer.model.User;
import com.powerdialer.request.AddPhoneNumberColumnsRequest;
import com.powerdialer.request.CreateSourceTableRequest;
import com.powerdialer.request.CustomFieldsRequest;
import com.powerdialer.request.DbTableRequest;
import com.powerdialer.util.Constants.APPLY_TO;
import com.powerdialer.util.Constants.DB_TYPE;
import com.powerdialer.util.Constants.FILTER_CONDITION;
import com.powerdialer.util.Constants.FILTER_JOIN_TYPE;
import com.powerdialer.util.Constants.LOG_MODULE;
import com.powerdialer.util.Constants.TIMEZONE_TYPE;

/**
 * Campaign specific database actions handler
 */
@Component
@Slf4j
public class CampaignDbOperations {

    public static final String RESULT_TABLE_ALIAS_SPACE = "res ";
    public static final String RESULT_TABLE_ALIAS_DOT = "res.";
    public static final String SOURCE_TABLE_ALIAS_SPACE = "src ";
    public static final String SOURCE_TABLE_ALIAS_DOT = "src.";
    public static final String PRIORITY_TABLE_ALIAS_SPACE = "pri ";
    public static final String PRIORITY_TABLE_ALIAS_DOT = "pri.";
    private final LoggerDecorator LOG;
    private final ACDEngine acdEngine;
    private final DBEngine dbEngine;
    private final EnCipherUtils enCipherUtils;
    private final PriorityService priorityService;

    private final Map<Long, Set<String>> campaignColumnsCache = new ConcurrentHashMap<>();


    @Autowired
    public CampaignDbOperations(DBEngine dbEngine,
                                LoggerDecorator loggerDecorator,
                                ACDEngine acdEngine,
                                EnCipherUtils enCipherUtils,
                                PriorityService priorityService) {
        this.dbEngine = dbEngine;
        this.LOG = loggerDecorator.getLogger(CampaignDbOperations.class);
        this.acdEngine = acdEngine;
        this.enCipherUtils = enCipherUtils;
        this.priorityService = priorityService;
    }

    /**
     * Check connection to client db
     *
     * @param campaignId
     * @param dbConnectionString
     * @param dbUsername
     * @param dbPassword
     * @return true if can get connection
     * @throws SQLException
     */
    public boolean checkConnection(Long campaignId, String dbConnectionString, String dbUsername, String dbPassword) throws SQLException {
        return dbEngine.checkConnection(campaignId, dbConnectionString, dbUsername, dbPassword);
    }

    /**
     * Create data source for this campaign if campaignId is not null
     *
     * @param campaignId
     * @param dbConnectionString
     * @param dbUsername
     * @param dbPassword
     * @return true if can get connection
     * @throws SQLException
     */
    public boolean connect(Long campaignId, String dbConnectionString, String dbUsername, String dbPassword) throws SQLException {
        return dbEngine.connect(campaignId, dbConnectionString, dbUsername, dbPassword);
    }

    /**
     * Return available tables and views
     *
     * @param campaignId
     * @param dbConnectionString
     * @param dbUsername
     * @param dbPassword
     * @return available tables and views
     * @throws SQLException
     */
    public String[] getTablesAndViews(Long campaignId, String dbConnectionString, String dbUsername, String dbPassword) throws SQLException {
        DB_TYPE dbType = DB_TYPE.getByConnectionString(dbConnectionString);
        String sqlQuery;
        if (dbType == DB_TYPE.MS_SQL) {
            sqlQuery = "SELECT table_name FROM INFORMATION_SCHEMA.TABLES ORDER BY table_name";
        } else if (dbType == DB_TYPE.MYSQL) {
            sqlQuery = "SELECT table_name FROM INFORMATION_SCHEMA.TABLES WHERE table_type = 'base table' OR table_type = 'view' ORDER BY table_name";
        } else if (dbType == DB_TYPE.ORACLE) {
            sqlQuery = "SELECT table_name FROM USER_TABLES UNION ALL SELECT view_name FROM USER_VIEWS ORDER BY 1";
        } else if ((dbType == DB_TYPE.POSTGRE_SQL)) {
            sqlQuery = "select table_name from information_schema.tables where (table_type = 'BASE TABLE' OR table_type = " +
                    "'VIEW') AND NOT (table_schema IN ('pg_catalog', 'information_schema')) order by table_name";
        } else {
            throw new SQLException("No driver found for connection string: " + dbConnectionString);
        }
        return dbEngine.selectTablesAndViews(sqlQuery, campaignId, dbConnectionString, dbUsername, dbPassword);
    }

    /**
     * Create default source table for campaign
     *
     * @param request
     * @throws SQLException
     */
    public synchronized void createDefaultSourceTableForCampaign(CreateSourceTableRequest request) throws SQLException {
        StringBuilder query = new StringBuilder();
        DB_TYPE dbType = DB_TYPE.getByConnectionString(request.getConnectionString());

        if (dbType == DB_TYPE.MS_SQL) {
            query.append("CREATE TABLE ").append(request.getTableName()).append(" (");
            if (request.isFetchDataRandomly()) {
                query.append(RANDOM_ID.getValue()).append(" BIGINT DEFAULT (CONVERT([bigint],rand(CONVERT([varbinary](16),newid(),0))*(9223372036854775807.),0)) NOT NULL,");
            }
            query.append(CALLER_ID.getValue()).append("_1 NVARCHAR(255),");
            query.append(PHONE_NUMBER.getValue()).append("_1 NVARCHAR(255),");
            query.append(CALL_FROM_TIME.getValue()).append("_1 FLOAT,");
            query.append(CALL_TILL_TIME.getValue()).append("_1 FLOAT,");
            query.append(TIMEZONE_OFFSET.getValue()).append("_1 INT,");

            query.append(CALL_FROM_TIME.getValue()).append(" FLOAT,");
            query.append(CALL_TILL_TIME.getValue()).append(" FLOAT,");
            query.append(TIMEZONE_OFFSET.getValue()).append(" INT,");
            query.append(SPECIFIC_SKILL_NAME.getValue()).append(" NVARCHAR(255),");
            query.append(CUSTOMER_NO.getValue()).append(" NVARCHAR(255),");
            query.append(ID.getValue()).append(" BIGINT PRIMARY KEY NOT NULL IDENTITY");
            query.append(");");
        } else if (dbType == DB_TYPE.MYSQL) {
            query.append("CREATE TABLE ").append(request.getTableName()).append(" (");
            if (request.isFetchDataRandomly()) {
                query.append(RANDOM_ID.getValue()).append(" BIGINT,");
            }
            query.append(CALLER_ID.getValue()).append("_1 NVARCHAR(255),");
            query.append(PHONE_NUMBER.getValue()).append("_1 NVARCHAR(255),");
            query.append(CALL_FROM_TIME.getValue()).append("_1 FLOAT,");
            query.append(CALL_TILL_TIME.getValue()).append("_1 FLOAT,");
            query.append(TIMEZONE_OFFSET.getValue()).append("_1 INT,");

            query.append(CALL_FROM_TIME.getValue()).append(" FLOAT,");
            query.append(CALL_TILL_TIME.getValue()).append(" FLOAT,");
            query.append(TIMEZONE_OFFSET.getValue()).append(" INT,");
            query.append(SPECIFIC_SKILL_NAME.getValue()).append(" NVARCHAR(255),");
            query.append(CUSTOMER_NO.getValue()).append(" NVARCHAR(255),");
            query.append(ID.getValue()).append(" BIGINT NOT NULL AUTO_INCREMENT");
            query.append(");");

            if (request.isFetchDataRandomly()) {
                query.append("CREATE TRIGGER ").append(request.getTableName()).append("_rand BEFORE INSERT ON ").append(request.getTableName());
                query.append(" FOR EACH ROW SET NEW.").append(RANDOM_ID.getValue()).append("=ROUND(RAND(9223372036854775807))");
            }
        } else if (dbType == DB_TYPE.ORACLE) {
            query.append("BEGIN execute immediate 'CREATE TABLE \"").append(request.getTableName()).append("\" (");
            if (request.isFetchDataRandomly()) {
                query.append("\"").append(RANDOM_ID.getValue()).append("\" NUMBER(30),");
            }
            query.append("\"").append(CALLER_ID.getValue()).append("_1\" NVARCHAR2(255),");
            query.append("\"").append(PHONE_NUMBER.getValue()).append("_1\" NVARCHAR2(255),");
            query.append("\"").append(CALL_FROM_TIME.getValue()).append("_1\" FLOAT,");
            query.append("\"").append(CALL_TILL_TIME.getValue()).append("_1\" FLOAT,");
            query.append("\"").append(TIMEZONE_OFFSET.getValue()).append("_1\" NUMBER,");

            query.append("\"").append(CALL_FROM_TIME.getValue()).append("\" FLOAT,");
            query.append("\"").append(CALL_TILL_TIME.getValue()).append("\" FLOAT,");
            query.append("\"").append(TIMEZONE_OFFSET.getValue()).append("\" NUMBER,");
            query.append("\"").append(SPECIFIC_SKILL_NAME.getValue()).append("\" NVARCHAR2(255),");
            query.append("\"").append(CUSTOMER_NO.getValue()).append("\" NVARCHAR2(255),");
            query.append("\"").append(ID.getValue()).append("\" NUMBER(30),");
            query.append("CONSTRAINT \"").append(request.getTableName()).append("_pk\"").append(" PRIMARY KEY (\"").append(ID.getValue()).append("\")");
            query.append(")'; ");

            query.append("execute immediate 'CREATE sequence \"").append(request.getTableName()).append("_seq\" start with 1 increment by 1 nomaxvalue'; ");

            // trigger for autoincrement and random_id
            query.append("execute immediate 'create trigger \"").append(request.getTableName()).append("_trigger\" ");
            query.append("before insert on \"").append(request.getTableName()).append("\"");
            query.append(" for each row begin");
            query.append(" select \"").append(request.getTableName()).append("_seq\".nextval into :new.\"").append(ID.getValue()).append("\" from dual; ");
            if (request.isFetchDataRandomly()) {
                query.append("select DBMS_RANDOM.RANDOM into :new.\"").append(RANDOM_ID.getValue()).append("\" from dual; ");
            }
            query.append("END;';");
            query.append("END;");
        } else if (dbType == DB_TYPE.POSTGRE_SQL) {
            query.append("CREATE TABLE ").append(request.getTableName()).append(" (");
            if (request.isFetchDataRandomly()) {
                query.append("\"").append(RANDOM_ID.getValue()).append("\" VARCHAR(255) default md5(random()::text),");
            }
            query.append("\"").append(CALLER_ID.getValue()).append("_1\" VARCHAR(255),");
            query.append("\"").append(PHONE_NUMBER.getValue()).append("_1\" VARCHAR(255),");
            query.append("\"").append(CALL_FROM_TIME.getValue()).append("_1\" FLOAT,");
            query.append("\"").append(CALL_TILL_TIME.getValue()).append("_1\" FLOAT,");
            query.append("\"").append(TIMEZONE_OFFSET.getValue()).append("_1\" INTEGER,");

            query.append("\"").append(CALL_FROM_TIME.getValue()).append("\" FLOAT,");
            query.append("\"").append(CALL_TILL_TIME.getValue()).append("\" FLOAT,");
            query.append("\"").append(TIMEZONE_OFFSET.getValue()).append("\" INTEGER,");
            query.append("\"").append(SPECIFIC_SKILL_NAME.getValue()).append("\" VARCHAR(255),");
            query.append("\"").append(CUSTOMER_NO.getValue()).append("\" VARCHAR(255),");
            query.append("\"").append(ID.getValue()).append("\" BIGSERIAL NOT NULL PRIMARY KEY");
            query.append(");");
        } else {
            throw new SQLException("No driver found for connection string: " + request.getConnectionString());
        }

        dbEngine.executeUpdate(query.toString(), request.getCampaignId(), request.getConnectionString(), request.getDbUsername(), request.getDbPassword());
    }

    /**
     * Create default columns for phone numbers
     *
     * @param request
     * @throws SQLException
     */
    @EscapeSqlNames
    public synchronized void addDefaultPhoneNumberColumns(AddPhoneNumberColumnsRequest request) throws SQLException {
        StringBuilder query = new StringBuilder();
        DB_TYPE dbType = DB_TYPE.getByConnectionString(request.getConnectionString());

        String ncharType;
        String intType;
        if (dbType == DB_TYPE.MS_SQL || dbType == DB_TYPE.MYSQL) {
            ncharType = " NVARCHAR(255);";
            intType = " INT;";
        } else if (dbType == DB_TYPE.ORACLE) {
            ncharType = " NVARCHAR2(255);";
            intType = " NUMBER;";
        } else if (dbType == DB_TYPE.POSTGRE_SQL) {
            ncharType = " VARCHAR(255);";
            intType = " INTEGER;";
        } else {
            throw new SQLException("No driver found for connection string: " + request.getConnectionString());
        }

        query.append("ALTER TABLE ").append(request.getTableName()).append(" ADD ");
        query.append(request.getCallerIdColumnName()).append(ncharType);

        query.append("ALTER TABLE ").append(request.getTableName()).append(" ADD ");
        query.append(request.getPhoneNumberColumnName()).append(ncharType);

        query.append("ALTER TABLE ").append(request.getTableName()).append(" ADD ");
        query.append(request.getCallFromTimeColumnName()).append(" FLOAT;");

        query.append("ALTER TABLE ").append(request.getTableName()).append(" ADD ");
        query.append(request.getCallTillTimeColumnName()).append(" FLOAT;");

        query.append("ALTER TABLE ").append(request.getTableName()).append(" ADD ");
        query.append(request.getTimezoneOffsetColumnName()).append(intType);

        dbEngine.executeUpdate(query.toString(), request.getCampaignId(), request.getConnectionString(), request.getDbUsername(), request.getDbPassword());
    }

    /**
     * Create result table with default columns for campaign
     *
     * @param request
     * @throws SQLException
     */
    public synchronized void createDefaultResultTable(DbTableRequest request) throws SQLException {
        StringBuilder query = new StringBuilder();
        DB_TYPE dbType = DB_TYPE.getByConnectionString(request.getConnectionString());

        String ncharType;
        String intType;
        String bigintType;
        String dateType;
        String ncharMaxType;

        boolean shouldEscapeNames = shouldEscapeNames(dbType);
        if (dbType == DB_TYPE.MS_SQL || dbType == DB_TYPE.MYSQL) {
            ncharType = " NVARCHAR(255),";
            ncharMaxType = " NVARCHAR(MAX),";
            intType = " INT,";
            bigintType = " BIGINT,";
            dateType = " DATETIME,";
        } else if (dbType == DB_TYPE.ORACLE) {
            ncharType = " NVARCHAR2(255),";
            intType = " NUMBER,";
            ncharMaxType = " CLOB,";
            bigintType = " NUMBER(30),";
            dateType = " TIMESTAMP,";
        } else if (dbType == DB_TYPE.POSTGRE_SQL) {
            ncharType = " VARCHAR(255),";
            ncharMaxType = " TEXT,";
            intType = " INTEGER,";
            bigintType = " BIGINT,";
            dateType = " TIMESTAMP,";
        } else {
            throw new SQLException("No driver found for connection string: " + request.getConnectionString());
        }
        query.append("CREATE TABLE ");
        if (shouldEscapeNames) {
            query.append("\"").append(request.getTableName()).append("\"");
        } else {
            query.append(request.getTableName());
        }
        query.append(" (");

        query.append(UNIQUE_KEY.getValue(shouldEscapeNames)).append(bigintType);
        query.append(START_DATE.getValue(shouldEscapeNames)).append(" FLOAT,");
        query.append(ANSWER_DATE.getValue(shouldEscapeNames)).append(" FLOAT,");
        query.append(COMPLETE_DATE.getValue(shouldEscapeNames)).append(" FLOAT,");
        query.append(CALLED_NUMBER.getValue(shouldEscapeNames)).append(ncharType);
        query.append(TELEPHONY_RC.getValue(shouldEscapeNames)).append(intType);
        query.append(USER_RC.getValue(shouldEscapeNames)).append(bigintType);
        query.append(RC_SEQUENCE.getValue(shouldEscapeNames)).append(intType);
        query.append(USER_ID.getValue(shouldEscapeNames)).append(bigintType);
        query.append(CALLBACK_NUMBER.getValue(shouldEscapeNames)).append(ncharType);
        query.append(CALLBACK_DATE.getValue(shouldEscapeNames)).append(" FLOAT,");
        query.append(RETRY_NUMBER.getValue(shouldEscapeNames)).append(ncharType);
        query.append(RETRY_HISTORY.getValue(shouldEscapeNames)).append(ncharMaxType);
        query.append(CB_HISTORY.getValue(shouldEscapeNames)).append(ncharMaxType);
        query.append(CALL_PROCESS.getValue(shouldEscapeNames)).append(ncharMaxType);
        query.append(RETRY_DATE.getValue(shouldEscapeNames)).append(" FLOAT,");
        query.append(FETCHED_DATA_FLAG.getValue(shouldEscapeNames)).append(" FLOAT,");
        query.append(DNC_NUMBERS.getValue(shouldEscapeNames)).append(ncharType);
        query.append(ATTEMPTS.getValue(shouldEscapeNames)).append(intType);
        query.append(RESPONSE_VALUES.getValue(shouldEscapeNames)).append(" NVARCHAR(4000),");
        query.append(CALLBACK_TYPE.getValue(shouldEscapeNames)).append(intType);
        // Task 325559: numara başına limit kaynaklı kapanışın nedeni.
        query.append(CLOSE_REASON.getValue(shouldEscapeNames)).append(ncharType);
        query.append(UPDATE_DATE.getValue(shouldEscapeNames)).append(dateType.replace(",", ""));

        query.append(")");
        dbEngine.executeUpdate(query.toString(), request.getCampaignId(), request.getConnectionString(), request.getDbUsername(), request.getDbPassword());
    }

    /**
     * Add default result columns to source table
     *
     * @param request
     * @throws SQLException
     */
    public synchronized void addDefaultResultColumnsToSourceTable(DbTableRequest request) throws SQLException {
        DB_TYPE dbType = DB_TYPE.getByConnectionString(request.getConnectionString());

        String ncharType;
        String intType;
        String bigintType;
        String dateType;
        String ncharMaxType;
        boolean shouldEscapeNames = shouldEscapeNames(dbType);
        if (dbType == DB_TYPE.MS_SQL || dbType == DB_TYPE.MYSQL) {
            ncharType = " NVARCHAR(255),";
            ncharMaxType = " NVARCHAR(MAX),";
            intType = " INT,";
            bigintType = " BIGINT,";
            dateType = " DATETIME,";
        } else if (dbType == DB_TYPE.ORACLE) {
            ncharType = " NVARCHAR2(255),";
            ncharMaxType = " CLOB,";
            intType = " NUMBER,";
            bigintType = " NUMBER(30),";
            dateType = " TIMESTAMP,";
        } else if (dbType == DB_TYPE.POSTGRE_SQL) {
            ncharType = " VARCHAR(255),";
            ncharMaxType = " TEXT,";
            intType = " INTEGER,";
            bigintType = " BIGINT,";
            dateType = " TIMESTAMP,";
        } else {
            throw new SQLException("No driver found for connection string: " + request.getConnectionString());
        }

        StringBuilder query = createAddColumnsQuery(dbType, shouldEscapeNames, request.getTableName());

        addColumn(query, dbType, START_DATE.getValue(shouldEscapeNames), " FLOAT,");
        addColumn(query, dbType, ANSWER_DATE.getValue(shouldEscapeNames), " FLOAT,");
        addColumn(query, dbType, COMPLETE_DATE.getValue(shouldEscapeNames), " FLOAT,");
        addColumn(query, dbType, CALLED_NUMBER.getValue(shouldEscapeNames), ncharType);
        addColumn(query, dbType, TELEPHONY_RC.getValue(shouldEscapeNames), intType);
        addColumn(query, dbType, USER_RC.getValue(shouldEscapeNames), bigintType);
        addColumn(query, dbType, RC_SEQUENCE.getValue(shouldEscapeNames), intType);
        addColumn(query, dbType, USER_ID.getValue(shouldEscapeNames), bigintType);
        addColumn(query, dbType, CALLBACK_NUMBER.getValue(shouldEscapeNames), ncharType);
        addColumn(query, dbType, CALLBACK_DATE.getValue(shouldEscapeNames), " FLOAT,");
        addColumn(query, dbType, RETRY_NUMBER.getValue(shouldEscapeNames), ncharType);
        addColumn(query, dbType, RETRY_HISTORY.getValue(shouldEscapeNames), ncharMaxType);
        addColumn(query, dbType, CB_HISTORY.getValue(shouldEscapeNames), ncharMaxType);
        addColumn(query, dbType, CALL_PROCESS.getValue(shouldEscapeNames), ncharMaxType);
        addColumn(query, dbType, RETRY_DATE.getValue(shouldEscapeNames), " FLOAT,");
        addColumn(query, dbType, DNC_NUMBERS.getValue(shouldEscapeNames), ncharType);
        addColumn(query, dbType, FETCHED_DATA_FLAG.getValue(shouldEscapeNames), " FLOAT,");
        addColumn(query, dbType, ATTEMPTS.getValue(shouldEscapeNames), intType);
        addColumn(query, dbType, RESPONSE_VALUES.getValue(shouldEscapeNames), " NVARCHAR(4000),");
        addColumn(query, dbType, CALLBACK_TYPE.getValue(shouldEscapeNames), intType);
        // Task 325559: numara başına limit kaynaklı kapanışın nedeni.
        addColumn(query, dbType, CLOSE_REASON.getValue(shouldEscapeNames), ncharType);
        addColumn(query, dbType, UPDATE_DATE.getValue(shouldEscapeNames), dateType.replace(",", ""));

        if (dbType == DB_TYPE.ORACLE) {
            query.append(")");
        } else {
            query.append(";");
        }

        dbEngine.executeUpdate(query.toString(), request.getCampaignId(), request.getConnectionString(), request.getDbUsername(), request.getDbPassword());
    }

    /**
     * Add default result columns to source table
     *
     * @param request
     * @throws SQLException
     */
    public synchronized void addCustomColumnsToSourceTable(CustomFieldsRequest request) throws SQLException {
        if (CollectionUtils.isEmpty(request.getCustomFields())) {
            return;
        }
        DB_TYPE dbType = DB_TYPE.getByConnectionString(request.getConnectionString());
        String ncharType;
        String intType;
        String dateType;
        boolean shouldEscapeNames = shouldEscapeNames(dbType);
        if (dbType == DB_TYPE.MS_SQL || dbType == DB_TYPE.MYSQL) {
            ncharType = "NVARCHAR(255),";
            intType = "INT,";
            dateType = "DATETIME,";
        } else if (dbType == DB_TYPE.ORACLE) {
            ncharType = "NVARCHAR2(255),";
            intType = "NUMBER,";
            dateType = "TIMESTAMP,";
        } else if (dbType == DB_TYPE.POSTGRE_SQL) {
            ncharType = "VARCHAR(255),";
            intType = "INTEGER,";
            dateType = "timestamp without time zone,";
        } else {
            throw new SQLException("No driver found for connection string: " + request.getConnectionString());
        }
        StringBuilder query = createAddColumnsQuery(dbType, shouldEscapeNames, request.getTableName());

        request.getCustomFields().forEach(f -> {
            String columnType = null;
            switch (f.getFieldType()) {
                case NVARCHAR2:
                    columnType = ncharType;
                    break;
                case INT:
                    columnType = intType;
                    break;
                case FLOAT:
                    columnType = "FLOAT,";
                    break;
                case DATETIME:
                    columnType = dateType;
            }
            addColumn(query, dbType, getEscapedValue(f.getFieldName(), shouldEscapeNames), columnType);
        });
        query.deleteCharAt(query.length() - 1);
        if (dbType == DB_TYPE.ORACLE) {
            query.append(")");
        } else {
            query.append(";");
        }
        dbEngine.executeUpdate(query.toString(), request.getCampaignId(), request.getConnectionString(), request.getDbUsername(), request.getDbPassword());
    }

    private StringBuilder createAddColumnsQuery(DB_TYPE dbType, boolean shouldEscapeNames, String tableName) {
        StringBuilder query = new StringBuilder("ALTER TABLE ");
        if (shouldEscapeNames) {
            query.append("\"").append(tableName).append("\"");
        } else {
            query.append(tableName);
        }

        if (dbType != DB_TYPE.POSTGRE_SQL) {
            query.append(" ADD ");
        }

        if (dbType == DB_TYPE.ORACLE) {
            query.append("(");
        }
        return query;
    }

    public String getEscapedValue(String value, boolean escapeValue) {
        return (escapeValue) ? "\"" + value + "\"" : value;
    }

    private void addColumn(StringBuilder query, DB_TYPE dbType, String columnName, String columnType) {
        if (dbType == DB_TYPE.POSTGRE_SQL) {
            query.append(" ADD COLUMN ");
        }
        query.append(columnName).append(" ").append(columnType);
    }

    /**
     * Get columns of chosen table
     *
     * @param tableName
     * @param campaignId
     * @param dbConnectionString
     * @param dbUsername
     * @param dbPassword
     * @return array of columns
     * @throws SQLException
     */
    /**
     * Check whether the given column has an index on the specified table.
     *
     * @param tableName           full table name (may include schema, e.g. dbo.MY_TABLE)
     * @param columnName          column to check for an index
     * @param campaignId          used for data-source lookup
     * @param dbConnectionString  JDBC connection string
     * @param dbUsername           DB username
     * @param dbPassword           DB password (already decrypted)
     * @return true if at least one index covers the column
     * @throws SQLException on connection / query errors
     */
    public boolean hasIndexOnColumn(String tableName, String columnName, Long campaignId,
                                    String dbConnectionString, String dbUsername, String dbPassword) throws SQLException {
        DB_TYPE dbType = DB_TYPE.getByConnectionString(dbConnectionString);
        StringBuilder sqlQuery = new StringBuilder();

        // Extract bare table name (strip schema prefix and quotes)
        String bareTableName = tableName;
        if (bareTableName.contains(".")) {
            bareTableName = bareTableName.substring(bareTableName.lastIndexOf('.') + 1);
        }
        bareTableName = bareTableName.replace("\"", "").replace("[", "").replace("]", "");

        if (dbType == DB_TYPE.MS_SQL) {
            sqlQuery.append("SELECT COUNT(*) AS IDX_COUNT FROM sys.index_columns ic ")
                    .append("JOIN sys.indexes i ON ic.object_id = i.object_id AND ic.index_id = i.index_id ")
                    .append("JOIN sys.columns c ON ic.object_id = c.object_id AND ic.column_id = c.column_id ")
                    .append("WHERE ic.object_id = OBJECT_ID(?) AND c.name = ?");
        } else if (dbType == DB_TYPE.MYSQL) {
            sqlQuery.append("SELECT COUNT(*) AS IDX_COUNT FROM INFORMATION_SCHEMA.STATISTICS ")
                    .append("WHERE table_name = ? AND column_name = ?");
        } else if (dbType == DB_TYPE.ORACLE) {
            bareTableName = bareTableName.toUpperCase();
            columnName = columnName.toUpperCase();
            sqlQuery.append("SELECT COUNT(*) AS IDX_COUNT FROM USER_IND_COLUMNS ")
                    .append("WHERE table_name = ? AND column_name = ?");
        } else if (dbType == DB_TYPE.POSTGRE_SQL) {
            sqlQuery.append("SELECT COUNT(*) AS IDX_COUNT FROM pg_indexes pi ")
                    .append("WHERE pi.tablename = ? AND pi.indexdef ILIKE '%' || ? || '%'");
        } else {
            throw new SQLException("No driver found for connection string: " + dbConnectionString);
        }

        // For MSSQL use the full table name (with schema) for OBJECT_ID(); for others use bare name
        String tableParam = (dbType == DB_TYPE.MS_SQL) ? tableName : bareTableName;
        List<Object> params = new ArrayList<>();
        params.add(tableParam);
        params.add(columnName);

        JSONArray result = dbEngine.executeParametrizedQuery(
                new ParametrizedQuery(sqlQuery.toString(), params),
                campaignId, null, dbConnectionString, dbUsername, dbPassword);

        if (result != null && result.length() > 0) {
            int count = Integer.parseInt(result.getJSONObject(0).get("IDX_COUNT").toString());
            return count > 0;
        }
        return false;
    }

    public String[][] getTableColumnsWithTypes(String tableName, Long campaignId, String dbConnectionString, String dbUsername, String dbPassword) throws SQLException {
        DB_TYPE dbType = DB_TYPE.getByConnectionString(dbConnectionString);
        StringBuilder sqlQuery = new StringBuilder();
        if (StringUtils.isBlank(tableName)) {
            throw new SQLException("Cannot get columns for empty table name");
        }
        if (dbType == DB_TYPE.MS_SQL || dbType == DB_TYPE.MYSQL) {
            sqlQuery.append("SELECT column_name, data_type FROM INFORMATION_SCHEMA.COLUMNS WHERE table_name = ?");
        } else if (dbType == DB_TYPE.ORACLE) {
            if (tableName.contains("\"")) {
                tableName = unescapeSqlName(tableName);
            }
            sqlQuery.append("SELECT column_name, data_type FROM USER_TAB_COLUMNS WHERE table_name = ? ORDER BY column_id");
        } else if (dbType == DB_TYPE.POSTGRE_SQL) {
            sqlQuery.append("select column_name, data_type from information_schema.columns where table_name = ?");
        } else {
            throw new SQLException("No driver found for connection string: " + dbConnectionString);
        }
        return dbEngine.selectColumnsWithTypes(new ParametrizedQuery(sqlQuery.toString(), Collections.singletonList(tableName)), campaignId, dbConnectionString, dbUsername, dbPassword);
    }

    private String unescapeSqlName(String tableName) {
        return tableName.length() > 2 ? tableName.substring(1, tableName.length() - 1) :
                tableName;
    }

    /**
     * Create new data source for database
     *
     * @param dbConnectionString
     * @param dbUsername
     * @param dbPassword
     * @param campaignId
     * @return data source
     * @throws SQLException
     */
    public void addDataSource(Long campaignId, String dbConnectionString, String dbUsername, String dbPassword) {
        try {
            dbEngine.getDataSource(campaignId, dbConnectionString, dbUsername, dbPassword);
        } catch (SQLException e) {
            LOG.error(e, e, LOG_MODULE.DB);
        }
    }

    /**
     * Close and remove registered data source
     *
     * @param campaignId
     * @return true if successful
     */
    public boolean removeDataSource(Long campaignId) {
        if (campaignId != null) {
            try {
                dbEngine.closeDataSource(campaignId);
                return true;
            } catch (SQLException e) {
                return false;
            }
        } else {
            return false;
        }
    }

    /**
     * Test the whole campaign filter set. Return first 100 rows of filtered
     * result
     *
     * @param campaign
     * @return array of rows
     * @throws SQLException
     */
    @EscapeSqlNames
    public String[][] filtersTest(Campaign campaign) throws SQLException {
        return filtersTest(campaign, campaign.getEnabledFilters(), false);
    }

    /**
     * Test the single campaign filter.
     *
     * @param campaign
     * @param filter
     * @throws SQLException
     */
    @EscapeSqlNames
    public void filterTest(Campaign campaign, Filter filter) throws SQLException {
        filtersTest(campaign, Collections.singletonList(filter), true);
    }

    private String[][] filtersTest(Campaign campaign, List<Filter> filters, boolean selectNone) throws SQLException {
        StringBuilder query = createSelectAllQuery(campaign);
        List<Object> params;
        String whereFromFilters = createWhereFromFiltersForPreparedStatement(filters,
                campaign.getFilterJoinType(),
                SOURCE_TABLE_ALIAS_DOT,
                getResultAliasWithDot(campaign),
                DB_TYPE.getByConnectionString(campaign.getDbConnectionString()));
        if (!whereFromFilters.isEmpty()) {
            query.append(" WHERE ").append(whereFromFilters);
            params = getNonNullValuesFromPredefinedFilters(filters);
        } else {
            params = Collections.emptyList();
        }
        if (selectNone) {
            query.append(" AND 1=0");
        } else {
            query.append(createOrderByClause(campaign));
        }
        return dbEngine.select(new ParametrizedQuery(query.toString(), params),
                Constants.FILTER_TEST_LIMIT,
                campaign.getId(),
                campaign.getDbConnectionString(),
                campaign.getDbUsername(),
                enCipherUtils.decryptPassword(campaign.getDbPassword()));
    }

    private StringBuilder createSelectAllQuery(Campaign campaign) {
        return createSelectAllQuery(campaign, null);
    }

    /**
     * SELECT * sorgusunu kurar; {@code maxRows} verildiğinde satır limiti sorgu metninde taşınır.
     *
     * Task 325461: limit eskiden yalnız {@link DBEngine} tarafında
     * {@code PreparedStatement.setMaxRows} ile veriliyordu. Sürücü bunu sunucuda
     * {@code SET ROWCOUNT} olarak uygular; optimizer satır tahmininde ve memory grant
     * hesabında kullanmaz. ORDER BY blocking operatör olduğu için sıralama eşleşen kümenin
     * tamamı için boyutlanır, grant kuyruğa girer (RESOURCE_SEMAPHORE) ve sorgu 10 saniyelik
     * query timeout'una takılır. Limit sorgunun parçası olduğunda plan Top N Sort'a döner ve
     * grant ~maxRows satır için boyutlanır.
     */
    private StringBuilder createSelectAllQuery(Campaign campaign, Integer maxRows) {
        return new StringBuilder("SELECT ")
                .append(createRowLimitPrefix(campaign, maxRows))
                .append("* FROM ")
                .append(createFromClause(campaign));
    }

    /**
     * Satır limitinin SELECT listesinden önce gelen kısmı (MS SQL: {@code TOP (n)}).
     *
     * Kapsam kararı (task 325461): yalnız MS SQL kolu dolduruldu; diğer DB tipleri bugünkü
     * davranışta (limit ifadesi üretilmez) bırakıldı. Oracle'a taşıma yapıldığında limit
     * ORDER BY'dan SONRA eklenmelidir ({@code FETCH FIRST n ROWS ONLY}, Oracle 12c+);
     * PostgreSQL/MySQL için {@code LIMIT n}. Ek noktası: bu metot ile fetch metotlarındaki
     * {@code createOrderByClause} çağrısının hemen ardı.
     *
     * {@code maxRows <= 0} bilinçli olarak limitsiz kabul edilir: JDBC'de
     * {@code setMaxRows(0)} "limitsiz" demektir ve {@code CallProducer} retry fetch'ine
     * {@code retryRatio * remain / 100} sonucu olarak 0 gönderebiliyor. {@code TOP (0)}
     * yazmak "hiç satır" anlamına gelip mevcut davranışı değiştirirdi.
     */
    private static String createRowLimitPrefix(Campaign campaign, Integer maxRows) {
        if (maxRows == null || maxRows <= 0 || campaign.getDbConnectionString() == null) {
            return "";
        }
        return DB_TYPE.MS_SQL.equals(DB_TYPE.getByConnectionString(campaign.getDbConnectionString()))
                ? "TOP (" + maxRows + ") "
                : "";
    }

    private String createFromClause(Campaign campaign) {
        StringBuilder query = new StringBuilder(campaign.getDbListTable()).append(" ").append(SOURCE_TABLE_ALIAS_SPACE);
        if (!campaign.hasSameSourceAndResultTable()) {
            query.append("LEFT JOIN ").append(campaign.getDbResultTable()).append(" ").append(RESULT_TABLE_ALIAS_SPACE)
                    .append("ON (").append(SOURCE_TABLE_ALIAS_DOT).append(campaign.getColUniqueKey()).append(" = ")
                    .append(RESULT_TABLE_ALIAS_DOT).append(campaign.getColResultUniqueKey()).append(") ");
        }
        if (campaign.isMultipleSort() && StringUtils.isNotBlank(campaign.getDbPriorityTable())) {
            query.append("LEFT JOIN ").append(campaign.getDbPriorityTable()).append(" ").append(PRIORITY_TABLE_ALIAS_SPACE)
                    .append("ON (").append(SOURCE_TABLE_ALIAS_DOT).append(campaign.getColUniqueKey()).append(" = ")
                    .append(PRIORITY_TABLE_ALIAS_DOT).append(campaign.getColPriorityUniqueKey()).append(") ");
        }
        return query.toString();
    }

    /**
     * Create string for order by and order direction
     *
     * @param campaign
     * @return string of order by clause
     */
    private String createOrderByClause(Campaign campaign) {
        if (StringUtils.isEmpty(campaign.getColSortBy()) || campaign.getDbListSortDirection() == null)
            return "";
        if (campaign.isMultipleSort()) {
            return priorityService.getOrderByClause(campaign);
        } else {
            return " ORDER BY " + SOURCE_TABLE_ALIAS_DOT + campaign.getColSortBy() + " " + campaign.getDbListSortDirection();
        }
    }

    /**
     * Fetch fresh data for data producer thread for per campaign
     *
     * @param campaign
     * @param maxRows
     * @return result of fetched data as JsonArray
     * @throws SQLException
     */
    @EscapeSqlNames
    public JSONArray fetchDataForProducerFresh(Campaign campaign, Integer maxRows, Optional<Filter> segmentationFilter) throws SQLException {
        String resultAliasWithDot = getResultAliasWithDot(campaign);
        StringBuilder query = createSelectAllQuery(campaign, maxRows);
        List<Filter> nonSegmentationFilters = campaign.getEnabledFiltersBySegmentation(false);
        String campaignWhereFromFilters = createWhereFromFilters(nonSegmentationFilters, segmentationFilter, campaign);
        query.append(" WHERE ").append(resultAliasWithDot).append(campaign.getColResultFetchedDataFlag()).append(" IS NULL");
        query.append(" AND ").append(resultAliasWithDot).append(campaign.getColResultCompleteDate()).append(" IS NULL");
        if (campaign.getRetryScheduleEnabled())
            query.append(" AND ").append(resultAliasWithDot).append(campaign.getColResultRetryDate()).append(" IS NULL");
        if (campaign.getAllowCallback() && campaign.getColResultCallbackDate() != null)
            query.append(" AND ").append(resultAliasWithDot).append(campaign.getColResultCallbackDate()).append(" IS NULL");
        if (!campaign.getTimezoneType().equals(TIMEZONE_TYPE.OFF))
            appendTimezoneToQuery(query, campaign);
        if (campaign.getSkillEnabled()) {
            String skillFilter = createSkillFilter(campaign);
            if (!StringUtils.isEmpty(skillFilter)) {
                query.append(" AND ").append(skillFilter);
            }
        }
        query.append(campaignWhereFromFilters.length() > 0 ? " AND " + campaignWhereFromFilters : "");
        query.append(createOrderByClause(campaign));
        return dbEngine.executeQuery(query.toString(),
                campaign.getId(),
                maxRows,
                campaign.getDbConnectionString(),
                campaign.getDbUsername(),
                enCipherUtils.decryptPassword(campaign.getDbPassword()));
    }

    /**
     * Create filter from skills of idle users. Also add condition for fetching
     * rows with no required skill
     *
     * @param campaign
     * @return
     */
    private String createSkillFilter(Campaign campaign) {
        if (StringUtils.isNotEmpty(campaign.getColSkill())) {
            Set<User> campaignUsers = acdEngine.getCampaignIdleUsers(campaign);
            Set<Skill> allSkillsOfOnlineUsers = new HashSet<>();
            for (User campaignUser : campaignUsers) {
                Map<Skill, Long> skillWeightPair = campaignUser.getSkillWeightPair();
                Set<Skill> userSkills = skillWeightPair.keySet();
                allSkillsOfOnlineUsers.addAll(userSkills);
            }
            Skill noSkill = new Skill();
            noSkill.setName("");
            allSkillsOfOnlineUsers.add(noSkill);
            String skillFilter = FilterUtils.createSkillFilter(campaign.getColSkill(), allSkillsOfOnlineUsers);
            return "(" + skillFilter + " OR " + campaign.getColSkill() + " IS NULL) ";
        } else {
            return "";
        }
    }

    /**
     * Append timezone connected conditionals to query
     *
     * @param query
     * @param campaign
     */
    private void appendTimezoneToQuery(StringBuilder query, Campaign campaign) {
        String colTimeZoneOffset;
        String colCallFromTime;
        String colCallTillTime;
        if (campaign.getTimezoneType().equals(TIMEZONE_TYPE.SIMPLE)) {
            colTimeZoneOffset = campaign.getColTimezoneOffset();
            colCallFromTime = campaign.getColCallFromTime();
            colCallTillTime = campaign.getColCallToTime();
        } else if (campaign.getTimezoneType().equals(TIMEZONE_TYPE.ADVANCED)) {
            PhoneNumberColumn numberColumn = campaign.getEnabledPhoneNumberColumns().get(0);
            colTimeZoneOffset = numberColumn.getColTimezoneOffset();
            colCallFromTime = numberColumn.getColCallFromTime();
            colCallTillTime = numberColumn.getColCallTillTime();
        } else {
            LOG.error("Can't append timezone to query: " + query + " because of timezone type: " + campaign.getTimezoneType());
            return;
        }
        StringBuilder fromTillTimeRestrictions = createFromTillTimeRestrictions(colTimeZoneOffset, colCallFromTime, colCallTillTime);
        query.append(fromTillTimeRestrictions);
    }

    /**
     * Fetch callbacks data for data producer thread for per campaign
     *
     * @param campaign
     * @param maxRows
     * @param usersIds
     * @return result of fetched data as JsonArray
     * @throws SQLException
     */
    @EscapeSqlNames
    public JSONArray fetchDataForProducerCallbacks(Campaign campaign, Integer maxRows, List<Long> usersIds, Optional<Filter> segmentationFilter) throws SQLException {
        StringBuilder query = createSelectAllQuery(campaign, maxRows);
        // Task 325461: callback tarih koşulu literal yerine parametreyle bağlanıyor; böylece
        // sorgu metni çalıştırmalar arasında sabit kalır ve plan yeniden kullanılabilir.
        List<Object> queryParameters = new ArrayList<>();
        addCallbacksWhereClause(campaign, usersIds, query, getResultAliasWithDot(campaign), segmentationFilter, queryParameters);
        query.append(createOrderByClause(campaign));
        return dbEngine.executeParametrizedQuery(new ParametrizedQuery(query.toString(), queryParameters),
                campaign.getId(),
                maxRows,
                campaign.getDbConnectionString(),
                campaign.getDbUsername(),
                enCipherUtils.decryptPassword(campaign.getDbPassword()));
    }

    //TODO: TO LOOK BACK AT
    private void addCallbackCondition(StringBuilder query, String resultAlias, Campaign campaign, List<Long> usersIds, List<Object> queryParameters) {
        Date currentDate = new Date();
        double now = Utils.convertDateToDouble(currentDate);
        // Task 325461: tarih literali yerine parametre. Parametreler eklendikleri sırayla
        // bağlanır; agent id listesi literal olarak yazıldığı için parametre üretmez.
        query.append(" AND ").append(resultAlias).append(campaign.getColResultCallbackDate()).append(" <= ?");
        queryParameters.add(now);
        if (!CollectionUtils.isEmpty(usersIds) && campaign.getColResultUserId() != null) {
            query.append(" AND (");
            // Task 321997: zaman temelli kaçış (callback_date <= now-resetTimeout) yalnız TIMER stratejisinde
            // uygulanır. ATTEMPT_COUNT'ta release user_id=NULL ile yapıldığından bu OR dalı eklenmez (defansif:
            // appointerReset yanlışlıkla true olsa bile attempt modunda time-escape devreye girmez).
            if (campaign.getAppointerReset() && !campaign.isAppointerResetByAttempt()) {
                double nowMinusResetTimeout = Utils.convertDateToDouble(new Date(currentDate.getTime() - campaign.getAppointerResetTimeout() * 1000));
                addAgentsIdsCondition(query, resultAlias, campaign, usersIds);
                query.append(" OR ").append(resultAlias).append(campaign.getColResultCallbackDate()).append(" <= ?");
                queryParameters.add(nowMinusResetTimeout);
            } else {
                // CB to Appointer enabled but Reset Appointer disabled
                // Only fetch callbacks assigned to active users (user_id IS NULL or IN active list)
                addAgentsIdsCondition(query, resultAlias, campaign, usersIds);
            }
            query.append(")");
        }
    }

    private void addAgentsIdsCondition(StringBuilder query, String resultAlias, Campaign campaign, List<Long> usersIds) {
        query.append(resultAlias)
                .append(campaign.getColResultUserId())
                .append(" is null or (")
                .append(campaign.getColResultUserId())
                .append(" IN ")
                .append(usersIds.stream().map(Object::toString).collect(Collectors.joining(",", "(", ")")))
                .append(" )");

    }

    /**
     * Fetch retry data for data producer thread for per campaign
     *
     * @param campaign
     * @param maxRows
     * @return result of fetched data as JsonArray
     * @throws SQLException
     */
    @EscapeSqlNames
    public JSONArray fetchDataForProducerRetries(Campaign campaign, Integer maxRows, Optional<Filter> segmentationFilter) throws SQLException {
        String resultAlias = getResultAliasWithDot(campaign);
        StringBuilder query = createSelectAllQuery(campaign, maxRows);
        // Task 325461: retry tarih koşulu literal yerine parametreyle bağlanıyor; sorgu metni
        // sabit kaldığı için plan yeniden kullanılabilir ve memory grant feedback öğrenebilir.
        List<Object> queryParameters = new ArrayList<>();
        query.append(" WHERE ").append(resultAlias).append(campaign.getColResultFetchedDataFlag()).append(" IS NULL");
        query.append(" AND ").append(resultAlias).append(campaign.getColResultCompleteDate()).append(" IS NULL");
        if (campaign.getAllowCallback() && campaign.getColResultCallbackDate() != null) {
            query.append(" AND ").append(resultAlias).append(campaign.getColResultCallbackDate()).append(" IS NULL");
        }
        double now = Utils.convertDateToDouble(new Date());
        query.append(" AND ").append(resultAlias).append(campaign.getColResultRetryDate()).append(" <= ?");
        queryParameters.add(now);
        appendPerCycleAttemptsLimitToRetryQuery(query, campaign, resultAlias);
        appendTimezoneOptionsTo(query, campaign, RETRIES, resultAlias);
        if (campaign.isApplyFilterRetries() || segmentationFilter.isPresent()) {
            List<Filter> nonSegmentationFilters = campaign.getEnabledFiltersBySegmentation(false);
            String campaignWhereFromFilters = createWhereFromFilters(nonSegmentationFilters, segmentationFilter, campaign);
            query.append(campaignWhereFromFilters.length() > 0 ? " AND " + campaignWhereFromFilters : "");
        }
        if (campaign.getSkillEnabled()) {
            String skillFilter = createSkillFilter(campaign);
            if (!StringUtils.isEmpty(skillFilter)) {
                query.append(" AND ").append(skillFilter);
            }
        }
        query.append(createOrderByClause(campaign));
        return dbEngine.executeParametrizedQuery(new ParametrizedQuery(query.toString(), queryParameters),
                campaign.getId(),
                maxRows,
                campaign.getDbConnectionString(),
                campaign.getDbUsername(),
                enCipherUtils.decryptPassword(campaign.getDbPassword()));
    }

    private void appendPerCycleAttemptsLimitToRetryQuery(StringBuilder query, Campaign campaign, String resultAlias) {
        if (!Constants.RETRY_STRATEGY.ROUND_ROBIN_DIAL_WITH_PER_CYCLE_RETRY_LIMIT_SCHEDULE_NEXT_NUMBER.equals(campaign.getRetryStrategy())) {
            return;
        }
        if (campaign.getColResultAttempts() == null || campaign.getRetryOveralAttempt() == null || campaign.getRetryOveralAttempt() <= 0) {
            return;
        }

        query.append(" AND (")
                .append(resultAlias).append(campaign.getColResultAttempts())
                .append(" IS NULL OR ")
                .append(resultAlias).append(campaign.getColResultAttempts())
                .append(" < ")
                .append(campaign.getRetryOveralAttempt())
                .append(")");
    }

    /**
     * Append timezone connected conditionals to retry or callback query
     *
     * @param query
     * @param campaign
     */
    private void appendTimezoneOptionsTo(StringBuilder query, Campaign campaign, APPLY_TO applyTo, String resultAliasWithDot) {
        if (campaign.getTimezoneType().equals(TIMEZONE_TYPE.SIMPLE)) {
            StringBuilder fromTillTimeRestrictions = createFromTillTimeRestrictions(campaign.getColTimezoneOffset(),
                    campaign.getColCallFromTime(),
                    campaign.getColCallToTime());
            query.append(fromTillTimeRestrictions);
        } else if (campaign.getTimezoneType().equals(TIMEZONE_TYPE.ADVANCED)) {
            String colResultNumberWithAlias;
            if (RETRIES.equals(applyTo)) {
                colResultNumberWithAlias = resultAliasWithDot + campaign.getColResultRetryNumber();
            } else if (CALLBACKS.equals(applyTo) && campaign.getColResultCallbackDate() != null && campaign.getColResultCallbackNumber() != null) {
                colResultNumberWithAlias = resultAliasWithDot + campaign.getColResultCallbackNumber();
            } else {
                throw new NotImplementedException("applyTo parameter can't be: " + applyTo);
            }
            query.append(" AND (");
            // todo callback to custom number (which not exists is prone numbers list)
            // with advanced timezone will be failed.
            // Callback from and till date must be taken from previous called number
            for (int i = 0; i < campaign.getEnabledPhoneNumberColumns().size(); i++) {
                PhoneNumberColumn phoneNumberColumn = campaign.getEnabledPhoneNumberColumns().get(i);
                query.append("(").append(colResultNumberWithAlias).append(" = ").append(phoneNumberColumn.getColNumber());
                String colTimezoneOffset = phoneNumberColumn.getColTimezoneOffset();
                String colCallFromTime = phoneNumberColumn.getColCallFromTime();
                String colCallTillTime = phoneNumberColumn.getColCallTillTime();
                StringBuilder fromTillTimeRestrictions = createFromTillTimeRestrictions(colTimezoneOffset, colCallFromTime, colCallTillTime);
                query.append(fromTillTimeRestrictions);
                query.append(")");
                if (i < campaign.getEnabledPhoneNumberColumns().size() - 1)
                    query.append(" OR ");
            }
            query.append(")");
        }
    }

    /**
     * Create restrictions based on now time. Only rows with suitable from and
     * till date will pass this restrictions. In another words rows which passed
     * this restrictions could be used to perform dialing now. For range which
     * overlap 2 days (e. g. from 23 to 1) till time must be >1
     *
     * @param colTimezoneOffset
     * @param colCallFromTime
     * @param colCallTillTime
     * @return conditions that can be used in where clause of query
     */
    private StringBuilder createFromTillTimeRestrictions(String colTimezoneOffset, String colCallFromTime, String colCallTillTime) {
        StringBuilder queryRestrictions = new StringBuilder();
        double nowTimeAtUTC = Utils.convertTimeToDouble(new LocalTime(DateTimeZone.UTC));
        String timezoneOffset = "(" + SOURCE_TABLE_ALIAS_DOT + colTimezoneOffset + " / 24.0)";
        // todo now+timezone offset might be >24 or <0 (at the beginning or end of
        // the day)
        String nowTimeWithTimezoneOffset = nowTimeAtUTC + " + " + timezoneOffset;
        queryRestrictions.append(" AND ((");
        queryRestrictions.append(SOURCE_TABLE_ALIAS_DOT).append(colCallFromTime).append(" <= ").append(nowTimeWithTimezoneOffset);
        queryRestrictions.append(" AND ");
        queryRestrictions.append(SOURCE_TABLE_ALIAS_DOT).append(colCallTillTime).append(" >= ").append(nowTimeWithTimezoneOffset);
        queryRestrictions.append(") OR (");

        String nowBetweenTwoDaysWithTimeZoneOffset = nowTimeWithTimezoneOffset + " + 1";
        queryRestrictions.append(SOURCE_TABLE_ALIAS_DOT)
                .append(colCallFromTime)
                .append(" <= ")
                .append(nowBetweenTwoDaysWithTimeZoneOffset);
        queryRestrictions.append(" AND ")
                .append(SOURCE_TABLE_ALIAS_DOT)
                .append(colCallTillTime)
                .append(" >= ")
                .append(nowBetweenTwoDaysWithTimeZoneOffset);
        queryRestrictions.append("))");
        return queryRestrictions;
    }

    /**
     * Fetch data for campaign using specified filters
     *
     * @throws SQLException
     */
    @EscapeSqlNames
    public JSONArray fetchDataByFilters(Campaign campaign, List<Filter> filters, FILTER_JOIN_TYPE join_type, Integer limit) throws SQLException {
        StringBuilder query = createSelectAllQuery(campaign);
        List<Object> params;
        String whereFromFilters = createWhereFromFiltersForPreparedStatement(filters,
                join_type,
                SOURCE_TABLE_ALIAS_DOT,
                getResultAliasWithDot(campaign),
                DB_TYPE.getByConnectionString(campaign.getDbConnectionString()));
        if (!whereFromFilters.isEmpty()) {
            query.append(" WHERE ").append(whereFromFilters);
            params = getNonNullValuesFromPredefinedFilters(filters);
        } else {
            params = Collections.emptyList();
        }
        return dbEngine.executeParametrizedQuery(new ParametrizedQuery(query.toString(), params),
                campaign.getId(),
                limit,
                campaign.getDbConnectionString(),
                campaign.getDbUsername(),
                enCipherUtils.decryptPassword(campaign.getDbPassword()));
    }

    /**
     * Count campaign's data which satisfies specified filters
     *
     * @throws SQLException
     */
    @EscapeSqlNames
    public int countDataByFilters(Campaign campaign, List<Filter> filters, FILTER_JOIN_TYPE join_type) throws SQLException {
        StringBuilder query = new StringBuilder();
        query.append("SELECT COUNT(").append(campaign.getColUniqueKey()).append(") AS \"PD_ROWS\" FROM ").append(createFromClause(campaign));
        String whereFromFilters = createWhereFromFiltersForPreparedStatement(filters,
                join_type,
                SOURCE_TABLE_ALIAS_DOT,
                getResultAliasWithDot(campaign),
                DB_TYPE.getByConnectionString(campaign.getDbConnectionString()));
        List<Object> params;
        if (whereFromFilters.isEmpty()) {
            params = Collections.emptyList();
        } else {
            query.append(" WHERE ").append(whereFromFilters);
            params = getNonNullValuesFromPredefinedFilters(filters);
        }
        JSONArray jsonArray = dbEngine.executeParametrizedQuery(new ParametrizedQuery(query.toString(), params),
                campaign.getId(),
                1,
                campaign.getDbConnectionString(),
                campaign.getDbUsername(),
                enCipherUtils.decryptPassword(campaign.getDbPassword()));
        return jsonArray.getJSONObject(0).getInt("PD_ROWS");
    }

    private List<Object> getNonNullValuesFromPredefinedFilters(List<Filter> filters) {
        return filters.stream()
                .filter(f -> Constants.FILTER_TYPE.PREDEFINED.equals(f.getType()))
                .filter(f -> f.getCondition() == null || !f.getCondition().isNullCheck())
                .filter(f -> f.getValue() != null && !"null".equalsIgnoreCase(f.getValue()))
                .map(this::mapFilterValue)
                .collect(Collectors.toList());
    }

    private Object mapFilterValue(Filter filter) {
        if (filter.getCondition() == FILTER_CONDITION.IN || filter.getCondition() == FILTER_CONDITION.NOTIN)
            return filter.getValue().split("\\s*,\\s*");
        return filter.getValue();
    }

    /**
     * Check is filters have only campaign's columns
     *
     * @param campaign
     * @param filters
     * @return
     * @throws SQLException
     */
    public boolean checkFilterFields(Campaign campaign, List<Filter> filters) throws SQLException {
        Set<String> allCampaignColumnNames = new HashSet<>(getCampaignsTablesColumnNames(campaign));
        for (Filter filter : filters) {
            if (!allCampaignColumnNames.contains(filter.getField().getFieldName())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Fetch edilen satırları bu fetch adına rezerve eder ({@code is_fetched = now}, start/answer
     * date reset; ayrı sonuç tablosunda eksik satırlar INSERT edilir) ve rezervasyonu
     * ALAMADIĞI satırların uniqueKey'lerini döner. Çağıran bu satırlar için Call üretmez.
     *
     * <p>FIX-330538: rezervasyon UPDATE'i yalnız {@code is_fetched IS NULL} satırları kapar
     * (optimistic guard). SELECT ile UPDATE arasında satırı başka bir fetch veya node rezerve
     * ettiyse onun işareti ezilmez. Etkilenen satır sayısı batch'ten küçükse bu fetch'in işaretini
     * taşıyan satırlar tek bir yeniden-okuma ile saptanır; normal akışta (tüm satırlar kapandı) ek
     * sorgu yoktur. {@code executeUpdate} sayısı trigger'lı tablolarda güvenilir olmadığından yalnız
     * ön kontrol olarak kullanılır; karar yeniden-okumaya bırakılır.</p>
     *
     * <p>Fail-open kuralı: yeniden-okuma bu fetch'in işaretini HİÇBİR satırda doğrulayamazsa batch
     * bugünkü davranışla korunur (boş küme döner, hiçbir satır düşürülmez). Aksi hâlde yeniden-okumanın
     * güvenilmez olduğu bir tabloda (kolon tipi / literal dönüşümü / hatalı rowcount) her tick tüm batch
     * düşer, satırlar "DB'de fetched, cache'te yok" kalır ve kampanya durur. Düşürme yalnız yeniden-okuma
     * çalıştığını kanıtladığında (en az bir satırı doğruladığında) yapılır.</p>
     *
     * @param campaign kampanya
     * @param calls    aynı fetch tick'inde üretilen, uniqueKey'leri birbirinden farklı Call'lar
     * @return bu fetch'in rezerve EDEMEDİĞİ uniqueKey'ler; normal akışta boş küme
     * @throws SQLException
     */
    @EscapeSqlNames
    public Set<String> reserveRowsForFetch(Campaign campaign, List<Call> calls) throws SQLException {
        if (calls == null || calls.isEmpty()) {
            return Collections.emptySet();
        }
        DB_TYPE dbType = DB_TYPE.getByConnectionString(campaign.getDbConnectionString());
        double now = Utils.convertDateToDouble(new Date());
        String fetchedFlagLiteral = fetchedFlagLiteral(dbType, now);
        String joinedCallsIds = getUniqueKeysOfCallsAsString(calls);
        String rowKeyColumn = columnToCheckRowExistence(campaign);
        String fetchedFlagColumn = campaign.getColResultFetchedDataFlag();

        StringBuilder updateQuery = new StringBuilder();
        updateQuery.append("UPDATE ").append(campaign.getDbResultTable()).append(" SET ");
        // Task 323316: fetch (kuyruğa alma) işaretlemesi UpdatedDate'i (col_result_update_date)
        // ARTIK ezmiyor. Bu kolon raporlamada "işlem gören kayıt tarihi" ve expire tarihi için
        // kullanılıyor; fetch'te güncellenmesi, hiç aranmamış kaydın fetch gününe düşmesine
        // (hayalet kayıt) yol açıyordu. UpdatedDate yalnız gerçek sonuç yazımı ve expire ile hareket eder.
        updateQuery.append(fetchedFlagColumn).append(" = ").append(fetchedFlagLiteral);
        appendStartAndAnswerDatesReset(campaign, updateQuery);
        updateQuery.append(" WHERE ").append(rowKeyColumn).append(" IN (").append(joinedCallsIds).append(")");
        // FIX-330538: optimistic guard — başka bir fetch'in/node'un işaretini ezme.
        updateQuery.append(" AND ").append(fetchedFlagColumn).append(" IS NULL");
        int reservedRows = executeUpdateReturningRowCount(updateQuery.toString(), campaign);

        if (!campaign.hasSameSourceAndResultTable()) {
            StringBuilder insertQuery = new StringBuilder();
            insertQuery.append("INSERT INTO ").append(campaign.getDbResultTable());
            // Task 323316: yeni fetch-satırı da UpdatedDate ile oluşturulmuyor (bkz. yukarıdaki UPDATE branch açıklaması).
            insertQuery.append("(").append(fetchedFlagColumn).append(",").append(rowKeyColumn).append(")");
            if (dbType == DB_TYPE.ORACLE) {
                insertQuery.append("SELECT ").append(now).append(", ").append(campaign.getColUniqueKey());
            } else {
                insertQuery.append("SELECT '").append(now).append("', ").append(campaign.getColUniqueKey());
            }
            insertQuery.append(" FROM ").append(campaign.getDbListTable());
            insertQuery.append(" WHERE ").append(campaign.getColUniqueKey()).append(" IN (").append(getCallIdsNotPresentResultTableQuery(campaign, joinedCallsIds)).append(")");
            reservedRows += executeUpdateReturningRowCount(insertQuery.toString(), campaign);
        }

        if (reservedRows >= calls.size()) {
            return Collections.emptySet();
        }

        Set<String> reservedKeys = findRowKeysReservedBy(campaign, rowKeyColumn, fetchedFlagColumn, joinedCallsIds, fetchedFlagLiteral);
        Set<String> notReserved = new HashSet<>();
        for (Call call : calls) {
            if (!reservedKeys.contains(normalizeRowKey(call.getUniqueKey()))) {
                notReserved.add(call.getUniqueKey());
            }
        }
        if (notReserved.size() == calls.size()) {
            // Fail-open: yeniden-okuma bu fetch'in işaretini HİÇBİR satırda görmedi. Ya batch'in tamamı
            // gerçekten başka bir fetch'e gitti (yalnız birebir eşzamanlı çok-node yarışında) ya da
            // yeniden-okuma bu tabloda güvenilir değil (kolon tipi / literal dönüşümü / hatalı rowcount).
            // İkinci durumda batch'i düşürmek satırları "DB'de fetched, cache'te yok" bırakır ve her tick
            // tekrarlanarak kampanyayı DURDURUR. Bu yüzden hiçbir satır doğrulanamazsa bugünkü davranışa
            // geri düşülür (batch korunur); düşürme yalnız yeniden-okumanın çalıştığını kanıtladığı (en az
            // bir satırı doğruladığı) durumda yapılır.
            LOG.warn("[FETCH-RESERVATION] " + campaign + ": re-read confirmed none of " + calls.size()
                    + " row(s) (DB reported " + reservedRows + " reserved); keeping the batch as before, no row dropped");
            return Collections.emptySet();
        }
        return notReserved;
    }

    /**
     * UPDATE, yeniden-okuma ve geri alma aynı literal metnini kullanır; böylece
     * {@code is_fetched = <literal>} karşılaştırması kolon tipinden bağımsız olarak yazımla aynı
     * dönüşümden geçer. MS SQL'de tırnaklı (mevcut davranış), Oracle/PostgreSQL'de sayısal.
     */
    private static String fetchedFlagLiteral(DB_TYPE dbType, double now) {
        if (dbType == DB_TYPE.ORACLE || dbType == DB_TYPE.POSTGRE_SQL) {
            return String.valueOf(now);
        }
        return "'" + now + "'";
    }

    private int executeUpdateReturningRowCount(String query, Campaign campaign) throws SQLException {
        return dbEngine.executeUpdate(query,
                campaign.getId(),
                campaign.getDbConnectionString(),
                campaign.getDbUsername(),
                enCipherUtils.decryptPassword(campaign.getDbPassword()));
    }

    /**
     * Batch içinden bu fetch'in işaretini ({@code is_fetched = literal}) taşıyan satırların
     * anahtarlarını okur. Yalnız anomali yolunda (rezerve edilen satır sayısı batch'ten küçük)
     * çalışır.
     */
    private Set<String> findRowKeysReservedBy(Campaign campaign, String rowKeyColumn, String fetchedFlagColumn,
                                              String joinedCallsIds, String fetchedFlagLiteral) throws SQLException {
        String query = "SELECT " + rowKeyColumn + " AS \"PD_KEY\" FROM " + campaign.getDbResultTable()
                + " WHERE " + rowKeyColumn + " IN (" + joinedCallsIds + ")"
                + " AND " + fetchedFlagColumn + " = " + fetchedFlagLiteral;
        JSONArray rows = dbEngine.executeQuery(query,
                campaign.getId(),
                null,
                campaign.getDbConnectionString(),
                campaign.getDbUsername(),
                enCipherUtils.decryptPassword(campaign.getDbPassword()));
        Set<String> reservedKeys = new HashSet<>();
        if (rows == null) {
            return reservedKeys;
        }
        for (int i = 0; i < rows.length(); i++) {
            Object key = rows.getJSONObject(i).opt("PD_KEY");
            if (key != null) {
                reservedKeys.add(normalizeRowKey(key.toString()));
            }
        }
        return reservedKeys;
    }

    /**
     * Yeniden-okumadan dönen anahtar ile {@code Call.uniqueKey} metinsel olarak farklı biçimlenebilir
     * ("310493" / "310493.0"). Sayısal anahtarlar değer eşdeğerliğiyle, diğerleri trim'lenmiş
     * metinle karşılaştırılır.
     */
    static String normalizeRowKey(String key) {
        if (key == null) {
            return null;
        }
        String trimmed = key.trim();
        try {
            return new BigDecimal(trimmed).stripTrailingZeros().toPlainString();
        } catch (NumberFormatException e) {
            return trimmed;
        }
    }

    private void appendStartAndAnswerDatesReset(Campaign campaign, StringBuilder query) {
        query.append(", ").append(campaign.getColResultStartDate()).append(" = NULL");
        query.append(", ").append(campaign.getColResultAnswerDate()).append(" = NULL");
    }

    /**
     * Get query that return call ids which not present in result table and
     * present in parameter {@param joinedCallsIds}
     *
     * @param campaign
     * @param joinedCallsIds
     * @return
     */
    private StringBuilder getCallIdsNotPresentResultTableQuery(Campaign campaign, String joinedCallsIds) {
        StringBuilder query = new StringBuilder();
        String resultAliasWithDot = getResultAliasWithDot(campaign);
        query.append("SELECT ").append(SOURCE_TABLE_ALIAS_DOT).append(campaign.getColUniqueKey())
                .append(" FROM ").append(campaign.getDbListTable()).append(" ").append(SOURCE_TABLE_ALIAS_SPACE)
                .append("LEFT JOIN ").append(campaign.getDbResultTable()).append(" ").append(resultAliasWithDot.replace(".", ""))
                .append(" ON ").append(SOURCE_TABLE_ALIAS_DOT).append(campaign.getColUniqueKey()).append("=").append(resultAliasWithDot).append(campaign.getColResultUniqueKey())
                .append(" WHERE ").append(SOURCE_TABLE_ALIAS_DOT).append(campaign.getColUniqueKey()).append(" IN (").append(joinedCallsIds).append(")")
                .append(" AND ").append(resultAliasWithDot).append(campaign.getColResultUniqueKey()).append(" IS NULL");
        return query;
    }

    /**
     * Generate String for uniqueKeys
     *
     * @param calls
     * @return string of uniqueKeys
     */
    private String getUniqueKeysOfCallsAsString(Collection<Call> calls) {
        return calls.stream().map(Call::getUniqueKey).collect(Collectors.joining(","));
    }

    /**
     * Update campaign data using filters
     *
     * @throws SQLException
     */
    @EscapeSqlNames
    public int updateCampaignData(Campaign campaign, List<Filter> filters, FILTER_JOIN_TYPE joinType, Map<String, Object> dataToUpdate) throws SQLException {
        checkDataValues(dataToUpdate.values());
        List<String> allCampaignColumnNames = getCampaignsTablesColumnNames(campaign);
        Set<String> columnNames = dataToUpdate.keySet();
        if (allCampaignColumnNames.containsAll(columnNames) || allCampaignColumnNames.containsAll(unescapeSqlNames(columnNames))) {
            return updateCampaignDataUnsafe(campaign, filters, joinType, dataToUpdate);
        } else {
            columnNames.removeAll(allCampaignColumnNames);
            throw new BadRequestColumnName("Request contains unknown columns: " + Arrays.toString(columnNames.toArray()));
        }
    }

    /**
     * Update campaign data using filters. This method is faster than
     * {@link CampaignDbOperations#updateCampaignData(Campaign, List, FILTER_JOIN_TYPE, Map)}
     * but do not have SQL injection protection for data keys
     *
     * @throws SQLException
     */
    @EscapeSqlNames
    public int updateCampaignDataUnsafe(Campaign campaign, List<Filter> filters, FILTER_JOIN_TYPE joinType, Map<String, Object> dataToUpdate) throws SQLException {
        StringBuilder query = new StringBuilder();
        query.append("UPDATE ").append(SOURCE_TABLE_ALIAS_SPACE).append(" SET ");
        List<Object> parameters = new ArrayList<>();
        DatabaseUtil.validateColumnNames(campaign.getDbConnectionString(), dataToUpdate);
        DatabaseUtil.validateColumnNames(campaign.getDbConnectionString(), filters);
        Iterator<Map.Entry<String, Object>> entryIterator = dataToUpdate.entrySet().iterator();
        while (entryIterator.hasNext()) {
            Map.Entry<String, Object> entry = entryIterator.next();
            String k = entry.getKey();
            Object v = entry.getValue();
            query.append(k).append(" = ?");
            if (entryIterator.hasNext()) {
                query.append(",");
            } else {
                query.append(" ");
            }
            parameters.add(v);
        }
        query.append(" FROM ").append(createFromClause(campaign));
        addFetchedFilter(campaign, filters);
        String whereFromFilters = createWhereFromFilters(filters, joinType, SOURCE_TABLE_ALIAS_DOT, getResultAliasWithDot(campaign),
                DB_TYPE.getByConnectionString(campaign.getDbConnectionString()));
        if (StringUtils.isNotEmpty(whereFromFilters))
            query.append(" WHERE ").append(whereFromFilters);
        return dbEngine.executeBatchParametrizedUpdate(new ParametrizedQuery(query.toString(), parameters),
                campaign.getId(),
                campaign.getDbConnectionString(),
                campaign.getDbUsername(),
                enCipherUtils.decryptPassword(campaign.getDbPassword()));
    }

    /**
     * Remove quote marks from start and end of all strings in collection
     *
     * @param names
     * @return
     */
    private List<String> unescapeSqlNames(Collection<String> names) {
        return names.stream()
                .map(this::unescapeSqlName)
                .collect(Collectors.toList());
    }

    /**
     * Delete rows from campaign's list table by given filters
     *
     * @param campaign
     * @param filters
     * @param join_type
     * @throws SQLException
     */
    @EscapeSqlNames
    public int deleteCampaignData(Campaign campaign, List<Filter> filters, FILTER_JOIN_TYPE join_type) throws SQLException {
        StringBuilder query = new StringBuilder();
        query.append("DELETE ").append(SOURCE_TABLE_ALIAS_SPACE).append("FROM ").append(createFromClause(campaign));
        addFetchedFilter(campaign, filters);
        DatabaseUtil.validateColumnNames(campaign.getDbConnectionString(), filters);
        String whereFromFilters = createWhereFromFiltersForPreparedStatement(filters,
                join_type,
                SOURCE_TABLE_ALIAS_DOT,
                getResultAliasWithDot(campaign),
                DB_TYPE.getByConnectionString(campaign.getDbConnectionString()));
        List<Object> params;
        if (StringUtils.isEmpty(whereFromFilters)) {
            params = Collections.emptyList();
        } else {
            query.append(" WHERE ").append(whereFromFilters);
            params = getNonNullValuesFromPredefinedFilters(filters);
        }
        return dbEngine.executeBatchParametrizedUpdate(new ParametrizedQuery(query.toString(), params),
                campaign.getId(),
                campaign.getDbConnectionString(),
                campaign.getDbUsername(),
                enCipherUtils.decryptPassword(campaign.getDbPassword()));
    }

    /**
     * Set new value to column for all campaign data which satisfies campaign
     * filters and {@param filters}
     *
     * @param column   - column to update
     * @param value    - new valued for column
     * @param campaign - campaign to update
     * @param filters  - filter for data which need to update
     * @throws SQLException
     */
    @EscapeSqlNames
    public void setResultColumnToValue(String column, Object value, Campaign campaign, Boolean useCampaignFilters, List<Filter> filters) throws SQLException {
        StringBuilder query = new StringBuilder();
        DB_TYPE dbType = DB_TYPE.getByConnectionString(campaign.getDbConnectionString());
        String campaignWhereFromFilters = "";
        if (campaign.hasSameSourceAndResultTable()) {
            boolean shouldEscapeName = shouldEscapeNames(dbType);
            if (Boolean.TRUE.equals(useCampaignFilters))
                campaignWhereFromFilters = createWhereFromFilters(campaign.getEnabledFilters(), campaign.getFilterJoinType(), "", "", dbType);
            String whereFromFilters = createWhereFromFilters(filters, FILTER_JOIN_TYPE.AND, "", "", dbType);
            String joinedFilters = "";
            if (!campaignWhereFromFilters.isEmpty() || !whereFromFilters.isEmpty())
                joinedFilters = Stream.of(campaignWhereFromFilters, whereFromFilters).filter(f -> !f.isEmpty()).collect(Collectors.joining(") AND (", " WHERE (", ")"));
            query.append("UPDATE ").append(campaign.getDbResultTable()).append(" SET ");
            if (shouldEscapeName) {
                query.append("\"").append(column).append("\"");
            } else {
                query.append(column);
            }
            query.append(" = '").append(value).append("'");
            if (!joinedFilters.isEmpty())
                query.append(joinedFilters);
        } else {
            String resultAliasWithDot = getResultAliasWithDot(campaign);
            if (Boolean.TRUE.equals(useCampaignFilters))
                campaignWhereFromFilters = createWhereFromFilters(campaign);
            String whereFromFilters = createWhereFromFilters(filters, FILTER_JOIN_TYPE.AND, SOURCE_TABLE_ALIAS_DOT, resultAliasWithDot, dbType);
            String joinedFilters = "";
            if (!campaignWhereFromFilters.isEmpty() || !whereFromFilters.isEmpty())
                joinedFilters = Stream.of(campaignWhereFromFilters, whereFromFilters).filter(f -> !f.isEmpty()).collect(Collectors.joining(") AND (", " WHERE (", ")"));
            StringBuilder getCallsIdsInResultTableQuery = new StringBuilder();
            getCallsIdsInResultTableQuery.append("SELECT ").append(resultAliasWithDot).append(campaign.getColResultUniqueKey()).append(" FROM ")
                    .append(createFromClause(campaign));

            if (!joinedFilters.isEmpty())
                getCallsIdsInResultTableQuery.append(joinedFilters);

            query.append("UPDATE ").append(campaign.getDbResultTable()).append(" SET ");
            query.append(column).append(" = '").append(value).append("'").append(" WHERE ").append(campaign.getColResultUniqueKey()).append(" IN (").append(getCallsIdsInResultTableQuery)
                    .append(")");
        }
        dbEngine.executeUpdate(query.toString(),
                campaign.getId(),
                campaign.getDbConnectionString(),
                campaign.getDbUsername(),
                enCipherUtils.decryptPassword(campaign.getDbPassword()));
    }

    /**
     * Get not completed calls count for campaign
     *
     * @param campaign
     * @return
     * @throws SQLException
     */
    @EscapeSqlNames
    public int getNotCompletedCallsCount(Campaign campaign) throws SQLException {
        StringBuilder query = new StringBuilder();
        String resultAliasWithDot = getResultAliasWithDot(campaign);
        Supplier<String> whereFilterSupplier = () -> {
            String filters = createWhereFromFilters(campaign);
            return filters.isEmpty() ? "" : filters + " AND ";
        };

        query.append("SELECT count(*) FROM ");
        if (campaign.hasSameSourceAndResultTable()) {
            query.append(campaign.getDbResultTable()).append(" WHERE ").append(whereFilterSupplier.get());
        } else {
            query.append(createFromClause(campaign)).append(" WHERE ").append(whereFilterSupplier.get()).append(resultAliasWithDot);
        }
        query.append(campaign.getColResultCompleteDate()).append(" IS NULL");

        JSONObject result = dbEngine.executeQuery(query.toString(),
                        campaign.getId(),
                        null,
                        campaign.getDbConnectionString(),
                        campaign.getDbUsername(),
                        enCipherUtils.decryptPassword(campaign.getDbPassword()))
                .getJSONObject(0);
        return Integer.parseInt(result.get("").toString());
    }

    /**
     * Set telephony result code for rows that filtered by dnc list
     *
     * @param campaign
     * @param calls
     */
    @EscapeSqlNames
    public void setFilteredByDncListStatus(Campaign campaign, Collection<Call> calls) throws SQLException {
        StringBuilder query = new StringBuilder()
                .append("UPDATE ").append(campaign.getDbResultTable()).append(" SET ")
                .append(campaign.getColResultCompleteDate()).append(" = '").append(Utils.convertDateToDouble(new Date())).append("', ")
                .append(campaign.getColResultTelephonyRc()).append(" = ").append(Constants.TELEPHONY_RC_ID_FILTERED_BY_DNC).append(" , ")
                .append(DISABLED.getValue()).append(" = ").append(Constants.DISABLED);

        // DNC filtered calls never reach a user — nullify user_id unconditionally
        if (campaign.getColResultUserId() != null) {
            query.append(", ").append(campaign.getColResultUserId()).append(" = NULL");
        }

        if (campaign.getColResultUserRc() != null) {
            query.append(", ").append(campaign.getColResultUserRc()).append(" = NULL");
        }

        query.append(" WHERE ").append(columnToCheckRowExistence(campaign))
                .append(" IN (").append(getUniqueKeysOfCallsAsString(calls)).append(")");
        executeUpdate(query.toString(), campaign);
    }

    /**
     * Verilen customer_no'ya ait satırları (source LEFT JOIN result) çeker.
     * Müşteri bazlı DNC genişletmesinde telefon kolon değerlerini toplamak
     * için kullanılır; customer_no parametrized gönderilir.
     */
    @EscapeSqlNames
    public JSONArray fetchRowsByCustomerNo(Campaign campaign, String customerNo) throws SQLException {
        StringBuilder query = createSelectAllQuery(campaign);
        query.append(" WHERE ").append(getResultAliasWithDot(campaign)).append(campaign.getColResultCustomerNo()).append(" = ?");
        return dbEngine.executeParametrizedQuery(new ParametrizedQuery(query.toString(), Collections.singletonList(customerNo)),
                campaign.getId(),
                Integer.MAX_VALUE,
                campaign.getDbConnectionString(),
                campaign.getDbUsername(),
                enCipherUtils.decryptPassword(campaign.getDbPassword()));
    }

    /**
     * Delta-sync skip probe + pencere üst sınırı: watermark'tan yeni en büyük
     * date_entered değerini döndürür; yeni satır yoksa null döner (ucuz skip).
     * Watermark null ise (ilk koşu) koşulsuz MAX alınır. Değerler kampanya
     * DB'sinden geldiği biçimiyle string taşınır (tip dönüşümü yapılmaz).
     */
    @EscapeSqlNames
    public String findMaxDateEnteredAfter(Campaign campaign, String watermark) throws SQLException {
        StringBuilder query = new StringBuilder("SELECT MAX(")
                .append(SOURCE_TABLE_ALIAS_DOT).append(campaign.getColDateEntered())
                .append(") AS \"PD_MAX_DATE_ENTERED\" FROM ").append(createFromClause(campaign));
        List<Object> params = new ArrayList<>();
        if (watermark != null) {
            query.append(" WHERE ").append(SOURCE_TABLE_ALIAS_DOT).append(campaign.getColDateEntered()).append(" > ?");
            params.add(watermark);
        }
        JSONArray result = dbEngine.executeParametrizedQuery(new ParametrizedQuery(query.toString(), params),
                campaign.getId(),
                Integer.MAX_VALUE,
                campaign.getDbConnectionString(),
                campaign.getDbUsername(),
                enCipherUtils.decryptPassword(campaign.getDbPassword()));
        if (result == null || result.length() == 0) {
            return null;
        }
        JSONObject row = result.optJSONObject(0);
        if (row == null || !row.has("PD_MAX_DATE_ENTERED") || row.isNull("PD_MAX_DATE_ENTERED")) {
            return null;
        }
        String maxDateEntered = row.get("PD_MAX_DATE_ENTERED").toString().trim();
        return maxDateEntered.isEmpty() ? null : maxDateEntered;
    }

    /**
     * Delta-sync penceresi: (fromWatermark, toWatermark] aralığına giren yeni
     * satırları unique key keyset sayfalamasıyla çeker. Kampanya DB'si PD
     * DB'sinden ayrı olabilir; bu sorgu pd_dnc_pn'e JOIN YAPMAZ — DNC kontrolü
     * PD tarafında ayrıca yapılır.
     */
    @EscapeSqlNames
    public JSONArray fetchRowsEnteredAfter(Campaign campaign, String fromWatermark, String toWatermark,
                                           String lastUniqueKey, int batchSize) throws SQLException {
        StringBuilder query = createSelectAllQuery(campaign);
        List<Object> params = new ArrayList<>();
        query.append(" WHERE ");
        if (fromWatermark != null) {
            query.append(SOURCE_TABLE_ALIAS_DOT).append(campaign.getColDateEntered()).append(" > ? AND ");
            params.add(fromWatermark);
        }
        query.append(SOURCE_TABLE_ALIAS_DOT).append(campaign.getColDateEntered()).append(" <= ?");
        params.add(toWatermark);
        if (lastUniqueKey != null) {
            query.append(" AND ").append(SOURCE_TABLE_ALIAS_DOT).append(campaign.getColUniqueKey()).append(" > ?");
            params.add(lastUniqueKey);
        }
        query.append(" ORDER BY ").append(SOURCE_TABLE_ALIAS_DOT).append(campaign.getColUniqueKey());
        return dbEngine.executeParametrizedQuery(new ParametrizedQuery(query.toString(), params),
                campaign.getId(),
                batchSize,
                campaign.getDbConnectionString(),
                campaign.getDbUsername(),
                enCipherUtils.decryptPassword(campaign.getDbPassword()));
    }

    /**
     * Unique key listesi üzerinden satırları DNC filtre semantiğiyle kapatır
     * (setFilteredByDncListStatus ile aynı alanlar); yalnız henüz
     * tamamlanmamış satırlara dokunur. Key listesi 1000'lik parçalara bölünür.
     */
    @EscapeSqlNames
    public void setFilteredByDncListStatusByUniqueKeys(Campaign campaign, Collection<String> uniqueKeys) throws SQLException {
        if (uniqueKeys == null || uniqueKeys.isEmpty()) {
            return;
        }
        for (List<String> uniqueKeyChunk : partitionKeys(uniqueKeys, 1000)) {
            StringBuilder query = new StringBuilder()
                    .append("UPDATE ").append(campaign.getDbResultTable()).append(" SET ")
                    .append(campaign.getColResultCompleteDate()).append(" = '").append(Utils.convertDateToDouble(new Date())).append("', ")
                    .append(campaign.getColResultTelephonyRc()).append(" = ").append(Constants.TELEPHONY_RC_ID_FILTERED_BY_DNC).append(" , ")
                    .append(DISABLED.getValue()).append(" = ").append(Constants.DISABLED);
            if (campaign.getColResultUserId() != null) {
                query.append(", ").append(campaign.getColResultUserId()).append(" = NULL");
            }
            if (campaign.getColResultUserRc() != null) {
                query.append(", ").append(campaign.getColResultUserRc()).append(" = NULL");
            }
            query.append(" WHERE ").append(columnToCheckRowExistence(campaign))
                    .append(" IN (").append(String.join(",", uniqueKeyChunk)).append(")")
                    .append(" AND ").append(campaign.getColResultCompleteDate()).append(" IS NULL");
            executeUpdate(query.toString(), campaign);
        }
    }

    /**
     * Task 325559 — hiç aranamayacak kayıtları kapatır: kayıtta geçerli numara yok ya da tüm
     * numaraları {@code maxAttemptPerNumber} limitini doldurmuş. Kayıt originate edilmeden
     * havuzdan çıkarılır.
     *
     * <p><b>DNC kapatmasından iki önemli farkı vardır:</b></p>
     *
     * <ul>
     * <li><b>telephony_result_code YAZILMAZ</b> (K11). Yapılmamış bir çağrı için sonuç kodu
     * üretmek arama raporlarını şişirir; ayrıca kayıt daha önce gerçekten aranmış olabilir ve o
     * çağrının TRC'si korunmalıdır.</li>
     * <li><b>user_id / user_result_code'a DOKUNULMAZ.</b> DNC kapatmasında bu alanlar NULL'lanır
     * çünkü o kayıtlar hiç agent'a düşmemiştir. Burada ise kayıt geçmişte aranmış ve agent
     * disposition'ı almış olabilir; o alanları silmek URC kaybı olurdu.</li>
     * </ul>
     *
     * <p>Result satırı henüz yoksa önce {@code setFetchedDataFlagForCalls} ile aynı desende
     * eklenir; aksi halde UPDATE 0 satır etkiler ve kayıt her producer tick'inde yeniden SELECT
     * edilmeye devam ederdi.</p>
     *
     * @param campaign kampanya
     * @param uniqueKeys kapatılacak kayıtların unique key'leri
     * @param closeReason {@code Constants.CLOSE_REASON_*}; binding eşli değilse yazılmaz
     */
    @EscapeSqlNames
    public void setClosedWithoutDialing(Campaign campaign, Collection<String> uniqueKeys, String closeReason) throws SQLException {
        if (uniqueKeys == null || uniqueKeys.isEmpty()) {
            return;
        }
        for (List<String> uniqueKeyChunk : partitionKeys(uniqueKeys, 1000)) {
            String joinedKeys = String.join(",", uniqueKeyChunk);
            insertMissingResultRows(campaign, joinedKeys);

            StringBuilder query = new StringBuilder()
                    .append("UPDATE ").append(campaign.getDbResultTable()).append(" SET ")
                    .append(campaign.getColResultCompleteDate()).append(" = '").append(Utils.convertDateToDouble(new Date())).append("', ")
                    .append(DISABLED.getValue()).append(" = ").append(Constants.DISABLED);
            if (campaign.getColResultCloseReason() != null && StringUtils.isNotBlank(closeReason)) {
                query.append(", ").append(campaign.getColResultCloseReason())
                        .append(" = '").append(closeReason.replace("'", "''")).append("'");
            }
            // Task 325559: kapanan kayit BEKLEYEN ARAMA PLANI tasimamalidir. Musteri kaydi yeniden
            // aktive etmek icin complete_date/is_fetched'i bosaltirsa, gecmis tarihli retry_date
            // duran satir FRESH degil RETRY fetch'inden gecer ve yeni eklenen numara yerine eski
            // (limitli) plan denenir; ayrica CDR outdialing tipi yanlis siniflanir.
            if (campaign.getColResultRetryDate() != null) {
                query.append(", ").append(campaign.getColResultRetryDate()).append(" = NULL");
            }
            if (campaign.getColResultRetryNumber() != null) {
                query.append(", ").append(campaign.getColResultRetryNumber()).append(" = NULL");
            }
            query.append(" WHERE ").append(columnToCheckRowExistence(campaign))
                    .append(" IN (").append(joinedKeys).append(")")
                    .append(" AND ").append(campaign.getColResultCompleteDate()).append(" IS NULL");
            executeUpdate(query.toString(), campaign);
        }
    }

    /**
     * Result tablosunda karşılığı olmayan satırları key'iyle oluşturur ({@code
     * setFetchedDataFlagForCalls} ile aynı desen). Kaynak ve result tablosu aynıysa gerek yoktur.
     */
    private void insertMissingResultRows(Campaign campaign, String joinedKeys) throws SQLException {
        if (campaign.hasSameSourceAndResultTable()) {
            return;
        }
        StringBuilder insertQuery = new StringBuilder()
                .append("INSERT INTO ").append(campaign.getDbResultTable())
                .append("(").append(columnToCheckRowExistence(campaign)).append(")")
                .append("SELECT ").append(campaign.getColUniqueKey())
                .append(" FROM ").append(campaign.getDbListTable())
                .append(" WHERE ").append(campaign.getColUniqueKey())
                .append(" IN (").append(getCallIdsNotPresentResultTableQuery(campaign, joinedKeys)).append(")");
        executeUpdate(insertQuery.toString(), campaign);
    }

    private List<List<String>> partitionKeys(Collection<String> keys, int chunkSize) {
        List<String> keyList = new ArrayList<>(keys);
        List<List<String>> chunks = new ArrayList<>();
        for (int from = 0; from < keyList.size(); from += chunkSize) {
            chunks.add(keyList.subList(from, Math.min(from + chunkSize, keyList.size())));
        }
        return chunks;
    }

    /**
     * Müşteri bazlı DNC tetiklendiğinde, customer_no eşleşen ve henüz
     * tamamlanmamış sibling satırları mevcut DNC filtre semantiğiyle kapatır:
     * complete_date + telephony_result_code=76 + is_disabled=1 + user
     * alanları NULL. Tetikleyen çağrının satırı (excludeUniqueKeyValue)
     * hariç tutulur ki agent'ın gerçek result yazımı ezilmesin. customer_no
     * ve exclude key parametrized'dır.
     */
    @EscapeSqlNames
    public void setFilteredByDncListStatusByCustomerNo(Campaign campaign, String customerNo, String excludeUniqueKeyValue) throws SQLException {
        StringBuilder query = new StringBuilder()
                .append("UPDATE ").append(campaign.getDbResultTable()).append(" SET ")
                .append(campaign.getColResultCompleteDate()).append(" = '").append(Utils.convertDateToDouble(new Date())).append("', ")
                .append(campaign.getColResultTelephonyRc()).append(" = ").append(Constants.TELEPHONY_RC_ID_FILTERED_BY_DNC).append(" , ")
                .append(DISABLED.getValue()).append(" = ").append(Constants.DISABLED);

        if (campaign.getColResultUserId() != null) {
            query.append(", ").append(campaign.getColResultUserId()).append(" = NULL");
        }

        if (campaign.getColResultUserRc() != null) {
            query.append(", ").append(campaign.getColResultUserRc()).append(" = NULL");
        }

        List<Object> params = new ArrayList<>();
        query.append(" WHERE ").append(campaign.getColResultCustomerNo()).append(" = ?");
        params.add(customerNo);
        query.append(" AND ").append(campaign.getColResultCompleteDate()).append(" IS NULL");
        if (excludeUniqueKeyValue != null) {
            query.append(" AND ").append(columnToCheckRowExistence(campaign)).append(" <> ?");
            params.add(excludeUniqueKeyValue);
        }

        dbEngine.executeBatchParametrizedUpdate(new ParametrizedQuery(query.toString(), params),
                campaign.getId(),
                campaign.getDbConnectionString(),
                campaign.getDbUsername(),
                enCipherUtils.decryptPassword(campaign.getDbPassword()));
    }

    /**
     * Mark rows expired by JIT validator. Mirrors CampaignResultJobService#getSetSql:
     *   is_disabled = 1
     *   [user_result_code] = resultCodeId (if not null)
     *   [UpdatedUser]      = expireUserId (if not null)
     *   [UpdatedDate]      = GETDATE()
     * telephony_result_code, complete_date and user_id are NOT touched here.
     * Idempotent via AND <disabled> = 0.
     */
    @EscapeSqlNames
    public void setFilteredByExpireStatus(Campaign campaign, Collection<Call> calls,
                                          Long resultCodeId, Long expireUserId) throws SQLException {
        if (calls == null || calls.isEmpty()) return;

        StringBuilder query = new StringBuilder()
                .append("UPDATE ").append(campaign.getDbResultTable()).append(" SET ")
                .append(DISABLED.getValue()).append(" = ").append(Constants.DISABLED);

        if (resultCodeId != null) {
            query.append(", [user_result_code] = ").append(resultCodeId);
        }
        if (expireUserId != null) {
            query.append(", [UpdatedUser] = ").append(expireUserId);
        }
        query.append(", [UpdatedDate] = GETDATE()");

        query.append(" WHERE ").append(columnToCheckRowExistence(campaign))
                .append(" IN (").append(getUniqueKeysOfCallsAsString(calls)).append(")")
                .append(" AND ").append(DISABLED.getValue()).append(" = 0");
        executeUpdate(query.toString(), campaign);
    }

    /**
     * Removes fetchedDataFlag from rows. Used on start of stopped campaign if
     * {@code Сampaign.startupDbCheck} is enabled.
     *
     * @param campaign
     * @throws SQLException
     */
    @EscapeSqlNames
    public void removeFetchedDataFlagFromUnfinishedCalls(Campaign campaign) throws SQLException {
        removeFetchedDataFlagFromUnfinishedCalls(campaign, null);
    }

    /**
     * FIX-333599: {@code fetchedBefore} verilirse yalnız o andan ÖNCE yapılmış rezervasyonlar
     * temizlenir. Cleanup kampanya filtresi taşımadığı için tablo geneline uygulanır; aynı result
     * tablosunu kullanan canlı kardeş kampanyanın rezervasyonları ({@code is_fetched >= kardeşin
     * canlıya geçiş anı}) böylece korunur. Rezervasyonla aynı literal kuralı kullanılır
     * ({@link #fetchedFlagLiteral}); {@code null} iken SQL bugünküyle birebir aynıdır.
     */
    @EscapeSqlNames
    public void removeFetchedDataFlagFromUnfinishedCalls(Campaign campaign, Date fetchedBefore) throws SQLException {
        String query = "UPDATE " + campaign.getDbResultTable() + " SET " + campaign.getColResultFetchedDataFlag() + " = NULL WHERE "
                + campaign.getColResultCompleteDate() + " IS NULL AND " + campaign.getColResultFetchedDataFlag() + " IS NOT NULL AND ("
                + campaign.getColResultStartDate() + " IS NULL OR "
                + campaign.getColResultStartDate() + " < " + campaign.getColResultRetryDate() + " OR "
                + campaign.getColResultStartDate() + " < " + campaign.getColResultCallbackDate() + ")";
        if (fetchedBefore != null) {
            DB_TYPE dbType = DB_TYPE.getByConnectionString(campaign.getDbConnectionString());
            query += " AND " + campaign.getColResultFetchedDataFlag() + " < "
                    + fetchedFlagLiteral(dbType, Utils.convertDateToDouble(fetchedBefore));
        }
        executeUpdate(query, campaign);
    }

    /**
     * Remove fetchedDataFlag from rows
     *
     * @param campaign
     * @param calls
     * @throws SQLException
     */
    @EscapeSqlNames
    public void removeFetchedDataFlagFromCalls(Campaign campaign, List<Call> calls) throws SQLException {
        if (calls.size() > 0) {
            String query = "UPDATE " + campaign.getDbResultTable() +
                    " SET " + campaign.getColResultFetchedDataFlag() + " = NULL" +
                    " WHERE " + columnToCheckRowExistence(campaign) +
                    " IN (" + getUniqueKeysOfCallsAsString(calls) + ")";
            executeUpdate(query, campaign);
        }
    }

    /**
     * Remove fetchedDataFlag from rows in asynchronous way with retry mechanism
     *
     * @param campaign
     * @param calls
     */
    @EscapeSqlNames
    public CompletableFuture<Void> removeFetchedDataFlagFromCallsAsync(Campaign campaign, List<Call> calls) {
        return CompletableFuture.runAsync(() -> {
            int maxRetries = 3;
            int attempt = 0;
            SQLException lastException = null;

            while (attempt < maxRetries) {
                try {
                    removeFetchedDataFlagFromCalls(campaign, calls);
                    return;

                } catch (SQLException e) {
                    lastException = e;


                    if (e.getMessage() != null && e.getMessage().contains("was deadlocked")) {
                        attempt++;

                        if (attempt < maxRetries) {
                            // Exponential backoff: 50ms, 100ms, 200ms
                            long waitTime = 50L * (1L << (attempt - 1));

                            log.warn("Deadlock detected for campaign {} (batch size: {}), retry {}/{} after {}ms",
                                    campaign.getName(),
                                    calls.size(),
                                    attempt,
                                    maxRetries,
                                    waitTime);

                            try {
                                Thread.sleep(waitTime);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                throw new RuntimeException("Retry interrupted", ie);
                            }
                        }
                    } else {
                        throw new RuntimeException("Non-deadlock SQL error", e);
                    }
                }
            }

            throw new RuntimeException(
                    String.format("Failed after %d deadlock retries for campaign %s (batch size: %d)",
                            maxRetries,
                            campaign.getName(),
                            calls.size()),
                    lastException
            );
        });
    }

    /**
     * Appointer'a ulaşılamayan callback'i (task 321997 ATTEMPT_COUNT) tek atomik UPDATE ile yeniden
     * zamanlar veya limitte campaign-wide'a çevirir. Karar ve sayım sunucu tarafında (SQL CASE) yapılır;
     * optimistic guard (callback_attempts = expectedOld) iki-node lost-update'i idempotent kılar.
     *
     * Sahip o an AVAILABLE değilse (online-meşgul VEYA offline farketmez) her çağrıda callback_attempts+1
     * ve callback_date=now+delay (pencere snap). Eşik karşılaştırması ESKİ sayaç değeriyle yapılır
     * (callback_attempts >= agentCbConvertAfterAttempts): sahip tam agentCbConvertAfterAttempts kez denenir ve
     * sayaç eşiğe (ör. 4) GÖRÜNÜR biçimde ulaşır; ANCAK ondan SONRAKİ tetiklemede user_id=NULL +
     * callback_date=now + callback_attempts=0 ile havuza devredilir ("convert AFTER N attempts").
     * Her durumda is_fetched=NULL → satır sonraki uygun zamanda tekrar fetch edilir.
     *
     * col_result_callback_attempts veya eşik tanımlı değilse sayım yapılamaz; yalnız reschedule yapılır
     * (churn durur), convert yapılmaz. callback_type'a hiç dokunulmaz (CallProducer valueOf hassasiyeti).
     */
    @EscapeSqlNames
    public void rescheduleAppointedCallback(Campaign campaign, Call call) throws SQLException {
        String uniqueKeyIn = getUniqueKeysOfCallsAsString(java.util.Collections.singletonList(call));
        String cbDateCol = campaign.getColResultCallbackDate();
        String fetchedCol = campaign.getColResultFetchedDataFlag();
        String userIdCol = campaign.getColResultUserId();
        String attemptsCol = campaign.getColResultCallbackAttempts();
        Integer threshold = campaign.getAgentCbConvertAfterAttempts();

        long now = System.currentTimeMillis();
        int delayMin = campaign.getAgentCbRescheduleDelayMin() != null ? campaign.getAgentCbRescheduleDelayMin() : 0;
        double rescheduleDate = call.computeRescheduledCallbackDate(now + delayMin * 60_000L);
        double convertDate = call.computeRescheduledCallbackDate(now);

        boolean canCount = attemptsCol != null && threshold != null && threshold > 0;

        StringBuilder query = new StringBuilder("UPDATE ").append(campaign.getDbResultTable()).append(" SET ");

        if (!canCount) {
            // Eşik/sayaç kolonu tanımlı değil: sayaç anlamsız → yalnız reschedule (convert yok).
            query.append(cbDateCol).append(" = ").append(rescheduleDate);
            query.append(", ").append(fetchedCol).append(" = NULL");
            query.append(" WHERE ").append(columnToCheckRowExistence(campaign)).append(" IN (").append(uniqueKeyIn).append(")");
        } else {
            // Sahip o an AVAILABLE değil (online-meşgul VEYA offline farketmez): deneme sayacı +1 ve reschedule;
            // sayaç eşiğe (agentCbConvertAfterAttempts) ulaşınca callback campaign-wide'a (user_id=NULL,
            // callback_date=now) devredilir ve sayaç sıfırlanır. Karar/sayım sunucu tarafında (CASE);
            // expectedOld guard iki-node lost-update'i idempotent kılar.
            int expectedOld = call.getCallbackAttempts();
            // callback_attempts kolonu NULL olabilir (yeni eklenen kolon; callback'i kuran disposition 0'a set etmez,
            // eski satırlar da NULL). Çıplak kolon kullanılırsa NULL + 1 = NULL olur ve "= expectedOld" optimistic
            // guard'ı NULL satırı eşleştirmediği için UPDATE 0 satır eder; callback is_fetched dolu kalır, bir daha
            // çekilmez/dial edilmez/havuza düşmez (sonsuza dek takılır). COALESCE(col,0) hem aritmetiği hem guard'ı
            // NULL'ı 0 kabul edecek şekilde kilitler: ilk reschedule'da NULL -> 1 olur ve akış normal ilerler.
            String attemptsExpr = "COALESCE(" + attemptsCol + ", 0)";
            // "convert AFTER N attempts": eşik karşılaştırması ESKİ sayaç değeriyle yapılır (>= threshold), "+1" ile
            // DEĞİL. Böylece sahip tam agentCbConvertAfterAttempts kez denenip sayaç eşiğe (ör. 4) görünür biçimde
            // ulaşır (1,2,3,4); ELSE dalında sayaç +1 artar, THEN dalında (eski sayaç >= eşik) havuza devredilip
            // 0'a sıfırlanır. "+1 >= N" karşılaştırması sayacı N-1'de convert edip N'e hiç ulaştırmıyordu
            // (saha gözlemi: "sanki 4 değil 3 kere deniyor").
            String cond = attemptsExpr + " >= " + threshold;
            query.append(attemptsCol).append(" = CASE WHEN ").append(cond).append(" THEN 0 ELSE ").append(attemptsExpr).append(" + 1 END");
            if (userIdCol != null) {
                query.append(", ").append(userIdCol).append(" = CASE WHEN ").append(cond).append(" THEN NULL ELSE ").append(userIdCol).append(" END");
            }
            query.append(", ").append(cbDateCol).append(" = CASE WHEN ").append(cond)
                 .append(" THEN ").append(convertDate).append(" ELSE ").append(rescheduleDate).append(" END");
            query.append(", ").append(fetchedCol).append(" = NULL");
            query.append(" WHERE ").append(columnToCheckRowExistence(campaign)).append(" IN (").append(uniqueKeyIn).append(")")
                 .append(" AND ").append(attemptsExpr).append(" = ").append(expectedOld);
        }
        executeUpdate(query.toString(), campaign);
    }

    /**
     * {@link #rescheduleAppointedCallback} için deadlock-aware async sarmalayıcı
     * (removeFetchedDataFlagFromCallsAsync ile aynı 3 deneme / exponential backoff semantiği).
     */
    @EscapeSqlNames
    public CompletableFuture<Void> rescheduleAppointedCallbackAsync(Campaign campaign, Call call) {
        return CompletableFuture.runAsync(() -> {
            int maxRetries = 3;
            int attempt = 0;
            SQLException lastException = null;
            while (attempt < maxRetries) {
                try {
                    rescheduleAppointedCallback(campaign, call);
                    return;
                } catch (SQLException e) {
                    lastException = e;
                    if (e.getMessage() != null && e.getMessage().contains("was deadlocked")) {
                        attempt++;
                        if (attempt < maxRetries) {
                            long waitTime = 50L * (1L << (attempt - 1));
                            log.warn("Deadlock detected rescheduling appointed callback for campaign {}, retry {}/{} after {}ms",
                                    campaign.getName(), attempt, maxRetries, waitTime);
                            try {
                                Thread.sleep(waitTime);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                throw new RuntimeException("Retry interrupted", ie);
                            }
                        }
                    } else {
                        throw new RuntimeException("Non-deadlock SQL error", e);
                    }
                }
            }
            throw new RuntimeException(
                    String.format("Failed after %d deadlock retries rescheduling appointed callback for campaign %s",
                            maxRetries, campaign.getName()),
                    lastException);
        });
    }

    /**
     * Remove fetchedDataFlag from row. Used to remove fetched flag of a row when
     * socket exception occured
     *
     * @param call
     * @throws SQLException
     */
    @EscapeSqlNames
    public void removeFetchedDataFlagFromCall(Call call) throws SQLException {
        removeFetchedDataFlagFromCalls(call.getCampaign(), Collections.singletonList(call));
    }

    /**
     * Count rows from campaign list table with filters. Status-targeting filters
     * (is_disabled, complete_date, is_fetched, attempts, callback_date, retry_date)
     * are excluded so that eliminating or completing records does not shrink the
     * campaign goal — FIX-317277 Faz 2.
     */
    @EscapeSqlNames
    public int countActualListSize(Campaign campaign) throws SQLException {
        String campaignWhereFromFilters = createWhereFromFilters(campaign, true);
        StringBuilder query = createCountSelectQueryForResultTable(campaign);

        if (campaignWhereFromFilters.length() > 0) {
            query.append(" WHERE ").append(campaignWhereFromFilters);
        }
        JSONArray result = dbEngine.executeQuery(query.toString(),
                campaign.getId(),
                null,
                campaign.getDbConnectionString(),
                campaign.getDbUsername(),
                enCipherUtils.decryptPassword(campaign.getDbPassword()));
        return result != null && result.length() > 0 ? Integer.parseInt((String) result.getJSONObject(0).get("PD_ROWS")) : 0;
    }

    @EscapeSqlNames
    public int countCallbackListSize(Campaign campaign) throws SQLException {
        String resultAliasWithDot = getResultAliasWithDot(campaign);
        StringBuilder query = createCountSelectQueryForResultTable(campaign);

        query.append(" WHERE ").append(resultAliasWithDot).append(campaign.getColResultCompleteDate()).append(" IS NULL");
        query.append(" AND ").append(resultAliasWithDot).append(campaign.getColResultCallbackDate()).append(" IS NOT NULL");

        if (campaign.isApplyFilterRetries()) {
            String campaignWhereFromFilters = createWhereFromFilters(campaign);
            query.append(campaignWhereFromFilters.length() > 0 ? " AND " + campaignWhereFromFilters : "");
        }
        JSONArray result = dbEngine.executeQuery(query.toString(),
                campaign.getId(),
                null,
                campaign.getDbConnectionString(),
                campaign.getDbUsername(),
                enCipherUtils.decryptPassword(campaign.getDbPassword()));
        return result != null && result.length() > 0 ? Integer.parseInt((String) result.getJSONObject(0).get("PD_ROWS")) : 0;
    }

    @EscapeSqlNames
    public int countFreshDataListSize(Campaign campaign) throws SQLException {
        String resultAliasWithDot = getResultAliasWithDot(campaign);
        StringBuilder query = createCountSelectQueryForResultTable(campaign);
        boolean shouldEscapeNames = shouldEscapeNames(DB_TYPE.getByConnectionString(campaign.getDbConnectionString()));

        query.append(" WHERE ").append(resultAliasWithDot).append(campaign.getColResultCompleteDate()).append(" IS NULL");
        query.append(" AND ").append(resultAliasWithDot).append(campaign.getColResultCallbackDate()).append(" IS NULL");
        query.append(" AND (").append(resultAliasWithDot).append(campaign.getColResultRetryHistory()).append(" IS NULL");
        query.append(" OR ").append(resultAliasWithDot).append(campaign.getColResultRetryHistory()).append(" = '')");
        query.append(" AND (").append(resultAliasWithDot).append(campaign.getColResultAttempts()).append(" IS NULL");
        query.append(" OR ").append(resultAliasWithDot).append(campaign.getColResultAttempts()).append(" = ''");
        query.append(" OR ").append(resultAliasWithDot).append(campaign.getColResultAttempts()).append(" = '0')");
        query.append(" AND (").append(resultAliasWithDot).append(DISABLED.getValue(shouldEscapeNames)).append(" IS NULL");
        query.append(" OR ").append(resultAliasWithDot).append(DISABLED.getValue(shouldEscapeNames)).append(" <> ").append(Constants.DISABLED).append(")");

        if (campaign.isApplyFilterRetries()) {
            String campaignWhereFromFilters = createWhereFromFilters(campaign);
            query.append(campaignWhereFromFilters.length() > 0 ? " AND " + campaignWhereFromFilters : "");
        }

        JSONArray result = dbEngine.executeQuery(query.toString(),
                campaign.getId(),
                null,
                campaign.getDbConnectionString(),
                campaign.getDbUsername(),
                enCipherUtils.decryptPassword(campaign.getDbPassword()));
        return result != null && result.length() > 0 ? Integer.parseInt((String) result.getJSONObject(0).get("PD_ROWS")) : 0;
    }

    private StringBuilder createCountSelectQueryForResultTable(Campaign campaign) {
        return new StringBuilder("SELECT COUNT(*) AS \"PD_ROWS\" FROM ").append(createFromClause(campaign));
    }

    /**
     * Count rows from campaign list table without filters
     *
     * @param campaign
     * @return total list size
     * @throws SQLException
     */
    @EscapeSqlNames
    public int countTotalListSize(Campaign campaign) throws SQLException {
        String query = "SELECT COUNT(*) AS \"PD_ROWS\" FROM " + campaign.getDbListTable();
        JSONArray result = dbEngine.executeQuery(query,
                campaign.getId(),
                null,
                campaign.getDbConnectionString(),
                campaign.getDbUsername(),
                enCipherUtils.decryptPassword(campaign.getDbPassword()));
        return result != null && result.length() > 0 ? Integer.parseInt((String) result.getJSONObject(0).get("PD_ROWS")) : 0;
    }

    /**
     * Count rows from campaign list table for not completed retries
     *
     * @param campaign
     * @return retry list size
     * @throws SQLException
     */
    @EscapeSqlNames
    public int countRetryListSize(Campaign campaign) throws SQLException {
        String resultAliasWithDot = getResultAliasWithDot(campaign);
        StringBuilder query = createCountSelectQueryForResultTable(campaign);

        query.append(" WHERE ").append(resultAliasWithDot).append(campaign.getColResultCompleteDate()).append(" IS NULL");
        query.append(" AND ").append(resultAliasWithDot).append(campaign.getColResultRetryDate()).append(" IS NOT NULL");

        if (campaign.isApplyFilterRetries()) {
            String campaignWhereFromFilters = createWhereFromFilters(campaign);
            query.append(campaignWhereFromFilters.length() > 0 ? " AND " + campaignWhereFromFilters : "");
        }

        JSONArray result = dbEngine.executeQuery(query.toString(),
                campaign.getId(),
                null,
                campaign.getDbConnectionString(),
                campaign.getDbUsername(),
                enCipherUtils.decryptPassword(campaign.getDbPassword()));
        return result != null && result.length() > 0 ? Integer.parseInt((String) result.getJSONObject(0).get("PD_ROWS")) : 0;
    }

    /**
     * Count rows from campaign result table that are no longer dialable.
     * "Completed" covers finished calls plus DNC/eliminated rows, rows currently
     * fetched by an agent, and (when applicable) rows whose attempts have hit the
     * overall retry limit. Used by completion percent so eliminated and limit-reached
     * rows stop inflating the remaining-work counter (FIX-317277).
     *
     * Status-targeting user filters (is_disabled=0 etc.) are stripped from the WHERE
     * clause: otherwise the {@code is_disabled=1} branch of the completion predicate
     * would be silently contradicted by the same {@code is_disabled=0} user filter
     * and eliminated rows would never be counted as completed — the exact bug the
     * Faz 2 denominator fix set out to solve, kept symmetric here (FIX-317277 Faz 2).
     */
    @EscapeSqlNames
    public int countCompletedCalls(Campaign campaign) throws SQLException {
        String resultAliasWithDot = getResultAliasWithDot(campaign);
        String campaignWhereFromFilters = createWhereFromFilters(campaign, true);
        // DISABLED enum is referenced statically, so the @EscapeSqlNames Campaign proxy
        // does not wrap it; escape manually for Oracle/PostgreSQL case-sensitive identifiers.
        boolean shouldEscapeNames = shouldEscapeNames(DB_TYPE.getByConnectionString(campaign.getDbConnectionString()));

        StringBuilder completionPredicate = new StringBuilder();
        completionPredicate.append(resultAliasWithDot).append(campaign.getColResultCompleteDate()).append(" IS NOT NULL");
        completionPredicate.append(" OR ").append(resultAliasWithDot).append(DISABLED.getValue(shouldEscapeNames)).append(" = ").append(Constants.DISABLED);
        completionPredicate.append(" OR ").append(resultAliasWithDot).append(campaign.getColResultFetchedDataFlag()).append(" IS NOT NULL");
        if (Boolean.TRUE.equals(campaign.getCountAllCallTypes())
                && campaign.getRetryOveralAttempt() != null
                && campaign.getRetryOveralAttempt() > 0) {
            completionPredicate.append(" OR ").append(resultAliasWithDot).append(campaign.getColResultAttempts())
                    .append(" >= ").append(campaign.getRetryOveralAttempt());
        }

        StringBuilder query = new StringBuilder("SELECT COUNT(*) AS \"PD_ROWS\" FROM ").append(createFromClause(campaign))
                .append(" WHERE (").append(completionPredicate).append(")");

        query.append((campaignWhereFromFilters.length() > 0 ? " AND " + campaignWhereFromFilters : ""));
        JSONArray result = dbEngine.executeQuery(query.toString(),
                campaign.getId(),
                null,
                campaign.getDbConnectionString(),
                campaign.getDbUsername(),
                enCipherUtils.decryptPassword(campaign.getDbPassword()));
        return result != null && result.length() > 0 ? Integer.parseInt((String) result.getJSONObject(0).get("PD_ROWS")) : 0;
    }

    /**
     * Insert call result to db, check if row by call unique key already exists
     * and update/insert row
     *
     * @param call
     */
    @EscapeSqlNames
    public void insertCallResultToDB(Call call) {
        try {
            if (rowExistsInResultTableByCallUniqueKey(call)) {
                updateCampaignListTableRow(call);
            } else {
                insertIntoCampaignResultTable(call);
            }
        } catch (Exception e) {
            LOG.error(e, e, LOG_MODULE.DB);
        }
    }

    /**
     * Checking whether row by uniqueKey already exists in db result table
     *
     * @param call
     * @return true if row already exists in db
     * @throws JSONException
     * @throws SQLException
     */
    private boolean rowExistsInResultTableByCallUniqueKey(Call call) throws JSONException, SQLException {
        Campaign campaign = call.getCampaign();
        String query = "SELECT COUNT(*) AS \"PD_ROWS\" FROM " + campaign.getDbResultTable() + " WHERE "
                + columnToCheckRowExistence(campaign) + " = " + call.getUniqueKey();
        JSONObject result = dbEngine.executeQuery(query,
                campaign.getId(),
                null,
                campaign.getDbConnectionString(),
                campaign.getDbUsername(),
                enCipherUtils.decryptPassword(campaign.getDbPassword())).getJSONObject(0);
        int rowsFound = Integer.parseInt((String) result.get("PD_ROWS"));
        return rowsFound > 0;
    }

    /**
     * Update specific column of call row
     *
     * @param call
     * @param fieldToUpdate
     * @param newValue
     */
    @EscapeSqlNames
    public void updateCallField(Call call, String fieldToUpdate, String newValue) throws SQLException {
        StringBuilder query = new StringBuilder("UPDATE ");
        Campaign campaign = call.getCampaign();
        DB_TYPE dbType = DB_TYPE.getByConnectionString(campaign.getDbConnectionString());
        query.append(campaign.getDbListTable()).append(" SET ");
        if (shouldEscapeNames(dbType)) {
            query.append("\"").append(fieldToUpdate).append("\" = '");
        } else {
            query.append(fieldToUpdate).append(" = '");
        }
        query.append(newValue).append("'");
        query.append(" WHERE ").append(campaign.getColUniqueKey()).append(" = ").append(call.getUniqueKey());
        dbEngine.executeUpdate(query.toString(),
                campaign.getId(),
                campaign.getDbConnectionString(),
                campaign.getDbUsername(),
                enCipherUtils.decryptPassword(campaign.getDbPassword()));
    }

    /**
     * Update campaign list table row
     *
     * @param call
     * @throws SQLException
     */
    private void updateCampaignListTableRow(Call call) throws SQLException {
        Campaign campaign = call.getCampaign();
        ensureCallProcessHistoryForResultWrite(campaign, call);
        StringBuilder query = new StringBuilder();
        query.append("UPDATE ").append(campaign.getDbResultTable()).append(" SET ");

        if (campaign.getColResultAnswerDate() != null && call.getAnswerDate() != null)
            query.append(campaign.getColResultAnswerDate()).append(" = ").append(call.getAnswerDate()).append(", ");

        if (campaign.getColResultCallbackDate() != null) {
            if (call.getCallbackDate() != null) {
                query.append(campaign.getColResultCallbackDate()).append(" = ").append(call.getCallbackDate()).append(", ");
            } else {
                query.append(campaign.getColResultCallbackDate()).append(" = NULL, ");
            }
        }

        if (call.getCustomerName() != null)
            query.append(CUSTOMER_NAME.getValue()).append(" = '").append(call.getCustomerName()).append("', ");

        if (call.getCustomerSurname() != null)
            query.append(CUSTOMER_SURNAME.getValue()).append(" = '").append(call.getCustomerSurname()).append("', ");

        if (call.getRelation() != null)
            query.append(RELATION.getValue()).append(" = '").append(call.getRelation()).append("', ");

        if (call.getRelationType() != null)
            query.append(RELATION_TYPE.getValue()).append(" = '").append(call.getRelationType()).append("', ");

        if (call.getUser()!= null && call.getUser().getGroup()!=null && call.getUser().getGroup().getId()!=null)
            query.append(CALLED_GROUP_ID.getValue()).append(" = '").append(call.getUser().getGroup().getId()).append("', ");



        if (campaign.getColResultCallbackNumber() != null) {
            if (campaign.getColResultCallbackDate() != null && call.getCallbackDate() == null) {
                query.append(campaign.getColResultCallbackNumber()).append(" = NULL, ");
            } else if (call.getCallbackNumber() != null) {
                query.append(campaign.getColResultCallbackNumber()).append(" = '").append(call.getCallbackNumber()).append("', ");
            }
        }

        if (campaign.getColResultCalledNumber() != null && call.getCalledNumber() != null)
            query.append(campaign.getColResultCalledNumber()).append(" = '").append(call.getCalledNumber()).append("', ");

        if (campaign.getColResultCompleteDate() != null && call.getCompleteDate() != null) {
            query.append(campaign.getColResultCompleteDate()).append(" = ").append(call.getCompleteDate()).append(", ");
            query.append(DISABLED.getValue()).append(" = ").append(Constants.DISABLED).append(", ");
        }
        if (campaign.getColResultRetryDate() != null) {
            if (call.getRetryDate() != null) {
                query.append(campaign.getColResultRetryDate()).append(" = '").append(call.getRetryDate()).append("', ");
            } else {
                query.append(campaign.getColResultRetryDate()).append(" = NULL, ");
            }
        }

        if (call.getCallbackDate() != null || call.getRetryDate() != null) {
            query.append(campaign.getColResultFetchedDataFlag()).append(" = NULL, ");
        }

        if (campaign.getColResultRetryNumber() != null && call.getRetryNumber() != null)
            query.append(campaign.getColResultRetryNumber()).append(" = '").append(call.getRetryNumber()).append("', ");

        if (campaign.getColResultStartDate() != null && call.getStartDate() != null)
            query.append(campaign.getColResultStartDate()).append(" = ").append(call.getStartDate()).append(", ");

        if (campaign.getColResultTelephonyRc() != null && call.getTelephonyResultCode() != null)
            query.append(campaign.getColResultTelephonyRc()).append(" = '").append(call.getTelephonyResultCode().getId()).append("', ");

        if (!campaign.hasSameSourceAndResultTable() && campaign.getColResultUniqueKey() != null && call.getUniqueKey() != null)
            query.append(campaign.getColResultUniqueKey()).append(" = '").append(call.getUniqueKey()).append("', ");

        // Populate user_id only for successful calls (telephony_result_code 10 or 24)
        if (campaign.getColResultUserId() != null) {
            if (call.getUser() != null && UserPopulationEvaluator.shouldPopulateCampUserId(call)) {
                query.append(campaign.getColResultUserId()).append(" = '").append(call.getUser().getId()).append("', ");
            } else {
                query.append(campaign.getColResultUserId()).append(" = NULL, ");
            }
        }

        if (campaign.getColResultUserRc() != null) {
            if (call.getUserResultCode() != null && isUserResultCodeSelected(call)
                    && UserPopulationEvaluator.shouldPopulateCampUserResultCode(call)) {
                query.append(campaign.getColResultUserRc()).append(" = '").append(call.getUserResultCode().getId()).append("', ");
            } else {
                query.append(campaign.getColResultUserRc()).append(" = NULL, ");
            }
        }
        if (campaign.getColResultRcSequence() != null && call.getResultCodeSequence() != null)
            query.append(campaign.getColResultRcSequence()).append(" = '").append(call.getResultCodeSequence()).append("', ");

        if (campaign.getColResultDNCNumbers() != null) {
            if (!CollectionUtils.isEmpty(call.getDNCNumbers())) {
                query.append(campaign.getColResultDNCNumbers()).append(" = '").append(String.join(Constants.COL_RESULT_NUMBER_SEPERATOR, call.getDNCNumbers())).append("', ");
            } else if (call.getRawData() != null && call.getRawData().has(campaign.getColResultDNCNumbers())) {
                query.append(campaign.getColResultDNCNumbers()).append(" = NULL, ");
            }
        }

        if (campaign.getColResultAttempts() != null)
            query.append(campaign.getColResultAttempts()).append(" = '").append(call.getAttempts()).append("', ");

        if (campaign.getColResultUpdateDate() != null)
            query.append(campaign.getColResultUpdateDate()).append(" = CURRENT_TIMESTAMP, ");

        if (campaign.getColResultRetryHistory() != null && !CollectionUtils.isEmpty(call.getRetryHistory()))
            query.append(campaign.getColResultRetryHistory()).append(" = '").append(String.join(Constants.COL_RESULT_NUMBER_SEPERATOR, call.getRetryHistory())).append("', ");

        if (StringUtils.isNotBlank(campaign.getColResultCbHistory()) && !CollectionUtils.isEmpty(call.getCbHistory()))
            query.append(campaign.getColResultCbHistory()).append(" = '").append(String.join(",", call.getCbHistory())).append("', ");

        if (StringUtils.isNotBlank(campaign.getColResultCallProcess()) && !CollectionUtils.isEmpty(call.getCallProcessHistory()))
            query.append(campaign.getColResultCallProcess()).append(" = '").append(String.join(Constants.COL_RESULT_NUMBER_SEPERATOR, call.getCallProcessHistory())).append("', ");

        if (call.getCallbackType() != null)
            query.append(CALLBACK_TYPE.getValue()).append(" = '").append(call.getCallbackType()).append("', ");

        if (campaign.getColResultResponseValues() != null && StringUtils.isNotEmpty(call.getLscResponseValues()))
            query.append(campaign.getColResultResponseValues()).append(" = '").append(call.getLscResponseValues().replace("'", "''")).append("', ");

        // Task 325559: numara basina limit kaynakli kapanisin nedeni. Yazim KOSULLUDUR - yalniz
        // binding eslendiginde VE gercekten bir kapanis nedeni uretildiginde SET listesine girer.
        // Kolonu bulunmayan kampanyalarin result yazimi bu sayede etkilenmez (bkz. FEAT-318332
        // vakasi: eksik kolona yazim tum UPDATE'i dusuruyordu). Basarili kapanista neden set
        // edilmedigi icin bu kolon da yazilmaz.
        if (campaign.getColResultCloseReason() != null && StringUtils.isNotBlank(call.getCloseReason()))
            query.append(campaign.getColResultCloseReason()).append(" = '").append(call.getCloseReason().replace("'", "''")).append("', ");

        query.append(" WHERE ").append(columnToCheckRowExistence(campaign)).append(" = ").append(call.getUniqueKey());
        dbEngine.executeUpdate(query.toString().replaceAll(",([^,]*)$", "$1"),
                campaign.getId(),
                campaign.getDbConnectionString(),
                campaign.getDbUsername(),
                enCipherUtils.decryptPassword(campaign.getDbPassword()));
    }

    /**
     * Insert new row to campaign result table
     *
     * @param call
     * @throws SQLException
     */
    private void insertIntoCampaignResultTable(Call call) throws SQLException {
        Campaign campaign = call.getCampaign();
        ensureCallProcessHistoryForResultWrite(campaign, call);
        StringBuilder query = new StringBuilder();
        query.append("INSERT INTO ").append(campaign.getDbResultTable()).append(" (");

        if (campaign.getColResultAnswerDate() != null && call.getAnswerDate() != null)
            query.append(campaign.getColResultAnswerDate()).append(", ");

        if (campaign.getColResultCallbackDate() != null)
            query.append(campaign.getColResultCallbackDate()).append(", ");

        if (campaign.getColResultCallbackNumber() != null
                && (campaign.getColResultCallbackDate() == null || call.getCallbackDate() != null)
                && call.getCallbackNumber() != null)
            query.append(campaign.getColResultCallbackNumber()).append(", ");

        if (call.getCustomerName() != null)
            query.append(CUSTOMER_NAME.getValue()).append(", ");

        if (call.getCustomerSurname() != null)
            query.append(CUSTOMER_SURNAME.getValue()).append(", ");

        if (call.getRelation() != null)
            query.append(RELATION.getValue()).append(", ");

        if (call.getRelationType() != null)
            query.append(RELATION_TYPE.getValue()).append(", ");

        if (call.getUser() != null && call.getUser().getGroup() != null && call.getUser().getGroup().getId() != null)
            query.append(CALLED_GROUP_ID.getValue()).append(", ");

        if (campaign.getColResultCalledNumber() != null && call.getCalledNumber() != null)
            query.append(campaign.getColResultCalledNumber()).append(", ");

        if (campaign.getColResultCompleteDate() != null && call.getCompleteDate() != null) {
            query.append(campaign.getColResultCompleteDate()).append(", ");
            query.append(DISABLED.getValue()).append(", ");
        }

        if (campaign.getColResultRetryDate() != null && call.getRetryDate() != null) {
            query.append(campaign.getColResultRetryDate()).append(", ");
            query.append(campaign.getColResultFetchedDataFlag()).append(", ");
        }

        if (campaign.getColResultRetryNumber() != null && call.getRetryNumber() != null)
            query.append(campaign.getColResultRetryNumber()).append(", ");

        if (campaign.getColResultStartDate() != null && call.getStartDate() != null)
            query.append(campaign.getColResultStartDate()).append(", ");

        if (campaign.getColResultTelephonyRc() != null && call.getTelephonyResultCode() != null)
            query.append(campaign.getColResultTelephonyRc()).append(", ");

        if (campaign.getColResultUniqueKey() != null && call.getUniqueKey() != null)
            query.append(campaign.getColResultUniqueKey()).append(", ");

        // Populate user_id only for successful calls (telephony_result_code 10 or 24)
        if (campaign.getColResultUserId() != null && call.getUser() != null && UserPopulationEvaluator.shouldPopulateCampUserId(call))
            query.append(campaign.getColResultUserId()).append(", ");

        if (campaign.getColResultUserRc() != null && call.getUserResultCode() != null && isUserResultCodeSelected(call)
                && UserPopulationEvaluator.shouldPopulateCampUserResultCode(call))
            query.append(campaign.getColResultUserRc()).append(", ");

        if (campaign.getColResultRcSequence() != null && call.getResultCodeSequence() != null)
            query.append(campaign.getColResultRcSequence()).append(", ");

        if (campaign.getColResultDNCNumbers() != null && !CollectionUtils.isEmpty(call.getDNCNumbers()))
            query.append(campaign.getColResultDNCNumbers()).append(", ");

        if (campaign.getColResultRetryHistory() != null && !CollectionUtils.isEmpty(call.getRetryHistory()))
            query.append(campaign.getColResultRetryHistory()).append(", ");

        if (StringUtils.isNotBlank(campaign.getColResultCbHistory()) && !CollectionUtils.isEmpty(call.getCbHistory()))
            query.append(campaign.getColResultCbHistory()).append(", ");

        if (StringUtils.isNotBlank(campaign.getColResultCallProcess()) && !CollectionUtils.isEmpty(call.getCallProcessHistory()))
            query.append(campaign.getColResultCallProcess()).append(", ");

        if (campaign.getColResultAttempts() != null)
            query.append(campaign.getColResultAttempts()).append(", ");

        if (call.getCallbackType() != null)
            query.append(CALLBACK_TYPE.getValue()).append(", ");

        if (campaign.getColResultResponseValues() != null && StringUtils.isNotEmpty(call.getLscResponseValues()))
            query.append(campaign.getColResultResponseValues());

        query.append(") VALUES (");

        if (campaign.getColResultAnswerDate() != null && call.getAnswerDate() != null)
            query.append(call.getAnswerDate()).append(", ");

        if (campaign.getColResultCallbackDate() != null)
            if (call.getCallbackDate() != null)
                query.append(call.getCallbackDate()).append(", ");
            else
                query.append("NULL, ");

        if (campaign.getColResultCallbackNumber() != null
                && (campaign.getColResultCallbackDate() == null || call.getCallbackDate() != null)
                && call.getCallbackNumber() != null)
            query.append("'").append(call.getCallbackNumber()).append("', ");

        if (call.getCustomerName() != null)
            query.append("'").append(call.getCustomerName()).append("', ");

        if (call.getCustomerSurname() != null)
            query.append("'").append(call.getCustomerSurname()).append("', ");

        if (call.getRelation() != null)
            query.append("'").append(call.getRelation()).append("', ");

        if (call.getRelationType() != null)
            query.append("'").append(call.getRelationType()).append("', ");

        if (call.getUser() != null && call.getUser().getGroup() != null && call.getUser().getGroup().getId() != null)
            query.append("'").append(call.getUser().getGroup().getId()).append("', ");

        if (campaign.getColResultCalledNumber() != null && call.getCalledNumber() != null)
            query.append("'").append(call.getCalledNumber()).append("', ");

        if (campaign.getColResultCompleteDate() != null && call.getCompleteDate() != null) {
            query.append(call.getCompleteDate()).append(", ");
            query.append(Constants.DISABLED).append(", ");
        }

        if (campaign.getColResultRetryDate() != null && call.getRetryDate() != null) {
            query.append(call.getRetryDate()).append(", ");
            query.append("NULL, ");
        }

        if (campaign.getColResultRetryNumber() != null && call.getRetryNumber() != null)
            query.append("'").append(call.getRetryNumber()).append("', ");

        if (campaign.getColResultStartDate() != null && call.getStartDate() != null)
            query.append(call.getStartDate()).append(", ");

        if (campaign.getColResultTelephonyRc() != null && call.getTelephonyResultCode() != null)
            query.append("'").append(call.getTelephonyResultCode().getId()).append("', ");

        if (campaign.getColResultUniqueKey() != null && call.getUniqueKey() != null)
            query.append("'").append(call.getUniqueKey()).append("', ");

        // Populate user_id only for successful calls (telephony_result_code 10 or 24)
        if (campaign.getColResultUserId() != null && call.getUser() != null && UserPopulationEvaluator.shouldPopulateCampUserId(call))
            query.append("'").append(call.getUser().getId()).append("', ");

        if (campaign.getColResultUserRc() != null && call.getUserResultCode() != null && isUserResultCodeSelected(call))
            query.append("'").append(call.getUserResultCode().getId()).append("', ");

        if (campaign.getColResultRcSequence() != null && call.getResultCodeSequence() != null)
            query.append("'").append(call.getResultCodeSequence()).append("', ");

        if (campaign.getColResultDNCNumbers() != null && !CollectionUtils.isEmpty(call.getDNCNumbers()))
            query.append("'").append(String.join(Constants.COL_RESULT_NUMBER_SEPERATOR, call.getDNCNumbers())).append("', ");

        if (campaign.getColResultRetryHistory() != null && !CollectionUtils.isEmpty(call.getRetryHistory()))
            query.append("'").append(String.join(Constants.COL_RESULT_NUMBER_SEPERATOR, call.getRetryHistory())).append("', ");

        if (StringUtils.isNotBlank(campaign.getColResultCbHistory()) && !CollectionUtils.isEmpty(call.getCbHistory()))
            query.append("'").append(String.join(",", call.getCbHistory())).append("', ");

        if (StringUtils.isNotBlank(campaign.getColResultCallProcess()) && !CollectionUtils.isEmpty(call.getCallProcessHistory()))
            query.append("'").append(String.join(Constants.COL_RESULT_NUMBER_SEPERATOR, call.getCallProcessHistory())).append("', ");

        if (campaign.getColResultAttempts() != null)
            query.append("'").append(call.getAttempts()).append("', ");

        if (call.getCallbackType() != null)
            query.append("'").append(call.getCallbackType()).append("', ");

        if (campaign.getColResultResponseValues() != null && StringUtils.isNotEmpty(call.getLscResponseValues()))
            query.append("'").append(call.getLscResponseValues().replace("'", "''")).append("'");

        query.append(")");

        dbEngine.executeUpdate(query.toString().replaceAll("\\, \\)", "\\)"),
                campaign.getId(),
                campaign.getDbConnectionString(),
                campaign.getDbUsername(),
                enCipherUtils.decryptPassword(campaign.getDbPassword()));
    }

    private void ensureCallProcessHistoryForResultWrite(Campaign campaign, Call call) {
        if (StringUtils.isNotBlank(campaign.getColResultCallProcess())) {
            call.ensureCallProcessStartsWithFresh();
        }
    }

    private boolean isUserResultCodeSelected(Call call) {
        return call.getUserResultCode().getId() != null;
    }

    /**
     * Check if db list and result table are the same and return the column by
     * which row existence will be checked
     *
     * @param campaign
     * @return column to check row existence
     */
    private String columnToCheckRowExistence(Campaign campaign) {
        return campaign.hasSameSourceAndResultTable() ? campaign.getColUniqueKey() : campaign.getColResultUniqueKey();
    }

    /**
     * Do batch insert for campaign
     *
     * @param campaign
     * @param data
     * @throws SQLException
     */
    @EscapeSqlNames
    public void insertDataForCampaign(Campaign campaign, List<Map> data) throws SQLException {
        if (CollectionUtils.isEmpty(data))
            return;
        data.stream()
                .map(Map::values)
                .forEach(this::checkDataValues);
        Set<String> existingColumns = new HashSet<>(getCampaignsTablesColumnNames(campaign));
        List<ParametrizedQuery> parametrizedQueries = new ArrayList<>();
        for (Map dataMap : data) {
            parametrizedQueries.add(getParametrizedInsertQuery(campaign, dataMap, existingColumns));
        }
        dbEngine.executeBatchParametrizedUpdate(parametrizedQueries, campaign.getId(),
                campaign.getDbConnectionString(),
                campaign.getDbUsername(),
                enCipherUtils.decryptPassword(campaign.getDbPassword()));
    }

    public void insertDataForCampaignExtended(Campaign campaign, List<Map<String, Object>> data, Connection con) throws SQLException {
        if (CollectionUtils.isEmpty(data)) return;

        Set<String> existingColumns = campaignColumnsCache.computeIfAbsent(
                campaign.getId(),
                id -> {
                    try {
                        return new HashSet<>(getCampaignsTablesColumnNames(campaign));
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                }
        );

        Map<String, Object> firstRow = data.get(0);
        List<String> columns = new ArrayList<>();
        for (String col : firstRow.keySet()) {
            if (existingColumns.contains(col) || existingColumns.contains(unescapeSqlName(col))) {
                columns.add(col);
            }
        }

        if (columns.isEmpty()) {
            log.warn("⚠️ No valid columns found for campaign {}", campaign.getId());
            return;
        }

        String columnList = String.join(",", columns);
        String placeholders = String.join(",", Collections.nCopies(columns.size(), "?"));
        String sql = "INSERT INTO " + campaign.getDbListTable() + " (" + columnList + ") VALUES (" + placeholders + ")";

        final int MAX_PARAMS = 2000;
        final int safeBatchSize = Math.max(1, MAX_PARAMS / columns.size());

        try (PreparedStatement ps = con.prepareStatement(sql)) {
            int batchCounter = 0;

            for (Map<String, Object> row : data) {
                int i = 1;
                for (String col : columns) {
                    ps.setObject(i++, row.get(col));
                }

                ps.addBatch();
                batchCounter++;

                if (batchCounter >= safeBatchSize) {
                    ps.executeBatch();
                    ps.clearBatch();
                    batchCounter = 0;
                }
            }

            if (batchCounter > 0) {
                ps.executeBatch();
            }
        }
    }

    /**
     * Do data insertion for campaign. This method is faster than
     * {@link this#insertDataForCampaign(Campaign, List)} but data keys has NO
     * protection from SQL injection attacks.
     *
     * @param campaign
     * @param data
     * @throws SQLException
     */
    @EscapeSqlNames
    public void insertDataForCampaignUnsafe(Campaign campaign, Map data) throws SQLException {
        insertSingleRowForCampaign(campaign, data, data.keySet());
    }

    private void insertSingleRowForCampaign(Campaign campaign, Map dataMap, Set<String> existingColumns) throws SQLException {
        ParametrizedQuery parametrizedQuery = getParametrizedInsertQuery(campaign, dataMap, existingColumns);
        dbEngine.executeBatchParametrizedUpdate(parametrizedQuery,
                campaign.getId(),
                campaign.getDbConnectionString(),
                campaign.getDbUsername(),
                enCipherUtils.decryptPassword(campaign.getDbPassword()));
    }

    private ParametrizedQuery getParametrizedInsertQuery(Campaign campaign, Map dataMap, Set<String> existingColumns) {
        List<Object> values = new ArrayList<>();
        StringBuilder query = new StringBuilder("INSERT INTO ").append(campaign.getDbListTable()).append(" (");
        DatabaseUtil.validateColumnNames(campaign.getDbConnectionString(), dataMap);
        dataMap.forEach((dataKey, dataValue) -> {
            if (existingColumns.contains(dataKey) || existingColumns.contains(unescapeSqlName(dataKey.toString()))) {
                values.add(dataMap.get(dataKey));
                query.append(dataKey).append(",");
            }
        });
        query.delete(query.length() - 1, query.length());
        if (values.isEmpty()) {
            query.append("DEFAULT VALUES");
        } else {
            query.append(") VALUES (");
            String valuesPlaceholder = IntStream.range(0, values.size())
                    .mapToObj(i -> "?")
                    .collect(Collectors.joining(", "));
            query.append(valuesPlaceholder);
            query.append(")");
        }
        return new ParametrizedQuery(query.toString(), values);
    }

    /**
     * Get column names of campaign's source table
     *
     * @param campaign
     * @return
     * @throws SQLException
     */
    private List<String> getCampaignsTablesColumnNames(Campaign campaign) throws SQLException {
        String[][] sourceTableColumnsWithTypes = getTableColumnsWithTypes(campaign.getDbListTable(),
                campaign.getId(),
                campaign.getDbConnectionString(),
                campaign.getDbUsername(),
                enCipherUtils.decryptPassword(campaign.getDbPassword()));
        String[][] resultTableColumnsWithTypes = getTableColumnsWithTypes(campaign.getDbResultTable(),
                campaign.getId(),
                campaign.getDbConnectionString(),
                campaign.getDbUsername(),
                enCipherUtils.decryptPassword(campaign.getDbPassword()));
        return Stream.concat(Stream.of(sourceTableColumnsWithTypes), Stream.of(resultTableColumnsWithTypes))
                .map(columnToType -> columnToType[0])
                .collect(Collectors.toList());
    }

    public String aggregateCampaignSourceTableColumn(String columnName, String aggregation, Campaign campaign) throws SQLException {
        String sourceTable = campaign.getDbListTable();
        String query = "SELECT " + aggregation + "(" + columnName + ") AS \"PD_ROWS\" FROM " + sourceTable;
        Integer maxRows = 1;
        JSONObject result = dbEngine.executeQuery(query,
                campaign.getId(),
                maxRows,
                campaign.getDbConnectionString(),
                campaign.getDbUsername(),
                enCipherUtils.decryptPassword(campaign.getDbPassword())).getJSONObject(0);
        if (result.has("PD_ROWS")) {
            return (String) result.get("PD_ROWS");
        } else {
            return null;
        }
    }

    @EscapeSqlNames
    public JSONArray getAllPhoneNumbers(Campaign campaign) throws SQLException {
        String joinedPhoneNumbersColumns = campaign.getEnabledPhoneNumberColumns()
                .stream()
                .map(PhoneNumberColumn::getColNumber)
                .collect(Collectors.joining(","));
        List<Filter> sourceTableFilters = campaign.getEnabledFilters().stream()
                .filter(f -> f.getTargetTable() != Constants.FILTER_TARGET_TABLE.RESULT_TABLE)
                .collect(Collectors.toList());
        String campaignWhereFromFilters = createWhereFromFilters(sourceTableFilters,
                campaign.getFilterJoinType(),
                "",
                "",
                DB_TYPE.getByConnectionString(campaign.getDbConnectionString()));

        StringBuilder query = new StringBuilder("SELECT ").append(joinedPhoneNumbersColumns)
                .append(" FROM ")
                .append(campaign.getDbListTable());
        query.append((campaignWhereFromFilters.length() > 0 ? " WHERE " + campaignWhereFromFilters : ""));
        return dbEngine.executeQuery(query.toString(),
                campaign.getId(),
                null,
                campaign.getDbConnectionString(),
                campaign.getDbUsername(),
                enCipherUtils.decryptPassword(campaign.getDbPassword()));
    }

    private void addCallbacksWhereClause(
            Campaign campaign,
            List<Long> usersIds,
            StringBuilder query,
            String resultAliasWithDot,
            Optional<Filter> segmentationFilter,
            List<Object> queryParameters
    ) {
        query.append(" WHERE ").append(resultAliasWithDot).append(campaign.getColResultFetchedDataFlag()).append(" IS NULL");
        query.append(" AND ").append(resultAliasWithDot).append(campaign.getColResultCompleteDate()).append(" IS NULL");
        if (campaign.getColResultCallbackDate() != null) {
            addCallbackCondition(query, resultAliasWithDot, campaign, usersIds, queryParameters);
        }
        appendTimezoneOptionsTo(query, campaign, CALLBACKS, resultAliasWithDot);
        if (campaign.isApplyFilterCallbacks() || segmentationFilter.isPresent()) {
            List<Filter> filters = campaign.getEnabledFiltersBySegmentation(false);
            String campaignWhereFromFilters = createWhereFromFilters(filters, segmentationFilter, campaign);
            query.append(!campaignWhereFromFilters.isEmpty() ? " AND " + campaignWhereFromFilters : "");
        }
        if (campaign.getSkillEnabled()) {
            String skillFilter = createSkillFilter(campaign);
            if (!StringUtils.isEmpty(skillFilter)) {
                query.append(" AND ").append(skillFilter);
            }
        }
    }

    public void executeUpdate(String query, Campaign campaign) throws SQLException {
        dbEngine.executeUpdate(query,
                campaign.getId(),
                campaign.getDbConnectionString(),
                campaign.getDbUsername(),
                enCipherUtils.decryptPassword(campaign.getDbPassword()));
    }

    private void checkDataValues(Collection<Object> values) {
        if (values.stream().anyMatch(v -> v instanceof Collection || v instanceof Map)) {
            throw new IllegalValueException("Data value can not be the object/collection");
        }
    }

    @EscapeSqlNames
    public synchronized void createPriorityTable(Campaign campaign) throws SQLException {
        StringBuilder query = new StringBuilder();
        Constants.DB_TYPE dbType = Constants.DB_TYPE.getByConnectionString(campaign.getDbConnectionString());
        String intType;
        String bigintType;
        if (dbType == Constants.DB_TYPE.MS_SQL || dbType == Constants.DB_TYPE.MYSQL) {
            intType = " INT,";
            bigintType = " BIGINT,";
        } else if (dbType == Constants.DB_TYPE.ORACLE) {
            intType = " NUMBER,";
            bigintType = " NUMBER(30),";
        } else if (dbType == Constants.DB_TYPE.POSTGRE_SQL) {
            intType = " INTEGER,";
            bigintType = " BIGINT,";
        } else {
            throw new SQLException("No driver found for connection string: " + campaign.getDbConnectionString());
        }
        priorityService.getPriorityTableCreationQuery(campaign, query, intType, bigintType);
        executeUpdate(query.toString(), campaign);
    }

    @EscapeSqlNames
    public boolean addForeignKeyToPriorityTable(Campaign campaign) {
        DB_TYPE dbType = DB_TYPE.getByConnectionString(campaign.getDbConnectionString());
        boolean shouldEscapeNames = shouldEscapeNames(dbType);

        try {
            StringBuilder foreignKeyAdditionQuery = new StringBuilder()
                    .append("ALTER TABLE ").append(campaign.getDbPriorityTable()).append(" ")
                    .append("ADD CONSTRAINT ").append(getEscapedValue(getForeignKeyName(campaign), shouldEscapeNames)).append(" ")
                    .append("FOREIGN KEY(").append(campaign.getColPriorityUniqueKey()).append(") ")
                    .append("REFERENCES ").append(campaign.getDbListTable())
                    .append("(").append(campaign.getColUniqueKey()).append(")")
                    .append("ON DELETE CASCADE");

            executeUpdate(foreignKeyAdditionQuery.toString(), campaign);
        } catch (SQLException e) {
            LOG.warn("Unable to add foreign key to table " + campaign.getDbPriorityTable(), e, LOG_MODULE.DB);
            return false;
        }
        return true;
    }

    @EscapeSqlNames
    public void dropForeignKeyInPriorityTable(Campaign oldCampaign, Campaign newCampaign) {
        DB_TYPE dbType = DB_TYPE.getByConnectionString(newCampaign.getDbConnectionString());
        boolean shouldEscapeNames = shouldEscapeNames(dbType);

        try {
            StringBuilder foreignKeyDroppingQuery = new StringBuilder()
                    .append("ALTER TABLE ").append(newCampaign.getDbPriorityTable()).append(" ")
                    .append("DROP CONSTRAINT ").append(getEscapedValue(getForeignKeyName(oldCampaign), shouldEscapeNames));

            executeUpdate(foreignKeyDroppingQuery.toString(), newCampaign);
        } catch (SQLException e) {
            LOG.warn("Unable to drop foreign key in table " + newCampaign.getDbPriorityTable(), e, LOG_MODULE.DB);
        }
    }

    private String getForeignKeyName(Campaign campaign) throws SQLException {
        DB_TYPE dbType = DB_TYPE.getByConnectionString(campaign.getDbConnectionString());
        if (dbType == null) {
            throw new SQLException("No driver found for connection string: " + campaign.getDbConnectionString());
        }
        String baseName = "FK_pd_multiple_sort_" + campaign.getId();
        String extendedName = baseName + "_" + campaign.getDbListTable() + "_" + campaign.getColUniqueKey();
        if (dbType == DB_TYPE.ORACLE) {
            baseName = baseName.toUpperCase();
            extendedName = extendedName.toUpperCase();
        }
        int maxLength = dbType.getMaxKeyLength();
        return extendedName.length() <= maxLength ? extendedName : baseName;
    }

    @EscapeSqlNames
    public void clearPriorityTable(Campaign campaign)
            throws SQLException {
        executeUpdate("TRUNCATE TABLE " + campaign.getDbPriorityTable(), campaign);
    }

    @EscapeSqlNames
    public void createEmptyPriorityRecords(Campaign campaign) throws SQLException {
        String resultAliasWithDot = getResultAliasWithDot(campaign);
        String filters = createWhereFromFilters(campaign);
        StringBuilder query = new StringBuilder("INSERT INTO ").append(campaign.getDbPriorityTable())
                .append("(")
                .append(campaign.getColPriorityUniqueKey())
                .append(") SELECT ").append(SOURCE_TABLE_ALIAS_DOT).append(campaign.getColUniqueKey()).append(" FROM ")
                .append(createFromClause(campaign))
                .append(" WHERE ").append(SOURCE_TABLE_ALIAS_DOT).append(campaign.getColUniqueKey())
                .append(" NOT IN (SELECT ").append(campaign.getColPriorityUniqueKey())
                .append(" FROM ").append(campaign.getDbPriorityTable()).append(") AND ")
                .append(resultAliasWithDot).append(campaign.getColResultFetchedDataFlag()).append(" IS NULL");
        if (org.springframework.util.StringUtils.hasText(filters)) {
            query.append(" AND ").append(filters);
        }
        executeUpdate(query.toString(), campaign);
    }

    @EscapeSqlNames
    public void updatePriorities(Campaign campaign, List<SortField> sortFields) throws SQLException {
        if (!CollectionUtils.isEmpty(sortFields) && sortFields.stream().anyMatch(s -> s.getSortMethod() != Constants.MULTIPLE_SORT_METHOD.NATURAL_ORDER)) {
            String resultAliasWithDot = getResultAliasWithDot(campaign);
            String filters = createWhereFromFilters(campaign);
            StringBuilder query = new StringBuilder("UPDATE ").append(PRIORITY_TABLE_ALIAS_SPACE).append("SET");
            priorityService.addFieldsCasesToRecalculationQuery(sortFields, campaign, query);
            query.append(" FROM ").append(createFromClause(campaign));
            query.append(" WHERE ").append(resultAliasWithDot).append(campaign.getColResultFetchedDataFlag()).append(" IS NULL");
            if (filters.length() > 0) {
                query.append(" AND ").append(filters);
            }
            executeUpdate(query.toString(), campaign);
        }
    }

    static String getResultAliasWithDot(Campaign campaign) {
        return campaign.hasSameSourceAndResultTable() ? SOURCE_TABLE_ALIAS_DOT : RESULT_TABLE_ALIAS_DOT;
    }

    public static boolean shouldEscapeNames(DB_TYPE dbType) {
        return DB_TYPE.ORACLE.equals(dbType) || DB_TYPE.POSTGRE_SQL.equals(dbType);
    }

    @EscapeSqlNames
    public int countCallbacksToLoad(Campaign campaign, List<Long> usersIds) throws SQLException {
        StringBuilder query = new StringBuilder("SELECT COUNT(*) FROM ").append(createFromClause(campaign));
        // null yerine Optional.empty()
        // Task 325461: callback tarih koşulu parametreye taşındığı için bu sayım da parametreli
        // yolu kullanır (aynı WHERE üreticisini paylaşıyorlar).
        List<Object> queryParameters = new ArrayList<>();
        addCallbacksWhereClause(campaign, usersIds, query, getResultAliasWithDot(campaign), Optional.empty(), queryParameters);

        JSONArray result = dbEngine.executeParametrizedQuery(new ParametrizedQuery(query.toString(), queryParameters),
                campaign.getId(),
                1,
                campaign.getDbConnectionString(),
                campaign.getDbUsername(),
                enCipherUtils.decryptPassword(campaign.getDbPassword()));
        return result.length() > 0 ? ((JSONObject) result.get(0)).getInt("") : 0;
    }


    /**
     * Fetches a single row from the campaign's source table by its unique key,
     * returning all columns — including those whose value is {@code NULL} in the database.
     *
     * <p>This method is used to enrich the call's raw data ({@code rawData2}) before
     * publishing the interaction-notifier event. Because {@link DBEngine#executeQuery}
     * silently drops null-valued columns, this method delegates to
     * {@link DBEngine#executeQueryWithNulls} to preserve the full column schema.</p>
     *
     * <p>The returned {@link JSONObject} contains every column of the row.
     * Columns that are {@code NULL} in the database are represented as
     * {@link org.json.JSONObject#NULL} so that downstream mappers can serialize
     * them as explicit JSON {@code null} values rather than omitting them.</p>
     *
     * @param campaign  the campaign whose source table and connection details will be used
     * @param uniqueKey the unique key value identifying the row to fetch
     * @return a {@link JSONObject} containing all columns of the matched row,
     *         or {@code null} if no row was found or an error occurred
     */
    public JSONObject fetchFullRowByUniqueKey(Campaign campaign, String uniqueKey) {
        try {
            String table = campaign.getDbListTable();
            String keyColumn = campaign.getColUniqueKey();

            // Build a plain SELECT * to retrieve every column in the row.
            // Null-inclusive variant (executeQueryWithNulls) is used intentionally —
            // the standard executeQuery strips null-valued columns from the ResultSet.
            String query = "SELECT * FROM " + table
                    + " WHERE " + keyColumn + " = " + uniqueKey;

            log.debug("fetchFullRowByUniqueKey → query: {}", query);

            JSONArray result = dbEngine.executeQueryWithNulls(
                    query,
                    campaign.getId(),
                    1, // we only need the single row matching the unique key
                    campaign.getDbConnectionString(),
                    campaign.getDbUsername(),
                    enCipherUtils.decryptPassword(campaign.getDbPassword())
            );

            // Return null so the caller can skip enrichment gracefully
            if (result == null || result.length() == 0) {
                log.warn("fetchFullRowByUniqueKey → no row found for uniqueKey={}", uniqueKey);
                return null;
            }

            JSONObject row = result.getJSONObject(0);
            log.debug("fetchFullRowByUniqueKey → fetched {} columns for uniqueKey={}",
                    row.keySet().size(), uniqueKey);

            return row;

        } catch (Exception e) {
            // Log and swallow — enrichment failure must not interrupt call result publishing
            log.warn("fetchFullRowByUniqueKey failed — campaign={}, uniqueKey={}",
                    campaign.getId(), uniqueKey, e);
            return null;
        }
    }

}
