/**
 * Controller to show list of campaigns /
 */
app
    .controller(
        'campaignCtrl',
        [
            '$scope',
            '$http',
            'uiGridConstants',
            '$log',
            '$modal',
            'campaignFactory',
            'i18nService',
            '$locale',
            '$rootScope',
            '$filter',
            'locMsg',
            '$window',
            'adminLogsFactory',
            '$timeout',
            '$interval',
            'localStorageService',
            'gridStateService',
            'constants',
            'alertService',
            function($scope, $http, uiGridConstants, $log, $modal, campaignFactory, i18nService, $locale, $rootScope, $filter, locMsg, $window, adminLogsFactory, $timeout,
                     $interval, localStorageService, gridStateService, constants, alertService) {
              var vm = this;

              if (!$rootScope.permitedFieds) {
                (function() {
                  $http.get('campaign/getPermittedFields', {}).then(function(response) {
                    if (response.data) {
                      $rootScope.permitedFieds = response.data;
                    }
                  }, function(response) {
                    $log.error(response);
                  });
                })();
              }

              vm.selectedCampaigns = [];

              vm.setEnabled = function(enabled) {
                var stoppedCampaigns = _.where(vm.selectedCampaigns, {
                  state : 0
                });
                saveEnabled(stoppedCampaigns, campaignFactory, vm.campaignGridOptions.data, 'campaign', enabled, function() {
                  adminLogsFactory.enableDisableFromList(0, vm.gridApi.selection.getSelectedRows(), enabled);
                });
              };

              vm.isNeedDisableButton = function(disableForStates) {
                if (vm.selectedCampaigns == null || vm.selectedCampaigns[0] === undefined) {
                  return false;
                }
                for (var i = 0; i < disableForStates.length; ++i) {
                  if (disableForStates[i] == vm.selectedCampaigns[0].state) {
                    return true;
                  }
                }
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

              vm.enableFiltering = function() {
                var isEnabledFiltering = localStorageService.get('enableFiltering_campaign');
                if (!_.isNull(isEnabledFiltering) && !_.isUndefined(isEnabledFiltering)) {
                  return JSON.parse(isEnabledFiltering);
                }
                return false;
              };

              vm.colDefs = [
                  {
                    displayName : locMsg['label.id'],
                    field : 'id',
                    enableHiding : true,
                    visible : false,
                    headerCellClass : vm.highlightFilteredHeader,
                    type : 'number',
                    filters : [ {
                      condition : uiGridConstants.filter.CONTAINS
                    } ],
                    minWidth : 30
                  },
                  {
                    displayName : locMsg['campaign.name'],
                    field : 'name',
                    enableHiding : true,
                    headerCellClass : vm.highlightFilteredHeader,
                    filters : [ {
                      condition : uiGridConstants.filter.CONTAINS
                    } ],
                    minWidth : 50
                  },
                  {
                    displayName : locMsg['campaign.type'],
                    field : 'type',
                    enableHiding : true,
                    headerCellClass : vm.highlightFilteredHeader,
                    filter : {
                      type : uiGridConstants.filter.SELECT,
                      selectOptions : constants.CAMPAIGN_TYPE_SELECT_OPTIONS
                    },
                    filterHeaderTemplate :
                        '<div class="ui-grid-filter-container" ng-repeat="colFilter in col.filters">'
                        + '<div ng-if="colFilter.type === \'select\'" class="ng-scope">'
                        + '<select class="ui-grid-filter-select ui-grid-filter-input-0 ng-touched" ng-model="colFilter.term" ng-init="colFilter.term = colFilter.term == null ? \'\' : colFilter.term" ng-attr-placeholder="{{colFilter.placeholder || aria.defaultFilterLabel}}" ng-options="option.value as option.label for option in colFilter.selectOptions">'
                        + '</select>'
                        + '</div>'
                        + '</div>',
                    cellTemplate : '<div class="ui-grid-cell-contents">{{"campaign.type." + row.entity.type | i18n}}</div>',
                    minWidth : 50
                  },
                  {
                    displayName : locMsg['campaign.state'],
                    field : 'state',
                    enableHiding : true,
                    headerCellClass : vm.highlightFilteredHeader,
                    filter : {
                      type : uiGridConstants.filter.SELECT,
                      selectOptions : constants.CAMPAIGN_STATE_SELECT_OPTIONS
                    },
                    filterHeaderTemplate :
                        '<div class="ui-grid-filter-container" ng-repeat="colFilter in col.filters">'
                        + '<div ng-if="colFilter.type === \'select\'" class="ng-scope">'
                        + '<select class="ui-grid-filter-select ui-grid-filter-input-0 ng-touched" ng-model="colFilter.term" ng-init="colFilter.term = colFilter.term == null ? \'\' : colFilter.term" ng-attr-placeholder="{{colFilter.placeholder || aria.defaultFilterLabel}}" ng-options="option.value as option.label for option in colFilter.selectOptions">'
                        + '</select>'
                        + '</div>'
                        + '</div>',
                    cellTemplate : '<div class="ui-grid-cell-contents {{row.entity.state == 0 || row.entity.state == 1 ? \'red\' : (row.entity.state == 2 || row.entity.state == 3 ? \'green\' : (row.entity.state == 4 || row.entity.state == 5 ? \'yellow\' : \'blue\'))}}">{{"campaign.state." + row.entity.state | i18n}}</div>',
                    minWidth : 50
                  },
                  {
                    displayName : locMsg['campaign.agentsSize'],
                    field : 'agentsSize',
                    enableHiding : true,
                    headerCellClass : vm.highlightFilteredHeader,
                    type : 'number',
                    filters : [ {
                      condition : uiGridConstants.filter.GREATER_THAN,
                      placeholder : locMsg['placeholder.greaterThan']
                    }, {
                      condition : uiGridConstants.filter.LESS_THAN,
                      placeholder : locMsg['placeholder.lessThan']
                    } ],
                    minWidth : 50
                  },
                  {
                    displayName : locMsg['campaign.availAgentsSize'],
                    field : 'availAgentsSize',
                    enableHiding : true,
                    headerCellClass : vm.highlightFilteredHeader,
                    type : 'number',
                    filters : [ {
                      condition : uiGridConstants.filter.GREATER_THAN,
                      placeholder : locMsg['placeholder.greaterThan']
                    }, {
                      condition : uiGridConstants.filter.LESS_THAN,
                      placeholder : locMsg['placeholder.lessThan']
                    } ],
                    minWidth : 50
                  },
                  {
                    displayName : locMsg['campaign.totalListSize'],
                    field : 'totalListSize',
                    enableHiding : true,
                    headerCellClass : vm.highlightFilteredHeader,
                    type : 'number',
                    filters : [ {
                      condition : uiGridConstants.filter.GREATER_THAN,
                      placeholder : locMsg['placeholder.greaterThan']
                    }, {
                      condition : uiGridConstants.filter.LESS_THAN,
                      placeholder : locMsg['placeholder.lessThan']
                    } ],
                    minWidth : 50
                  },
                  {
                    displayName : locMsg['campaign.actualListSize'],
                    field : 'actualListSize',
                    enableHiding : true,
                    headerCellClass : vm.highlightFilteredHeader,
                    type : 'number',
                    filters : [ {
                      condition : uiGridConstants.filter.GREATER_THAN,
                      placeholder : locMsg['placeholder.greaterThan']
                    }, {
                      condition : uiGridConstants.filter.LESS_THAN,
                      placeholder : locMsg['placeholder.lessThan']
                    } ],
                    minWidth : 50
                  },
                  {
                    displayName : locMsg['campaign.listRemaining'],
                    field : 'listRemaining',
                    enableHiding : true,
                    headerCellClass : vm.highlightFilteredHeader,
                    type : 'number',
                    filters : [ {
                      condition : uiGridConstants.filter.GREATER_THAN,
                      placeholder : locMsg['placeholder.greaterThan']
                    }, {
                      condition : uiGridConstants.filter.LESS_THAN,
                      placeholder : locMsg['placeholder.lessThan']
                    } ],
                    minWidth : 50
                  },
                  {
                    displayName : locMsg['campaign.freshDataListSize'],
                    field : 'freshDataListSize',
                    enableHiding : true,
                    headerCellClass : vm.highlightFilteredHeader,
                    type : 'number',
                    filters : [ {
                      condition : uiGridConstants.filter.GREATER_THAN,
                      placeholder : locMsg['placeholder.greaterThan']
                    }, {
                      condition : uiGridConstants.filter.LESS_THAN,
                      placeholder : locMsg['placeholder.lessThan']
                    } ],
                    minWidth : 50
                  },
                  {
                    displayName : locMsg['campaign.retryListSize'],
                    field : 'retryListSize',
                    enableHiding : true,
                    headerCellClass : vm.highlightFilteredHeader,
                    type : 'number',
                    filters : [ {
                      condition : uiGridConstants.filter.GREATER_THAN,
                      placeholder : locMsg['placeholder.greaterThan']
                    }, {
                      condition : uiGridConstants.filter.LESS_THAN,
                      placeholder : locMsg['placeholder.lessThan']
                    } ],
                    minWidth : 50
                  },
                  {
                    displayName : locMsg['campaign.callbackListSize'],
                    field : 'callbackListSize',
                    enableHiding : true,
                    headerCellClass : vm.highlightFilteredHeader,
                    type : 'number',
                    filters : [ {
                      condition : uiGridConstants.filter.GREATER_THAN,
                      placeholder : locMsg['placeholder.greaterThan']
                    }, {
                      condition : uiGridConstants.filter.LESS_THAN,
                      placeholder : locMsg['placeholder.lessThan']
                    } ],
                    minWidth : 50
                  },
                  {
                    displayName : locMsg['campaign.cacheSize'],
                    field : 'cacheSize',
                    enableHiding : true,
                    headerCellClass : vm.highlightFilteredHeader,
                    type : 'number',
                    filters : [ {
                      condition : uiGridConstants.filter.GREATER_THAN,
                      placeholder : locMsg['placeholder.greaterThan']
                    }, {
                      condition : uiGridConstants.filter.LESS_THAN,
                      placeholder : locMsg['placeholder.lessThan']
                    } ],
                    minWidth : 50
                  },
                  {
                    displayName : locMsg['campaign.queueSize'],
                    field : 'queueSize',
                    enableHiding : true,
                    headerCellClass : vm.highlightFilteredHeader,
                    type : 'number',
                    filters : [ {
                      condition : uiGridConstants.filter.GREATER_THAN,
                      placeholder : locMsg['placeholder.greaterThan']
                    }, {
                      condition : uiGridConstants.filter.LESS_THAN,
                      placeholder : locMsg['placeholder.lessThan']
                    } ],
                    minWidth : 50
                  },
                  {
                    displayName : locMsg['campaign.ringingCalls'],
                    field : 'ringingCalls',
                    enableHiding : true,
                    headerCellClass : vm.highlightFilteredHeader,
                    type : 'number',
                    filters : [ {
                      condition : uiGridConstants.filter.GREATER_THAN,
                      placeholder : locMsg['placeholder.greaterThan']
                    }, {
                      condition : uiGridConstants.filter.LESS_THAN,
                      placeholder : locMsg['placeholder.lessThan']
                    } ],
                    minWidth : 50
                  },
                  {
                    displayName : locMsg['campaign.noConnectionCalls'],
                    field : 'noConnectionCalls',
                    enableHiding : true,
                    headerCellClass : vm.highlightFilteredHeader,
                    type : 'number',
                    filters : [ {
                      condition : uiGridConstants.filter.GREATER_THAN,
                      placeholder : locMsg['placeholder.greaterThan']
                    }, {
                      condition : uiGridConstants.filter.LESS_THAN,
                      placeholder : locMsg['placeholder.lessThan']
                    } ],
                    minWidt : 50
                  },
                  {
                    displayName : locMsg['campaign.activeCalls'],
                    field : 'activeCalls',
                    enableHiding : true,
                    visible : false,
                    headerCellClass : vm.highlightFilteredHeader,
                    type : 'number',
                    filters : [ {
                      condition : uiGridConstants.filter.GREATER_THAN,
                      placeholder : locMsg['placeholder.greaterThan']
                    }, {
                      condition : uiGridConstants.filter.LESS_THAN,
                      placeholder : locMsg['placeholder.lessThan']
                    } ],
                    minWidth : 50
                  },
                  {
                    displayName : locMsg['campaign.incallAgents'],
                    field : 'incallAgents',
                    enableHiding : true,
                    headerCellClass : vm.highlightFilteredHeader,
                    type : 'number',
                    filters : [ {
                      condition : uiGridConstants.filter.GREATER_THAN,
                      placeholder : locMsg['placeholder.greaterThan']
                    }, {
                      condition : uiGridConstants.filter.LESS_THAN,
                      placeholder : locMsg['placeholder.lessThan']
                    } ],
                    minWidth : 50
                  },
                  {
                    displayName : locMsg['campaign.liveCalls'],
                    field : 'liveCalls',
                    enableHiding : true,
                    headerCellClass : vm.highlightFilteredHeader,
                    type : 'number',
                    filters : [ {
                      condition : uiGridConstants.filter.GREATER_THAN,
                      placeholder : locMsg['placeholder.greaterThan']
                    }, {
                      condition : uiGridConstants.filter.LESS_THAN,
                      placeholder : locMsg['placeholder.lessThan']
                    } ],
                    minWidth : 50
                  },
                  {
                    displayName : locMsg['campaign.callsMade'],
                    field : 'callsMade',
                    enableHiding : true,
                    headerCellClass : vm.highlightFilteredHeader,
                    type : 'number',
                    filters : [ {
                      condition : uiGridConstants.filter.GREATER_THAN,
                      placeholder : locMsg['placeholder.greaterThan']
                    }, {
                      condition : uiGridConstants.filter.LESS_THAN,
                      placeholder : locMsg['placeholder.lessThan']
                    } ],
                    minWidth : 50
                  },
                  {
                    displayName : locMsg['campaign.retriesMade'],
                    field : 'retriesMade',
                    enableHiding : true,
                    headerCellClass : vm.highlightFilteredHeader,
                    type : 'number',
                    filters : [ {
                      condition : uiGridConstants.filter.GREATER_THAN,
                      placeholder : locMsg['placeholder.greaterThan']
                    }, {
                      condition : uiGridConstants.filter.LESS_THAN,
                      placeholder : locMsg['placeholder.lessThan']
                    } ],
                    minWidth : 50
                  },
                  {
                    displayName : locMsg['campaign.abandonedCallsRatio'],
                    field : 'abandonedCallsRatio',
                    enableHiding : true,
                    visible : false,
                    headerCellClass : vm.highlightFilteredHeader,
                    type : 'number',
                    filters : [ {
                      condition : uiGridConstants.filter.GREATER_THAN,
                      placeholder : locMsg['placeholder.greaterThan']
                    }, {
                      condition : uiGridConstants.filter.LESS_THAN,
                      placeholder : locMsg['placeholder.lessThan']
                    } ],
                    cellTemplate : '<div class="ui-grid-cell-contents">{{(row.entity.abandonedCallsRatio | number:3)*1}}</div>',
                    minWidth : 50
                  },
                  {
                    displayName : locMsg['campaign.runoutEstimation'],
                    field : 'runoutEstimation',
                    enableHiding : true,
                    headerCellClass : vm.highlightFilteredHeader,
                    type : 'number',
                    filters : [ {
                      condition : uiGridConstants.filter.GREATER_THAN,
                      placeholder : locMsg['placeholder.greaterThan']
                    }, {
                      condition : uiGridConstants.filter.LESS_THAN,
                      placeholder : locMsg['placeholder.lessThan']
                    } ],
                    cellFilter : 'duration : \'hh:mm:ss\'',
                    minWidth : 50
                  },
                  {
                    displayName : locMsg['campaign.dbEfficiency'],
                    field : 'dbEfficiency',
                    enableHiding : true,
                    visible : false,
                    headerCellClass : vm.highlightFilteredHeader,
                    type : 'number',
                    filters : [ {
                      condition : uiGridConstants.filter.GREATER_THAN,
                      placeholder : locMsg['placeholder.greaterThan']
                    }, {
                      condition : uiGridConstants.filter.LESS_THAN,
                      placeholder : locMsg['placeholder.lessThan']
                    } ],
                    cellTemplate : '<div class="ui-grid-cell-contents">{{(row.entity.dbEfficiency | number:3)*1}}</div>',
                    minWidth : 50
                  },
                  {
                    displayName : locMsg['campaign.answerRate'],
                    field : 'answerRate',
                    enableHiding : true,
                    visible : false,
                    headerCellClass : vm.highlightFilteredHeader,
                    type : 'number',
                    filters : [ {
                      condition : uiGridConstants.filter.GREATER_THAN,
                      placeholder : locMsg['placeholder.greaterThan']
                    }, {
                      condition : uiGridConstants.filter.LESS_THAN,
                      placeholder : locMsg['placeholder.lessThan']
                    } ],
                    cellTemplate : '<div class="ui-grid-cell-contents">{{(row.entity.answerRate | number:3)*1}}</div>',
                    minWidth : 50
                  },
                  {
                    displayName : locMsg['campaign.completionPercent'],
                    field : 'completionPercent',
                    enableHiding : true,
                    headerCellClass : vm.highlightFilteredHeader,
                    type : 'number',
                    filters : [ {
                      condition : uiGridConstants.filter.GREATER_THAN,
                      placeholder : locMsg['placeholder.greaterThan']
                    }, {
                      condition : uiGridConstants.filter.LESS_THAN,
                      placeholder : locMsg['placeholder.lessThan']
                    } ],
                    cellTemplate : '<progressbar class="progress-striped" value="grid.getCellValue(row, col)" type="{{row.entity.state == 0 || row.entity.state == 1 ? \'danger\' : (row.entity.state == 2 || row.entity.state == 3 ? \'success\' : (row.entity.state == 4 || row.entity.state == 5 ? \'warning\' : \'\'))}}">{{row.entity.completionPercent}}%</progressbar>',
                    minWidth : 50
                  }, {
                    displayName : locMsg['common.tenant'],
                    field : 'tenantListDTO.name',
                    enableHiding : true,
                    visible : $rootScope.hasAnyRole([ 'ROLE_SUPERADMIN', 'ROLE_TENANT_ADMIN' ]),
                    headerCellClass : vm.highlightFilteredHeader,
                    filters : [ {
                      condition : uiGridConstants.filter.CONTAINS
                    } ],
                    minWidth : 50
                  }, {
                    displayName : locMsg['label.enabled'],
                    field : 'enabled',
                    enableHiding : true,
                    visible : false,
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
                    minWidth : 30
                  } ];

              vm.colDefs = campaignFactory.hideTenantColumn(vm.colDefs);
              vm.regularGridStateName = "campaignsGrid";
              vm.defaultGridStateName = "defaultCampaignsGrid";
              vm.campaignGridOptions = {
                enablePaginationControls : false,
                enableRowSelection : true,
                enableSelectAll : true,
                enableFiltering : vm.enableFiltering(),
                enableFullRowSelection : true,
                modifierKeysToMultiSelect : true,
                enableGridMenu : true,
                exporterCsvColumnSeparator : ';',
                exporterOlderExcelCompatibility : true,
                exporterCsvFilename : $rootScope.getExportFilename('campaigns', 'csv'),
                exporterPdfFilename : $rootScope.getExportFilename('campaigns', 'pdf'),
                exporterPdfOrientation : 'landscape',
                exporterPdfPageSize : 'A4',
                exporterPdfMaxGridWidth : 660,
                exporterPdfCustomFormatter : $rootScope.exporterPdfCustomFormatter,
                saveFocus : false,
                saveFilter : true,
                saveVisible : true,
                saveGroupingExpandedStates : true,
                i18n : 'en',
                exporterFieldCallback : function(grid, row, col, value) {
                  if (col.name == 'runoutEstimation') {
                    return $filter('duration')(value, 'hh:mm:ss')
                  } else if (col.name == 'state') {
                    return constants.CAMPAIGN_STATE_SELECT_OPTIONS[parseInt(value) + 1].label;
                  } else if (col.name == 'type') {
                    return constants.CAMPAIGN_TYPE_SELECT_OPTIONS[parseInt(value) + 1].label;
                  } else {
                    return value;
                  }
                },
                columnDefs : vm.colDefs
              };

              i18nService.setCurrentLang(locMsg['lang.id']);

              vm.toggleFiltering = function() {
                vm.campaignGridOptions.enableFiltering = !vm.campaignGridOptions.enableFiltering;
                localStorageService.set('enableFiltering_campaign', vm.campaignGridOptions.enableFiltering);
                vm.gridApi.core.notifyDataChange(uiGridConstants.dataChange.COLUMN);
                gridStateService.restore(vm.regularGridStateName, vm.gridApi, vm);
              };

              vm.campaignGridOptions.onRegisterApi = function(gridApi) {
                vm.gridApi = gridApi;
                vm.campaignGridOptions.gridMenuCustomItems = $rootScope.getExportMenuItemsXLSX(vm.gridApi, 'campaigns');
                vm.campaignGridOptions.gridMenuCustomItems.push({
                  title : locMsg['resetTable'],
                  action : function() {
                    gridStateService.reset(vm.defaultGridStateName, vm.regularGridStateName, vm.gridApi, vm);
                  },
                  order : 220
                });
                var onSelect = function(row) {
                  vm.selectedCampaigns = vm.gridApi.selection.getSelectedRows();
                };
                vm.gridApi.selection.on.rowSelectionChanged($scope, onSelect);
                vm.gridApi.selection.on.rowSelectionChangedBatch($scope, onSelect);

                vm.gridApi.core.on.sortChanged($scope, function(grid, sortColumns) {

                  gridStateService.save(vm.regularGridStateName, vm.gridApi);

                  if (sortColumns.length == 0) {
                    paginationOptions.sort = {};
                  } else {
                    paginationOptions.sort['field'] = sortColumns[0].field;
                    paginationOptions.sort['direction'] = sortColumns[0].sort.direction;
                  }
                });

                vm.gridApi.core.on.filterChanged($scope, function() {
                  gridStateService.save(vm.regularGridStateName, vm.gridApi);
                });

                vm.gridApi.colResizable.on.columnSizeChanged($scope, function() {
                  gridStateService.save(vm.regularGridStateName, vm.gridApi);
                });

                vm.gridApi.core.on.columnVisibilityChanged($scope, function(changedColumn) {
                  $scope.columnChanged = {
                    name : changedColumn.colDef.name,
                    visible : changedColumn.colDef.visible
                  };
                  // gridApi.saveState here is old on restore! That's why we add timeout
                  $timeout(function() {
                    gridStateService.save(vm.regularGridStateName, vm.gridApi);
                  }, 50);
                });

                $timeout(function() {
                  gridStateService.saveDefaultStateIfNotExist(vm.defaultGridStateName, vm.gridApi);
                  gridStateService.restore(vm.regularGridStateName, vm.gridApi, vm);
                }, 10);

              };

              vm.getPage = function() {
                campaignFactory.getAll(paginationOptions.page, paginationOptions.sort, paginationOptions.filters).then(function(data) {
                  vm.campaignGridOptions.totalItems = data.totalItems;
                  vm.campaignGridOptions.data = data.data;
                  $rootScope.campaignListRefresh = $interval(getRealTimeInfo, 1000);
                  pollUpdates();
                });
              };

              vm.getPage();

              vm.open = function(size, item) {

                vm.item = item;

                vm.isNew = true;
                if (item.id != null) {
                  vm.isNew = false;
                }

                var modalInstance = $modal.open({
                  animation : true,
                  backdrop : 'static',
                  templateUrl : 'pages/campaignForm.html',
                  controller : 'campaignFormCtrl as campaign',
                  size : size,
                  resolve : {
                    item : function() {
                      return vm.item;
                    }
                  }
                });

                modalInstance.result.then(function(result) {
                  if (vm.isNew) {
                    var campaign = _.find(vm.campaignGridOptions.data, {
                      id : result.id
                    });
                    if (!campaign) {
                      vm.campaignGridOptions.data.push(result);
                    }
                  } else {
                    angular.merge(vm.item, result);
                  }
                  adminLogsFactory.check(vm.item, result, 0, null, null, null);
                  liveUpdates(true);
                  if (vm.isRunningCampaignDisabled(result))
                    vm.changeState('STOP_REGULAR');
                }, function() {
                  liveUpdates(true);
                });

                liveUpdates(false);
              };

              vm.openUploadTool = function () {
                liveUpdates(false);
                var selectedCampaign = vm.selectedCampaigns[0];
                var uploadToolModalInstance = $modal.open({
                  animation: true,
                  backdrop: 'static',
                  templateUrl: 'pages/uploadTool.html',
                  controller: 'uploadToolCtrl as uploadTool',
                  resolve: {
                    campaign: selectedCampaign
                  }
                });
                uploadToolModalInstance.result.then(function (result) {
                  liveUpdates(true);
                }, function () {
                  liveUpdates(true);
                })
              };

              vm.isRunningCampaignDisabled = function(campaign) {
                return campaign.enabled == false && (campaign.state == 3 || campaign.state == 5);
              };

              vm.clone = function() {

                var modalInstance = $modal.open({
                  animation : true,
                  backdrop : 'static',
                  templateUrl : 'pages/campaignCloneForm.html',
                  controller : 'campaignCloneFormCtrl as campaignClone',
                  size : 'sm',
                  resolve : {
                    item : function() {
                      return vm.selectedCampaigns[0].id;
                    }
                  }
                });

                modalInstance.result.then(function(result) {
                  var campaign = _.find(vm.campaignGridOptions.data, {
                    id : result.id
                  });
                  if (!campaign) {
                    vm.campaignGridOptions.data.push(result);
                  }
                  adminLogsFactory.check({}, result, 0, null, null, null);
                  $rootScope.updateAuth();
                  liveUpdates(true);
                }, function() {
                  $log.info('Modal dismissed at: ' + new Date());
                  liveUpdates(true);
                });
              };

              vm.remove = function() {
                var ids = [];
                var objects = [];

                vm.gridApi.selection.getSelectedRows().forEach(function(item, i, arr) {
                  ids.push(item.id);
                  objects.push(item);
                });

                campaignFactory.remove(ids).then(function(data) {
                  data.forEach(function(item, i, arr) {
                    vm.campaignGridOptions.data = _.without(vm.campaignGridOptions.data, _.findWhere(vm.campaignGridOptions.data, {
                      id : item
                    }));
                    adminLogsFactory.proccessList(_.find(objects, function(num) {
                      return num.id === item;
                    }), 0, 2);
                  });
                }, function(response) {
                  $log.error(response);
                });

                vm.selectedCampaigns = null;
              };

              vm.newCampaign = function() {
                return campaignFactory.createNew();
              };

              vm.changeState = function(type) {
                _.each(vm.selectedCampaigns, function(campaign) {
                  switch (type) {
                  case 'START':
                    campaign.state = 2;
                    break;
                  case 'STOP_REGULAR':
                    campaign.state = 1;
                    break;
                  case 'STOP_FORCEFULLY':
                    campaign.state = 1;
                    break;
                  case 'PAUSE':
                    campaign.state = 4;
                    break;
                  case 'RESET':
                    if (campaign.state == 0)
                      resetCampaign(campaign);
                    break;
                  }
                  campaignFactory.action(campaign.id, type);
                });
              };

              vm.canManageSelectedCampaigns = function() {
                var permission = 'ManageCampaign';
                if ($rootScope.hasAnyRole([ 'ROLE_SUPERADMIN' ]) || this.hasAllAuthority(permission)) {
                  return true;
                }
                for (var i = 0; i < vm.selectedCampaigns.length; i++) {
                  if (!this.hasAuthorityByCampaignOrTenant(permission, vm.selectedCampaigns[i])) {
                    return false;
                  }
                }
                return true;
              };

              vm.canEditSelectedCampaigns = function(permission) {
                if ($rootScope.hasAnyRole([ 'ROLE_SUPERADMIN' ]) || this.hasAllAuthority(permission)) {
                  return true;
                }
                for (var i = 0; i < vm.selectedCampaigns.length; i++) {
                  if (!this.hasAuthorityByCampaignOrTenant(permission, vm.selectedCampaigns[i])) {
                    return false;
                  }
                }
                return true;
              };

              vm.canDeleteSelectedCampaigns = function() {
                return $rootScope.hasAnyRole([ 'ROLE_SUPERADMIN', 'ROLE_TENANT_ADMIN' ]);
              };

              vm.canCreateCampaign = function() {
                return $rootScope.hasAnyRole([ 'ROLE_SUPERADMIN', 'ROLE_TENANT_ADMIN' ]) || $rootScope.hasAuthority({
                  authority : "CreateCampaign"
                });
              };

              vm.hasAuthorityByCampaignOrTenant = function(permission, campaign) {
                return $rootScope.hasAuthority({
                  authority : permission,
                  value : campaign.id
                }) || $rootScope.hasAuthority({
                  authority : permission,
                  value : 'ALL' + campaign.tenant
                });
              };

              vm.hasAllAuthority = function(permission) {
                return $rootScope.hasAuthority({
                  authority : permission,
                  value : 'ALL'
                });
              };

              vm.recalculatePriorities = function () {
                let campaign = vm.selectedCampaigns[0];
                if (campaign.id != null) {
                  campaignFactory.recalculatePriorities(campaign.id).then((result) => {
                    if (result === constants.PRIORITY_RECALCULATION_STATE.EXECUTING) {
                      alertService.add('success', locMsg['label.priorityRecalculation.started']);
                    } else if (result === constants.PRIORITY_RECALCULATION_STATE.EXPECTING) {
                      alertService.add('success', locMsg['label.priorityRecalculation.alreadyRunning']);
                    } else {
                      alertService.add('danger', locMsg['label.priorityRecalculation.declined']);
                    }
                  }).catch(error => {
                    $log.error(error);
                  });
                }
              }

              vm.shouldHideRecalculationButton = function () {
                return !vm.selectedCampaigns || vm.selectedCampaigns.length !== 1 || !vm.canEditSelectedCampaigns('EditCampaignNonTech')
                  || !vm.selectedCampaigns[0].dbResultTable
                  || vm.selectedCampaigns[0].colSortBy !== constants.SORT_BY_MULTIPLE;
              }

              resetCampaign = function(campaign) {
                campaign.agentsSize = 0;
                campaign.availAgentsSize = 0;
                campaign.incallAgents = 0;
                campaign.totalListSize = 0;
                campaign.actualListSize = 0;
                campaign.retryListSize = 0;
                campaign.callbackListSize = 0;
                campaign.freshDataListSize = 0;
                campaign.listRemaining = 0;
                campaign.callsMade = 0;
                campaign.activeCalls = 0;
                campaign.liveCalls = 0;
                campaign.ringingCalls = 0;
                campaign.noConnectionCalls = 0;
                campaign.retriesMade = 0;
                campaign.cacheSize = 0;
                campaign.queueSize = 0;
                campaign.runoutEstimation = 0;
                campaign.completionPercent = 0;
                campaign.abandonedCallsRatio = 0;
                campaign.dbEfficiency = 0;
                campaign.answerRate = 0;
              };

              getRealTimeInfo = function() {
                var campaignIds = _.pluck(vm.campaignGridOptions.data, 'id');
                $.ajax(window.location.pathname + "campaign/realtimeInfo?ids=" + campaignIds).done(function(data) {
                  _.each(vm.campaignGridOptions.data, function(campaign) {
                    var campaignToMerge = _.where(data, {
                      id : campaign.id
                    });
                    if (campaignToMerge.length != 0) {
                      angular.merge(campaign, campaignToMerge[0]);
                    } else {
                      campaign.state = 0;
                    }
                  });
                  vm.gridApi.grid.api.core.refresh();
                });
              };

              var pollInProgress = false;
              pollUpdates = function() {
                $rootScope.activePollUpdates = true;
                if (!pollInProgress) {
                  pollInProgress = true;
                  $.ajax(window.location.pathname + "campaign/pollUpdates").done(function(response) {
                    if ($rootScope.activePollUpdates) {
                      $timeout(pollUpdates, 1);
                    }
                    _.each(response, function(campaign) {
                      if (campaign.deleted == true) {
                        vm.campaignGridOptions.data = _.without(vm.campaignGridOptions.data, _.findWhere(vm.campaignGridOptions.data, {
                          id : campaign.id
                        }));
                      } else {
                        var campaignOld = _.find(vm.campaignGridOptions.data, function(campaignOld) {
                          return campaignOld.id === campaign.id
                        });
                        if (campaignOld) {
                          angular.merge(campaignOld, campaign);
                        } else {
                          if (vm.campaignGridOptions.data !== null) {
                            vm.campaignGridOptions.data.push(campaign)
                          }
                        }
                      }
                    });
                    pollInProgress = false;
                  });
                }
              };

              liveUpdates = function(flag) {
                if (flag) {
                  $rootScope.campaignListRefresh = $interval(getRealTimeInfo, 1000);
                  $rootScope.actionLogListRefresh = $interval(getActionLog, 1000);
                  pollUpdates();
                } else {
                  $interval.cancel($rootScope.campaignListRefresh);
                  $interval.cancel($rootScope.actionLogListRefresh);
                  $rootScope.activePollUpdates = false;
                }
              };

              $scope.$on('$destroy', function(e) {
                $interval.cancel($rootScope.campaignListRefresh);
                $rootScope.activePollUpdates = false;
              });
            }

        ]);
