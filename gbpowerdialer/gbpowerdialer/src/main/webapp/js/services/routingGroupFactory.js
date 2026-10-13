/**
 * Factory for retrieval operations on routing group (callerId pool)
 */
app.factory('routingGroupFactory', [ '$http', '$log', function($http) {

  return {

    getAll : function(page, sort, filters) {
      var promise = $http.get('routingGroup/list', {
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

  }
} ]);
