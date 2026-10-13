/**
 * Controller to edit a campaign
 */
app.controller('campaignFormCtrl', [
    '$modalInstance',
    'item',
    'campaignFactory',
    '$log',
    'locMsg',
    'tenantFactory',
    'locationFactory',
    'dncFactory',
    'lscFactory',
    'campaignScheduleFactory',
    'skillFactory',
    '$rootScope',
    '$q',
    '$scope',
    'uiGridConstants',
    'timeValidatorService',
    'alertService',
    'adminLogsFactory',
    'ivrApplicationFactory',
    'numberFactory',
    '$modal',
    'dbTableService',
    'constants',
    'predefinedValuesFactory',
    'telephonyRcFactory',
    'resultCodeFactory',
    'routingGroupFactory',
    'uuid',
    '$http',
    '$timeout',
    function($modalInstance, item, campaignFactory, $log, locMsg, tenantFactory, locationFactory, dncFactory, lscFactory, campaignScheduleFactory, skillFactory, $rootScope, $q,
             $scope, uiGridConstants, timeValidatorService, alertService, adminLogsFactory, ivrApplicationFactory, numberFactory, $modal, dbTableService, constants,
             predefinedValuesFactory, telephonyRcFactory, resultCodeFactory, routingGroupFactory, uuid, $http, $timeout) {

      var vm = this;
      vm.constants = constants;
      vm.item = angular.copy(item);
      vm.itemBeforeSaving = null;
      vm.itemInitial = angular.copy(item);

      // Task 325559: "Numara Basina Azami Arama" alanini acan checkbox yalniz arayuz durumudur
      // (DB kolonu yok). Tek kaynak alanin kendisidir: deger doluysa ozellik aciktir. Isaret
      // kaldirilinca deger temizlenir ve ozellik kapanir (backend: null = kapali).
      vm.syncMaxAttemptPerNumberEnabled = function() {
        vm.maxAttemptPerNumberEnabled = vm.item.maxAttemptPerNumber != null;
      };
      vm.onMaxAttemptPerNumberEnabledChange = function() {
        if (!vm.maxAttemptPerNumberEnabled) {
          vm.item.maxAttemptPerNumber = null;
        }
      };
      vm.isPerNumberLimitFromOverall = function() {
        return vm.item.retryStrategy === 0 || vm.item.retryStrategy === 1;
      };
      vm.onRetryStrategyChange = function() {
        if (vm.isPerNumberLimitFromOverall()) {
          vm.item.maxAttemptPerNumber = null;
          vm.maxAttemptPerNumberEnabled = false;
        }
      };
      vm.syncMaxAttemptPerNumberEnabled();

      vm.listSourceTables = [];
      vm.listSourceColumns = [];
      vm.callResultColumns = [];
      vm.listSourceTableToCreate = '';
      vm.resultTableToCreate = '';
      vm.listSourceColumnsToCreate = [];
      vm.callResultColumnsToCreate = [];
      vm.predefinedValues = [];
      vm.customColumnsToCreate = [];
      vm.item.blackoutSchedules = []

      vm.apiTokenRegex = /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-1[0-9a-fA-F]{3}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/;

      // Gamma Correction Factor yardım popover'ı: info ikonu veya input focus'u açar,
      // kapatma butonu, ikona tekrar tıklama veya input'tan focus çıkışı kapatır.
      vm.gammaCfHelpOpen = false;
      var gammaCfHelpBlurTimer = null;

      var cancelGammaCfHelpBlur = function() {
        if (gammaCfHelpBlurTimer) {
          $timeout.cancel(gammaCfHelpBlurTimer);
          gammaCfHelpBlurTimer = null;
        }
      };

      vm.openGammaCfHelp = function() {
        cancelGammaCfHelpBlur();
        vm.gammaCfHelpOpen = true;
      };

      vm.closeGammaCfHelp = function() {
        cancelGammaCfHelpBlur();
        vm.gammaCfHelpOpen = false;
      };

      vm.toggleGammaCfHelp = function() {
        cancelGammaCfHelpBlur();
        vm.gammaCfHelpOpen = !vm.gammaCfHelpOpen;
      };

      // Input'tan focus çıkınca popover kapanır. Kapatma butonuna veya info ikonuna
      // tıklandığında blur click'ten önce tetiklendiği için kapatma bir tick geciktirilir;
      // böylece o tıklamalar kaybolmaz.
      vm.scheduleGammaCfHelpClose = function() {
        cancelGammaCfHelpBlur();
        gammaCfHelpBlurTimer = $timeout(function() {
          gammaCfHelpBlurTimer = null;
          vm.gammaCfHelpOpen = false;
        }, 150);
      };

      $scope.$on('$destroy', cancelGammaCfHelpBlur);


      vm.loadRdsStaticLink = function() {
        $http({
          method: 'GET',
          url: '/gbpowerdialer/rds/static/link',
          headers: {
            'Content-Type': 'text/plain'
          }
        }).then(function successCallback(response) {
          if (response && response.data) {
            vm.rdsStaticLink = response.data;
          } else {
            console.error('Invalid response format for RDS static link');
          }
        }, function errorCallback(error) {
          console.error('Failed to load RDS static link:', error);
        });
      }
      
      vm.loadRdsStaticLink();
      
      vm.initDelayedActions = function() {
        vm.delayedCreateTableActions = {
            createSourceTable : new Function(),
            createDefaultPhoneNumbers: [],
            addResultColsToSourceTable : new Function(),
            addCustomFieldsToSourceTable : new Function(),
            createResultTable : new Function()
        };
      };
      vm.initDelayedActions();

      vm.initDefaultTablesAndColumns = function() {
        vm.listSourceTables.length = 0;
        var initializedTables = [ vm.item.dbListTable, vm.item.dbResultTable ];
        initializedTables.forEach(function(item) {
          if (item && vm.listSourceTables.indexOf(item) < 0)
            vm.listSourceTables.push(item);
        });

        vm.listSourceColumns.length = 0;
        var initializedColumns = [ vm.item.colUniqueKey, vm.item.colCallFromTime, vm.item.colCallToTime, vm.item.colTimezoneOffset, vm.item.colSkill ];
        if (vm.item.colSortBy !== constants.SORT_BY_MULTIPLE) {
          initializedColumns.push(vm.item.colSortBy);
        }
        initializedColumns.forEach(function(item) {
          if (item)
            vm.listSourceColumns.push({
              fieldName : item
            });
        });
        vm.listSourceColumns.uniqueByField("fieldName");

        vm.callResultColumns.length = 0;
        var initializedResultColumns = [ vm.item.colResultFetchedDataFlag, vm.item.colResultCalledNumber, vm.item.colResultTelephonyRc, vm.item.colResultUserRc,
            vm.item.colResultRcSequence, vm.item.colResultUserId, vm.item.colResultCallbackNumber, vm.item.colResultCallbackDate, vm.item.colResultRetryNumber,
            vm.item.colResultRetryDate, vm.item.colResultStartDate, vm.item.colResultAnswerDate, vm.item.colResultCompleteDate, vm.item.colResultUniqueKey,
            vm.item.colResultDNCNumbers, vm.item.colResultRetryHistory, vm.item.colResultCbHistory, vm.item.colResultCallProcess, vm.item.colResultAttempts, vm.item.colResultCallbackAttempts, vm.item.colResultUpdateDate, vm.item.colResultCustomerNo, vm.item.colResultCloseReason, vm.item.colDateEntered ];
        if (vm.item.colSortBy !== constants.SORT_BY_MULTIPLE) {
          initializedResultColumns.push(vm.item.colSortBy);
        }
        initializedResultColumns.forEach(function(item) {
          if (item != null) {
            vm.callResultColumns.push({
              fieldName : item
            });
          }
        });
        vm.callResultColumns.uniqueByField("fieldName");
      };

      if (item.id != null) {
        campaignFactory.get(item.id).then(function(data) {
          vm.item = data;
          if (vm.isPerNumberLimitFromOverall()) {
            vm.item.maxAttemptPerNumber = null;
          }
          vm.item.state = vm.itemInitial.state;
          vm.itemInitial = angular.copy(data);
          vm.syncMaxAttemptPerNumberEnabled();

          vm.initDefaultTablesAndColumns();

          if (vm.item.id != null && vm.item.type == 4) {
            vm.loadAvailableIvrApplications();
            vm.loadAvailableIvrApplicationNumbers();
          }
          vm.checkCallerIdPool();
        });
      }

      vm.checkCallerIdPool = function() {
        if (vm.item.callerIdPool) {
          vm.loadCallerIdPools().then(() => {
            if (vm.callerIdPools.filter(p => p.id === vm.item.callerIdPool).length === 0) {
              $log.warn("Could not find callerId pool in the list. Resetting...");
              vm.item.callerIdPool = null;
              vm.item.callerIdBehaviour = null;
            }
          });
        }
      }

      vm.openCalendar = function(calendar) {
        vm.calendars[calendar] = true;
      };

      vm.calendars = {
        startDate : false,
        endDate : false
      };

      vm.applyToList = [ {
        value : 0,
        label : locMsg['resultCodeSchedule.applyTo.0']
      }, {
        value : 1,
        label : locMsg['resultCodeSchedule.applyTo.1']
      }, {
        value : 2,
        label : locMsg['resultCodeSchedule.applyTo.2']
      } ];

      vm.save = function(isValid) {

        var errorTabIndex = focusAtErrorTab(vm.tabs);
        if (errorTabIndex == 1) {
          focusAtErrorAccordionGroup(vm.accordions);
        }

        if (!isValid) {
          return;
        }

        if (!vm.validateFilterRatios()) {
          return;
        }

        vm.itemBeforeSaving = vm.item;
        vm.item = angular.copy(vm.itemBeforeSaving);
        vm.gatherPredefinedValues([vm.concurrentLinesMax, vm.concurrentLinesMin,
                                  vm.abandonTimeoutMax, vm.abandonTimeoutMin,
                                  vm.ringTimeoutMax, vm.ringTimeoutMin,
                                  vm.dialingBoostMax, vm.dialingBoostMin]);

        if (!vm.resolveDatesFormat()) {
          alertService.closePermanentAlert(0);
          return;
        }

        if (timeValidatorService.validateDate(vm.item.startDate, vm.item.endDate)) {

          $q.when(vm.delayedCreateTableActions.createSourceTable()).then(function() {

            $q.all([vm.delayedPhoneNumberActions(),
              vm.delayedCreateTableActions.addResultColsToSourceTable(),
              vm.delayedCreateTableActions.addCustomFieldsToSourceTable(),
              vm.delayedCreateTableActions.createResultTable()]).then(function(result) {

              if (vm.item.id != null) {

                if ($rootScope.canEditSelectedCampaignByPermission(vm.item, "EditCampaign")) {
                  $q.all([ vm.loadCampaignLocations(), vm.loadCampaignDncsLists(), vm.loadCampaignSkills(),vm.loadCampaignLscServices()]).then(function() {

                    // Send LSC priorities and wait before saving
                    var priorityPromise = $q.when();
                    if (vm.item.id != null && vm.item.lscs && vm.item.lscs.length > 0) {
                      var lscIds = vm.item.lscs.map(function(lsc) {
                        return lsc.id;
                      });
                      console.log('[LSC-PRIORITY] . LSC IDs:', lscIds);
                      priorityPromise = campaignFactory.updateLscPriorities(vm.item.id, lscIds);
                    }

                    priorityPromise.then(function() {
                      vm.item = campaignFactory.setModifiedCollections(vm.item);
                      vm.processCampaignSaving();
                    });
                  });
                } else if (vm.userHasTechOrNonTechPermission()) {
                  vm.loadCampaignSkills().then(function() {
                    vm.item = campaignFactory.setModifiedCollections(vm.item);

                    vm.processCampaignSaving();
                  });
                }

              } else {
                vm.item.tenantListDTO = {};
                campaignFactory.create(vm.item).then(function(response) {
                  if (response.data.foreignKeyAdditionError == true) {
                    let errorMessage = locMsg['alert.fk.addition.error'].replace('%s', response.data.campaign.dbPriorityTable);
                    alertService.add('warning', errorMessage);
                  }
                  $rootScope.updateAuth();
                  $modalInstance.close(response.data.campaign);
                }, function(response) {
                  $log.error(response);
                });
              }
            }, function(reason) {
              vm.listSourceTableToCreate = '';
              vm.resultTableToCreate = '';
              vm.listSourceColumnsToCreate = [];
              vm.callResultColumnsToCreate = [];
              vm.initDelayedActions();
            });

          });

        } else {
          alertService.add('danger', locMsg['alert.campaign.startEnd.date.error']);
        }

        alertService.closePermanentAlert(0);
      };

      vm.validateFilterRatios = function() {
        var enabledSegmentationFilters = (vm.item.filters || []).filter(f => f.segmentation && f.enabled && !f.deleted);
        if (enabledSegmentationFilters.length === 0) {
          return true;
        }
        if (enabledSegmentationFilters.length === 1) {
          alertService.add('danger', locMsg['filter.segmentation.minimum.required']);
          return false;
        }
        var totalRatio = enabledSegmentationFilters.reduce((sum, filter) => sum + (parseInt(filter.ratio, 10) || 0), 0);
        if (totalRatio !== 100) {
          alertService.add('danger', locMsg['filter.incorrect.ratio.sum']);
          return false;
        }
        return true;
      };

      vm.delayedPhoneNumberActions = function() {
        var defaultPhoneNumberPromises = [];
        if (vm.delayedCreateTableActions.createDefaultPhoneNumbers && vm.delayedCreateTableActions.createDefaultPhoneNumbers.length > 0) {
          vm.delayedCreateTableActions.createDefaultPhoneNumbers.forEach(function(action) {
            defaultPhoneNumberPromises.push(action());
          })
        }
        return defaultPhoneNumberPromises;
      };

      vm.userHasTechOrNonTechPermission = function() {
        return $rootScope.canEditSelectedCampaignByPermission(vm.item, 'EditCampaignNonTech');
      };

      vm.processCampaignSaving = function() {
        if (adminLogsFactory.campaignHasBeenChanged(vm.itemInitial, vm.item, 0, null, null, null)) {
          if (vm.item.state === 3 || vm.item.state === 5) {
            if (adminLogsFactory.campaignMustBeRestarted()) {
              vm.item.mustBeRestarted = true;
            } else if (adminLogsFactory.campaignMustBeReloaded()) {
              vm.item.mustBeReloaded = true;
            }
          }
          vm.processRegularSave();
        } else
          $modalInstance.dismiss('cancel');
      };

      vm.processRegularSave = function() {
        var allowedProperties = {};
        if ($rootScope.canEditSelectedCampaignByPermission(vm.item, 'EditCampaign'))
          allowedProperties = vm.item;
        else if ($rootScope.canEditSelectedCampaignByPermission(vm.item, 'EditCampaignTech')) {
          for ( var itemKey in vm.item)
            if ($rootScope.permitedFieds.ALLOWED_TECH_FIELDS.includes(itemKey))
              allowedProperties[itemKey] = vm.item[itemKey];
        } else if ($rootScope.canEditSelectedCampaignByPermission(vm.item, 'EditCampaignNonTech')) {
          for ( var itemKey in vm.item)
            if ($rootScope.permitedFieds.ALLOWED_NON_TECH_FIELDS.includes(itemKey))
              allowedProperties[itemKey] = vm.item[itemKey];
        }
        campaignFactory.save(allowedProperties).then(function(response) {
          if (response.data.foreignKeyAdditionError == true) {
            let errorMessage = locMsg['alert.fk.addition.error'].replace('%s', response.data.campaign.dbPriorityTable);
            alertService.add('warning', errorMessage);
          }
          adminLogsFactory.createAdminLogForEditedCampaign(vm.itemInitial);
          $rootScope.updateAuth();

          if (vm.itemBeforeSaving && vm.itemBeforeSaving.lscs && vm.itemBeforeSaving.lscs.length > 0) {
            var lscIds = vm.itemBeforeSaving.lscs.map(function(lsc) { return lsc.id; });
            campaignFactory.updateLscPriorities(vm.itemBeforeSaving.id, lscIds).then(function() {
              $modalInstance.close(response.data.campaign);
            });
          } else {
            $modalInstance.close(response.data.campaign);
          }

        }, function(response) {
          $log.error(response);
          vm.item = vm.itemBeforeSaving;
        });
      };

      $scope.$watch('campaign.item.callbackToAppointer', function (newVal) {
        if (newVal === true) {
          $scope.campaign.item.allowOverwriteByWebphone = false;
        }
        if (!$scope.campaign.item.callbackToAppointer && !$scope.campaign.item.allowOverwriteByWebphone) {
          $scope.campaign.item.appointerReset = false;
        }
      });

      $scope.$watch('campaign.item.allowOverwriteByWebphone', function (newVal) {
        if (newVal === true) {
          $scope.campaign.item.callbackToAppointer = false;
        }
        if (!$scope.campaign.item.callbackToAppointer && !$scope.campaign.item.allowOverwriteByWebphone) {
          $scope.campaign.item.appointerReset = false;
        }
      });


      vm.cancel = function() {
        $modalInstance.dismiss('cancel');
        alertService.closePermanentAlert(0);
      };

      vm.connectToDB = function() {
        if (vm.item.dbConnectionString != null) {
          campaignFactory.connect(vm.item).then(function(response) {
            vm.item.connectedToDB = response;
          }, function(response) {
            vm.item.connectedToDB = false;
            $log.error(response);
          });
        }
      };

      vm.getTables = function() {
        if (vm.item.dbConnectionString != null) {
          campaignFactory.getTables(vm.item).then(function(response) {
            vm.listSourceTables = response;
            if (vm.listSourceTableToCreate)
              vm.listSourceTables.push(vm.listSourceTableToCreate);
            if (vm.resultTableToCreate)
              vm.listSourceTables.push(vm.resultTableToCreate);
            return vm.getTableColumns(vm.item.dbListTable, vm.listSourceColumns);
          }, function(response) {
            $log.error(response);
          }).then(function(response) {
            return vm.getTableColumns(vm.item.dbResultTable, vm.callResultColumns);
          });
        }
      };

      vm.getTableColumns = function(table, columns) {
        var d = $q.defer();
        if (vm.item.dbConnectionString != null && table != null && vm.item.connectedToDB) {
          campaignFactory.getTableColumns(table, vm.item).then(function(response) {
            columns.length = 0;
            Array.prototype.push.apply(columns, response);
            if (table == vm.listSourceTableToCreate) {
              Array.prototype.push.apply(columns, vm.listSourceColumnsToCreate);
            }
            if (table == vm.resultTableToCreate || vm.callResultColumnsToCreate) {
              Array.prototype.push.apply(columns, vm.callResultColumnsToCreate);
            }
            Array.prototype.uniqueByField.apply(columns, [ "fieldName" ]);
            d.resolve();
          }, function(response) {
            $log.error(response);
            d.reject(response);
          });
        } else {
          columns.length = 0;
          d.resolve();
        }
        return d.promise;
      };

      vm.getTableColumnsAndSetDefaultValuesByResultTable = function(table, columns) {
        var defer = $q.defer();
        $q.all([ vm.getTableColumns(table, columns) ]).then(function() {
          if (vm.item.dbResultTable != vm.item.dbListTable)
            vm.item.colResultUniqueKey = vm.setDefaultValueForResultColumn('unique_key');
          else
            vm.item.colResultUniqueKey = undefined;

          vm.item.colResultStartDate = vm.setDefaultValueForResultColumn('start_date');
          vm.item.colResultAnswerDate = vm.setDefaultValueForResultColumn('answer_date');
          vm.item.colResultCompleteDate = vm.setDefaultValueForResultColumn('complete_date');
          vm.item.colResultCalledNumber = vm.setDefaultValueForResultColumn('called_number');
          vm.item.colResultTelephonyRc = vm.setDefaultValueForResultColumn('telephony_result_code');
          vm.item.colResultRcSequence = vm.setDefaultValueForResultColumn('rc_sequence');
          vm.item.colResultRetryNumber = vm.setDefaultValueForResultColumn('retry_number');
          vm.item.colResultRetryHistory = vm.setDefaultValueForResultColumn('retry_history');
          vm.item.colResultCbHistory = vm.setDefaultValueForResultColumn('cb_history');
          vm.item.colResultCallProcess = vm.setDefaultValueForResultColumn('call_process');
          vm.item.colResultRetryDate = vm.setDefaultValueForResultColumn('retry_date');
          vm.item.colResultFetchedDataFlag = vm.setDefaultValueForResultColumn('is_fetched');
          vm.item.colSkill = vm.setDefaultValueForResultColumn('specific_skill_name');
          vm.item.colResultAttempts = vm.setDefaultValueForResultColumn('attempts');
          vm.item.colResultCallbackAttempts = vm.setDefaultValueForResultColumn('callback_attempts');
          vm.item.colResultUpdateDate = vm.setDefaultValueForResultColumn('update_date');
          vm.item.colResultResponseValues = vm.setDefaultValueForResultColumn('response_values');
          // Task 325559: result tablosu secildiginde close_reason binding'i de otomatik atanir.
          // setDefaultValueForResultColumn tip kontrolu yaptigi icin ayni adli ama yanlis tipli
          // bir kolon baglanmaz; alan undefined kalir ve kapanis nedeni yazilmaz.
          vm.item.colResultCloseReason = vm.setDefaultValueForResultColumn('close_reason');


          if (vm.item.type != 4) {
            vm.item.colResultCallbackDate = vm.setDefaultValueForResultColumn('callback_date');
            vm.item.colResultCallbackNumber = vm.setDefaultValueForResultColumn('callback_number');
            vm.item.colResultUserRc = vm.setDefaultValueForResultColumn('user_result_code');
            vm.item.colResultUserId = vm.setDefaultValueForResultColumn('user_id');
            vm.item.colResultDNCNumbers = vm.setDefaultValueForResultColumn('dnc_numbers');
          }
          defer.resolve();
        }, function() {
          defer.reject();
        });
        return defer.promise;
      };

      vm.clearOrSetDefaultValues = function() {
        if (vm.item.type == 4) {
          vm.item.colResultCallbackDate = undefined;
          vm.item.colResultCallbackNumber = undefined;
          vm.item.colResultUserRc = undefined;
          vm.item.colResultUserId = undefined;
        } else if (vm.item.type != undefined && vm.item.type != 4) {
          vm.processNotOutboundIVRCampaignType();
        }
      };

      vm.processNotOutboundIVRCampaignType = function () {
        if (vm.item.connectedToDB) {
          vm.setDefaultValuesForUndefinedResultTableColumns();
        } else {
          vm.checkDBConnection().then(function () {
              vm.setDefaultValuesForUndefinedResultTableColumns()
          });
        }
      };

      vm.setDefaultValuesForUndefinedResultTableColumns = function() {
        vm.getTableColumns(vm.item.dbResultTable, vm.callResultColumns)
          .then(function () {
            vm.item.colResultCallbackDate = vm.setDefaultValueForResultColumn('callback_date');
            vm.item.colResultCallbackNumber = vm.setDefaultValueForResultColumn('callback_number');
            vm.item.colResultUserRc = vm.setDefaultValueForResultColumn('user_result_code');
            vm.item.colResultUserId = vm.setDefaultValueForResultColumn('user_id');
            // Task 325559: close_reason kolonu result tablosunda varsa binding otomatik atanir;
            // kolon yoksa undefined kalir ve kapanis nedeni yazilmaz (ozellik yine calisir).
            vm.item.colResultCloseReason = vm.setDefaultValueForResultColumn('close_reason');
          });
      };

      vm.setDefaultValueForResultColumn = function(item) {
        for (var i = 0; i < vm.callResultColumns.length; i++) {
          if (vm.callResultColumns[i].fieldName == item) {
            var allowedTypes = vm.constants.RESULT_FIELDS_ALLOWED_TYPES[item];
            if (vm.callResultColumns[i].fieldType == undefined || !allowedTypes || allowedTypes.indexOf(vm.callResultColumns[i].fieldType) != -1) {
              vm.callResultColumns[i].isSelected = true;
              return vm.callResultColumns[i].fieldName;
            }
          }
        }
        return undefined;
      };

      vm.getTableColumnsAndSetDefaultValuesByListTable = function(table, columns) {
        $q.all([ vm.getTableColumns(table, columns) ]).then(function() {
          if (vm.item.dbResultTable == vm.item.dbListTable)
            vm.item.colResultUniqueKey = undefined;

          vm.item.colUniqueKey = vm.item.colSortBy = vm.setDefaultValueForListSourceColumn('id');
          vm.item.colSkill = vm.setDefaultValueForListSourceColumn('skill');
        });
      };

      vm.getTableColumnsAndSetDefaultValuesForTimezoneOrClear = function(table, columns) {
        $q.all([ vm.getTableColumns(table, columns) ]).then(function() {
          if (vm.item.timezoneType == 1) {
            vm.item.colCallFromTime = vm.setDefaultValueForListSourceColumn('call_from_time');
            vm.item.colCallToTime = vm.setDefaultValueForListSourceColumn('call_till_time');
            vm.item.colTimezoneOffset = vm.setDefaultValueForListSourceColumn('timezone_offset');
          } else {
            vm.item.colCallFromTime = undefined;
            vm.item.colCallToTime = undefined;
            vm.item.colTimezoneOffset = undefined;
          }
        });
      };

      vm.setDefaultValueForListSourceColumn = function(item) {
        for (var i = 0; i < vm.listSourceColumns.length; i++) {
          if (vm.listSourceColumns[i].fieldName.toUpperCase() == item.toUpperCase()) {
            var allowedTypes = vm.constants.RESULT_FIELDS_ALLOWED_TYPES[item];
            if (vm.callResultColumns[i] == undefined || vm.callResultColumns[i].fieldType == undefined || (allowedTypes && allowedTypes.indexOf(vm.callResultColumns[i].fieldType) != -1)) {
              vm.listSourceColumns[i].isSelected = true;
              return vm.listSourceColumns[i].fieldName;
            }
          }
        }
        return undefined;
      };

      vm.synchronizeAttempts = function(oldRetryOveralAttempt) {
        if (oldRetryOveralAttempt == vm.item.macroMaxAttempt || !vm.item.macroMaxAttempt || vm.item.macroMaxAttempt > vm.item.retryOveralAttempt)
          vm.item.macroMaxAttempt = vm.item.retryOveralAttempt;
      };

      vm.setActualResultColumns = function(previousColumnName, selectedColumnName) {
        for (var i = 0; i < vm.callResultColumns.length; i++) {
          if (vm.callResultColumns[i].fieldName == selectedColumnName)
            vm.callResultColumns[i].isSelected = true;
          if (vm.callResultColumns[i].fieldName == previousColumnName)
            vm.callResultColumns[i].isSelected = false;
        }
      };

      vm.setActualSourceColumns = function(previousColumnName, selectedColumnName) {
        for (var i = 0; i < vm.listSourceColumns.length; i++) {
          if (vm.listSourceColumns[i].fieldName == selectedColumnName)
            vm.listSourceColumns[i].isSelected = true;
          if (vm.listSourceColumns[i].fieldName == previousColumnName)
            vm.listSourceColumns[i].isSelected = false;
        }
      };

      vm.disconnectFromDB = function() {
        if (vm.item.id != null) {
          campaignFactory.disconnect(vm.item.id).then(function(response) {
            vm.item.connectedToDB = false;
            vm.listSourceTables = [];
            vm.initDefaultTablesAndColumns();
            dbTablesRefreshed = false;
          }, function(response) {
            $log.error(response);
          });
        } else {
          vm.item.connectedToDB = false;
          vm.listSourceTables = [];
          vm.initDefaultTablesAndColumns();
          dbTablesRefreshed = false;
        }
      };

      var dbTablesRefreshed = false;
      vm.refreshDBTables = function() {
        if (vm.item.connectedToDB && !dbTablesRefreshed) {
          vm.getTables();
          dbTablesRefreshed = true;
        }
      };

      var dbConnectionChecked = false;
      vm.checkDBConnection = function() {
        var d = $q.defer();
        if (vm.item.id != null && !dbConnectionChecked) {
          dbConnectionChecked = true;
          campaignFactory.checkConnection(vm.item).then(function(response) {
            vm.item.connectedToDB = response;
            d.resolve(response);
          }, function(response) {
            vm.item.connectedToDB = false;
            d.reject();
          });
        } else {
          d.resolve(vm.item.connectedToDB);
        }
        return d.promise;
      };

      vm.generateToken = function() {
        vm.item.apiToken = uuid.v1().toUpperCase();
      }

      vm.onDialingOptionsTabClick = function () {
        vm.loadAvailableLocations();
        vm.loadCampaignLocations();
        if (!vm.callerIdPools) {
          vm.loadCallerIdPools();
        }
        vm.loadCampaignPredefinedValues().then(vm.initPredefinedValues);
      };

      vm.loadCampaignPredefinedValues = function () {
        var d = $q.defer();
        if (vm.item.id != null)
          predefinedValuesFactory.loadValuesByCampaignId(vm.item.id).then(function (data) {
            vm.resolvePredefinedValues(d, data);
          });
        else
          vm.resolvePredefinedValues(d, []);
        return d.promise;
      };

      vm.resolvePredefinedValues = function (deferred, data) {
        vm.item.predefinedValues = data;
        vm.itemInitial.predefinedValues = angular.copy(data);
        deferred.resolve();
      };

      vm.initPredefinedValues = function () {
        vm.concurrentLinesMax = vm.getPredefinedValueByType(constants.predefinedValuesTypes.CONCURRENT_LINES_MAX) || vm.createNewPredefinedValue(constants.predefinedValuesTypes.CONCURRENT_LINES_MAX, false);
        vm.concurrentLinesMin = vm.getPredefinedValueByType(constants.predefinedValuesTypes.CONCURRENT_LINES_MIN) || vm.createNewPredefinedValue(constants.predefinedValuesTypes.CONCURRENT_LINES_MIN, false);
        vm.abandonTimeoutMax = vm.getPredefinedValueByType(constants.predefinedValuesTypes.ABANDON_TIMEOUT_MAX) || vm.createNewPredefinedValue(constants.predefinedValuesTypes.ABANDON_TIMEOUT_MAX, false);
        vm.abandonTimeoutMin = vm.getPredefinedValueByType(constants.predefinedValuesTypes.ABANDON_TIMEOUT_MIN) || vm.createNewPredefinedValue(constants.predefinedValuesTypes.ABANDON_TIMEOUT_MIN, false);
        vm.ringTimeoutMax = vm.getPredefinedValueByType(constants.predefinedValuesTypes.RING_TIMEOUT_MAX) || vm.createNewPredefinedValue(constants.predefinedValuesTypes.RING_TIMEOUT_MAX, false);
        vm.ringTimeoutMin = vm.getPredefinedValueByType(constants.predefinedValuesTypes.RING_TIMEOUT_MIN) || vm.createNewPredefinedValue(constants.predefinedValuesTypes.RING_TIMEOUT_MIN, false);
        vm.dialingBoostMax = vm.getPredefinedValueByType(constants.predefinedValuesTypes.DIALING_BOOST_MAX) || vm.createNewPredefinedValue(constants.predefinedValuesTypes.DIALING_BOOST_MAX, false);
        vm.dialingBoostMin = vm.getPredefinedValueByType(constants.predefinedValuesTypes.DIALING_BOOST_MIN) || vm.createNewPredefinedValue(constants.predefinedValuesTypes.DIALING_BOOST_MIN, false);
      };

      vm.getPredefinedValueByType = function (type) {
        return vm.item.predefinedValues.filter(function(item) {
          return item.type === type;
        })[0];
      };

      vm.createNewPredefinedValue = function(type, enabled) {
        return predefinedValuesFactory.createNew(type, enabled);
      };

      vm.loadAvailableLocations = function() {
        if (vm.locationsList == null) {
          var availableLocationsPromise = locationFactory.getAll({}, {
            'field': 'name',
            'direction': 'asc'
          }, {
            'enabled': [{
              term: 'true'
            }]
          }).then(function(data) {
            return data.data;
          });
          vm.mergePromisesResults([availableLocationsPromise, vm.loadCampaignLocations()], _.property('id')).then(function(mergedData) {
            vm.locationsList = mergedData;
          });
        }
      };

      vm.loadCampaignLocations = function() {
        var d = $q.defer();
        if (vm.item.id != null && vm.item.locations == null) {
          campaignFactory.getCampaignsLocations(vm.item.id).then(function(data) {
            vm.item.locations = data.data;
            vm.itemInitial.locations = angular.copy(data.data);
            d.resolve(data.data);
          });
        } else {
          d.resolve(vm.item.locations);
        }
        return d.promise;
      };

      vm.loadCallerIdPools = function () {
        return routingGroupFactory.getAll({}, {
          'field': 'name',
          'direction': 'asc'
        }, {}).then((data) => {
          vm.callerIdPools = data.data;
          vm.callerIdPools.filter(p => p.name.length > 50).forEach(p => p.name = p.name.substring(0, 47) + '...');
        });
      }

      vm.loadAvailableSkills = function() {
        if (vm.skillsList == null) {
          var availableSkillsPromise = skillFactory.getAll({}, {
            'field': 'name',
            'direction': 'asc'
          }, {
            'enabled': [{
              term: 'true'
            }]
          }).then(function(data) {
            return data.data;
          });
          vm.mergePromisesResults([availableSkillsPromise, vm.loadCampaignSkills()], _.property('id')).then(function(mergedData) {
            vm.skillsList = mergedData;
          });
        }
      };

      vm.loadCampaignSkills = function() {
        var d = $q.defer();
        if (vm.item.id != null && vm.item.skills == null) {
          campaignFactory.getCampaignsSkills(vm.item.id).then(function(data) {
            vm.item.skills = data.data;
            vm.itemInitial.skills = angular.copy(data.data);
            d.resolve(data.data);
          });
        } else {
          d.resolve(vm.item.skills);
        }
        return d.promise;
      };

      vm.loadTelephonyRcs = function() {
        var d = $q.defer();
        if (vm.telephonyRcList == null) {
          telephonyRcFactory.getAll({}, {
            'field' : 'name',
            'direction' : 'asc'
          }, {}).then(function(data) {
            vm.telephonyRcList = _.sortBy(data.data, 'name');
            d.resolve();
          });
        } else {
          d.resolve();
        }
        return d.promise;
      };

      vm.loadResultCodes = function() {
        var d = $q.defer();

        if (vm.item.id !== undefined && vm.item.campaignGroups == null && vm.resultCodeList == null) {

          resultCodeFactory.getResultCodesByCampaignId(vm.item.id, false).then(function(data) {
            vm.resultCodeList = _.sortBy(data, 'name');
            d.resolve();
          });

        } else {

          if (vm.item.campaignGroups != null) {

            var ids = [];
            $.each(vm.item.campaignGroups, function(index, item) {
              if (item.enabled) {
                ids.push(item.group.id);
              }
            });

            resultCodeFactory.getResultCodesByGroupIds(ids, false).then(function(data) {
              vm.resultCodeList = _.sortBy(data, 'name');
              d.resolve();
            });

          } else {
            d.resolve();
          }

        }

        return d.promise;
      };

      $rootScope.canEditSelectedCampaignByPermission = function(campaign, permissionName) {
        return $rootScope.hasAnyRole([ 'ROLE_SUPERADMIN' ])
          || (!campaign.id && $rootScope.hasAuthority({ authority : "CreateCampaign" }))
          || $rootScope.hasAllAuthority(permissionName)
          || $rootScope.hasAuthorityByCampaignOrTenant(permissionName, campaign);
      };

      $rootScope.hasAuthorityByCampaignOrTenant = function(permission, campaign) {
        return $rootScope.hasAuthority({
          authority : permission,
          value : campaign.id
        }) || $rootScope.hasAuthority({
          authority : permission,
          value : 'ALL' + campaign.tenant
        });
      };

      $rootScope.hasAllAuthority = function(permission) {
        return $rootScope.hasAuthority({
          authority : permission,
          value : 'ALL'
        });
      };

      vm.getColumnRange = function (predefinedValueMax, predefinedValueMin) {
        if (vm.columnRangeEnabled(predefinedValueMax, predefinedValueMin))
          return {max: parseFloat(predefinedValueMax.value), min: parseFloat(predefinedValueMin.value)};
        else
          return {max: undefined, min: undefined};
      };

      vm.columnRangeEnabled = function(predefinedValueMax, predefinedValueMin) {
        return predefinedValueMin && predefinedValueMax && predefinedValueMin.enabled && predefinedValueMax.enabled && (predefinedValueMax.value >= predefinedValueMin.value);
      };

      $rootScope.hasCampaignTechnicalFilters = function(campaign) {
        if (!campaign.filters)
          return false;
        else {
          for (filter in campaign.filters) {
            if (campaign.filters[filter].technical)
              return true;
          }
          return false;
        }
      };

      $scope.$watchCollection(
          function () { return vm.item.filters; },
          function () {
            if ($rootScope.hasSegmentationFilters(vm.item)) {
              vm.item.applyFilterTo = 2;
            }
          }
      );

      $rootScope.hasSegmentationFilters = function (campaign) {
        if (!campaign || !campaign.filters) { return false; }
        return campaign.filters.some(function (f) {
          return f.segmentation && !f.deleted && f.enabled;
        });
      };

      $rootScope.isTechnicalFilter = function(filter) {
        if (filter)
          return filter.technical;
        return false
      };

      vm.loadAvailableDncsLists = function() {
        if (vm.dncsList == null) {
          var availableDncsPromise = dncFactory.getAll({}, {
            'field': 'name',
            'direction': 'asc'
          }, {
            'enabled': [{
              term: 'true'
            }],
            'name': [{
              term: 'Default',
              condition: 512,
            }],
            'campaign': [{
              term: '',
              condition: 1024
            }]
          }).then(function(data) {
            return data.data;
          });
          vm.mergePromisesResults([availableDncsPromise, vm.loadCampaignDncsLists()], _.property('id')).then(function(mergedData) {
            vm.dncsList = mergedData;
          });
        }
      };

      vm.loadCampaignDncsLists = function() {
        var d = $q.defer();
        if (vm.item.id != null && vm.item.dncs == null) {
          campaignFactory.getCampaignsDNCs(vm.item.id).then(function(data) {
            vm.item.dncs = data.data;
            vm.itemInitial.dncs = angular.copy(data.data);
            d.resolve(data.data);
          });
        } else {
          d.resolve(vm.item.dncs);
        }
        return d.promise;
      };

      vm.isDefaultDncItem = function(item) {
        return item && item.campaignDefault;
      };

      vm.loadAvailableLscServices = function() {
        if (vm.lscsList == null) {
          var availableLscsPromise = lscFactory.getAll({
            'fetchRowCount': false
          }, {
            'field': 'name',
            'direction': 'asc'
          }, {
            'enabled': [{
              term: 'true'
            }]
          }).then(function(data) {
            return data.data;
          });
          vm.mergePromisesResults([availableLscsPromise, vm.loadCampaignLscServices()], _.property('id')).then(function(mergedData) {
            vm.lscsList = mergedData;
          });
        }
      };

      vm.mergePromisesResults = function(promisesArray, comparisonFeatureExtractor) {
        return $q.all(promisesArray).then(function(result) {
          var resultsUnion = _.union.apply(this, result);
          return _.uniq(resultsUnion, false, comparisonFeatureExtractor);
        })
      };

      vm.loadCampaignLscServices = function() {
        var d = $q.defer();
        if (vm.item.id != null && vm.item.lscs == null) {
          campaignFactory.getCampaignsLSCs(vm.item.id).then(function(data) {
            vm.item.lscs = data.data;
            vm.itemInitial.lscs = angular.copy(vm.item.lscs);
            d.resolve(data.data);
          });
        } else {
          d.resolve(vm.item.lscs);
        }
        return d.promise;
      };

      vm.loadAvailableIvrApplications = function() {
        if (vm.ivrApplicationList == null && vm.item.type == 4) {

          var requestOptions = {
            sort : {
              'field' : 'name',
              'direction' : 'asc'
            },
            filters : {
              'enabled' : [ {
                term : 'true'
              } ]
            }
          };

          if (!$rootScope.hasAnyRole([ 'ROLE_SUPERADMIN' ])) {
            requestOptions.filters['tenant.id'] = [ {
              term : $rootScope.currentUser.tenantId.toString(),
              condition : uiGridConstants.filter.EXACT
            } ];
          }

          ivrApplicationFactory.getAll({}, requestOptions.sort, requestOptions.filters).then(function(data) {
            vm.ivrApplicationList = data.data;
            vm.loadAvailableIvrApplicationNumbers();
          });

        }
      };

      vm.loadAvailableIvrApplicationNumbers = function() {
        if (vm.item.ivrApplication !== undefined) {
          numberFactory.getAll({}, {
            'field' : 'name',
            'direction' : 'asc'
          }, {
            'enabled' : [ {
              term : 'true'
            } ],
            'ivrApplication.id' : vm.item.ivrApplication ? [ {
              term : vm.item.ivrApplication.toString(),
              condition : uiGridConstants.filter.EXACT
            } ] : ''
          }).then(function(data) {
            vm.ivrApplicationNumberList = data.data;
          });
        }
      };

      vm.isDialingBoostDisabled = function() {
        return vm.item.type == undefined || vm.item.type == 2 || vm.item.type == 3 || vm.item.type == 4 || vm.item.type == 5;
      };

      vm.isAbandonTimeoutDisabled = function() {
        return vm.item.type == undefined || vm.item.type == 2 || vm.item.type == 3 || vm.item.type == 4;
      };

      vm.isAvailableThresholdDisabled = function() {
        return vm.item.type == 4;
      };

      if ($rootScope.hasAnyRole([ 'ROLE_SUPERADMIN', 'ROLE_TENANT_ADMIN' ])) {
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

      vm.openCreateSourceTableDialog = function() {
        var modalInstance = $modal.open({
          animation : true,
          backdrop : 'static',
          templateUrl : 'pages/dbSourceTableForm.html',
          controller : 'dbTableFormCtrl as dbTable',
          size : 'sm',
          resolve : {
            campaign : function() {
              return vm.item;
            },
            listSourceTables : function() {
              return vm.listSourceTables;
            }
          }
        });

        modalInstance.result.then(function(result) {
          if (result) {
            vm.listSourceTableToCreate = result.tableToCreate;
            vm.listSourceTables.push(result.tableToCreate);
            Array.prototype.push.apply(vm.listSourceColumnsToCreate, result.columnsToCreate);
            vm.delayedCreateTableActions.createSourceTable = result.delayedAction;
            vm.getTables();
          }
        }, function() {
          $log.info('Modal dismissed at: ' + new Date());
        });
      };

      vm.openAddColumnDialog = function() {
        let modalInstance = $modal.open({
          animation : true,
          backdrop : 'static',
          templateUrl : 'pages/dbCustomFieldForm.html',
          controller : 'dbCustomFieldFormCtrl as dbFieldCtrl',
          size : 'sm',
          resolve : {
            listSourceColumns : function() {
              return vm.listSourceColumns;
            },
            customColumnsToCreate : function() {
              return vm.customColumnsToCreate;
            }
          }
        });

        modalInstance.result.then(function(result) {
          if (result) {
            vm.customColumnsToCreate.push(result);
            vm.listSourceColumns.push(result);
            if (vm.item.dbListTable === vm.item.dbResultTable) {
              vm.callResultColumns.push(result);
            }
            var delayedAction = function() {
              var defer = $q.defer();
              dbTableService.addCustomFieldsToSourceTable(vm.item, vm.customColumnsToCreate).then(function(result) {
                defer.resolve(result);
              }, function(reason) {
                defer.reject(reason);
              });
              return defer.promise;
            };
            vm.delayedCreateTableActions.addCustomFieldsToSourceTable = delayedAction;
          }
        }, function() {
          $log.info('Modal dismissed at: ' + new Date());
        });
      };

      vm.openRangePredefinedValueDialog = function(predefinedValueMax, predefinedValueMin, systemMax, systemMin) {
        var modalInstance = $modal.open({
          animation : true,
          backdrop : 'static',
          templateUrl : 'pages/rangePredefinedValueForm.html',
          controller : 'rangePredefinedValueCtrl as rangePredefinedValue',
          size : 'sm',
          resolve : {
            campaign : function() {
              return vm.item;
            },
            maxPredefinedValue : predefinedValueMax,
            minPredefinedValue : predefinedValueMin,
            systemMax : systemMax,
            systemMin : systemMin
          }
        });

        modalInstance.result.then(function (result) {
          angular.merge(predefinedValueMax, result[0]);
          angular.merge(predefinedValueMin, result[1]);
        }, function() {
          $log.info('Modal dismissed at: ' + new Date());
        });
      };

      vm.gatherPredefinedValues = function (predefinedValues) {
        vm.item.predefinedValues = [];
        predefinedValues.forEach(function (predefinedValue) {
          if (predefinedValue && !predefinedValue.isNew) {
            vm.item.predefinedValues.push(predefinedValue)
          }
        })
      };

      vm.openCreateResultTableDialog = function() {
        var modalInstance = $modal.open({
          animation : true,
          backdrop : 'static',
          templateUrl : 'pages/dbResultTableForm.html',
          controller : 'dbTableFormCtrl as dbTable',
          size : 'sm',
          resolve : {
            campaign : function() {
              return vm.item;
            },
            listSourceTables : function() {
              return vm.listSourceTables;
            }
          }
        });

        modalInstance.result.then(function(result) {
          if (result) {
            vm.resultTableToCreate = result.tableToCreate;
            Array.prototype.push.apply(vm.callResultColumnsToCreate, result.columnsToCreate);
            vm.delayedCreateTableActions.createResultTable = result.delayedAction;
            vm.getTables();
          }
        }, function() {
          $log.info('Modal dismissed at: ' + new Date());
        });
      };

      vm.addResultColsToSourceTable = function() {
        var updateCampaignColumns = function() {
          vm.item.dbResultTable = vm.item.dbListTable;
          vm.item.colResultUniqueKey = undefined;
          vm.item.colResultStartDate = constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.START_DATE;
          vm.item.colResultAnswerDate = constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.ANSWER_DATE;
          vm.item.colResultCompleteDate = constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.COMPLETE_DATE;
          vm.item.colResultCalledNumber = constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.CALLED_NUMBER;
          vm.item.colResultTelephonyRc = constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.TELEPHONY_RC;
          vm.item.colResultRcSequence = constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.RC_SEQUENCE;
          vm.item.colResultRetryNumber = constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.RETRY_NUMBER;
          vm.item.colResultRetryHistory = constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.RETRY_HISTORY;
          vm.item.colResultCbHistory = constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.CB_HISTORY;
          vm.item.colResultCallProcess = constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.CALL_PROCESS;
          vm.item.colResultRetryDate = constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.RETRY_DATE;
          vm.item.colResultFetchedDataFlag = constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.FETCHED_DATA_FLAG;
          vm.item.colResultAttempts = constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.ATTEMPTS;
          vm.item.colResultCallbackAttempts = constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.CALLBACK_ATTEMPTS;
          vm.item.colResultUpdateDate = constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.UPDATE_DATE;
          vm.item.colResultResponseValues = constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.RESPONSE_VALUES;
          // Task 325559: kaynak tabloya result kolonlari eklendiginde close_reason da eklenir
          // (backend addDefaultResultColumnsToSourceTable icinde zaten var), binding'i de atanir.
          vm.item.colResultCloseReason = constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.CLOSE_REASON;

          if (vm.item.type != 4) {
            vm.item.colResultUserRc = constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.USER_RC;
            vm.item.colResultUserId = constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.USER_ID;
            vm.item.colResultCallbackNumber = constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.CALLBACK_NUMBER;
            vm.item.colResultCallbackDate = constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.CALLBACK_DATE;
            vm.item.colResultDNCNumbers = constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.DNC_NUMBERS;
          }
        };
        if (dbTableService.checkSourceTableForResultColumnsExistence(vm.listSourceColumns)) {
          vm.getTableColumnsAndSetDefaultValuesByResultTable(vm.item.dbListTable, vm.callResultColumns).then(function() {
            updateCampaignColumns();
          });
        } else {
          var delayedAction = function() {
            var defer = $q.defer();
            dbTableService.addResultColsToSourceTable(vm.item).then(function(result) {
              defer.resolve(result);
            }, function(reason) {
              defer.reject(reason);
            });
            return defer.promise;
          };

          vm.delayedCreateTableActions.addResultColsToSourceTable = delayedAction;
          vm.callResultColumnsToCreate.push({
            fieldName : constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.START_DATE
          });
          vm.callResultColumnsToCreate.push({
            fieldName : constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.ANSWER_DATE
          });
          vm.callResultColumnsToCreate.push({
            fieldName : constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.COMPLETE_DATE
          });
          vm.callResultColumnsToCreate.push({
            fieldName : constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.CALLED_NUMBER
          });
          vm.callResultColumnsToCreate.push({
            fieldName : constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.TELEPHONY_RC
          });
          vm.callResultColumnsToCreate.push({
            fieldName : constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.USER_RC
          });
          vm.callResultColumnsToCreate.push({
            fieldName : constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.RC_SEQUENCE
          });
          vm.callResultColumnsToCreate.push({
            fieldName : constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.USER_ID
          });
          vm.callResultColumnsToCreate.push({
            fieldName : constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.CALLBACK_NUMBER
          });
          vm.callResultColumnsToCreate.push({
            fieldName : constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.CALLBACK_DATE
          });
          vm.callResultColumnsToCreate.push({
            fieldName : constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.RETRY_NUMBER
          });
          vm.callResultColumnsToCreate.push({
            fieldName : constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.RETRY_HISTORY
          });
          vm.callResultColumnsToCreate.push({
            fieldName : constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.CB_HISTORY
          });
          vm.callResultColumnsToCreate.push({
            fieldName : constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.CALL_PROCESS
          });
          vm.callResultColumnsToCreate.push({
            fieldName : constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.RETRY_DATE
          });
          vm.callResultColumnsToCreate.push({
            fieldName : constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.FETCHED_DATA_FLAG
          });
          vm.callResultColumnsToCreate.push({
            fieldName : constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.DNC_NUMBERS
          });
          vm.callResultColumnsToCreate.push({
            fieldName : constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.SPECIFIC_SKILL_NAME
          });
          vm.callResultColumnsToCreate.push({
            fieldName : constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.ATTEMPTS
          });
          vm.callResultColumnsToCreate.push({
            fieldName : constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.CALLBACK_ATTEMPTS
          });
          vm.callResultColumnsToCreate.push({
            fieldName : constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.UPDATE_DATE
          });
          vm.callResultColumnsToCreate.push({
            fieldName : constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.RESPONSE_VALUES
          });
          // Task 325559: olusturulacak-kolon listesinde close_reason da yer alir; aksi halde
          // operator kolonu dropdown'da bulamiyordu.
          vm.callResultColumnsToCreate.push({
            fieldName : constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.CLOSE_REASON
          });
          vm.getTables();
          updateCampaignColumns();
        }
      };

      vm.onClickSkillTab = function() {
        vm.checkDBConnection().then(vm.refreshDBTables);
        vm.loadCampaignSkills();
        vm.loadAvailableSkills();
      };

      vm.resolveDatesFormat = function () {
        const startDate = vm.item.startDate;
        const endDate = vm.item.endDate;
        let resolved = true;
        if (typeof startDate  === 'string') {
          if (timeValidatorService.isValidDateFormat(startDate)) {
            vm.item.startDate = moment(startDate, 'DD/MM/YYYY HH:mm').toDate();
          } else {
            vm.item.startDate = vm.itemInitial.startDate;
            alertService.add('danger', locMsg['alert.campaign.startDate.invalid']);
            resolved = false;
          }
        }
        if (typeof endDate  === 'string') {
          if (timeValidatorService.isValidDateFormat(endDate)) {
            vm.item.endDate = moment(endDate, 'DD/MM/YYYY HH:mm').toDate();
          } else {
            vm.item.endDate= vm.itemInitial.endDate;
            alertService.add('danger', locMsg['alert.campaign.endDate.invalid']);
            resolved = false;
          }
        }
        return resolved;
      };

      vm.shouldShowConfirmDialog = function(){
        if (vm.item.scheduleAutomationEnabled && vm.item.scheduleAutomations) {
          let enabledSchedules = vm.item.scheduleAutomations.filter(item =>{
                return item.enabled;
              }
          );
          return enabledSchedules.length !== 2;
        }
        return false;
      };

      vm.processConfirmationSave = function (isValid) {
        vm.save(isValid);
        vm.cancel();
      };

      vm.onColSortByChange = function() {
        if (vm.item.colSortBy === constants.SORT_BY_MULTIPLE) {
          vm.item.dbListSortDirection = vm.dbListSortDirections[1].value;
        }
      };

      // Filter LSCs that are not yet selected
      vm.lscNotSelected = function(lsc) {
        return !vm.item.lscs || !vm.item.lscs.some(function(s) {
          return s.id === lsc.id;
        });
      };

      // Move all available items to selected list
      vm.addAllLscs = function() {
        (vm.lscsList || []).forEach(function(lsc) {
          if (vm.lscNotSelected(lsc)) {
            vm.item.lscs.push(lsc);
          }
        });
        vm.selectedAvailableLscs = [];
      };

      // Move selected available items to selected list
      vm.addOneLsc = function() {
        (vm.selectedAvailableLscs || []).forEach(function(id) {
          var lsc = vm.lscsList.find(function(l) {
            return l.id == id;
          });
          if (lsc && vm.lscNotSelected(lsc)) {
            vm.item.lscs.push(lsc);
          }
        });
        vm.selectedAvailableLscs = [];
      };

      // Remove selected items from selected list
      vm.removeOneLsc = function() {
        (vm.selectedChosenLscs || []).forEach(function(id) {
          vm.item.lscs = vm.item.lscs.filter(function(l) {
            return l.id != id;
          });
        });
        vm.selectedChosenLscs = [];
      };

      // Remove all items from selected list
      vm.removeAllLscs = function() {
        vm.item.lscs = [];
        vm.selectedChosenLscs = [];
      };

      // Move selected item one position up in the list
      vm.moveLscUp = function() {
        (vm.selectedChosenLscs || []).forEach(function(id) {
          var idx = vm.item.lscs.findIndex(function(l) {
            return l.id == id;
          });
          if (idx > 0) {
            var tmp = vm.item.lscs[idx - 1];
            vm.item.lscs[idx - 1] = vm.item.lscs[idx];
            vm.item.lscs[idx] = tmp;
          }
        });
      };

      // Move selected item one position down in the list
      // Reversed to prevent multiple selections from jumping out of order
      vm.moveLscDown = function() {
        var ids = (vm.selectedChosenLscs || []).slice().reverse();
        ids.forEach(function(id) {
          var idx = vm.item.lscs.findIndex(function(l) {
            return l.id == id;
          });
          if (idx >= 0 && idx < vm.item.lscs.length - 1) {
            var tmp = vm.item.lscs[idx + 1];
            vm.item.lscs[idx + 1] = vm.item.lscs[idx];
            vm.item.lscs[idx] = tmp;
          }
        });
      };

      vm.types = [ {
        value : 0,
        label : locMsg['campaign.type.0']
      }, {
        value : 1,
        label : locMsg['campaign.type.1']
      }, {
        value : 2,
        label : locMsg['campaign.type.2']
      }, {
        value : 3,
        label : locMsg['campaign.type.3']
      }, {
        value : 4,
        label : locMsg['campaign.type.4']
      }, {
        value : 5,
        label : locMsg['campaign.type.5']
      } ];


      vm.endListBehaviours = [ {
        value : 0,
        label : locMsg['campaign.endListBehaviour.0']
      }, {
        value : 1,
        label : locMsg['campaign.endListBehaviour.1']
      }, {
        value : 2,
        label : locMsg['campaign.endListBehaviour.2']
      } ];

      vm.predictiveStrategies = [ {
        value : 0,
        label : locMsg['campaign.predictiveStrategy.0']
      }, {
        value : 1,
        label : locMsg['campaign.predictiveStrategy.1']
      }, {
        value : 2,
        label : locMsg['campaign.predictiveStrategy.2']
      } ];

      vm.timezoneTypes = [ {
        value : 0,
        label : locMsg['campaign.timezoneType.0']
      }, {
        value : 1,
        label : locMsg['campaign.timezoneType.1']
      }, {
        value : 2,
        label : locMsg['campaign.timezoneType.2']
      } ];

      vm.retryStrategies = [ {
        value : 0,
        label : locMsg['campaign.retryStrategy.0']
      }, {
        value : 1,
        label : locMsg['campaign.retryStrategy.1']
      }, {
        value : 2,
        label : locMsg['campaign.retryStrategy.2']
      }, {
        value : 3,
        label : locMsg['campaign.retryStrategy.3']
      }, {
        value : 4,
        label : locMsg['campaign.retryStrategy.4']
      }, {
        value : 5,
        label : locMsg['campaign.retryStrategy.5']
      } ];

      vm.callerIdBehaviours = [ {
        value : 0,
        label : locMsg['campaign.callerIdBehaviour.0']
      }, {
        value : 1,
        label : locMsg['campaign.callerIdBehaviour.1']
      }, {
        value : 2,
        label : locMsg['campaign.callerIdBehaviour.2']
      }, {
        value : 3,
        label : locMsg['campaign.callerIdBehaviour.3']
      } ];

      vm.dbListSortDirections = [ {
        value : 0,
        label : locMsg['campaign.dbListSortDirection.0']
      }, {
        value : 1,
        label : locMsg['campaign.dbListSortDirection.1']
      } ];

      vm.stopConditionParameters = [ {
        value : 0,
        label : locMsg['campaign.stopCondition.parameter.0']
      }, {
        value : 1,
        label : locMsg['campaign.stopCondition.parameter.1']
      }, {
        value : 2,
        label : locMsg['campaign.stopCondition.parameter.2']
      }, {
        value : 3,
        label : locMsg['campaign.stopCondition.parameter.3']
      } ];

      vm.accordions = [ {
        id : 'dbConnection',
        active : false
      }, {
        id : 'source',
        active : false
      }, {
        id : 'sortConditions',
        active : false
      }, {
        id : 'phoneNumbers',
        active : false
      }, {
        id : 'filters',
        active : false
      }, {
        id : 'callResults',
        active : false
      }, {
        id : 'timezone',
        active : false
      }, {
        id : 'attachedDatas',
        active : false
      } ];

      vm.tabs = [ {
        id : 'general',
        active : true
      }, {
        id : 'listSource',
        active : false
      }, {
        id : 'groups',
        active : false
      }, {
        id : 'skills',
        active : false
      }, {
        id : 'dialingOptions',
        active : false
      }, {
        id : 'retryRules',
        active : false
      }, {
        id : 'schedule',
        active : false
      }, {
        id : 'scheduleAutomation',
        active : false
      }, {
        id : 'dnc',
        active : false
      }, {
        id : 'lsc',
        active : false
      }, {
        id : 'prediction',
        active : false
      }, {
        // Callback tab (DOM'da General'in hemen ardinda render edilir; active binding campaign.tabs[11]).
        // Dizinin sonuna eklendi ki mevcut index'ler ve focusAtErrorTab'taki "errorTabIndex == 1" (listSource)
        // hardcode'u bozulmasin.
        id : 'callback',
        active : false
      } ];


      vm.filterJoinTypes = [ {
        value : 0,
        label : 'AND'
      }, {
        value : 1,
        label : 'OR'
      } ];

      vm.lscTimeoutBehaviors = [ {
        value : 0,
        label : locMsg['campaign.lscTimeoutBehavior.0']
      }, {
        value : 1,
        label : locMsg['campaign.lscTimeoutBehavior.1']
      } ];

    } ]);
