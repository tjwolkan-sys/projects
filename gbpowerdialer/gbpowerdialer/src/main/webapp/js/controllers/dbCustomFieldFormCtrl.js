app.controller('dbCustomFieldFormCtrl', function($modalInstance, listSourceColumns, customColumnsToCreate, $q, alertService, locMsg, constants) {

  let vm = this;
  vm.fieldNamePattern = /^[a-zA-Z0-9_]*$/;
  vm.listSourceColumns = listSourceColumns;
  vm.customColumnsToCreate = customColumnsToCreate;
  vm.fieldTypes = constants.CUSTOM_FIELD_TYPE;

  vm.submit = function() {
    if (validateFieldName(vm.fieldName)) {
      $modalInstance.close({
        fieldName: vm.fieldName,
        fieldType: vm.fieldType,
      });
    }
  };

  const validateFieldName = function (fieldName) {
    let errorMessage;
    if (!vm.fieldNamePattern.test(fieldName)) {
      errorMessage = 'form.validation.illegalColumnName';
    } else if (checkIfSameNameExists(fieldName, listSourceColumns)
        || checkIfSameNameExists(fieldName, customColumnsToCreate)) {
      errorMessage = 'form.validation.duplicateColumnName';
    }
    if (errorMessage) {
      alertService.add('danger', locMsg[errorMessage]);
      return false;
    }
    return true;
  };

  const checkIfSameNameExists = function (columnName, existingColumns) {
    for (let i = 0; i < existingColumns.length; i++) {
      if (existingColumns[i].fieldName === columnName) {
        return true;
      }
    }
    return false;
  }

  vm.close = function() {
    $modalInstance.close();
  };

});
