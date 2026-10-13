<div class="panel panel-primary">
  <div class="panel-heading">
    <h3 class="panel-title">{{'server.list' | i18n}}</h3>
    <div class="panel-buttons pull-right">
      <button class="btn btn-success btn-sm" ng-click="server.connect()" ng-disabled="server.selectionIsEmpty || server.isNeedDisableConnectBtn()">
        <i class="fa fa-plug"></i> {{'server.connect' | i18n}}
      </button>
      <button class="btn btn-danger btn-sm" ng-really-message="{{'label.confirm' | i18n}}" ng-really-click="server.disconnect()"
        ng-disabled="server.selectionIsEmpty || server.isNeedDisableDisconnectBtn()">
        <i class="fa fa-times"></i> {{'server.disconnect' | i18n}}
      </button>
      <button class="btn btn-default btn-sm" ng-click="server.toggleFiltering()" tooltip="{{'tooltip.filter' | i18n}}">
        <i class="fa fa-filter"></i>
      </button>
    </div>
  </div>
  <div class="panel-body">
    <div>
      <div id="serverGrid" ui-grid="server.serverGridOptions" ui-grid-pagination ui-grid-save-state ui-grid-resize-columns ui-grid-selection ui-grid-auto-resize ui-grid-exporter class="grid"></div>
    </div>
  </div>
</div>
