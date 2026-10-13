<div class="panel panel-primary">
	<nav class="navbar-default">
		<div class="container-fluid">
			<div class="navbar-header" style="margin-left: -15px;">
				<button type="button" class="navbarCompainList-toggle navbar-toggle collapsed" data-toggle="collapse" data-target="#navbarCompainList" aria-expanded="false"
					aria-controls="navbar">
					<span class="icon-bar"></span> <span class="icon-bar"></span> <span class="icon-bar"></span>
				</button>
				<div class="navbarCompainList-brand navbar-brand panel-title">{{'campaign.list' | i18n}}</div>
			</div>
			<div id="navbarCompainList" class="navbar-collapse collapse">
				<div class="navbarCompainList-buttons nav navbar-nav navbar-right" style="padding-left: 10px;">
					<button class="btn btn-success btn-sm navbar-btn" ng-click="campaign.changeState('START')"
						ng-disabled="campaign.selectedCampaigns == undefined || campaign.selectedCampaigns.length < 1 || campaign.isNeedDisableButton([1,2,3,4,6,7]) || !campaign.canManageSelectedCampaigns()">
						<i class="fa fa-play"></i> <span class="btn-text">{{'menu.start' | i18n}}</span>
					</button>
					<button class="btn btn-warning btn-sm navbar-btn" ng-really-message="{{'label.confirm' | i18n}}" ng-really-click="campaign.changeState('PAUSE')"
						ng-disabled="campaign.selectedCampaigns == undefined || campaign.selectedCampaigns.length < 1 || campaign.isNeedDisableButton([0,1,2,4,5,6,7]) || !campaign.canManageSelectedCampaigns()">
						<i class="fa fa-pause"></i> <span class="btn-text">{{'menu.pause' | i18n}}</span>
					</button>
					<div class="btn-group" uib-dropdown>
						<button id="single-button" type="button" class="btn btn-danger btn-sm" uib-dropdown-toggle
							ng-disabled="campaign.selectedCampaigns == undefined || campaign.selectedCampaigns.length < 1 || campaign.isNeedDisableButton([0,2,4,6,7]) || !campaign.canManageSelectedCampaigns()">
							<i class="fa fa-stop"></i> <span class="btn-text">{{'menu.stop' | i18n}}</span> <span class="caret"></span>
						</button>
						<ul class="dropdown-menu" uib-dropdown-menu role="menu" aria-labelledby="single-button">
							<li role="menuitem"><a href="javascript:void(0)" ng-really-click="campaign.changeState('STOP_REGULAR')" onclick="return false"
												   ng-hide="campaign.isNeedDisableButton([1])">{{'menu.stop.regular' | i18n}}</a></li>
							<li role="menuitem"><a href="javascript:void(0)" ng-really-click="campaign.changeState('STOP_FORCEFUL')" onclick="return false">{{'menu.stop.forceful' | i18n}}</a></li>
						</ul>
					</div>
					<button class="btn btn-info btn-sm navbar-btn" ng-really-message="{{'label.confirm' | i18n}}" ng-really-click="campaign.changeState('RELOAD')"
						ng-disabled="campaign.selectedCampaigns == undefined || campaign.selectedCampaigns.length < 1 || campaign.isNeedDisableButton([0,1,2,4,6,7]) || !campaign.canManageSelectedCampaigns()">
						<i class="fa fa-refresh"></i> <span class="btn-text">{{'menu.reload' | i18n}}</span>
					</button>
					<button class="btn btn-info btn-sm navbar-btn" ng-really-message="{{'label.confirm' | i18n}}" ng-really-click="campaign.changeState('RESET')"
						ng-disabled="campaign.selectedCampaigns == undefined || campaign.selectedCampaigns.length < 1 || campaign.isNeedDisableButton([1,2,4,6,7]) || !campaign.canManageSelectedCampaigns()">
						<i class="fa fa-undo"></i> <span class="btn-text">{{'menu.reset' | i18n}}</span>
					</button>
					<button class="btn btn-info btn-sm navbar-btn"
									ng-disabled="campaign.selectedCampaigns == undefined || campaign.selectedCampaigns.length != 1 || !campaign.canManageSelectedCampaigns()"
									ng-click="campaign.openUploadTool()">
						<i class="fa fa-upload"></i> <span class="btn-text">{{'menu.uploadTool' | i18n}}</span>
					</button>
					<button class="btn btn-info btn-sm navbar-btn" ng-if="!campaign.shouldHideRecalculationButton()" ng-click="campaign.recalculatePriorities()">
						<i class="fa fa-calculator"></i> <span class="btn-text">{{'menu.recalculate' | i18n}}</span>
					</button>
					<button class="btn btn-default btn-sm navbar-btn" ng-click="campaign.toggleFiltering()" tooltip="{{'tooltip.filter' | i18n}}" tooltip-append-to-body="true">
						<i class="fa fa-filter"></i>
					</button>
					<div class="btn-group">
						<button type="button" class="btn btn-default btn-sm navbar-btn dropdown-toggle"
							ng-disabled="campaign.selectedCampaigns == undefined || campaign.selectedCampaigns.length < 1
								|| campaign.isNeedDisableButton([1,2,3,4,5,6,7]) || !campaign.canEditSelectedCampaigns('EditCampaign')"
							data-toggle="dropdown" aria-expanded="false" tooltip="{{'menu.enable' | i18n}}/{{'menu.disable' | i18n}}" tooltip-append-to-body="true" aria-haspopup="true"
							aria-expanded="false">
							<i class="fa fa-check-square-o"></i>
						</button>
						<ul class="dropdown-menu">
							<li><a href="javascript:void(0)" ng-click="campaign.setEnabled(true)" onclick="return false">{{'menu.enable' | i18n}}</a></li>
							<li>
							<li><a href="javascript:void(0)" ng-click="campaign.setEnabled(false)" onclick="return false">{{'menu.disable' | i18n}}</a></li>
						</ul>
					</div>
					<button class="btn btn-default btn-sm navbar-btn" ng-disabled="!campaign.canCreateCampaign()" ng-click="campaign.open('lg', campaign.newCampaign())"
						tooltip="{{'tooltip.add' | i18n}}" tooltip-append-to-body="true">
						<i class="fa fa-plus"></i>
					</button>
					<button class="btn btn-default btn-sm navbar-btn"
						ng-disabled="campaign.selectedCampaigns == undefined || campaign.selectedCampaigns.length != 1 || campaign.isNeedDisableButton([1,2,4,6,7]) || !campaign.canEditSelectedCampaigns('EditCampaignNonTech')"
						ng-click="campaign.open('lg', campaign.selectedCampaigns[0])" tooltip="{{'tooltip.edit' | i18n}}" tooltip-append-to-body="true">
						<i class="fa fa-pencil-square-o"></i>
					</button>
					<button class="btn btn-default btn-sm navbar-btn"
						ng-disabled="campaign.selectedCampaigns == undefined || campaign.selectedCampaigns.length != 1 || !campaign.canCreateCampaign()" ng-click="campaign.clone()"
						tooltip="{{'tooltip.clone' | i18n}}" tooltip-append-to-body="true">
						<i class="fa fa-clone"></i>
					</button>
					<button class="btn btn-default btn-sm navbar-btn"
						ng-disabled="campaign.selectedCampaigns == undefined || campaign.selectedCampaigns.length < 1 || campaign.isNeedDisableButton([1,2,3,4,5,6,7]) || !campaign.canDeleteSelectedCampaigns()"
						ng-really-message="{{'label.confirm' | i18n}}" ng-really-click="campaign.remove()" tooltip="{{'tooltip.delete' | i18n}}" tooltip-append-to-body="true">
						<i class="fa fa-trash"></i>
					</button>
				</div>
			</div>
		</div>
	</nav>
	<div class="panel-primary">
		<div class="panel-body">
			<div>
				<div id="campaignGrid" ui-grid="campaign.campaignGridOptions" ui-grid-save-state ui-grid-resize-columns ui-grid-selection ui-grid-auto-resize ui-grid-exporter class="grid"></div>
			</div>
		</div>
	</div>
</div>
<div id="actionLogContainer" class="panel panel-primary" style="position: relative; width: 100%;" ng-controller="actionLogCacheCtrl as actionLogCache">
	<div class="panel-heading">
		<h3 class="panel-title">{{'action.log' | i18n}}</h3>
		<div class="panel-buttons pull-right">
			<button type="button" class="btn btn-default btn-sm" ng-click="actionLogCache.toggleFiltering()" tooltip="{{'tooltip.filter' | i18n}}">
				<i class="fa fa-filter" />
			</button>
			<button type="button" class="btn btn-default btn-sm" ng-click="actionLogCache.triggerDraggable()" tooltip="{{'tooltip.drag' | i18n}}">
				<i class="fa fa-arrows" />
			</button>
		</div>
	</div>
	<draggable obj="{{actionLogCache.draggableCookieKey}}" disabled="actionLogCache.draggableDisabled" width="95.6%" />
	<div class="panel-body">
		<div ng-include="'pages/actionLogCache'"></div>
	</div>
</div>
