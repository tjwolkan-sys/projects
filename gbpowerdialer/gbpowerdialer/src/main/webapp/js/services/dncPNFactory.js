/**
 * Factory for CRUD operations on dncPhoneNumber
 */
app.factory('dncPNFactory', [ '$http', '$log', 'baseFactory', function($http, $log, baseFactory) {

  return angular.extend(Object.create(baseFactory.prototype),{

    _save : function(pn) {
      if (pn != null && pn.id != null){
        var promise = $http.put('dncpn/' + pn.id, pn).then(function(response) {
          return response;
        });
        return promise;
      }
    },
    
    _create : function(pn){
      var promise = $http.post('dncpn', pn).then(function(response) {
        return response;
      });
      return promise;
    },

    _getAll : function(page, sort, filters) {
      var promise = $http.get('dncpn/list', {params:{"page": page, "sort" : sort, "filters" : filters}}).then(function(response) {
        return response.data;
      });
      return promise;
    },
    
    _remove : function(id) {
      var promise = $http.delete('dncpn/' + id).then(function(response) {
        return response.data;
      });
      return promise;
    }

  });

} ]);