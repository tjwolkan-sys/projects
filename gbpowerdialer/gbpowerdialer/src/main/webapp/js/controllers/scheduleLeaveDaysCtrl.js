app.controller('scheduleLeaveDaysCtrl', [
    '$scope', '$http', 'campaignLeaveDayFactory', 'uiGridConstants',
    '$log', '$modal', 'i18nService',
    '$locale', '$rootScope', '$filter',
    'locMsg', 'uiGridGroupingConstants', 'constants', 'alertService',
    function (
        $scope, $http, campaignLeaveDayFactory, uiGridConstants,
        $log, $modal, i18nService,
        $locale, $rootScope, $filter,
        locMsg, uiGridGroupingConstants, constants, alertService
    ) {

        var vm = this;

        // Start with empty; we will rebind to parent as soon as it's ready
        vm.campaign = vm.campaign || {};
        vm.campaignInitial = vm.campaignInitial || {};
        vm.selectedLeaveDays = [];
        vm.leaveDays = [];

        function ensureLeaveDaysArray(obj) {
            if (!obj) return;
            if (!Array.isArray(obj.blackoutSchedules)) obj.blackoutSchedules = [];
        }


        // --- Rebind to parent campaign.item when parent replaces it
        $scope.$watch(function () {
            return $scope.campaign && $scope.campaign.item;
        }, function (newItem) {
            if (!newItem) return;
            if (newItem === vm.campaign) return; // same ref, skip

            vm.campaign = newItem;                 // reattach to NEW object
            ensureLeaveDaysArray(vm.campaign);
            vm.refreshTable(vm.campaign.blackoutSchedules || []);
        });

        // Track parent campaign.itemInitial too (if needed)
        $scope.$watch(function () {
            return $scope.campaign && $scope.campaign.itemInitial;
        }, function (newInit) {
            if (!newInit) return;
            vm.campaignInitial = newInit;
        });

        // ---------- ui-grid ----------
        vm.leaveDaysGridOptions = {
            enableColumnResizing: true,
            enableColumnMenus: true,
            enableRowSelection: true,
            enableSelectAll: true,
            selectionRowHeaderWidth: 35,
            rowHeight: 32,
            data: vm.leaveDays, // replaced in refreshTable
            multiSelect: true,
            columnDefs: [
                {
                    name: 'startDate',
                    displayName: locMsg['campaign.leaveDay.startDate'],
                    width: 160,
                    cellFilter: 'date:"dd/MM/yyyy HH:mm"'
                },
                {
                    name: 'endDate',
                    displayName: locMsg['campaign.leaveDay.endDate'],
                    width: 160,
                    cellFilter: 'date:"dd/MM/yyyy HH:mm"'
                },
                {
                    name: 'description',
                    displayName: locMsg['campaign.leaveDay.description'],
                    width: '*'
                }
            ]
        };

        vm.leaveDaysGridOptions.onRegisterApi = function (gridApi) {
            vm.gridApi = gridApi;

            function updateSelection() {
                vm.selectedLeaveDays = vm.gridApi.selection.getSelectedRows();
            }

            gridApi.selection.on.rowSelectionChanged($scope, updateSelection);
            gridApi.selection.on.rowSelectionChangedBatch($scope, updateSelection);
        };

        i18nService.setCurrentLang(locMsg['lang.id']);

        // ---------- Modal open ----------
        vm.open = function (size, item) {
            vm.item = item || null;

            var modalInstance = $modal.open({
                animation: true,
                backdrop: 'static',
                templateUrl: 'pages/leaveDayForm.html',
                controller: 'leaveDayFormCtrl as leaveDayForm',
                size: size,
                resolve: {
                    item: function () { return vm.item; },
                    campaign: function () { return vm.campaign; }
                }
            });

            modalInstance.result.then(function (result) {
                // Expected: { item: <leaveDay>, continue: boolean }
                if (result && result.item) {
                    var res = upsertLeaveDay(vm.campaign.blackoutSchedules, result.item);
                    if (!res.ok) {
                        if (res.reason === 'duplicate_startDate') {
                            alertService.add('danger', locMsg['alert.leaveDay.duplicateStartDate']);
                        } else if (res.reason === 'startDate_in_range') {
                            alertService.add('danger', locMsg['alert.leaveDay.startDateInRange']);
                        }
                        return; // don't refresh
                    }

                    vm.refreshTable(vm.campaign.blackoutSchedules);
                    vm.selectedLeaveDays = [];
                }
                if (result && result.continue === true) {
                    vm.open(size, null); // reopen blank for fast entry
                }
            }, function () {
                $log.info('Modal dismissed at: ' + new Date());
            });
        };

        // Upsert helper (by id if present)
        // Helpers (safe for Date, millis, or ISO string)
        function toDateOnly(v){
            var d = (v instanceof Date) ? v : (v != null ? new Date(v) : null);
            if (!d || isNaN(d)) return null;
            return new Date(d.getFullYear(), d.getMonth(), d.getDate()); // midnight
        }
        function sameDay(a, b){
            var da = toDateOnly(a), db = toDateOnly(b);
            return !!da && !!db &&
                da.getFullYear() === db.getFullYear() &&
                da.getMonth() === db.getMonth() &&
                da.getDate() === db.getDate();
        }

        // Helpers (date-only normalization)
        function toDateOnly(v){
            var d = (v instanceof Date) ? v : (v != null ? new Date(v) : null);
            if (!d || isNaN(d)) return null;
            return new Date(d.getFullYear(), d.getMonth(), d.getDate()); // midnight
        }
        function sameDay(a, b){
            var da = toDateOnly(a), db = toDateOnly(b);
            return !!da && !!db &&
                da.getFullYear() === db.getFullYear() &&
                da.getMonth() === db.getMonth() &&
                da.getDate() === db.getDate();
        }

        function upsertLeaveDay(list, ld) {
            if (!Array.isArray(list) || !ld) return { ok: false, reason: 'invalid_args' };

            var copy = angular.copy(ld);
            copy.changed = true;

            // New start (date-only)
            var sNew = toDateOnly(copy.startDate);
            if (!sNew) return { ok: false, reason: 'invalid_startDate' };

            var conflict = _.find(list, function (x) {
                if (!x) return false;
                if (copy.id != null && x.id === copy.id) return false; // skip self
                return sameDay(x.startDate, copy.startDate);
            });
            if (conflict) {
                return { ok: false, reason: 'duplicate_startDate', conflict: conflict };
            }

            var rangeConflict = _.find(list, function (x) {
                if (!x) return false;
                if (copy.id != null && x.id === copy.id) return false; // skip self

                var sx = toDateOnly(x.startDate);
                if (!sx) return false;

                // If endDate missing, treat as same-day window
                var ex = toDateOnly(x.endDate) || sx;

                // Guard against accidental inverted ranges
                if (ex < sx) { var t = sx; sx = ex; ex = t; }

                return sNew >= sx && sNew <= ex;
            });
            if (rangeConflict) {
                return { ok: false, reason: 'startDate_in_range', conflict: rangeConflict };
            }

            // Upsert
            if (copy.id != null) {
                var idx = _.findIndex(list, function (x) { return x && x.id === copy.id; });
                if (idx > -1) angular.extend(list[idx], copy);
                else list.push(copy);
            } else {
                if (copy.enabled === undefined) copy.enabled = true;
                list.push(copy);
            }

            return { ok: true };
        }


        // ---------- Pagination / initial load ----------
        var paginationOptions = { page: {}, sort: {}, filters: {} };

        $scope.$watch(function () { return vm.campaign; }, function () {
            if (vm.campaign) ensureLeaveDaysArray(vm.campaign);

            if (vm.campaign && vm.campaign.id !== undefined) {
                paginationOptions.filters = {
                    'campaign.id': [{ term: vm.campaign.id.toString() }]
                };
                vm.getPage();
            } else {
                vm.refreshTable((vm.campaign && vm.campaign.blackoutSchedules) || []);
                if (vm.campaignInitial) {
                    vm.campaignInitial.blackoutSchedules = angular.copy((vm.campaign && vm.campaign.blackoutSchedules) || []);
                }
            }
        });

        // Watch array contents (adds/removes) and refresh grid
        $scope.$watchCollection(function () {
            return vm.campaign ? vm.campaign.blackoutSchedules : undefined;
        }, function (newVal) {
            vm.refreshTable(newVal || []);
        });

        vm.toggleFiltering = function () {
            vm.leaveDaysGridOptions.enableFiltering = !vm.leaveDaysGridOptions.enableFiltering;
            if (vm.gridApi) {
                vm.gridApi.core.notifyDataChange(uiGridConstants.dataChange.COLUMN);
            }
        };

        vm.getPage = function () {
            // Only load from server if panel starts empty and campaign has an id
            if (vm.campaign && vm.campaign.id != null && (!vm.leaveDays || vm.leaveDays.length === 0)) {
                campaignLeaveDayFactory.getAll(
                    paginationOptions.page,
                    { field: 'startDate', direction: 'asc' },
                    paginationOptions.filters
                ).then(function (data) {
                    ensureLeaveDaysArray(vm.campaign);
                    vm.campaign.blackoutSchedules = data.data || [];
                    if (vm.campaignInitial) {
                        vm.campaignInitial.blackoutSchedules = angular.copy(vm.campaign.blackoutSchedules);
                    }
                    vm.refreshTable(vm.campaign.blackoutSchedules);
                });
            }
        };

        // ---------- Remove ----------
        vm.remove = function () {
            var rows = (vm.gridApi && vm.gridApi.selection) ? vm.gridApi.selection.getSelectedRows() : [];
            removeSelectedLeaveDays(rows);
            vm.refreshTable(vm.campaign.blackoutSchedules);
        };

        function removeSelectedLeaveDays(leaveDays) {
            (leaveDays || []).forEach(function (item) {
                if (!item) return;
                if (item.id == null) {
                    // Remove newly added (no id) by matching fields
                    var match = _.find(vm.campaign.blackoutSchedules, function (x) {
                        return x
                            && x.startDate === item.startDate
                            && x.endDate === item.endDate
                            && x.description === item.description;
                    });
                    if (match) {
                        vm.campaign.blackoutSchedules = _.without(vm.campaign.blackoutSchedules, match);
                    }
                } else {
                    // Soft-delete persisted items
                    item.deleted = true;
                    item.changed = true;
                    item.enabled = false;
                }
            });
        }

        // ---------- Refresh / view model ----------
        vm.refreshTable = function (sch) {
            vm.selectedLeaveDays = [];

            var visible = (sch || [])
                .filter(function (x) { return x; })
                .sort(function (a, b) {
                    var s = (new Date(a.startDate)) - (new Date(b.startDate));
                    if (s !== 0) return s;
                    return (new Date(a.endDate)) - (new Date(b.endDate));
                });

            vm.leaveDays = visible;
            vm.leaveDaysGridOptions.data = vm.leaveDays;

            if (vm.gridApi) {
                vm.gridApi.core.notifyDataChange(uiGridConstants.dataChange.ALL);
            }
        };

        // ---------- Helpers for buttons ----------
        vm.disabledEditButton = function () {
            return !(vm.selectedLeaveDays && vm.selectedLeaveDays.length === 1);
        };

        vm.getSelectedLeaveDay = function () {
            return (vm.selectedLeaveDays && vm.selectedLeaveDays.length > 0)
                ? vm.selectedLeaveDays[0]
                : {};
        };

        vm.newLeaveDay = function () {
            return campaignLeaveDayFactory.createDefaultLeaveDay();
        };
    }
]);