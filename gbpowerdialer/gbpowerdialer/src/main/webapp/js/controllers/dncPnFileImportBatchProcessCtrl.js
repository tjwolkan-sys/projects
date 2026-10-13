/**
 * Controller to import file with phone numbers to delete
 */
app.controller('dncPnFileImportBatchProcessCtrl', ['$scope', '$modalInstance', 'i18nService', 'alertService', 'locMsg',
                                                   function($scope, $modalInstance, i18nService, alertService, locMsg) {

  var vm = this;
  vm.numberInput = 1;
  vm.nameInput = '';
  vm.check = false;
  vm.file = undefined;
  vm.validExtensions = ['.csv', '.xls', '.xlsx'];

  vm.save = function() {
    if (!vm.hasValidFileExtension() || vm.file.size === 0) {
      alertService.add('danger', locMsg['dnc.pn.file.invalid.alert']);
      return;
    }

  var resultArr = [];
  var params = {};
  var file = vm.file;

  resultArr.push(file);
  params.checkboxResult = vm.check;

  if (vm.check) {
    params.columnParameter = vm.nameInput;
    params.expiredColumnParameter = vm.expiredNameInput;
  } else {
    params.columnParameter = (vm.numberInput - 1).toString();
    params.expiredColumnParameter = (vm.expiredNumberInput - 1).toString();
  }

  var blobParameters = new Blob([JSON.stringify(params)], {'type': 'application/json'});
  resultArr.push(blobParameters);
  $modalInstance.close(resultArr);
  };

  vm.hasValidFileExtension = function() {
    var fileExtension = vm.file.name;
    fileExtension = fileExtension.substring(fileExtension.lastIndexOf('.'));
    return vm.validExtensions.includes(fileExtension);
  };

  vm.isValidForm = function() {
    return vm.file !== undefined && (vm.check ? vm.isValidNameInput() : vm.isValidNumberInput());
  };

  vm.isValidNameInput = function() {
    return vm.nameInput !== undefined && vm.nameInput.trim().length > 0;
  };

  vm.isValidNumberInput = function() {
    return vm.numberInput !== undefined && vm.numberInput > 0;
  };

  vm.cancel = function() {
    $modalInstance.dismiss('cancel');
  };

}]);