/**
 * Factory for retrieval operations on server
 */
app.factory('serverFactory', [ '$http', '$log', function($http, $log) {

  return {

    getAll : function(page, sort, filters) {
      var promise = $http.get('server/list', {
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

    connect : function(id, page, sort, filters) {
      var promise = $http.post('server/connect', {
        params : {
          "id" : id,
          "page" : page,
          "sort" : sort,
          "filters" : filters
        }
      }).then(function(response) {
        return response.data;
      });
      return promise;
    },

    disconnect : function(ip, page, sort, filters) {
      var promise = $http.post('server/disconnect', {
        params : {
          "ip" : ip,
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
