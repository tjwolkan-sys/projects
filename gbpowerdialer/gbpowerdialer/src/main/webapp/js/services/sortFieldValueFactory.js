/**
 * Factory for CRUD operations on sortFieldValue
 */
app.factory('sortFieldValueFactory',  [ '$http', '$log', 'baseFactory', function($http, $log, baseFactory) {

  return angular.extend(Object.create(baseFactory.prototype),{

    _getAll : function(page, sort, filters) {
      return $http.get('sortFieldValue/list', {params: {"page": page, "sort": sort, "filters": filters}}).then(function (response) {
        return response.data;
      });
    },

    _createNew : function(sortFieldValue) {
      sortFieldValue.priority = 0;
      return sortFieldValue;
    }

   });

} ]);