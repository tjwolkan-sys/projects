/**
 * Controller to show groups of a campaign
 */
app
    .controller(
        'campaignGroupCtrl',
        [
            '$scope',
            '$routeParams',
            '$http',
            'uiGridConstants',
            '$log',
            '$modal',
            'campaignGroupFactory',
            'commonResultStatusService',
            'i18nService',
            '$locale',
            '$rootScope',
            '$filter',
            'locMsg',
            '$window',
            'groupFactory',
            '$q',
            'constants',
            function($scope, $routeParams, $http, uiGridConstants, $log, $modal, campaignGroupFactory, commonResultStatusService, i18nService, $locale, $rootScope, $filter, locMsg, $window, groupFactory, $q, constants) {

              var vm = this;
              var $translate = $filter('translate');

              vm.selectedCampaignGroups = null;

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
                  vm.campaign.campaignGroups = vm.campaignGroupGridOptions.data = [];
                }
              });

              vm.campaignGroupGridOptions = {
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
                  displayName : locMsg['campaignGroup.group'],
                  field : 'group.name',
                  enableHiding : false,
                  headerCellClass : vm.highlightFilteredHeader,
                  filters : [ {
                    condition : uiGridConstants.filter.CONTAINS
                  } ],
                  minWidth : 100
                }, {
                  displayName : locMsg['campaignGroup.priority'],
                  field : 'priority',
                  enableHiding : false,
                  headerCellClass : vm.highlightFilteredHeader,
                  type : 'number',
                  filters : [ {
                    condition : uiGridConstants.filter.CONTAINS
                  } ],
                  minWidth : 100
                }, {
                  displayName : locMsg['label.enabled'],
                  field : 'enabled',
                  enableHiding : false,
                  headerCellClass : vm.highlightFilteredHeader,
                  filter : {
                    type : uiGridConstants.filter.SELECT,
                    selectOptions : [ {
                      value : '',
                      label : locMsg['input.option.select']
                    }, {
                      value : true,
                      label : locMsg['label.true']
                    }, {
                      value : false,
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
                  cellTemplate : '<input type="checkbox" ng-model="row.entity.enabled" ng-disabled="true"></input>',
                  minWidth : 50
                } ]
              };

              i18nService.setCurrentLang(locMsg['lang.id']);

              vm.toggleFiltering = function() {
                vm.campaignGroupGridOptions.enableFiltering = !vm.campaignGroupGridOptions.enableFiltering;
                vm.gridApi.core.notifyDataChange(uiGridConstants.dataChange.COLUMN);
              };

              vm.campaignGroupGridOptions.onRegisterApi = function(gridApi) {
                // set gridApi on scope
                vm.gridApi = gridApi;
                var onSelect = function(row) {
                  vm.selectedCampaignGroups = vm.gridApi.selection.getSelectedRows();
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

                campaignGroupFactory.getAll(paginationOptions.page, paginationOptions.sort, paginationOptions.filters).then(function(data) {

                  vm.campaignGroupGridOptions.totalItems = data.totalItems;
                  vm.campaignGroupGridOptions.data = data.data;
                  vm.campaign.campaignGroups = data.data;
                  vm.campaignInitial.campaignGroups = angular.copy(data.data);
                  vm.getCommonResultStatuses(vm.campaign.campaignGroups);
                });
              };

              vm.getCommonResultStatuses = function(from) {
                var ids = vm.getCampaignGroupIds(from);
                if(ids) {
                  commonResultStatusService.loadCommonResultStatuses(ids).then(function (result) {
                    vm.campaign.commonResultStatuses = result.data;
                  });
                }
              };

              vm.getCampaignGroupIds = function(from) {
                var ids;
                if(from && from.length > 0) {
                  ids = [from.length];
                  for (var i = 0; i < from.length; i++) {
                    if(from[i] && !from[i].deleted) {
                      ids[i] = from[i].group.id;
                    }
                    else {
                      ids[i] = null;
                    }
                  }
                }
                return ids;
              };

              vm.open = function(size, item) {

                var deferred = $q.defer();
                if (vm.groupList == null) {
                  groupFactory.getAll({}, {
                    'field' : 'name',
                    'direction' : 'asc'
                  }, {
                    'enabled' : [ {
                      term : 'true'
                    } ]
                  }).then(function(data) {
                    vm.groupList = data.data;
                    deferred.resolve();
                  });
                } else {
                  deferred.resolve();
                }

                deferred.promise.then(

                function() {

                  vm.item = item;
                  item.isNew = (item.isNew == null ? (item.id == null ? true : false) : item.isNew);

                  var modalInstance = $modal.open({
                    animation : true,
                    backdrop : 'static',
                    templateUrl : 'pages/campaignGroupForm.html',
                    controller : 'campaignGroupFormCtrl as campaignGroup',
                    size : size,
                    resolve : {
                      campaign : function() {
                        return vm.campaign;
                      },
                      groupList : function() {
                        return vm.groupList;
                      },
                      item : function() {
                        return vm.item;
                      },
                      campaignGroups : function() {
                        return vm.campaignGroupGridOptions.data;
                      }
                    }
                  });

                  modalInstance.result.then(function(result) {
                    if (vm.item.isNew) {
                      result.isNew = false;
                      vm.campaignGroupGridOptions.data.unshift(result);
                      vm.getCommonResultStatuses(vm.campaignGroupGridOptions.data);
                    } else {
                      angular.merge(vm.item, result);
                    }
                  }, function() {
                    $log.info('Modal dismissed at: ' + new Date());
                  });

                });

              };

              vm.remove = function() {
                vm.gridApi.selection.getSelectedRows().forEach(function(item, i, arr) {
                  var index = vm.campaignGroupGridOptions.data.indexOf(item);
                  if (item.id == null) {
                    vm.campaignGroupGridOptions.data.splice(index, 1);
                  } else {
                    item.deleted = true;
                    item.changed = true;
                  }
                  vm.getCommonResultStatuses(vm.campaignGroupGridOptions.data);
                  vm.selectedCampaignGroups = null;
                });
              };

              vm.newCampaignGroup = function() {
                return campaignGroupFactory.createNew();
              };

            } ]);
