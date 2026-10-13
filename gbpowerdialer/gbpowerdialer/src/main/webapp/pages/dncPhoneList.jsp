<div class="panel panel-primary">
  <div class="panel-heading">
    <h3 class="panel-title">{{'dnc.numbers' | i18n}}</h3>
    <div class="panel-buttons pull-right">
      <button class="btn btn-default btn-sm" ng-click="dncphone.selectAll()"
              tooltip="{{'tooltip.selectDeselectAll' | i18n}}" type="button">
        <i class="fa fa-list"></i>
      </button>
      <div class="btn-group">
        <button ng-disabled="dncphone.dncList.id == undefined" type="button" class="btn btn-default btn-sm dropdown-toggle" data-toggle="dropdown"
                aria-expanded="false" tooltip="{{'dnc.pn.import.add.delete' | i18n}}">
          <i class="fa fa-download"></i>
        </button>
        <ul class="dropdown-menu">
          <li>
            <a href="javascript:void(0)" ng-click="dncphone.dncPnFileImportBatchAddOpenForm('sm')" onclick="return false">
              {{'dnc.pn.file.import.batch.add.dropdown' | i18n}}</a>
          </li>
          <li>
            <a href="javascript:void(0)" ng-click="dncphone.dncPnFileImportBatchDeleteOpenForm('sm')" onclick="return false">
              {{'dnc.pn.file.import.batch.delete.dropdown' | i18n}}</a>
          </li>
        </ul>
      </div>
      <button class="btn btn-default btn-sm" ng-click="dncphone.toggleFiltering()" tooltip="{{'tooltip.filter' | i18n}}" type="button">
        <i class="fa fa-filter"></i>
      </button>
      <div class="btn-group">
        <button type="button" class="btn btn-default btn-sm dropdown-toggle" ng-disabled="dncphone.selectionIsEmptyPN"
                data-toggle="dropdown" aria-expanded="false" tooltip="{{'menu.enable' | i18n}}/{{'menu.disable' | i18n}}">
          <i class="fa fa-check-square-o"></i>
        </button>
        <ul class="dropdown-menu">
          <li>
            <a href="javascript:void(0)" ng-really-click="dncphone.setEnabled(true)" ng-really-click-disabled="{{!dncphone.isAllDncPnSelected}}"
               ng-really-message="{{dncphone.getNgReallyMessageForBatchUpdate('enable')}}" onclick="return false">{{'menu.enable' | i18n}}</a>
          </li>
          <li>
            <a href="javascript:void(0)" ng-really-click="dncphone.setEnabled(false)" ng-really-click-disabled="{{!dncphone.isAllDncPnSelected}}"
               ng-really-message="{{dncphone.getNgReallyMessageForBatchUpdate('disable')}}" onclick="return false">{{'menu.disable' | i18n}}</a>
          </li>
        </ul>
      </div>
      <button class="btn btn-default btn-sm" ng-click="dncphone.open('sm')" tooltip="{{'tooltip.add' | i18n}}" type="button">
        <i class="fa fa-plus"></i>
      </button>
      <button id="dncPnDelete" class="btn btn-default btn-sm" ng-disabled="dncphone.selectionIsEmptyPN"
              ng-really-message="{{dncphone.getNgReallyMessageForBatchUpdate('delete')}}" ng-really-click="dncphone.remove()"
              tooltip="{{'tooltip.delete' | i18n}}" type="button">
        <i class="fa fa-trash"></i>
      </button>
    </div>
  </div>
  <div class="panel-body">
    <div>
      <div id="dncPNGrid" ui-grid="dncphone.dncPNGridOptions"
           ui-grid-pagination ui-grid-resize-columns ui-grid-selection ui-grid-auto-resize class="grid"></div>
    </div>
  </div>
</div>
