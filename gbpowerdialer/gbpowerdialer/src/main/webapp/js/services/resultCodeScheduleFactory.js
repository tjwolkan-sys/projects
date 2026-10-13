/**
 * Factory for CRUD operations on resultCodeSchedule
 */
app.factory('resultCodeScheduleFactory',  [ '$http', '$log', 'baseFactory', function($http, $log, baseFactory) {

  return angular.extend(Object.create(baseFactory.prototype),{

    _save : function(resultCodeSchedule) {
      if (resultCodeSchedule != null && resultCodeSchedule.id != null){
        var promise = $http.put('resultCodeSchedule/' + resultCodeSchedule.id, resultCodeSchedule).then(function(response) {
          return response;
        });
        return promise;
      }
    },
    
    _create : function(resultCodeSchedule){
      var promise = $http.post('resultCodeSchedule', resultCodeSchedule).then(function(response) {
        return response;
      });
      return promise;
    },

    _getAll : function(page, sort, filters) {
      var promise = $http.get('resultCodeSchedule/list', {params:{"page": page, "sort" : sort, "filters" : filters}}).then(function(response) {
        return response.data;
      });
      return promise;
    },
    
    _get : function(id) {
      var promise = $http.get('resultCodeSchedule/' + id).then(function(response) {
        return response.data;
      });
      return promise;
    },
    
    _remove : function(id) {
      var promise = $http.delete('resultCodeSchedule/' + id).then(function(response) {
        return response.data;
      });
      return promise;
    },
    
    _createNew : function(resultCodeSchedule) {
    	resultCodeSchedule.retryAmount = 0;
    	resultCodeSchedule.retryInterval = 0;
    	resultCodeSchedule.minScheduleTime = new Date();
    	resultCodeSchedule.maxScheduleTime = new Date();
    	resultCodeSchedule.advancedIntervalEnabled = false;
    	resultCodeSchedule.macroCycle = false;
    	
    	return resultCodeSchedule;
    }

  });

} ]);