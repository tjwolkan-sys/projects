/**
 * Controller to edit a filterColumn
 */
app.controller('filterColumnFormCtrl', function($modalInstance, item, campaign, $log, locMsg, tenantFactory, locationFactory, dncFactory, $rootScope, $q, $scope, uiGridConstants,
                                                listSourceColumns, callResultColumns, $filter, constants, campaignFactory) {
  var vm = this;
  vm.item = angular.copy(item);
  vm.campaign = campaign;

  vm.columnSelect = function() {
    if(vm.item.field.simpleDbType == constants.SIMPLE_DB_TYPES.DATE){
      vm.item.value = undefined;
    }
  };

  vm.onSelectTargetTable = function() {
    vm.item.field = undefined;
    vm.item.condition = undefined;
    vm.item.value = undefined;
  };

  vm.onSelectType = function() {
    vm.item.field = undefined;
    vm.item.condition = undefined;
    vm.item.value = undefined;
    vm.item.targetTable = (vm.item.type === constants.FILTER_TYPE.PREDEFINED && vm.campaign.dbListTable === vm.campaign.dbResultTable)
      ? constants.FILTER_TARGET_TABLE.SOURCE_TABLE
      : undefined;
  };

  vm.getColumns = function() {
    return (vm.item.targetTable === constants.FILTER_TARGET_TABLE.RESULT_TABLE) ? vm.callResultColumns : vm.listSourceColumns;
  };

  vm.filterTypes = [ {
    value : constants.FILTER_TYPE.PREDEFINED,
    label : locMsg['filter.type.0']
  }, {
    value : constants.FILTER_TYPE.FREE,
    label : locMsg['filter.type.1']
  } ];

  vm.filterTargetTables = [ {
    value : constants.FILTER_TARGET_TABLE.SOURCE_TABLE,
    label : locMsg['filter.targetTable.0']
  }, {
    value : constants.FILTER_TARGET_TABLE.RESULT_TABLE,
    label : locMsg['filter.targetTable.1']
  } ];

  vm.filterJoinTypes = [ {
    value : 0,
    label : 'AND'
  }, {
    value : 1,
    label : 'OR'
  } ];

  vm.filterBrackets = [ {
    value : "(",
    label : "("
  }, {
    value : ")",
    label : ")"
  } ];

  vm.filterOperators = [ {
    value : "AND",
    label : "AND"
  }, {
    value : "OR",
    label : "OR"
  } ];

  vm.listSourceColumns = listSourceColumns;
  vm.callResultColumns = callResultColumns;

  vm.filterConditions = [ {
    value : 0,
    label : locMsg['filter.0']
  }, {
    value : 1,
    label : locMsg['filter.1']
  }, {
    value : 2,
    label : locMsg['filter.2']
  }, {
    value : 3,
    label : locMsg['filter.3']
  }, {
    value : 4,
    label : locMsg['filter.4']
  }, {
    value : 5,
    label : locMsg['filter.5']
  }, {
    value : 6,
    label : locMsg['filter.6']
  }, {
    value : 7,
    label : locMsg['filter.7']
  }, {
    value : 8,
    label : locMsg['filter.8']
  }, {
    value : 9,
    label : locMsg['filter.9']
  } ];

  vm.save = function() {
    if (vm.item.type == constants.FILTER_TYPE.PREDEFINED && vm.item.field.simpleDbType == constants.SIMPLE_DB_TYPES.DATE)
      vm.item.value = $filter('date')(vm.item.value, 'yyyy-MM-dd HH:mm');

    if (vm.item.type != constants.FILTER_TYPE.PREDEFINED) {
      vm.item.field = '';
      vm.item.condition = '';
    }
    vm.item.changed = vm.item.changed || !angular.equals(vm.item, item);

    if (!vm.item.technical)
       vm.item.technical = false;

    if (vm.item.type == constants.FILTER_TYPE.FREE) {
      var testItem = {};
      angular.copy(vm.item, testItem);
      testItem.campaign = campaign;
      campaignFactory.filterTest(testItem).then(function() {
        $modalInstance.close(vm.item);
      }, function() {
        $scope.filterColumnForm.filterColumnValue2.$setValidity('validatefilter', false);
      });
    } else {
      $modalInstance.close(vm.item);
    }
  };

  vm.cancel = function() {
    $modalInstance.dismiss('cancel');
  };

});
