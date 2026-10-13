/**
 * Controller to edit resultCodeScheduleAdvanced
 */
app.controller('resultCodeScheduleAdvancedFormCtrl', function($modalInstance, item, campaign, resultCodeSchedules, alertService, locMsg) {

  var vm = this;
  vm.item = angular.copy(item);
  vm.campaign = campaign;
  vm.resultCodeSchedules = resultCodeSchedules;
  vm.alertMessage = "";

  vm.saveAndContinue = function() {
    if (vm.validate()) {
      vm.item.changed = vm.item.changed || !angular.equals(vm.item, item);
      if (vm.item.isNew) {
        vm.item.isNew = false;
        vm.resultCodeSchedules.unshift(vm.item);
      } else {
        angular.merge(item, vm.item);
      }

      vm.setNewInitialItem();
      vm.copyItemData();
      alertService.add('success', locMsg['label.save.success']);
    } else {
      alertService.add('danger', locMsg[vm.alertMessage]);
    }
  };

  vm.setNewInitialItem = function() {
    item = {};
    item.dialAttempt = 0;
    item.retryInterval = 0;
  };

  vm.copyItemData = function() {
    var resultCodeScheduleAdvanced = {};
    resultCodeScheduleAdvanced.dialAttempt = vm.item.dialAttempt;
    resultCodeScheduleAdvanced.retryInterval = vm.item.retryInterval;
    resultCodeScheduleAdvanced.numberToTry = vm.item.numberToTry;
    resultCodeScheduleAdvanced.enabled = vm.item.enabled;
    resultCodeScheduleAdvanced.deleted = false;
    resultCodeScheduleAdvanced.isNew = true;
    vm.item = resultCodeScheduleAdvanced;
  };

  vm.save = function() {
    if (vm.validate()) {
      vm.item.changed = vm.item.changed || !angular.equals(vm.item, item);
      $modalInstance.close(vm.item);
    } else {
      alertService.add('danger', locMsg[vm.alertMessage]);
    }
  };

  vm.cancel = function() {
    $modalInstance.dismiss('cancel');
  };

  vm.numberToTryList = [ {
    value : 0,
    label : locMsg['resultCodeScheduleAdvanced.numberToTry.0']
  }, {
    value : 1,
    label : locMsg['resultCodeScheduleAdvanced.numberToTry.1']
  }, {
    value : 2,
    label : locMsg['resultCodeScheduleAdvanced.numberToTry.2']
  } ];

  vm.validate = function() {
    var isDuplicated = _.findWhere(_.without(resultCodeSchedules, item), {
      dialAttempt : vm.item.dialAttempt,
      deleted : false,
    });
    if (isDuplicated) {
      vm.alertMessage = 'alert.resultcodeschedule.dialattempt.duplicate';
      return false;
    }
    return true;
  };

});
