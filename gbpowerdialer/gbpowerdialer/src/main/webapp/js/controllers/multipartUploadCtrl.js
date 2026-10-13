/**
 * Controller for testing multipart upload
 */
app.controller('multipartUploadCtrl', [ '$http', function($http) {
  var vm = this;
  vm.saveForm = function() {
    var formData = new FormData();
    formData.append("file", vm.file);
    $http.post(vm.url, formData, {
      transformRequest : angular.identity,
      headers : {
        'Content-Type' : undefined,
        'Authorization' : 'Basic' + btoa(vm.username + ":" + vm.password)
      }
    }).success(function() {
      console.log('success');
    }).error(function() {
      console.log('error');
    });
  }

} ]);
