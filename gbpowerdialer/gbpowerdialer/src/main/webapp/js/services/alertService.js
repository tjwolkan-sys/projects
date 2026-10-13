/**
 * Factory for alert service
 */
app.factory('alertService', [ '$log', '$rootScope', function($log, $rootScope) {
  var alertService = {};
  alertService.alerts = [];
  alertService.permanentAlerts = [];

  alertService.add = function(type, msg) {
    alertService.alerts.push({
      'type' : type,
      'msg' : msg
    });
  };

  alertService.addPermanent = function(type, msg) {
    alertService.permanentAlerts.push({
      'type' : type,
      'msg' : msg
    });
  };

  alertService.closeAlert = function(index) {
    alertService.alerts.splice(index, 1);
  };

  alertService.closePermanentAlert = function(index) {
    alertService.permanentAlerts.splice(index, 1);
  };
  
  return alertService;

} ]);
