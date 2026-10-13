<div class="panel panel-primary">
    <div class="panel-heading">
        <h3 class="panel-title">{{'location.list' | i18n}}</h3>
        <div class="panel-buttons pull-right">
            <button class="btn btn-default btn-sm" ng-click="location.toggleFiltering()" tooltip="{{'tooltip.filter' | i18n}}">
                <i class="fa fa-filter"></i>
            </button>
        </div>
    </div>
    <div class="panel-body">
        <div>
            <div id="locationGrid" ui-grid="location.locationGridOptions" ui-grid-pagination ui-grid-save-state ui-grid-resize-columns ui-grid-selection ui-grid-auto-resize ui-grid-exporter class="grid"></div>
        </div>
    </div>
</div>
