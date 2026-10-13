<div class="panel panel-primary">
  <div class="panel-heading">
    <h3 class="panel-title">{{'campaign.schedule' | i18n}}</h3>
    <div class="panel-buttons pull-right">
      <div class="btn-group">
        <button type="button" class="btn btn-default btn-sm dropdown-toggle"
          ng-disabled="campaignSchedule.selectedSchedules.length == 0 && campaignSchedule.selectedChildSchedules.length == 0 || !canEditSelectedCampaignByPermission(campaign, 'EditCampaignTech')"
                data-toggle="dropdown" aria-expanded="false"
          tooltip="{{'menu.enable' | i18n}}/{{'menu.disable' | i18n}}">
          <i class="fa fa-check-square-o"></i>
        </button>
        <ul class="dropdown-menu">
          <li>
            <a href="javascript:void(0)" ng-click="campaignSchedule.setEnabled(true)" onclick="return false">{{'menu.enable' | i18n}}</a>
          </li>
          <li>
            <a href="javascript:void(0)" ng-click="campaignSchedule.setEnabled(false)" onclick="return false">{{'menu.disable' | i18n}}</a>
          </li>
        </ul>
      </div>
      <button class="btn btn-default btn-sm" ng-click="campaignSchedule.open('sm', campaignSchedule.newCampaignSchedule())" tooltip="{{'tooltip.add' | i18n}}" type="button"
        ng-disabled = "!canEditSelectedCampaignByPermission(campaign, 'EditCampaignTech')">
        <i class="fa fa-plus"></i>
      </button>
      <button class="btn btn-default btn-sm" type="button" ng-click="campaignSchedule.open('sm', campaignSchedule.getSelectedSchedule())"
        ng-disabled="campaignSchedule.disabledEditButton() || !canEditSelectedCampaignByPermission(campaign, 'EditCampaignTech')" tooltip="{{'tooltip.edit' | i18n}}">
        <i class="fa fa-pencil-square-o"></i>
      </button>
      <button class="btn btn-default btn-sm" type="button" ng-disabled="campaignSchedule.selectedSchedules.length == 0 && campaignSchedule.selectedChildSchedules.length == 0
        || !canEditSelectedCampaignByPermission(campaign, 'EditCampaignTech')"
        ng-really-message="{{'label.confirm' | i18n}}" ng-really-click="campaignSchedule.remove()" tooltip="{{'tooltip.delete' | i18n}}">
        <i class="fa fa-trash"></i>
      </button>
    </div>
  </div>
  <div class="panel-body">
    <div>
      <div id="campaignSchedule" ui-grid="campaignSchedule.campaignScheduleGridOptions" ui-grid-resize-columns ui-grid-selection ui-grid-pinning ui-grid-auto-resize class="grid"></div>
    </div>
  </div>
</div>
