/**
 * Factory for CRUD operations on campaignScheduleAutomation
 */
app.factory('campaignScheduleAutomationFactory', [ '$http', '$log', 'baseFactory', function($http, $log, baseFactory) {

  return angular.extend(Object.create(baseFactory.prototype),{

    _save : function(scheduleAutomation) {
      if (scheduleAutomation != null && scheduleAutomation.id != null){
        var promise = $http.put('scheduleAutomation/' + scheduleAutomation.id, scheduleAutomation).then(function(response) {
          return response;
        });
        return promise;
      }
    },

    _create : function(scheduleAutomation){
      var promise = $http.post('scheduleAutomation', scheduleAutomation).then(function(response) {
        return response;
      });
      return promise;
    },

    _getAll : function(page, sort, filters) {
      var promise = $http.get('scheduleAutomation/list', {params:{"page": page, "sort" : sort, "filters" : filters}}).then(function(response) {
        return response.data;
      });
      return promise;
    },

    _remove : function(id) {
      var promise = $http.delete('scheduleAutomation/' + id).then(function(response) {
        return response.data;
      });
      return promise;
    },

    _createNew : function(campaignScheduleAutomation) {
      campaignScheduleAutomation.deleted = false;
      campaignScheduleAutomation.enabled = false;
      campaignScheduleAutomation.threshold = 0;
      campaignScheduleAutomation.groups = [];
      campaignScheduleAutomation.comparisonType = 1;       // Ordinal mapping: 0=AVERAGE_VALUE, 1=ONE_BY_ONE
      return campaignScheduleAutomation;
    },

  });

}
]);
