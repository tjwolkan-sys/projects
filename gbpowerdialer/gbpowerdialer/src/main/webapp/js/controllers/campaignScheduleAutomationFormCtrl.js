/**
 * Controller to edit schedules of a campaign
 */
app.controller('campaignScheduleAutomationFormCtrl', [
    '$scope',
    '$http',
    'campaignScheduleAutomationFactory',
    'uiGridConstants',
    '$log',
    '$uibModalInstance',
    'i18nService',
    '$locale',
    '$rootScope',
    '$filter',
    'locMsg',
    'item',
    'campaign',
    'alertService',
    'scheduleAutomations',
    'groupList',
    function($scope, $http, campaignScheduleAutomationFactory, uiGridConstants, $log, $uibModalInstance , i18nService, $locale, $rootScope, $filter, locMsg, item, campaign,
             alertService, scheduleAutomations, groupList) {
        var vm = this;

        vm.campaign = campaign;
        vm.scheduleAutomations = scheduleAutomations;
        vm.groupList = groupList;
        vm.itemHashKey = item.$$hashKey;
        vm.alertMessage = "";

        vm.item = angular.copy(item);

        vm.saveAndContinue = function() {
            if (vm.validate()) {
                vm.item.changed = vm.item.changed || !angular.equals(vm.item, item);
                if (vm.item.isNew) {
                    vm.item.isNew = false;
                    vm.scheduleAutomations.unshift(vm.item);
                } else {
                    angular.merge(item, vm.item);
                }
                vm.setNewInitialItem();
                vm.item = angular.copy(item);
                alertService.add('success', locMsg['label.save.success']);
            } else {
                alertService.add('danger', locMsg[vm.alertMessage]);
            }
        };

        vm.setNewInitialItem = function() {
            item = {};
            item.deleted = false;
            item.enabled = false;
            vm.item.isNew = true;
            item.groups = [];
            item.threshold = 0;
        };

        vm.cancel = function() {
            $uibModalInstance.dismiss('cancel');
        };

        vm.save = function() {
            if (vm.validate()) {
                vm.item.changed = vm.item.changed || !angular.equals(vm.item, item);
                $uibModalInstance.close(vm.item);
            } else {
                alertService.add('danger', locMsg[vm.alertMessage]);
            }
        };

        vm.validate = function() {
            if (!vm.item.formula || vm.item.formula === "") {
                vm.alertMessage = 'scheduleAutomation.formula.alert';
                return false;
            }

            if ((vm.formulaContains('loginAgentsCount', vm.item.formula) || vm.formulaContains('availableAgentsCount', vm.item.formula))
                && vm.item.groups.length === 0) {
                vm.alertMessage = 'scheduleAutomation.groupstats.alert';
                return false;
            }


            if (vm.item.enabled && vm.enabledScheduleAutomationWithSameActionExist(vm.item)) {
                vm.alertMessage = 'scheduleAutomation.enabled.alert';
                return false;
            }

            return true;
        };

        vm.enabledScheduleAutomationWithSameActionExist = function(targetSchedule){
            return vm.campaign.scheduleAutomations.filter(schedule => {
                return vm.compareSchedule(schedule, targetSchedule);
            }).length > 0;
        };

        vm.formulaContains = function (key, text) {
            let searchExp = new RegExp(key);
            return (searchExp.test(text))
        };

        vm.isEditScheduleAutomation = function() {
            return vm.item.id >= 0 || vm.item.changed !== undefined;
        };

        vm.canEnableSelectedSchedule = function (selectedSchedule) {
            let canEnable = true;
            if (selectedSchedule.isNew && !selectedSchedule.actionType) {
                canEnable = vm.scheduleAutomations.filter(schedule => {
                    return schedule.enabled;
                }).length < 2;
            } else {
                canEnable = vm.scheduleAutomations.filter(schedule => {
                    return vm.compareSchedule(schedule, selectedSchedule);
                }).length === 0;
            }
            return canEnable;
        };

        vm.compareSchedule = function(schedule, targetSchedule){
            return schedule.$$hashKey !== vm.itemHashKey && schedule.enabled && schedule.actionType === targetSchedule.actionType;
        };


        vm.actionTypes = [ {
            value : 0,
            label : locMsg['scheduleAutomation.actionType.0']
        }, {
            value : 1,
            label : locMsg['scheduleAutomation.actionType.1']
        }, {
            value : 2,
            label : locMsg['scheduleAutomation.actionType.2']
        } ];


        const AGENT_STATS_METRICS = [
            "loginAgentsCount",
            "availableAgentsCount"
        ];


        $scope.vm = {
            formulaBuilder: {
                category: "",
                metric: "",
                metrics: [],
                operator: ">",
                value: null
            },

            onCategoryChange: function () {

                if ($scope.vm.formulaBuilder.category === "agentStats") {
                    $scope.vm.formulaBuilder.metrics = AGENT_STATS_METRICS;
                } else {
                    $scope.vm.formulaBuilder.metrics = [];
                }

                $scope.vm.formulaBuilder.metric = "";
            },

            applyFormula: function () {
                if ($scope.vm.formulaBuilder.category &&
                    $scope.vm.formulaBuilder.metric &&
                    $scope.vm.formulaBuilder.operator &&
                    $scope.vm.formulaBuilder.value !== null) {

                    if ($scope.vm.formulaBuilder.value === undefined) {
                        alertService.add('danger', "Value cannot be less than 0.");
                        $scope.vm.formulaBuilder.value = 0;
                    }

                    var formula = "{{" + $scope.vm.formulaBuilder.metric + "}}" +
                        $scope.vm.formulaBuilder.operator +
                        $scope.vm.formulaBuilder.value;

                    $scope.scheduleAutomationForm.item.formula = formula;
                }
            }
        };


    } ]);
