<div class="panel panel-primary">
	<div class="panel-heading">
		<h3 class="panel-title">{{'scheduleAutomation.list' | i18n}}</h3>
		<div class="panel-buttons pull-right">
			<button class="btn btn-default btn-sm" onclick="return false" ng-click="campaignScheduleAutomation.toggleFiltering()" tooltip="{{'tooltip.filter' | i18n}}">
				<i class="fa fa-filter"></i>
			</button>
			<div class="btn-group">
				<button type="button" class="btn btn-default btn-sm dropdown-toggle"
								ng-disabled="campaignScheduleAutomation.disableButton() || !canEditSelectedCampaignByPermission(campaign, 'EditCampaignTech')"
								data-toggle="dropdown" aria-expanded="false" tooltip="{{'menu.enable' | i18n}}/{{'menu.disable' | i18n}}">
					<i class="fa fa-check-square-o"></i>
				</button>
				<ul class="dropdown-menu">
					<li>
						<a href="javascript:void(0)" ng-click="campaignScheduleAutomation.setEnabled(true)" onclick="return false">{{'menu.enable' | i18n}}</a>
					</li>
					<li>
						<a href="javascript:void(0)" ng-click="campaignScheduleAutomation.setEnabled(false)" onclick="return false">{{'menu.disable' | i18n}}</a>
					</li>
				</ul>
			</div>
			<button class="btn btn-default btn-sm" onclick="return false" ng-click="campaignScheduleAutomation.open('lg', campaignScheduleAutomation.newCampaignScheduleAutomation())"
							ng-disabled = "!canEditSelectedCampaignByPermission(campaign, 'EditCampaignTech')"
							tooltip="{{'tooltip.add' | i18n}}">
				<i class="fa fa-plus"></i>
			</button>
			<button class="btn btn-default btn-sm" onclick="return false" ng-disabled="campaignScheduleAutomation.disableButton() || !canEditSelectedCampaignByPermission(campaign, 'EditCampaignTech')"
							ng-click="campaignScheduleAutomation.open('lg', campaignScheduleAutomation.selectedScheduleAutomations[0])" tooltip="{{'tooltip.edit' | i18n}}">
				<i class="fa fa-pencil-square-o"></i>
			</button>
			<button class="btn btn-default btn-sm" onclick="return false"
							ng-disabled="campaignScheduleAutomation.disableButton() || !canEditSelectedCampaignByPermission(campaign, 'EditCampaignTech')"
							ng-really-message="{{'label.confirm' | i18n}}"
							ng-really-click="campaignScheduleAutomation.remove()" tooltip="{{'tooltip.delete' | i18n}}">
				<i class="fa fa-trash"></i>
			</button>
		</div>
	</div>
	<div class="panel-body">
		<div>
			<div id="campaignScheduleAutomationGrid" ui-grid="campaignScheduleAutomation.scheduleAutomationGridOptions" ui-grid-resize-columns ui-grid-selection ui-grid-auto-resize class="grid"></div>
		</div>
	</div>
</div>
<hr class="divider"/>
