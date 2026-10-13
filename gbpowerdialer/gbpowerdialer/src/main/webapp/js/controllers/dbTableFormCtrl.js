app.controller('dbTableFormCtrl', function($modalInstance, dbTableService, campaign, constants, listSourceTables, $q, alertService, locMsg) {

  var vm = this;
  vm.campaign = campaign;

  vm.createSourceTable = function() {
    if (!validateTableName(vm.table.tableName)) {
      return;
    }
    var columnsToCreate = [];
    if (vm.table.fetchDataRandomly) {
      campaign.colSortBy = constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.RANDOM_ID;
      addColumnToCreate(columnsToCreate, campaign.colSortBy);
    } else {
      campaign.colSortBy = constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.ID;
    }
    campaign.colUniqueKey = constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.ID;
    campaign.dbListTable = vm.table.tableName;
    campaign.colCallFromTime = constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.CALL_FROM_TIME;
    campaign.colCallToTime = constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.CALL_TILL_TIME;
    campaign.colTimezoneOffset = constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.TIMEZONE_OFFSET;
    campaign.colSkill = constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.SPECIFIC_SKILL_NAME;
    addColumnToCreate(columnsToCreate, campaign.colUniqueKey);
    addColumnToCreate(columnsToCreate, campaign.colCallFromTime);
    addColumnToCreate(columnsToCreate, campaign.colCallToTime);
    addColumnToCreate(columnsToCreate, campaign.colTimezoneOffset);
    addColumnToCreate(columnsToCreate, campaign.colSkill);

    if (!campaign.phoneNumberColumns) {
      campaign.phoneNumberColumns = [];
    } else if (campaign.phoneNumberColumns.length > 0) {
      for (var i = campaign.phoneNumberColumns.length; i--;) {
        var phoneNumber = campaign.phoneNumberColumns[i];
        if (phoneNumber.id === undefined) {
          campaign.phoneNumberColumns.splice(i, 1);
        } else {
          phoneNumber.deleted = true;
          phoneNumber.changed = true;
        }
      }
    }
    campaign.phoneNumberColumns.push({
      colNumber : constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.PHONE_NUMBER + "_1",
      colCallerId : constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.CALLER_ID + "_1",
      colCallFromTime : constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.CALL_FROM_TIME + '_1',
      colCallTillTime : constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.CALL_TILL_TIME + '_1',
      colTimezoneOffset : constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.TIMEZONE_OFFSET + '_1',
      enabled : true,
      changed : true,
      deleted : false,
      isNew : false,
      colSort : 0
    });
    addColumnToCreate(columnsToCreate, constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.PHONE_NUMBER + "_1");
    addColumnToCreate(columnsToCreate, constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.CALLER_ID + "_1");
    addColumnToCreate(columnsToCreate, constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.CALL_FROM_TIME + '_1');
    addColumnToCreate(columnsToCreate, constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.CALL_TILL_TIME + '_1');
    addColumnToCreate(columnsToCreate, constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.TIMEZONE_OFFSET + '_1');

    var delayedAction = function() {
      var defer = $q.defer();
      dbTableService.createDefaultSourceTable(vm.table, campaign).then(function(result) {
        defer.resolve(result);
      }, function(reason) {
        defer.reject(reason);
      });
      return defer.promise;
    };
    $modalInstance.close({
      delayedAction : delayedAction,
      tableToCreate : vm.table.tableName,
      columnsToCreate : columnsToCreate
    });

  };

  var addColumnToCreate = function(columnsArray, colName) {
    columnsArray.push({
      fieldName : colName
    });
  };

  vm.createResultTable = function() {
    if (!validateTableName(vm.table.tableName)) {
      return;
    }

    var columnsToCreate = [];
    var delayedAction = function() {
      var defer = $q.defer();
      dbTableService.createDefaultResultTable(vm.table, campaign).then(function(result) {
        defer.resolve(result);
      }, function(reason) {
        defer.reject(reason);
      });
      return defer.promise;
    };
    campaign.dbResultTable = vm.table.tableName;
    campaign.colResultUniqueKey = constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.UNIQUE_KEY;
    campaign.colResultStartDate = constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.START_DATE;
    campaign.colResultAnswerDate = constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.ANSWER_DATE;
    campaign.colResultCompleteDate = constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.COMPLETE_DATE;
    campaign.colResultCalledNumber = constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.CALLED_NUMBER;
    campaign.colResultTelephonyRc = constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.TELEPHONY_RC;
    campaign.colResultUserRc = constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.USER_RC;
    campaign.colResultRcSequence = constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.RC_SEQUENCE;
    campaign.colResultUserId = constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.USER_ID;
    campaign.colResultCallbackNumber = constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.CALLBACK_NUMBER;
    campaign.colResultCallbackDate = constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.CALLBACK_DATE;
    campaign.colResultRetryNumber = constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.RETRY_NUMBER;
    campaign.colResultRetryHistory = constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.RETRY_HISTORY;
    campaign.colResultCbHistory = constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.CB_HISTORY;
    campaign.colResultCallProcess = constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.CALL_PROCESS;
    campaign.colResultRetryDate = constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.RETRY_DATE;
    campaign.colResultFetchedDataFlag = constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.FETCHED_DATA_FLAG;
    campaign.colResultDNCNumbers = constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.DNC_NUMBERS;
    campaign.colResultAttemtps = constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.ATTEMPTS;
    campaign.colResultUpdateDate = constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.UPDATE_DATE;
    campaign.colResultResponseValues = constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.RESPONSE_VALUES;
    // Task 325559: yeni result tablosu olusturulurken close_reason binding'i de atanir; kolonun
    // kendisi backend createDefaultResultTable icinde zaten olusturuluyor.
    campaign.colResultCloseReason = constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.CLOSE_REASON;

    addColumnToCreate(columnsToCreate, constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.UNIQUE_KEY);
    addColumnToCreate(columnsToCreate, constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.START_DATE);
    addColumnToCreate(columnsToCreate, constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.ANSWER_DATE);
    addColumnToCreate(columnsToCreate, constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.COMPLETE_DATE);
    addColumnToCreate(columnsToCreate, constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.CALLED_NUMBER);
    addColumnToCreate(columnsToCreate, constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.TELEPHONY_RC);
    addColumnToCreate(columnsToCreate, constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.USER_RC);
    addColumnToCreate(columnsToCreate, constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.RC_SEQUENCE);
    addColumnToCreate(columnsToCreate, constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.USER_ID);
    addColumnToCreate(columnsToCreate, constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.CALLBACK_NUMBER);
    addColumnToCreate(columnsToCreate, constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.CALLBACK_DATE);
    addColumnToCreate(columnsToCreate, constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.RETRY_NUMBER);
    addColumnToCreate(columnsToCreate, constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.RETRY_HISTORY);
    addColumnToCreate(columnsToCreate, constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.CB_HISTORY);
    addColumnToCreate(columnsToCreate, constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.CALL_PROCESS);
    addColumnToCreate(columnsToCreate, constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.RETRY_DATE);
    addColumnToCreate(columnsToCreate, constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.FETCHED_DATA_FLAG);
    addColumnToCreate(columnsToCreate, constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.DNC_NUMBERS);
    addColumnToCreate(columnsToCreate, constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.PRIORITY);
    addColumnToCreate(columnsToCreate, constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.ATTEMPTS);
    addColumnToCreate(columnsToCreate, constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.UPDATE_DATE);
    addColumnToCreate(columnsToCreate, constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.RESPONSE_VALUES);
    addColumnToCreate(columnsToCreate, constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.CLOSE_REASON);
    $modalInstance.close({
      delayedAction : delayedAction,
      tableToCreate : vm.table.tableName,
      columnsToCreate : columnsToCreate
    });

  };

  var validateTableName = function(tableName) {
    if (listSourceTables.indexOf(tableName) != -1) {
      alertService.add('danger', locMsg['form.validation.validatetablename']);
      return false;
    } else {
      return true;
    }
  };

  vm.close = function() {
    $modalInstance.close();
  };
});
