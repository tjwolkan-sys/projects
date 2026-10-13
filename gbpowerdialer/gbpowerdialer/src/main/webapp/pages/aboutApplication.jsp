<div>
  <div class="modal-header">
    <h3 class="modal-title">{{'menu.about.app' | i18n}}</h3>
  </div>
  <div class="modal-body">
    <div class="row">
      <label class="col-lg-5">{{'menu.about.app.version' | i18n}}:</label>
      <p class="col-lg-7">${appConfig['project.version']}</p>
      <label class="col-lg-5">{{'menu.about.app.builddate' | i18n}}:</label>
      <p class="col-lg-7">${appConfig['project.buildDate']}</p>
    </div>
  </div>
</div>
<div class="modal-footer">
  <button class="btn btn-primary" type="button" ng-click="aboutApp.ok()">OK</button>
</div>
</div>