<div class="panel panel-primary">
  <div class="panel-heading">
    <h3 class="panel-title">{{'phoneNumberColumn.list' | i18n}}</h3>
    <div class="panel-buttons pull-right">
      <button class="btn btn-default btn-sm" onclick="return false" ng-click="phoneNumberColumn.toggleFiltering()" tooltip="{{'tooltip.filter' | i18n}}"
              ng-disabled ="!canEditSelectedCampaignByPermission(campaign,  'EditCampaign')">
        <i class="fa fa-filter"></i>
      </button>
      <div class="btn-group">
        <button type="button" class="btn btn-default btn-sm dropdown-toggle"
          ng-disabled="phoneNumberColumn.selectedPhoneNumberColumn.length == 0 || phoneNumberColumn.selectedPhoneNumberColumn == undefined
                      || !canEditSelectedCampaignByPermission(campaign, 'EditCampaign')" data-toggle="dropdown"
          aria-expanded="false" tooltip="{{'menu.enable' | i18n}}/{{'menu.disable' | i18n}}">
          <i class="fa fa-check-square-o"></i>
        </button>
        <ul class="dropdown-menu">
          <li>
            <a href="javascript:void(0)" ng-click="phoneNumberColumn.setEnabled(true)" onclick="return false">{{'menu.enable' | i18n}}</a>
          </li>
          <li>
            <a href="javascript:void(0)" ng-click="phoneNumberColumn.setEnabled(false)" onclick="return false">{{'menu.disable' | i18n}}</a>
          </li>
        </ul>
      </div>
      <button class="btn btn-default btn-sm" onclick="return false" ng-click="phoneNumberColumn.open('sm', phoneNumberColumn.newPhoneNumberColumn())"
        tooltip="{{'tooltip.add' | i18n}}" ng-disabled ="!canEditSelectedCampaignByPermission(campaign, 'EditCampaign')">
        <i class="fa fa-plus"></i>
      </button>
      <button class="btn btn-default btn-sm" onclick="return false" ng-click="phoneNumberColumn.createDefault()"
              ng-if="phoneNumberColumn.campaign.connectedToDB && phoneNumberColumn.campaign.dbListTable"
              ng-disabled ="!canEditSelectedCampaignByPermission(campaign, 'EditCampaign')"
              tooltip="{{'tooltip.add.default' | i18n}}">
        <i class="fa fa-plus-circle"></i>
      </button>
      <button class="btn btn-default btn-sm" onclick="return false" ng-disabled="phoneNumberColumn.selectedPhoneNumberColumn.length != 1 || !canEditSelectedCampaignByPermission(campaign, 'EditCampaign')"
        ng-click="phoneNumberColumn.open('sm', phoneNumberColumn.selectedPhoneNumberColumn[0])" tooltip="{{'tooltip.edit' | i18n}}">
        <i class="fa fa-pencil-square-o"></i>
      </button>
      <button class="btn btn-default btn-sm" onclick="return false"
        ng-disabled="phoneNumberColumn.selectedPhoneNumberColumn.length == 0 || phoneNumberColumn.selectedPhoneNumberColumn == undefined || !canEditSelectedCampaignByPermission(campaign, 'EditCampaign')"
        ng-really-message="{{'label.confirm' | i18n}}" ng-really-click="phoneNumberColumn.remove()" tooltip="{{'tooltip.delete' | i18n}}">
        <i class="fa fa-trash"></i>
      </button>
    </div>
  </div>
  <div class="panel-body">
    <div>
      <div id="phoneNumberColumnGrid" ui-grid="phoneNumberColumn.phoneNumberColumnGridOptions" ui-grid-resize-columns ui-grid-selection ui-grid-auto-resize class="grid"></div>
    </div>
  </div>
</div>
