<div id="loginModal" class="modal fade" role="dialog" data-keyboard="false" data-backdrop="static">
  <div class="modal-dialog modal-sm">
    <div class="modal-content">
      <form name="loginForm" ng-submit="loginHandler()" novalidate>
        <div class="modal-header">
          <h4 class="modal-title">{{'login.title' | i18n}}</h4>
        </div>
        <div class="modal-body">
          <div id="alertLogin" class="alert alert-dismissible alert-danger" style="display: none;"></div>
          <div ng-show="${appConfig['oneaccess.enabled']}" class="row">
            <div class="col-lg-12">
              <button id="ssoButton" type="button"
                class="btn btn-primary" style="width: 100%;"
                ng-click="openOneAccessWindow()">Single Sign On
              </button>
              <hr>
            </div>
          </div>
          <div class="row">
            <div class="col-lg-12">
              <div class="form-group" ng-class="{ 'has-error' : (loginForm.$submitted || loginForm.username.$touched) && loginForm.username.$invalid }">
                <label for="username">
                  {{'login.username' | i18n}}
                  <span style="color: red;">*</span>
                </label>
                <input type="text" class="form-control input-sm" id="username" name="username" placeholder="Username" ng-required="true" ng-model="credentials.username" ng-error-messages>
              </div>
            </div>
          </div>
          <div class="row">
            <div class="col-lg-12">
              <div class="form-group" ng-class="{ 'has-error' : (loginForm.$submitted || loginForm.password.$touched) && loginForm.password.$invalid }">
                <label for="password">
                  {{'login.password' | i18n}}
                  <span style="color: red;">*</span>
                </label>
                <input type="password" ng-ie10-autocomplete="off" class="form-control input-sm" id="password" name="password" placeholder="Password" ng-required="true" ng-model="credentials.password" ng-error-messages>
              </div>
            </div>
          </div>
        </div>
        <div class="modal-footer">
          <button type="submit" class="btn btn-primary">
            <i class="fa fa-unlock-alt"></i> {{'login.submit' | i18n}}
          </button>
        </div>
      </form>
    </div>
  </div>
</div>
