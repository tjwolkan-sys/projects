/**
 * Controller to show list of servers
 */
app
  .controller(
    'serverCtrl', 
    [ 
      '$scope', 
      '$http', 
      'uiGridConstants', 
      '$log', 
      'serverFactory', 
      'i18nService', 
      '$locale', 
      '$rootScope', 
      '$filter', 
      'locMsg', 
      '$window', 
      '$timeout',
      'localStorageService',
      'gridStateService',
    function($scope, $http, uiGridConstants, $log, serverFactory, i18nService, $locale, $rootScope, $filter, locMsg, $window, $timeout, localStorageService, gridStateService) {

      var vm = this;
      vm.selectionIsEmpty = true;
      vm.selectedServer = {};

      vm.highlightFilteredHeader = function(row, rowRenderIndex, col, colRenderIndex) {
        if (col.filters[0].term) {
          return 'header-filtered';
        } else {
          return '';
        }
      };

      vm.enableFiltering = function() {
        var isEnabledFiltering = localStorageService.get('enableFiltering_server');
        if (!_.isNull(isEnabledFiltering) && !_.isUndefined(isEnabledFiltering)) {
          return JSON.parse(isEnabledFiltering);
        }
        return false;
      };

      var paginationOptions = {
        page : {
          'first' : 1,
          'max' : 25
        },
        sort : {},
        filters : {}
      };

      vm.regularGridStateName = "serverGrid";
      vm.defaultGridStateName = "defaultServerGrid";
      vm.serverGridOptions = {
        paginationPageSizes : [ 25, 50, 75 ],
        paginationPageSize : 25,
        useExternalPagination : true,
        enableRowSelection : true,
        enableSelectAll : true,
        enableFiltering : vm.enableFiltering(),
        enableFullRowSelection : true,
        enableGridMenu : true,
        exporterCsvColumnSeparator : ';',
        exporterOlderExcelCompatibility : true,
        exporterCsvFilename : $rootScope.getExportFilename('servers', 'csv'),
        exporterPdfFilename : $rootScope.getExportFilename('servers', 'pdf'),
        exporterPdfOrientation : 'landscape',
        exporterPdfPageSize : 'A4',
        exporterPdfCustomFormatter : $rootScope.exporterPdfCustomFormatter,
        exporterPdfMaxGridWidth : 660,
        modifierKeysToMultiSelect : true,
        i18n : 'en',
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
          displayName : locMsg['server.name'],
          field : 'name',
          enableHiding : false,
          headerCellClass : vm.highlightFilteredHeader,
          filters : [ {
            condition : uiGridConstants.filter.CONTAINS
          } ],
          minWidth : 100
        }, {
          displayName : locMsg['server.ip'],
          field : 'ip',
          enableHiding : false,
          headerCellClass : vm.highlightFilteredHeader,
          filters : [ {
            condition : uiGridConstants.filter.CONTAINS
          } ],
          minWidth : 100
        }, {
          displayName : locMsg['server.domain'],
          field : 'domain',
          enableHiding : false,
          headerCellClass : vm.highlightFilteredHeader,
          filters : [ {
            condition : uiGridConstants.filter.CONTAINS
          } ],
          minWidth : 100
        }, {
          displayName : locMsg['server.type'],
          field : 'type',
          enableHiding : false,
          headerCellClass : vm.highlightFilteredHeader,
          filter : {
            type : uiGridConstants.filter.SELECT,
            selectOptions : [ {
              value : '',
              label : locMsg['input.option.select']
            }, {
              value : 'REGISTRAR',
              label : locMsg['server.type.registrar']
            }, {
              value : 'MEDIA',
              label : locMsg['server.type.media']
            }, {
              value : 'CLUSTER',
              label : locMsg['server.type.cluster']
            }, {
              value : 'GATE',
              label : locMsg['server.type.gate']
            } ]
          },
          filterHeaderTemplate :
              '<div class="ui-grid-filter-container" ng-repeat="colFilter in col.filters">'
              + '<div ng-if="colFilter.type === \'select\'" class="ng-scope">'
              + '<select class="ui-grid-filter-select ui-grid-filter-input-0 ng-touched" ng-model="colFilter.term" ng-init="colFilter.term = colFilter.term == null ? \'\' : colFilter.term" ng-attr-placeholder="{{colFilter.placeholder || aria.defaultFilterLabel}}" ng-options="option.value as option.label for option in colFilter.selectOptions">'
              + '</select>'
              + '</div>'
              + '</div>',
          minWidth : 100
        }, {
          displayName : locMsg['server.connectionState'],
          field : 'connectionState',
          enableHiding : false,
          headerCellClass : vm.highlightFilteredHeader,
          filters : [ {
            condition : uiGridConstants.filter.CONTAINS
          } ],
          minWidth : 100
        }, {
          displayName : locMsg['server.cpsLimit'],
          field : 'cpsLimit',
          enableHiding : false,
          headerCellClass : vm.highlightFilteredHeader,
          type: 'number',
          filters : [ {
            condition : uiGridConstants.filter.GREATER_THAN,
            placeholder : locMsg['placeholder.greaterThan']
          }, {
            condition : uiGridConstants.filter.LESS_THAN,
            placeholder : locMsg['placeholder.lessThan']
          } ],
          minWidth : 100
        }, {
          displayName : locMsg['server.maxLines'],
          field : 'maxLines',
          enableHiding : false,
          headerCellClass : vm.highlightFilteredHeader,
          type: 'number',
          filters : [ {
            condition : uiGridConstants.filter.GREATER_THAN,
            placeholder : locMsg['placeholder.greaterThan']
          }, {
            condition : uiGridConstants.filter.LESS_THAN,
            placeholder : locMsg['placeholder.lessThan']
          } ],
          minWidth : 100
        }, {
          displayName : locMsg['server.inUse'],
          field : 'inUse',
          enableHiding : false,
          filter : {
            type : uiGridConstants.filter.SELECT,
            selectOptions : [ {
              value : '',
              label : locMsg['input.option.select']
            }, {
              value : 'true',
              label : locMsg['label.true']
            }, {
              value : 'false',
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
          headerCellClass : vm.highlightFilteredHeader,
          cellTemplate : '<input type="checkbox" ng-model="row.entity.inUse" ng-disabled="true"></input>',
          minWidth : 50
        }, {
          displayName : locMsg['label.enabled'],
          field : 'enabled',
          enableHiding : false,
          filter : {
            type : uiGridConstants.filter.SELECT,
            selectOptions : [ {
              value : '',
              label : locMsg['input.option.select']
            }, {
              value : 'true',
              label : locMsg['label.true']
            }, {
              value : 'false',
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
          headerCellClass : vm.highlightFilteredHeader,
          cellTemplate : '<input type="checkbox" ng-model="row.entity.enabled" ng-disabled="true"></input>',
          minWidth : 50
        } ]
      };

      i18nService.setCurrentLang(locMsg['lang.id']);

      vm.toggleFiltering = function() {
        vm.serverGridOptions.enableFiltering = !vm.serverGridOptions.enableFiltering;
        localStorageService.set('enableFiltering_server', vm.serverGridOptions.enableFiltering);
        vm.gridApi.core.notifyDataChange(uiGridConstants.dataChange.COLUMN);
        gridStateService.restore(vm.regularGridStateName, vm.gridApi, vm);
      };

      vm.serverGridOptions.onRegisterApi = function(gridApi) {
        vm.gridApi = gridApi;
        vm.serverGridOptions.gridMenuCustomItems = $rootScope.getExportMenuItemsXLSX(vm.gridApi, 'servers');
        vm.serverGridOptions.gridMenuCustomItems.push({
          title : locMsg['resetTable'],
          action: function () {
            gridStateService.reset(vm.defaultGridStateName, vm.regularGridStateName, vm.gridApi, vm);
          },
          order: 220
        });
        var onSelect = function(row) {
          if (vm.gridApi.selection.getSelectedRows().length == 0) {
            vm.selectionIsEmpty = true;
            vm.selectedServer = {};
          } else {
            vm.selectionIsEmpty = false;
            vm.selectedServer = vm.gridApi.selection.getSelectedRows()[0];
          }
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
                paginationOptions.filters[valueColumn.field] = filters;
              } else {
                if (paginationOptions.filters.hasOwnProperty(valueColumn.field)) {
                  delete paginationOptions.filters[valueColumn.field];
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
          paginationOptions.page['first'] = newPage;
          paginationOptions.page['max'] = pageSize;
          vm.getPage();
        });

        vm.gridApi.colResizable.on.columnSizeChanged($scope, function () {
          gridStateService.save(vm.regularGridStateName, vm.gridApi);
        });

        vm.gridApi.core.on.columnVisibilityChanged($scope, function (changedColumn) {
          $scope.columnChanged = {
            name: changedColumn.colDef.name,
            visible: changedColumn.colDef.visible
          };
          //vm.gridApi.saveState here is old on restore! That's why we add timeout
          $timeout(function () {
            gridStateService.save(vm.regularGridStateName, vm.gridApi);
          }, 50);
        });

        $timeout(function () {
          gridStateService.saveDefaultStateIfNotExist(vm.defaultGridStateName, vm.gridApi);
          gridStateService.restore(vm.regularGridStateName, vm.gridApi, vm);
        }, 10);

      };

      vm.getPage = function() {

        serverFactory.getAll(paginationOptions.page, paginationOptions.sort, paginationOptions.filters).then(function(data) {

          vm.serverGridOptions.totalItems = data.totalItems;
          vm.serverGridOptions.data = data.data;

        });

      };

      vm.getPage();

      vm.connect = function() {
        serverFactory.connect(vm.selectedServer.id, paginationOptions.page, paginationOptions.sort, paginationOptions.filters).then(function(data) {
          vm.serverGridOptions.totalItems = data.totalItems;
          vm.serverGridOptions.data = data.data;
          vm.selectedServer = {};
        });
      };

      vm.disconnect = function() {
        serverFactory.disconnect(vm.selectedServer.ip, paginationOptions.page, paginationOptions.sort, paginationOptions.filters).then(function(data) {
          vm.serverGridOptions.totalItems = data.totalItems;
          vm.serverGridOptions.data = data.data;
          vm.selectedServer = {};
        });
      };

      vm.isNeedDisableConnectBtn = function() {
        return !vm.selectedServer.enabled || vm.selectedServer.connectionState == "CONNECTED" || vm.selectedServer.connectionState == "RECONNECTING";
      };

      vm.isNeedDisableDisconnectBtn = function() {
        return !vm.selectedServer.enabled || vm.selectedServer.connectionState != "CONNECTED" && vm.selectedServer.connectionState != "RECONNECTING";
      }
    }

]);
