/**
 * Factory for retrieval operations on queue
 */
app.factory('queueFactory', [ '$http', '$log', function($http, $log) {

  return {
    getByCampaign: function (campaignId) {
      return $http.get('queue', {                     // örn. @RequestMapping("/queue")
        params: { campaignId: campaignId }
      }).then(function (res) { return res.data; });
    }
    ,

    getAll : function(page, sort, filters) {
      var promise = $http.get('queue/list', {
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
