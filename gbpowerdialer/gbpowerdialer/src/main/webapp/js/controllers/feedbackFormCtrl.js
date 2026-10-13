/**
 * Controller to create a feedback
 */
app.controller('feedbackFormCtrl', [ '$scope', '$modalInstance', 'helpMenuFactory', 'locMsg', function($scope, $modalInstance, helpMenuFactory, locMsg) {

  var vm = this;
  vm.item = {};

  vm.ok = function() {
    helpMenuFactory.create(vm.item).then(function(response) {
      $modalInstance.close();
    }, function(response) {
      $log.error(response);
    });
  };

  vm.cancel = function() {
    $modalInstance.close();
  };

  vm.types = [ {
    value : 0,
    label : locMsg['feedback.type.error']
  }, {
    value : 1,
    label : locMsg['feedback.type.suggestion']
  }, {
    value : 2,
    label : locMsg['feedback.type.comment']
  } ];

} ]);
