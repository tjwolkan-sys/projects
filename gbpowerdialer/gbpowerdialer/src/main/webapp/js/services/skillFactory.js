/**
 * Factory for retrieval operations on skill
 */
app.factory('skillFactory', [ '$http', '$log', function($http, $log) {

  return {

    getAll : function(page, sort, filters) {
      var promise = $http.get('skill/list', {
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
      var promise = $http.get('skill/' + id).then(function(response) {
        return response.data;
      });
      return promise;
    },

    getSkillsByCampaign : function(id) {
      var promise = $http.get('skill/campaign/' + id).then(function(response) {
        return response.data;
      });
      return promise;
    }

  };

} ]);