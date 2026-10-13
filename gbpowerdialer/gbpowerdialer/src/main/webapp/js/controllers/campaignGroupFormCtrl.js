/**
 * Controller to edit groups of a campaign
 */
app.controller('campaignGroupFormCtrl', function($modalInstance, item, campaign, groupList, campaignGroups, alertService, locMsg) {

  var vm = this;
  vm.item = angular.copy(item);
  vm.campaign = campaign;
  vm.groupList = groupList;
  vm.campaignGroups = campaignGroups;
  vm.alertMessage = "";

  vm.saveAndContinue = function() {
    if (vm.validate()) {
      vm.item.changed = vm.item.changed || !angular.equals(vm.item, item);
      if (vm.item.isNew) {
        vm.item.isNew = false;
        vm.campaignGroups.unshift(vm.item);
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
    item.deleted = false;
    item.enabled = true;
    item.priority = 0;
  };

  vm.copyItemData = function() {
    let campaignGroup = {};
    campaignGroup.group = vm.item.group;
    campaignGroup.priority = vm.item.priority;
    campaignGroup.enabled = vm.item.enabled;
    campaignGroup.deleted = false;
    campaignGroup.isNew = true;
    vm.item = campaignGroup;
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

  vm.validate = function() {
    var groups = [];
    campaignGroups.forEach(function(item, i, arr) {
      groups.push(item.group);
    });
    var isDuplicated = _.findWhere(_.without(groups, item.group), {
      name : vm.item.group.name,
    });
    if (isDuplicated) {
      vm.alertMessage = 'alert.group.duplicate';
      return false;
    }
    return true;
  };

  vm.isEditGroup = function() {
    if (vm.item.id >=0 || vm.item.changed != undefined) {
      return true;
    } else
      return false;
  };
});
