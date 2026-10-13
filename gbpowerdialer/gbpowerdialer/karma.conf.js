module.exports = function(config) {
  config.set({
    basePath: '',
    frameworks: ['jasmine'],
    files: [
      'src/main/webapp/js/core/angular.min.js',
      'node_modules/angular-mocks/angular-mocks.js',
      'src/main/webapp/js/util/ui-grid.min.js',
      'src/main/webapp/js/util/underscore-min.js',
      'src/main/webapp/js/util/angular-local-storage.min.js',
      'src/test/angular/setup/angular-app.module.js',
      'src/main/webapp/js/constants.js',
      'src/main/webapp/js/services/alertService.js',
      'src/main/webapp/js/services/baseFactory.js',
      'src/main/webapp/js/services/gridStateService.js',
      'src/main/webapp/js/services/fctChannelFactory.js',
      'src/main/webapp/js/controllers/dncPhoneFormCtrl.js',
      'src/main/webapp/js/controllers/phoneNumberColumnCtrl.js',
      'src/main/webapp/js/controllers/campaignGroupCtrl.js',
      'src/main/webapp/js/controllers/resultCodeScheduleCtrl.js',
      'src/main/webapp/js/controllers/resultCodeScheduleAdvancedCtrl.js',
      'src/main/webapp/js/controllers/filterColumnCtrl.js',
      'src/main/webapp/js/controllers/attachedDataColumnCtrl.js',
      'src/main/webapp/js/controllers/campaignScheduleAutomationCtrl.js',
      'src/main/webapp/js/controllers/adminLogsDetailsCtrl.js',
      'src/main/webapp/js/controllers/campaignCtrl.js',
      'src/main/webapp/js/controllers/fctChannelMonitorCtrl.js',
      'src/test/angular/**/*.spec.js'
    ],
    browsers: ['ChromeHeadlessNoSandbox'],
    customLaunchers: {
      ChromeHeadlessNoSandbox: {
        base: 'ChromeHeadless',
        flags: ['--no-sandbox']
      }
    },
    reporters: ['progress'],
    singleRun: true,
    autoWatch: false
  });
};
