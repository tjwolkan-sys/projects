/**
 * Controller to edit a dnc
 */
app.controller('dncFormCtrl', ['$q', 'blockUI', '$scope', '$rootScope', '$modalInstance', 'item', 'dncFactory', 'tenantFactory', 'i18nService', '$log', 'locMsg', '$timeout', 'adminLogsFactory',
                               'alertService', function($q, blockUI, $scope, $rootScope, $modalInstance, item, dncFactory, tenantFactory, i18nService, $log, locMsg, $timeout, adminLogsFactory, alertService) {
      var vm = this;
      vm.item = angular.copy(item);
      vm.itemInitial = angular.copy(item);

    $scope.$on('batchProcessingForSelectedItemsEvent', function(event, data) {
      blockUI.start();
      vm.save(true).then(function() {
        vm.itemForBatchProcessing = {};
        vm.itemForBatchProcessing.type = data.type;
        vm.itemForBatchProcessing.filters = data.filters;
        vm.batchUpdate(vm.item, vm.itemForBatchProcessing).then(function() {
          blockUI.stop();
        }, function(response) {
          $log.error(response);
        });
      }, function() {
        blockUI.stop();
      });
    });

      if (vm.item.id === undefined) {
        dncFactory.createNew(vm.item);
      }

      vm.changedRows = {
        added : [],
        edited : [],
        deleted : []
      };

    vm.save = function(disableAlert) {
      var promise;
        if (vm.item.expireDate == undefined || new Date() < vm.item.expireDate) {
          vm.savePhones();
          if (vm.item.id != null) {
            promise = dncFactory.save(vm.item, disableAlert).then(function(response) {
              vm.item = response.data;
              adminLogsFactory.check(vm.itemInitial, response.data, 2, vm.changedRows.deleted, vm.changedRows.added, vm.changedRows.edited);
              if (disableAlert !== true) {
                $modalInstance.close(response.data);
              }
            }, function(response) {
              $log.error(response);
            });
          } else {
            promise = dncFactory.create(vm.item).then(function(response) {
              vm.item = response.data;
              adminLogsFactory.check(vm.itemInitial, response.data, 2);
              $modalInstance.close(response.data);
            }, function(response) {
              $log.error(response);
            });
          }
        } else {
          var defer = $q.defer();
          promise = defer.promise;
          defer.reject();
          alertService.add('danger', locMsg['alert.dnc.expireDate.error']);
        }
      return promise;
    };

    vm.batchUpdate = function(dnc, itemForBatchProcessing) {
      var promise = dncFactory.update(dnc, itemForBatchProcessing).then(function(response) {
        vm.item = response.data;
        adminLogsFactory.check(vm.itemInitial, response.data, 2, vm.changedRows.deleted, vm.changedRows.added, vm.changedRows.edited);
        $modalInstance.close(response.data);
        alertService.add('success', locMsg['label.save.success']);
      }, function(response) {
        $log.error(response);
      });
      return promise;
    };

    $scope.$on('dncPnFileImportBatchDeleteSubmitFormEvent', function(event, data) {
      blockUI.start();
      vm.save(true).then(function() {
        vm.dncPnFileImportBatchDeletePrepareSendForm(data.dncId, data.result).then(function() {
          blockUI.stop();
        }, function(response) {
          $log.error(response);
        });
      }, function() {
        blockUI.stop();
      });
    });

    vm.dncPnFileImportBatchDeletePrepareSendForm = function(dncId, resultArr) {
      var formData = new FormData();
      formData.append("file", resultArr[0]);
      formData.append("params", resultArr[1]);

      var promise = dncFactory.dncPnFileImportBatchDeleteSendForm(dncId, formData).then(function(response) {
        if (response.status === 204) {//http no content, empty file
          vm.item = response.data;
          adminLogsFactory.check(vm.itemInitial, response.data, 2, vm.changedRows.deleted, vm.changedRows.added, vm.changedRows.edited);
          $modalInstance.close(response.data);
          alertService.add('danger', locMsg['dnc.pn.file.empty.alert']);
        } else {
          vm.item = response.data;
          adminLogsFactory.check(vm.itemInitial, response.data, 2, vm.changedRows.deleted, vm.changedRows.added, vm.changedRows.edited);
          $modalInstance.close(response.data);
          alertService.add('success', locMsg['label.save.success']);
        }
      }, function(response) {
        $modalInstance.close(response.data);
        $log.error(response);
      });
      return promise;
    };

    $scope.$on('dncPnFileImportBatchAddSubmitFormEvent', function(event, data) {
      blockUI.start();
      vm.save(true).then(function() {
        vm.dncPnFileImportBatchAddPrepareSendForm(data.dncId, data.result).then(function() {
          blockUI.stop();
        }, function(response) {
          $log.error(response);
        });
      }, function() {
        blockUI.stop();
      });
    });

    vm.dncPnFileImportBatchAddPrepareSendForm = function(dncId, resultArr) {
      var formData = new FormData();
      formData.append("file", resultArr[0]);
      formData.append("params", resultArr[1]);

      var promise = dncFactory.dncPnFileImportBatchAddSendForm(dncId, formData).then(function(response) {
        if (response.status === 204) {//http no content, empty file
          vm.item = response.data;
          adminLogsFactory.check(vm.itemInitial, response.data, 2, vm.changedRows.deleted, vm.changedRows.added, vm.changedRows.edited);
          $modalInstance.close(response.data);
          alertService.add('danger', locMsg['dnc.pn.file.empty.alert']);
        } else {
          vm.item = response.data;
          adminLogsFactory.check(vm.itemInitial, response.data, 2, vm.changedRows.deleted, vm.changedRows.added, vm.changedRows.edited);
          $modalInstance.close(response.data);
          alertService.add('success', locMsg['label.save.success']);
        }
      }, function(response) {
        $modalInstance.close(response.data);
        $log.error(response);
      });
      return promise;
    };

      vm.savePhones = function() {
        vm.item.phoneNumbers = [];

        vm.changedRows.added.forEach(function(item, i, arr) {
          vm.item.phoneNumbers.push(item);
        });

        vm.changedRows.edited.forEach(function(item, i, arr) {
          vm.item.phoneNumbers.push(item);
        });

        vm.changedRows.deleted.forEach(function(item, i, arr) {
          vm.item.phoneNumbers.push(item);
        });
      };

      vm.cancel = function() {
        $modalInstance.dismiss('cancel');
      };

      $scope.$on("$destroy", function(event) {
        if (angular.isDefined(vm.filterTimeout)) {
          $timeout.cancel(vm.filterTimeout);
        }
      });

      if ($rootScope.hasAnyRole([ 'ROLE_SUPERADMIN', 'ROLE_TENANT_ADMIN','ROLE_SUPERVISOR'])) {
        tenantFactory.getAll({}, {
          'field' : 'name',
          'direction' : 'asc'
        }, {
          'enabled' : [ {
            term : 'true'
          } ]
        }).then(function(data) {
          vm.tenantList = data.data;
        });
      }

    } ]);
