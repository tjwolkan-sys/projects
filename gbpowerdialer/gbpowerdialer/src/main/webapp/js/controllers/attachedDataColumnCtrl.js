/**
 * Controller to show list of attachedDatas
 */
app
    .controller(
        'attachedDataColumnCtrl',
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
            'attachedDataFactory',
            'constants',
            function($scope, $routeParams, $http, uiGridConstants, $log, $modal, i18nService, $locale, $rootScope, $filter, locMsg, $window, attachedDataFactory, constants) {

              var vm = this;
              var $translate = $filter('translate');

              vm.selectedAttachedDatas = null;

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
                  vm.campaign.attachedDatas = vm.attachedDataColumnGridOptions.data = [];
                  vm.campaignInitial.attachedDatas = [];
                }
              });

              $scope.$watch(function () {
                return vm.campaign.attachedDataLongTermSave;
              }, function (newVal) {
                if (vm.attachedDataColumnGridOptions.data !== undefined) {
                  let anyRowsAlreadyForSave = vm.attachedDataColumnGridOptions.data.some(function (row) {
                    return row.saveLongTerm === true;
                  });
                  if (!anyRowsAlreadyForSave || newVal === false) {
                    vm.attachedDataColumnGridOptions.data.forEach(function (row) {
                      row.saveLongTerm = newVal;
                    });
                  }
                }
              });

              $scope.$watch(function () {
                return vm.attachedDataColumnGridOptions.data;
              }, function (newData) {
                if (newData !== undefined) {
                  let anyLongTermSave = newData.some(function (row) {
                    return row.saveLongTerm === true;
                  });
                  vm.campaign.attachedDataLongTermSave = anyLongTermSave;
                }
              }, true);

              vm.attachedDataColumnGridOptions = {
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
                  displayName : locMsg['attachedData.field'],
                  field : 'field',
                  enableHiding : false,
                  headerCellClass : vm.highlightFilteredHeader,
                  filters : [ {
                    condition : uiGridConstants.filter.CONTAINS
                  } ],
                  minWidth : 100
                }, {
                  displayName : locMsg['attachedData.fieldName'],
                  field : 'fieldName',
                  enableHiding : false,
                  headerCellClass : vm.highlightFilteredHeader,
                  filters : [ {
                    condition : uiGridConstants.filter.CONTAINS
                  } ],
                  minWidth : 100
                }, {
                  displayName : locMsg['attachedData.popupName'],
                  field : 'popupName',
                  enableHiding : false,
                  headerCellClass : vm.highlightFilteredHeader,
                  filters : [ {
                    condition : uiGridConstants.filter.CONTAINS
                  } ],
                  minWidth : 100
                }, {
                  displayName : locMsg['label.popup'],
                  field : 'popup',
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
                  cellTemplate : '<input type="checkbox" ng-model="row.entity.popup" ng-disabled="true"></input>',
                  minWidth : 50
                }, {
                  displayName : locMsg['label.saveLongTerm'],
                  field : 'saveLongTerm',
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
                  cellTemplate : '<input type="checkbox" ng-model="row.entity.saveLongTerm" ng-disabled="true"></input>',
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
                } ]
              };

              i18nService.setCurrentLang(locMsg['lang.id']);

              vm.toggleFiltering = function() {
                vm.attachedDataColumnGridOptions.enableFiltering = !vm.attachedDataColumnGridOptions.enableFiltering;
                vm.gridApi.core.notifyDataChange(uiGridConstants.dataChange.COLUMN);
              };

              vm.attachedDataColumnGridOptions.onRegisterApi = function(gridApi) {

                vm.gridApi = gridApi;
                var onSelect = function(row) {
                  vm.selectedAttachedDatas = vm.gridApi.selection.getSelectedRows();
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

                attachedDataFactory.getAll(paginationOptions.page, paginationOptions.sort, paginationOptions.filters).then(function(data) {

                  vm.attachedDataColumnGridOptions.totalItems = data.totalItems;
                  vm.attachedDataColumnGridOptions.data = data.data;
                  vm.campaign.attachedDatas = data.data;
                  vm.campaignInitial.attachedDatas = angular.copy(data.data);

                });

              };

              vm.open = function(size, item) {

                vm.item = item;
                item.isNew = (item.isNew == null ? (item.id == null ? true : false) : item.isNew);

                var modalInstance = $modal.open({
                  animation : true,
                  backdrop : 'static',
                  templateUrl : 'pages/attachedDataColumnForm.html',
                  controller : 'attachedDataColumnFormCtrl as attachedDataColumn',
                  size : size,
                  resolve : {
                    campaign : function() {
                      return vm.campaign;
                    },
                    item : function() {
                      return vm.item;
                    },
                    listSourceColumns : function() {
                      return vm.listSourceColumns;
                    }
                  }
                });

                modalInstance.result.then(function(result) {
                  if (vm.item.isNew) {
                    result.isNew = false;
                    vm.attachedDataColumnGridOptions.data.unshift(result);
                  } else {
                    angular.merge(vm.item, result);
                  }
                }, function() {
                  $log.info('Modal dismissed at: ' + new Date());
                });
              };

              vm.remove = function() {
                vm.gridApi.selection.getSelectedRows().forEach(function(item, i, arr) {
                  var index = vm.attachedDataColumnGridOptions.data.indexOf(item);
                  if (item.id == null) {
                    vm.attachedDataColumnGridOptions.data.splice(index, 1);
                  } else {
                    item.deleted = true;
                    item.changed = true;
                  }
                  vm.selectedAttachedDatas = null;
                });
              };

              vm.newAttachedDataColumn = function() {
                return attachedDataFactory.createNew();
              };

            } ]);
