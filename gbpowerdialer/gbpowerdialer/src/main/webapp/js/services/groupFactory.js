/**
 * Factory for retrieval operations on group
 */
app.factory('groupFactory', [ '$http', '$log', function($http, $log) {

  return {

    getByCampaign: function (campaignId) {
      return $http.get('campaignGroup', {
        params: { campaignId: campaignId }
      }).then(function (res) { return res.data; });
    },

    getAll : function(page, sort, filters) {
      var promise = $http.get('group/list', {
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
      var promise = $http.get('group/' + id).then(function(response) {
        return response.data;
      });
      return promise;
    },

  };

} ]);