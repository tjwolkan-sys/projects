/**
 * Prototype for factories
 */
app.factory('baseFactory', [ '$http', 'alertService', 'locMsg', '$rootScope', '$log', function($http, alertService, locMsg, $rootScope, $log) {

  var service = function() {
  };

  service.prototype.save = function(item, disableAlert) {

    return this._save(item, disableAlert).then(function(response) {
      if (!disableAlert) {
        alertService.add('success', locMsg['label.save.success']);
      }
      return response;
    });

  };

  service.prototype.create = function(item) {

    return this._create(item).then(function(response) {
      alertService.add('success', locMsg['label.save.success']);
      return response;
    });

  };
  service.prototype.clone = function(item) {
    return this._clone(item).then(function(response) {
      alertService.add('success', locMsg['label.save.success']);
      return response;
    });

  };

  service.prototype.getAll = function(page, sort, filters) {

    return this._getAll(page, sort, filters);

  };

  service.prototype.get = function(id) {

    return this._get(id);

  };

  service.prototype.remove = function(id) {

    return this._remove(id).then(function(response) {
      alertService.add('success', locMsg['label.delete.success']);
      return response;
    });

  };

  service.prototype.setEnabled = function(ids, enabled, listName) {

    var promise = $http.put(listName + '/batch', {}, {
      params : {
        ids : ids.toString(),
        enabled : enabled
      }
    }).then(function(response) {
      alertService.add('success', locMsg['label.save.success']);
      return response.data;
    });
    return promise;

  };

  service.prototype.createNew = function() {
    object = {};
    object.enabled = true;
    object.deleted = false;
    return this._createNew(object);
  };

  service.prototype.hideTenantColumn = function(columns) {
    if (!$rootScope.hasAnyRole([ 'ROLE_SUPERADMIN', 'ROLE_TENANT_ADMIN' ])) {
      var tenantListDTOCol = _.findWhere(columns, {
        field : 'tenantListDTO.name'
      });
      var tenantCol = _.findWhere(columns, {
        field : 'tenant.name'
      });
      var col = _.without(columns, _.findWhere(columns, _.isUndefined(tenantListDTOCol) ? tenantCol : tenantListDTOCol));
      return this._hideTenantColumn(col);
    }
    return this._hideTenantColumn(columns);
  };

  return service;

} ]);
