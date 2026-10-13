/**
 * Factory for retrieval operations on number
 */
app.factory('numberFactory', [ '$http', '$log', function($http, $log) {

  return {

    getAll : function(page, sort, filters) {
      var promise = $http.get('number/list', {
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
      var promise = $http.get('number/' + id).then(function(response) {
        return response.data;
      });
      return promise;
    }

  };

} ]);