/**
 * Controller to show list of phoneNumbers of a campaign
 */
app
    .controller(
        'phoneNumberColumnCtrl',
        [
            '$scope',
            '$routeParams',
            '$http',
            'uiGridConstants',
            '$log',
            '$modal',
            'phoneNumberColumnFactory',
            'i18nService',
            '$locale',
            '$rootScope',
            '$filter',
            'locMsg',
            '$window',
            'dbTableService',
            'constants',
            '$q',
            function($scope, $routeParams, $http, uiGridConstants, $log, $modal, phoneNumberColumnFactory, i18nService, $locale, $rootScope, $filter, locMsg, $window,
                     dbTableService, constants, $q) {

              var vm = this;
              vm.selectedPhoneNumberColumn = null;

              vm.setEnabled = function(enabled) {
                setEnabledInGrid(vm.gridApi.selection.getSelectedRows(), enabled);
              };

              vm.highlightFilteredHeader = function(row, rowRenderIndex, col, colRenderIndex) {
                if (col.filters[0].term) {
                  return 'header-filtered';
                } else {
                  return '';
                }
              };

              var paginationOptions = {
                page : {},
                sort : {},
                filters : {}
              };

              $scope.$watch("vm.campaign", function() {
                if (vm.campaign.id !== undefined) {
                  paginationOptions.filters = {
                    'campaign.id' : [ {
                      term : vm.campaign.id.toString()
                    } ]
                  };
                  vm.getPage();
                } else {
                  if (!vm.campaign.phoneNumberColumns) {
                    vm.campaign.phoneNumberColumns = vm.phoneNumberColumnGridOptions.data = [];
                  } else {
                    vm.phoneNumberColumnGridOptions.data = vm.campaign.phoneNumberColumns;
                  }
                }
              });

              vm.phoneNumberColumnGridOptions = {
                enableRowSelection : true,
                enableSelectAll : true,
                enableFiltering : false,
                enableFullRowSelection : true,
                modifierKeysToMultiSelect : true,
                enableGridMenu : true,
                i18n : 'en',
                columnDefs : [ {
                  displayName : locMsg['label.id'],
                  field : 'id',
                  enableHiding : true,
                  visible : false,
                  headerCellClass : vm.highlightFilteredHeader,
                  type : 'number',
                  filters : [ {
                    condition : uiGridConstants.filter.CONTAINS
                  } ],
                  minWidth : 50
                }, {
                  displayName : locMsg['phoneNumberColumn.colNumber'],
                  field : 'colNumber',
                  enableHiding : false,
                  headerCellClass : vm.highlightFilteredHeader,
                  filters : [ {
                    condition : uiGridConstants.filter.CONTAINS
                  } ],
                  minWidth : 100
                }, {
                  displayName : locMsg['phoneNumberColumn.colCallerId'],
                  field : 'colCallerId',
                  enableHiding : false,
                  headerCellClass : vm.highlightFilteredHeader,
                  filters : [ {
                    condition : uiGridConstants.filter.CONTAINS
                  } ],
                  minWidth : 100
                }, {
                  displayName : locMsg['phoneNumberColumn.colCallFromTime'],
                  field : 'colCallFromTime',
                  enableHiding : false,
                  headerCellClass : vm.highlightFilteredHeader,
                  filters : [ {
                    condition : uiGridConstants.filter.CONTAINS
                  } ],
                  minWidth : 100
                }, {
                  displayName : locMsg['phoneNumberColumn.colCallTillTime'],
                  field : 'colCallTillTime',
                  enableHiding : false,
                  headerCellClass : vm.highlightFilteredHeader,
                  filters : [ {
                    condition : uiGridConstants.filter.CONTAINS
                  } ],
                  minWidth : 100
                }, {
                  displayName : locMsg['phoneNumberColumn.colTimezoneOffset'],
                  field : 'colTimezoneOffset',
                  enableHiding : false,
                  headerCellClass : vm.highlightFilteredHeader,
                  filters : [ {
                    condition : uiGridConstants.filter.CONTAINS
                  } ],
                  minWidth : 100
                }, {
                  displayName : locMsg['phoneNumberColumn.colSort'],
                  field : 'colSort',
                  enableHiding : false,
                  headerCellClass : vm.highlightFilteredHeader,
                  type : 'number',
                  filters : [ {
                    condition : uiGridConstants.filter.CONTAINS
                  } ],
                  minWidth : 100
                }, {
                  displayName : locMsg['label.enabled'],
                  field : 'enabled',
                  enableHiding : false,
                  headerCellClass : vm.highlightFilteredHeader,
                  filter : {
                    type : uiGridConstants.filter.SELECT,
                    selectOptions : [ {
                      value : '',
                      label : locMsg['input.option.select']
                    }, {
                      value : true,
                      label : locMsg['label.true']
                    }, {
                      value : false,
                      label : locMsg['label.false']
                    } ]
                  },
                  filterHeaderTemplate :
                      '<div class="ui-grid-filter-container" ng-repeat="colFilter in col.filters">'
                      + '<div ng-if="colFilter.type === \'select\'" class="ng-scope">'
                      + '<select class="ui-grid-filter-select ui-grid-filter-input-0 ng-touched" ng-model="colFilter.term" ng-init="colFilter.term = colFilter.term == null ? \'\' : colFilter.term" ng-attr-placeholder="{{colFilter.placeholder || aria.defaultFilterLabel}}" ng-options="option.value as option.label for option in colFilter.selectOptions">'
                      + '</select>'
                      + '</div>'
                      + '</div>',
                  cellTemplate : '<input type="checkbox" ng-model="row.entity.enabled" ng-disabled="true"></input>',
                  minWidth : 50
                } ]
              };

              i18nService.setCurrentLang(locMsg['lang.id']);

              vm.toggleFiltering = function() {
                vm.phoneNumberColumnGridOptions.enableFiltering = !vm.phoneNumberColumnGridOptions.enableFiltering;
                vm.gridApi.core.notifyDataChange(uiGridConstants.dataChange.COLUMN);
              };

              vm.phoneNumberColumnGridOptions.onRegisterApi = function(gridApi) {
                vm.gridApi = gridApi;

                var onSelect = function(row) {
                  vm.selectedPhoneNumberColumn = vm.gridApi.selection.getSelectedRows();
                };

                gridApi.selection.on.rowSelectionChanged($scope, onSelect);
                gridApi.selection.on.rowSelectionChangedBatch($scope, onSelect);

                gridApi.core.on.sortChanged($scope, function(grid, sortColumns) {
                  if (sortColumns.length == 0) {
                    paginationOptions.sort = {};
                  } else {
                    paginationOptions.sort['field'] = sortColumns[0].field;
                    paginationOptions.sort['direction'] = sortColumns[0].sort.direction;
                  }
                });

                gridApi.core.on.filterChanged($scope, function() {

                  $.each(this.grid.columns, function(indexColumn, valueColumn) {

                    if (valueColumn.field != 'selectionRowHeaderCol') {

                      var filters = [];

                      $.each(valueColumn.filters, function(indexFilter, valueFilter) {
                        if (valueFilter.hasOwnProperty('term') && valueFilter.term !== null && valueFilter.term !== "") {
                          var filter = {};
                          filter['term'] = valueFilter.term;
                          if (valueFilter.hasOwnProperty('condition')) {
                            filter['condition'] = valueFilter.condition;
                          }
                          filters.push(filter);
                        }
                      });

                      if (filters.length > 0) {
                        paginationOptions.filters[valueColumn.field] = filters;
                      } else {
                        if (paginationOptions.filters.hasOwnProperty(valueColumn.field)) {
                          delete paginationOptions.filters[valueColumn.field];
                        }
                      }

                    }

                  });

                });

                gridApi.core.on.columnVisibilityChanged($scope, function(changedColumn) {
                  $scope.columnChanged = {
                    name : changedColumn.colDef.name,
                    visible : changedColumn.colDef.visible
                  };
                });

              };

              vm.getPage = function() {

                phoneNumberColumnFactory.getAll(paginationOptions.page, paginationOptions.sort, paginationOptions.filters).then(function(data) {
                  var phoneNumberColumns;
                  if (vm.campaign.phoneNumberColumns) {
                    phoneNumberColumns = data.data.concat(vm.campaign.phoneNumberColumns);
                  } else {
                    phoneNumberColumns = data.data;
                  }
                  vm.phoneNumberColumnGridOptions.totalItems = data.totalItems;
                  vm.phoneNumberColumnGridOptions.data = phoneNumberColumns;
                  vm.campaign.phoneNumberColumns = phoneNumberColumns;
                  vm.campaignInitial.phoneNumberColumns = angular.copy(phoneNumberColumns);
                });

              };

              vm.open = function(size, item) {

                vm.item = item;
                item.isNew = (item.isNew == null ? (item.id == null ? true : false) : item.isNew);
                if (item.isNew)
                  item.colSort = vm.getDefaultSortForColumn();

                var modalInstance = $modal.open({
                  animation : true,
                  backdrop : 'static',
                  templateUrl : 'pages/phoneNumberColumnForm.html',
                  controller : 'phoneNumberColumnFormCtrl as phoneNumberColumn',
                  size : size,
                  resolve : {
                    campaign : function() {
                      return vm.campaign;
                    },
                    listSourceColumns : function() {
                      if (vm.selectedPhoneNumberColumn && vm.selectedPhoneNumberColumn.length > 0 && !vm.item.isNew) {
                        return vm.excludeSelectedNumbers(vm.listSourceColumns).concat([ {
                          fieldName : vm.selectedPhoneNumberColumn[0].colNumber
                        }, {
                          fieldName : vm.selectedPhoneNumberColumn[0].colCallerId
                        }, {
                          fieldName : vm.selectedPhoneNumberColumn[0].colCallFromTime
                        }, {
                          fieldName : vm.selectedPhoneNumberColumn[0].colCallTillTime
                        }, {
                          fieldName : vm.selectedPhoneNumberColumn[0].colTimezoneOffset
                        } ]).uniqueByField("fieldName");
                      } else {
                        return vm.excludeSelectedNumbers(vm.listSourceColumns);
                      }
                    },
                    item : function() {
                      return vm.item;
                    }
                  }
                });

                modalInstance.result.then(function(result) {
                  if (vm.item.isNew) {
                    result.isNew = false;
                    vm.phoneNumberColumnGridOptions.data.unshift(result);
                  } else {
                    angular.merge(vm.item, result);
                  }
                }, function() {
                  $log.info('Modal dismissed at: ' + new Date());
                });
              };

              vm.remove = function() {
                vm.gridApi.selection.getSelectedRows().forEach(function(item, i, arr) {
                  var index = vm.phoneNumberColumnGridOptions.data.indexOf(item);
                  if (item.id == null) {
                    if (vm.phoneNumberColumnGridOptions.data[index].delayedAction) {
                      var delayedActionIndex = vm.delayedCreateTableActions.createDefaultPhoneNumbers.indexOf(vm.phoneNumberColumnGridOptions.data[index].delayedAction);
                      vm.delayedCreateTableActions.createDefaultPhoneNumbers.splice(delayedActionIndex, 1);
                    }
                    vm.phoneNumberColumnGridOptions.data.splice(index, 1);
                  } else {
                    item.deleted = true;
                    item.changed = true;
                  }
                  vm.selectedPhoneNumberColumn = null;
                });
              };

              vm.newPhoneNumberColumn = function() {
                return phoneNumberColumnFactory.createNew();
              };

              vm.createDefault = function() {

                var colCallerIdField = constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.CALLER_ID + '_' + vm.getDefaultNumberForColumn('colCallerId', constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.CALLER_ID);
                var colNumberFieldName = constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.PHONE_NUMBER + '_' + vm.getDefaultNumberForColumn('colNumber', constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.PHONE_NUMBER);
                var colCallFromTimeField = constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.CALL_FROM_TIME + '_' + vm.getDefaultNumberForColumn('colCallFromTime', constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.CALL_FROM_TIME);
                var colCallTillTimeField = constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.CALL_TILL_TIME + '_' + vm.getDefaultNumberForColumn('colCallTillTime', constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.CALL_TILL_TIME);
                var colTimezoneOffsetField = constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.TIMEZONE_OFFSET + '_' + vm.getDefaultNumberForColumn('colTimezoneOffset', constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.TIMEZONE_OFFSET);

                var delayedAction = function() {
                  var defer = $q.defer();
                  dbTableService.addDefaultColumnsForPhoneNumber(colCallerIdField, colNumberFieldName, colCallFromTimeField, colCallTillTimeField, colTimezoneOffsetField,
                      vm.campaign).then(function(result) {
                    defer.resolve(result);
                  }, function(reason) {
                    defer.reject(reason);
                  });
                  return defer.promise;
                };

                var colSortField = vm.getDefaultSortForColumn();
                vm.listSourceColumnsToCreate.push({
                  fieldName : colNumberFieldName
                });
                vm.listSourceColumnsToCreate.push({
                  fieldName : colCallerIdField
                });
                vm.listSourceColumnsToCreate.push({
                  fieldName : colCallFromTimeField
                });
                vm.listSourceColumnsToCreate.push({
                  fieldName : colCallTillTimeField
                });
                vm.listSourceColumnsToCreate.push({
                  fieldName : colTimezoneOffsetField
                });
                vm.delayedCreateTableActions.createDefaultPhoneNumbers.push(delayedAction);
                vm.phoneNumberColumnGridOptions.data.unshift({
                  colNumber : colNumberFieldName,
                  colCallerId : colCallerIdField,
                  colCallFromTime : colCallFromTimeField,
                  colCallTillTime : colCallTillTimeField,
                  colTimezoneOffset : colTimezoneOffsetField,
                  colSort : colSortField,
                  deleted : false,
                  enabled : true,
                  changed : true,
                  isNew : false,
                  delayedAction : delayedAction
                });
              };

              vm.getDefaultNumberForColumn = function(columnName, prefixToSearch) {
                var existingColumns = [];
                vm.phoneNumberColumnGridOptions.data.forEach(function (element, index, array) {
                  if (element.deleted != true && element[columnName])
                    existingColumns.push(element[columnName]);
                });

                vm.listSourceColumns.forEach(function (element, index, array) {
                  if (element.fieldName.indexOf(prefixToSearch) == 0)
                    existingColumns.push(element.fieldName);
                });

                var numbers = [];
                existingColumns.forEach(function (element, index, array) {
                  var threeDigitNumber = Number(element.slice(-3));
                  var twoDigitNumber = Number(element.slice(-2));
                  var number = Number(element.slice(-1));
                  numbers.push(_.isNaN(threeDigitNumber) ? (_.isNaN(twoDigitNumber) ? number : twoDigitNumber) : threeDigitNumber);
                });

                if (numbers.length < 1)
                  return 1;

                for (var possibleNumber = 1; possibleNumber <= numbers.length + 1; possibleNumber++)
                  if (numbers.indexOf(possibleNumber) == -1)
                    return possibleNumber;
              };

              vm.getDefaultSortForColumn = function() {
                var phoneNumberColumnWithMaxSort = _.max(_.filter(vm.phoneNumberColumnGridOptions.data, {
                  deleted : false
                }), function(o) {
                  return o.colSort;
                });
                return _.isObject(phoneNumberColumnWithMaxSort) ? phoneNumberColumnWithMaxSort.colSort + 1 : 0;
              };

              vm.excludeSelectedNumbers = function(allAvailableColumns) {
                var listSourceColumnsCopy = [];
                Object.assign(listSourceColumnsCopy, allAvailableColumns);
                for (var j = 0; j < vm.campaign.phoneNumberColumns.length; j++) {
                  for (var i = 0; i < listSourceColumnsCopy.length; i++) {
                    if (listSourceColumnsCopy[i].fieldName === vm.campaign.phoneNumberColumns[j].colNumber
                        && !vm.campaign.phoneNumberColumns[j].deleted) {
                      listSourceColumnsCopy.splice(i, 1);
                      break;
                    }
                  }
                }
                return listSourceColumnsCopy;
              }

            } ]);
