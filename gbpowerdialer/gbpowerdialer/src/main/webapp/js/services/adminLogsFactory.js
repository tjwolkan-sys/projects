/**
 * Factory for CRUD operations on adminLog
 */
app.factory('adminLogsFactory', [
    '$http',
    '$log',
    'baseFactory',
    'tenantFactory',
    'locMsg',
    'timeValidatorService',
    'ivrApplicationFactory',
    'constants',
    function($http, $log, baseFactory, tenantFactory, locMsg, timeValidatorService, ivrApplicationFactory, constants) {

      return angular.extend(Object.create(baseFactory.prototype), {

        ignoreList : [ 'updateDate', 'createDate', 'stoppedDate', 'totalCallDuration', 'retryListSize', '$$hashKey', 'campaign', 'changed', 'isNew', 'deleted', 'id', 'tenantListDTO', 'tenantListDTOname', 'tenantListDTOdescription',
            'phoneNumbers', 'connected', 'version', 'totalListSize', 'actualListSize', 'activeCalls', 'completionPercent', 'dbEfficiency', 'answerRate', 'liveCalls', 'callsMade', 'telephonyResultCodecode',
            'agentsSize', 'availAgentsSize', 'listRemaining', 'ringingCalls', 'noConnectionCalls', 'retriesMade', 'cacheSize', 'queueSize', 'runoutEstimation' ],

        sets : [ 'lscs', 'dncs', 'skills', 'locations', 'scheduleAutomations'],

        timeValues : [ 'from', 'till', 'maxScheduleTime', 'minScheduleTime' ],

        filterTypes : {
          0 : 'Predefined',
          1 : 'Free'
        },

        filterJoinTypes : {
          0 : 'AND',
          1 : 'OR'
        },

        campaignTypes : {
          0 : 'Progressive Reserved',
          1 : 'Progressive Released',
          2 : 'Preview Free',
          3 : 'Preview Straight',
          4 : 'Outbound IVR'
        },

        days : {
          0 : 'Monday',
          1 : 'Tuesday',
          2 : 'Wednesday',
          3 : 'Thursday',
          4 : 'Friday',
          5 : 'Saturday',
          6 : 'Sunday'
        },

        numberToTryList : {
          0 : 'Previous',
          1 : 'Same',
          2 : 'Next'
        },

        filterConditions : {
          0 : 'Equal',
          1 : 'Not Equal',
          2 : 'Greater than',
          3 : 'Less than',
          4 : 'Greater than or Equal',
          5 : 'Less than or Equal',
          6 : 'Like',
          7 : 'Not Like',
          8 : 'In',
          9 : 'Not In'
        },

        applyToList : {
          0 : 'Retries',
          1 : 'Callbacks',
          2 : 'Retries & Callbacks'
        },

        endListBehaviours : {
          0 : 'Continue campaign',
          1 : 'Stop campaign discarding retries and callbacks',
          2 : 'Stop campaign accelerating retries and callbacks'
        },

        callerIdBehaviours : {
          0 : 'Fixed',
          1 : 'Dynamic',
          2 : 'DialPlan-Defined'
        },

        timezoneTypes : {
          0 : 'Off',
          1 : 'Simple',
          2 : 'Advanced'
        },

        retryStrategies : {
          0 : 'Reach retry limit for single number',
          1 : 'Reach retry limit before next number',
          2 : 'Round-robin dial with overall retry limit, schedule next number',
          3 : 'Round-robin dial with overall retry limit, schedule next cycle',
          4 : 'Round-robin dial with per cycle retry limit, schedule next cycle',
          5 : 'Retry rules manages cycle'
        },

        callerIdBehaviours : {
          0 : 'Fixed',
          1 : 'Dynamic',
          2 : 'DialPlan-Defined'
        },

        dbListSortDirections : {
          0 : 'Ascending',
          1 : 'Descending'
        },

        replyTypes : {
          0 : 'JSON',
          1 : 'XML',
          2 : 'Plain Text'
        },

        tenantList : tenantFactory.getAll({}, {
          'field' : 'name',
          'direction' : 'asc'
        }, {
          'enabled' : [ {
            term : 'true'
          } ]
        }),

        ivrApplicationList : ivrApplicationFactory.getAll({}, {
          'field' : 'name',
          'direction' : 'asc'
        }, {
          'enabled' : [ {
            term : 'true'
          } ]
        }),

        predefinedValuesTypes : _.invert(constants.predefinedValuesTypes),

        diff : [],

        _getAll : function(page, sort, filters) {
          var promise = $http.get('adminLogs/list', {
            params : {
              "page" : page,
              "sort" : sort,
              "filters" : filters
            }
          }).then(function(response) {
            $log.info(response);
            return response.data;
          });
          return promise;
        },

        _create : function(adminLogs) {
          var promise = $http.post('adminLogs', adminLogs).then(function(response) {
            $log.info(response);
            return response;
          });
          return promise;
        },

        proccessList : function(item, actionTarget, actionType) {
          adminLog = {};
          adminLog['actionValueName'] = item.name;
          adminLog['actionValue'] = item.id;
          adminLog['actionTarget'] = actionTarget;
          adminLog['actionDate'] = _.now();
          adminLog['actionType'] = actionType;
          if (item.tenantListDTO !== undefined)
            adminLog['tenant'] = item.tenantListDTO;
          if (item.version !== undefined)
            adminLog['actionTargetVersion'] = item.version;
          this._create(adminLog);
        },

        check : function(previous, current, actionTarget, deleted, added, edited) {
          this.checkDiff(previous, current, actionTarget, deleted, added, edited);
          if (_.size(this.diff) > 0 || _.isUndefined(previous.id)) {
            this._create(adminLog);
            this.diff = [];
          }
        },

        campaignHasBeenChanged : function(previous, current, actionTarget, deleted, added, edited) {
          this.checkDiff(previous, current, actionTarget, deleted, added, edited);
          return _.size(this.diff) > 0;
        },

        checkDiff : function (previous, current, actionTarget, deleted, added, edited) {
          adminLog = {};
          if (previous.id) {
            adminLog['actionType'] = 1;
            adminLog['actionValueName'] = previous.name;
            adminLog['actionValue'] = previous.id;
            this.compare(previous, current, '');
            this.dncPhoneNumbers(deleted, added, edited);
          } else {
            adminLog['actionType'] = 0;
            adminLog['actionValueName'] = current.name;
            adminLog['actionValue'] = current.id;
          }

          if (current.version !== undefined)
            adminLog['actionTargetVersion'] = current.version;

          if (current.tenantListDTO !== undefined)
            adminLog['tenant'] = current.tenantListDTO;
          else if (current.tenant !== undefined)
            adminLog['tenant'] = {
              id : current.tenant
            };

          adminLog['actionTarget'] = actionTarget;
          adminLog['actionDate'] = _.now();
          adminLog['adminLogDetailsList'] = this.diff;
        },

          /*eski hali*/
          // campaignMustBeRestarted : function () {
          //     for (var i = 0; i < _.size(this.diff); i++) {
          //         if (!(this.diff[i].field.indexOf('startDate') != -1
          //             || this.diff[i].field.indexOf('endDate') != -1
          //             || this.diff[i].field.indexOf('description') != -1
          //             || this.diff[i].field.indexOf('ringTimeout') != -1
          //             || this.diff[i].field.indexOf('skills') != -1
          //             || this.diff[i].field.indexOf('schedule') != -1
          //             || this.diff[i].field.indexOf('maxAnswerRate') != -1
          //             || this.diff[i].field.indexOf('maxAbandonRate') != -1
          //             || (this.diff[i].field.indexOf('enabled') != -1 && this.diff[i].field.indexOf('campaignGroups') == -1)))
          //             return true;
          //     }
          //     return false;
          // },

        campaignMustBeRestarted : function () {
          for (var i = 0; i < _.size(this.diff); i++) {

            var field = this.diff[i].field;

            if (field.indexOf('colSortBy') !== -1
                || field.indexOf('dbListSortDirection') !== -1
                || field.indexOf('sortFields') !== -1) {
              continue;
            }

            if (field.indexOf('col') === 0
                || field.indexOf('db') === 0
                || field.indexOf('timezoneType') !== -1
                || field.indexOf('locations') !== -1) {
              console.debug('Campaign restart required');
              return true;
            }

          }
          console.debug('No campaign restart required');
          return false;
        },

        campaignMustBeReloaded : function () {
          for (var i = 0; i < _.size(this.diff); i++) {
            var field = this.diff[i].field;
            if (field.indexOf('cacheLimit') !== -1
                || field.indexOf('filters') !== -1
                || field.indexOf('filterJoinType') !== -1
                || field.indexOf('colSortBy') !== -1           // ← eklendi
                || field.indexOf('dbListSortDirection') !== -1 // ← eklendi
                || field.indexOf('sortFields') !== -1) {       // ← eklendi
              console.debug('Campaign reload required');
              return true;
            }
          }
          console.debug('No campaign reload required');
          return false;
        },

        createAdminLogForEditedCampaign : function(previous) {
          if (_.size(this.diff) > 0 || _.isUndefined(previous.id)) {
            this._create(adminLog);
            this.diff = [];
          }
        },

        clearDiff : function () {
          this.diff = [];
        },
        
        compare : function(oldObj, newObj, key) {
          var self = this;
          if (!_.isArray(oldObj) && !_.isArray(newObj)) {
            _.mapObject(newObj, function(v, k) {
              if (_.isObject(oldObj[k]) && _.isObject(v)) {
                if (!_.contains(self.ignoreList, k)) {
                  self.compare(oldObj[k], v, key + k);
                }
              } else {
                if (oldObj[k] !== v && !_.contains(self.ignoreList, k)) {
                  if (k === 'payload') {
                    self.comparePayload(oldObj, newObj);
                  } else {
                    detail = {};
                    detail.field = key + k;
                    var newVal = v;
                    var oldVal = oldObj[k];
                    if (_.isDate(v) || _.isDate(oldObj[k])) {
                      if (k === 'from' || k === 'till' || k === 'maxScheduleTime' || k === 'minScheduleTime') {
                        newVal = timeValidatorService.formatTime(v);
                        oldVal = timeValidatorService.formatTime(oldObj[k]);
                      } else if (k === 'startDate' || k === 'endDate') {
                        newVal = new Date(v).toLocaleString();
                        oldVal = new Date(oldObj[k]).toLocaleString();
                      }
                    }

                    var newValue = self.intToString(k, v, newObj);
                    var oldValue = self.intToString(k, oldObj[k], oldObj);

                    detail.newValue = newValue ? newValue : newVal;
                    detail.previousValue = oldValue ? oldValue : oldVal;

                    if ((detail.previousValue || detail.newValue) && (!_.isUndefined(detail.previousValue) && !_.isUndefined(detail.newValue)
                      && !_.isObject(detail.previousValue) && !_.isObject(detail.newValue))) {
                      self.diff.push(detail);
                    }
                  }
                }
              }
            });
          } else {
            detail = {};
            detail.field = key;
            if (_.contains(self.sets, key)) {
              self.prepareDifferenceForArrayWithHardDeletions(oldObj, newObj);
            } else {
              self.prepareDifferenceForArrayWithSoftDeletions(newObj);
            }
            self.compareArrayObjects(oldObj, newObj, key);
          }
        },

        prepareDifferenceForArrayWithHardDeletions : function(oldArr, newArr) {
          var self = this;
          var removed = self.compareSets(newArr, oldArr);
          var added = self.compareSets(oldArr, newArr);
          var intersection = _.intersection(removed, added);
          _.each(intersection, function (item) {
            var removedIndex = removed.indexOf(item);
            var addedIndex = added.indexOf(item);
            removed.splice(removedIndex, 1);
            added.splice(addedIndex, 1);
          });
          self.addItemsDetails(removed, false);
          self.addItemsDetails(added, true);
        },

        addItemsDetails: function(arr, added) {
          var self = this;
          _.chain(arr)
            .compact()
            .forEach(function (item) {
              var detailCopy = angular.copy(detail);
              if (added)
                detailCopy.added = item;
              else
                detailCopy.removed = item;
              self.diff.push(detailCopy);
            });
        },

        prepareDifferenceForArrayWithSoftDeletions : function(arr) {
          var self = this;
          if (!_.isUndefined(arr)) {
            self.addRemovedItemsDetails(arr);
            self.addAddedItemsDetails(arr);
          }
        },

        addRemovedItemsDetails : function(arr) {
          var self = this;
          _.each(self.getRemoved(arr), function (item) {
            var detailCopy = angular.copy(detail);
            detailCopy.removed = self.getRemovedValue(item);
            if (detailCopy.removed !== '')
              self.diff.push(detailCopy);
          });
        },

        addAddedItemsDetails : function(arr) {
          var self = this;
          _.each(self.getAdded(arr), function (item) {
            var detailCopy = angular.copy(detail);
            detailCopy.added = _.compact(self.getAddedValues(item)).toString();
            if (detailCopy.added !== '')
              self.diff.push(detailCopy);
          });
        },

        comparePayload : function(oldObj, newObj) {
          var self = this;
          if (oldObj.freePayloadEnabled === newObj.freePayloadEnabled) {
            if (oldObj.freePayloadEnabled) {
              if (oldObj.payload !== newObj.payload) {
                self.compareStringPayload(oldObj.payload, newObj.payload)
              }
            } else {
              self.compareJSONPayload(oldObj.payload, newObj.payload);
            }
          } else {
            self.compareStringPayload(oldObj.payload, newObj.payload)
          }
        },

        compareStringPayload : function (oldVal, newVal) {
          var self = this;
          detail = {};
          detail.field = 'payload';
          detail.newValue = newVal;
          detail.previousValue = oldVal;
          self.diff.push(detail)
        },

        compareJSONPayload : function(oldVal, newVal) {
          var oldV = JSON.parse(oldVal);
          var newV = JSON.parse(newVal);
          var self = this;
          var added = this.compareSets(oldV, newV);
          var removed = this.compareSets(newV, oldV);
          var intersection = _.intersection(added, removed);
          if (intersection.length > 0) {
            _.each(intersection, function(item) {
              var oldTemp = _.findWhere(oldV, item).value;
              var newTemp = _.findWhere(newV, item).value;
              var removedIndex = removed.indexOf(item);
              var addedIndex = added.indexOf(item);
              removed.splice(removedIndex, 1);
              added.splice(addedIndex, 1);

              var detail = {};
              detail.field = 'payload/' + item + '/value';
              detail.newValue = newTemp;
              detail.previousValue = oldTemp;
              self.diff.push(detail);
            });
          }
          if (added.toString() && removed.toString()) {
            var detail = {};
            detail.field = 'payload';
            detail.added = added.toString();
            detail.removed = removed.toString();
            self.diff.push(detail);
          }
        },

        compareSets : function(firstSet, secondSet) {
          var diff = [];
          _.each(secondSet, function(item) {
            if (!_.findWhere(firstSet, item)) {
              if (item.name) {
                diff.push(item.name);
              } else {
                diff.push(item.key);
              }
            }
          });
          return diff;
        },

        compareArrayObjects : function(oldArr, newArr, key) {
          var self = this;
          var nArr = _.filter(newArr, function(item) {
            return item.id !== undefined;
          });
          _.each(oldArr, function(item) {
            var newArrObj = _.findWhere(nArr, {
              id : item.id
            });
            self.compare(item, newArrObj, key + '/' + (self.getStringIdentifier(item) ? self.getStringIdentifier(item) : item.id) + '/');
          });
        },

        getStringIdentifier : function(obj) {
          var val = '';
          var self = this;
          if (_.has(obj, 'day')) {
            val = self.intToString('day', _.pick(obj, 'day').day, obj);
          } else if (_.has(obj, 'applyTo')) {
            val = self.intToString('applyTo', _.pick(obj, 'applyTo').applyTo, obj);
          } else if (_.has(obj, 'numberToTry')) {
            val = self.intToString('numberToTry', _.pick(obj, 'numberToTry').numberToTry, obj);
          }
          return val;
        },

        intToString : function(key, intVal, obj) {
          var self = this;
          switch (key) {
          case 'day':
            return _.propertyOf(self.days)(intVal);
          case 'condition':
            return _.propertyOf(self.filterConditions)(intVal);
          case 'numberToTry':
            return _.propertyOf(self.numberToTryList)(intVal);
          case 'endListBehaviour':
            return _.propertyOf(self.endListBehaviours)(intVal);
          case 'dbListSortDirection':
            return _.propertyOf(self.dbListSortDirections)(intVal);
          case 'retryStrategy':
            return _.propertyOf(self.retryStrategies)(intVal);
          case 'callerIdBehaviour':
            return _.propertyOf(self.callerIdBehaviours)(intVal);
          case 'timezoneType':
            return _.propertyOf(self.timezoneTypes)(intVal);
          case 'filterJoinType':
            return _.propertyOf(self.filterJoinTypes)(intVal);
          case 'applyTo':
            return _.propertyOf(self.applyToList)(intVal);
          case 'replyType':
            return _.propertyOf(self.replyTypes)(intVal);
          case 'type':
            if (_.has(obj, 'condition') && _.has(obj, 'type')) {
              return _.propertyOf(self.filterTypes)(intVal);
            } else {
              return _.propertyOf(self.campaignTypes)(intVal);
            }
          case 'tenant':
            return self.getTenantNameByTenantId(intVal);
          case 'ivrApplication':
            return self.getIvrApplicationNameById(intVal);
          default:
            return '';
          }
        },

        getAdded : function(arr) {
          return _.filter(arr, function(item) {
            return item.id === undefined;
          });
        },

        getAddedValues : function(item) {
          var added = [];
          var self = this;
          _.filter(item, function (v, k) {
            if (!_.contains(self.ignoreList, k)) {
              if (_.isObject(v) && !_.contains(self.timeValues, k)) {
                added.push(self.getIdentity(v));
              } else if (_.contains(self.timeValues, k)) {
                added.push(k + ' = ' + timeValidatorService.formatTime(v));
              } else if (k === 'startDate' || k === 'endDate') {
                added.push(k + ' = ' + new Date(v).toLocaleString());
              } else {
                if (k === 'day') {
                  added.push(k + ' = ' + _.propertyOf(self.days)(v));
                } else if (k === 'type') {
                  added.push(k + ' = ' + _.propertyOf(self.predefinedValuesTypes)(v));
                } else {
                  added.push(k + ' = ' + v);
                }
              }
            }
          });
          return added;
        },

        getRemovedValue : function(item) {
          return item.id
            ? ('id = ' + item.id)
            : '';
        },

        getRemoved : function(arr) {
          return _.where(arr, {deleted: true});
        },

        getIdentity : function(obj) {
          if (!obj) {
            return '';
          }
          var identifier = '';
          if (_.has(obj, 'name')) {
            identifier = 'name = ' + obj.name;
          } else {
            identifier = obj.id;
          }
          return identifier;
        },

        dncPhoneNumbers : function(deleted, added, edited) {
          var del = [];
          var add = [];
          var self = this;
          _.each(deleted, function(item) {
            del.push(_.pick(item, 'phoneNumber').phoneNumber);
          });
          _.each(added, function(item) {
            add.push(_.pick(item, 'phoneNumber').phoneNumber);
          });

          var removedArray = del.toString();
          var addedArray = add.toString();
          if (removedArray || addedArray) {
            detail = {};
            detail.field = 'phoneNumber';
            detail.added = addedArray;
            detail.removed = removedArray;
            self.diff.push(detail);
          }
        },

        getTenantNameByTenantId : function(id) {
          if (id && this.tenantList) {
            var tenant = _.filter(this.tenantList.$$state.value.data, function(item) {
              return item.id === id;
            });
            if (tenant[0]) {
              return tenant[0].name;
            }
          }
        },

        getTenantList : function() {
          if (!this.tenantList) {
            tenantFactory.getAll({}, {
              'field' : 'name',
              'direction' : 'asc'
            }, {
              'enabled' : [ {
                term : 'true'
              } ]
            }).then(function(data) {
              this.tenantList = data.data;
            });
          }
        },

        getIvrApplicationList : function() {
          if (!this.ivrApplicationList) {
            ivrApplicationFactory.getAll({}, {
              'field' : 'name',
              'direction' : 'asc'
            }, {
              'enabled' : [ {
                term : 'true'
              } ]
            }).then(function(data) {
              this.ivrApplicationList = data.data;
            });
          }
        },

        getIvrApplicationNameById : function(id) {
          this.getIvrApplicationList();
          if (id && this.ivrApplicationList) {
            var ivrApplication = _.filter(this.ivrApplicationList.$$state.value.data, function(item) {
              return item.id === id;
            });
            if (ivrApplication[0]) {
              return ivrApplication[0].name;
            }
          }
        },

        enableDisableFromList : function(actionTarget, items, enabled) {
          var self = this;
          _.each(items, function(item) {
            if (enabled !== item.enabled) {
              adminLog = {};
              details = {};
              adminLog['actionValueName'] = item.name;
              adminLog['actionValue'] = item.id;
              adminLog['actionTarget'] = actionTarget;
              adminLog['actionDate'] = _.now();
              adminLog['actionType'] = 1;
              if (item.tenantListDTO !== undefined)
                adminLog['tenant'] = item.tenantListDTO;
              if (item.version !== undefined)
                adminLog['actionTargetVersion'] = item.version;
              details['field'] = 'enabled';
              details['previousValue'] = item.enabled;
              details['newValue'] = enabled;
              self.diff.push(details);
              adminLog['adminLogDetailsList'] = self.diff;
              self._create(adminLog);
              self.diff = [];
            }
          });
        }

      });

    } ]);
