/**
 * Controller to handle errors
 */
app.controller('errorCtrl', [ '$scope', '$routeParams', function($scope, $routeParams) {

  var vm = this;
  vm.status = $routeParams.status;

} ]);