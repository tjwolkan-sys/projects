/**
 * Controller to edit campaignPredefinedValue
 */
app.controller('rangePredefinedValueCtrl', function($modalInstance, campaign, maxPredefinedValue, minPredefinedValue, constants, systemMax, systemMin) {

  var vm = this;
  vm.maxPredefinedValue = angular.copy(maxPredefinedValue);
  vm.minPredefinedValue = angular.copy(minPredefinedValue);
  vm.rangeEnabled = maxPredefinedValue.enabled && minPredefinedValue.enabled && (vm.maxPredefinedValue.value >= vm.minPredefinedValue.value);
  vm.constants = constants;
  vm.systemMax = systemMax;
  vm.systemMin = systemMin;

  vm.save = function() {
    vm.maxPredefinedValue.changed = vm.maxPredefinedValue.changed || !angular.equals(vm.maxPredefinedValue, maxPredefinedValue) || vm.rangeEnabled !== (maxPredefinedValue.enabled && minPredefinedValue.enabled);
    vm.minPredefinedValue.changed = vm.minPredefinedValue.changed || !angular.equals(vm.minPredefinedValue, minPredefinedValue) || vm.rangeEnabled !== (maxPredefinedValue.enabled && minPredefinedValue.enabled);
    if (vm.maxPredefinedValue.changed || vm.minPredefinedValue.changed) {
      vm.maxPredefinedValue.enabled = vm.minPredefinedValue.enabled = vm.rangeEnabled;
      vm.maxPredefinedValue.isNew = false;
      vm.minPredefinedValue.isNew = false;
      $modalInstance.close([vm.maxPredefinedValue, vm.minPredefinedValue]);
    } else {
      $modalInstance.close([maxPredefinedValue, minPredefinedValue]);
    }
  };

  vm.isRangeValid = function() {
   return vm.maxPredefinedValue.value >= vm.minPredefinedValue.value;
  };

  vm.cancel = function() {
    $modalInstance.dismiss('cancel');
  };
  
});
