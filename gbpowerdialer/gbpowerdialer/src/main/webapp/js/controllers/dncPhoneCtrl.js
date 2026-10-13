/**
 * Controller to show phoneNumbers of a dnc
 */
app
    .controller(
        'dncPhoneCtrl',
        [
          '$scope',
          'uiGridConstants',
          'dncFactory',
          '$log',
          '$modal',
          'dncPNFactory',
          'i18nService',
          'locMsg',
          '$timeout',
          '$filter',
          '$q',
          function($scope, uiGridConstants, dncFactory, $log, $modal, dncPNFactory, i18nService, locMsg, $timeout, $filter, $q) {

              var vm = this;
              vm.selectionIsEmptyPN = true;
              vm.selectedDNCPN = {};
              vm.changedRows = {
                added : [],
                edited : [],
                deleted : []
              };
            vm.isAllDncPnSelected;
            vm.totalItemsCountOnAllPages;

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
                filters : {
                  'deleted' : [{
                    term : 'false'
                  }]
                }
              };

              $scope.$watch("vm.dncList", function() {
                if (vm.dncList !== undefined && vm.dncList.id !== undefined) {
                  vm.getPage();
                }
              });

              vm.dncPNGridOptions = {
                rowTemplate : '<div ng-if="!row.entity.deleted" ng-repeat="(colRenderIndex, col) in colContainer.renderedColumns track by col.colDef.name" class="ui-grid-cell" ng-class="{ \'ui-grid-row-header-cell\': col.isRowHeader }"  ui-grid-cell></div>',
                paginationPageSizes : [25, 50, 75],
                paginationPageSize : 25,
                useExternalPagination : true,
                useExternalSorting : true,
                useExternalFiltering : true,
                enableRowSelection : true,
                enableSelectAll : true,
                enableFiltering : false,
                enableFullRowSelection : true,
                modifierKeysToMultiSelect : true,
                enableGridMenu : true,
                i18n : 'en',
                columnDefs : [{
                  displayName : locMsg['label.id'],
                  field : 'id',
                  enableHiding : true,
                  visible : false,
                  headerCellClass : vm.highlightFilteredHeader,
                  filters : [{
                    condition : uiGridConstants.filter.CONTAINS
                  }],
                  minWidth : 50
                }, {
                  displayName : locMsg['dnc.number'],
                  field : 'phoneNumber',
                  enableHiding : false,
                  headerCellClass : vm.highlightFilteredHeader,
                  filters : [{
                    condition : uiGridConstants.filter.CONTAINS
                  }],
                  minWidth : 100
                },
                {
                  displayName : locMsg['dnc.expiredDate'],
                  field : 'expiredDate',
                  enableHiding : false,
                  headerCellClass : vm.highlightFilteredHeader,
                  type : 'number',
                  filterHeaderTemplate : '<div class="ui-grid-filter-container" ng-repeat="colFilter in col.filters">'
                      + '<div class="dropdown">'
                      + '<a class="dropdown-toggle expireStartDate-select" id="dLabel" role="button" data-toggle="dropdown" data-target="javascript:void(0)" href="">'
                      + '<div class="input-append">'
                      + '<input placeholder="From Date" id="dncphone.expireStartDate" type="text" class="ui-grid-filter-input" data-ng-model="col.grid.appScope.dncphone.expireStartDate" ng-click="col.grid.appScope.dncphone.beforeRender()" ng-format-date="dd/MM/yyyy HH:mm" readonly />'
                      + '<div class="ui-grid-filter-button ng-scope" ng-show="col.grid.appScope.dncphone.expireStartDate" ng-click="col.grid.appScope.dncphone.clearFilterDate(32, col.field); col.grid.appScope.dncphone.expireStartDate = null"><i class="ui-grid-icon-cancel" style="margin-top: -10px; right: 0px">&nbsp;</i></div>'
                      + '</div>'
                      + '</a>'
                      + '<ul class="dropdown-menu" role="menu" aria-labelledby="dLabel">'
                      + '<datetimepicker fixdatepicker="dncphone.expireStartDate" data-on-set-time="col.grid.appScope.dncphone.parseFilterDate(newDate, oldDate, 32, col.field);" data-ng-model="col.grid.appScope.dncphone.expireStartDate" data-datetimepicker-config="{ dropdownSelector: \'.expireStartDate-select\' }"></datetimepicker>'
                      + '</ul>'
                      + '</div>'
                      + '<div class="dropdown" style="padding-top: 8px;">'
                      + '<a class="dropdown-toggle expireEndDate-select" id="dLabel" role="button" data-toggle="dropdown" data-target="javascript:void(0)" href="">'
                      + '<div class="input-append">'
                      + '<input placeholder="Till Date" id="dncphone.expireEndDate" type="text" class="ui-grid-filter-input" data-ng-model="col.grid.appScope.dncphone.expireEndDate" ng-format-date="dd/MM/yyyy HH:mm" readonly/>'
                      + '<div class="ui-grid-filter-button ng-scope" ng-show="col.grid.appScope.dncphone.expireEndDate" ng-click="col.grid.appScope.dncphone.clearFilterDate(256, col.field); col.grid.appScope.dncphone.expireEndDate = null" ><i class="ui-grid-icon-cancel" style="margin-top: -10px; right: 0px">&nbsp;</i></div>'
                      + '</div>'
                      + '</a>'
                      + '<ul class="dropdown-menu" role="menu" aria-labelledby="dLabel">'
                      + '<datetimepicker fixdatepicker="dncphone.expireEndDate" data-on-set-time="col.grid.appScope.dncphone.parseFilterDate(newDate, oldDate, 256, col.field);" data-ng-model="col.grid.appScope.dncphone.expireEndDate" data-datetimepicker-config="{ dropdownSelector: \'.expireEndDate-select\' }"></datetimepicker>'
                      + '</ul>' + '</div>' + '</div>',
                  cellTemplate : '<div class="ui-grid-cell-contents {{row.entity.logLevel == 2 ? \'green\':(row.entity.logLevel == 1 ? \'yellow\':(row.entity.logLevel == 0 ? \'red\':\'\'))}}">{{row.entity.expiredDate  | date : \'dd/MM/yyyy HH:mm\'}}</div>',
                  minWidth : 100
                },
                  {
                    displayName : locMsg['dnc.reason'],
                    field : 'reason',
                    enableHiding : false,
                    headerCellClass : vm.highlightFilteredHeader,
                    filter : {
                      type : uiGridConstants.filter.SELECT,
                      selectOptions : [
                        { value : '', label : locMsg['input.option.select'] },
                        { value : '0', label : locMsg['dnc.reason.0'] },
                        { value : '1', label : locMsg['dnc.reason.1'] }
                      ]
                    },
                    filterHeaderTemplate :
                        '<div class="ui-grid-filter-container" ng-repeat="colFilter in col.filters">'
                        + '<div ng-if="colFilter.type === \'select\'" class="ng-scope">'
                        + '<select class="ui-grid-filter-select ui-grid-filter-input-0 ng-touched" ng-model="colFilter.term" ng-init="colFilter.term = colFilter.term == null ? \'\' : colFilter.term" ng-attr-placeholder="{{colFilter.placeholder || aria.defaultFilterLabel}}" ng-options="option.value as option.label for option in colFilter.selectOptions">'
                        + '</select>'
                        + '</div>'
                        + '</div>',
                    cellTemplate : '<div class="ui-grid-cell-contents">{{"dnc.reason." + row.entity.reason | i18n}}</div>',
                    minWidth : 100
                  },{
                    displayName : locMsg['label.enabled'],
                    field : 'enabled',
                    enableHiding : false,
                    filter : {
                      type : uiGridConstants.filter.SELECT,
                      selectOptions : [
                        { value : '', label : locMsg['input.option.select'] },
                        { value : 'true', label : 'True' },
                        { value : 'false', label : 'False' }
                      ]
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
                  }, {
                  displayName : locMsg['dnc.addDate'],
                  field : 'createDate',
                  enableHiding : true,
                  headerCellClass : vm.highlightFilteredHeader,
                  type : 'number',
                  filterHeaderTemplate : '<div class="ui-grid-filter-container" ng-repeat="colFilter in col.filters">'
                                         + '<div class="dropdown">'
                                         + '<a class="dropdown-toggle startDate-select" id="dLabel" role="button" data-toggle="dropdown" data-target="javascript:void(0)" href="">'
                                         + '<div class="input-append">'
                                         + '<input placeholder="From Date" id="dncphone.startDate" type="text" class="ui-grid-filter-input" data-ng-model="col.grid.appScope.dncphone.startDate" ng-click="col.grid.appScope.dncphone.beforeRender()" ng-format-date="dd/MM/yyyy HH:mm" readonly />'
                                         + '<div class="ui-grid-filter-button ng-scope" ng-show="col.grid.appScope.dncphone.startDate" ng-click="col.grid.appScope.dncphone.clearFilterDate(32, col.field); col.grid.appScope.dncphone.startDate = null"><i class="ui-grid-icon-cancel" style="margin-top: -10px; right: 0px">&nbsp;</i></div>'
                                         + '</div>'
                                         + '</a>'
                                         + '<ul class="dropdown-menu dropdown-menu-right" role="menu" aria-labelledby="dLabel">'
                                         + '<datetimepicker fixdatepicker="dncphone.startDate" data-on-set-time="col.grid.appScope.dncphone.parseFilterDate(newDate, oldDate, 32, col.field);" data-ng-model="col.grid.appScope.dncphone.startDate" data-datetimepicker-config="{ dropdownSelector: \'.startDate-select\' }"></datetimepicker>'
                                         + '</ul>'
                                         + '</div>'
                                         + '<div class="dropdown" style="padding-top: 8px;">'
                                         + '<a class="dropdown-toggle endDate-select" id="dLabel" role="button" data-toggle="dropdown" data-target="javascript:void(0)" href="">'
                                         + '<div class="input-append">'
                                         + '<input placeholder="Till Date" id="dncphone.endDate" type="text" class="ui-grid-filter-input" data-ng-model="col.grid.appScope.dncphone.endDate" ng-format-date="dd/MM/yyyy HH:mm" readonly/>'
                                         + '<div class="ui-grid-filter-button ng-scope" ng-show="col.grid.appScope.dncphone.endDate" ng-click="col.grid.appScope.dncphone.clearFilterDate(256, col.field); col.grid.appScope.dncphone.endDate = null" ><i class="ui-grid-icon-cancel" style="margin-top: -10px; right: 0px">&nbsp;</i></div>'
                                         + '</div>'
                                         + '</a>'
                                         + '<ul class="dropdown-menu dropdown-menu-right" role="menu" aria-labelledby="dLabel">'
                                         + '<datetimepicker fixdatepicker="dncphone.endDate" data-on-set-time="col.grid.appScope.dncphone.parseFilterDate(newDate, oldDate, 256, col.field);" data-ng-model="col.grid.appScope.dncphone.endDate" data-datetimepicker-config="{ dropdownSelector: \'.endDate-select\' }"></datetimepicker>'
                                         + '</ul>' + '</div>' + '</div>',
                  cellTemplate : '<div class="ui-grid-cell-contents {{row.entity.logLevel == 2 ? \'green\':(row.entity.logLevel == 1 ? \'yellow\':(row.entity.logLevel == 0 ? \'red\':\'\'))}}">{{row.entity.createDate | date : \'dd/MM/yyyy HH:mm\'}}</div>',
                  minWidth : 100
                }]
              };



            vm.clearFilterDate = function(condition, field) {
              vm.addCustomFilter(paginationOptions.filters, '', field, condition);
            };

            vm.parseFilterDate = function(newDate, oldDate, condition, field) {
              vm.addCustomFilter(paginationOptions.filters, $filter('date')(newDate, 'dd/MM/yyyy HH:mm', 0), field, condition);
            };

            vm.addCustomFilter = function(filters, term, field, condition) {
              if (!filters || !field || !condition) {
                return;
              }

              if (term === '' && !filters[field]) {
                return;
              }

              // if filter removed
              if (term === '') {
                for (var i = filters[field].length - 1; i >= 0; i--) {
                  if (filters[field][i]['condition'] === condition) {
                    filters[field].splice(i, 1);
                  }
                }
                // after filter set
                vm.getPage();
                return;
              }

              if (filters[field]) {
                // if filters by this field already exists -
                // go through all filters
                // and
                // check conditions
                var alreadyAdded = false;
                $.each(filters[field], function(index, value) {
                  if (value["condition"] === condition) {
                    value["term"] = term;
                    alreadyAdded = true;
                  }
                });
                if (!alreadyAdded) {
                  filters[field].push({
                                        "condition" : condition,
                                        "term" : term
                                      });
                }
              } else {
                // if not - create it
                filters[field] = [ {
                  "condition" : condition,
                  "term" : term
                } ];
              }
              // after filter set
              vm.getPage();

            };

              i18nService.setCurrentLang(locMsg['lang.id']);

              vm.toggleFiltering = function() {
                vm.dncPNGridOptions.enableFiltering = !vm.dncPNGridOptions.enableFiltering;
                vm.gridApi.core.notifyDataChange(uiGridConstants.dataChange.COLUMN);
              };

              vm.dncPNGridOptions.onRegisterApi = function(gridApi) {
                vm.gridApi = gridApi;
                var onSelect = function(row) {
                  if (vm.gridApi.selection.getSelectedRows().length === 0) {
                    vm.selectionIsEmptyPN = true;
                    vm.selectedDNCPN = {};
                  } else {
                    vm.selectionIsEmptyPN = false;
                    vm.selectedDNCPN = vm.gridApi.selection.getSelectedRows()[0];
                  }
                };
                gridApi.selection.on.rowSelectionChanged($scope, onSelect);
                gridApi.selection.on.rowSelectionChangedBatch($scope, onSelect);

                gridApi.core.on.sortChanged($scope, function(grid, sortColumns) {
                  if (sortColumns.length === 0) {
                    paginationOptions.sort = {};
                  } else {
                    paginationOptions.sort['field'] = sortColumns[0].field;
                    paginationOptions.sort['direction'] = sortColumns[0].sort.direction;
                  }
                  vm.getPage();
                });

                gridApi.core.on.filterChanged($scope, function() {

                  if (vm.gridApi && vm.gridApi.selection.getSelectedCount() > 0) {
                    vm.deselectAll();
                  }

                  $.each(this.grid.columns, function(indexColumn, valueColumn) {

                    if (valueColumn.field !== 'selectionRowHeaderCol') {

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
                        if (paginationOptions.filters.hasOwnProperty(valueColumn.field) && valueColumn.field !== 'createDate') {
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

                gridApi.pagination.on.paginationChanged($scope, function(newPage, pageSize) {
                  vm.gridApi.selection.clearSelectedRows();
                  paginationOptions.page['first'] = newPage;
                  paginationOptions.page['max'] = pageSize;
                  vm.getPage();
                });

                gridApi.core.on.columnVisibilityChanged($scope, function(changedColumn) {
                  $scope.columnChanged = {
                    name : changedColumn.colDef.name,
                    visible : changedColumn.colDef.visible
                  };
                });

                vm.gridApi.selection.on.rowSelectionChanged($scope, function() {
                  vm.isAllDncPnSelected = false;
                });

              };

            vm.test = function() {
              console.log('here');
            };

              vm.getPage = function() {

                if (vm.dncList !== undefined && vm.dncList.id !== undefined) {

                  var modifiedFilters = angular.copy(paginationOptions.filters);
                  modifiedFilters['dncList.id'] = [{
                    term : vm.dncList.id.toString()
                  }];
                  modifiedFilters['id'] = [];
                  vm.changedRows.deleted.forEach(function(item, i, arr) {
                    modifiedFilters['id'].push({
                                                 term : item.id.toString(),
                                                 condition : uiGridConstants.filter.NOT_EQUAL
                                               });
                  });
                  vm.changedRows.edited.forEach(function(item, i, arr) {
                    modifiedFilters['id'].push({
                                                 term : item.id.toString(),
                                                 condition : uiGridConstants.filter.NOT_EQUAL
                                               });
                  });

                  $("#dncPNGrid").find('button, input[type=number], textarea, select').prop('disabled', true);

                  var getAllDefer = $q.defer();
                  var selectAllDefer = $q.defer();

                  dncPNFactory.getAll(paginationOptions.page, paginationOptions.sort, modifiedFilters).then(function(data) {

                    vm.addUserRows(data);

                    if (vm.isAllDncPnSelected === true) {
                      $timeout(function() {
                        vm.selectAll();
                        selectAllDefer.resolve();
                      }, 50);
                    } else {
                      selectAllDefer.resolve();
                    }
                    getAllDefer.resolve();
                  }, function() {
                    getAllDefer.resolve();
                  });

                  $q.all(selectAllDefer, getAllDefer).then(function() {
                    $("#dncPNGrid").find('button, input[type=number], textarea, select').prop('disabled', false);
                  });

                } else {

                  var data = {};
                  data.data = [];
                  data.totalItems = 0;
                  vm.addUserRows(data);

                }

              };

              vm.addUserRows = function(data) {
                var filteredAdded = vm.filterElements(vm.changedRows.added);
                var filteredEdited = vm.filterElements(vm.changedRows.edited);
                var filtered = filteredEdited.concat(filteredAdded);
                if (data.totalItems < paginationOptions.page['first'] * paginationOptions.page['max']) {
                  var firstAdded;
                  if (data.totalItems > (paginationOptions.page['first'] - 1) * paginationOptions.page['max'])
                    firstAdded = 0;
                  else
                    firstAdded = (paginationOptions.page['first'] - 1) * paginationOptions.page['max'] - data.totalItems;

                  var lastAdded;
                  if (filtered.length >= (paginationOptions.page['first'] * paginationOptions.page['max'] - data.totalItems))
                    lastAdded = paginationOptions.page['first'] * paginationOptions.page['max'] - data.totalItems;
                  else
                    lastAdded = filtered.length;

                  for (var counter = firstAdded; counter < lastAdded; counter++) {
                    data.data.push(filtered[counter]);
                  }
                }

                vm.dncPNGridOptions.totalItems = data.totalItems + filtered.length;
                vm.totalItemsCountOnAllPages = vm.dncPNGridOptions.totalItems;
                vm.dncPNGridOptions.data = data.data;
              };

            vm.getTotalItemsCountOnAllPages = function() {
              if (vm.totalItemsCountOnAllPages > 0) {
                return vm.totalItemsCountOnAllPages;
              }
            };

              vm.filterElements = function(elements) {
                return elements.filter(function(element) {
                  var result = true;
                  for (var property in paginationOptions.filters) {
                    if (element.hasOwnProperty(property)) {
                      paginationOptions.filters[property].forEach(function(item, i, arr) {
                        if (item['condition'] == undefined || item['condition'] == uiGridConstants.filter.EXACT) {
                          if (element[property].toString() != item['term'])
                            result = false;
                        } else if (item['condition'] == uiGridConstants.filter.CONTAINS) {
                          if (element[property].indexOf(item['term']) < 0)
                            result = false;
                        }
                      });
                    }
                  }
                  return result;
                });
              };

              vm.getPage();

              vm.setEnabled = function(enabled) {

                if (vm.isAllDncPnSelected === true) {
                  if (enabled)
                    $scope.$emit('batchProcessingForSelectedItemsEvent', {
                      type : 'enable',
                      filters : paginationOptions.filters
                    });
                  else
                    $scope.$emit('batchProcessingForSelectedItemsEvent', {
                      type : 'disable',
                      filters : paginationOptions.filters
                    });
                }

                setEnabledInGrid(vm.gridApi.selection.getSelectedRows(), enabled);
                vm.gridApi.selection.getSelectedRows().forEach(function(item, i, arr) {
                  if (item.id != null) {
                    vm.dncPhoneNumberPickLastModified(item);
                    vm.changedRows.edited.push(item);
                  }
                });
                vm.gridApi.core.notifyDataChange('all');
              };

            vm.selectAll = function() {
              if (vm.gridApi.selection.getSelectedCount() > 0) {
                vm.deselectAll();
              } else {
                vm.gridApi.selection.selectAllRows();
                vm.isAllDncPnSelected = true;
              }
            };

            vm.deselectAll = function() {
              vm.gridApi.selection.clearSelectedRows();
              vm.isAllDncPnSelected = false;
            };

            vm.dncPnFileImportBatchDeleteOpenForm = function(size) {
              var modalInstance = $modal.open({
                                                animation : true,
                                                backdrop : 'static',
                                                templateUrl : 'pages/dncPnFileImportBatchDeleteForm.html',
                                                controller : 'dncPnFileImportBatchProcessCtrl as dncPnFileImportBatchDelete',
                                                size : size,
                                              });
              modalInstance.result.then(function(result) {
                if (result != null) {
                  $scope.$emit('dncPnFileImportBatchDeleteSubmitFormEvent', {
                    dncId : vm.dncList.id,
                    result : result
                  });
                }
              }, function() {
                $log.info('Modal import dismissed at: ' + new Date());
              });
            };

            vm.dncPnFileImportBatchAddOpenForm = function(size) {
              var modalInstance = $modal.open({
                                                animation : true,
                                                backdrop : 'static',
                                                templateUrl : 'pages/dncPnFileImportBatchAddForm.html',
                                                controller : 'dncPnFileImportBatchProcessCtrl as dncPnFileImportBatchAdd',
                                                size : size,
                                              });
              modalInstance.result.then(function(result) {
                if (result != null) {
                  $scope.$emit('dncPnFileImportBatchAddSubmitFormEvent', {
                    dncId : vm.dncList.id,
                    result : result
                  });
                }
              }, function() {
                $log.info('Modal import dismissed at: ' + new Date());
              });
            };

            vm.dncPhoneNumberPickLastModified = function(item) {
              if (vm.changedRows.edited.length > 0) {
                for (var i = 0; i < vm.changedRows.edited.length; i++) {
                  if (item.id === vm.changedRows.edited[i].id) {
                    vm.changedRows.edited.splice(vm.changedRows.edited[i]);
                  }
                }
              }
            };

            // modal dialog for downloading
            vm.import = function(size) {
              // process open modal
              var modalInstance = $modal.open({
                                                animation : true,
                                                backdrop : 'static',
                                                // modal template page
                                                templateUrl : 'pages/dncPhoneImportForm.html',
                                                // modal page controller
                                                controller : 'dncPhoneImportFormCtrl as dncimport',
                                                size : size,
                                              });

              // finally receive result
              modalInstance.result.then(function(result) {
                // modal result handler
                if (result != null) {
                  result.forEach(function(item) {
                    var phone = {
                      phoneNumber : +item,
                      enabled : true,
                      reason : 0,
                      createDate: new Date()
                    };
                    if (vm.dncPNGridOptions.data.length < paginationOptions.page['max']) {
                      vm.dncPNGridOptions.data.push(phone);
                    }
                    vm.changedRows.added.push(phone);
                    vm.dncPNGridOptions.totalItems = vm.dncPNGridOptions.totalItems + 1;
                  });
                }
              }, function() {
                // modal dismissed(canceled)
                $log.info('Modal import dismissed at: ' + new Date());
              });
            };

              // modal dialog

              vm.open = function(size) {

                // process open modal
                var modalInstance = $modal.open({
                                                  animation : true,
                                                  backdrop : 'static',
                                                  // modal template page
                                                  templateUrl : 'pages/dncPhone.html',
                                                  // modal page controller
                                                  controller : 'dncPhoneFormCtrl as dncphone',
                                                  size : size,
                                                  resolve: {
                    existingNumbers: function() {
                      var numbers = [];

                      if (vm.dncPNGridOptions.data) {
                        vm.dncPNGridOptions.data.forEach(function(item) {
                          if (!item.deleted) {
                            numbers.push(item.phoneNumber);
                          }
                        });
                      }

                      vm.changedRows.added.forEach(function(item) {
                        if (!item.deleted) {
                          numbers.push(item.phoneNumber);
                        }
                      });

                      return numbers;
                    },
                    createdAt: function() {
                      return new Date();
                    }
                  }
                });

                // finally receive result
                modalInstance.result.then(function(result) {
                  // modal result handler
                  if (!result.createDate) {
                    result.createDate = new Date();
                  }
                  if (vm.dncPNGridOptions.data.length < paginationOptions.page['max']) {
                    vm.dncPNGridOptions.data.push(result);
                  }
                  vm.changedRows.added.push(result);
                  vm.dncPNGridOptions.totalItems = vm.dncPNGridOptions.totalItems + 1;
                }, function() {
                  // modal dismissed(canceled)
                  $log.info('Modal dismissed at: ' + new Date());
                });
              };

              vm.remove = function() {
                if (vm.isAllDncPnSelected === true) {
                  $scope.$emit('batchProcessingForSelectedItemsEvent', {
                    type : 'delete',
                    filters : paginationOptions.filters
                  });
                }
                vm.gridApi.selection.getSelectedRows().forEach(function(item, i, arr) {
                  if (item.id == null) {
                    vm.dncPNGridOptions.data.splice(vm.dncPNGridOptions.data.indexOf(item), 1);
                    vm.changedRows.added.splice(vm.changedRows.added.indexOf(item), 1);
                  } else {
                    item.deleted = true;
                    item.enabled = false;
                    vm.dncPhoneNumberPickLastModified(item);
                    vm.changedRows.deleted.push(item);
                    if (vm.changedRows.edited.indexOf(item) > 0) {
                      vm.changedRows.edited.splice(vm.changedRows.edited.indexOf(item));
                    }
                  }
                });
                if (vm.isAllDncPnSelected === true) {
                  vm.setNoData();
                } else {
                  vm.getPage();
                }
                vm.deselectAll();
              };

            vm.setNoData = function() {
              var data = {totalItems : 0, data : []};
              vm.addUserRows(data);
              };

            vm.getNgReallyMessageForBatchUpdate = function(actionType) {
              if (vm.isAllDncPnSelected === true) {
                var actionMessage ='';
                if (actionType === 'enable') {
                  actionMessage = $filter('i18n')('label.dncpn.batch.update.action.type.enable');
                }
                if (actionType === 'disable') {
                  actionMessage = $filter('i18n')('label.dncpn.batch.update.action.type.disable');
                }
                if (actionType === 'delete') {
                   actionMessage = $filter('i18n')('label.dncpn.batch.update.action.type.delete');
                }
                var messagePart1 = $filter('i18n')('label.dncpn.batch.update.1');
                var messagePart2 = $filter('i18n')('label.dncpn.batch.update.2');
                var messagePart3 = $filter('i18n')('label.dncpn.batch.update.3');
                return messagePart1 + ' ' + actionMessage + ' ' + vm.totalItemsCountOnAllPages + ' ' + messagePart2 + ' ' + messagePart3
              } else {
                var messageDefault = $filter('i18n')('label.confirm');
                return messageDefault;
              }
            };

              $scope.$on("$destroy", function(event) {
                if (angular.isDefined(vm.filterTimeout)) {
                  $timeout.cancel(vm.filterTimeout);
                }
              });
          }]);
