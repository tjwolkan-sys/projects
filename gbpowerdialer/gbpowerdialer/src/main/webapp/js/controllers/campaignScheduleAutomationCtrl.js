/**
 * Controller to show schedules automation of a campaign
 */
app.controller('campaignScheduleAutomationCtrl', [
  '$scope',
  '$http',
  'campaignScheduleAutomationFactory',
  'uiGridConstants',
  '$log', '$modal',
  'i18nService',
  '$locale',
  '$rootScope',
  '$filter',
  'locMsg',
  '$q',
  'groupFactory',
  'alertService',
  'campaignGroupFactory',
  function($scope, $http, campaignScheduleAutomationFactory, uiGridConstants, $log, $modal, i18nService, $locale, $rootScope, $filter, locMsg,  $q, groupFactory, alertService,campaignGroupFactory) {
    var vm = this;

    vm.selectedScheduleAutomations = null;

    vm.setEnabled = function(enabled) {
      if (enabled && vm.enabledScheduleAutomationWithSameActionExist(vm.gridApi.selection.getSelectedRows()[0])) {
        alertService.add('danger', locMsg['scheduleAutomation.enabled.alert']);
      } else {
        setEnabledInGrid(vm.gridApi.selection.getSelectedRows(), enabled);
      }
    };

    vm.enabledScheduleAutomationWithSameActionExist = function(targetSchedule){
      return vm.campaign.scheduleAutomations.filter(schedule => {
        return vm.compareSchedule(schedule, targetSchedule);
      }).length > 0;
    };

    vm.compareSchedule = function(schedule, targetSchedule){
      return schedule.$$hashKey !== targetSchedule.$$hashKey && schedule.enabled && schedule.actionType === targetSchedule.actionType;
    };

    vm.highlightFilteredHeader = function(row, rowRenderIndex, col) {
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
        vm.campaign.scheduleAutomations = vm.scheduleAutomationGridOptions.data = [];
      }
    });

    vm.scheduleAutomationGridOptions = {
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
        displayName : locMsg['scheduleAutomation.name'],
        field : 'name',
        enableHiding : false,
        headerCellClass : vm.highlightFilteredHeader,
        filters : [ {
          condition : uiGridConstants.filter.CONTAINS
        } ],
        minWidth : 100
      }, {
        displayName : locMsg['scheduleAutomation.description'],
        field : 'description',
        enableHiding : false,
        headerCellClass : vm.highlightFilteredHeader,
        filters : [ {
          condition : uiGridConstants.filter.CONTAINS
        } ],
        minWidth : 100
      }, {
        displayName : locMsg['scheduleAutomation.group'],
        field : 'groups',
        enableHiding : false,
        headerCellClass : vm.highlightFilteredHeader,
        filters : [ {
          condition : uiGridConstants.filter.CONTAINS
        } ],
        cellTemplate : '<div class="ui-grid-cell-contents"><span ng-repeat="group in row.entity.groups track by $index">{{group.name}}<span ng-if=" ! $last ">, </span></span></div>',
        minWidth : 100
      }, {
        displayName : locMsg['scheduleAutomation.action'],
        field : 'actionType',
        enableHiding : false,
        headerCellClass : vm.highlightFilteredHeader,
        filters : [ {
          condition : uiGridConstants.filter.CONTAINS
        } ],
        cellTemplate : '<div class="ui-grid-cell-contents">' +
            '{{ row.entity.actionType === 0 ? "Start" : (row.entity.actionType === 1 ? "Stop" : "Pause") }}' +
            '</div>',
        minWidth : 100
      },{
        displayName : locMsg['scheduleAutomation.enabled'],
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

    vm.scheduleAutomationGridOptions.onRegisterApi = function(gridApi) {
      // set gridApi on scope
      vm.gridApi = gridApi;
      let onSelect = function() {
        vm.selectedScheduleAutomations = vm.gridApi.selection.getSelectedRows();
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

    i18nService.setCurrentLang(locMsg['lang.id']);

    vm.getPage = function() {
      campaignScheduleAutomationFactory.getAll(paginationOptions.page, paginationOptions.sort, paginationOptions.filters).then(function(data) {
        vm.scheduleAutomationGridOptions.totalItems = data.totalItems;
        vm.scheduleAutomationGridOptions.data = data.data;
        vm.campaign.scheduleAutomations = data.data;
        vm.campaignInitial.scheduleAutomations = angular.copy(data.data);
      });
    };

    vm.remove = function() {
      vm.gridApi.selection.getSelectedRows().forEach(function(item) {
        var index = vm.scheduleAutomationGridOptions.data.indexOf(item);
        if (item.id == null) {
          vm.scheduleAutomationGridOptions.data.splice(index, 1);
        } else {
          item.deleted = true;
          item.changed = true;
          item.enabled = false;
        }
        vm.selectedAutomationSchedules = null;
      });
    };

    vm.processGroupList = function () {
      var groupListDeferred = $q.defer();
      if (!vm.groupList) {
        var p = (vm.campaign && vm.campaign.id)
            ? groupFactory.getByCampaign(vm.campaign.id)
            : groupFactory.getAll({}, { field: 'name', direction: 'asc' }, { enabled: [{ term: 'true' }] });

        p.then(function (data) {
          var raw = (data && data.data) ? data.data : (data || []);
          vm.groupList = raw
              .map(function (cg) {
                var g = cg && cg.group ? cg.group : {};
                return {
                  id: g.id,
                  name: g.name,
                  priority: cg.priority
                };
              })
              .filter(function (x) { return x.id != null; });
          groupListDeferred.resolve();
        }, function () {
          vm.groupList = [];
          groupListDeferred.resolve();
        });
      } else {
        groupListDeferred.resolve();
      }
       return groupListDeferred.promise;
    };


    vm.processScheduleAutomationList = function () {
      let deferred = $q.defer();
      if (vm.scheduleAutomationList == null) {
        campaignScheduleAutomationFactory.getAll({}, {
          'field' : 'name',
          'direction' : 'asc'
        }, {
          'enabled' : [ {
            term : 'true'
          } ]
        }).then(function(data) {
          vm.scheduleAutomationList = data.data;
          deferred.resolve();
        });
      } else {
        deferred.resolve();
      }
      return deferred.promise;
    };

    function normalizeSelectedGroups() {
      if (!vm.item || !vm.item.groups) return;
      vm.item.groups = vm.item.groups.map(function (gr) {
        var id = (gr && gr.id != null)
            ? gr.id
            : (gr && gr.group && gr.group.id != null)
                ? gr.group.id
                : gr;

        if (vm.groupList && vm.groupList.length) {
          for (var i = 0; i < vm.groupList.length; i++) {
            if (vm.groupList[i].id === id) {
              return vm.groupList[i];
            }
          }
        }
        var name = (gr && gr.name) ? gr.name : (gr && gr.group ? gr.group.name : undefined);
        return { id: id, name: name };
      });
    }

    vm.open = function (size, item) {
      $q.all([vm.processGroupList()]).then(function () {
        vm.item = item;
        if (vm.groupList && vm.groupList.length && vm.groupList[0].group) {
          vm.groupList = vm.groupList
              .map(function (cg) {
                var g = cg && cg.group ? cg.group : {};
                return { id: g.id, name: g.name, priority: cg.priority };
              })
              .filter(function (x) { return x.id != null; });
        }
        normalizeSelectedGroups();

        item.isNew = item.isNew == null ? item.id == null : item.isNew;
        if (item.isNew) {
          vm.item.groups = vm.item.groups || [];
        }

        var modalInstance = $modal.open({
          animation: true,
          backdrop: 'static',
          templateUrl: 'pages/campaignScheduleAutomationForm.html',
          controller: 'campaignScheduleAutomationFormCtrl as scheduleAutomationForm',
          size: size,
          resolve: {
            campaign: function () { return vm.campaign; },
            scheduleAutomationList: function () { return vm.scheduleAutomationList; },
            item: function () { return vm.item; },
            scheduleAutomations: function () { return vm.scheduleAutomationGridOptions.data; },
            groupList: function () { return vm.groupList; },
            queueList: function () { return []; }
          }
        });

        modalInstance.result.then(function (result) {
          if (vm.item.isNew) {
            result.isNew = false;
            vm.scheduleAutomationGridOptions.data.unshift(result);
          } else {
            vm.item.groups = result.groups;
            angular.merge(vm.item, result);
          }
        }, function () {
          $log.info('Modal dismissed at: ' + new Date());
        });
      });
    };

    vm.newCampaignScheduleAutomation = function() {
      return campaignScheduleAutomationFactory.createNew();
    };

    vm.toggleFiltering = function() {
      vm.scheduleAutomationGridOptions.enableFiltering = !vm.scheduleAutomationGridOptions.enableFiltering;
      vm.gridApi.core.notifyDataChange(uiGridConstants.dataChange.COLUMN);
    };

    vm.disableButton = function() {
      if (vm.selectedScheduleAutomations && vm.selectedScheduleAutomations.length == 1) {
        return false;
      }
      return true;
    };

  } ]);
