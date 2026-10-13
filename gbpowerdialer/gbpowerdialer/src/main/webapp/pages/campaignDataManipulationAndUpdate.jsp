<div class="panel panel-primary" ng-controller="campaignDataManipulationAndUpdateCtrl as ctrl" ui-i18n>
    <div class="panel-heading">
        <h3 class="panel-title">{{ 'campaignDataManipulation.title' | i18n}}</h3>
    </div>

    <div class="panel-body">
        <div class="panel-heading" style="display: flex; justify-content: space-between; align-items: center;">

            <label class="col-sm-1 control-label" style="font-size: 16px; display: flex; align-items: center;">{{
                'campaignDataManipulation.operation' | i18n}}</label>

            <div class="col-sm-4">
                <select class="form-control ng-valid ng-touched ng-dirty ng-valid-parse" ng-model="ctrl.selectedTenant"
                        ng-options="tenant.tenantId as tenant.tenantName for tenant in ctrl.operations"
                        ng-change="ctrl.loadCampaignsByTenant()">
                    <option value="" class="" selected="selected">{{ 'input.option.select' | i18n}}</option>
                </select>
            </div>

            <label class="col-sm-1 control-label"
                   style="font-size: 16px;display: flex;align-items: center;justify-content: flex-end;"> {{
                'campaignDataManipulation.campaign' | i18n}}</label>
            <div class="col-sm-4">
                <select class="form-control ng-valid ng-dirty ng-valid-parse ng-touched"
                        ng-model="ctrl.selectedCampaign" ng-options="c.id as c.name for c in ctrl.campaigns">
                    <option value="" class="" selected="selected">{{ 'input.option.select' | i18n}}</option>
                </select>
            </div>
            <div ng-if="ctrl.isPriorityCampaign" class="alert alert-warning" style="margin-top: 10px; padding: 6px; font-size: 13px;">
                <i class="fa fa-exclamation-triangle" style="margin-right: 5px;"></i>
                {{ 'campaignDataManipulation.label.priorityCampaign' | i18n }}
            </div>
        </div>



        <div class="panel-body">
            <div class="panel panel-default" style="margin-top: 20px;">
                <div class="panel-heading" style="display: flex; justify-content: space-between; align-items: center;">
                    <div>
                        <button class="btn btn-xs btn-default logic-button" ng-click="ctrl.setLogic('AND')"
                                ng-class="{active: ctrl.logicOperator === 'AND'}">{{ 'campaignDataManipulation.and' |
                            i18n}}
                        </button>
                        <button class="btn btn-xs btn-default logic-button" ng-click="ctrl.setLogic('OR')"
                                ng-class="{active: ctrl.logicOperator === 'OR'}">{{ 'campaignDataManipulation.or' |
                            i18n}}
                        </button>
                    </div>
                    <div>
                        <button class="btn btn-xs btn-info" ng-click="ctrl.testFilters()">{{
                            'campaignDataManipulation.test' | i18n}}
                        </button>
                        <button class="btn btn-xs btn-success" ng-click="ctrl.addFilter()">{{
                            'campaignDataManipulation.add' | i18n}}
                        </button>
                        <button class="btn btn-xs btn-warning" ng-click="ctrl.clearFilters()">{{
                            'campaignDataManipulation.clear' | i18n}}
                        </button>
                    </div>
                </div>
                <div class="panel-body">
                    <div ng-repeat="f in ctrl.filterRows" class="row filter-row" style="margin-bottom: 8px;">
                        <div class="col-sm-5 col-md-3">
                            <select class="form-control" ng-model="f.field"
                                    ng-options="col.name as col.name for col in ctrl.columns"
                                    ng-change="ctrl.updateFieldType(f)">
                            </select>
                        </div>
                        <div class="col-sm-2 col-md-2">
                            <select class="form-control" ng-model="f.condition" ng-change="ctrl.updateCondition(f)">
                                <option value="EQUAL">EQUAL TO</option>
                                <option value="NOTEQUAL">NOT EQUAL TO</option>
                                <option value="GREATER">GREATER THAN</option>
                                <option value="LESS">LESS THAN</option>
                                <option value="GREATEROREQUAL">GREATER THAN OR EQUAL</option>
                                <option value="LESSOREQUAL">LESS THAN OR EQUAL</option>
                                <option value="LIKE">LIKE</option>
                                <option value="NOTLIKE">NOT LIKE</option>
                                <option value="IN">IN</option>
                                <option value="NOTIN">NOT IN</option>
                                <option value="ISNULL">IS NULL</option>
                                <option value="ISNOTNULL">IS NOT NULL</option>
                            </select>
                        </div>
                        <div class="col-sm-3 col-md-5">
                            <input type="text" class="form-control" ng-model="f.value" ng-disabled="ctrl.isNullCondition(f.condition)"/>
                        </div>
                        <div class="col-sm-1 col-md-2 d-flex align-items-center">
                            <button class="btn btn-danger" ng-click="ctrl.filterRows.splice($index, 1)"
                                    style="font-size: 12px; padding: 12px 6px;">X
                            </button>
                        </div>
                    </div>
                </div>
            </div>
        </div>

        <div style="margin-top: 10px;">
            <button class="btn btn-warning" ng-click="ctrl.fetchRecords()">{{ 'campaignDataManipulation.getRecords' |
                i18n}}
            </button>
        </div>

        <div class="panel panel-default" ng-show="ctrl.dataVisible" style="margin-top: 20px;">
            <div class="panel-body">
                <ul class="nav nav-tabs" style="margin-bottom:10px;">
                    <li ng-class="{active: ctrl.activeTab==='records'}">
                        <a href="" ng-click="ctrl.setActiveTabAndRefresh('records')"> <h3 class="panel-title">{{ 'campaignDataManipulation.records' | i18n}}</h3></a>
                    </li>

                    <li ng-class="{active: ctrl.activeTab==='userRC'}">
                        <a href="" ng-click="ctrl.setActiveTabAndRefresh('userRC')"><h3 class="panel-title">{{ 'campaignDataManipulation.userRc' | i18n}}</h3></a>
                    </li>

                    <li ng-class="{active: ctrl.activeTab==='telephonyRC'}">
                        <a href="" ng-click="ctrl.setActiveTabAndRefresh('telephonyRC')"><h3 class="panel-title">{{ 'campaignDataManipulation.telephoneRc' | i18n}}</h3></a>
                    </li>
                </ul>

                <div class="tab-content">
                    <div class="tab-pane"
                         ng-class="{'active': ctrl.activeTab==='records', 'in': ctrl.activeTab==='records'}">
                        <div id="recordGrid"
                             ui-grid="ctrl.gridOptions"
                             ui-grid-pagination
                             ui-grid-selection
                             ui-grid-resize-columns
                             ui-grid-auto-resize
                             class="grid"
                             style="height:320px;"></div>
                    </div>

                    <div class="tab-pane"
                         ng-class="{'active': ctrl.activeTab==='userRC', 'in': ctrl.activeTab==='userRC'}">
                        <div id="userRCGrid"
                             ui-grid="ctrl.userResultCodeGridOptions"
                             ui-grid-pagination
                             ui-grid-resize-columns
                             ui-grid-auto-resize
                             class="grid"
                             style="height:320px;"></div>
                    </div>

                    <div class="tab-pane"
                         ng-class="{'active': ctrl.activeTab==='telephonyRC', 'in': ctrl.activeTab==='telephonyRC'}">
                        <div id="telephonyResultCodeGrid"
                             ui-grid="ctrl.telephonyResultCodeGridOptions"
                             ui-grid-pagination
                             ui-grid-resize-columns
                             ui-grid-auto-resize
                             class="grid"
                             style="height:320px;"></div>
                    </div>
                </div>

            </div>
        </div>

        <div class="modal fade" id="filterTestModal" tabindex="-1" role="dialog" aria-labelledby="filterTestModalLabel">
            <div class="modal-dialog modal-lg" role="document">
                <div class="modal-content">
                    <div class="modal-header">
                        <h4 class="modal-title">{{ 'campaignDataManipulation.filterResults' | i18n}} <span
                                class="badge">Adet: {{ctrl.testResultCount}}</span></h4>
                    </div>
                    <div class="modal-body">
                        <div id="testGrid"
                             ui-grid="ctrl.testGridOptions"
                             ui-grid-pagination
                             ui-grid-resize-columns
                             ui-grid-auto-resize
                             class="grid">
                        </div>
                    </div>
                    <div class="modal-footer">
                        <button type="button" class="btn btn-default" data-dismiss="modal">{{
                            'campaignDataManipulation.close' | i18n}}
                        </button>
                    </div>
                </div>
            </div>
        </div>


        <div class="form-group row" style="margin-top: 25px;">
            <label class="col-sm-2 control-label">{{ 'campaignDataManipulation.operationType' | i18n }}:</label>
            <div class="col-sm-4">
                <div style="display:flex; align-items:center;">
                    <select class="form-control" ng-model="ctrl.update.operation">
                        <option value="">{{ 'input.option.select' | i18n }}</option>
                        <option value="ACTIVATE_RECORD">{{ 'campaignDataManipulation.activateRecord' | i18n }}</option>
                        <option value="ADD_CALL_AS_PRIORITY">{{ 'campaignDataManipulation.addPriority' | i18n }}</option>
                        <option value="REMOVE_CALL_FROM_PRIORITY">{{ 'campaignDataManipulation.removePriority' | i18n }}</option>
                        <option value="CONTACT_LIST_BULK_UPDATE">{{ 'campaignDataManipulation.bulkContactUpdate' | i18n }}</option>
                        <option value="DELETE_RECORD">{{ 'campaignDataManipulation.deleteRecord' | i18n }}</option>
                    </select>
                    <span ng-show="ctrl.operationHintText"
                          id="operationHint"
                          class="text-info"
                          ng-attr-title="{{ ctrl.operationHintText }}"
                          style="margin-left:8px; cursor:help;">
                <i class="fa fa-info-circle"></i>
            </span>
                </div>
                <div ng-if="ctrl.lastUpdatedCountMsg"
                     class="text-success"
                     style="margin-top:6px;">
                    {{ ctrl.lastUpdatedCountMsg }}
                </div>
            </div>


        </div>

        <!-- Properly wrapped deletion reason row -->
        <div class="form-group row" ng-show="ctrl.update.operation === 'DELETE_RECORD'">
            <label class="col-sm-2 control-label">{{ 'campaignDataManipulation.deletionReason' | i18n }}:</label>
            <div class="col-sm-4">
                <select class="form-control" ng-model="ctrl.update.reasonForDeletion">
                    <option value="">{{ 'input.option.select' | i18n }}</option>
                    <option value="REMOVED_BY_BANK_REQUEST">{{ 'campaignDataManipulation.eliminationReason.bank' | i18n }}</option>
                    <option value="COMPLAINT">{{ 'campaignDataManipulation.eliminationReason.complaint' | i18n }}</option>
                    <option value="COUNTRY">{{ 'campaignDataManipulation.eliminationReason.country' | i18n }}</option>
                    <option value="LIST_CLOSURE">{{ 'campaignDataManipulation.eliminationReason.list' | i18n }}</option>
                    <option value="SYSTEM_ISSUE">{{ 'campaignDataManipulation.eliminationReason.system' | i18n }}</option>
                    <option value="REACHED_RECORD_CLOSURE">{{ 'campaignDataManipulation.eliminationReason.reached' | i18n }}</option>
                </select>
            </div>
        </div>



        <div class="form-group row" ng-show="ctrl.update.operation === 'CONTACT_LIST_BULK_UPDATE'">
            <label class="col-sm-2 control-label">{{ 'campaignDataManipulation.updateField' | i18n }}:</label>
            <div class="col-sm-4">
                <select class="form-control"
                        ng-model="ctrl.update.field"
                        ng-options="col.name as col.name for col in ctrl.columns">
                    <option value="">{{ 'input.option.select' | i18n }}</option>
                </select>
            </div>

            <label class="col-sm-2 control-label">{{ 'campaignDataManipulation.updateValue' | i18n }}:</label>
            <div class="col-sm-4">
                <input type="text" class="form-control" ng-model="ctrl.update.value" placeholder="{{ 'campaignDataManipulation.updateValue' | i18n }}">
            </div>
        </div>


        <div class="form-group row" style="margin-top: 15px;">
            <div class="col-sm-offset-2 col-sm-10 text-right">
                <button class="btn btn-success" ng-click="ctrl.applyUpdate()">
                    {{ 'campaignDataManipulation.updateButton' | i18n }}
                </button>
            </div>
        </div>



        <div id="successModal" class="modal fade" tabindex="-1" role="dialog" aria-labelledby="successModalLabel">
            <div class="modal-dialog modal-sm" role="document">
                <div class="modal-content">
                    <div class="modal-header bg-success text-white">
                        <button type="button" class="close" data-dismiss="modal">&times;</button>
                        <h4 class="modal-title" id="successModalLabel">{{ 'campaignDataManipulation.success' | i18n }}</h4>
                    </div>
                    <div class="modal-body">
                        <p id="successModalMessage"></p>
                    </div>
                    <div class="modal-footer">
                        <button type="button" class="btn btn-success" data-dismiss="modal">{{ 'campaignDataManipulation.ok' | i18n }}</button>
                    </div>
                </div>
            </div>
        </div>

        <div class="modal fade" id="warningModal" tabindex="-1" role="dialog" aria-labelledby="warningModalLabel">
            <div class="modal-dialog modal-sm" role="document">
                <div class="modal-content">
                    <div class="modal-header bg-warning">
                        <h4 class="modal-title" id="warningModalLabel">{{ 'campaignDataManipulation.warning' |
                            i18n}}</h4>
                    </div>
                    <div class="modal-body" id="warningModalMessage">
                    </div>
                    <div class="modal-footer">
                        <button type="button" class="btn btn-warning" data-dismiss="modal">{{
                            'campaignDataManipulation.ok' | i18n}}
                        </button>
                    </div>
                </div>
            </div>
        </div>

        <div class="modal fade" id="errorModal" tabindex="-1" role="dialog" aria-labelledby="errorModalLabel">
            <div class="modal-dialog modal-sm" role="document">
                <div class="modal-content">
                    <div class="modal-header" style="background-color: #f2dede;">
                        <h4 class="modal-title" id="errorModalLabel">{{ 'campaignDataManipulation.error' | i18n }}</h4>
                    </div>
                    <div class="modal-body" id="errorModalMessage">
                    </div>
                    <div class="modal-footer">
                        <button type="button" class="btn btn-danger" data-dismiss="modal">{{ 'campaignDataManipulation.ok' | i18n }}</button>
                    </div>
                </div>
            </div>
        </div>

        <div class="modal fade" id="confirmationModal" tabindex="-1" role="dialog" aria-labelledby="confirmationModalLabel">
            <div class="modal-dialog modal-sm" role="document">
                <div class="modal-content">
                    <div class="modal-header" style="background-color: #d9edf7;">
                        <h4 class="modal-title" id="confirmationModalLabel">{{ 'campaignDataManipulation.confirmation' | i18n }}</h4>
                    </div>
                    <div class="modal-body" id="confirmationModalMessage">
                    </div>
                    <div class="modal-footer">
                        <button type="button" class="btn btn-primary" id="confirmationModalOk">{{ 'campaignDataManipulation.ok' | i18n }}</button>
                        <button type="button" class="btn btn-default" data-dismiss="modal">{{ 'campaignDataManipulation.cancel' | i18n }}</button>
                    </div>
                </div>
            </div>
        </div>


</div>

    </div>
