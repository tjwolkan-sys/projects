/**
 * Factory for retrieval operations on telephony result code
 */
app.factory('telephonyRcFactory', [ '$http', '$log', function($http, $log) {

  return {

    getAll : function(page, sort, filters) {
      var promise = $http.get('telephonyRc/list', {
        params : {
          "page" : page,
          "sort" : sort,
          "filters" : filters
        }
      }).then(function(response) {
        return response.data;
      });
      return promise;
    }

  };

} ]);