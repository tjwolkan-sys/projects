<div class="panel panel-primary" style="margin-top:15px;">
  <div class="panel-heading">
    <h3 class="panel-title">{{ 'campaign.tab.leaveDays' | i18n }}</h3>
    <div class="panel-buttons pull-right">
      <button class="btn btn-default btn-sm"
              type="button"
              ng-click="leaveDays.open('md', leaveDays.newLeaveDay())"
              ng-disabled="!canEditSelectedCampaignByPermission(leaveDays.campaign, 'EditCampaignTech')"
              tooltip="{{ 'tooltip.add' | i18n }}">
        <i class="fa fa-plus"></i>
      </button>

      <button class="btn btn-default btn-sm"
              type="button"
              ng-click="leaveDays.open('md', leaveDays.getSelectedLeaveDay())"
              ng-disabled="leaveDays.disabledEditButton() || !canEditSelectedCampaignByPermission(leaveDays.campaign, 'EditCampaignTech')"
              tooltip="{{ 'tooltip.edit' | i18n }}">
        <i class="fa fa-pencil-square-o"></i>
      </button>

      <button class="btn btn-default btn-sm"
              type="button"
              ng-disabled="leaveDays.selectedLeaveDays.length == 0 || !canEditSelectedCampaignByPermission(leaveDays.campaign, 'EditCampaignTech')"
              ng-really-message="{{ 'campaign.tab.leaveDays.deleteMessage' | i18n }}"
              ng-really-click="leaveDays.remove()"
              tooltip="{{ 'tooltip.delete' | i18n }}">
        <i class="fa fa-trash"></i>
      </button>
    </div>
  </div>

  <div class="panel-body">
    <div>
      <!-- ui-grid needs a height -->
      <div id="leaveDaysGrid"
           class="grid"
           style="height: 360px;"
           ui-grid="leaveDays.leaveDaysGridOptions"
           ui-grid-resize-columns
           ui-grid-selection
           ui-grid-pinning
           ui-grid-auto-resize>
      </div>
    </div>
  </div>
</div>
