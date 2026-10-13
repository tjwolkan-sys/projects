/**
 * Handler for alerts throughout the application
 */
app.controller('alertCtrl', [ '$scope', '$http', '$location', '$rootScope', 'localeFactory', '$log', '$translate', '$window', 'alertService',
    function($scope, $http, $location, $rootScope, localeFactory, $log, $translate, $window, alertService) {
      var vm = this;

      vm.alerts = alertService.alerts;
      vm.closeAlert = function(index) {
        alertService.closeAlert(index);
      };
      vm.permanentAlerts = alertService.permanentAlerts;
      vm.closePermanentAlert = function(index) {
        alertService.closePermanentAlert(index);
      };
      vm.add = function() {
        alertService.add('danger', 'asdfasdf');
      };

    } ]);
