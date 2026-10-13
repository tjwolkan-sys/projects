<div class="panel panel-primary">
  <div class="panel-heading">
    <h3 class="panel-title">{{'resultCodeSchedule.list' | i18n}}</h3>
    <div class="panel-buttons pull-right">
      <button class="btn btn-default btn-sm" onclick="return false" ng-click="resultCodeSchedule.toggleFiltering()" tooltip="{{'tooltip.filter' | i18n}}"
        ng-disabled = "!canEditSelectedCampaignByPermission(resultCodeSchedule.campaign, 'EditCampaign') || !resultCodeSchedule.resultCodeScheduleListEnabled()">
        <i class="fa fa-filter"></i>
      </button>
      <div class="btn-group">
        <button type="button" class="btn btn-default btn-sm dropdown-toggle"
          ng-disabled="resultCodeSchedule.selectedResultCodeSchedules.length == 0 || resultCodeSchedule.selectedResultCodeSchedules == undefined
          || !canEditSelectedCampaignByPermission(resultCodeSchedule.campaign, 'EditCampaign') || !resultCodeSchedule.resultCodeScheduleListEnabled()" data-toggle="dropdown"
          aria-expanded="false" tooltip="{{'menu.enable' | i18n}}/{{'menu.disable' | i18n}}">
          <i class="fa fa-check-square-o"></i>
        </button>
        <ul class="dropdown-menu">
          <li>
            <a href="javascript:void(0)" ng-click="resultCodeSchedule.setEnabled(true)" onclick="return false">{{'menu.enable' | i18n}}</a>
          </li>
          <li>
            <a href="javascript:void(0)" ng-click="resultCodeSchedule.setEnabled(false)" onclick="return false">{{'menu.disable' | i18n}}</a>
          </li>
        </ul>
      </div>
      <button class="btn btn-default btn-sm" onclick="return false" ng-click="resultCodeSchedule.open('lg', resultCodeSchedule.newResultCodeSchedule())"
              ng-disabled = "!canEditSelectedCampaignByPermission(resultCodeSchedule.campaign, 'EditCampaign') || !resultCodeSchedule.resultCodeScheduleListEnabled()"
              tooltip="{{'tooltip.add' | i18n}}">
        <i class="fa fa-plus"></i>
      </button>
      <button class="btn btn-default btn-sm" onclick="return false" ng-disabled="resultCodeSchedule.selectedResultCodeSchedules.length != 1
      || !canEditSelectedCampaignByPermission(resultCodeSchedule.campaign, 'EditCampaign') || !resultCodeSchedule.resultCodeScheduleListEnabled()"
        ng-click="resultCodeSchedule.open('lg', resultCodeSchedule.selectedResultCodeSchedules[0])" tooltip="{{'tooltip.edit' | i18n}}">
        <i class="fa fa-pencil-square-o"></i>
      </button>
      <button class="btn btn-default btn-sm" onclick="return false"
        ng-disabled="resultCodeSchedule.selectedResultCodeSchedules.length == 0 || resultCodeSchedule.selectedResultCodeSchedules == undefined
        || !canEditSelectedCampaignByPermission(resultCodeSchedule.campaign, 'EditCampaign') || !resultCodeSchedule.resultCodeScheduleListEnabled()"
        ng-really-message="{{'label.confirm' | i18n}}" ng-really-click="resultCodeSchedule.remove()" tooltip="{{'tooltip.delete' | i18n}}">
        <i class="fa fa-trash"></i>
      </button>
    </div>
  </div>
  <div class="panel-body">
    <div>
      <div id="resultCodeScheduleGrid" ui-grid="resultCodeSchedule.resultCodeScheduleGridOptions" ui-grid-resize-columns ui-grid-selection ui-grid-auto-resize class="grid"></div>
    </div>
  </div>
</div>