/**
 * Controller to edit sortField
 */
app.controller('sortFieldFormCtrl', [
  '$modalInstance',
  'item',
  'campaign',
  'campaignInitial',
  'listSourceColumns',
  'constants',
  'locMsg',
  function($modalInstance, item, campaign, campaignInitial, listSourceColumns, constants, locMsg) {

  var vm = this;
  vm.item = angular.copy(item);
  vm.campaign = campaign;
  vm.campaignInitial = campaignInitial;
  vm.itemInitial = _.findWhere(vm.campaignInitial.sortFields, vm.item.id);
  vm.listSourceColumns = listSourceColumns;
  vm.constants = constants;
  vm.sortDirections = [ {
      value : 0,
      label : locMsg['campaign.dbListSortDirection.0']
    }, {
      value : 1,
      label : locMsg['campaign.dbListSortDirection.1']
    } ];
  vm.sortMethods = [ {
      value : 0,
      label : locMsg['sortField.sortMethod.0']
    }, {
      value : 1,
      label : locMsg['sortField.sortMethod.1']
    }, {
      value : 2,
      label : locMsg['sortField.sortMethod.2']
    } ];

   if (vm.item.sortDirection === undefined) {
     vm.item.sortDirection = vm.sortDirections[1].value;
   }

  vm.save = function() {
    vm.item.changed = vm.item.changed || !angular.equals(vm.item, item);
    $modalInstance.close(vm.item);
  };

  vm.cancel = function() {
    $modalInstance.dismiss('cancel');
  };

}]);
