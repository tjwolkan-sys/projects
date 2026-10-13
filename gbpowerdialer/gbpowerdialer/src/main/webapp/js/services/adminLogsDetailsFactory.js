/**
 * Factory for CRUD operations on adminLogDetail
 */
app.factory('adminLogsDetailsFactory', [ '$http', '$log', 'baseFactory', function($http, $log, baseFactory) {

  return angular.extend(Object.create(baseFactory.prototype), {

    _getAll : function(page, sort, filters) {
      var promise = $http.get('adminLogDetails/list', {
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

  });

} ]);