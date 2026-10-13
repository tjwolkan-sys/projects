/**
 * Controller to show list of resultCodeScheduleAdvanceds of a campaign
 */
app
    .controller(
        'resultCodeScheduleAdvancedCtrl',
        [
            '$scope',
            '$routeParams',
            '$http',
            'uiGridConstants',
            '$log',
            '$modal',
            'resultCodeScheduleAdvancedFactory',
            'i18nService',
            '$locale',
            '$rootScope',
            '$filter',
            'locMsg',
            '$window',
            'telephonyRcFactory',
            '$q',
            'resultCodeFactory',
            'constants',
            function($scope, $routeParams, $http, uiGridConstants, $log, $modal, resultCodeScheduleAdvancedFactory, i18nService, $locale, $rootScope, $filter, locMsg, $window,
                     telephonyRcFactory, $q, resultCodeFactory, constants) {

              var vm = this;
              var $translate = $filter('translate');
              vm.selectedResultCodeSchedules = {};

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

              $scope.$watch("vm.resultCodeSchedule", function() {
                if (vm.resultCodeSchedule.id !== undefined) {

                  if (vm.resultCodeSchedule.resultCodeScheduleAdvanceds == null) {

                    paginationOptions.filters = {
                      'resultCodeSchedule.id' : [ {
                        term : vm.resultCodeSchedule.id.toString()
                      } ]
                    };
                    vm.getPage();

                  } else {
                    vm.resultCodeScheduleAdvancedGridOptions.data = vm.resultCodeSchedule.resultCodeScheduleAdvanceds;
                  }

                } else {

                  if (vm.resultCodeSchedule.resultCodeScheduleAdvanceds !== undefined) {
                    vm.resultCodeScheduleAdvancedGridOptions.data = vm.resultCodeSchedule.resultCodeScheduleAdvanceds;
                  } else {
                    vm.resultCodeSchedule.resultCodeScheduleAdvanceds = vm.resultCodeScheduleAdvancedGridOptions.data = [];
                  }

                }
              });

              vm.resultCodeScheduleAdvancedGridOptions = {
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
                  displayName : locMsg['resultCodeScheduleAdvanced.dialAttempt'],
                  field : 'dialAttempt',
                  enableHiding : false,
                  headerCellClass : vm.highlightFilteredHeader,
                  type : 'number',
                  filters : [ {
                    condition : uiGridConstants.filter.CONTAINS
                  } ],
                  minWidth : 100
                }, {
                  displayName : locMsg['resultCodeScheduleAdvanced.retryInterval'],
                  field : 'retryInterval',
                  enableHiding : false,
                  headerCellClass : vm.highlightFilteredHeader,
                  type : 'number',
                  filters : [ {
                    condition : uiGridConstants.filter.CONTAINS
                  } ],
                  minWidth : 100
                }, {
                  displayName : locMsg['resultCodeScheduleAdvanced.numberToTry'],
                  field : 'numberToTry',
                  enableHiding : false,
                  headerCellClass : vm.highlightFilteredHeader,
                  type : 'number',
                  filter : {
                    type : uiGridConstants.filter.SELECT,
                    selectOptions : [ {
                      value : 0,
                      label : locMsg['resultCodeScheduleAdvanced.numberToTry.0']
                    }, {
                      value : 1,
                      label : locMsg['resultCodeScheduleAdvanced.numberToTry.1']
                    }, {
                      value : 2,
                      label : locMsg['resultCodeScheduleAdvanced.numberToTry.2']
                    } ]
                  },
                  cellTemplate : '<div class="ui-grid-cell-contents">{{"resultCodeScheduleAdvanced.numberToTry." + row.entity.numberToTry | i18n}}</div>',
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
                vm.resultCodeScheduleAdvancedGridOptions.enableFiltering = !vm.resultCodeScheduleAdvancedGridOptions.enableFiltering;
                vm.gridApi.core.notifyDataChange(uiGridConstants.dataChange.COLUMN);
              };

              vm.resultCodeScheduleAdvancedGridOptions.onRegisterApi = function(gridApi) {
                vm.gridApi = gridApi;
                gridApi.selection.on.rowSelectionChanged($scope, function(row) {
                  vm.selectedResultCodeSchedules = vm.gridApi.selection.getSelectedRows();
                });

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

                resultCodeScheduleAdvancedFactory.getAll(paginationOptions.page, paginationOptions.sort, paginationOptions.filters).then(function(data) {

                  vm.resultCodeScheduleAdvancedGridOptions.totalItems = data.totalItems;
                  vm.resultCodeScheduleAdvancedGridOptions.data = data.data;
                  vm.resultCodeSchedule.resultCodeScheduleAdvanceds = data.data;
                  vm.resultCodeScheduleInitial.resultCodeScheduleAdvanceds = angular.copy(data.data);

                });

              };

              vm.open = function(size, item) {

                vm.item = item;
                item.isNew = (item.isNew == null ? (item.id == null ? true : false) : item.isNew);
                if(item.isNew)
                  item.dialAttempt = vm.getDefaultDialAttempt();

                var modalInstance = $modal.open({
                  animation : true,
                  backdrop : 'static',
                  templateUrl : 'pages/resultCodeScheduleAdvancedForm.html',
                  controller : 'resultCodeScheduleAdvancedFormCtrl as rca',
                  size : size,
                  resolve : {
                    item : function() {
                      return vm.item;
                    },
                    campaign : function() {
                      return vm.campaign;
                    },
                    resultCodeSchedules : function() {
                      return vm.resultCodeScheduleAdvancedGridOptions.data;
                    }
                  }
                });

                modalInstance.result.then(function(result) {
                  if (vm.item.isNew) {
                    vm.resultCodeScheduleAdvancedGridOptions.data.unshift(result);
                  } else {
                    angular.merge(vm.item, result);
                  }
                }, function() {
                  $log.info('Modal dismissed at: ' + new Date());
                });

              };

              vm.remove = function() {
                vm.gridApi.selection.getSelectedRows().forEach(function(item, i, arr) {
                  var index = vm.resultCodeScheduleAdvancedGridOptions.data.indexOf(item);
                  if (item.id == null) {
                    vm.resultCodeScheduleAdvancedGridOptions.data.splice(index, 1);
                  } else {
                    item.deleted = true;
                    item.changed = true;
                  }
                  vm.selectedResultCodeSchedules = null;
                });
              };

              vm.getDefaultDialAttempt = function() {
                var resultCodeScheduleWithMaxDialAttempt = _.max(_.filter(vm.resultCodeScheduleAdvancedGridOptions.data, {
                  deleted : false
                }), function(o) {
                  return o.dialAttempt;
                });
                return _.isObject(resultCodeScheduleWithMaxDialAttempt) ? resultCodeScheduleWithMaxDialAttempt.dialAttempt + 1 : 0;
              };

              vm.newResultCodeScheduleAdvanced = function() {
                return resultCodeScheduleAdvancedFactory.createNew();
              };

            } ]);
