/**
 * Controller to show details of an adminLog
 */
app.controller('adminLogsDetailsCtrl', [
    '$scope',
    '$http',
    'uiGridConstants',
    '$log',
    '$modal',
    'i18nService',
    '$locale',
    'adminLogsDetailsFactory',
    '$rootScope',
    '$filter',
    'locMsg',
    '$window',
    '$timeout',
    'uiGridConstants',
    '$q',
    'item',
    '$modalInstance',
    function($scope, $http, uiGridConstants, $log, $modal, i18nService, $locale, adminLogsDetailsFactory, $rootScope, $filter, locMsg, $window, $timeout, uiGridConstants, $q,
             item, $modalInstance) {

      var vm = this;
      vm.item = item;
      vm.adminLogsDetails = [];

      vm.adminLogsDetailsGridOptions = {
        enableRowSelection : false,
        enableSelectAll : false,
        enableFiltering : false,
        enableFullRowSelection : false,
        multiSelect : false,
        enableGridMenu : true,
        i18n : 'en',
        columnDefs : [ {
          displayName : locMsg['label.id'],
          field : 'id',
          headerCellClass : vm.highlightFilteredHeader,
          type : 'number',
          enableHiding : true,
          visible : false,
          filters : [ {
            condition : uiGridConstants.filter.CONTAINS
          } ],
          minWidth : 50
        }, {
          displayName : locMsg['admin.logs.details.field'],
          field : 'field',
          enableHiding : false,
          headerCellClass : vm.highlightFilteredHeader,
          filters : [ {
            condition : uiGridConstants.filter.CONTAINS
          } ],
          minWidth : 100
        }, {
          displayName : locMsg['admin.logs.details.previousValue'],
          field : 'previousValue',
          enableHiding : false,
          headerCellClass : vm.highlightFilteredHeader,
          filters : [ {
            condition : uiGridConstants.filter.CONTAINS
          } ],
          minWidth : 100
        }, {
          displayName : locMsg['admin.logs.details.newValue'],
          field : 'newValue',
          enableHiding : false,
          headerCellClass : vm.highlightFilteredHeader,
          filters : [ {
            condition : uiGridConstants.filter.CONTAINS
          } ],
          minWidth : 100
        }, {
          displayName : locMsg['admin.logs.details.added'],
          field : 'added',
          enableHiding : false,
          headerCellClass : vm.highlightFilteredHeader,
          filters : [ {
            condition : uiGridConstants.filter.CONTAINS
          } ],
          minWidth : 100
        }, {
          displayName : locMsg['admin.logs.details.removed'],
          field : 'removed',
          enableHiding : false,
          headerCellClass : vm.highlightFilteredHeader,
          filters : [ {
            condition : uiGridConstants.filter.CONTAINS
          } ],
          minWidth : 100
        } ]
      };

      vm.loadAdminLogsDetails = function() {
        var d = $q.defer();
        var self = this;
        if (vm.item.id != null) {
          adminLogsDetailsFactory.getAll({}, {
            'field' : 'name',
            'direction' : 'asc'
          }, {
            'adminLog.id' : [ {
              term : item.id.toString(),
              condition : uiGridConstants.filter.EXACT
            } ]
          }).then(function(data) {
            self.adminLogsDetails = data.data;
            self.adminLogsDetailsGridOptions.data = self.adminLogsDetails;
            d.resolve();
          });
        } else {
          d.resolve();
        }
        return d.promise;
      };

      vm.adminLogsDetailsGridOptions.onRegisterApi = function(gridApi) {
        gridApi.core.on.columnVisibilityChanged($scope, function(changedColumn) {
          $scope.columnChanged = {
            name : changedColumn.colDef.name,
            visible : changedColumn.colDef.visible
          };
        });
      };

      vm.loadAdminLogsDetails();

      vm.close = function() {
        $modalInstance.dismiss('cancel');
      };

    } ]);