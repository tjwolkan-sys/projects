/**
 * Controller for application information
 */
app.controller('aboutApplicationCtrl', [ '$scope', '$modalInstance', function($scope, $modalInstance) {

  var vm = this;

  vm.ok = function() {
    $modalInstance.close();
  };

} ]);