/**
 * Factory for retrieval operations on location
 */
app.factory('locationFactory', [ '$http', '$log', function($http, $log) {

  return {

    getAll : function(page, sort, filters) {
      var promise = $http.get('location/list', {
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
      var promise = $http.get('location/' + id).then(function(response) {
        return response.data;
      });
      return promise;
    },

    getLocationsByCampaign : function(id) {
      var promise = $http.get('location/campaign/' + id).then(function(response) {
        return response.data;
      });
      return promise;
    },

    getLocationWithServersAndCampaigns : function(page, sort, filters) {
      var promise = $http.get('location/list/initializedProxies', {
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

  };

} ]);
