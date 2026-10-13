/**
 * Controller to edit a lsc
 */
app.controller('lscFormCtrl', [ '$scope','$http', 'uiGridConstants', '$modalInstance', 'item', 'lscFactory', 'tenantFactory', 'i18nService', '$log', '$modal', 'locMsg', '$timeout',
  '$rootScope', function($scope,$http, uiGridConstants, $modalInstance, item, lscFactory, tenantFactory, i18nService, $log, $modal, locMsg, $timeout, $rootScope) {
    var vm = this;
    vm.item = item;
    vm.selectedItems = null;

    vm.tabs = [ {
      id : 'general',
      active : true
    }, {
      id : 'payload',
      active : false
    } ];
    vm.freePayloadEnabled = vm.item.freePayloadEnabled;
    if (vm.item != undefined && vm.item.payload != undefined) {
      if (vm.freePayloadEnabled) {
        vm.payload = vm.item.payload;
      } else {
        vm.payload = JSON.parse(vm.item.payload);
      }
    } else {
      vm.payload = [];
    }

    if (vm.item != undefined && vm.item.headers != undefined) {
      vm.headers = JSON.parse(vm.item.headers);
    } else {
      vm.headers = [];
    }

    if (vm.item != undefined && vm.item.authorization != undefined) {
      vm.authorization = vm.item.authorization;
    } else {
      vm.authorization = {
        url: '',
        method: 'GET',
        bodyType: 'JSON',
        body: '',
        headers: '',
        tokenHeaderName: ''
      };
    }

    if (vm.item.id === undefined) {
      lscFactory.createNew(vm.item);
    }

    vm.selected = {
      item : angular.copy(vm.item)
    };

    if ($rootScope.hasAnyRole([ 'ROLE_SUPERADMIN', 'ROLE_TENANT_ADMIN' ])) {
      tenantFactory.getAll({}, {
        'field' : 'name',
        'direction' : 'asc'
      }, {
        'deleted' : [ {
          term : 'false'
        } ],
        'enabled' : [ {
          term : 'true'
        } ]
      }).then(function(data) {
        vm.tenants = data.data;
      });
    } else {
      if (vm.item.id == null) {
        vm.item.tenant = $rootScope.currentUser.tenantId;
        vm.tenants = [ vm.item.tenant ];
      } else {
        vm.tenants = [ vm.item.tenant ];
      }
    }

    vm.save = function() {
      focusAtErrorTab(vm.tabs);

      vm.selected.item.payload = vm.freePayloadEnabled ? vm.payload : angular.toJson(vm.payload);
      vm.selected.item.headers = angular.toJson(vm.headers);
      vm.selected.item.authorization = vm.authorization;
      vm.selected.item.freePayloadEnabled = vm.freePayloadEnabled;
      // if item is not new
      if (vm.selected.item.id != null) {
        // service save
        lscFactory.save(vm.selected.item).then(function(response) {
          // send result
          $modalInstance.close(response.data);
        }, function(response) {
          // show error msg
          $log.error(response);
        });
        // if item is new
      } else {
        // service create
        lscFactory.create(vm.selected.item).then(function(response) {
          // send result
          $modalInstance.close(response.data);
        }, function(response) {
          // show error msg
          $log.error(response);
        });
      }

    };

    vm.cancel = function() {
      $modalInstance.dismiss('cancel');
    };

    vm.replyTypes = [ {
      value : 0,
      label : locMsg['lsc.replyType.0']
    }, {
      value : 1,
      label : locMsg['lsc.replyType.1']
    }, {
      value : 2,
      label : locMsg['lsc.replyType.2']
    } ];

    vm.httpMethods = [ {
      value : 'GET',
      label : 'GET'
    }, {
      value : 'POST',
      label : 'POST'
    } ];

    vm.lscPayloadGridOptions = {
      enableRowSelection : true,
      enableSelectAll : true,
      enableFullRowSelection : true,
      modifierKeysToMultiSelect : true,
      columnDefs : [ {
        displayName : locMsg['lsc.payload.key'],
        field : 'key',
        enableHiding : false,
      }, {
        displayName : locMsg['lsc.payload.value'],
        field : 'value',
        enableHiding : false,
      } ]
    };
    vm.lscPayloadGridOptions.data = vm.freePayloadEnabled ? [] : vm.payload;

    vm.lscPayloadGridOptions.onRegisterApi = function(gridApi) {
      // set gridApi on scope
      vm.gridApi = gridApi;
      gridApi.selection.on.rowSelectionChanged($scope, function(row) {
        vm.selectedItems = vm.gridApi.selection.getSelectedRows();
      });

    };

    vm.remove = function() {
      vm.gridApi.selection.getSelectedRows().forEach(function(item, i, arr) {
        vm.payload.splice(vm.payload.indexOf(item), 1);
        vm.selectedItems = {};
      });
    };

    vm.lscHeadersGridOptions = {
      enableRowSelection : true,
      enableSelectAll : true,
      enableFullRowSelection : true,
      modifierKeysToMultiSelect : true,
      columnDefs : [ {
        displayName : locMsg['lsc.headers.key'],
        field : 'key',
        enableHiding : false,
      }, {
        displayName : locMsg['lsc.headers.value'],
        field : 'value',
        enableHiding : false,
      } ]
    };
    vm.lscHeadersGridOptions.data = vm.headers;

    vm.lscHeadersGridOptions.onRegisterApi = function(gridApi) {
      // set gridApi on scope
      vm.headersGridApi = gridApi;
      gridApi.selection.on.rowSelectionChanged($scope, function(row) {
        vm.selectedItems = vm.headersGridApi.selection.getSelectedRows();
      });

    };

    vm.removeHeader = function () {
      vm.headersGridApi.selection.getSelectedRows().forEach(function(item, i, arr) {
        vm.headers.splice(vm.headers.indexOf(item), 1);
        vm.selectedItems = {};
      });
    };

    vm.open = function(size, item) {

      vm.item = item;

      vm.isNew = true;
      if (item.key != null && item.value != null) {
        vm.isNew = false;
      }

      var modalInstance = $modal.open({
        animation : true,
        backdrop : 'static',
        // modal template page
        templateUrl : 'pages/lscField.html',
        // modal page controller
        controller : 'lscFieldCtrl as lscField',
        size : size,
        resolve : {
          item : function() {
            return vm.item;
          },
          header : function() {
            return 'lsc.payload.form';
          }
        }
      });

      modalInstance.result.then(function(result) {
        if (vm.isNew) {
          vm.lscPayloadGridOptions.data.push(result);
        } else {
          angular.merge(vm.item, result);
        }
      }, function() {
        $log.info('Modal dismissed at: ' + new Date());
      });
    };

    vm.openHeadersDialog = function(size, item) {

      vm.item = item;

      vm.isNew = true;
      if (item.key != null && item.value != null) {
        vm.isNew = false;
      }

      var modalInstance = $modal.open({
        animation : true,
        backdrop : 'static',
        // modal template page
        templateUrl : 'pages/lscField.html',
        // modal page controller
        controller : 'lscFieldCtrl as lscField',
        size : size,
        resolve : {
          item : function() {
            return vm.item;
          },
          header : function() {
            return 'lsc.header.form';
          }
        }
      });

      modalInstance.result.then(function(result) {
        if (vm.isNew) {
          vm.lscHeadersGridOptions.data.push(result);
        } else {
          angular.merge(vm.item, result);
        }
      }, function() {
        $log.info('Modal dismissed at: ' + new Date());
      });
    };

    vm.clearPayload = function () {
      if (vm.freePayloadEnabled) {
        vm.payload = '';
      } else {
        vm.payload = [];
        vm.lscPayloadGridOptions.data = vm.payload;
        vm.selectedItems = null;
      }
    }

    vm.resetFreePayloadFlagToPreviousValue = function () {
      vm.freePayloadEnabled = !vm.freePayloadEnabled;
    }

    vm.restResponse = '';

// REST client isteğini gönderecek metot
    vm.callRestClient = function() {
      console.log("callRestClient invoked with restRequest=", vm.authorization);

      var reqPayload = {
        url: vm.authorization.url,
        method: vm.authorization.method,
        bodyType: vm.authorization.bodyType,
        body: vm.authorization.body,
        headers: vm.authorization.headers,
        responseKey: vm.authorization.responseKey,
        responseType: vm.authorization.responseType
      };
      console.log("Sending reqPayload:", reqPayload);
      $http.post('/gbpowerdialer/lscAuth/send', reqPayload).then(
          function success(resp) {
            console.log("REST client success", resp);
            vm.restResponse = 'Status: ' + resp.status + '\n\n' + JSON.stringify(resp.data);
          },
          function error(err) {
            console.log("REST client error", err);
            vm.restResponse = 'Error: ' + err.status + '\n' + (err.data || '');
          }
      );
    };

    vm.parseAuthBodyToGridData = function(bodyStr) {
      if (!bodyStr) return [];

      return bodyStr.split('&').map(pair => {
        const [key, value] = pair.split('=');
        return {
          key: decodeURIComponent(key),
          value: decodeURIComponent(value || '')
        };
      });
    };

    vm.lscAuthGridOptions = {
      enableRowSelection : true,
      enableSelectAll : true,
      enableFullRowSelection : true,
      modifierKeysToMultiSelect : true,
      columnDefs : [ {
        displayName : locMsg['lsc.auth.key'],
        field : 'key',
        enableHiding : false,
      }, {
        displayName : locMsg['lsc.auth.value'],
        field : 'value',
        enableHiding : false,
      } ]
    };
    vm.lscAuthGridOptions.data = vm.parseAuthBodyToGridData(vm.authorization.body);

    vm.lscAuthGridOptions.onRegisterApi = function(gridApi) {
      // set gridApi on scope
      vm.authGridApi = gridApi;
      gridApi.selection.on.rowSelectionChanged($scope, function(row) {
        vm.selectedItems = vm.authGridApi.selection.getSelectedRows();
      });

    };

    vm.openAuthFormDialog = function(size, item) {
      vm.item = item;
      vm.isNew = true;
      if (item.key != null && item.value != null) {
        vm.isNew = false;
      }

      var modalInstance = $modal.open({
        animation : true,
        backdrop : 'static',
        // modal template page
        templateUrl : 'pages/lscAuthField.html',
        // modal page controller
        controller : 'lscFieldCtrl as lscAuthField',
        size : size,
        resolve : {
          item : function() {
            return vm.item;
          },
          header : function() {
            return 'lsc.auth.form';
          }
        }
      });

      modalInstance.result.then(function(result) {
        if (vm.isNew) {
          vm.lscAuthGridOptions.data.push(result);
        } else {
          angular.merge(vm.item, result);
        }

        var bodyStr = vm.lscAuthGridOptions.data
            .filter(item => item.key) // skip empty
            .map(item => `${encodeURIComponent(item.key)}=${item.value}`)
            .join('&');

        vm.authorization.body = bodyStr;
      }, function() {
        $log.info('Modal dismissed at: ' + new Date());
      });
    };

    vm.removeAuth = function () {
      vm.authGridApi.selection.getSelectedRows().forEach(function(item, i, arr) {
        vm.lscAuthGridOptions.data.splice(vm.lscAuthGridOptions.data.indexOf(item), 1);
        vm.selectedItems = {};

        var bodyStr = vm.lscAuthGridOptions.data
            .filter(item => item.key)
            .map(item => `${encodeURIComponent(item.key)}=${item.value}`)
            .join('&');

        vm.authorization.body = bodyStr;
      });
    };

    vm.onBodyTypeChange = function () {
      if (vm.authorization.bodyType === 'FORM_URLENCODED') {
        var bodyStr = vm.lscAuthGridOptions.data
            .filter(item => item.key)
            .map(item => `${encodeURIComponent(item.key)}=${item.value}`)
            .join('&');

        vm.authorization.body = bodyStr;
      }
    };

    vm.lscAuthHeadersGridOptions = {
      enableRowSelection : true,
      enableSelectAll : true,
      enableFullRowSelection : true,
      modifierKeysToMultiSelect : true,
      columnDefs : [ {
        displayName : locMsg['lsc.auth.key'],
        field : 'key',
        enableHiding : false,
      }, {
        displayName : locMsg['lsc.auth.value'],
        field : 'value',
        enableHiding : false,
      } ]
    };
    vm.lscAuthHeadersGridOptions.data = vm.parseAuthBodyToGridData(vm.authorization.headers);

    vm.lscAuthHeadersGridOptions.onRegisterApi = function(gridApi) {
      // set gridApi on scope
      vm.authHeadersGridApi = gridApi;
      gridApi.selection.on.rowSelectionChanged($scope, function(row) {
        vm.selectedItems = vm.authHeadersGridApi.selection.getSelectedRows();
      });

    };

    vm.openAuthHeadersFormDialog = function(size, item) {
      vm.item = item;
      vm.isNew = true;
      if (item.key != null && item.value != null) {
        vm.isNew = false;
      }

      var modalInstance = $modal.open({
        animation : true,
        backdrop : 'static',
        // modal template page
        templateUrl : 'pages/lscField.html',
        // modal page controller
        controller : 'lscFieldCtrl as lscField',
        size : size,
        resolve : {
          item : function() {
            return vm.item;
          },
          header : function() {
            return 'lsc.auth.form';
          }
        }
      });

      modalInstance.result.then(function(result) {
        if (vm.isNew) {
          vm.lscAuthHeadersGridOptions.data.push(result);
        } else {
          angular.merge(vm.item, result);
        }

        var headers = vm.lscAuthHeadersGridOptions.data
            .filter(item => item.key) // skip empty
            .map(item => `${encodeURIComponent(item.key)}=${encodeURIComponent(item.value)}`)
            .join('&');

        vm.authorization.headers = headers;
      }, function() {
        $log.info('Modal dismissed at: ' + new Date());
      });
    };

    vm.removeAuthHeader = function () {
      vm.authHeadersGridApi.selection.getSelectedRows().forEach(function(item, i, arr) {
        vm.lscAuthHeadersGridOptions.data.splice(vm.lscAuthHeadersGridOptions.data.indexOf(item), 1);
        vm.selectedItems = {};

        var headers = vm.lscAuthHeadersGridOptions.data
            .filter(item => item.key)
            .map(item => `${encodeURIComponent(item.key)}=${encodeURIComponent(item.value)}`)
            .join('&');

        vm.authorization.headers = headers;
      });
    };

  } ]);