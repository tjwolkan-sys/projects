/* global angular */
window.app = angular.module('app', []);
var app = window.app;

app.value('locMsg', {
  'label.save.success': 'Kaydetme işlemi başarılı',
  'label.delete.success': 'Silme işlemi başarılı'
});
