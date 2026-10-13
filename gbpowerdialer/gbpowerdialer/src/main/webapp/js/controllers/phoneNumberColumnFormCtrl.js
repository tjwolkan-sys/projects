/**
 * Controller to edit phoneNumberColumn
 */
app.controller('phoneNumberColumnFormCtrl', function($modalInstance, item, campaign, listSourceColumns, constants) {

  var vm = this;
  vm.item = angular.copy(item);
  vm.campaign = campaign;
  vm.listSourceColumns = listSourceColumns;
  vm.constants = constants;

  vm.save = function() {
    vm.item.changed = vm.item.changed || !angular.equals(vm.item, item);
    $modalInstance.close(vm.item);
  };

  vm.cancel = function() {
    $modalInstance.dismiss('cancel');
  };
});
