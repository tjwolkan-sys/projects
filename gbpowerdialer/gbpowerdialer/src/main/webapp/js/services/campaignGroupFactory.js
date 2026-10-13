/**
 * Factory for CRUD operations on campaignGroup
 */
app.factory('campaignGroupFactory',  [ '$http', '$log', 'baseFactory', function($http, $log, baseFactory) {

  return angular.extend(Object.create(baseFactory.prototype),{

    _save : function(campaignGroup) {
      if (campaignGroup != null && campaignGroup.id != null){
        var promise = $http.put('campaignGroup/' + campaignGroup.id, campaignGroup).then(function(response) {
          return response;
        });
        return promise;
      }
    },
    
    _create : function(campaignGroup){
      var promise = $http.post('campaignGroup', campaignGroup).then(function(response) {
        return response;
      });
      return promise;
    },

    _getAll : function(page, sort, filters) {
      var promise = $http.get('campaignGroup/list', {params:{"page": page, "sort" : sort, "filters" : filters}}).then(function(response) {
        return response.data;
      });
      return promise;
    },
    
    _get : function(id) {
      var promise = $http.get('campaignGroup/' + id).then(function(response) {
        return response.data;
      });
      return promise;
    },
    
    _remove : function(id) {
      var promise = $http.delete('campaignGroup/' + id).then(function(response) {
        return response.data;
      });
      return promise;
    },
    
    _createNew : function(campaignGroup) {
    	campaignGroup.priority = 0;
    	return campaignGroup;
    }

  });

} ]);