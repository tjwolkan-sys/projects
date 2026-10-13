<div class="panel panel-primary">
  <div class="panel-heading">
    <h3 class="panel-title">{{'sortField.list' | i18n}}</h3>
    <div class="panel-buttons pull-right">
      <button class="btn btn-default btn-sm" onclick="return false" ng-click="sortField.toggleFiltering()" tooltip="{{'tooltip.filter' | i18n}}"
              ng-disabled ="!canEditSelectedCampaignByPermission(campaign,  'EditCampaign')">
        <i class="fa fa-filter"></i>
      </button>
      <div class="btn-group">
        <button type="button" class="btn btn-default btn-sm dropdown-toggle"
          ng-disabled="sortField.selectedSortField.length == 0 || sortField.selectedSortField == undefined
                      || !canEditSelectedCampaignByPermission(campaign, 'EditCampaign')" data-toggle="dropdown"
          aria-expanded="false" tooltip="{{'menu.enable' | i18n}}/{{'menu.disable' | i18n}}">
          <i class="fa fa-check-square-o"></i>
        </button>
        <ul class="dropdown-menu">
          <li>
            <a href="javascript:void(0)" ng-click="sortField.setEnabled(true)" onclick="return false">{{'menu.enable' | i18n}}</a>
          </li>
          <li>
            <a href="javascript:void(0)" ng-click="sortField.setEnabled(false)" onclick="return false">{{'menu.disable' | i18n}}</a>
          </li>
        </ul>
      </div>
      <button class="btn btn-default btn-sm" onclick="return false" ng-click="sortField.open('lg', sortField.newSortField())"
        tooltip="{{'tooltip.add' | i18n}}" ng-disabled ="!canEditSelectedCampaignByPermission(campaign, 'EditCampaign')">
        <i class="fa fa-plus"></i>
      </button>
      <button class="btn btn-default btn-sm" onclick="return false" ng-disabled="sortField.selectedSortField.length != 1 || !canEditSelectedCampaignByPermission(campaign, 'EditCampaign')"
        ng-click="sortField.open('lg', sortField.selectedSortField[0])" tooltip="{{'tooltip.edit' | i18n}}">
        <i class="fa fa-pencil-square-o"></i>
      </button>
      <button class="btn btn-default btn-sm" onclick="return false"
        ng-disabled="sortField.selectedSortField.length == 0 || sortField.selectedSortField == undefined || !canEditSelectedCampaignByPermission(campaign, 'EditCampaign')"
        ng-really-message="{{'label.confirm' | i18n}}" ng-really-click="sortField.remove()" tooltip="{{'tooltip.delete' | i18n}}">
        <i class="fa fa-trash"></i>
      </button>
    </div>
  </div>
  <div class="panel-body">
    <div>
      <div id="sortFieldGrid" ui-grid="sortField.sortFieldGridOptions" ui-grid-resize-columns ui-grid-selection ui-grid-auto-resize class="grid"></div>
    </div>
  </div>
</div>
