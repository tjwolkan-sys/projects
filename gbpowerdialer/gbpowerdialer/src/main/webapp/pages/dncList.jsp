<div class="panel panel-primary">
  <div class="panel-heading">
    <h3 class="panel-title">{{'dnc.list' | i18n}}</h3>
    <div class="panel-buttons pull-right">
      <button class="btn btn-default btn-sm" ng-click="dnc.toggleFiltering()" tooltip="{{'tooltip.filter' | i18n}}" type="button">
        <i class="fa fa-filter"></i>
      </button>
      <div class="btn-group">
        <button type="button" class="btn btn-default btn-sm dropdown-toggle" ng-disabled="dnc.selectedDNCs == undefined || dnc.selectedDNCs.length == 0" data-toggle="dropdown"
          aria-expanded="false" tooltip="{{'menu.enable' | i18n}}/{{'menu.disable' | i18n}}">
          <i class="fa fa-check-square-o"></i>
        </button>
        <ul class="dropdown-menu">
          <li>
            <a href="javascript:void(0)" ng-click="dnc.setEnabled(true)" onclick="return false">{{'menu.enable' | i18n}}</a>
          </li>
          <li>
            <a href="javascript:void(0)" ng-click="dnc.setEnabled(false)" onclick="return false">{{'menu.disable' | i18n}}</a>
          </li>
        </ul>
      </div>
      <button class="btn btn-default btn-sm" ng-click="dnc.open('lg', dnc.newDnc())" tooltip="{{'tooltip.add' | i18n}}" type="button"
              ng-disabled ="!hasAnyRole(['ROLE_SUPERADMIN', 'ROLE_TENANT_ADMIN','ROLE_SUPERVISOR'])">
        <i class="fa fa-plus"></i>
      </button>
      <button class="btn btn-default btn-sm" ng-disabled="dnc.selectedDNCs.length != 1" type="button" ng-click="dnc.open('lg', dnc.selectedDNCs[0])"
        tooltip="{{'tooltip.edit' | i18n}}">
        <i class="fa fa-pencil-square-o"></i>
      </button>
      <button class="btn btn-default btn-sm" type="button"
              ng-disabled="!hasAnyRole(['ROLE_SUPERADMIN', 'ROLE_TENANT_ADMIN','ROLE_SUPERVISOR']) || dnc.selectedDNCs == undefined || dnc.selectedDNCs.length == 0"
        ng-really-message="{{'label.confirm' | i18n}}" ng-really-click="dnc.remove()" tooltip="{{'tooltip.delete' | i18n}}">
        <i class="fa fa-trash"></i>
      </button>
    </div>
  </div>
  <div class="panel-body">
    <div>
      <div id="dncGrid" ui-grid="dnc.dncGridOptions" ui-grid-pagination ui-grid-save-state ui-grid-resize-columns ui-grid-selection ui-grid-auto-resize ui-grid-exporter class="grid"></div>
    </div>
  </div>
</div>
