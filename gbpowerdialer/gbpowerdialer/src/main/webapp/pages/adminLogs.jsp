<div class="panel panel-primary">
  <div class="panel-heading">
    <h3 class="panel-title">{{'admin.logs' | i18n}}</h3>
    <div class="panel-buttons pull-right">
      <button class="btn btn-default btn-sm" ng-click="admLogs.toggleFiltering()" tooltip="{{'tooltip.filter' | i18n}}" type="button">
        <i class="fa fa-filter"></i>
      </button>
      <button class="btn btn-default btn-sm" ng-disabled="admLogs.selectedAdminLog == null" ng-click="admLogs.open('lg', admLogs.selectedAdminLog)"
        tooltip="{{'tooltip.details' | i18n}}" type="button">
        <i class="fa fa-info-circle"></i>
      </button>
    </div>
  </div>
  <div class="panel-body">
    <div>
      <div id="adminLogsGrid" ui-grid="admLogs.adminLogsGridOptions" ui-grid-pagination ui-grid-save-state ui-grid-resize-columns ui-grid-selection ui-grid-auto-resize ui-grid-exporter external-scopes="$scope" class="grid"></div>
    </div>
  </div>
</div>
