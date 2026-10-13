app.service('constants', function(locMsg) {

  var vm = this;

  vm.DEFAULT_CAMPAIGN_COLUMN_NAMES = {
    ID : 'id',
    RANDOM_ID : 'random_id',
    CALLER_ID : 'caller_id',
    PHONE_NUMBER : 'phone_number',
    CALL_FROM_TIME : 'call_from_time',
    CALL_TILL_TIME : 'call_till_time',
    TIMEZONE_OFFSET : 'timezone_offset',
    SPECIFIC_SKILL_NAME: 'specific_skill_name',

    UNIQUE_KEY : 'unique_key',
    START_DATE : 'start_date',
    ANSWER_DATE : 'answer_date',
    COMPLETE_DATE : 'complete_date',
    CALLED_NUMBER : 'called_number',
    TELEPHONY_RC : 'telephony_result_code',
    USER_RC : 'user_result_code',
    RC_SEQUENCE : 'rc_sequence',
    USER_ID : 'user_id',
    CALLBACK_NUMBER : 'callback_number',
    CALLBACK_DATE : 'callback_date',
    RETRY_NUMBER : 'retry_number',
    RETRY_HISTORY : 'retry_history',
    CB_HISTORY : 'cb_history',
    CALL_PROCESS : 'call_process',
    RETRY_DATE : 'retry_date',
    FETCHED_DATA_FLAG : 'is_fetched',
    DNC_NUMBERS : 'dnc_numbers',
    ATTEMPTS : 'attempts',
    CALLBACK_ATTEMPTS : 'callback_attempts',
    UPDATE_DATE : 'update_date',
    RESPONSE_VALUES: 'response_values',
    CUSTOMER_NO : 'customer_no',
    CLOSE_REASON : 'close_reason',
    DATE_ENTERED : 'date_entered'
  };

  vm.APPOINTER_RESET_STRATEGY = {
    TIMER : 0,
    ATTEMPT_COUNT : 1
  };

  vm.SIMPLE_DB_TYPES = {
    INTEGER : 'INTEGER',
    DECIMAL : 'DECIMAL',
    DATE : 'DATE',
    STRING : 'STRING',
    OTHER : 'OTHER'
  };

  vm.DB_FIELD_TYPES = {
    FLOAT : 'FLOAT',
    DATE : 'DATE',
    CHAR : 'CHAR',
    NCHAR : 'NCHAR',
    BIGINT : 'BIGINT',
    SMALLINT : 'SMALLINT',
    DECIMAL : 'DECIMAL',
    INT : 'INT',
    TINYINT : 'TINYINT',
    DATETIME : 'DATETIME',
    TIME : 'TIME',
    VARCHAR : 'VARCHAR',
    TEXT : 'TEXT',
    TIMESTAMP : 'TIMESTAMP',
    BLOB : 'BLOB',
    // SQL Server types
    NUMERIC : 'NUMERIC',
    BIT : 'BIT',
    SMALLMONEY : 'SMALLMONEY',
    MONEY : 'MONEY',
    REAL : 'REAL',
    DATETIMEOFFSET : 'DATETIMEOFFSET',
    DATETIME2 : 'DATETIME2',
    SMALLDATETIME : 'SMALLDATETIME',
    NVARCHAR : 'NVARCHAR',
    NTEXT : 'NTEXT',
    BINARY : 'BINARY',
    IMAGE : 'IMAGE',
    // oracle types
    VARCHAR2 : 'VARCHAR2',
    NVARCHAR2 : 'NVARCHAR2',
    NUMBER : 'NUMBER',
    LONG : 'LONG',
    INTEGER : 'INTEGER',
    BINARY_FLOAT : 'BINARY_FLOAT',
    BINARY_DOUBLE : 'BINARY_DOUBLE',
    RAW : 'RAW',
    LONG_RAW : 'LONG',
    ROWID : 'ROWID',
    UROWID : 'UROWID',
    CLOB : 'CLOB',
    NCLOB : 'NCLOB',
    BFILE : 'BFILE',
    // My SQL types
    MEDIUMINT : 'MEDIUMINT',
    DOUBLE : 'DOUBLE',
    YEAR : 'YEAR',
    TINYBLOB : 'TINYBLOB',
    TINYTEXT : 'TINYTEXT',
    MEDIUMBLOB : 'MEDIUMBLOB',
    MEDIUMTEXT : 'MEDIUMTEXT',
    LONGBLOB : 'LONGBLOB',
    LONGTEXT : 'LONGTEXT',
    ENUM : 'ENUM',
    CHARACTER_VARYING : 'CHARACTER_VARYING',
    TIMESTAMP_WITHOUT_TIME_ZONE : 'TIMESTAMP_WITHOUT_TIME_ZONE',
    DOUBLE_PRECISION : 'DOUBLE_PRECISION'
  };

  vm.RESULT_FIELDS_ALLOWED_TYPES = {};
  vm.RESULT_FIELDS_ALLOWED_TYPES[vm.DEFAULT_CAMPAIGN_COLUMN_NAMES.FETCHED_DATA_FLAG] = [ vm.DB_FIELD_TYPES.FLOAT, vm.DB_FIELD_TYPES.DOUBLE_PRECISION ];
  vm.RESULT_FIELDS_ALLOWED_TYPES[vm.DEFAULT_CAMPAIGN_COLUMN_NAMES.RETRY_DATE] = [ vm.DB_FIELD_TYPES.FLOAT, vm.DB_FIELD_TYPES.DOUBLE_PRECISION ];
  vm.RESULT_FIELDS_ALLOWED_TYPES[vm.DEFAULT_CAMPAIGN_COLUMN_NAMES.RETRY_NUMBER] = [ vm.DB_FIELD_TYPES.VARCHAR, vm.DB_FIELD_TYPES.NVARCHAR, vm.DB_FIELD_TYPES.NVARCHAR2, vm.DB_FIELD_TYPES.CHARACTER_VARYING ];
  vm.RESULT_FIELDS_ALLOWED_TYPES[vm.DEFAULT_CAMPAIGN_COLUMN_NAMES.RETRY_HISTORY] = [ vm.DB_FIELD_TYPES.VARCHAR, vm.DB_FIELD_TYPES.NVARCHAR, vm.DB_FIELD_TYPES.NVARCHAR2, vm.DB_FIELD_TYPES.CHARACTER_VARYING ];
  vm.RESULT_FIELDS_ALLOWED_TYPES[vm.DEFAULT_CAMPAIGN_COLUMN_NAMES.CB_HISTORY] = [ vm.DB_FIELD_TYPES.VARCHAR, vm.DB_FIELD_TYPES.NVARCHAR, vm.DB_FIELD_TYPES.NVARCHAR2, vm.DB_FIELD_TYPES.CHARACTER_VARYING ];
  vm.RESULT_FIELDS_ALLOWED_TYPES[vm.DEFAULT_CAMPAIGN_COLUMN_NAMES.CALL_PROCESS] = [ vm.DB_FIELD_TYPES.VARCHAR, vm.DB_FIELD_TYPES.NVARCHAR, vm.DB_FIELD_TYPES.NVARCHAR2, vm.DB_FIELD_TYPES.CHARACTER_VARYING ];
  vm.RESULT_FIELDS_ALLOWED_TYPES[vm.DEFAULT_CAMPAIGN_COLUMN_NAMES.CLOSE_REASON] = [ vm.DB_FIELD_TYPES.VARCHAR, vm.DB_FIELD_TYPES.NVARCHAR, vm.DB_FIELD_TYPES.NVARCHAR2, vm.DB_FIELD_TYPES.CHARACTER_VARYING ];
  vm.RESULT_FIELDS_ALLOWED_TYPES[vm.DEFAULT_CAMPAIGN_COLUMN_NAMES.CALLBACK_DATE] = [ vm.DB_FIELD_TYPES.FLOAT, vm.DB_FIELD_TYPES.DOUBLE_PRECISION ];
  vm.RESULT_FIELDS_ALLOWED_TYPES[vm.DEFAULT_CAMPAIGN_COLUMN_NAMES.CALLBACK_NUMBER] = [ vm.DB_FIELD_TYPES.VARCHAR, vm.DB_FIELD_TYPES.NVARCHAR, vm.DB_FIELD_TYPES.NVARCHAR2, vm.DB_FIELD_TYPES.CHARACTER_VARYING ];
  vm.RESULT_FIELDS_ALLOWED_TYPES[vm.DEFAULT_CAMPAIGN_COLUMN_NAMES.USER_ID] = [ vm.DB_FIELD_TYPES.BIGINT, vm.DB_FIELD_TYPES.NUMBER ];
  vm.RESULT_FIELDS_ALLOWED_TYPES[vm.DEFAULT_CAMPAIGN_COLUMN_NAMES.RC_SEQUENCE] = [ vm.DB_FIELD_TYPES.INT, vm.DB_FIELD_TYPES.BIGINT, vm.DB_FIELD_TYPES.NUMBER ];
  vm.RESULT_FIELDS_ALLOWED_TYPES[vm.DEFAULT_CAMPAIGN_COLUMN_NAMES.USER_RC] = [ vm.DB_FIELD_TYPES.BIGINT, vm.DB_FIELD_TYPES.NUMBER ];
  vm.RESULT_FIELDS_ALLOWED_TYPES[vm.DEFAULT_CAMPAIGN_COLUMN_NAMES.TELEPHONY_RC] = [ vm.DB_FIELD_TYPES.INT, vm.DB_FIELD_TYPES.BIGINT, vm.DB_FIELD_TYPES.NUMBER ];
  vm.RESULT_FIELDS_ALLOWED_TYPES[vm.DEFAULT_CAMPAIGN_COLUMN_NAMES.CALLED_NUMBER] = [ vm.DB_FIELD_TYPES.VARCHAR, vm.DB_FIELD_TYPES.NVARCHAR, vm.DB_FIELD_TYPES.NVARCHAR2, vm.DB_FIELD_TYPES.CHARACTER_VARYING ];
  vm.RESULT_FIELDS_ALLOWED_TYPES[vm.DEFAULT_CAMPAIGN_COLUMN_NAMES.COMPLETE_DATE] = [ vm.DB_FIELD_TYPES.FLOAT, vm.DB_FIELD_TYPES.DOUBLE_PRECISION ];
  vm.RESULT_FIELDS_ALLOWED_TYPES[vm.DEFAULT_CAMPAIGN_COLUMN_NAMES.ANSWER_DATE] = [ vm.DB_FIELD_TYPES.FLOAT, vm.DB_FIELD_TYPES.DOUBLE_PRECISION ];
  vm.RESULT_FIELDS_ALLOWED_TYPES[vm.DEFAULT_CAMPAIGN_COLUMN_NAMES.START_DATE] = [ vm.DB_FIELD_TYPES.FLOAT, vm.DB_FIELD_TYPES.DOUBLE_PRECISION ];
  vm.RESULT_FIELDS_ALLOWED_TYPES[vm.DEFAULT_CAMPAIGN_COLUMN_NAMES.UNIQUE_KEY] = [ vm.DB_FIELD_TYPES.BIGINT, vm.DB_FIELD_TYPES.NUMBER ];
  vm.RESULT_FIELDS_ALLOWED_TYPES[vm.DEFAULT_CAMPAIGN_COLUMN_NAMES.TIMEZONE_OFFSET] = [ vm.DB_FIELD_TYPES.INT, vm.DB_FIELD_TYPES.BIGINT, vm.DB_FIELD_TYPES.NUMBER ];
  vm.RESULT_FIELDS_ALLOWED_TYPES[vm.DEFAULT_CAMPAIGN_COLUMN_NAMES.CALL_FROM_TIME] = [ vm.DB_FIELD_TYPES.FLOAT, vm.DB_FIELD_TYPES.DOUBLE_PRECISION ];
  vm.RESULT_FIELDS_ALLOWED_TYPES[vm.DEFAULT_CAMPAIGN_COLUMN_NAMES.CALL_TILL_TIME] = [ vm.DB_FIELD_TYPES.FLOAT, vm.DB_FIELD_TYPES.DOUBLE_PRECISION ];
  vm.RESULT_FIELDS_ALLOWED_TYPES[vm.DEFAULT_CAMPAIGN_COLUMN_NAMES.PHONE_NUMBER] = [ vm.DB_FIELD_TYPES.VARCHAR, vm.DB_FIELD_TYPES.NVARCHAR, vm.DB_FIELD_TYPES.NVARCHAR2, vm.DB_FIELD_TYPES.CHARACTER_VARYING ];
  vm.RESULT_FIELDS_ALLOWED_TYPES[vm.DEFAULT_CAMPAIGN_COLUMN_NAMES.CALLER_ID] = [ vm.DB_FIELD_TYPES.VARCHAR, vm.DB_FIELD_TYPES.NVARCHAR, vm.DB_FIELD_TYPES.NVARCHAR2, vm.DB_FIELD_TYPES.CHARACTER_VARYING ];
  vm.RESULT_FIELDS_ALLOWED_TYPES[vm.DEFAULT_CAMPAIGN_COLUMN_NAMES.DNC_NUMBERS] = [ vm.DB_FIELD_TYPES.VARCHAR, vm.DB_FIELD_TYPES.NVARCHAR, vm.DB_FIELD_TYPES.NVARCHAR2, vm.DB_FIELD_TYPES.CHARACTER_VARYING ];
  vm.RESULT_FIELDS_ALLOWED_TYPES[vm.DEFAULT_CAMPAIGN_COLUMN_NAMES.SPECIFIC_SKILL_NAME] = [ vm.DB_FIELD_TYPES.VARCHAR, vm.DB_FIELD_TYPES.NVARCHAR, vm.DB_FIELD_TYPES.NVARCHAR2, vm.DB_FIELD_TYPES.CHARACTER_VARYING ];
  vm.RESULT_FIELDS_ALLOWED_TYPES[vm.DEFAULT_CAMPAIGN_COLUMN_NAMES.PRIORITY] = [ vm.DB_FIELD_TYPES.INT, vm.DB_FIELD_TYPES.BIGINT, vm.DB_FIELD_TYPES.NUMBER ];
  vm.RESULT_FIELDS_ALLOWED_TYPES[vm.DEFAULT_CAMPAIGN_COLUMN_NAMES.ATTEMPTS] = [ vm.DB_FIELD_TYPES.INT, vm.DB_FIELD_TYPES.BIGINT, vm.DB_FIELD_TYPES.NUMBER ];
  vm.RESULT_FIELDS_ALLOWED_TYPES[vm.DEFAULT_CAMPAIGN_COLUMN_NAMES.CALLBACK_ATTEMPTS] = [ vm.DB_FIELD_TYPES.INT, vm.DB_FIELD_TYPES.INTEGER, vm.DB_FIELD_TYPES.BIGINT, vm.DB_FIELD_TYPES.NUMBER ];
  vm.RESULT_FIELDS_ALLOWED_TYPES[vm.DEFAULT_CAMPAIGN_COLUMN_NAMES.UPDATE_DATE] = [ vm.DB_FIELD_TYPES.DATETIME, vm.DB_FIELD_TYPES.TIMESTAMP ];
  vm.RESULT_FIELDS_ALLOWED_TYPES[vm.DEFAULT_CAMPAIGN_COLUMN_NAMES.RESPONSE_VALUES] = [ vm.DB_FIELD_TYPES.VARCHAR, vm.DB_FIELD_TYPES.NVARCHAR, vm.DB_FIELD_TYPES.NVARCHAR2, vm.DB_FIELD_TYPES.CHARACTER_VARYING ];
  vm.RESULT_FIELDS_ALLOWED_TYPES[vm.DEFAULT_CAMPAIGN_COLUMN_NAMES.CUSTOMER_NO] = [ vm.DB_FIELD_TYPES.VARCHAR, vm.DB_FIELD_TYPES.NVARCHAR, vm.DB_FIELD_TYPES.NVARCHAR2, vm.DB_FIELD_TYPES.CHARACTER_VARYING ];
  vm.RESULT_FIELDS_ALLOWED_TYPES[vm.DEFAULT_CAMPAIGN_COLUMN_NAMES.DATE_ENTERED] = [ vm.DB_FIELD_TYPES.DATETIME, vm.DB_FIELD_TYPES.DATETIME2, vm.DB_FIELD_TYPES.SMALLDATETIME, vm.DB_FIELD_TYPES.DATETIMEOFFSET, vm.DB_FIELD_TYPES.DATE, vm.DB_FIELD_TYPES.TIMESTAMP, vm.DB_FIELD_TYPES.TIMESTAMP_WITHOUT_TIME_ZONE ];

  vm.FILTER_TYPE = {
    PREDEFINED : 0,
    FREE : 1
  };

  vm.FILTER_TARGET_TABLE = {
    SOURCE_TABLE : 0,
    RESULT_TABLE : 1
  };

  vm.CAMPAIGN_TYPE_SELECT_OPTIONS = [ {
    value : '',
    label : locMsg['input.option.select']
  }, {
    value : '0',
    label : locMsg['campaign.type.0']
  }, {
    value : '1',
    label : locMsg['campaign.type.1']
  }, {
    value : '2',
    label : locMsg['campaign.type.2']
  }, {
    value : '3',
    label : locMsg['campaign.type.3']
  }, {
    value : '4',
    label : locMsg['campaign.type.4']
  }, {
    value : '5',
    label : locMsg['campaign.type.5']
  } ];


  vm.CAMPAIGN_STATE_SELECT_OPTIONS = [ {
    value : '',
    label : locMsg['input.option.select']
  }, {
    value : '0',
    label : locMsg['campaign.state.0']
  }, {
    value : '1',
    label : locMsg['campaign.state.1']
  }, {
    value : '2',
    label : locMsg['campaign.state.2']
  }, {
    value : '3',
    label : locMsg['campaign.state.3']
  }, {
    value : '4',
    label : locMsg['campaign.state.4']
  }, {
    value : '5',
    label : locMsg['campaign.state.5']
  }, {
    value : '6',
    label : locMsg['campaign.state.6']
  }, {
    value : '7',
    label : locMsg['campaign.state.7']
  } ];

  vm.ACTION_LOG_LOG_LEVEL_SELECT_OPTIONS = [{
      value : '',
      label : locMsg['input.option.select']
  }, {
    value : '0',
    label : locMsg['log.level.0']
  }, {
    value : '1',
    label : locMsg['log.level.1']
  }, {
    value : '2',
    label : locMsg['log.level.2']
  } ];

  vm.ACTION_LOG_MODULE_SELECT_OPTIONS = [{
      value : '',
      label : locMsg['input.option.select']},
  {
    value : '0',
    label : locMsg['log.module.0']
  }, {
    value : '1',
    label : locMsg['log.module.1']
  }, {
    value : '2',
    label : locMsg['log.module.2']
  }, {
    value : '3',
    label : locMsg['log.module.3']
  }, {
    value : '4',
    label : locMsg['log.module.4']
  }, {
    value : '5',
    label : locMsg['log.module.5']
  }, {
    value : '6',
    label : locMsg['log.module.6']
  } ];

  vm.LSC_REPLY_TYPE_SELECT_OPTIONS = [ {
    value : '',
    label : locMsg['input.option.select']
  }, {
    value : '0',
    label : locMsg['lsc.replyType.0']
  }, {
    value : '1',
    label : locMsg['lsc.replyType.1']
  }, {
    value : '2',
    label : locMsg['lsc.replyType.2']
  } ];

  vm.ADMIN_LOGS_ACTION_TYPE_SELECT_OPTIONS = [ {
    value : '',
    label : locMsg['input.option.select']
  }, {
    value : '0',
    label : locMsg['admin.logs.action.type.0']
  }, {
    value : '1',
    label : locMsg['admin.logs.action.type.1']
  }, {
    value : '2',
    label : locMsg['admin.logs.action.type.2']
  } ];

  vm.ADMIN_LOGS_ACTION_TARGET_SELECT_OPTIONS = [ {
    value : '',
    label : locMsg['input.option.select']
  }, {
    value : '0',
    label : locMsg['admin.logs.action.target.0']
  }, {
    value : '1',
    label : locMsg['admin.logs.action.target.1']
  }, {
    value : '2',
    label : locMsg['admin.logs.action.target.2']
  } ];

  vm.predefinedValuesTypes = {
    CONCURRENT_LINES_MAX: 0,
    CONCURRENT_LINES_MIN: 1,
    ABANDON_TIMEOUT_MAX: 2,
    ABANDON_TIMEOUT_MIN: 3,
    RING_TIMEOUT_MAX: 4,
    RING_TIMEOUT_MIN: 5,
    DIALING_BOOST_MAX: 6,
    DIALING_BOOST_MIN: 7
  };

  vm.SORT_BY_MULTIPLE = '{MULTIPLE_SORT}';

  vm.PRIORITY_RECALCULATION_STATE = {
    EXECUTING: 0,
    EXPECTING: 1,
    DECLINED: 2
  };

  vm.CUSTOM_FIELD_TYPE = [{
    value: '0',
    label: locMsg['campaign.customColumnType.text'],
    dbType: this.DB_FIELD_TYPES.NVARCHAR2
  }, {
    value: '1',
    label: locMsg['campaign.customColumnType.int'],
    dbType: this.DB_FIELD_TYPES.INT
  }, {
    value: '2',
    label: locMsg['campaign.customColumnType.fractional'],
    dbType: this.DB_FIELD_TYPES.FLOAT
  }, {
    value: '3',
    label: locMsg['campaign.customColumnType.date'],
    dbType: this.DB_FIELD_TYPES.DATETIME
  } ];

  vm.RETRY_CALLER_ID_BEHAVIOR = [
    {
      value: 0,
      displayValue: 'Any'
    },
    {
      value: 1,
      displayValue: 'Unique'
    },
    {
      value: 2,
      displayValue: 'Same'
    }
  ]

  vm.STATUS_TRANSLATIONS = {
    'status.completed': locMsg['status.completed'],
    'status.attempts_limit_reached': locMsg['status.attempts_limit_reached'],
    'status.callable': locMsg['status.callable'],
    'status.eliminated': locMsg['status.eliminated'],
    'status.in_process': locMsg['status.in_process'],
    'status.retry': locMsg['status.retry'],
    'status.callback': locMsg['status.callback'],
    'status.excluded': locMsg['status.excluded'],
    'status.dnc': locMsg['status.dnc'],
    'status.unprocessed': locMsg['status.unprocessed']
  };

});
