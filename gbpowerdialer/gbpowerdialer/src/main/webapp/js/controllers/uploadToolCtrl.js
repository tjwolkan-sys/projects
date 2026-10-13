app.controller('uploadToolCtrl', ['$scope', 'campaignFactory', 'campaign', '$q', 'blockUI', '$modalInstance', '$modal', 'alertService', '$filter','uiGridConstants',
  function ($scope, campaignFactory, campaign, $q, blockUI, $modalInstance, $modal, alertService, $filter, uiGridConstants) {

    var vm = this;
    vm.startUploadParameters = {
      firstRowColumnNames: true,
      updateExistingData: vm.updateExistingData,
      dbIdColumnName: vm.dbIdColumnName
    };
    vm.processUploadParameters = {
      antiDuplication: '0',
      remappedColumns: {},
      hiddenColumns: []
    };
    vm.gridColumns = [];
    vm.campaignDbColumns = [];
    vm.campaignDbColumnForDropdown = [];
    vm.fileSelected = false;
    vm.lastProgressResponse = {
      processed: undefined,
      failed: undefined,
      total: undefined,
      uploadedPercent: undefined,
      failedPercent: undefined,
      failedRowsFilenameLink: undefined
    };
    var monitoringInterval = undefined;

    $modalInstance.opened.then(function () {
      blockUI.start();
      var loadRowsPromise = loadCampaignSourceTableColumns().then(function () {
        return campaignFactory.getRowsForPreview(campaign)
      }).then(function (data) {
        handlePreviewRows(data)
      });
      var loadProgressPromise = loadProgress(campaign).then(function (progressResponse) {
        if (progressResponse) {
          startProgressMonitoring();
        }
      });
      $q.all([loadRowsPromise, loadProgressPromise]).finally(function () {
        vm.gridApi.core.notifyDataChange('all');
        blockUI.stop();
      });
    });
    $modalInstance.result.finally(function () {
      stopProgressMonitoring();
    });

    vm.gridOptions = {
      onRegisterApi: function (gridApi) {
        vm.gridApi = gridApi;
        gridApi.core.on.columnVisibilityChanged($scope, function (changedColumn) {
          if (!changedColumn.visible) {
            var columnName = changedColumn.colDef.name;
            vm.processUploadParameters.hiddenColumns.push(columnName);
            if (vm.processUploadParameters.remappedColumns.hasOwnProperty(columnName)) {
              delete vm.processUploadParameters.remappedColumns[columnName];
            }
          }
        });
      },
      columnDefs: vm.gridColumns
    };

    vm.uploadSelectedFile = function () {
      vm.uploadFile(vm.file);
    };

    function loadCampaignSourceTableColumns() {
      return campaignFactory.getSourceTableColumns(campaign.id).then(function (campaignColumns) {
        vm.campaignDbColumns = campaignColumns;
        vm.campaignDbColumnForDropdown.splice(0, vm.campaignDbColumnForDropdown.length);
        for (var index in vm.campaignDbColumns) {
          if (vm.campaignDbColumns.hasOwnProperty(index)) {
            var campaignDbColumn = vm.campaignDbColumns[index];
            vm.campaignDbColumnForDropdown.push({
              id: campaignDbColumn.fieldName,
              fieldName: campaignDbColumn.fieldName
            })
          }
        }
      })
    }

    function handlePreviewRows(previewData) {
      prepareGridColumns(Object.keys(previewData[0]));
      var columnNamesObject = {};
      for (var columnName in previewData[0]) {
        if (previewData[0].hasOwnProperty(columnName)) {
          columnNamesObject[columnName] = columnName;
          vm.processUploadParameters.remappedColumns[columnName] = columnName;
        }
      }
      vm.gridOptions.data = previewData;
      loadProgress(campaign);
    }

    vm.uploadFile = function (file) {
      if (!vm.isFileUploaded()) {
        var loadColumnNamesPromise = loadCampaignSourceTableColumns();
        blockUI.start();
        var uploadPromise = campaignFactory.startUploadWithFile(campaign, vm.startUploadParameters, file).then(function (previewData) {
          vm.fileSelected = true;
          handlePreviewRows(previewData);
          blockUI.stop();
        }, function (reason) {
          vm.file = undefined;
          blockUI.stop();
        }, function (progressEvent) {
          blockUI.message(Math.ceil((progressEvent.loaded / progressEvent.total) * 100) + '%');
        });
        $q.all([loadColumnNamesPromise, uploadPromise]).then(function () {
          vm.gridApi.core.notifyDataChange('all');
        })
      }
    };

    vm.validateUploadedFile = function () {
      var validationDefer = $q.defer();
      campaignFactory.validateUploadedFile(campaign, vm.processUploadParameters.remappedColumns).then(function (validationResult) {
        var dataToShow = [];
        for (var rowIndex in validationResult) {
          if (validationResult.hasOwnProperty(rowIndex)) {
            dataToShow.push({
              'rowIndex': rowIndex,
              'validationMessage': validationResult[rowIndex].messages.join(', ')
            });
          }
        }
        if (dataToShow.length === 0) {
          validationDefer.resolve();
        } else {
          validationDefer.reject();
          var validationResultCtrl = function ($scope, $modalInstance, dataToShow) {
            $scope.gridOptions = {
              columnDefs: [
                {
                  displayName: $filter('i18n')('uploadTool.validationResult.rowIndex'),
                  enableHiding: true,
                  enableColumnMenu: false,
                  field: 'rowIndex',
                  minWidth: 100,
                  width: 100
                }, {
                  displayName: $filter('i18n')('uploadTool.validationResult.validationMessage'),
                  enableHiding: true,
                  enableColumnMenu: false,
                  field: 'validationMessage',
                  minWidth: 400,
                  width: 1000
                }
              ],
              data: dataToShow
            };
            $scope.close = function () {
              $modalInstance.dismiss('cancel');
            };
          };
          $modal.open({
            backdrop: 'static',
            controller: validationResultCtrl,
            templateUrl: 'pages/uploadToolValidationResult.html',
            resolve: {
              dataToShow: function () {
                return dataToShow;
              }
            }
          });
        }
      });
      return validationDefer.promise;
    };

    vm.processUpload = function () {
      if (isRemappedColumnNamesContainsDuplication()) {
        alertService.add('danger', $filter('i18n')('uploadTool.validationResult.duplicatesColumnsMapping'));
        return;
      }
      blockUI.start();
      vm.validateUploadedFile().then(function () {
        campaignFactory.processUpload(campaign, vm.processUploadParameters).then(function () {
          startProgressMonitoring();
        }).finally(function () {
          blockUI.stop();
        });
      }, function (reason) {
        blockUI.stop();
      });
    };

    var isRemappedColumnNamesContainsDuplication = function () {
      var remappedColumnsNames = new Set();
      for (var originalColumnName in vm.processUploadParameters.remappedColumns) {
        if (vm.processUploadParameters.remappedColumns.hasOwnProperty(originalColumnName) && '$$hashKey' !== originalColumnName) {
          var remappedColumnName = vm.processUploadParameters.remappedColumns[originalColumnName];
          if(remappedColumnName !== '') {
            if (remappedColumnsNames.has(remappedColumnName)) {
              return true;
            }
            remappedColumnsNames.add(remappedColumnName);
          }
        }
      }
      return false;
    };

    vm.isFileUploaded = function () {
      return vm.lastProgressResponse.total;
    };

    vm.isUploadInProgress = function () {
      return vm.lastProgressResponse.processed && !vm.isFinished() && !vm.isTerminated();
    };

    vm.isFinished = function () {
      return vm.lastProgressResponse.processed && vm.lastProgressResponse.processed === vm.lastProgressResponse.total;
    };

    vm.isTerminated = function () {
      return vm.terminated;
    };

    var startProgressMonitoring = function () {
      monitoringInterval = setInterval(function () {
        loadProgress(campaign).then(function (progressResponse) {
          if (!progressResponse.processed || progressResponse.processed === progressResponse.total) {
            stopProgressMonitoring();
          }
        }, function (reason) {
          stopProgressMonitoring();
        });
      }, 1000);
    };

    var stopProgressMonitoring = function () {
      clearInterval(monitoringInterval);
    };

    function loadProgress(campaign) {
      return campaignFactory.checkUploadProgress(campaign).then(handleProgressResponse);
    }

    vm.closeWindow = function () {
      $modalInstance.dismiss('cancel');
    };

    vm.terminateUpload = function () {
      campaignFactory.terminateUpload(campaign).then(handleProgressResponse).then(function () {
        vm.terminated = true;
      });
    };

    vm.isRemappedColumnsValid = function () {
      for (var originalColumnName in vm.processUploadParameters.remappedColumns) {
        if (vm.processUploadParameters.remappedColumns.hasOwnProperty(originalColumnName) && '$$hashKey' !== originalColumnName) {
          var remappedColumnName = vm.processUploadParameters.remappedColumns[originalColumnName];
          if (remappedColumnName !== '' && !checkDbColumnExistence(remappedColumnName) && !vm.processUploadParameters.hiddenColumns.includes(remappedColumnName)) {
            return false;
          }
        }
      }
      return true;
    };

    var handleProgressResponse = function (progressResponse) {
      if (progressResponse) {
        vm.lastProgressResponse = progressResponse;
        var processedPercent = Math.round((progressResponse.processed - 1) / (progressResponse.total - 1) * 100);
        vm.lastProgressResponse.failedPercent = Math.round(progressResponse.failed / (progressResponse.total - 1)  * 100);
        vm.lastProgressResponse.uploadedPercent = processedPercent - vm.lastProgressResponse.failedPercent;
      } else {
        vm.lastProgressResponse = {
          processed: undefined,
          failed: undefined,
          total: undefined,
          uploadedPercent: undefined,
          failedPercent: undefined,
          failedRowsFilenameLink: undefined
        }
      }
      return progressResponse;
    };

    var prepareGridColumns = function (columnNames) {
      vm.gridColumns.length = 0;
      for (var i = 0; i < columnNames.length; i++) {
        vm.gridColumns.push({
          field: columnNames[i],
          displayName: columnNames[i],
          enableSorting: false,
          headerCellClass: highlightHeadersDbMatch,
          minWidth: 100,
          menuItems: createMenuItems(),
          cellTemplate: '<div class="ui-grid-cell-contents">{{row.entity["' + columnNames[i] + '"]}}</div>'
        });
      }
    };

    var createMenuItems = function () {
      var menu = [];
      for (var i = 0; i < vm.campaignDbColumnForDropdown.length; i++) {
        menu.push({
          title: vm.campaignDbColumnForDropdown[i].fieldName,
          action: function($event) {
            var newColumn = $event.target.innerText.trim();
            var mappingKey = findColumnMappingKey(this.context.col.colDef.field);
            vm.processUploadParameters.remappedColumns[mappingKey] = newColumn;
            this.context.col.colDef.field = newColumn;
            this.context.col.colDef.displayName = newColumn;
            vm.gridApi.core.notifyDataChange(uiGridConstants.dataChange.COLUMN);
          },
        })
      }
      return menu;
    };

    var findColumnMappingKey = function (oldColumn) {
      for(var column in vm.processUploadParameters.remappedColumns) {
        if (vm.processUploadParameters.remappedColumns[column] === oldColumn) {
          return column;
        }
      }
    };

    var highlightHeadersDbMatch = function (grid, row, col, rowRenderIndex, colRenderIndex) {
        var headerValue = col.field;
        if (!headerValue || checkDbColumnExistence(headerValue))
          return 'upload-tool-header-match-db';
        else
          return 'upload-tool-header-not-match-db'
    };

    var checkDbColumnExistence = function (columnNameToCheck) {
      var anyMatch = vm.campaignDbColumns.filter(function (campaignDbColumn) {
        return campaignDbColumn.fieldName === columnNameToCheck;
      })[0];
      return !!anyMatch;
    }

  }]);
