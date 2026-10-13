/**
 * Factory for retrieval operations on filter
 */
app.factory('ivrApplicationFactory', [ '$http', '$log', function($http, $log) {

  return {

    getAll : function(page, sort, filters) {
      var promise = $http.get('ivrApplication/list', {
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
      var promise = $http.get('ivrApplication/' + id).then(function(response) {
        return response.data;
      });
      return promise;
    }

  };

} ]);