/**
 * Factory for retrieval operations on resultCode
 */
app.factory('resultCodeFactory', [ '$http', '$log', function($http, $log) {

  return {

    getAll : function(page, sort, filters) {
      var promise = $http.get('resultCode/list', {
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
      var promise = $http.get('resultCode/' + id).then(function(response) {
        return response.data;
      });
      return promise;
    },

    getResultCodesByCampaignId : function(id, nonFinalOnly) {
      var promise = $http.get('resultCode/list/campaign/' + id, {
        params: {
          "nonFinalOnly" : nonFinalOnly
        }
      }).then(function(response) {
        return response.data;
      });
      return promise;
    },

    getResultCodesByGroupIds : function(ids, nonFinalOnly) {
      var promise = $http.get('resultCode/list/group', {
        params : {
          "ids" : ids.toString(),
          "nonFinalOnly" : nonFinalOnly
        }
      }).then(function(response) {
        return response.data;
      });
      return promise;
    }

  };

} ]);