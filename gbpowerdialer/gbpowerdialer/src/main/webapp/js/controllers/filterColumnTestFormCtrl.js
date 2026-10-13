/**
 * Controller to test filters of a campaign
 */
app.controller('filterColumnTestFormCtrl', function($modalInstance, campaign, listSourceColumns, $scope, $timeout, $log, locMsg, $http, i18nService, campaignFactory) {
  var vm = this;

  campaignFactory.filtersTest(campaign).then(function(data) {
    for (var i = 0; i < data.length; i++) {
      data[i].unshift(i + 1);
    }
    var initialVisibleItemCount = 10;
    vm.testGridOptions.columnDefs = [];
    if (data.length > 0) {
      listSourceColumns.forEach(function(item, i, arr) {
        vm.testGridOptions.columnDefs.push({
          displayName : item.fieldName,
          enableHiding : true,
          enableColumnMenu : false,
          visible : i < initialVisibleItemCount,
          field : 'value',
          cellTemplate : '<div class="ui-grid-cell-contents">{{row.entity[' + i + ']}}</div>'
        });
      });
      vm.testGridOptions.totalItems = data.length;
      vm.testGridOptions.data = data;
      vm.refreshTable = $timeout(function() {
        vm.gridApi.grid.refresh();
      }, 500);
    }
  });

  vm.testGridOptions = {
    useExternalPagination : false,
    useExternalSorting : false,
    useExternalFiltering : false,
    enableRowSelection : false,
    enableSelectAll : false,
    enableFiltering : false,
    enableSorting : false,
    enableFullRowSelection : false,
    enableGridMenu : true,
    modifierKeysToMultiSelect : false,
    i18n : 'en',
    columnDefs : []
  };

  vm.testGridOptions.onRegisterApi = function(gridApi) {
    vm.gridApi = gridApi;
  };

  i18nService.setCurrentLang(locMsg['lang.id']);

  vm.cancel = function() {
    $modalInstance.dismiss('cancel');
  };

  $scope.$on('$destroy', function(e) {
    if (angular.isDefined(vm.refreshTable)) {
      $timeout.cancel(vm.refreshTable);
    }
  });

});
