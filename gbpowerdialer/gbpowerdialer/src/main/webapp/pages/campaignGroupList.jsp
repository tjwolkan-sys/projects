<div class="panel panel-primary">
  <div class="panel-heading">
    <h3 class="panel-title">{{'campaignGroup.list' | i18n}}</h3>
    <div class="panel-buttons pull-right">
      <button class="btn btn-default btn-sm" onclick="return false" ng-click="campaignGroup.toggleFiltering()" tooltip="{{'tooltip.filter' | i18n}}">
        <i class="fa fa-filter"></i>
      </button>
      <div class="btn-group">
        <button type="button" class="btn btn-default btn-sm dropdown-toggle"
          ng-disabled="campaignGroup.selectedCampaignGroups.length == 0 || campaignGroup.selectedCampaignGroups == undefined" data-toggle="dropdown" aria-expanded="false"
          tooltip="{{'menu.enable' | i18n}}/{{'menu.disable' | i18n}}">
          <i class="fa fa-check-square-o"></i>
        </button>
        <ul class="dropdown-menu">
          <li>
            <a href="javascript:void(0)" ng-click="campaignGroup.setEnabled(true)" onclick="return false">{{'menu.enable' | i18n}}</a>
          </li>
          <li>
            <a href="javascript:void(0)" ng-click="campaignGroup.setEnabled(false)" onclick="return false">{{'menu.disable' | i18n}}</a>
          </li>
        </ul>
      </div>
      <button class="btn btn-default btn-sm" onclick="return false" ng-click="campaignGroup.open('sm', campaignGroup.newCampaignGroup())" tooltip="{{'tooltip.add' | i18n}}">
        <i class="fa fa-plus"></i>
      </button>
      <button class="btn btn-default btn-sm" onclick="return false" ng-disabled="campaignGroup.selectedCampaignGroups.length != 1"
        ng-click="campaignGroup.open('sm', campaignGroup.selectedCampaignGroups[0])" tooltip="{{'tooltip.edit' | i18n}}">
        <i class="fa fa-pencil-square-o"></i>
      </button>
      <button class="btn btn-default btn-sm" onclick="return false"
        ng-disabled="campaignGroup.selectedCampaignGroups.length == 0 || campaignGroup.selectedCampaignGroups == undefined" ng-really-message="{{'label.confirm' | i18n}}"
        ng-really-click="campaignGroup.remove()" tooltip="{{'tooltip.delete' | i18n}}">
        <i class="fa fa-trash"></i>
      </button>
    </div>
  </div>
  <div class="panel-body">
    <div>
      <div id="campaignGroupGrid" ui-grid="campaignGroup.campaignGroupGridOptions" ui-grid-resize-columns ui-grid-selection ui-grid-auto-resize class="grid"></div>
    </div>
  </div>
</div>
<div class="row">
  <div class="col-lg-2-5">
    <div class="form-group"
         ng-class="{ 'has-error' : (campaignForm.$submitted || campaignForm.campaignResultCodeTimer.$touched) && campaignForm.campaignResultCodeTimer.$invalid }">
      <label for="campaign.resultCodeTimer">{{'campaign.resultCodeTimer' | i18n}} </label>
      <input id="campaign.resultCodeTimer" name="campaignResultCodeTimer" type="number" min="0" class="form-control input-sm" ng-model="campaign.item.resultCodeTimer"
             ng-disabled = "!canEditSelectedCampaignByPermission(campaign.item, 'EditCampaign') || !campaign.item.commonResultStatuses">
    </div>
  </div>
  <div class="col-lg-2-5">
    <div class="form-group"
         ng-class="{ 'has-error' : (campaignForm.$submitted || campaignForm.resultCodeTimerStatus.$touched) && campaignForm.resultCodeTimerStatus.$invalid }">
      <label for="campaign.resultCodeTimerStatus" ng-hide="true">
        {{'campaign.resultCodeTimerStatus' | i18n}}
        <span ng-if="campaign.item.type == 4" style="color: red;">*</span>
      </label>
      <select id="campaign.resultCodeTimerStatus" ng-hide="true" name="resultCodeTimerStatus" class="form-control input-sm" ng-model="campaign.item.resultCodeTimerStatus"
              ng-options="item.id as item.statusText for item in campaign.item.commonResultStatuses"
              ng-disabled="!canEditSelectedCampaignByPermission(campaign.item, 'EditCampaign') || !campaign.item.commonResultStatuses" ng-error-messages>
        <option value="">{{'input.option.select' | i18n}}</option>
      </select>
    </div>
  </div>
</div>
<hr class="divider"/>