/**
 * Controller to show list of dncs /
 */
app.controller('dncCtrl', [ '$scope', 'uiGridConstants', '$log', '$modal', 'dncFactory', 'i18nService', 'locMsg', '$timeout', 'alertService', 'adminLogsFactory', '$rootScope',
    'localStorageService', 'gridStateService',
    function($scope, uiGridConstants, $log, $modal, dncFactory, i18nService, locMsg, $timeout, alertService, adminLogsFactory, $rootScope, localStorageService, gridStateService) {
      var vm = this;
      vm.selectedDNCs = null;

      vm.setEnabled = function(enabled) {
        saveEnabled(vm.gridApi.selection.getSelectedRows(), dncFactory, vm.dncGridOptions.data, 'dnc', enabled, function() {
          adminLogsFactory.enableDisableFromList(2, vm.gridApi.selection.getSelectedRows(), enabled);
        });
      };

      vm.highlightFilteredHeader = function(row, rowRenderIndex, col, colRenderIndex) {
        if (col.filters[0].term) {
          return 'header-filtered';
        } else {
          return '';
        }
      };

      var paginationOptions = {
        page : {
          'first' : 1,
          'max' : 25
        },
        sort : {},
        filters : {}
      };

      vm.enableFiltering = function() {
        var isEnabledFiltering = localStorageService.get('enableFiltering_dnc');
        if (!_.isNull(isEnabledFiltering) && !_.isUndefined(isEnabledFiltering)) {
          return JSON.parse(isEnabledFiltering);
        }
        return false;
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
        displayName : locMsg['campaign.label'],
        field : 'campaign.name',
        enableHiding : false,
        headerCellClass : vm.highlightFilteredHeader,
        filters : [ {
          condition : uiGridConstants.filter.CONTAINS
        } ],
        minWidth : 100
      }, {
        displayName : locMsg['dnc.global'],
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
        displayName : locMsg['common.tenant'],
        field : 'tenantListDTO.name',
        enableHiding : false,
        visible : $rootScope.hasAnyRole([ 'ROLE_SUPERADMIN', 'ROLE_TENANT_ADMIN','ROLE_SUPERVISOR' ]),
        headerCellClass : vm.highlightFilteredHeader,
        filter : {
          alias : 'tenant.name',
          condition : uiGridConstants.filter.CONTAINS
        },
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
          } ],
          minWidth : 50
        },
        filterHeaderTemplate :
            '<div class="ui-grid-filter-container" ng-repeat="colFilter in col.filters">'
            + '<div ng-if="colFilter.type === \'select\'" class="ng-scope">'
            + '<select class="ui-grid-filter-select ui-grid-filter-input-0 ng-touched" ng-model="colFilter.term" ng-init="colFilter.term = colFilter.term == null ? \'\' : colFilter.term" ng-attr-placeholder="{{colFilter.placeholder || aria.defaultFilterLabel}}" ng-options="option.value as option.label for option in colFilter.selectOptions">'
            + '</select>'
            + '</div>'
            + '</div>',
        headerCellClass : vm.highlightFilteredHeader,
        cellTemplate : '<input type="checkbox" ng-model="row.entity.enabled" ng-disabled="true"></input>'
      } ];

      vm.colDefs = dncFactory.hideTenantColumn(vm.colDefs);
      vm.regularGridStateName = "dncGrid";
      vm.defaultGridStateName = "defaultDncGrid";
      vm.dncGridOptions = {
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
        exporterCsvFilename : $rootScope.getExportFilename('dnc list', 'csv'),
        exporterPdfFilename : $rootScope.getExportFilename('dnc list', 'pdf'),
        exporterPdfOrientation : 'landscape',
        exporterPdfPageSize : 'A4',
        exporterPdfMaxGridWidth : 660,
        i18n : 'en',
        columnDefs : vm.colDefs
      };

      i18nService.setCurrentLang(locMsg['lang.id']);

      vm.toggleFiltering = function() {
        vm.dncGridOptions.enableFiltering = !vm.dncGridOptions.enableFiltering;
        localStorageService.set('enableFiltering_dnc', vm.dncGridOptions.enableFiltering);
        vm.gridApi.core.notifyDataChange(uiGridConstants.dataChange.COLUMN);
        gridStateService.restore(vm.regularGridStateName, vm.gridApi, vm);
      };

      vm.dncGridOptions.onRegisterApi = function(gridApi) {
        // set gridApi on scope
        vm.gridApi = gridApi;
        vm.dncGridOptions.gridMenuCustomItems = $rootScope.getExportMenuItemsXLSX(vm.gridApi, 'dnc list');
        vm.dncGridOptions.gridMenuCustomItems.push({
          title : locMsg['resetTable'],
          action : function() {
            gridStateService.reset(vm.defaultGridStateName, vm.regularGridStateName, vm.gridApi, vm);
          },
          order : 220
        });
        var onSelect = function(row) {
          vm.selectedDNCs = vm.gridApi.selection.getSelectedRows();
        };
        gridApi.selection.on.rowSelectionChanged($scope, onSelect);
        gridApi.selection.on.rowSelectionChangedBatch($scope, onSelect);

        gridApi.core.on.sortChanged($scope, function(grid, sortColumns) {

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
          console.log("columnSizeChangedSave");
          gridStateService.save(vm.regularGridStateName, vm.gridApi);
        });

        vm.gridApi.core.on.columnVisibilityChanged($scope, function(changedColumn) {
          $scope.columnChanged = {
            name : changedColumn.colDef.name,
            visible : changedColumn.colDef.visible
          };
          //gridApi.saveState here is old on restore! That's why we add timeout
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

        dncFactory.getAll(paginationOptions.page, paginationOptions.sort, paginationOptions.filters).then(function(data) {

          vm.dncGridOptions.totalItems = data.totalItems;
          vm.dncGridOptions.data = data.data;

        });

      };

      vm.getPage();

      // modal dialog

      vm.open = function(size, item) {

        vm.item = item;

        // remember if item is new (to know if we need to push to dnc list or merge)
        vm.isNew = true;
        if (item.id != null) {
          vm.isNew = false;
        }

        // process open modal
        var modalInstance = $modal.open({
          animation : true,
          backdrop : 'static',
          // modal template page
          templateUrl : 'pages/dncForm.html',
          // modal page controller
          controller : 'dncFormCtrl as dnc',
          size : size,
          resolve : {
            item : function() {
              // pass in selected dnc to modal
              return vm.item;
            }
          }
        });

        // finally receive result
        modalInstance.result.then(function(result) {
          // modal result handler
          // if item is new - push it to list
          if (vm.isNew) {
            vm.dncGridOptions.data.push(result);
          } else {
            // if item is updated - merge with original item(to update in grid)
            angular.merge(vm.item, result);
          }
        }, function() {
          // modal dismissed(canceled)
          $log.info('Modal dismissed at: ' + new Date());
        });
      };

      vm.remove = function() {
        var ids = [];
        var objects = [];

        selectionHasDefaultDNCList = false;
        vm.gridApi.selection.getSelectedRows().forEach(function(item, i, arr) {
          if (item.campaign != null)
            selectionHasDefaultDNCList = true;
        });

        if (selectionHasDefaultDNCList) {
          alertService.add('danger', locMsg['dnc.cantDeleteDefault']);
          return;
        }

        vm.gridApi.selection.getSelectedRows().forEach(function(item, i, arr) {
          ids.push(item.id);
          objects.push(item);
        });
        vm.gridApi.selection.clearSelectedRows();
        dncFactory.remove(ids).then(function(data) {
          data.forEach(function(item, i, arr) {
            vm.dncGridOptions.data = _.without(vm.dncGridOptions.data, _.findWhere(vm.dncGridOptions.data, {
              id : item
            }));
            adminLogsFactory.proccessList(_.find(objects, function(num) {
              return num.id === item;
            }), 2, 2);
          });
          vm.selectedDNCs = null;
        }, function(response) {
          // show error msg
          $log.error(response);
        });
      };

      $scope.$on("$destroy", function(event) {
        if (angular.isDefined(vm.filterTimeout)) {
          $timeout.cancel(vm.filterTimeout);
        }
      });

      vm.newDnc = function() {
        return dncFactory.createNew();
      };

    }

]);
