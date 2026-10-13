/**
 * Factory for CRUD operations on resultCodeSchedule
 */
app.factory('resultCodeScheduleAdvancedFactory',  [ '$http', '$log', 'baseFactory', function($http, $log, baseFactory) {

  return angular.extend(Object.create(baseFactory.prototype),{

    _save : function(resultCodeScheduleAdvanced) {
      if (resultCodeScheduleAdvanced != null && resultCodeScheduleAdvanced.id != null){
        var promise = $http.put('resultCodeScheduleAdvanced/' + resultCodeScheduleAdvanced.id, resultCodeScheduleAdvanced).then(function(response) {
          return response;
        });
        return promise;
      }
    },
    
    _create : function(resultCodeScheduleAdvanced){
      var promise = $http.post('resultCodeScheduleAdvanced', resultCodeScheduleAdvanced).then(function(response) {
        return response;
      });
      return promise;
    },

    _getAll : function(page, sort, filters) {
      var promise = $http.get('resultCodeScheduleAdvanced/list', {params:{"page": page, "sort" : sort, "filters" : filters}}).then(function(response) {
        return response.data;
      });
      return promise;
    },
    
    _get : function(id) {
      var promise = $http.get('resultCodeScheduleAdvanced/' + id).then(function(response) {
        return response.data;
      });
      return promise;
    },
    
    _remove : function(id) {
      var promise = $http.delete('resultCodeScheduleAdvanced/' + id).then(function(response) {
        return response.data;
      });
      return promise;
    },
    
    _createNew : function(resultCodeScheduleAdvanced) {
        delete resultCodeScheduleAdvanced.id;
        resultCodeScheduleAdvanced.dialAttempt = 0;
    	resultCodeScheduleAdvanced.retryInterval = 0;
    	return resultCodeScheduleAdvanced;
    }

  });

} ]);