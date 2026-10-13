app.controller('serviceStatusesCtrl', [ '$scope', '$http', 'uiGridConstants', '$log', 'i18nService', '$locale', '$rootScope', '$filter', 'locMsg', '$window', '$timeout',
    'localStorageService', 'gridStateService',
    function($scope, $http, uiGridConstants, $log, i18nService, $locale, $rootScope, $filter, locMsg, $window, $timeout, localStorageService, gridStateService) {
      var vm = this;
      vm.selectionIsEmpty = true;

      vm.highlightFilteredHeader = function(row, rowRenderIndex, col, colRenderIndex) {
        if (col.filters[0].term) {
          return 'header-filtered';
        } else {
          return '';
        }
      };

      vm.enableFiltering = function() {
        var isEnabledFiltering = localStorageService.get('enableFiltering_telephonyRc');
        if (!_.isNull(isEnabledFiltering) && !_.isUndefined(isEnabledFiltering)) {
          return JSON.parse(isEnabledFiltering);
        }
        return false;
      };

      var paginationOptions = {
        page : {},
        sort : {},
        filters : {}
      };

      vm.colDefs = [ {
        displayName : locMsg['telephonyRc.code'],
        field : 'code',
        enableHiding : false,
        headerCellClass : vm.highlightFilteredHeader,
        type : 'number',
        filters : [ {
          condition : uiGridConstants.filter.CONTAINS
        } ],
        minWidth : 100
      }, {
        displayName : locMsg['telephonyRc.description'],
        field : 'description',
        enableHiding : false,
        headerCellClass : vm.highlightFilteredHeader,
        filters : [ {
          condition : uiGridConstants.filter.CONTAINS
        } ],
        minWidth : 100
      } ];

      vm.regularGridStateName = "ServiceStatusesGrid";
      vm.defaultGridStateName = "defaultServiceStatusesGrid";
      vm.serviceStatusesGridOptions = {
        useExternalPagination : false,
        enableRowSelection : true,
        enableSelectAll : true,
        enableFiltering : vm.enableFiltering(),
        enableFullRowSelection : true,
        enableGridMenu : true,
        exporterCsvColumnSeparator : ';',
        exporterOlderExcelCompatibility : true,
        exporterCsvFilename : $rootScope.getExportFilename('service statuses', 'csv'),
        exporterPdfFilename : $rootScope.getExportFilename('service statuses', 'pdf'),
        exporterPdfOrientation : 'landscape',
        exporterPdfPageSize : 'A4',
        exporterPdfCustomFormatter : $rootScope.exporterPdfCustomFormatter,
        exporterPdfMaxGridWidth : 660,
        modifierKeysToMultiSelect : true,
        i18n : 'en',
        columnDefs : vm.colDefs
      };

      i18nService.setCurrentLang(locMsg['lang.id']);

      vm.toggleFiltering = function() {
        vm.serviceStatusesGridOptions.enableFiltering = !vm.serviceStatusesGridOptions.enableFiltering;
        localStorageService.set('enableFiltering_serviceStatuses', vm.serviceStatusesGridOptions.enableFiltering);
        vm.gridApi.core.notifyDataChange(uiGridConstants.dataChange.COLUMN);
        gridStateService.restore(vm.regularGridStateName, vm.gridApi, vm);
      };

      vm.serviceStatusesGridOptions.onRegisterApi = function(gridApi) {
        // set gridApi on scope
        vm.gridApi = gridApi;
        vm.serviceStatusesGridOptions.gridMenuCustomItems = $rootScope.getExportMenuItemsXLSX(vm.gridApi, 'service statuses');
        vm.serviceStatusesGridOptions.gridMenuCustomItems.push({
          title : locMsg['resetTable'],
          action : function() {
            gridStateService.reset(vm.defaultGridStateName, vm.regularGridStateName, vm.gridApi, vm);
          },
          order : 220
        });
        var onSelect = function(row) {
          if (vm.gridApi.selection.getSelectedRows().length == 0) {
            vm.selectionIsEmpty = true;
            vm.selectedtelephonyRc = {};
          } else {
            vm.selectionIsEmpty = false;
            vm.selectedtelephonyRc = vm.gridApi.selection.getSelectedRows()[0];
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

      vm.getPage = function() {

        $http.get("serviceStatuses/list").then(function(response) {
          vm.serviceStatusesGridOptions.totalItems = response.data.length;
          vm.serviceStatusesGridOptions.data =  response.data;
        });

      };

      vm.getPage();
    } ]);
