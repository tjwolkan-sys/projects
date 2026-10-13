/**
 * Factory for CRUD operations on sortField
 */
app.factory('sortFieldFactory',  [ '$http', '$log', 'baseFactory', function($http, $log, baseFactory) {

  return angular.extend(Object.create(baseFactory.prototype),{

    _getAll : function(page, sort, filters) {
      return $http.get('sortField/list', {params: {"page": page, "sort": sort, "filters": filters}}).then(function (response) {
        return response.data;
      });
    },

    _createNew : function(sortField) {
      sortField.priority = 0;
      return sortField;
    }

   });

} ]);