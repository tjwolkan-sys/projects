<div class="panel panel-primary">
  <div class="panel-heading">
    <h3 class="panel-title">{{'filterColumn.list' | i18n}}</h3>
    <div class="panel-buttons pull-right">
      <button type="button" class="btn btn-info btn-sm clearfix" onclick="return false"
              ng-disabled="!filterColumn.campaign.connectedToDB" ng-click="filterColumn.test()"
              tooltip="{{'tooltip.filter' | i18n}}">
        <i class="fa fa-flag"></i> {{'filterColumn.test' | i18n}}
      </button>
      <button class="btn btn-default btn-sm" onclick="return false" ng-click="filterColumn.toggleFiltering()" tooltip="{{'tooltip.filter' | i18n}}"
              ng-disabled = "!canEditSelectedCampaignByPermission(filterColumn.campaign, 'EditCampaignNonTech')">
        <i class="fa fa-filter"></i>
      </button>
      <div class="btn-group">
        <button type="button" class="btn btn-default btn-sm dropdown-toggle"
                ng-disabled="filterColumn.selectedFilters.length != 1 || !canEditSelectedCampaignByPermission(filterColumn.campaign, 'EditCampaignTech') && (isTechnicalFilter(filterColumn.selectedFilters[0])
                      || !canEditSelectedCampaignByPermission(filterColumn.campaign,  'EditCampaignNonTech'))" data-toggle="dropdown" aria-expanded="false"
          tooltip="{{'menu.enable' | i18n}}/{{'menu.disable' | i18n}}">
          <i class="fa fa-check-square-o"></i>
        </button>
        <ul class="dropdown-menu">
          <li>
            <a href="javascript:void(0)" ng-click="filterColumn.setEnabled(true)" onclick="return false">{{'menu.enable' | i18n}}</a>
          </li>
          <li>
            <a href="javascript:void(0)" ng-click="filterColumn.setEnabled(false)" onclick="return false">{{'menu.disable' | i18n}}</a>
          </li>
        </ul>
      </div>
      <button class="btn btn-default btn-sm" onclick="return false" ng-click="filterColumn.open('sm', filterColumn.newFilterColumn())" tooltip="{{'tooltip.add' | i18n}}"
              ng-disabled="!canEditSelectedCampaignByPermission(filterColumn.campaign, 'EditCampaignNonTech')">
        <i class="fa fa-plus"></i>
      </button>
      <button class="btn btn-default btn-sm" onclick="return false"
              ng-disabled="filterColumn.selectedFilters.length != 1 || !canEditSelectedCampaignByPermission(filterColumn.campaign, 'EditCampaignNonTech')"
              ng-click="filterColumn.open('sm', filterColumn.selectedFilters[0])" tooltip="{{'tooltip.edit' | i18n}}">
        <i class="fa fa-pencil-square-o"></i>
      </button>
      <button class="btn btn-default btn-sm" onclick="return false"
              ng-disabled="filterColumn.selectedFilters.length == 0 || filterColumn.selectedFilters == undefined
                || !canEditSelectedCampaignByPermission(filterColumn.campaign, 'EditCampaignTech') && (isTechnicalFilter(filterColumn.selectedFilters[0])
                || !canEditSelectedCampaignByPermission(filterColumn.campaign, 'EditCampaignNonTech'))"
      	ng-really-message="{{'label.confirm' | i18n}}"
        ng-really-click="filterColumn.remove()" tooltip="{{'tooltip.delete' | i18n}}">
        <i class="fa fa-trash"></i>
      </button>
    </div>
  </div>
  <div class="panel-body">
    <div id="filterColumnGrid" ui-grid="filterColumn.filterColumnGridOptions" ui-grid-resize-columns ui-grid-selection ui-grid-auto-resize class="grid"></div>
  </div>
</div>
