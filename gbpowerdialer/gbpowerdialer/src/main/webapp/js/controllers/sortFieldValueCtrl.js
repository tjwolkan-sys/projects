/**
 * Controller to show list of sortFields of a campaign
 */
app
    .controller(
        'sortFieldValueCtrl',
        [
            '$scope',
            '$routeParams',
            '$http',
            'uiGridConstants',
            '$log',
            '$modal',
            'sortFieldValueFactory',
            'i18nService',
            '$locale',
            '$rootScope',
            '$filter',
            'locMsg',
            '$window',
            'dbTableService',
            'constants',
            '$q',
            'alertService',
            function($scope, $routeParams, $http, uiGridConstants, $log, $modal, sortFieldValueFactory, i18nService, $locale, $rootScope, $filter, locMsg, $window,
                     dbTableService, constants, $q, alertService) {

              var vm = this;
              vm.selectedSortFieldValue = null;

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

              $scope.$watch("vm.sortField", function () {
                if (vm.sortField.id !== undefined) {
                  if (vm.sortField.values == null) {
                    paginationOptions.filters = {
                      'sortField.id': [{
                        term: vm.sortField.id.toString()
                      }]
                    };
                    vm.getPage();
                  } else {
                    vm.sortFieldValueGridOptions.data = vm.sortField.values;
                  }
                } else {
                  if (!vm.sortField.values) {
                    vm.sortField.values = vm.sortFieldValueGridOptions.data = [];
                  } else {
                    vm.sortFieldValueGridOptions.data = vm.sortField.values;
                  }
                }
              });

              vm.sortFieldValueGridOptions = {
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
                  displayName : locMsg['sortFieldValue.value'],
                  field : 'value',
                  enableHiding : false,
                  headerCellClass : vm.highlightFilteredHeader,
                  filters : [ {
                    condition : uiGridConstants.filter.CONTAINS
                  } ],
                  minWidth : 100
                }, {
                  displayName : locMsg['sortFieldValue.priority'],
                  field : 'priority',
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
                      value : true,
                      label : locMsg['label.true']
                    }, {
                      value : false,
                      label : locMsg['label.false']
                    } ]
                  },
                  cellTemplate : '<input type="checkbox" ng-model="row.entity.enabled" ng-disabled="true"></input>',
                  minWidth : 50
                } ]
              };

              i18nService.setCurrentLang(locMsg['lang.id']);

              vm.toggleFiltering = function() {
                vm.sortFieldValueGridOptions.enableFiltering = !vm.sortFieldValueGridOptions.enableFiltering;
                vm.gridApi.core.notifyDataChange(uiGridConstants.dataChange.COLUMN);
              };

              vm.sortFieldValueGridOptions.onRegisterApi = function(gridApi) {
                vm.gridApi = gridApi;
                var onSelect = function(row) {
                  vm.selectedSortFieldValue = vm.gridApi.selection.getSelectedRows();
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

                sortFieldValueFactory.getAll(paginationOptions.page, paginationOptions.sort, paginationOptions.filters).then(function(data) {
                  vm.sortFieldValueGridOptions.totalItems = data.totalItems;
                  vm.sortFieldValueGridOptions.data = data.data;
                  vm.sortField.values = data.data;
                  vm.sortFieldInitial.values = angular.copy(data.data);
                  vm.campaignInitial.sortFields.filter(field => field.id === vm.sortField.id)
                    .forEach(field => field.values = vm.sortFieldInitial.values);
                });

              };

              vm.open = function(size, item) {

                vm.item = item;
                item.isNew = (item.isNew == null ? (item.id == null ? true : false) : item.isNew);
                if (item.isNew)
                  item.priority = vm.getDefaultPriority();

                const modalInstance = $modal.open({
                  animation: true,
                  backdrop: 'static',
                  templateUrl: 'pages/sortFieldValueForm.html',
                  controller: 'sortFieldValueFormCtrl as sortFieldValue',
                  size: size,
                  resolve: {
                    campaign: function () {
                      return vm.campaign;
                    },
                    item: function () {
                      return vm.item;
                    },
                    sortField: function () {
                      return vm.sortField;
                    },
                    locMsg: function() {
                      return locMsg;
                    },
                    alertService: function() {
                      return alertService;
                    }
                  }
                });

                modalInstance.result.then(function(result) {
                  if (vm.item.isNew) {
                    result.isNew = false;
                    vm.sortFieldValueGridOptions.data.unshift(result);
                  } else {
                    angular.merge(vm.item, result);
                  }
                }, function() {
                  $log.info('Modal dismissed at: ' + new Date());
                });
              };

              vm.remove = function() {
                vm.gridApi.selection.getSelectedRows().forEach(function(item, i, arr) {
                  var index = vm.sortFieldValueGridOptions.data.indexOf(item);
                  if (item.id == null) {
                    vm.sortFieldValueGridOptions.data.splice(index, 1);
                  } else {
                    item.deleted = true;
                    item.changed = true;
                  }
                  vm.selectedSortFieldValue = null;
                });
              };

              vm.newSortFieldValue = function() {
                return sortFieldValueFactory.createNew();
              };

              vm.getDefaultPriority = function() {
                const valueWithMaxPriority = _.max(_.filter(vm.sortFieldValueGridOptions.data, {
                  deleted: false
                }), function (o) {
                  return o.priority;
                });
                return _.isObject(valueWithMaxPriority) ? valueWithMaxPriority.priority + 1 : 1;
              };

            } ]);
