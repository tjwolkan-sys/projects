app.controller('leaveDayFormCtrl', [
    '$scope', '$modalInstance', 'locMsg', 'alertService', 'item', 'campaign',
    function ($scope, $modalInstance, locMsg, alertService, item, campaign) {
        var vm = this;

        // helpers
        function toDate(v){ return v instanceof Date ? v : (v!=null ? new Date(v) : null); }
        function toMillis(v){ return v instanceof Date ? v.getTime() : (typeof v==='number' ? v : null); }

        vm.campaign = campaign || {};
        vm.item = angular.copy(item) || null;
        vm.isEdit = !!vm.item

        // Work with Date objects in the form
        vm.selected = {
            item: vm.isEdit ? {
                id: vm.item.id,
                startDate: toDate(vm.item.startDate),
                endDate:   toDate(vm.item.endDate),
                description: vm.item.description || "",
                campaign: campaign ? campaign.id : null
            } : {
                startDate: null,
                endDate: null,
                description: "",
                campaign: campaign ? campaign.id : null
            }
        };

        // close dropdowns on pick (from earlier)
        vm.ddStartOpen = vm.ddEndOpen = false;
        vm.onStartDateSet = function(){ vm.ddStartOpen = false; };
        vm.onEndDateSet   = function(){ vm.ddEndOpen = false;  };

        function payload(){
            var out = angular.copy(vm.selected.item);
            out.startDate = toMillis(out.startDate);
            out.endDate   = toMillis(out.endDate);
            return out;
        }

        vm.save = function(){
            if (!vm.validate()){
                if ($scope.leaveDayModalForm) $scope.leaveDayModalForm.$setSubmitted();
                return alertService.add('danger', locMsg[vm.alertMessage]);
            }
            $modalInstance.close({ item: payload(), continue: false });
        };

        vm.saveAndContinue = function(){
            if (!vm.validate()){
                if ($scope.leaveDayModalForm) $scope.leaveDayModalForm.$setSubmitted();
                return alertService.add('danger', locMsg[vm.alertMessage]);
            }
            $modalInstance.close({ item: payload(), continue: true });
        };

        vm.cancel = function(){ $modalInstance.dismiss('cancel'); };

        vm.alertMessage = '';
        vm.validate = function(){
            var it = vm.selected.item || {};
            var startDate = toDate(it.startDate), endDate = toDate(it.endDate);

            if (!startDate || !endDate){ vm.alertMessage='form.required'; return false; }


            if (isNaN(startDate) || isNaN(endDate)){ vm.alertMessage='form.required'; return false; }

            if (startDate >= endDate) { vm.alertMessage='alert.campaign.startEnd.date.error'; return false; }

            if (!it.description || !it.description.trim()){ vm.alertMessage='form.required'; return false; }

            var now = new Date();
            var today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
            if (startDate < today){
                vm.alertMessage='alert.campaign.startDate.past.error';
                return false;
            }

            return true;
        };
    }
]);
