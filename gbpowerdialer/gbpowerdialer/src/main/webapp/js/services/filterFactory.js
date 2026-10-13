/**
 * Factory for CRUD operations on filter
 */
app.factory('filterFactory',  [ '$http', '$log', 'baseFactory', function($http, $log, baseFactory) {

  return angular.extend(Object.create(baseFactory.prototype),{

    _save : function(filter) {
      if (filter != null && filter.id != null){
        var promise = $http.put('filter/' + filter.id, filter).then(function(response) {
          return response;
        });
        return promise;
      }
    },
    
    _create : function(filter){
      var promise = $http.post('filter', filter).then(function(response) {
        return response;
      });
      return promise;
    },

    _getAll : function(page, sort, filters) {
      var promise = $http.get('filter/list', {params:{"page": page, "sort" : sort, "filters" : filters}}).then(function(response) {
        return response.data;
      });
      return promise;
    },
    
    _get : function(id) {
      var promise = $http.get('filter/' + id).then(function(response) {
        return response.data;
      });
      return promise;
    },
    
    _remove : function(id) {
      var promise = $http.delete('filter/' + id).then(function(response) {
        return response.data;
      });
      return promise;
    },
    
    _createNew : function(filterColumn) {
    	return filterColumn;
    }

  });

} ]);