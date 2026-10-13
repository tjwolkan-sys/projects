/**
 * Factory for CRUD operations on phoneNumberColumn
 */
app.factory('phoneNumberColumnFactory',  [ '$http', '$log', 'baseFactory', function($http, $log, baseFactory) {

  return angular.extend(Object.create(baseFactory.prototype),{

    _save : function(phoneNumberColumn) {
      if (phoneNumberColumn != null && phoneNumberColumn.id != null){
        var promise = $http.put('phoneNumberColumn/' + phoneNumberColumn.id, phoneNumberColumn).then(function(response) {
          return response;
        });
        return promise;
      }
    },
    
    _create : function(phoneNumberColumn){
      var promise = $http.post('phoneNumberColumn', phoneNumberColumn).then(function(response) {
        return response;
      });
      return promise;
    },

    _getAll : function(page, sort, filters) {
      var promise = $http.get('phoneNumberColumn/list', {params:{"page": page, "sort" : sort, "filters" : filters}}).then(function(response) {
        return response.data;
      });
      return promise;
    },
    
    _get : function(id) {
      var promise = $http.get('phoneNumberColumn/' + id).then(function(response) {
        return response.data;
      });
      return promise;
    },
    
    _remove : function(id) {
      var promise = $http.delete('phoneNumberColumn/' + id).then(function(response) {
        return response.data;
      });
      return promise;
    },
    
    _createNew : function(phoneNumberColumn) {
      phoneNumberColumn.colSort = 0;
    	return phoneNumberColumn;
    }

  });

} ]);