/**
 * Controller to shwo list of resultCodeSchedules of a campaign
 */
app
    .controller(
        'resultCodeScheduleCtrl',
        [
            '$scope',
            '$routeParams',
            '$http',
            'uiGridConstants',
            '$log',
            '$modal',
            'resultCodeScheduleFactory',
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
            function($scope, $routeParams, $http, uiGridConstants, $log, $modal, resultCodeScheduleFactory, i18nService, $locale, $rootScope, $filter, locMsg, $window,
                     telephonyRcFactory, $q, resultCodeFactory, constants) {

              var vm = this;
              var $translate = $filter('translate');
              vm.selectedResultCodeSchedules = null;

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
                  vm.campaign.resultCodeSchedules = vm.resultCodeScheduleGridOptions.data = [];
                }
              });

              vm.resultCodeScheduleGridOptions = {
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
                  displayName : locMsg['resultCodeSchedule.resultCode'],
                  field : 'resultCode.name',
                  enableHiding : false,
                  headerCellClass : vm.highlightFilteredHeader,
                  filters : [ {
                    condition : uiGridConstants.filter.CONTAINS
                  } ],
                  minWidth : 50
                }, {
                  displayName : locMsg['resultCodeSchedule.telephonyResultCode'],
                  field : 'telephonyResultCode.name',
                  enableHiding : false,
                  headerCellClass : vm.highlightFilteredHeader,
                  filters : [ {
                    condition : uiGridConstants.filter.CONTAINS
                  } ],
                  minWidth : 100
                }, {
                  displayName : locMsg['resultCodeSchedule.applyTo'],
                  field : 'applyTo',
                  enableHiding : false,
                  headerCellClass : vm.highlightFilteredHeader,
                  filter : {
                    type : uiGridConstants.filter.SELECT,
                    selectOptions : [ {
                      value : 0,
                      label : locMsg['resultCodeSchedule.applyTo.0']
                    }, {
                      value : 1,
                      label : locMsg['resultCodeSchedule.applyTo.1']
                    }, {
                      value : 2,
                      label : locMsg['resultCodeSchedule.applyTo.2']
                    } ]
                  },
                  cellTemplate : '<div class="ui-grid-cell-contents">{{"resultCodeSchedule.applyTo." + row.entity.applyTo | i18n}}</div>',
                  minWidth : 100
                }, {
                  displayName : locMsg['resultCodeSchedule.retryAmount'],
                  field : 'retryAmount',
                  enableHiding : false,
                  headerCellClass : vm.highlightFilteredHeader,
                  type : 'number',
                  filters : [ {
                    condition : uiGridConstants.filter.CONTAINS
                  } ],
                  minWidth : 100
                }, {
                  displayName : locMsg['resultCodeSchedule.retryInterval'],
                  field : 'retryInterval',
                  enableHiding : false,
                  headerCellClass : vm.highlightFilteredHeader,
                  type : 'number',
                  filters : [ {
                    condition : uiGridConstants.filter.CONTAINS
                  } ],
                  minWidth : 100
                }, {
                  displayName : locMsg['resultCodeSchedule.minScheduleTime'],
                  field : 'minScheduleTime',
                  enableHiding : false,
                  headerCellClass : vm.highlightFilteredHeader,
                  filters : [ {
                    condition : uiGridConstants.filter.CONTAINS
                  } ],
                  cellTemplate : '<div class="ui-grid-cell-contents">{{row.entity.minScheduleTime | date:"HH:mm"}}</div>',
                  minWidth : 100
                }, {
                  displayName : locMsg['resultCodeSchedule.maxScheduleTime'],
                  field : 'maxScheduleTime',
                  enableHiding : false,
                  headerCellClass : vm.highlightFilteredHeader,
                  filters : [ {
                    condition : uiGridConstants.filter.CONTAINS
                  } ],
                  cellTemplate : '<div class="ui-grid-cell-contents">{{row.entity.maxScheduleTime | date:"HH:mm"}}</div>',
                  minWidth : 100
                }, {
                  displayName : locMsg['resultCodeSchedule.macroCycle'],
                  field : 'macroCycle',
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
                  cellTemplate : '<input type="checkbox" ng-model="row.entity.macroCycle" ng-disabled="true"></input>',
                  minWidth : 100
                }, {
                  displayName : locMsg['resultCodeSchedule.advancedIntervalEnabled'],
                  field : 'advancedIntervalEnabled',
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
                  cellTemplate : '<input type="checkbox" ng-model="row.entity.advancedIntervalEnabled" ng-disabled="true"></input>',
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

              vm.resultCodeScheduleListEnabled = function () {
                return vm.campaign.retryStrategy === 5
              };

              vm.toggleFiltering = function() {
                vm.resultCodeScheduleGridOptions.enableFiltering = !vm.resultCodeScheduleGridOptions.enableFiltering;
                vm.gridApi.core.notifyDataChange(uiGridConstants.dataChange.COLUMN);
              };

              vm.resultCodeScheduleGridOptions.onRegisterApi = function(gridApi) {
                vm.gridApi = gridApi;
                var onSelect = function(row) {
                  vm.selectedResultCodeSchedules = vm.gridApi.selection.getSelectedRows();
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

                resultCodeScheduleFactory.getAll(paginationOptions.page, paginationOptions.sort, paginationOptions.filters).then(function(data) {

                  vm.resultCodeScheduleGridOptions.totalItems = data.totalItems;
                  vm.resultCodeScheduleGridOptions.data = data.data;
                  vm.campaign.resultCodeSchedules = data.data;
                  vm.campaignInitial.resultCodeSchedules = angular.copy(data.data);
                });

              };

              vm.loadTelephonyRcs = function() {
                var d = $q.defer();
                if (vm.telephonyRcList == null) {
                  telephonyRcFactory.getAll({}, {
                    'field' : 'name',
                    'direction' : 'asc'
                  }, {}).then(function(data) {
                    var filteredData = _(data.data).filter(function(item) {
                      return item.code !== 901 && item.code !== 902 && item.code !== 903 && item.code !== 904 && item.code !== 905;
                    });
                    filteredData = _.sortBy(filteredData, 'name');
                    vm.telephonyRcList = filteredData;
                    d.resolve();
                  });
                } else {
                  d.resolve();
                }
                return d.promise;
              };

              vm.loadResultCodes = function() {
                var d = $q.defer();

                if (vm.campaign.id !== undefined && vm.campaign.campaignGroups == null && vm.resultCodeList == null) {

                  resultCodeFactory.getResultCodesByCampaignId(vm.campaign.id, true).then(function(data) {
                    vm.resultCodeList = _.sortBy(data, 'name');
                    d.resolve();
                  });

                } else {

                  if (vm.campaign.campaignGroups != null) {

                    var ids = [];
                    $.each(vm.campaign.campaignGroups, function(index, item) {
                      if (item.enabled) {
                        ids.push(item.group.id);
                      }
                    });

                    resultCodeFactory.getResultCodesByGroupIds(ids, true).then(function(data) {
                      vm.resultCodeList = _.sortBy(data, 'name');
                      d.resolve();
                    });

                  } else {
                    d.resolve();
                  }

                }

                return d.promise;
              };

              vm.open = function(size, item) {

                $q.all([ vm.loadTelephonyRcs(), vm.loadResultCodes() ]).then(function() {

                  vm.item = item;
                  item.isNew = (item.isNew == null ? (item.id == null ? true : false) : item.isNew);

                  var modalInstance = $modal.open({
                    animation : true,
                    backdrop : 'static',
                    templateUrl : 'pages/resultCodeScheduleForm.html',
                    controller : 'resultCodeScheduleFormCtrl as resultCodeSchedule',
                    size : size,
                    resolve : {
                      campaign : function() {
                        return vm.campaign;
                      },
                      campaignInitial : function() {
                        return vm.campaignInitial;
                      },
                      telephonyRcList : function() {
                        return vm.telephonyRcList;
                      },
                      resultCodeList : function() {
                        return vm.resultCodeList;
                      },
                      item : function() {
                        return vm.item;
                      }
                    }
                  });

                    modalInstance.result.then(function (result) {
                        if (vm.item.isNew) {
                            result.isNew = false;
                            vm.resultCodeScheduleGridOptions.data.unshift(result);
                        } else {
                          if (result.resultCodeScheduleAdvanceds !== undefined) {
                            vm.item.resultCodeScheduleAdvanceds = result.resultCodeScheduleAdvanceds;
                          }
                          var tmp = angular.copy(result);
                          delete tmp.resultCodeScheduleAdvanceds;
                          angular.merge(vm.item, tmp);
                        }
                    }, function () {
                        $log.info('Modal dismissed at: ' + new Date());
                    });
                });
              };

              vm.remove = function() {
                vm.gridApi.selection.getSelectedRows().forEach(function(item, i, arr) {
                  var index = vm.resultCodeScheduleGridOptions.data.indexOf(item);
                  if (item.id == null) {
                    vm.resultCodeScheduleGridOptions.data.splice(index, 1);
                  } else {
                    item.deleted = true;
                    item.changed = true;
                  }
                  vm.selectedResultCodeSchedules = null;
                });
              };

              vm.newResultCodeSchedule = function() {
                return resultCodeScheduleFactory.createNew();
              };

            } ]);
