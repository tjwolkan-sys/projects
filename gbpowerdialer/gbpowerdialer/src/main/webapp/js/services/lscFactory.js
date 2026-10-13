/**
 * Factory for CRUD operations on lsc
 */
app.factory('lscFactory', [ '$http', '$log', 'baseFactory', function($http, $log, baseFactory) {

  return angular.extend(Object.create(baseFactory.prototype),{

    _save : function(lsc) {
      if (lsc != null && lsc.id != null){
        var promise = $http.put('lsc/' + lsc.id, lsc).then(function(response) {
          return response;
        });
        return promise;
      }
    },
    
    _create : function(lsc){
      var promise = $http.post('lsc', lsc).then(function(response) {
        return response;
      });
      return promise;
    },

    _getAll : function(page, sort, filters) {
      var promise = $http.get('lsc/list', {params:{"page": page, "sort" : sort, "filters" : filters}}).then(function(response) {
        return response.data;
      });
      return promise;
    },
    
    _remove : function(ids) {
    	var promise = $http.delete('lsc/batch', {
    		params: {ids: ids.toString()}
    	}).then(function(response) {
        return response.data;
      });
      return promise;
    },
    
    _createNew : function(lsc) {
    	return lsc;
    },
    
    _hideTenantColumn : function(columns) {
      return columns;
    }

  });

} ]);