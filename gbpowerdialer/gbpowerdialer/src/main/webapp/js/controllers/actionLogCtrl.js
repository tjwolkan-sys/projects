/**
 * Controller to show list of actionLogs / / /
 */
app
    .controller(
        'actionLogCtrl',
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
            'localStorageService',
            'gridStateService',
            'constants',
            function($scope, $http, uiGridConstants, $log, $modal, actionLogFactory, i18nService, $locale, $rootScope, $filter, locMsg, $window, $timeout, localStorageService,
                     gridStateService, constants) {
              var vm = this;
              var $translate = $filter('translate');

              vm.selectedActionLogs = null;

              vm.highlightFilteredHeader = function(row, rowRenderIndex, col, colRenderIndex) {
                if (col.filters[0].term !== undefined && col.filters[0].term !== null && col.filters[0].term !== '') {
                  return 'header-filtered';
                } else {
                  return '';
                }
              };

              vm.enableFiltering = function() {
                var isEnabledFiltering = localStorageService.get('enableFiltering_actionLog');
                if (!_.isNull(isEnabledFiltering) && !_.isUndefined(isEnabledFiltering)) {
                  return JSON.parse(isEnabledFiltering);
                }
                return false;
              };

              vm.paginationOptions = {
                page : {
                  'first' : 1,
                  'max' : 25
                },
                sort : {
                  'field' : 'createDate',
                  'direction' : 'desc'
                },
                filters : {}
              };

              vm.colDefs = [
                  {
                    displayName : locMsg['label.id'],
                    field : 'id',
                    enableHiding : true,
                    visible : false,
                    headerCellClass : vm.highlightFilteredHeader,
                    type: 'number',
                    filters : [ {
                      condition : uiGridConstants.filter.EXACT
                    } ],
                    minWidth : 50
                  },
                  {
                    displayName : locMsg['log.log'],
                    field : 'log',
                    enableHiding : true,
                    enableSorting : false,
                    headerCellClass : vm.highlightFilteredHeader,
                    filters : [ {
                      condition : uiGridConstants.filter.CONTAINS
                    } ],
                    cellTemplate : '<div class="ui-grid-cell-contents {{row.entity.logLevel == 2 ? \'green\':(row.entity.logLevel == 1 ? \'yellow\':(row.entity.logLevel == 0 ? \'red\':\'\'))}}">{{ row.entity.log }}</div>',
                    minWidth : 100
                  },
                  {
                    displayName : locMsg['log.level'],
                    field : 'logLevel',
                    enableHiding : true,
                    headerCellClass : vm.highlightFilteredHeader,
                    filter : {
                      type : uiGridConstants.filter.SELECT,
                      selectOptions : constants.ACTION_LOG_LOG_LEVEL_SELECT_OPTIONS
                    },
                    filterHeaderTemplate :
                        '<div class="ui-grid-filter-container" ng-repeat="colFilter in col.filters">'
                        + '<div ng-if="colFilter.type === \'select\'" class="ng-scope">'
                        + '<select class="ui-grid-filter-select ui-grid-filter-input-0 ng-touched" ng-model="colFilter.term" ng-init="colFilter.term = colFilter.term == null ? \'\' : colFilter.term" ng-attr-placeholder="{{colFilter.placeholder || aria.defaultFilterLabel}}" ng-options="option.value as option.label for option in colFilter.selectOptions">'
                        + '</select>'
                        + '</div>'
                        + '</div>',
                    cellTemplate : '<div class="ui-grid-cell-contents {{row.entity.logLevel == 2 ? \'green\':(row.entity.logLevel == 1 ? \'yellow\':(row.entity.logLevel == 0 ? \'red\':\'\'))}}">{{"log.level." + row.entity.logLevel | i18n}}</div>',
                    minWidth : 100
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
                    filterHeaderTemplate :
                        '<div class="ui-grid-filter-container" ng-repeat="colFilter in col.filters">'
                        + '<div ng-if="colFilter.type === \'select\'" class="ng-scope">'
                        + '<select class="ui-grid-filter-select ui-grid-filter-input-0 ng-touched" ng-model="colFilter.term" ng-init="colFilter.term = colFilter.term == null ? \'\' : colFilter.term" ng-attr-placeholder="{{colFilter.placeholder || aria.defaultFilterLabel}}" ng-options="option.value as option.label for option in colFilter.selectOptions">'
                        + '</select>'
                        + '</div>'
                        + '</div>',
                    cellTemplate : '<div class="ui-grid-cell-contents">{{"log.level." + row.entity.logLevel | i18n}}</div>',
                    minWidth : 100
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
                    cellTemplate : '<div class="ui-grid-cell-contents {{row.entity.logLevel == 2 ? \'green\':(row.entity.logLevel == 1 ? \'yellow\':(row.entity.logLevel == 0 ? \'red\':\'\'))}}">{{row.entity.campaign.name}}</div>',
                    minWidth : 100
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
                    cellTemplate : '<div class="ui-grid-cell-contents {{row.entity.logLevel == 2 ? \'green\':(row.entity.logLevel == 1 ? \'yellow\':(row.entity.logLevel == 0 ? \'red\':\'\'))}}">{{row.entity.lsc.name}}</div>',
                    minWidth : 100
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
                    cellTemplate : '<div class="ui-grid-cell-contents {{row.entity.logLevel == 2 ? \'green\':(row.entity.logLevel == 1 ? \'yellow\':(row.entity.logLevel == 0 ? \'red\':\'\'))}}">{{row.entity.dnc.name}}</div>',
                    minWidth : 100
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
                    cellTemplate : '<div class="ui-grid-cell-contents {{row.entity.logLevel == 2 ? \'green\':(row.entity.logLevel == 1 ? \'yellow\':(row.entity.logLevel == 0 ? \'red\':\'\'))}}">{{row.entity.user.username}}</div>',
                    minWidth : 100
                  },
                  {
                    displayName : locMsg['common.tenant'],
                    field : 'tenant.name',
                    enableHiding : true,
                    visible : $rootScope.hasAnyRole([ 'ROLE_SUPERADMIN', 'ROLE_TENANT_ADMIN' ]),
                    headerCellClass : vm.highlightFilteredHeader,
                    filters : [ {
                      condition : uiGridConstants.filter.CONTAINS
                    } ],
                    cellTemplate : '<div class="ui-grid-cell-contents {{row.entity.logLevel == 2 ? \'green\':(row.entity.logLevel == 1 ? \'yellow\':(row.entity.logLevel == 0 ? \'red\':\'\'))}}">{{row.entity.tenant.name}}</div>',
                    minWidth : 100
                  },
                  {
                    displayName : locMsg['label.createDate'],
                    field : 'createDate',
                    enableHiding : true,
                    type: 'number',
                    filterHeaderTemplate : '<div class="ui-grid-filter-container" ng-repeat="colFilter in col.filters">'
                        + '<div class="dropdown">'
                        + '<a class="dropdown-toggle startDate-select" id="dLabel" role="button" data-toggle="dropdown" data-target="javascript:void(0)" href="">'
                        + '<div class="input-append">'
                        + '<input placeholder="{{\'campaign.schedule.from\' | i18n}}" id="campaign.startDate" type="text" class="ui-grid-filter-input" data-ng-model="col.grid.appScope.actionLog.startDate" ng-click="col.grid.appScope.actionLog.beforeRender()" ng-format-date="dd/MM/yyyy HH:mm" readonly />'
                        + '<div class="ui-grid-filter-button ng-scope" ng-show="col.grid.appScope.actionLog.startDate" ng-click="col.grid.appScope.actionLog.clearFilterDate(32, col.field); col.grid.appScope.actionLog.startDate = null"><i class="ui-grid-icon-cancel" style="margin-top: -10px; right: 0px">&nbsp;</i></div>'
                        + '</div>'
                        + '</a>'
                        + '<ul class="dropdown-menu dropdown-menu-left" role="menu" aria-labelledby="dLabel">'
                        + '<datetimepicker fixdatepicker="campaign.startDate" data-on-set-time="col.grid.appScope.actionLog.parseFilterDate(newDate, oldDate, 32, col.field);" data-ng-model="col.grid.appScope.actionLog.startDate" data-datetimepicker-config="{ dropdownSelector: \'.startDate-select\' }"></datetimepicker>'
                        + '</ul>'
                        + '</div>'
                        + '<div class="dropdown" style="padding-top: 8px;">'
                        + '<a class="dropdown-toggle endDate-select" id="dLabel" role="button" data-toggle="dropdown" data-target="javascript:void(0)" href="">'
                        + '<div class="input-append">'
                        + '<input placeholder="{{\'campaign.schedule.till\' | i18n}}" id="campaign.endDate" type="text" class="ui-grid-filter-input" data-ng-model="col.grid.appScope.actionLog.endDate" ng-format-date="dd/MM/yyyy HH:mm" readonly/>'
                        + '<div class="ui-grid-filter-button ng-scope" ng-show="col.grid.appScope.actionLog.endDate" ng-click="col.grid.appScope.actionLog.clearFilterDate(256, col.field); col.grid.appScope.actionLog.endDate = null" ><i class="ui-grid-icon-cancel" style="margin-top: -10px; right: 0px">&nbsp;</i></div>'
                        + '</div>'
                        + '</a>'
                        + '<ul class="dropdown-menu dropdown-menu-left" role="menu" aria-labelledby="dLabel">'
                        + '<datetimepicker fixdatepicker="campaign.endDate" data-on-set-time="col.grid.appScope.actionLog.parseFilterDate(newDate, oldDate, 256, col.field);" data-ng-model="col.grid.appScope.actionLog.endDate" data-datetimepicker-config="{ dropdownSelector: \'.endDate-select\' }"></datetimepicker>'
                        + '</ul>' + '</div>' + '</div>',
                    cellTemplate : '<div class="ui-grid-cell-contents {{row.entity.logLevel == 2 ? \'green\':(row.entity.logLevel == 1 ? \'yellow\':(row.entity.logLevel == 0 ? \'red\':\'\'))}}">{{row.entity.createDate | date : \'dd/MM/yyyy HH:mm\'}}</div>',
                    minWidth : 100
                  } ];

              vm.colDefs = actionLogFactory.hideTenantColumn(vm.colDefs);

              vm.regularGridStateName = "actionLogGrid";
              vm.defaultGridStateName = "defaultActionLogGrid";
              vm.actionLogGridOptions = {
                enablePaginationControls : true,
                paginationPageSizes : [ 25, 50, 75 ],
                paginationPageSize : 25,
                useExternalPagination : true,
                useExternalSorting : true,
                enableRowSelection : true,
                enableFiltering : vm.enableFiltering(),
                enableSelectAll : true,
                enableFullRowSelection : true,
                modifierKeysToMultiSelect : true,
                enableGridMenu : true,
                exporterCsvColumnSeparator : ';',
                exporterOlderExcelCompatibility : true,
                exporterCsvFilename : $rootScope.getExportFilename('action logs', 'csv'),
                exporterPdfFilename : $rootScope.getExportFilename('action logs', 'pdf'),
                exporterPdfOrientation : 'landscape',
                exporterPdfPageSize : 'A4',
                exporterPdfMaxGridWidth : 660,
                exporterPdfCustomFormatter : $rootScope.exporterPdfCustomFormatter,
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

              vm.startDate = null;
              vm.endDate = null;

              vm.clearFilterDate = function(condition, field) {
                var filtersChanged = addCustomFilter(vm.paginationOptions.filters, '', field, condition);
                if (filtersChanged)
                  vm.getPage();
              };

              vm.parseFilterDate = function(newDate, oldDate, condition, field) {
                var filtersChanged = addCustomFilter(vm.paginationOptions.filters, $filter('date')(newDate, 'dd/MM/yyyy HH:mm', 0), field, condition);
                if (filtersChanged)
                  vm.getPage();
              };

              i18nService.setCurrentLang(locMsg['lang.id']);

              vm.toggleFiltering = function() {
                vm.actionLogGridOptions.enableFiltering = !vm.actionLogGridOptions.enableFiltering;
                localStorageService.set('enableFiltering_actionLog', vm.actionLogGridOptions.enableFiltering);
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
                var onSelect = function(row) {
                  vm.selectedActionLogs = vm.gridApi.selection.getSelectedRows();
                };
                vm.gridApi.selection.on.rowSelectionChanged($scope, onSelect);
                vm.gridApi.selection.on.rowSelectionChangedBatch($scope, onSelect);

                vm.gridApi.core.on.sortChanged($scope, function(grid, sortColumns) {

                  gridStateService.save(vm.regularGridStateName, vm.gridApi);

                  if (sortColumns.length == 0) {
                    vm.paginationOptions.sort = {};
                  } else {
                    vm.paginationOptions.sort['field'] = sortColumns[0].filter.alias ? sortColumns[0].filter.alias : sortColumns[0].field;
                    vm.paginationOptions.sort['direction'] = sortColumns[0].sort.direction;
                  }
                  vm.getPage();
                });

                vm.gridApi.core.on.filterChanged($scope, function() {

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
                        vm.paginationOptions.filters[this.filter.alias ? this.filter.alias : valueColumn.field] = filters;
                      } else {
                        if ((vm.paginationOptions.filters.hasOwnProperty(valueColumn.field) || vm.paginationOptions.filters.hasOwnProperty(this.filter.alias))
                            && valueColumn.field != 'createDate') {

                          delete vm.paginationOptions.filters[this.filter.alias ? this.filter.alias : valueColumn.field];

                        }
                      }

                    }

                  });

                  if (angular.isDefined(vm.filterTimeout)) {
                    $timeout.cancel(vm.filterTimeout);
                  }
                  vm.filterTimeout = $timeout(function() {
                    vm.getPage();
                  }, 500);

                });

                vm.gridApi.pagination.on.paginationChanged($scope, function(newPage, pageSize) {
                  vm.gridApi.selection.clearSelectedRows();
                  vm.paginationOptions.page['first'] = newPage;
                  vm.paginationOptions.page['max'] = pageSize;
                  vm.getPage();
                });

                vm.gridApi.colResizable.on.columnSizeChanged($scope, function() {
                  gridStateService.save(vm.regularGridStateName, vm.gridApi);
                });

                vm.gridApi.core.on.columnVisibilityChanged($scope, function(changedColumn) {
                  $scope.columnChanged = {
                    name : changedColumn.colDef.name,
                    visible : changedColumn.colDef.visible
                  };
                  //vm.gridApi.saveState here is old on restore! That's why we add timeout
                  $timeout(function() {
                    gridStateService.save(vm.regularGridStateName, vm.gridApi);
                  }, 50);
                });

                $timeout(function() {
                  gridStateService.saveDefaultStateIfNotExist(vm.defaultGridStateName, vm.gridApi);
                  gridStateService.restore(vm.regularGridStateName, vm.gridApi, vm);
                }, 10);
                vm.registerCustomFilterClearing();
              };

	            vm.registerCustomFilterClearing = function () {
		            var clearAllFilters = vm.gridApi.grid.clearAllFilters;
		            vm.gridApi.grid.clearAllFilters = function (refreshRows, clearConditions, clearFlags) {
			            clearAllFilters.apply(this, refreshRows, clearConditions, clearFlags);
			            vm.startDate = null;
			            vm.endDate = null;

			            vm.clearFilterDate(32, "createDate");
			            vm.clearFilterDate(256, "createDate");
		            };
	            };

	            vm.getPage = function() {

                actionLogFactory.getAll(vm.paginationOptions.page, vm.paginationOptions.sort, vm.paginationOptions.filters).then(function(data) {

                  vm.actionLogGridOptions.totalItems = data.totalItems;
                  vm.actionLogGridOptions.data = data.data;

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
                  templateUrl : 'pages/actionLogForm.html',
                  controller : 'actionLogFormCtrl as actionLog',
                  size : size,
                  resolve : {
                    item : function() {
                      return vm.item;
                    }
                  }
                });

                modalInstance.result.then(function(result) {
                  if (vm.isNew) {
                    vm.actionLogGridOptions.data.push(result);
                  } else {
                    angular.merge(vm.item, result);
                  }
                }, function() {
                  $log.info('Modal dismissed at: ' + new Date());
                });
              };

              vm.remove = function() {
                var ids = [];
                vm.gridApi.selection.getSelectedRows().forEach(function(item, i, arr) {
                  ids.push(item.id);
                });

                actionLogFactory.remove(ids).then(function(data) {
                  data.forEach(function(item, i, arr) {
                    vm.actionLogGridOptions.data = _.without(vm.actionLogGridOptions.data, _.findWhere(vm.actionLogGridOptions.data, {
                      id : item
                    }));
                  });
                }, function(response) {
                  $log.error(response);
                });
                vm.selectedActionLogs = null;
              };

              $scope.$on("$destroy", function(event) {
                if (angular.isDefined(vm.filterTimeout)) {
                  $timeout.cancel(vm.filterTimeout);
                }
              });

              vm.newActionLog = function() {
                return actionLogFactory.createNew();
              };

            }

        ]);
