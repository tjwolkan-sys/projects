app.controller('dncPhoneFormCtrl', ['$scope', '$modalInstance', 'alertService', 'locMsg', 'existingNumbers', 'createdAt',
  function($scope, $modalInstance, alertService, locMsg, existingNumbers, createdAt) {

    var vm = this;
    vm.existingNumbers = existingNumbers || [];
    vm.alertMessage = '';
    vm.createdAt = createdAt || new Date();

    vm.item = {
      enabled : true,
      reason : 0,
      createDate : vm.createdAt
    };

    vm.validate = function() {

      var phoneNumber = vm.item.phoneNumber.toString();
      for (var i = 0; i < vm.existingNumbers.length; i++) {
        if (vm.existingNumbers[i].toString() === phoneNumber) {
          vm.alertMessage = 'alert.dnc.phone.duplicate';
          return false;
        }
      }

      if (vm.item.expiredDate && vm.item.expiredDate < vm.createdAt) {
        vm.alertMessage = 'alert.dnc.phone.expireDate.beforeCreateDate';
        return false;
      }

      return true;
    };

    vm.save = function() {
      if (!vm.validate()) {
        if ($scope.dncPhone) {
          $scope.dncPhone.$setSubmitted();
        }
        alertService.add('danger', locMsg[vm.alertMessage]);
        return;
      }

      $modalInstance.close(vm.item);
    };

    vm.cancel = function() {
      $modalInstance.dismiss('cancel');
    };
  }]);
