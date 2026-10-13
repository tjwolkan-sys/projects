/**
 * Controller to show list of fieds for lsc
 */
app.controller('lscFieldCtrl', [ 'item', 'header', '$modalInstance', '$filter', function(item, header, $modalInstance, $filter) {
  var vm = this;
  vm.item = item;
  vm.header = $filter('i18n')(header);
  vm.selected = {
    item : angular.copy(vm.item)
  };

  vm.save = function() {
    var newItem = {};
    newItem['key'] = vm.selected.item.key;
    newItem['value'] = vm.selected.item.value;

    $modalInstance.close(newItem);
  };

  vm.cancel = function() {
    $modalInstance.dismiss('cancel');
  };

} ]);
