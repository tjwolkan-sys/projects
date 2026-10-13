/**
 * Controller to edit resultCodeSchedule
 */
app.controller('resultCodeScheduleFormCtrl',
    function($modalInstance, item, campaign, campaignInitial, telephonyRcList, resultCodeList, locMsg, timeValidatorService, alertService) {

      var vm = this;
      vm.item = angular.copy(item);
      vm.campaign = campaign;
      vm.campaignInitial = campaignInitial;
      vm.itemInitial = _.findWhere(vm.campaignInitial.resultCodeSchedules, vm.item.id);
      vm.telephonyRcList = telephonyRcList;
      vm.resultCodeList = resultCodeList;
      vm.alertMessage = "";

      vm.save = function() {
        vm.item.changed = vm.item.changed || !angular.equals(vm.item, item);
        if (!_.isUndefined(vm.item.resultCodeScheduleAdvanceds) && _.isEmpty(vm.item.resultCodeScheduleAdvanceds)) {
          vm.item.resultCodeScheduleAdvanceds = null;
        }
        if (vm.validate()) {
          $modalInstance.close(vm.item);
        } else {
          alertService.add('danger', locMsg[vm.alertMessage]);
        }
      };

      vm.cancel = function() {
        $modalInstance.dismiss('cancel');
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

      vm.validate = function() {
        if (!timeValidatorService.validate(vm.item.minScheduleTime, vm.item.maxScheduleTime)) {
          vm.alertMessage = 'resultCodeSchedule.time.validation.error';
          return false;
        }

        var isDuplicated = false;
        _.each(_.without(campaign.resultCodeSchedules, item), function(item, i, arr) {
          if (!item.deleted
              && (item.telephonyResultCode != null && vm.item.telephonyResultCode != null && item.telephonyResultCode.id == vm.item.telephonyResultCode.id
                  && item.resultCode == vm.item.resultCode || item.resultCode != null && vm.item.resultCode != null && item.resultCode.id == vm.item.resultCode.id
                  && item.telephonyResultCode == vm.item.telephonyResultCode)) {
            isDuplicated = true;
          }
        });

        if (isDuplicated) {
          vm.alertMessage = 'resultCodeSchedule.duplicate.error';
          return false;
        }

        return true;
      };

    });
