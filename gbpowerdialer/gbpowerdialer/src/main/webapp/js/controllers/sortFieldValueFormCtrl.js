/**
 * Controller to edit sortFieldValue
 */
app.controller('sortFieldValueFormCtrl', function($modalInstance, item, campaign, sortField, locMsg, alertService) {

  var vm = this;
  vm.itemInitial = item;
  vm.item = angular.copy(item);
  vm.campaign = campaign;
  vm.sortField = sortField;

  vm.save = function() {
    if (vm.itemInitial && vm.itemInitial.value !== vm.item.value && sortField.values.filter(value => value.value === vm.item.value).length !== 0) {
      alertService.add('danger', locMsg['alert.sortFieldValue.duplicate']);
    } else {
      vm.item.changed = vm.item.changed || !angular.equals(vm.item, item);
      $modalInstance.close(vm.item);
    }
  };

  vm.cancel = function() {
    $modalInstance.dismiss('cancel');
  };

});
