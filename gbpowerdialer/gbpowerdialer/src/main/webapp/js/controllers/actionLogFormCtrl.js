/**
 * Controller to show details of an actionLog
 */
app.controller('actionLogFormCtrl', [
    '$modalInstance',
    'item',
    function($modalInstance, item) {
      var vm = this;
      vm.item = item;

      vm.close = function() {
        $modalInstance.dismiss('cancel');
      };

    } ]);