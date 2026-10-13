/**
 * Controller to show filterColumns of a campaign
 */
app
    .controller(
        'filterColumnCtrl',
        [
            '$scope',
            '$routeParams',
            '$http',
            'uiGridConstants',
            '$log',
            '$modal',
            'i18nService',
            '$locale',
            '$rootScope',
            '$filter',
            'locMsg',
            '$window',
            'filterFactory',
            'constants',
            function($scope, $routeParams, $http, uiGridConstants, $log, $modal, i18nService, $locale, $rootScope, $filter, locMsg, $window, filterFactory, constants) {

              var vm = this;
              var $translate = $filter('translate');

              vm.selectedFilters = null;

              var compareFields = function(a, b) {
                if (a && b) {
                  if (a.fieldName < b.fieldName)
                    return -1;
                  if (a.fieldName > b.fieldName)
                    return 1;
                }
                return 0;
              };

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
                  vm.campaign.filters = vm.filterColumnGridOptions.data = [];
                  vm.campaignInitial.filters = [];
                }
              });

              vm.filterColumnGridOptions = {
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
                  displayName : locMsg['filter.type'],
                  field : 'type',
                  enableHiding : false,
                  headerCellClass : vm.highlightFilteredHeader,
                  filter : {
                    type : uiGridConstants.filter.SELECT,
                    selectOptions : [ {
                      value : '',
                      label : locMsg['input.option.select']
                    }, {
                      value : 0,
                      label : locMsg['filter.type.0']
                    }, {
                      value : 1,
                      label : locMsg['filter.type.1']
                    } ]
                  },
                  filterHeaderTemplate :
                      '<div class="ui-grid-filter-container" ng-repeat="colFilter in col.filters">'
                      + '<div ng-if="colFilter.type === \'select\'" class="ng-scope">'
                      + '<select class="ui-grid-filter-select ui-grid-filter-input-0 ng-touched" ng-model="colFilter.term" ng-init="colFilter.term = colFilter.term == null ? \'\' : colFilter.term" ng-attr-placeholder="{{colFilter.placeholder || aria.defaultFilterLabel}}" ng-options="option.value as option.label for option in colFilter.selectOptions">'
                      + '</select>'
                      + '</div>'
                      + '</div>',
                  cellTemplate : '<div class="ui-grid-cell-contents">{{"filter.type." + row.entity.type | i18n}}</div>',
                  minWidth : 100
                }, {
                  displayName : locMsg['filter.targetTable'],
                  field : 'targetTable',
                  enableHiding : false,
                  headerCellClass : vm.highlightFilteredHeader,
                  filter : {
                    type : uiGridConstants.filter.SELECT,
                    selectOptions : [ {
                      value : '',
                      label : locMsg['input.option.select']
                    }, {
                      value : 0,
                      label : locMsg['filter.targetTable.0']
                    }, {
                      value : 1,
                      label : locMsg['filter.targetTable.1']
                    } ]
                  },
                  filterHeaderTemplate :
                      '<div class="ui-grid-filter-container" ng-repeat="colFilter in col.filters">'
                      + '<div ng-if="colFilter.type === \'select\'" class="ng-scope">'
                      + '<select class="ui-grid-filter-select ui-grid-filter-input-0 ng-touched" ng-model="colFilter.term" ng-init="colFilter.term = colFilter.term == null ? \'\' : colFilter.term" ng-attr-placeholder="{{colFilter.placeholder || aria.defaultFilterLabel}}" ng-options="option.value as option.label for option in colFilter.selectOptions">'
                      + '</select>'
                      + '</div>'
                      + '</div>',
                  cellTemplate : '<div class="ui-grid-cell-contents"><div ng-show="row.entity.type == 0">{{"filter.targetTable." + row.entity.targetTable | i18n}}</div></div>',
                  minWidth : 100
                }, {
                  displayName : locMsg['filter.field'],
                  field : 'field.fieldName',
                  enableHiding : false,
                  headerCellClass : vm.highlightFilteredHeader,
                  filters : [ {
                    condition : uiGridConstants.filter.CONTAINS
                  } ],
                  minWidth : 100
                }, {
                  displayName : locMsg['filter.condition'],
                  field : 'condition',
                  enableHiding : false,
                  headerCellClass : vm.highlightFilteredHeader,
                  filter : {
                    type : uiGridConstants.filter.SELECT,
                    selectOptions : [ {
                      value : '',
                      label : locMsg['input.option.select']
                    }, {
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
                    } ]
                  },
                  filterHeaderTemplate :
                      '<div class="ui-grid-filter-container" ng-repeat="colFilter in col.filters">'
                      + '<div ng-if="colFilter.type === \'select\'" class="ng-scope">'
                      + '<select class="ui-grid-filter-select ui-grid-filter-input-0 ng-touched" ng-model="colFilter.term" ng-init="colFilter.term = colFilter.term == null ? \'\' : colFilter.term" ng-attr-placeholder="{{colFilter.placeholder || aria.defaultFilterLabel}}" ng-options="option.value as option.label for option in colFilter.selectOptions">'
                      + '</select>'
                      + '</div>'
                      + '</div>',
                  cellTemplate : '<div class="ui-grid-cell-contents"><div ng-show="row.entity.type == 0">{{"filter." + row.entity.condition | i18n}}</div></div>',
                  minWidth : 100
                }, {
                  displayName : locMsg['filter.value'],
                  field : 'value',
                  enableHiding : false,
                  headerCellClass : vm.highlightFilteredHeader,
                  filters : [ {
                    condition : uiGridConstants.filter.CONTAINS
                  } ],
                  cellTemplate : '<div class="ui-grid-cell-contents ng-binding ng-scope" ng-if="row.entity.field.simpleDbType != \'DATE\'">{{row.entity.value}}</div> <div class="ui-grid-cell-contents ng-binding ng-scope" ng-if="row.entity.field.simpleDbType == \'DATE\'">{{row.entity.value | date:\'yyyy-MM-dd HH:mm\'}}</div>',
                  minWidth : 100
                }, {
                  displayName : locMsg['campaign.technical'],
                  field : 'technical',
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
                  cellTemplate : '<input type="checkbox" ng-model="row.entity.technical" ng-disabled="true"></input>',
                  minWidth : 50
                }, {
                  displayName : locMsg['filter.segmentation'],
                  field : 'segmentation',
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
                  sort: {
                    direction: uiGridConstants.ASC,
                    priority: 0
                  },
                  cellTemplate : '<input type="checkbox" ng-model="row.entity.segmentation" ng-disabled="true"></input>',
                  minWidth : 50
                }, {
                  displayName : locMsg['filter.ratio'],
                  field : 'ratio',
                  enableHiding : false,
                  headerCellClass : vm.highlightFilteredHeader,
                  type : 'number',
                  filters : [ {
                    condition : uiGridConstants.filter.EXACT
                  } ],
                  cellTemplate: '<div class="ui-grid-cell-contents">{{row.entity.ratio}}</div>',
                  minWidth : 50
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
                }]
              };

              i18nService.setCurrentLang(locMsg['lang.id']);

              vm.toggleFiltering = function() {
                vm.filterColumnGridOptions.enableFiltering = !vm.filterColumnGridOptions.enableFiltering;
                vm.gridApi.core.notifyDataChange(uiGridConstants.dataChange.COLUMN);
              };

              vm.filterColumnGridOptions.onRegisterApi = function(gridApi) {
                vm.gridApi = gridApi;
                var onSelect = function(row) {
                  vm.selectedFilters = vm.gridApi.selection.getSelectedRows();
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

                filterFactory.getAll(paginationOptions.page, paginationOptions.sort, paginationOptions.filters).then(function(data) {

                  vm.filterColumnGridOptions.totalItems = data.totalItems;
                  vm.filterColumnGridOptions.data = data.data;
                  vm.campaign.filters = data.data;
                  vm.campaignInitial.filters = angular.copy(data.data);

                });

              };

              vm.open = function(size, item) {

                vm.item = item;
                item.isNew = (item.isNew == null ? (item.id == null ? true : false) : item.isNew);
                if (vm.campaign.dbListTable === vm.campaign.dbResultTable)
                  item.targetTable = constants.FILTER_TARGET_TABLE.SOURCE_TABLE;
                var modalInstance = $modal.open({
                  animation : true,
                  backdrop : 'static',
                  templateUrl : 'pages/filterColumnForm.html',
                  controller : 'filterColumnFormCtrl as filterColumn',
                  size : size,
                  resolve : {
                    campaign : function() {
                      return vm.campaign;
                    },
                    item : function() {
                      return vm.item;
                    },
                    listSourceColumns : function() {
                      if (vm.selectedFilters && vm.selectedFilters[0] && vm.selectedFilters[0].field
                          && vm.selectedFilters[0].targetTable === constants.FILTER_TARGET_TABLE.SOURCE_TABLE) {
                        return vm.listSourceColumns.concat([{fieldName : vm.selectedFilters[0].field.fieldName}]).uniqueByField("fieldName").sort(compareFields);
                      } else {
                        return vm.listSourceColumns.sort(compareFields);
                      }
                    },
                    callResultColumns : function() {
                      if (vm.selectedFilters && vm.selectedFilters[0] && vm.selectedFilters[0].field
                          && vm.selectedFilters[0].targetTable === constants.FILTER_TARGET_TABLE.RESULT_TABLE) {
                        return vm.callResultColumns.concat([{fieldName : vm.selectedFilters[0].field.fieldName}]).uniqueByField("fieldName").sort(compareFields);
                      } else {
                        return vm.callResultColumns.sort(compareFields);
                      }
                    }
                  }
                });

                modalInstance.result.then(function(result) {
                  if (vm.item.isNew) {
                    result.isNew = false;
                    vm.filterColumnGridOptions.data.unshift(result);
                  } else {
                    angular.merge(vm.item, result);
                  }
                }, function() {
                  $log.info('Modal dismissed at: ' + new Date());
                });
              };

              vm.remove = function() {
                vm.gridApi.selection.getSelectedRows().forEach(function(item, i, arr) {
                  var index = vm.filterColumnGridOptions.data.indexOf(item);
                  if (item.id == null) {
                    vm.filterColumnGridOptions.data.splice(index, 1);
                  } else {
                    item.deleted = true;
                    item.changed = true;
                  }
                  vm.selectedFilters = null;
                });
              };

              vm.test = function() {
                if (vm.campaign.dbListTable != null) {
                  $modal.open({
                    animation : true,
                    templateUrl : 'pages/filterColumnTestForm.html',
                    controller : 'filterColumnTestFormCtrl as filterColumnTest',
                    size : 'lg',
                    windowClass : 'wide-modal-window',
                    resolve : {
                      campaign : function() {
                        return vm.campaign;
                      },
                      listSourceColumns : function() {
                        var clonedListSourceColumnsForTestForm = vm.listSourceColumns.slice(0);
                        var indexColumn = {fieldType: "BIGINT", fieldName: "Index", simpleDbType: "INTEGER"};
                        clonedListSourceColumnsForTestForm.unshift(indexColumn);
                        return clonedListSourceColumnsForTestForm;
                      }
                    }
                  });
                }
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

              vm.newFilterColumn = function() {
                return filterFactory.createNew();
              };

            } ]);
