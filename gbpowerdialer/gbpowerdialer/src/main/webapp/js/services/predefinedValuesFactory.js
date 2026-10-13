/**
 * Factory for CRUD operations on adminLogDetail
 */
app.factory('predefinedValuesFactory',  [ '$http', '$log', function($http) {

  return {

    createNew: function(type, enabled) {
      var predefinedValue = {};
      predefinedValue.enabled = enabled;
      predefinedValue.type = type;
      predefinedValue.isNew = true;
      predefinedValue.deleted = false;
      return predefinedValue;
    },

    loadValuesByCampaignId: function(campaignId) {
      return $http.get('predefinedValues/list/' + campaignId, {}).then(function(response) {
        return response.data;
      });
    }

  }
} ]);