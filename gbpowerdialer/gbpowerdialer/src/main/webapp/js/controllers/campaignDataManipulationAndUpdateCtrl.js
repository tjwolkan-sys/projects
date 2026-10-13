app.controller('campaignDataManipulationAndUpdateCtrl',
    ['$http', '$scope', '$filter', 'i18nService', 'locMsg', function ($http, $scope, $filter, i18nService, locMsg) {
    const ctrl = this;
    ctrl.locMsg = locMsg;
    ctrl.operations = [];
    ctrl.campaigns = [];
    ctrl.allCampaigns = [];
    ctrl.processTypes = [
        { value: 'ACTIVATE_RECORD', label: 'Kayıt Aktife Çekme' },
        { value: 'ADD_CALL_AS_PRIORITY', label: 'Arama Öncelik Ekleme' },
        { value: 'REMOVE_CALL_FROM_PRIORITY', label: 'Arama Öncelik Silme' },
        { value: 'CONTACT_LIST_BULK_UPDATE', label: 'Kontak Listesi Toplu Güncelleme' },
        { value: 'DELETE_RECORD', label: 'Kayıt Eleme' }
    ];

    ctrl.selectedTenant = null;
    ctrl.selectedCampaign = null;
    ctrl.selectedProcess = null;

    ctrl.filterRows = [];
    ctrl.columns = [];
    ctrl.logicOperator = 'AND';
    ctrl.dataRows = [];
    ctrl.selectAll = false;
        ctrl.init = function () {
            ctrl.initializeData();
            ctrl.activeTab = 'records';

            ctrl.gridOptions = {
            enableSorting: true,
            enableRowHeaderSelection: true,
            enableFiltering: false,
            enableColumnResizing: true,
            enablePaginationControls: true,
            paginationPageSizes: [10, 25, 50],
            paginationPageSize: 10,
            enableRowSelection: true,
            enableSelectAll: true,
            multiSelect: true,
            columnDefs: [],
            data: [],
            onRegisterApi: function(gridApi) {
                ctrl.recordGridApi = gridApi;
            }
        };



            ctrl.userResultCodeGridOptions = {
                enableSorting: true,
                enableFiltering: false,
                enableColumnResizing: true,
                enablePaginationControls: true,
                paginationPageSizes: [10, 25, 50],
                paginationPageSize: 10,
                enableRowSelection: true,
                enableSelectAll: true,
                multiSelect: true,
                columnDefs: [],
                data: [],
                onRegisterApi: function(gridApi) {
                    ctrl.userRcGridApi = gridApi;
                }

            };


            ctrl.telephonyResultCodeGridOptions = {
                enableSorting: true,
                enableFiltering: false,
                enableColumnResizing: true,
                enablePaginationControls: true,
                paginationPageSizes: [10, 25, 50],
                paginationPageSize: 10,
                enableRowSelection: true,
                enableSelectAll: true,
                multiSelect: true,
                columnDefs: [],
                data: [],
                onRegisterApi: function(gridApi) {
                    ctrl.telephonyRCGridApi = gridApi;
                }

            };



        ctrl.testGridOptions = {
            enableSorting: true,
            enableFiltering: false,
            enableColumnResizing: true,
            enablePaginationControls: true,
            paginationPageSizes: [5, 10],
            paginationPageSize: 5,
            columnDefs: [],
            data: []
        };
    };

        $scope.$watch(function () {
            return ctrl.selectedCampaign;
        }, function (newVal) {
            if (newVal) {
                $http.get('campaign-records/columns/' + newVal).then(function (resp) {
                    ctrl.columns = resp.data;
                }).catch(function (err) {
                    console.error("Colunms error:", err);
                });

                if (ctrl.filterTemplates[newVal] && ctrl.filterTemplates[newVal].length > 0) {
                    ctrl.filterRows = angular.copy(ctrl.filterTemplates[newVal]);
                } else {
                    ctrl.filterRows = [];
                }
                const selectedCampaign = ctrl.campaignMap[newVal];
                ctrl.isPriorityCampaign = selectedCampaign?.is_priority === true;
            }
        });

    i18nService.setCurrentLang(locMsg['lang.id']);
        ctrl.initializeData = function () {
            $http.get("campaign-records/initialize-data").then(
                function (resp) {
                    ctrl.operations = resp.data.tenantsList;
                    ctrl.allCampaigns = resp.data.campaignsByTenantList;

                    ctrl.filterTemplates = {};
                    ctrl.campaignMap = {};

                    Object.values(ctrl.allCampaigns).forEach(campaignList => {
                        campaignList.forEach(campaign => {
                            ctrl.filterTemplates[campaign.id] = campaign.filters || [];
                            ctrl.campaignMap[campaign.id] = campaign;
                        });
                    });
                },
                function (err) {
                    console.error("❌ Yükleme hatası:", err);
                }
            );
        };

    ctrl.loadCampaignsByTenant = function () {
        if (!ctrl.selectedTenant) {
            ctrl.campaigns = [];
        }

        if (ctrl.allCampaigns != null) {
            ctrl.campaigns = ctrl.allCampaigns[ctrl.selectedTenant];
        }
    };

    ctrl.updateFieldType = function (row) {
        const selected = ctrl.columns.find(col => col.name === row.field);
        row.fieldType = selected ? selected.type : 'STRING';
    };

    ctrl.addFilter = function () {
        ctrl.filterRows.push({ field: '', condition: 'EQUAL', value: '' });
    };

    ctrl.clearFilters = function () {
        ctrl.filterRows = [];
    };

    ctrl.setLogic = function(type) {
        ctrl.logicOperator = type;
    };

    ctrl.isNullCondition = function (condition) {
        return condition === 'ISNULL' || condition === 'ISNOTNULL';
    };

    ctrl.updateCondition = function (row) {
        if (ctrl.isNullCondition(row.condition)) {
            row.value = null;
        }
    };

    ctrl.toggleSelectAll = function () {
        ctrl.dataRows.forEach(row => row.selected = ctrl.selectAll);
    };

    ctrl.testFilters = function () {
        debugger;

        const cleanedFilterRows = ctrl.filterRows.map(f => {
            return {
                ...f,
                value: ctrl.isNullCondition(f.condition) ? null : f.value
            };
        });

        const payload = {
            campaignId: ctrl.selectedCampaign,
            logicOperator: ctrl.logicOperator,
            filterRows: cleanedFilterRows
        };


        if (!ctrl.selectedTenant || !ctrl.selectedCampaign) {
            showWarningPopup(ctrl.locMsg['campaignDataManipulation.validation.filterTestNoTenantOrCampaign']);
            return;
        }

        if (ctrl.filterRows.length === 0) {
            showWarningPopup(ctrl.locMsg['campaignDataManipulation.validation.filterTestEmpty']);
            return;
        }


        $http.post('campaign-records/filter-test', payload)
            .then(function (response) {
                const data = response.data;
                ctrl.testGridOptions.data = data.rows || [];
                ctrl.testResultCount = data.totalCount || 0;
                if (data.rows && data.rows.length > 0) {
                    const columns = Object.keys(data.rows[0]).map(key => ({
                        field: key,
                        displayName: key
                    }));
                    ctrl.testGridOptions.columnDefs = columns;
                }

                $('#filterTestModal').modal('show');
            })
            .catch(function (error) {
                alert("Filtre testi başarısız.");
                console.error("❌ Test Hatası:", error);
            });
    };

        ctrl.setGridData = function (dataArray, gridOptions, process) {
            const GRID_TEMPLATES = {
                records: [
                    { field: 'ListID',       displayName: gridLabel('ListID') },
                    { field: 'StatueLabel',  displayName: gridLabel('Statue') },
                    { field: 'Record Count', displayName: gridLabel('Record Count') }
                ],
                userRc: [
                    { field: 'result_code_text', displayName: gridLabel('result_code_text') },
                    { field: 'statusCode',       displayName: gridLabel('statusCode') },
                    { field: 'record_count',     displayName: gridLabel('record_count') }

                ],
                telephonyRc: [
                    { field: 'result_code_text', displayName: gridLabel('result_code_text') },
                    { field: 'statusCode',       displayName: gridLabel('statusCode') },
                    { field: 'record_count',     displayName: gridLabel('record_count') }

                ]
            };

            if (!Array.isArray(dataArray) || dataArray.length === 0) {
                gridOptions.columnDefs = GRID_TEMPLATES[process] || [];
                gridOptions.data = [];
                return;
            }

            const dataKeys = Object.keys(dataArray[0]);


            const template = GRID_TEMPLATES[process];
            const columnDefs = template
                ? template.filter(c => dataKeys.includes(c.field))
                : dataKeys.map(key => ({
                    field: key,
                    displayName: gridLabel(key)
                }));

            if (process === 'records') {
                dataArray = dataArray.map(row => {
                    if ('Statue' in row && !('StatueLabel' in row)) {
                        row.StatueLabel = row.Statue ? t(row.Statue) : '';
                    }
                    return row;
                });
            }
            if (process === 'userRc' || process === 'telephonyRc') {
                dataArray = localizeRcRows(dataArray);
            }

            gridOptions.columnDefs = columnDefs;
            gridOptions.data = dataArray;
        };

        function localizeRcRows(rows) {
            return (rows || []).map(r => {

                if (!r._statusCodeOriginal && r.statusCode) r._statusCodeOriginal = r.statusCode;


                var groupCode = r._statusCodeOriginal || r.statusCode || r.status_code_description || '';


                if (groupCode) {
                    var groupKey = 'campaignDataManipulation.rcGroup.' + groupCode;
                    var localizedGroup = t(groupKey);
                    if (localizedGroup && localizedGroup !== groupKey) {
                        r.statusCode = localizedGroup;
                    }
                }


                if (typeof r.result_code_text === 'string' && /^SUM\s+/i.test(r.result_code_text)) {
                    var sumPrefix = t('campaignDataManipulation.sum.prefix') || 'SUM ';
                    var groupForSum = r.status_code_description || groupCode || r.result_code_text.replace(/^SUM\s+/i, '');
                    var groupKeyForSum = 'campaignDataManipulation.rcGroup.' + groupForSum;
                    var localizedGroupForSum = t(groupKeyForSum);
                    r.result_code_text = sumPrefix + (localizedGroupForSum && localizedGroupForSum !== groupKeyForSum
                        ? localizedGroupForSum
                        : groupForSum);
                }

                return r;
            });
        }



        ctrl.fetchRecords = function () {
        if (!ctrl.selectedTenant || !ctrl.selectedCampaign) {
            showWarningPopup(ctrl.locMsg['campaignDataManipulation.validation.missingTenantOrCampaignForFetch']);
            return;
        }


        const cleanedFilterRows = ctrl.filterRows.map(f => {
            return {
                ...f,
                value: ctrl.isNullCondition(f.condition) ? null : f.value
            };
        });


        const payload = {
            campaignId: ctrl.selectedCampaign,
            logicOperator: ctrl.logicOperator,
            filterRows: cleanedFilterRows
        };

        $http.post('campaign-records/fetch', payload)
            .then(function(response) {

                const allData = response.data;

                const dataToProcessRecords = allData.Records;
                const dataToProcessUserRC = allData.UserRC;
                const dataToProcessTelephonyRC = allData.TelephonyRC;

                dataToProcessRecords.forEach(row => {
                    row.StatueLabel = row.Statue ? t(row.Statue) : '';
                });
               ctrl.setGridData(dataToProcessRecords, ctrl.gridOptions,'records');

               ctrl.setGridData(dataToProcessUserRC, ctrl.userResultCodeGridOptions,'userRc');

               ctrl.setGridData(dataToProcessTelephonyRC, ctrl.telephonyResultCodeGridOptions,'telephonyRc');

                ctrl.dataVisible = true;
                ctrl.setActiveTabAndRefresh('records');

                if (response.headers('X-Records-Truncated') === 'true') {
                    showWarningPopup(ctrl.locMsg['campaignDataManipulation.validation.recordsTruncated']);
                }
            })
            .catch(function(error) {
                ctrl.gridOptions.data = [];
                ctrl.userResultCodeGridOptions.data = [];
                ctrl.telephonyResultCodeGridOptions.data = [];
                ctrl.dataVisible = false;

                if (error.data && error.data.arguments) {
                    const message = error.data.arguments.join(", ") + " alanları seçilmeden mevcut kayıtlar listelenemez.";
                    showWarningPopup(message);
                    return;
                } else {
                    showWarningPopup(ctrl.locMsg['campaignDataManipulation.validation.updateFailed']);
                }
                console.error("Error:", error);
            });
    };


        ctrl.applyUpdate = function () {
            const selectedRecords = ctrl.recordGridApi.selection.getSelectedRows();
            const recordIds = selectedRecords.flatMap(r => r['Record Ids']);
            const operation = ctrl.update.operation;

            if (!ctrl.selectedTenant || !ctrl.selectedCampaign || !operation) {
                showWarningPopup(ctrl.locMsg['campaignDataManipulation.validation.missingFieldsForUpdate']);
                return;
            }

            let blockProceed = false;
            function norm(s){ return (s || '').toString().toLowerCase().trim(); }
            const colNames = (ctrl.columns || []).map(c => norm(c.name));
            function hasAny(cands){ return cands.some(x => colNames.includes(norm(x))); }

            if (operation === 'DELETE_RECORD') {
                var elimCols = ['elenme_nedeni'];
                if (!hasAny(elimCols)) {
                    showWarningPopup(ctrl.locMsg['campaignDataManipulation.validation.missingEliminationReasonColumn']);
                    blockProceed = true;
                }
            }

            if (operation === 'ADD_CALL_AS_PRIORITY' || operation === 'REMOVE_CALL_FROM_PRIORITY') {
                var prioCols = ['arama_oncelik'];
                if (!hasAny(prioCols)) {
                    showWarningPopup(ctrl.locMsg['campaignDataManipulation.validation.missingCallPriorityColumn']);
                    blockProceed = true;
                }
            }
            if (blockProceed) return;

            const selectedCampaignObject = ctrl.campaignMap[ctrl.selectedCampaign];
            if (selectedCampaignObject && selectedCampaignObject.state === 'RUNNING') {
                showWarningPopup(ctrl.locMsg['campaignDataManipulation.validation.campaignIsRunning']);
                return;
            }

            const operationsRequiringSelectedRecords = [
                'ACTIVATE_RECORD',
                'CONTACT_LIST_BULK_UPDATE',
                'DELETE_RECORD'
            ];
            if (operationsRequiringSelectedRecords.includes(operation) && recordIds.length === 0) {
                showWarningPopup(ctrl.locMsg['campaignDataManipulation.validation.noRecordsSelected']);
                return;
            }

            if (operation === 'CONTACT_LIST_BULK_UPDATE') {
                if (!ctrl.update.field) {
                    showWarningPopup(ctrl.locMsg['campaignDataManipulation.validation.missingFieldForBulkUpdate']);
                    return;
                }
                if (!ctrl.update.value) {
                    showWarningPopup(ctrl.locMsg['campaignDataManipulation.validation.missingValueForBulkUpdate']);
                    return;
                }
            }

            if (operation === 'DELETE_RECORD' && !ctrl.update.reasonForDeletion) {
                showWarningPopup(ctrl.locMsg['campaignDataManipulation.validation.missingEliminationReason']);
                return;
            }

            showConfirmationPopup(
                ctrl.locMsg['campaignDataManipulation.validation.confirmationMessage'],
                function () {
                    const payload = {
                        operation: operation,
                        recordIds: operationsRequiringSelectedRecords.includes(operation)
                            ? recordIds
                            : ctrl.filteredRecordIds,
                    };

                    if (operation === 'DELETE_RECORD') {
                        payload.reasonForDeletion = ctrl.update.reasonForDeletion;
                    } else if (operation === 'CONTACT_LIST_BULK_UPDATE') {
                        payload.field = ctrl.update.field;
                        payload.value = ctrl.update.value;
                    } else if (operation === 'ADD_CALL_AS_PRIORITY' || operation === 'REMOVE_CALL_FROM_PRIORITY') {
                        if (payload.recordIds) {
                            delete payload.recordIds;
                        }
                        payload.filterListDTO = ctrl.filterRows || [];
                        payload.logicOperator = ctrl.logicOperator;
                        payload.filterListDTO.forEach(filter => {
                            filter.logicOperator = ctrl.logicOperator;
                        });
                    }




                    $http.post(`/gbpowerdialer/campaign-records/${ctrl.selectedCampaign}/apply-operation`, payload)
                        .then(function (response) {
                            const result = response.data;
                            var affected = parseInt(response.headers('X-Updated-Count'), 10);
                            if (isNaN(affected)) { affected = 0; }
                            if (result === 'UPDATE_SUCCESSFUL') {
                                ctrl.lastUpdatedCountMsg = ctrl.locMsg['campaignDataManipulation.updateAffectedCount']
                                    .replace('{{count}}', affected);
                                showSuccessPopup(ctrl.locMsg['campaignDataManipulation.validation.updateSuccessful']);
                                ctrl.fetchRecords();
                            } else if (result === 'CAMPAIGN_IS_RUNNING_CANT_UPDATE') {
                                ctrl.lastUpdatedCountMsg = '';
                                showWarningPopup(ctrl.locMsg['campaignDataManipulation.validation.campaignIsRunning']);
                            } else if (result === 'CAMPAIGN_IS_ALREADY_PRIORITY') {
                                ctrl.lastUpdatedCountMsg = '';
                                showWarningPopup(ctrl.locMsg['campaignDataManipulation.validation.campaignAlreadyPriority']);
                            } else {
                                ctrl.lastUpdatedCountMsg = '';
                                showWarningPopup(ctrl.locMsg['campaignDataManipulation.validation.updateFailed']);
                            }
                        })
                        .catch(function () {
                            ctrl.lastUpdatedCountMsg = '';
                            showErrorPopup(ctrl.locMsg['campaignDataManipulation.validation.updateError']);
                        });
                }
            );
        };




    function showSuccessPopup(message) {
        document.getElementById("successModalMessage").innerText = message;
        $('#successModal').modal('show');
    }

    function showWarningPopup(message) {
        document.getElementById("warningModalMessage").innerText = message;
        $('#warningModal').modal('show');
    }

    function showErrorPopup(message) {
        document.getElementById('errorModalMessage').innerText = message;
        $('#errorModal').modal('show');
    }

        function showConfirmationPopup(message, onConfirm) {
            document.getElementById("confirmationModalMessage").innerText = message;

            $('#confirmationModalOk').off('click');
            $('#confirmationModalOk').on('click', function () {
                $('#confirmationModal').modal('hide');
                if (typeof onConfirm === 'function') {
                    onConfirm();
                }
            });

            $('#confirmationModal').modal('show');
        }



        function t(key){
            try { return $filter('i18n')(key); } catch(e){ return key; }
        }

        const GRID_LABEL_KEYS = {
            'ListID':        'campaignDataManipulation.grid.listId',
            'Statue':        'campaignDataManipulation.grid.statue',
            'Record Count':  'campaignDataManipulation.grid.recordCount',
            'result_code_text' :  'campaignDataManipulation.grid.result_code_text',
            'statusCode':        'campaignDataManipulation.grid.statusCode',
            'record_count':  'campaignDataManipulation.grid.record_count'
        };

        function gridLabel(rawKey) {
            const i18nKey = GRID_LABEL_KEYS[rawKey];
            if (i18nKey) return t(i18nKey);
            return t(`campaignDataManipulation.grid.${rawKey}`);
        }

        const OP_HINT_KEY = {
            'ACTIVATE_RECORD'          : 'campaignDataManipulation.tooltip.activateRecord',
            'ADD_CALL_AS_PRIORITY'     : 'campaignDataManipulation.tooltip.addPriority',
            'REMOVE_CALL_FROM_PRIORITY': 'campaignDataManipulation.tooltip.removePriority',
            'CONTACT_LIST_BULK_UPDATE' : 'campaignDataManipulation.tooltip.bulkContactUpdate',
            'DELETE_RECORD'            : 'campaignDataManipulation.tooltip.deleteRecord'
        };



        ctrl.update = ctrl.update || {};
        $scope.$watch(() => ctrl.update.operation, function (newVal) {
            const key = OP_HINT_KEY[newVal] || null;
            ctrl.operationHintText = key ? t(key) : '';
            setTimeout(function () {
                var $el = $('#operationHint');
                if (!$el.length) return;
                    if (ctrl.operationHintText) {
                        $el.attr('title', ctrl.operationHintText)
                            .attr('data-original-title', ctrl.operationHintText);
                        var inst = $el.data('bs.tooltip') || $el.data('tooltip');
                        if (!inst) {
                            $el.tooltip({ container: 'body', placement: 'right', trigger: 'hover focus' });
                        } else {
                            try { $el.tooltip('fixTitle'); } catch(e) {}
                        }
                    } else {
                        try { $el.tooltip('hide').tooltip('destroy'); } catch(e) {}
                    $el.removeAttr('title data-original-title aria-describedby');
                }
            }, 0);
        });

        ctrl.setActiveTabAndRefresh = function (tabName) {
            ctrl.activeTab = tabName;

            setTimeout(function() {
                if (tabName === 'records' && ctrl.recordGridApi) {
                    ctrl.recordGridApi.grid.refresh();
                } else if (tabName === 'userRC' && ctrl.userRcGridApi) {
                    ctrl.userRcGridApi.grid.refresh();
                } else if (tabName === 'telephonyRC' && ctrl.telephonyRCGridApi) {
                    ctrl.telephonyRCGridApi.grid.refresh();
                }

                if (!$scope.$$phase) {
                    $scope.$apply();
                }
            }, 50);
        };

    ctrl.init();
}
]);
