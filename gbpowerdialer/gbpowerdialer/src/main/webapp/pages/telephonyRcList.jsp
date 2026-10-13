<div class="panel panel-primary">
  <div class="panel-heading">
    <h3 class="panel-title">{{'telephonyRc.telephonyRclist' | i18n}}</h3>
    <div class="panel-buttons pull-right">
      <button class="btn btn-default btn-sm" ng-click="telephonyRc.toggleFiltering()" tooltip="{{'tooltip.filter' | i18n}}">
        <i class="fa fa-filter"></i>
      </button>
    </div>
  </div>
  <div class="panel-body">
    <div>
      <div id="telephonyRcGrid" ui-grid="telephonyRc.telephonyRcGridOptions" ui-grid-resize-columns ui-grid-save-state ui-grid-selection ui-grid-auto-resize ui-grid-exporter class="grid"></div>
    </div>
  </div>
</div>
