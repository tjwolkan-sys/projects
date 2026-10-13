<div class="panel panel-primary">
  <nav class="navbar-default">
    <div class="container-fluid">
      <div class="navbar-header" style="margin-left: -15px;">
        <button type="button" class="navbarCompainList-toggle navbar-toggle collapsed" data-toggle="collapse" data-target="#navbarCompainList" aria-expanded="false"
          aria-controls="navbar">
          <span class="icon-bar"></span> <span class="icon-bar"></span> <span class="icon-bar"></span>
        </button>
        <div class="navbarCompainList-brand navbar-brand panel-title">{{'action.log' | i18n}}</div>
      </div>
      <div id="navbarCompainList" class="navbar-collapse collapse">
        <div class="navbarCompainList-buttons nav navbar-nav navbar-right" style="padding-left: 10px;">
          <button class="btn btn-default btn-sm navbar-btn" ng-click="actionLog.toggleFiltering()" tooltip="{{'tooltip.filter' | i18n}}" tooltip-append-to-body="true">
            <i class="fa fa-filter"></i>
          </button>
          <button class="btn btn-default btn-sm navbar-btn" ng-disabled="actionLog.selectedActionLogs == undefined || actionLog.selectedActionLogs.length != 1"
            ng-click="actionLog.open('lg', actionLog.selectedActionLogs[0])" tooltip="{{'tooltip.details' | i18n}}" tooltip-append-to-body="true">
            <i class="fa fa-info-circle"></i>
          </button>
        </div>
      </div>
    </div>
  </nav>
  <div class="panel-primary">
    <div class="panel-body">
      <div>
        <div ui-grid="actionLog.actionLogGridOptions" ui-grid-pagination ui-grid-save-state ui-grid-resize-columns ui-grid-selection ui-grid-auto-resize ui-grid-exporter class="grid"></div>
      </div>
    </div>
  </div>
</div>
