/**
 * Factory to get information about application and create feedback
 */
app.factory('helpMenuFactory', [ '$http', '$log', 'baseFactory', function($http, $log, baseFactory) {

  return angular.extend(Object.create(baseFactory.prototype), {

    getAboutAppData : function() {
      var promise = $http.get('aboutApplication', {
        cache : true
      }).then(function(response) {
        return response.data;
      });
      return promise;
    },

    _create : function(feedback) {
      var promise = $http.post('feedback', feedback).then(function(response) {
        return response;
      });
      return promise;
    }
  });
} ]);