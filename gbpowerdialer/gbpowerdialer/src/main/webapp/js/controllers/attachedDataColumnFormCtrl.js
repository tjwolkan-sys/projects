/**
 * Controller to edit an attachedData
 */
app.controller('attachedDataColumnFormCtrl', function($modalInstance, item, campaign, $log, locMsg, tenantFactory, locationFactory, dncFactory, $rootScope, $q, $scope,
                                                      uiGridConstants, listSourceColumns, alertService) {
  var vm = this;
  vm.item = angular.copy(item);
  vm.campaign = campaign;
  vm.listSourceColumns = listSourceColumns;

  if (vm.item !== undefined && vm.item.field === undefined) {
    vm.item.saveLongTerm = true;
  }
  /**
   * Handle save for attachedData
   */
  vm.save = function() {
    if (!isValidDecorator())
      return;

    vm.item.changed = vm.item.changed || !angular.equals(vm.item, item);
    $modalInstance.close(vm.item);
  };

  /**
   * Handle cancel
   */
  vm.cancel = function() {
    $modalInstance.dismiss('cancel');
  };

  /**
   * Add additional validations
   */
  function isValidDecorator() {
    if (isDuplicateFieldName()) {
      alertService.add('danger', locMsg['attachedData.error.duplicate.fieldName']);
      return false;
    }

    return true;
  }

  /**
   * Check for duplicate fieldName
   */
  function isDuplicateFieldName() {
    for (var i = 0; i < vm.campaign.attachedDatas.length; i++) {
      var existingAttachedData = vm.campaign.attachedDatas[i];
      if (existingAttachedData.deleted !== true && existingAttachedData.id !== vm.item.id
          && existingAttachedData.fieldName === vm.item.fieldName) {
        return true
      }
    }
    return false;
  }

  vm.onFieldChange = function onFieldChange() {
    vm.item.fieldName = vm.item.field;
    vm.item.popupName = vm.item.field;
  }

});
