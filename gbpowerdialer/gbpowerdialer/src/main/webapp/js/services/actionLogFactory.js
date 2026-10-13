/**
 * Factory for CRUD operations on actionLog
 */
app.factory('actionLogFactory', [ '$http', '$log', 'baseFactory', function($http, $log, baseFactory) {

  return angular.extend(Object.create(baseFactory.prototype),{

    getCache : function() {
      var promise = $http.get('actionLog/cache').then(function(response) {
        return response.data;
      });
      return promise;
    },
    
    _save : function(actionLog) {
      if (actionLog != null && actionLog.id != null){
        var promise = $http.put('actionLog/' + actionLog.id, actionLog).then(function(response) {
          return response;
        });
        return promise;
      }
    },
    
    _create : function(actionLog){
      var promise = $http.post('actionLog', actionLog).then(function(response) {
        return response;
      });
      return promise;
    },

    _getAll : function(page, sort, filters) {
      var promise = $http.get('actionLog/list', {params:{"page": page, "sort" : sort, "filters" : filters}}).then(function(response) {
        return response.data;
      });
      return promise;
    },
    
    _get : function(id) {
      var promise = $http.get('actionLog/' + id).then(function(response) {
        return response.data;
      });
      return promise;
    },
    
    _remove : function(id) {
      var promise = $http.delete('actionLog/' + id).then(function(response) {
        return response.data;
      });
      return promise;
    },
    
    _hideTenantColumn : function(columns) {
      return columns;
    }
    
  });
  
} ]);