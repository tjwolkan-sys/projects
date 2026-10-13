/**
 * Controller to show list of adminLogs /
 */
app.controller('adminLogsCtrl', [
    '$scope',
    '$http',
    'uiGridConstants',
    '$log',
    '$modal',
    'i18nService',
    '$locale',
    'adminLogsFactory',
    '$rootScope',
    '$filter',
    'locMsg',
    '$window',
    '$timeout',
    'uiGridConstants',
    'localStorageService',
    'gridStateService',
    'constants',
    function($scope, $http, uiGridConstants, $log, $modal, i18nService, $locale, adminLogsFactory, $rootScope, $filter, locMsg, $window, $timeout, uiGridConstants,
             localStorageService, gridStateService, constants) {

      var vm = this;

      vm.enableFiltering = function() {
        var isEnabledFiltering = localStorageService.get('enableFiltering_adminLogs');
        if (!_.isNull(isEnabledFiltering) && !_.isUndefined(isEnabledFiltering)) {
          return JSON.parse(isEnabledFiltering);
        }
        return false;
      };

      vm.selectedAdminLog = null;
      vm.regularGridStateName = "adminLogsGrid";
      vm.defaultGridStateName = "defaultAdminLogsGrid";
      vm.adminLogsGridOptions = {
        paginationPageSizes : [ 25, 50, 75 ],
        paginationPageSize : 25,
        useExternalPagination : true,
        useExternalSorting : true,
        useExternalFiltering : true,
        enableRowSelection : true,
        enableSelectAll : false,
        enableFiltering : vm.enableFiltering(),
        enableFullRowSelection : true,
        multiSelect : false,
        modifierKeysToMultiSelect : true,
        enableGridMenu : true,
        exporterCsvColumnSeparator : ';',
        exporterOlderExcelCompatibility : true,
        exporterCsvFilename : $rootScope.getExportFilename('admin logs', 'csv'),
        exporterPdfFilename : $rootScope.getExportFilename('admin logs', 'pdf'),
        exporterPdfOrientation : 'landscape',
        exporterPdfPageSize : 'A4',
        exporterPdfMaxGridWidth : 660,
        exporterPdfCustomFormatter : $rootScope.exporterPdfCustomFormatter,
        i18n : 'en',
        exporterFieldCallback : function(grid, row, col, value) {
          if (col.name == 'actionType') {
            return constants.ADMIN_LOGS_ACTION_TYPE_SELECT_OPTIONS[parseInt(value) + 1].label;
          } else if (col.name == 'actionTarget') {
            return constants.ADMIN_LOGS_ACTION_TARGET_SELECT_OPTIONS[parseInt(value) + 1].label;
          } else if (col.name == 'actionDate') {
            return $filter('date')(value, 'dd/MM/yyyy HH:mm:ss');
          } else {
            return value;
          }
        },
        columnDefs : [ {
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
        }, {
          displayName : locMsg['admin.logs.user'],
          field : 'user.username',
          enableHiding : false,
          headerCellClass : vm.highlightFilteredHeader,
          filters : [ {
            condition : uiGridConstants.filter.CONTAINS
          } ],
          minWidth : 100
        }, {
          displayName : locMsg['admin.logs.action.type'],
          field : 'actionType',
          enableHiding : false,
          headerCellClass : vm.highlightFilteredHeader,
          filter : {
            type : uiGridConstants.filter.SELECT,
            selectOptions : constants.ADMIN_LOGS_ACTION_TYPE_SELECT_OPTIONS
          },
          filterHeaderTemplate :
              '<div class="ui-grid-filter-container" ng-repeat="colFilter in col.filters">'
              + '<div ng-if="colFilter.type === \'select\'" class="ng-scope">'
              + '<select class="ui-grid-filter-select ui-grid-filter-input-0 ng-touched" ng-model="colFilter.term" ng-init="colFilter.term = colFilter.term == null ? \'\' : colFilter.term" ng-attr-placeholder="{{colFilter.placeholder || aria.defaultFilterLabel}}" ng-options="option.value as option.label for option in colFilter.selectOptions">'
              + '</select>'
              + '</div>'
              + '</div>',
          cellTemplate : '<div class="ui-grid-cell-contents">{{"admin.logs.action.type." + row.entity.actionType | i18n}}</div>',
          minWidth : 100
        }, {
          displayName : locMsg['admin.logs.action.target'],
          field : 'actionTarget',
          enableHiding : false,
          headerCellClass : vm.highlightFilteredHeader,
          filter : {
            type : uiGridConstants.filter.SELECT,
            selectOptions : constants.ADMIN_LOGS_ACTION_TARGET_SELECT_OPTIONS
          },
          filterHeaderTemplate :
              '<div class="ui-grid-filter-container" ng-repeat="colFilter in col.filters">'
              + '<div ng-if="colFilter.type === \'select\'" class="ng-scope">'
              + '<select class="ui-grid-filter-select ui-grid-filter-input-0 ng-touched" ng-model="colFilter.term" ng-init="colFilter.term = colFilter.term == null ? \'\' : colFilter.term" ng-attr-placeholder="{{colFilter.placeholder || aria.defaultFilterLabel}}" ng-options="option.value as option.label for option in colFilter.selectOptions">'
              + '</select>'
              + '</div>'
              + '</div>',
          cellTemplate : '<div class="ui-grid-cell-contents">{{"admin.logs.action.target." + row.entity.actionTarget | i18n}}</div>',
          minWidth : 100
        }, {
          displayName : locMsg['admin.logs.action.value'],
          field : 'actionValue',
          enableHiding : false,
          headerCellClass : vm.highlightFilteredHeader,
          type: 'number',
          filters : [ {
            condition : uiGridConstants.filter.EQUALS
          } ],
          minWidth : 100
        }, {
          displayName: locMsg['admin.logs.action.valueName'],
          field: 'actionValueName',
          enableHiding: false,
          headerCellClass: vm.highlightFilteredHeader,
          filters: [{
            condition: uiGridConstants.filter.CONTAINS
          }],
          minWidth: 100
        }, {
          displayName: locMsg['admin.logs.action.date'],
          field: 'actionDate',
          enableHiding: false,
          headerCellClass: vm.highlightFilteredHeader,
          type: 'number',
          filterHeaderTemplate: '<div class="ui-grid-filter-container" ng-repeat="colFilter in col.filters">' +
              '<div class="dropdown">' +
              '<a class="dropdown-toggle startActionDate-select" id="startDateLabel" role="button" data-toggle="dropdown" data-target="javascript:void(0)" href="">' +
              '<div class="input-append">' +
              '<input id="startActionDate" placeholder="{{\'campaign.schedule.from\' | i18n}}" type="text" class="ui-grid-filter-input"' +
              ' data-ng-model="col.grid.appScope.admLogs.startActionDate"' +
              ' ng-format-date="dd/MM/yyyy HH:mm" readonly/>' +
              '<div class="ui-grid-filter-button ng-scope" ng-show="col.grid.appScope.admLogs.startActionDate"' +
              ' ng-click="col.grid.appScope.admLogs.clearFilterDate(32, col.field); col.grid.appScope.admLogs.startActionDate = null">' +
              '<i class="ui-grid-icon-cancel" style="margin-top: -10px; right: 0px">&nbsp;</i>' +
              '</div></div></a>' +
              '<ul class="dropdown-menu dropdown-menu-left" role="menu" aria-labelledby="startDateLabel">' +
              '<datetimepicker fixdatepicker="startActionDate" data-on-set-time="col.grid.appScope.admLogs.parseFilterDate(newDate, oldDate, 32, col.field);"' +
              ' data-ng-model="col.grid.appScope.admLogs.startActionDate"' +
              ' data-datetimepicker-config="{ dropdownSelector: \'.startActionDate-select\' }">' +
              '</datetimepicker></ul></div>' +
              '<div class="dropdown" style="padding-top: 8px;">' +
              '<a class="dropdown-toggle endActionDate-select" id="endDateLabel" role="button" data-toggle="dropdown" data-target="javascript:void(0)" href="">' +
              '<div class="input-append">' +
              '<input id="endActionDate" placeholder="{{\'campaign.schedule.till\' | i18n}}" type="text" class="ui-grid-filter-input"' +
              ' data-ng-model="col.grid.appScope.admLogs.endActionDate"' +
              ' ng-format-date="dd/MM/yyyy HH:mm" readonly/>' +
              '<div class="ui-grid-filter-button ng-scope" ng-show="col.grid.appScope.admLogs.endActionDate"' +
              ' ng-click="col.grid.appScope.admLogs.clearFilterDate(256, col.field); col.grid.appScope.admLogs.endActionDate = null">' +
              '<i class="ui-grid-icon-cancel" style="margin-top: -10px; right: 0px">&nbsp;</i>' +
              '</div></div></a>' +
              '<ul class="dropdown-menu dropdown-menu-left" role="menu" aria-labelledby="endDateLabel">' +
              '<datetimepicker fixdatepicker="endActionDate" data-on-set-time="col.grid.appScope.admLogs.parseFilterDate(newDate, oldDate, 256, col.field);"' +
              ' data-ng-model="col.grid.appScope.admLogs.endActionDate"' +
              ' data-datetimepicker-config="{ dropdownSelector: \'.endActionDate-select\' }">' +
              '</datetimepicker></ul></div></div>',
          cellTemplate: '<div class="ui-grid-cell-contents">{{row.entity.actionDate | date:"dd/MM/yyyy HH:mm:ss"}}</div>',
          minWidth: 100
        }, {
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
        }]
      };

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

      vm.adminLogsGridOptions.onRegisterApi = function(gridApi) {
        // set gridApi on scope
        vm.gridApi = gridApi;
        vm.adminLogsGridOptions.gridMenuCustomItems = $rootScope.getExportMenuItemsXLSX(vm.gridApi, 'admin logs');
        vm.adminLogsGridOptions.gridMenuCustomItems.push({
          title: locMsg['resetTable'],
          action: function() {
            gridStateService.reset(vm.defaultGridStateName, vm.regularGridStateName, vm.gridApi, vm);
          },
          order: 220
        });
        vm.gridApi.selection.on.rowSelectionChanged($scope, function(row) {
          if (vm.gridApi.selection.getSelectedRows().length === 1) {
            vm.selectedAdminLog = vm.gridApi.selection.getSelectedRows()[0];
          } else {
            vm.selectedAdminLog = null;
          }
        });

        vm.gridApi.pagination.on.paginationChanged($scope, function(newPage, pageSize) {
          vm.gridApi.selection.clearSelectedRows();
          vm.paginationOptions.page['first'] = newPage;
          vm.paginationOptions.page['max'] = pageSize;
          vm.getPage();
        });

        vm.gridApi.core.on.sortChanged($scope, function(grid, sortColumns) {

          gridStateService.save(vm.regularGridStateName, vm.gridApi);

          if (sortColumns.length == 0) {
            vm.paginationOptions.sort = {};
          } else {
            vm.paginationOptions.sort['field'] = sortColumns[0].field;
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
                vm.paginationOptions.filters[valueColumn.field] = filters;
              } else {
                if (vm.paginationOptions.filters.hasOwnProperty(valueColumn.field)) {
                  delete vm.paginationOptions.filters[valueColumn.field];
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

      };

      vm.toggleFiltering = function() {
        vm.adminLogsGridOptions.enableFiltering = !vm.adminLogsGridOptions.enableFiltering;
        localStorageService.set('enableFiltering_adminLogs', vm.adminLogsGridOptions.enableFiltering);
        vm.gridApi.core.notifyDataChange(uiGridConstants.dataChange.COLUMN);
        gridStateService.restore(vm.regularGridStateName, vm.gridApi, vm);
      };

      vm.open = function(size, item) {
        vm.item = item;

        var modalInstance = $modal.open({
          animation: true,
          backdrop: 'static',
          // modal template page
          templateUrl: 'pages/adminLogsDetails.html',
          // modal page controller
          controller: 'adminLogsDetailsCtrl as admLogsDetails',
          size: size,
          resolve: {
            item: function() {
              return vm.item;
            }
          }
        });

      };

      vm.paginationOptions = {
        page: {
          'first': 1,
          'max': 25
        },
        sort: {
          'field': 'actionDate',
          'direction': uiGridConstants.DESC
        },
        filters: {}
      };

      vm.getPage = function() {

        adminLogsFactory.getAll(vm.paginationOptions.page, vm.paginationOptions.sort, vm.paginationOptions.filters).then(function(data) {

          vm.adminLogsGridOptions.totalItems = data.totalItems;
          vm.adminLogsGridOptions.data = data.data;

        });

      };

      vm.getPage();

      vm.actionTypes = [ {
        value : 0,
        label : 'admin.logs.action.type.0'
      }, {
        value : 1,
        label : 'admin.logs.action.type.1'
      }, {
        value : 2,
        label : 'admin.logs.action.type.2'
      } ];

      vm.actionTargets = [ {
        value : 0,
        label : 'admin.logs.action.target.0'
      }, {
        value : 1,
        label : 'admin.logs.action.target.1'
      }, {
        value : 2,
        label : 'admin.logs.action.target.2'
      } ];

    } ]);
