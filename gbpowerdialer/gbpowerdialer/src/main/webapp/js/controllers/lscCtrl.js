/**
 * Controller to show list of lscs /
 */
app.controller('lscCtrl', [
    '$scope',
    '$http',
    'uiGridConstants',
    '$log',
    '$modal',
    'lscFactory',
    'i18nService',
    '$locale',
    '$rootScope',
    '$filter',
    'locMsg',
    '$window',
    '$timeout',
    'adminLogsFactory',
    'localStorageService',
    'gridStateService',
    'constants',
    function($scope, $http, uiGridConstants, $log, $modal, lscFactory, i18nService, $locale, $rootScope, $filter, locMsg, $window, $timeout, adminLogsFactory, localStorageService,
             gridStateService, constants) {

      var vm = this;
      vm.selectedLSCs = null;

      vm.setEnabled = function(enabled) {
        saveEnabled(vm.gridApi.selection.getSelectedRows(), lscFactory, vm.lscGridOptions.data, 'lsc', enabled, function() {
          adminLogsFactory.enableDisableFromList(1, vm.gridApi.selection.getSelectedRows(), enabled);
        });
      };

      vm.highlightFilteredHeader = function(row, rowRenderIndex, col, colRenderIndex) {
        if (col.filters[0].term) {
          return 'header-filtered';
        } else {
          return '';
        }
      };

      vm.enableFiltering = function() {
        var isEnabledFiltering = localStorageService.get('enableFiltering_lsc');
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

      vm.colDefs = [ {
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
        displayName : locMsg['label.name'],
        field : 'name',
        enableHiding : false,
        headerCellClass : vm.highlightFilteredHeader,
        filters : [ {
          condition : uiGridConstants.filter.CONTAINS
        } ],
        minWidth : 100
      }, {
        displayName : locMsg['label.description'],
        field : 'description',
        enableHiding : false,
        headerCellClass : vm.highlightFilteredHeader,
        filters : [ {
          condition : uiGridConstants.filter.CONTAINS
        } ],
        minWidth : 100
      }, {
        displayName : locMsg['lsc.url'],
        field : 'url',
        enableHiding : false,
        enableSorting : false,
        headerCellClass : vm.highlightFilteredHeader,
        filters : [ {
          condition : uiGridConstants.filter.CONTAINS
        } ],
        minWidth : 100
      }, {
        displayName : locMsg['lsc.httpMethod'],
        field : 'httpMethod',
        enableHiding : false,
        headerCellClass : vm.highlightFilteredHeader,
        filter : {
          type : uiGridConstants.filter.SELECT,
          selectOptions : [ {
            value : '',
            label : locMsg['input.option.select']
          }, {
            value : 'GET',
            label : 'GET'
          }, {
            value : 'POST',
            label : 'POST'
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
        displayName : locMsg['lsc.replyType'],
        field : 'replyType',
        enableHiding : false,
        headerCellClass : vm.highlightFilteredHeader,
        filter : {
          type : uiGridConstants.filter.SELECT,
          selectOptions : constants.LSC_REPLY_TYPE_SELECT_OPTIONS
        },
        filterHeaderTemplate :
            '<div class="ui-grid-filter-container" ng-repeat="colFilter in col.filters">'
            + '<div ng-if="colFilter.type === \'select\'" class="ng-scope">'
            + '<select class="ui-grid-filter-select ui-grid-filter-input-0 ng-touched" ng-model="colFilter.term" ng-init="colFilter.term = colFilter.term == null ? \'\' : colFilter.term" ng-attr-placeholder="{{colFilter.placeholder || aria.defaultFilterLabel}}" ng-options="option.value as option.label for option in colFilter.selectOptions">'
            + '</select>'
            + '</div>'
            + '</div>',
        cellTemplate : '<div class="ui-grid-cell-contents">{{"lsc.replyType." + row.entity.replyType | i18n}}</div>',
        minWidth : 100
      }, {
        displayName : locMsg['lsc.replyKey'],
        field : 'replyKey',
        enableHiding : false,
        headerCellClass : vm.highlightFilteredHeader,
        filters : [ {
          condition : uiGridConstants.filter.CONTAINS
        } ],
        minWidth : 100
      }, {
        displayName : locMsg['lsc.replyResult'],
        field : 'replyKey',
        enableHiding : false,
        headerCellClass : vm.highlightFilteredHeader,
        filters : [ {
          condition : uiGridConstants.filter.CONTAINS
        } ],
        minWidth : 100
      }, {
        displayName : locMsg['common.tenant'],
        field : 'tenantListDTO.name',
        enableHiding : false,
        visible : $rootScope.hasAnyRole([ 'ROLE_SUPERADMIN', 'ROLE_TENANT_ADMIN' ]),
        headerCellClass : vm.highlightFilteredHeader,
        type: 'number',
        filter : {
          alias : 'tenant.name',
          condition : uiGridConstants.filter.CONTAINS
        },
        minWidth : 100
      }, {
        displayName : locMsg['lsc.global'],
        field : 'global',
        enableHiding : false,
        filter : {
          type : uiGridConstants.filter.SELECT,
          selectOptions : [ {
            value : '',
            label : locMsg['input.option.select']
          }, {
            value : 'true',
            label : 'True'
          }, {
            value : 'false',
            label : 'False'
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
        cellTemplate : '<input type="checkbox" ng-model="row.entity.global" ng-disabled="true"></input>',
        minWidth : 100
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
            label : 'True'
          }, {
            value : 'false',
            label : 'False'
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
      } ];

      vm.colDefs = lscFactory.hideTenantColumn(vm.colDefs);
      vm.regularGridStateName = "lscGrid";
      vm.defaultGridStateName = "defaultLscGrid";
      vm.lscGridOptions = {
        paginationPageSizes : [ 25, 50, 75 ],
        paginationPageSize : 25,
        useExternalPagination : true,
        useExternalSorting : true,
        useExternalFiltering : true,
        enableRowSelection : true,
        enableSelectAll : true,
        enableFiltering : vm.enableFiltering(),
        enableFullRowSelection : true,
        modifierKeysToMultiSelect : true,
        enableGridMenu : true,
        exporterCsvColumnSeparator : ';',
        exporterOlderExcelCompatibility : true,
        exporterCsvFilename : $rootScope.getExportFilename('lsc list', 'csv'),
        exporterPdfFilename : $rootScope.getExportFilename('lsc list', 'pdf'),
        exporterPdfOrientation : 'landscape',
        exporterPdfPageSize : 'A4',
        exporterPdfMaxGridWidth : 660,
        exporterPdfCustomFormatter : $rootScope.exporterPdfCustomFormatter,
        i18n : 'en',
        exporterFieldCallback : function(grid, row, col, value) {
          if (col.name == 'replyType') {
            return constants.LSC_REPLY_TYPE_SELECT_OPTIONS[parseInt(value) + 1].label;
          } else {
            return value;
          }
        },
        columnDefs : vm.colDefs
      };

      i18nService.setCurrentLang(locMsg['lang.id']);

      vm.toggleFiltering = function() {
        vm.lscGridOptions.enableFiltering = !vm.lscGridOptions.enableFiltering;
        localStorageService.set('enableFiltering_lsc', vm.lscGridOptions.enableFiltering);
        vm.gridApi.core.notifyDataChange(uiGridConstants.dataChange.COLUMN);
        gridStateService.restore(vm.regularGridStateName, vm.gridApi, vm);
      };

      vm.lscGridOptions.onRegisterApi = function(gridApi) {
        vm.gridApi = gridApi;
        vm.lscGridOptions.gridMenuCustomItems = $rootScope.getExportMenuItemsXLSX(vm.gridApi, 'lsc list');
        vm.lscGridOptions.gridMenuCustomItems.push({
          title : locMsg['resetTable'],
          action : function() {
            gridStateService.reset(vm.defaultGridStateName, vm.regularGridStateName, vm.gridApi, vm);
          },
          order : 220
        });
        var onSelect = function(row) {
          vm.selectedLSCs = vm.gridApi.selection.getSelectedRows();
        };
        vm.gridApi.selection.on.rowSelectionChanged($scope, onSelect);
        vm.gridApi.selection.on.rowSelectionChangedBatch($scope, onSelect);

        vm.gridApi.core.on.sortChanged($scope, function(grid, sortColumns) {

          gridStateService.save(vm.regularGridStateName, vm.gridApi);

          if (sortColumns.length == 0) {
            paginationOptions.sort = {};
          } else {
            paginationOptions.sort['field'] = sortColumns[0].filter.alias ? sortColumns[0].filter.alias : sortColumns[0].field;
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
                paginationOptions.filters[this.filter.alias ? this.filter.alias : valueColumn.field] = filters;
              } else {
                if (paginationOptions.filters.hasOwnProperty(valueColumn.field) || paginationOptions.filters.hasOwnProperty(this.filter.alias)) {
                  delete paginationOptions.filters[this.filter.alias ? this.filter.alias : valueColumn.field];
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

        lscFactory.getAll(paginationOptions.page, paginationOptions.sort, paginationOptions.filters).then(function(data) {

          vm.lscGridOptions.totalItems = data.totalItems;
          vm.lscGridOptions.data = data.data;

        });

      };

      vm.getPage();

      vm.open = function(size, item) {

        vm.item = item;

        // remember if item is new (to know if we need to push to lsc list or merge)
        vm.isNew = true;
        if (item.id != null) {
          vm.isNew = false;
        }

        // process open modal
        var modalInstance = $modal.open({
          animation : true,
          backdrop : 'static',
          // modal template page
          templateUrl : 'pages/lscForm.html',
          // modal page controller
          controller : 'lscFormCtrl as lsc',
          size : size,
          resolve : {
            item : function() {
              // pass in selected lsc to modal
              return vm.item;
            }
          }
        });

        // finally receive result
        modalInstance.result.then(function(result) {
          // modal result handler
          // if item is new - push it to list
          adminLogsFactory.check(vm.item, result, 1, null, null, null);
          if (vm.isNew) {
            vm.lscGridOptions.data.push(result);
          } else {
            // if item is updated - merge with original item(to update in grid)
            angular.merge(vm.item, result);
          }
        }, function() {
          $log.info('Modal dismissed at: ' + new Date());
        });
      };

      vm.remove = function() {

        var ids = [];
        var objects = [];
        vm.gridApi.selection.getSelectedRows().forEach(function(item, i, arr) {
          ids.push(item.id);
          objects.push(item);
        });

        lscFactory.remove(ids).then(function(data) {
          data.forEach(function(item, i, arr) {
            vm.lscGridOptions.data = _.without(vm.lscGridOptions.data, _.findWhere(vm.lscGridOptions.data, {
              id : item
            }));
            adminLogsFactory.proccessList(_.find(objects, function(num) {
              return num.id === item;
            }), 1, 2);
          });
          vm.selectedLSCs = null;
        }, function(response) {
          $log.error(response);
        });
      };

      $scope.$on("$destroy", function(event) {
        if (angular.isDefined(vm.filterTimeout)) {
          $timeout.cancel(vm.filterTimeout);
        }
      });

      vm.newLsc = function() {
        return lscFactory.createNew();
      };

    }

]);
