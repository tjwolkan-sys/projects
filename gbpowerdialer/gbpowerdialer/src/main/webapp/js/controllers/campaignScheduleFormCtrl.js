/**
 * Controller to edit schedules of a campaign
 */
app.controller('campaignScheduleFormCtrl', [
    '$scope',
    '$http',
    'campaignScheduleFactory',
    'uiGridConstants',
    '$log',
    '$modal',
    '$modalInstance',
    'i18nService',
    '$locale',
    '$rootScope',
    '$filter',
    'locMsg',
    'item',
    'campaign',
    'alertService',
    'timeValidatorService',
    function($scope, $http, campaignScheduleFactory, uiGridConstants, $log, $modal, $modalInstance, i18nService, $locale, $rootScope, $filter, locMsg, item, campaign,
             alertService, timeValidatorService) {
      var vm = this;

      vm.alertMessage = "";
      vm.campaign = campaign;

      vm.item = item;
      vm.selected = {
        item : angular.copy(vm.item)
      };

      vm.saveAndContinue = function() {
        if (vm.validate()) {
          vm.selected.item.changed = !angular.equals(vm.selected.item, vm.item);
          if (vm.item.id != null) {
            vm.campaign.schedules = _.without(vm.campaign.schedules, _.findWhere(vm.campaign.schedules, {
              id : item.id
            }));
          } else if (vm.item.day != null) {
            vm.campaign.schedules.splice(vm.campaign.schedules.indexOf(vm.item), 1);
          }
          vm.campaign.schedules.push(vm.selected.item);

          vm.item.day = null;
          vm.item.id = null;

          var campaignSchedule = {};
          campaignSchedule.day = vm.selected.item.day;
          campaignSchedule.from = vm.selected.item.from;
          campaignSchedule.till = vm.selected.item.till;
          campaignSchedule.enabled = vm.selected.item.enabled;
          campaignSchedule.resetEnabled = vm.selected.item.resetEnabled;
          vm.selected.item = campaignSchedule;

          alertService.add('success', locMsg['label.save.success']);
        } else {
          alertService.add('danger', locMsg[vm.alertMessage]);
        }

      };

      vm.save = function() {
        if (vm.validate()) {
          vm.selected.item.changed = !angular.equals(vm.selected.item, vm.item);
          $modalInstance.close(vm.selected.item);
        } else {
          alertService.add('danger', locMsg[vm.alertMessage]);
        }
      };

      vm.cancel = function() {
        $modalInstance.dismiss('cancel');
      };

      vm.validate = function() {
        vm.fromTime = timeValidatorService.formatTime(vm.selected.item.from);
        vm.tillTime = timeValidatorService.formatTime(vm.selected.item.till);
        if (!timeValidatorService.validate(vm.selected.item.from, vm.selected.item.till)) {
          vm.alertMessage = 'alert.schedule.date.error';
          return false;
        } else if (vm.validateRange(timeValidatorService.getComparableTime(vm.fromTime), vm.campaign.schedules, vm.selected.item, false)) {
          vm.alertMessage = 'alert.schedule.date.in.range.from';
          return false;
        } else if (vm.validateRange(timeValidatorService.getComparableTime(vm.tillTime), vm.campaign.schedules, vm.selected.item, true)) {
          vm.alertMessage = 'alert.schedule.date.in.range.till';
          return false;
        } else if (vm.selected.item.resetEnabled && !vm.selected.item.enabled) {
            vm.alertMessage = 'alert.schedule.resetEnabled.requires.enabled';
            return false;
        }

        return true;
      };

      vm.validateRange = function(time, schedules, selectedItem, validateTill) {
        vm.resultArray = [];
        vm.isPresent = vm.item.day !== null && Object.keys(vm.item).length > 0 && vm.campaign.schedules.indexOf(vm.item) > -1;
        if (vm.isPresent) {
          vm.campaign.schedules.splice(vm.campaign.schedules.indexOf(vm.item), 1);
        }
        $.grep(schedules, function(item, index) {
          return item.day === selectedItem.day && !item.deleted;
        }).forEach(
            function(item, i, arr) {

              vm.tempFromTime = timeValidatorService.formatTime(item.from);
              vm.tempTillTime = timeValidatorService.formatTime(item.till);

              vm.resultArray.push(timeValidatorService.getComparableTime(time) >= timeValidatorService.getComparableTime(vm.tempFromTime)
                  && timeValidatorService.getComparableTime(time) <= timeValidatorService.getComparableTime(vm.tempTillTime));
              if (validateTill) {
                vm.resultArray.push(timeValidatorService.getComparableTime(time) >= timeValidatorService.getComparableTime(vm.tempTillTime)
                    && timeValidatorService.getComparableTime(timeValidatorService.formatTime(vm.selected.item.from)) <= timeValidatorService.getComparableTime(vm.tempFromTime));
              }
            });
        if (vm.isPresent) {
          vm.campaign.schedules.push(vm.item);
        }
        return vm.resultArray.indexOf(true) > -1;
      };

      vm.days = [ {
        value : 0,
        label : locMsg['week.day.0']
      }, {
        value : 1,
        label : locMsg['week.day.1']
      }, {
        value : 2,
        label : locMsg['week.day.2']
      }, {
        value : 3,
        label : locMsg['week.day.3']
      }, {
        value : 4,
        label : locMsg['week.day.4']
      }, {
        value : 5,
        label : locMsg['week.day.5']
      }, {
        value : 6,
        label : locMsg['week.day.6']
      }, ];
    } ]);