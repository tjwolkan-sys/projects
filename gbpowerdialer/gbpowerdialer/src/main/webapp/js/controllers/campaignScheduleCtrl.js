/**
 * Controller to show schedules of a campaign
 */
app.controller('campaignScheduleCtrl', [ '$scope', '$http', 'campaignScheduleFactory', 'uiGridConstants', '$log', '$modal', 'i18nService', '$locale', '$rootScope', '$filter',
    'locMsg', 'uiGridGroupingConstants', 'constants',
    function($scope, $http, campaignScheduleFactory, uiGridConstants, $log, $modal, i18nService, $locale, $rootScope, $filter, locMsg, uiGridGroupingConstants, constants) {
      var vm = this;

      vm.schedules = null;

      vm.selectedSchedules = [];

      vm.setEnabled = function(enabled) {
        setEnabledInGrid(vm.gridApi.selection.getSelectedRows(), enabled);
        if (enabled === false) {
          setResetEnabled(vm.gridApi.selection.getSelectedRows(), enabled);
        }
      };

      function setResetEnabled(selectedRows, enabled) {
        _.each(selectedRows, function(item) {
          if (item.resetEnabled !== enabled) {
            item.resetEnabled = enabled;
            item.changed = true;
          }
        });
      }

      vm.campaignScheduleGridOptions = {
        useExternalSorting : false,
        useExternalFiltering : true,
        enableRowSelection : true,
        enableFullRowSelection : true,
        enableSelectAll : true,
        enableFiltering : false,
        modifierKeysToMultiSelect : true,
        i18n : 'en',
        columnDefs : [ {
          displayName : locMsg['campaign.schedule.day'],
          field : 'day',
          cellTemplate : '<div class="ui-grid-cell-contents">{{"week.day." + row.entity.day | i18n}}</div>',
          minWidth : 50
        }, {
          displayName : locMsg['campaign.schedule.from'],
          field : 'from',
          cellTemplate : '<div class="ui-grid-cell-contents">{{row.entity.from | date:"HH:mm"}}</div>',
          minWidth : 50
        }, {
          displayName : locMsg['campaign.schedule.till'],
          field : 'till',
          cellTemplate : '<div class="ui-grid-cell-contents">{{row.entity.till | date:"HH:mm"}}</div>',
          minWidth : 50
        }, {
          displayName : locMsg['label.enabled'],
          field : 'enabled',
          cellTemplate : '<input type="checkbox" ng-model="row.entity.enabled" ng-disabled="true"></input>',
          minWidth : 50
        },
          {
            displayName : locMsg['label.reset_enabled'],
            field : 'resetEnabled',
            cellTemplate : '<input type="checkbox" ng-model="row.entity.resetEnabled" ng-disabled="true"></input>',
            minWidth : 50
          } ]
      };

      vm.campaignScheduleGridOptions.onRegisterApi = function(gridApi) {
        vm.gridApi = gridApi;
        gridApi.selection.on.rowSelectionChanged($scope, function(row) {
          vm.selectedSchedules = vm.gridApi.selection.getSelectedRows();
        });

        gridApi.selection.on.rowSelectionChangedBatch($scope, function () {
          vm.selectedSchedules = vm.gridApi.selection.getSelectedRows();
        });

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
      };

      i18nService.setCurrentLang(locMsg['lang.id']);

      vm.open = function(size, item) {
        vm.item = item;

        vm.isNew = true;
        if (item.id != null) {
          vm.isNew = false;
        }

        var modalInstance = $modal.open({
          animation : true,
          backdrop : 'static',
          templateUrl : 'pages/campaignScheduleForm.html',
          controller : 'campaignScheduleFormCtrl as scheduleForm',
          size : size,
          resolve : {
            item : function() {
              return vm.item;
            },
            campaign : function() {
              return vm.campaign;
            }
          }
        });

        modalInstance.result.then(function(result) {
          if (!vm.isNew) {
            vm.campaign.schedules = _.without(vm.campaign.schedules, _.findWhere(vm.campaign.schedules, {
              id : item.id
            }));
          } else if (vm.item.day != null) {
            vm.campaign.schedules.splice(vm.campaign.schedules.indexOf(vm.item), 1);
          }

          vm.campaign.schedules.push(result);
          vm.refreshTable(vm.campaign.schedules);

          vm.selectedSchedules = [];
        }, function() {
          $log.info('Modal dismissed at: ' + new Date());
        });

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
          vm.refreshTable(vm.campaign.schedules);
          vm.campaignInitial.schedules = angular.copy(vm.campaign.schedules);
        }
      });

      $scope.$watchCollection('campaignSchedule.campaign.schedules', function() {
        if (vm.campaign.schedules != null)
          vm.refreshTable(vm.campaign.schedules);
      });

      vm.toggleFiltering = function() {
        vm.campaignScheduleGridOptions.enableFiltering = !vm.campaignScheduleGridOptions.enableFiltering;
        vm.gridApi.core.notifyDataChange(uiGridConstants.dataChange.COLUMN);
      };

      vm.getPage = function() {
        if (vm.schedules == null) {
          campaignScheduleFactory.getAll(paginationOptions.page, {
            'field' : 'day',
            'direction' : 'asc'
          }, paginationOptions.filters).then(function(data) {
            vm.campaign.schedules = data.data;
            vm.campaignInitial.schedules = angular.copy(data.data);
            vm.refreshTable(vm.campaign.schedules);
          });
        }
      };

      vm.remove = function() {
        removeSelectedSchedules(vm.gridApi.selection.getSelectedRows());
        vm.refreshTable(vm.campaign.schedules);
      };

      function removeSelectedSchedules(schedules) {
        schedules.forEach(function (item) {
          var filters = {};
          if (item.id == null) {
            filters = {
              day: item.day,
              from: item.from
            };
            vm.campaign.schedules = _.without(vm.campaign.schedules, _.findWhere(vm.campaign.schedules, filters));
          } else {
            item.deleted = item.changed = true;
            item.enabled = false;
          }
        });
      }

      vm.refreshTable = function(sch) {
        vm.selectedSchedules = [];
        vm.schedules = [];
        vm.campaignScheduleGridOptions.data = [];
        sch.sort(function (a, b) {
          return a.day - b.day || a.from - b.from
        });

        for (var i = 0; i < sch.length; i++) {
          if(sch[i].deleted == false) {
            vm.schedules.push(sch[i])
          }
        }
        vm.campaignScheduleGridOptions.data = vm.schedules;
      };

      vm.disabledEditButton = function() {
        if (vm.selectedSchedules.length == 1) {
          return false;
        }
        return true;
      };

      vm.getSelectedSchedule = function() {
        if (vm.selectedSchedules.length > 0) {
          return vm.selectedSchedules[0];
        }
        return {};
      };

      vm.newCampaignSchedule = function() {
        return campaignScheduleFactory.createNew();
      };

    } ]);
