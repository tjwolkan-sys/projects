/**
 * Controller to show list of sortFields of a campaign
 */
app
    .controller(
        'sortFieldCtrl',
        [
            '$scope',
            '$routeParams',
            'uiGridConstants',
            '$log',
            '$modal',
            'sortFieldFactory',
            'i18nService',
            '$locale',
            '$rootScope',
            '$filter',
            'locMsg',
            '$window',
            'dbTableService',
            'constants',
            '$q',
            function($scope, $routeParams, uiGridConstants, $log, $modal, sortFieldFactory, i18nService, $locale, $rootScope, $filter, locMsg, $window,
                     dbTableService, constants, $q) {

              var vm = this;
              vm.selectedSortField = null;

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
                  if (!vm.campaign.sortFields) {
                    vm.campaign.sortFields = vm.sortFieldGridOptions.data = [];
                  } else {
                    vm.sortFieldGridOptions.data = vm.sortFields;
                  }
                }
              });

              vm.sortFieldGridOptions = {
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
                  displayName : locMsg['sortField.name'],
                  field : 'name',
                  enableHiding : false,
                  headerCellClass : vm.highlightFilteredHeader,
                  filters : [ {
                    condition : uiGridConstants.filter.CONTAINS
                  } ],
                  minWidth : 100
                }, {
                  displayName : locMsg['sortField.priority'],
                  field : 'priority',
                  enableHiding : false,
                  headerCellClass : vm.highlightFilteredHeader,
                  type : 'number',
                  filters : [ {
                    condition : uiGridConstants.filter.CONTAINS
                  } ],
                  minWidth : 100
                }, {
                  displayName : locMsg['sortField.sortMethod'],
                  field : 'sortMethod',
                  enableHiding : false,
                  headerCellClass : vm.highlightFilteredHeader,
                  filter : {
                    type : uiGridConstants.filter.SELECT,
                    selectOptions : [ {
                      value : 0,
                      label : locMsg['sortField.sortMethod.0']
                    }, {
                      value : 1,
                      label : locMsg['sortField.sortMethod.1']
                    }, {
                      value : 2,
                      label : locMsg['sortField.sortMethod.2']
                    } ]
                  },
                  cellTemplate : '<div class="ui-grid-cell-contents">{{"sortField.sortMethod." + row.entity.sortMethod | i18n}}</div>',
                  minWidth : 100
                }, {
                  displayName : locMsg['sortField.sortDirection'],
                  field : 'sortDirection',
                  enableHiding : false,
                  headerCellClass : vm.highlightFilteredHeader,
                  filter : {
                    type : uiGridConstants.filter.SELECT,
                    selectOptions : [ {
                      value : 0,
                      label : locMsg['campaign.dbListSortDirection.0']
                    }, {
                      value : 1,
                      label : locMsg['campaign.dbListSortDirection.1']
                    } ]
                  },
                  cellTemplate : '<div class="ui-grid-cell-contents">{{"campaign.dbListSortDirection." + row.entity.sortDirection | i18n}}</div>',
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

              vm.toggleFiltering = function() {
                vm.sortFieldGridOptions.enableFiltering = !vm.sortFieldGridOptions.enableFiltering;
                vm.gridApi.core.notifyDataChange(uiGridConstants.dataChange.COLUMN);
              };

              vm.sortFieldGridOptions.onRegisterApi = function(gridApi) {
                vm.gridApi = gridApi;
                var onSelect = function(row) {
                  vm.selectedSortField = vm.gridApi.selection.getSelectedRows();
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

                sortFieldFactory.getAll(paginationOptions.page, paginationOptions.sort, paginationOptions.filters).then(function(data) {
                  let sortFields;
                  if (vm.campaign.sortFields) {
                    sortFields = data.data.concat(vm.campaign.sortFields);
                  } else {
                    sortFields = data.data;
                  }
                  vm.sortFieldGridOptions.totalItems = data.totalItems;
                  vm.sortFieldGridOptions.data = sortFields;
                  vm.campaign.sortFields = sortFields;
                  vm.campaignInitial.sortFields = angular.copy(sortFields);
                });

              };

              vm.open = function(size, item) {

                vm.item = item;
                item.isNew = (item.isNew == null ? (item.id == null ? true : false) : item.isNew);
                if (item.isNew)
                  item.priority = vm.getDefaultPriority();

                const modalInstance = $modal.open({
                  animation: true,
                  backdrop: 'static',
                  templateUrl: 'pages/sortFieldForm.html',
                  controller: 'sortFieldFormCtrl as sortField',
                  size: size,
                  resolve: {
                    campaign: function () {
                      return vm.campaign;
                    },
                    campaignInitial : function() {
                      return vm.campaignInitial;
                    },
                    listSourceColumns: function () {
                      if (vm.selectedSortField && vm.selectedSortField.length > 0 && !vm.item.isNew) {
                        return vm.excludeSelectedFields(vm.listSourceColumns).concat([{
                          fieldName: vm.selectedSortField[0].name
                        }]).uniqueByField("fieldName");
                      } else {
                        return vm.excludeSelectedFields(vm.listSourceColumns);
                      }
                    },
                    item: function () {
                      return vm.item;
                    }
                  }
                });
                modalInstance.result.then(function (result) {
                  if (vm.item.isNew) {
                    result.isNew = false;
                    vm.sortFieldGridOptions.data.unshift(result);
                  } else {
                    angular.merge(vm.item, result);
                  }
                }, function () {
                  $log.info('Modal dismissed at: ' + new Date());
                });
              };

              vm.remove = function() {
                vm.gridApi.selection.getSelectedRows().forEach(function(item, i, arr) {
                  var index = vm.sortFieldGridOptions.data.indexOf(item);
                  if (item.id == null) {
                    vm.sortFieldGridOptions.data.splice(index, 1);
                  } else {
                    item.deleted = true;
                    item.changed = true;
                  }
                  vm.selectedSortField = null;
                });
              };

              vm.newSortField = function() {
                return sortFieldFactory.createNew();
              };

              vm.getDefaultPriority = function() {
                const sortFieldWithMaxPriority = _.max(_.filter(vm.sortFieldGridOptions.data, {
                  deleted: false
                }), function (o) {
                  return o.priority;
                });
                return _.isObject(sortFieldWithMaxPriority) ? sortFieldWithMaxPriority.priority + 1 : 0;
              };

              vm.excludeSelectedFields = function(allAvailableColumns) {
                const listSourceColumnsCopy = [];
                Object.assign(listSourceColumnsCopy, allAvailableColumns);
                for (let j = 0; j < vm.campaign.sortFields.length; j++) {
                  for (let i = 0; i < listSourceColumnsCopy.length; i++) {
                    if (listSourceColumnsCopy[i].fieldName === vm.campaign.sortFields[j].name
                        && !vm.campaign.sortFields[j].deleted) {
                      listSourceColumnsCopy.splice(i, 1);
                      break;
                    }
                  }
                }
                return listSourceColumnsCopy;
              }

            } ]);
