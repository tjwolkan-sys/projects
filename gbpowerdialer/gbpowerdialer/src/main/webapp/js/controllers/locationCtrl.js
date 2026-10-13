app
  .controller(
    'locationCtrl',
    [
      '$scope',
      '$http',
      'uiGridConstants',
      '$log',
      'locationFactory',
      'i18nService',
      '$locale',
      '$rootScope',
      '$filter',
      'locMsg',
      '$window',
      '$timeout',
      'localStorageService',
      'gridStateService',
      function ($scope, $http, uiGridConstants, $log, locationFactory, i18nService, $locale, $rootScope, $filter, locMsg, $window, $timeout, 
                localStorageService, gridStateService) {
        var vm = this;

        vm.selectionIsEmpty = true;
        vm.selectedlocation = {};

        vm.highlightFilteredHeader = function (row, rowRenderIndex, col) {
          if (col.filters[0].term) {
            return 'header-filtered';
          } else {
            return '';
          }
        };

        vm.enableFiltering = function() {
          var isEnabledFiltering = localStorageService.get('enableFiltering_location');
          if (!_.isNull(isEnabledFiltering) && !_.isUndefined(isEnabledFiltering)) {
            return JSON.parse(isEnabledFiltering);
          }
          return false;
        };

        vm.customFilter = function (term, condition, alias) {
          var filtersChanged = vm.addCustomFilter(vm.paginationOptions.filters, term, alias, condition);
          if (filtersChanged) {
            if (angular.isDefined(vm.filterTimeout)) {
              $timeout.cancel(vm.filterTimeout);
            }
            vm.filterTimeout = $timeout(function() {
              vm.getPage();
            }, 500);
          }
        };

        vm.clearCustomFilter = function (condition, alias) {
          var filtersChanged = addCustomFilter(vm.paginationOptions.filters, '', alias, condition);
          if (filtersChanged) {
            if (angular.isDefined(vm.filterTimeout)) {
              $timeout.cancel(vm.filterTimeout);
            }
            vm.filterTimeout = $timeout(function() {
              vm.getPage();
            }, 500);
          }
        };

        vm.paginationOptions = {
          page: {
            'first': 1,
            'max': 25
          },
          sort: {},
          filters: {}
        };

        vm.regularGridStateName = "locationGrid";
        vm.defaultGridStateName = "defaultLocationGrid";
        vm.locationGridOptions = {
          paginationPageSizes: [25, 50, 75],
          paginationPageSize: 25,
          useExternalPagination: true,
          enableRowSelection: true,
          enableSelectAll: true,
          enableFiltering: vm.enableFiltering(),
          enableFullRowSelection: true,
          enableGridMenu: true,
          exporterCsvColumnSeparator: ';',
          exporterOlderExcelCompatibility : true,
          exporterCsvFilename: $rootScope.getExportFilename('locations', 'csv'),
          exporterPdfFilename: $rootScope.getExportFilename('locations', 'pdf'),
          exporterPdfOrientation: 'landscape',
          exporterPdfPageSize: 'A4',
          exporterPdfMaxGridWidth: 660,
          exporterPdfCustomFormatter : $rootScope.exporterPdfCustomFormatter,
          modifierKeysToMultiSelect: true,
          i18n: 'en',
          saveFocus : false,
          saveFilter : true,
          saveVisible : true,
          saveGroupingExpandedStates : true,
          exporterFieldCallback : function(grid, row, col, value) {
            if (col.name == 'serverListDTOs' || col.name == 'campaignListDTOs') {
              if (value && value.length > 0) {
                var names = _.map(value, function(item) {
                  return item.name;
                });
                var joinedNames = _.reduce(names, function(memo, name) {
                  return memo + ', ' + name;
                }, '');
                return joinedNames.substr(1).trim();
              } else {
                return '';
              }
            } else {
              return value;
            }
          },
          columnDefs: [{
            displayName: locMsg['label.id'],
            field: 'id',
            enableHiding: true,
            visible: false,
            headerCellClass: vm.highlightFilteredHeader,
            type: 'number',
            filters: [{
              condition: uiGridConstants.filter.EXACT
            }],
            minWidth : 50
          }, {
            displayName: locMsg['location.name'],
            field: 'name',
            enableHiding: false,
            headerCellClass: vm.highlightFilteredHeader,
            filters: [{
              condition: uiGridConstants.filter.CONTAINS
            }],
            minWidth : 100
          }, {
            displayName: locMsg['location.description'],
            field: 'description',
            enableHiding: false,
            headerCellClass: vm.highlightFilteredHeader,
            filters: [{
              condition: uiGridConstants.filter.CONTAINS
            }],
            minWidth : 100
          }, {
            enableColumnMenu: false,
            displayName: locMsg['location.servers'],
            field: 'serverListDTOs',
            enableHiding: false,
            enableSorting: false,
            filterHeaderTemplate: '<div class="header-filtered">'
            + '<div class="ui-grid-filter-container" ng-repeat="colFilter in col.filters">'
            + '<div class="ng-scope"><input type="text" class="ui-grid-filter-input" ng-model="col.grid.appScope.location.serverNameTerm" ng-change="col.grid.appScope.location.customFilter(col.grid.appScope.location.serverNameTerm, 16, \'servers.server.name\')" /></div>'
            + '<div class="ui-grid-filter-button ng-scope" ng-click="col.grid.appScope.location.clearCustomFilter(16, \'servers.server.name\'); col.grid.appScope.location.serverNameTerm = null"><i class="ui-grid-icon-cancel" ng-show="col.grid.appScope.location.serverNameTerm">&nbsp;</i></div>'
            + '</div>'
            + '</div>',
                      cellTemplate : '<div class="ui-grid-cell-contents"><span ng-repeat="server in row.entity.serverListDTOs track by $index">{{server.name}}<span ng-if=" ! $last ">, </span></span></div>',
            minWidth : 100
          }, {
            enableColumnMenu: false,
            displayName: locMsg['location.campaigns'],
            field: 'campaignListDTOs',
            enableHiding: false,
            enableSorting: false,
            filterHeaderTemplate: '<div class="header-filtered">'
            + '<div class="ui-grid-filter-container" ng-repeat="colFilter in col.filters">'
            + '<div class="ng-scope"><input type="text" class="ui-grid-filter-input" ng-model="col.grid.appScope.location.campaignNameTerm" ng-change="col.grid.appScope.location.customFilter(col.grid.appScope.location.campaignNameTerm, 16, \'campaigns.campaign.name\'); col.grid.appScope.location.campaignCancelButton={display: block}" /></div>'
            + '<div class="ui-grid-filter-button ng-scope" ng-click="col.grid.appScope.location.clearCustomFilter(16, \'campaigns.campaign.name\'); col.grid.appScope.location.campaignNameTerm = null"><i class="ui-grid-icon-cancel" ng-show="col.grid.appScope.location.campaignNameTerm">&nbsp;</i></div>'
            + '</div>'
            + '</div>',
                      cellTemplate : '<div class="ui-grid-cell-contents"><span ng-repeat="campaign in row.entity.campaignListDTOs track by $index">{{campaign.name}}<span ng-if=" ! $last ">, </span></span></div>',
            minWidth : 100
          }]
        };

        i18nService.setCurrentLang(locMsg['lang.id']);

        vm.toggleFiltering = function () {
          vm.locationGridOptions.enableFiltering = !vm.locationGridOptions.enableFiltering;
          localStorageService.set('enableFiltering_location', vm.locationGridOptions.enableFiltering);
          vm.gridApi.core.notifyDataChange(uiGridConstants.dataChange.COLUMN);
          gridStateService.restore(vm.regularGridStateName, vm.gridApi, vm);
        };

        vm.locationGridOptions.onRegisterApi = function (gridApi) {
          // set gridApi on scope
          vm.gridApi = gridApi;
          vm.locationGridOptions.gridMenuCustomItems = $rootScope.getExportMenuItemsXLSX(vm.gridApi, 'locations');
          vm.locationGridOptions.gridMenuCustomItems.push({
            title : locMsg['resetTable'],
            action: function () {
              gridStateService.reset(vm.defaultGridStateName, vm.regularGridStateName, vm.gridApi, vm);
            },
            order: 220
          });
          var onSelect = function (row) {
            if (vm.gridApi.selection.getSelectedRows().length == 0) {
              vm.selectionIsEmpty = true;
              vm.selectedlocation = {};
            } else {
              vm.selectionIsEmpty = false;
              vm.selectedlocation = vm.gridApi.selection.getSelectedRows()[0];
            }
          };
          vm.gridApi.selection.on.rowSelectionChanged($scope, onSelect);
          vm.gridApi.selection.on.rowSelectionChangedBatch($scope, onSelect);

          vm.gridApi.core.on.sortChanged($scope, function (grid, sortColumns) {

            gridStateService.save(vm.regularGridStateName, vm.gridApi);


            if (sortColumns.length == 0) {
              vm.paginationOptions.sort = {};
            } else {
              vm.paginationOptions.sort['field'] = sortColumns[0].field;
              vm.paginationOptions.sort['direction'] = sortColumns[0].sort.direction;
            }
            vm.getPage();
          });

          vm.gridApi.core.on.filterChanged($scope, function () {

            gridStateService.save(vm.regularGridStateName, vm.gridApi);

            $.each(this.grid.columns, function (indexColumn, valueColumn) {

              if (valueColumn.field != 'selectionRowHeaderCol') {

                var filters = [];

                $.each(valueColumn.filters, function (indexFilter, valueFilter) {
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
                  if (valueColumn.filters[0].hasOwnProperty('alias')) {
                    vm.paginationOptions.filters[valueColumn.filters[0].alias] = filters;
                  } else {
                    vm.paginationOptions.filters[valueColumn.field] = filters;
                  }
                } else {
                  if (vm.paginationOptions.filters.hasOwnProperty(valueColumn.field)) {
                    delete vm.paginationOptions.filters[valueColumn.field];
                  } else if (vm.paginationOptions.filters.hasOwnProperty(valueColumn.filters[0].alias)) {
                    delete vm.paginationOptions.filters[valueColumn.filters[0].alias];
                  }
                }
              }
            });

            if (angular.isDefined(vm.filterTimeout)) {
              $timeout.cancel(vm.filterTimeout);
            }
            vm.filterTimeout = $timeout(function () {
              vm.getPage();
            }, 500);

          });

          vm.gridApi.pagination.on.paginationChanged($scope, function (newPage, pageSize) {
            vm.gridApi.selection.clearSelectedRows();
            vm.paginationOptions.page['first'] = newPage;
            vm.paginationOptions.page['max'] = pageSize;
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

        vm.getPage = function () {

          locationFactory.getLocationWithServersAndCampaigns(vm.paginationOptions.page, vm.paginationOptions.sort, vm.paginationOptions.filters).then(function (data) {

            vm.locationGridOptions.totalItems = data.totalItems;
            vm.locationGridOptions.data = data.data;

          });

        };

        vm.getPage();
      }

    ]);
