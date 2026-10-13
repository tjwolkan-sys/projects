/**
 * Factory for CRUD operations on dnc
 */
app.factory('dncFactory', [ '$http', '$log', 'baseFactory', function($http, $log, baseFactory) {

  return angular.extend(Object.create(baseFactory.prototype),{

    _save : function(dnc, disableAlert) {
      var promise = $http.put('dnc/' + dnc.id, dnc).then(function(response) {
          return response;
        });
      return promise;
    },

    update : function(dnc, itemForBatchProcessing) {
      return $http.put('dncpn/dncPnBatchProcessing/' + dnc.id, itemForBatchProcessing);
    },

    dncPnFileImportBatchDeleteSendForm : function(dncId, formData) {
      return $http.post('dncpn/dncPnFileImportBatchDelete/' + dncId, formData, {
        transformRequest : angular.identity, headers : {'Content-Type' : undefined,}
      });
    },

    dncPnFileImportBatchAddSendForm : function(dncId, formData) {
      return $http.post('dncpn/dncPnFileImportBatchAdd/' + dncId, formData, {
        transformRequest : angular.identity, headers : {'Content-Type' : undefined,}
      });
    },

    _create : function(dnc){
      var promise = $http.post('dnc', dnc).then(function(response) {
        return response;
      });
      return promise;
    },

    _getAll : function(page, sort, filters) {
      var promise = $http.get('dnc/list', {params:{"page": page, "sort" : sort, "filters" : filters}}).then(function(response) {
        return response.data;
      });
      return promise;
    },
    
    _get : function(id) {
        var promise = $http.get('dnc/' + id).then(function(response) {
          return response.data;
        });
        return promise;
      },
      
    _remove : function(ids) {
    	var promise = $http.delete('dnc/batch', {
    		params: {ids: ids.toString()}
    	}).then(function(response) {
        return response.data;
      });
      return promise;
    },
    
    _createNew : function(dnc) {
    	return dnc;
    },
    
    _hideTenantColumn : function(columns) {
      return columns;
    }

  });

} ]);