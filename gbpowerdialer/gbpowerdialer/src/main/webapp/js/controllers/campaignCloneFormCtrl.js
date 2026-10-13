/**
 * Controller to clone a campaign
 */
app.controller('campaignCloneFormCtrl', [
	'$modalInstance',
	'item',
	'campaignFactory',
	'$log',
	'locMsg',
	'alertService',
	function ($modalInstance, item, campaignFactory, $log, locMsg, alertService) {

		var vm = this;
		vm.item = {};
		vm.item.id = item;

		vm.save = function () {
			campaignFactory.clone(vm.item).then(function (response) {
				if (response.data.foreignKeyAdditionError == true) {
					let errorMessage = locMsg['alert.fk.addition.error'].replace('%s', response.data.campaign.dbPriorityTable);
					alertService.add('warning', errorMessage);
				}
				$modalInstance.close(response.data.campaign);
			}, function (response) {
				$log.error(response);
			});
		};

		vm.cancel = function () {
			$modalInstance.dismiss('cancel');
		};

	}])
;
