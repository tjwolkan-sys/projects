/**
 * Factory for CRUD operations on adminLogDetail
 */
app.factory('attachedDataFactory',  [ '$http', '$log', 'baseFactory', function($http, $log, baseFactory) {

  return angular.extend(Object.create(baseFactory.prototype),{

    _save : function(attachedData) {
      if (attachedData != null && attachedData.id != null){
        var promise = $http.put('attachedData/' + attachedData.id, attachedData).then(function(response) {
          return response;
        });
        return promise;
      }
    },
    
    _create : function(attachedData){
      var promise = $http.post('attachedData', attachedData).then(function(response) {
        return response;
      });
      return promise;
    },

    _getAll : function(page, sort, filters) {
      var promise = $http.get('attachedData/list', {params:{"page": page, "sort" : sort, "filters" : filters}}).then(function(response) {
        return response.data;
      });
      return promise;
    },
    
    _get : function(id) {
      var promise = $http.get('attachedData/' + id).then(function(response) {
        return response.data;
      });
      return promise;
    },
    
    _remove : function(id) {
      var promise = $http.delete('attachedData/' + id).then(function(response) {
        return response.data;
      });
      return promise;
    },
    
    _createNew : function(attachedDataColumn) {
    	return attachedDataColumn;
    }

  });

} ]);