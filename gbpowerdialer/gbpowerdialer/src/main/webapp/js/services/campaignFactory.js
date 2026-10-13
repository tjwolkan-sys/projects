/**
 * Factory for CRUD operations on campaign
 */
app.factory('campaignFactory', ['$http', '$log', 'baseFactory', 'campaignScheduleFactory', '$q', 'csrfService', function ($http, $log, baseFactory, campaignScheduleFactory, $q, csrfService) {

  return angular.extend(Object.create(baseFactory.prototype), {

    _save: function (campaign) {
      if (campaign != null && campaign.id != null) {
        var urlPrefix;
        if (campaign.mustBeRestarted) {
          urlPrefix = 'campaign/saveAndRestartRunningCampaign/';
        } else if (campaign.mustBeReloaded) {
          urlPrefix = 'campaign/saveAndReloadRunningCampaign/';
        } else {
          urlPrefix = 'campaign/';
        }
        return $http.put(urlPrefix + campaign.id + '/response', campaign).then(function (response) {
          return response;
        });
      }
    },

    _create: function (campaign) {
      var promise = $http.post('campaign/response', campaign).then(function (response) {
        return response;
      });
      return promise;
    },

    _clone: function (campaign) {
      var promise = $http.post('campaign/clone/' + campaign.id + '/response', campaign.name).then(function (response) {
        return response;
      });
      return promise;
    },

    _getAll: function (page, sort, filters) {
      var promise = $http.get('campaign/list', {
        params: {
          "page": page,
          "sort": sort,
          "filters": filters
        }
      }).then(function (response) {
        return response.data;
      });
      return promise;
    },

    _get: function (id) {
      var promise = $http.get('campaign/' + id).then(function (response) {
        return response.data;
      });
      return promise;
    },

    _remove: function (ids) {
      var promise = $http.delete('campaign/batch', {
        params: {ids: ids.toString()}
      }).then(function (response) {
        return response.data;
      });
      return promise;
    },

    _createNew: function (campaign) {
      campaign.previewTimeout = 0;
      campaign.previewRelax = 0;
      campaign.concurrentLines = 0;
      campaign.callsPerSecond = 0;
      campaign.ringTimeout = 30;
      campaign.abandonTimeout = 0;
      campaign.retryOveralAttempt = 5;
      campaign.macroInterval = 24;
      campaign.macroMaxAttempt = 5;
      campaign.retryRatio = 100;
      campaign.retryDelay = 10;
      campaign.dialingBoost = 1;
      campaign.filterJoinType = 0;
      campaign.cacheLimit = 1000;
      campaign.endListBehaviour = 0;
      campaign.dbListSortDirection = 0;
      campaign.timezoneType = 0;
      campaign.callerIdBehaviour = 2;
      campaign.fctRoutingEnabled = false;
      // gamma_plus NOT NULL; isaretlenmemis checkbox POST'a hic girmez, varsayilan sart.
      campaign.gammaPlus = false;
      campaign.appointerReset = true;
      campaign.appointerResetTimeout = 3600;
      campaign.appointerResetStrategy = 0;
      campaign.agentCbRescheduleDelayMin = 20;
      campaign.agentCbConvertAfterAttempts = 3;

      var dateNow = new Date();
      campaign.startDate = dateNow.getTime();
      dateNow.setFullYear(dateNow.getFullYear() + 1);
      campaign.endDate = dateNow.getTime();

      campaign.schedules = [];

      campaign.schedules.push(campaignScheduleFactory.createDefaultSchedule(0));
      campaign.schedules.push(campaignScheduleFactory.createDefaultSchedule(1));
      campaign.schedules.push(campaignScheduleFactory.createDefaultSchedule(2));
      campaign.schedules.push(campaignScheduleFactory.createDefaultSchedule(3));
      campaign.schedules.push(campaignScheduleFactory.createDefaultSchedule(4));
      campaign.schedules.push(campaignScheduleFactory.createDefaultSchedule(5));
      campaign.schedules.push(campaignScheduleFactory.createDefaultSchedule(6));

      campaign.blackoutSchedules = []

      return campaign;
    },

    checkConnection: function (campaign) {
      var promise = $http.post('campaign/checkConnection', {}, {
        params: {
          url: campaign.dbConnectionString,
          user: (campaign.dbUsername == null ? '' : campaign.dbUsername),
          password: (campaign.dbPassword == null ? '' : campaign.dbPassword),
          id: (campaign.id == null ? '' : campaign.id)
        }
      }).then(function (response) {
        return response.data;
      });
      return promise;
    },

    connect: function (campaign) {
      var promise = $http.post('campaign/connect', {}, {
        params: {
          url: campaign.dbConnectionString,
          user: (campaign.dbUsername == null ? '' : campaign.dbUsername),
          password: (campaign.dbPassword == null ? '' : campaign.dbPassword),
          id: (campaign.id == null ? '' : campaign.id)
        }
      }).then(function (response) {
        return response.data;
      });
      return promise;
    },

    disconnect: function (id) {
      var promise = $http.post('campaign/disconnect/' + id).then(function (response) {
        return response.data;
      });
      return promise;
    },

    getTables: function (campaign) {
      var promise = $http.post('campaign/tables', {}, {
        params: {
          url: campaign.dbConnectionString,
          user: (campaign.dbUsername == null ? '' : campaign.dbUsername),
          password: (campaign.dbPassword == null ? '' : campaign.dbPassword),
          id: (campaign.id == null ? '' : campaign.id)
        }
      }).then(function (response) {
        return response.data;
      });
      return promise;
    },

    getTableColumns: function (table, campaign) {
      var promise = $http.post('campaign/columns', {}, {
        params: {
          table: table,
          url: campaign.dbConnectionString,
          user: (campaign.dbUsername == null ? '' : campaign.dbUsername),
          password: (campaign.dbPassword == null ? '' : campaign.dbPassword),
          id: (campaign.id == null ? '' : campaign.id)
        }
      }).then(function (response) {
        return response.data;
      });
      return promise;
    },

    getSourceTableColumns: function (campaignId) {
      var promise = $http.get('campaign/sourceTable/columns?id=' + campaignId).then(function (response) {
        return response.data;
      });
      return promise;
    },

    setModifiedCollections: function (campaign) {
      if (campaign) {
        var self = this;
        campaign.schedules = this.filterCollection(campaign.schedules);
        campaign.phoneNumberColumns = this.filterCollection(campaign.phoneNumberColumns);
        campaign.sortFields = this.filterCollection(campaign.sortFields);
        campaign.campaignGroups = this.filterCollection(campaign.campaignGroups);
        campaign.filters = this.filterCollection(campaign.filters);
        campaign.scheduleAutomations = this.filterCollection(campaign.scheduleAutomations);
        campaign.blackoutSchedules = this.filterCollection(campaign.blackoutSchedules);

        if (campaign.resultCodeSchedules) {
          campaign.resultCodeSchedules.forEach(function (item, i, arr) {
            item.resultCodeScheduleAdvanceds = self.filterCollection(item.resultCodeScheduleAdvanceds);
          });
          campaign.resultCodeSchedules = this.filterCollection(campaign.resultCodeSchedules);
        }

        return campaign;
      }
    },

    filterCollection: function (collection) {
      if (collection) {
        return $.grep(collection, function (item, index) {
          return item.changed;
        });
      }
    },

    _hideTenantColumn: function (columns) {
      return columns;
    },

    action: function (campaignId, type) {
      var promise = $http.post('campaign/action', {}, {
        params: {
          campaignId: campaignId,
          type: type
        }
      }).then(function (response) {
        return response.data;
      });
      return promise;
    },

    realtimeInfo: function (ids) {
      var promise = $http.get('campaign/realtimeInfo', {params: {"ids": ids.toString()}}).then(function (response) {
        return response.data;
      });
      return promise;
    },

    filtersTest: function (campaign) {
      var promise = $http.post('campaign/filtersTest', campaign).then(function (response) {
        return response.data;
      });
      return promise;
    },

    filterTest: function (filter) {
      var promise = $http.post('campaign/filterTest', filter).then(function (response) {
        return response.data;
      });
      return promise;
    },

    startUploadWithFile: function (campaign, parameters, multipartFile) {
      var formData = new FormData();
      var blobParameters = new Blob([JSON.stringify(parameters)], {'type': 'application/json'});
      formData.append("file", multipartFile);
      formData.append("parameters", blobParameters);
      var requestDeferred = $q.defer();
      var xhr = new XMLHttpRequest();
      xhr.open('POST', 'campaign/upload/start/' + campaign.id, true);
      xhr.setRequestHeader(csrfService.settings.csrfTokenHeader, csrfService.token);
      xhr.onreadystatechange = function () {
        if (xhr.readyState !== 4) return;
        if (xhr.status !== 200) {
          requestDeferred.reject(xhr);
        } else {
          requestDeferred.resolve(JSON.parse(xhr.responseText));
        }
      };
      xhr.upload.onprogress = function (progressEvent) {
        requestDeferred.notify(progressEvent)
      };
      xhr.send(formData);
      return requestDeferred.promise;
    },

    processUpload: function (campaign, processParameters) {
      return $http.post('campaign/upload/process/' + campaign.id, processParameters).then(function (response) {
        return response.data;
      });
    },

    validateUploadedFile: function (campaign, remappedColumns) {
      return $http.post('campaign/upload/process/' + campaign.id + '/validate', remappedColumns, {skipErrorRedirect: true}).then(function (response) {
        return response.data;
      });
    },

    checkUploadProgress: function (campaign) {
      return $http.get('campaign/upload/process/' + campaign.id + '/progress', {skipErrorRedirect: true}).then(function (response) {
        if (response.data === '')
          return Promise.reject();
        return response.data;
      })
    },

    getRowsForPreview: function (campaign) {
      return $http.get('campaign/upload/process/' + campaign.id + '/previewRows', {skipErrorRedirect: true}).then(function (response) {
        if (response.data === '')
          return Promise.reject();
        return response.data;
      })
    },

    terminateUpload: function(campaign) {
      return $http.post('campaign/upload/process/' + campaign.id + '/terminate', {}, {
        skipErrorRedirect: true,
        showUIBlock: false
      }).then(function(response) {
        return response.data;
      });
    },

    getCampaignsLocations: function(campaignId) {
      return $http.get('campaign/' + campaignId + '/locations').then(function(response) {
        return response.data;
      });
    },

    getCampaignsDNCs: function(campaignId) {
      return $http.get('campaign/' + campaignId + '/dncs').then(function(response) {
        return response.data;
      });
    },

    getCampaignsLSCs: function(campaignId) {
      return $http.get('campaign/' + campaignId + '/lscs').then(function(response) {
        return response.data;
      });
    },

    getCampaignsSkills: function(campaignId) {
      return $http.get('campaign/' + campaignId + '/skills').then(function(response) {
        return response.data;
      });
    },

    recalculatePriorities: function (id) {
      return  $http.post('campaign/recalculatePriorities/' + id).then(function (response) {
        return response.data;
      });
    },

    updateLscPriorities: function(campaignId, lscIds) {
      return $http.put('campaign/' + campaignId + '/lscs/priority', lscIds)
          .then(function(response) {
            return response.data;
          });
    },

  });
}]);
