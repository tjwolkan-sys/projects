app.service('dbTableService', function($http, constants) {

  var vm = this;

  vm.checkTableExistence = function (tableName, campaign) {
    return $http.post('campaign/checkTableName', {
      tableName : tableName,
      campaignId : campaign.id,
      dbUsername : campaign.dbUsername,
      dbPassword : campaign.dbPassword,
      connectionString : campaign.dbConnectionString
    });
  };

  vm.createDefaultSourceTable = function(sourceTableOptions, campaign) {
    return $http.post('campaign/sourceTable', {
      campaignId : campaign.id,
      sortStrategy : sourceTableOptions.sortStrategy,
      tableName : sourceTableOptions.tableName,
      fetchDataRandomly : sourceTableOptions.fetchDataRandomly,
      dbUsername : campaign.dbUsername,
      dbPassword : campaign.dbPassword,
      connectionString : campaign.dbConnectionString
    });
  };

  vm.addCustomFieldsToSourceTable = function(campaign, customFields) {
    return $http.put('campaign/sourceTable', {
      campaignId : campaign.id,
      tableName : campaign.dbListTable,
      dbUsername : campaign.dbUsername,
      dbPassword : campaign.dbPassword,
      connectionString : campaign.dbConnectionString,
      customFields : customFields
    });
  };

  vm.addDefaultColumnsForPhoneNumber = function(callerIdColumnName, phoneNumberColumnName, callFromTimeColumnName, callTillTimeColumnName, timezoneOffsetColumnName, campaign) {
    return $http.post('campaign/sourceTable/phoneNumberColumns', {
      callerIdColumnName : callerIdColumnName,
      phoneNumberColumnName : phoneNumberColumnName,
      callFromTimeColumnName : callFromTimeColumnName,
      callTillTimeColumnName : callTillTimeColumnName,
      timezoneOffsetColumnName : timezoneOffsetColumnName,
      campaignId : campaign.id,
      tableName : campaign.dbListTable,
      dbUsername : campaign.dbUsername,
      dbPassword : campaign.dbPassword,
      connectionString : campaign.dbConnectionString
    });
  };

  vm.createDefaultResultTable = function(resultTableOptions, campaign) {
    return $http.post('campaign/resultTable', {
      campaignId : campaign.id,
      tableName : resultTableOptions.tableName,
      dbUsername : campaign.dbUsername,
      dbPassword : campaign.dbPassword,
      connectionString : campaign.dbConnectionString
    })
  };

  vm.addResultColsToSourceTable = function(campaign) {
    return $http.put('campaign/resultTable', {
      campaignId : campaign.id,
      tableName : campaign.dbListTable,
      dbUsername : campaign.dbUsername,
      dbPassword : campaign.dbPassword,
      connectionString : campaign.dbConnectionString
    });
  };

  vm.checkSourceTableForResultColumnsExistence = function(sourceColumns) {
    var defaultResultColumnsArray = [constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.START_DATE,
      constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.ANSWER_DATE, constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.COMPLETE_DATE, constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.CALLED_NUMBER,
      constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.TELEPHONY_RC, constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.USER_RC, constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.RC_SEQUENCE,
      constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.USER_ID, constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.CALLBACK_NUMBER, constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.CALLBACK_DATE,
      constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.RETRY_NUMBER, constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.RETRY_HISTORY, constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.CB_HISTORY,
      constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.CALL_PROCESS, constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.RETRY_DATE,
      constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.DNC_NUMBERS, constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.ATTEMPTS, constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.UPDATE_DATE,
      constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.RESPONSE_VALUES];
    var sourceColumnsNames = sourceColumns.map(function(c){ return c.fieldName; });
    return defaultResultColumnsArray.every(function(v, i) {
      return sourceColumnsNames.indexOf(v) !== -1;
    });
  };

});
