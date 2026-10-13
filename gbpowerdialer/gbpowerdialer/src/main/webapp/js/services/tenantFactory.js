/**
 * Factory for retrieval operations on tenant
 */
app.factory('tenantFactory', [ '$http', '$log', function($http, $log) {

  return {

    getAll : function(page, sort, filters) {
      var promise = $http.get('tenant/list', {
        params : {
          "page" : page,
          "sort" : sort,
          "filters" : filters
        }
      }).then(function(response) {
        return response.data;
      });
      return promise;
    },

    get : function(id) {
      var promise = $http.get('tenant/' + id).then(function(response) {
        return response.data;
      });
      return promise;
    }

  };

} ]);