/**
 * Controller to show actionLogs on main page /
 */
app
    .controller(
        'actionLogCacheCtrl',
        [
            '$scope',
            '$http',
            'uiGridConstants',
            '$log',
            '$modal',
            'actionLogFactory',
            'i18nService',
            '$locale',
            '$rootScope',
            '$filter',
            'locMsg',
            '$window',
            '$timeout',
            '$interval',
            'localStorageService',
            'gridStateService',
            'constants',
            function($scope, $http, uiGridConstants, $log, $modal, actionLogFactory, i18nService, $locale, $rootScope, $filter, locMsg, $window, $timeout, $interval,
                     localStorageService, gridStateService, constants) {
              var vm = this;

              vm.isCollapsed = false;
              vm.draggableCookieKey = 'actionLog';
              vm.draggableDisabled = (Cookie.find(vm.draggableCookieKey + 'Draggable') == "false") ? false : true;

              vm.triggerDraggable = function() {
                vm.draggableDisabled = !vm.draggableDisabled;
                Cookie.create(vm.draggableCookieKey + 'Draggable', vm.draggableDisabled);

                if (angular.isDefined(vm.filterTimeout)) {
                  $timeout.cancel(vm.filterTimeout);
                }
                vm.filterTimeout = $timeout(vm.updateGrid, 50);
              };

              vm.updateGrid = function() {
                vm.gridApi.core.handleWindowResize();
              };

              var paginationOptions = {
                page : {},
                sort : {},
                filters : {}
              };

              vm.enableFiltering = function() {
                var isEnabledFiltering = localStorageService.get('enableFiltering_actionLogCache');
                if (!_.isNull(isEnabledFiltering) && !_.isUndefined(isEnabledFiltering)) {
                  return JSON.parse(isEnabledFiltering);
                }
                return false;
              };

              vm.colDefs = [
                  {
                    displayName : locMsg['log.log'],
                    field : 'log',
                    enableHiding : true,
                    headerCellClass : vm.highlightFilteredHeader,
                    filters : [ {
                      condition : uiGridConstants.filter.CONTAINS
                    } ],
                    cellTemplate : '<div class="ui-grid-cell-contents-low {{row.entity.logLevel == 2 ? \'green\':(row.entity.logLevel == 1 ? \'yellow\':(row.entity.logLevel == 0 ? \'red\':\'\'))}}" >{{row.entity.log}}</div>',
                  },
                  {
                    displayName : locMsg['log.level'],
                    field : 'logLevel',
                    enableHiding : true,
                    headerCellClass : vm.highlightFilteredHeader,
                    filter : {
                      type : uiGridConstants.filter.SELECT,
                      selectOptions : [ {
                        value : 0,
                        label : locMsg['log.level.0']
                      }, {
                        value : 1,
                        label : locMsg['log.level.1']
                      }, {
                        value : 2,
                        label : locMsg['log.level.2']
                      }, ]
                    },
                    cellTemplate : '<div class="ui-grid-cell-contents-low  {{row.entity.logLevel == 2 ? \'green\':(row.entity.logLevel == 1 ? \'yellow\':(row.entity.logLevel == 0 ? \'red\':\'\'))}}" >{{"log.level." + row.entity.logLevel | i18n}}</div>',
                  },
                  {
                    displayName : locMsg['log.module'],
                    field : 'logModule',
                    enableHiding : true,
                    headerCellClass : vm.highlightFilteredHeader,
                    filter : {
                      type : uiGridConstants.filter.SELECT,
                      selectOptions : constants.ACTION_LOG_MODULE_SELECT_OPTIONS
                    },
                    cellTemplate : '<div class="ui-grid-cell-contents-low {{row.entity.logLevel == 2 ? \'green\':(row.entity.logLevel == 1 ? \'yellow\':(row.entity.logLevel == 0 ? \'red\':\'\'))}}">{{"log.module." + row.entity.logModule | i18n}}</div>',
                  },
                  {
                    displayName : locMsg['campaign.label'],
                    field : 'campaign.name',
                    enableHiding : true,
                    visible : false,
                    headerCellClass : vm.highlightFilteredHeader,
                    filters : [ {
                      condition : uiGridConstants.filter.CONTAINS
                    } ],
                    cellTemplate : '<div class="ui-grid-cell-contents-low {{row.entity.logLevel == 2 ? \'green\':(row.entity.logLevel == 1 ? \'yellow\':(row.entity.logLevel == 0 ? \'red\':\'\'))}}">{{row.entity.campaign.name}}</div>',
                  },
                  {
                    displayName : locMsg['lsc.label'],
                    field : 'lsc.name',
                    enableHiding : true,
                    visible : false,
                    headerCellClass : vm.highlightFilteredHeader,
                    filters : [ {
                      condition : uiGridConstants.filter.CONTAINS
                    } ],
                    cellTemplate : '<div class="ui-grid-cell-contents-low {{row.entity.logLevel == 2 ? \'green\':(row.entity.logLevel == 1 ? \'yellow\':(row.entity.logLevel == 0 ? \'red\':\'\'))}}">{{row.entity.lsc.name}}</div>',
                  },
                  {
                    displayName : locMsg['dnc.label'],
                    field : 'dnc.name',
                    enableHiding : true,
                    visible : false,
                    headerCellClass : vm.highlightFilteredHeader,
                    filters : [ {
                      condition : uiGridConstants.filter.CONTAINS
                    } ],
                    cellTemplate : '<div class="ui-grid-cell-contents-low {{row.entity.logLevel == 2 ? \'green\':(row.entity.logLevel == 1 ? \'yellow\':(row.entity.logLevel == 0 ? \'red\':\'\'))}}">{{row.entity.dnc.name}}</div>',
                  },
                  {
                    displayName : locMsg['admin.logs.user'],
                    field : 'user.username',
                    enableHiding : true,
                    visible : false,
                    headerCellClass : vm.highlightFilteredHeader,
                    filters : [ {
                      condition : uiGridConstants.filter.CONTAINS
                    } ],
                    cellTemplate : '<div class="ui-grid-cell-contents-low {{row.entity.logLevel == 2 ? \'green\':(row.entity.logLevel == 1 ? \'yellow\':(row.entity.logLevel == 0 ? \'red\':\'\'))}}">{{row.entity.user.username}}</div>',
                  },
                  {
                    displayName : locMsg['common.tenant'],
                    field : 'tenant.name',
                    enableHiding : true,
                    visible : $rootScope.hasAnyRole([ 'ROLE_SUPERADMIN' ]),
                    headerCellClass : vm.highlightFilteredHeader,
                    filters : [ {
                      condition : uiGridConstants.filter.CONTAINS
                    } ],
                    cellTemplate : '<div class="ui-grid-cell-contents-low {{row.entity.logLevel == 2 ? \'green\':(row.entity.logLevel == 1 ? \'yellow\':(row.entity.logLevel == 0 ? \'red\':\'\'))}}">{{row.entity.tenant.name}}</div>',
                  },
                  {
                    displayName : locMsg['label.createDate'],
                    field : 'createDate',
                    enableHiding : true,
                    headerCellClass : vm.highlightFilteredHeader,
                    enableFiltering : false,
                    type: 'number',
                    sort : {
                      direction : uiGridConstants.DESC,
                      priority : 0,
                    },
                    cellTemplate : '<div class="ui-grid-cell-contents-low {{row.entity.logLevel == 2 ? \'green\':(row.entity.logLevel == 1 ? \'yellow\':(row.entity.logLevel == 0 ? \'red\':\'\'))}}">{{row.entity.createDate | date : \'dd/MM/yyyy HH:mm\'}}</div>',
                  }, ];

              vm.colDefs = actionLogFactory.hideTenantColumn(vm.colDefs);
              vm.regularGridStateName = "actionLogCacheGrid";
              vm.defaultGridStateName = "defaultActionLogCacheGrid";
              vm.actionLogGridOptions = {
                rowHeight : 20,
                rowTemplate : '<div ng-repeat="(colRenderIndex, col) in colContainer.renderedColumns track by col.colDef.name" class="ui-grid-cell " ui-grid-cell></div>',
                enableFiltering : vm.enableFiltering(),
                enableGridMenu : true,
                exporterCsvColumnSeparator : ';',
                exporterOlderExcelCompatibility : true,
                exporterCsvFilename : $rootScope.getExportFilename('action logs', 'csv'),
                exporterPdfFilename : $rootScope.getExportFilename('action logs', 'pdf'),
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
                  if (col.name == 'logLevel') {
                    return constants.ACTION_LOG_LOG_LEVEL_SELECT_OPTIONS[value].label;
                  } else if (col.name == 'logModule') {
                    return constants.ACTION_LOG_MODULE_SELECT_OPTIONS[value].label;
                  } else if (col.name == 'createDate') {
                    return $filter('date')(value, 'dd/MM/yyyy HH:mm');
                  } else {
                    return value;
                  }
                },
                columnDefs : vm.colDefs
              };

              i18nService.setCurrentLang(locMsg['lang.id']);

              vm.toggleFiltering = function() {
                vm.actionLogGridOptions.enableFiltering = !vm.actionLogGridOptions.enableFiltering;
                localStorageService.set('enableFiltering_actionLogCache', vm.actionLogGridOptions.enableFiltering);
                vm.gridApi.core.notifyDataChange(uiGridConstants.dataChange.COLUMN);
                gridStateService.restore(vm.regularGridStateName, vm.gridApi, vm);
              };

              vm.actionLogGridOptions.onRegisterApi = function(gridApi) {
                vm.gridApi = gridApi;
                vm.actionLogGridOptions.gridMenuCustomItems = $rootScope.getExportMenuItemsXLSX(vm.gridApi, 'action logs');
                vm.actionLogGridOptions.gridMenuCustomItems.push({
                  title : locMsg['resetTable'],
                  action : function() {
                    gridStateService.reset(vm.defaultGridStateName, vm.regularGridStateName, vm.gridApi, vm);
                  },
                  order : 220
                });
                gridApi.core.on.sortChanged($scope, function(grid, sortColumns) {

                  gridStateService.save(vm.regularGridStateName, vm.gridApi);

                  if (sortColumns.length == 0) {
                    paginationOptions.sort = {};
                  } else {
                    paginationOptions.sort['field'] = sortColumns[0].field;
                    paginationOptions.sort['direction'] = sortColumns[0].sort.direction;
                  }
                });

                gridApi.core.on.filterChanged($scope, function() {

                  gridStateService.save(vm.regularGridStateName, vm.gridApi);

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
                  //gridApi.saveState here is old on restore! That's why we add timeout
                  $timeout(function() {
                    gridStateService.save(vm.regularGridStateName, vm.gridApi);
                  }, 50);
                });

                vm.gridApi.colResizable.on.columnSizeChanged($scope, function() {
                  gridStateService.save(vm.regularGridStateName, vm.gridApi);
                });

                $timeout(function() {
                  gridStateService.saveDefaultStateIfNotExist(vm.defaultGridStateName, vm.gridApi);
                  gridStateService.restore(vm.regularGridStateName, vm.gridApi, vm);
                }, 10);

              };

              getActionLog = function() {
                $.ajax(window.location.pathname + "actionLog/cache").done(function(data) {
                  if (!angular.equals(vm.actionLogGridOptions.data, data)) {
                    vm.actionLogGridOptions.data = data;
                  }
                });
              };

              $rootScope.actionLogListRefresh = $interval(getActionLog, 1000);

              $scope.$on('$destroy', function(e) {
                $interval.cancel($rootScope.actionLogListRefresh);
              });

            }

        ]);
