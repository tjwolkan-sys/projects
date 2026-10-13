app.service('commonResultStatusService', function ($http) {
  var vm = this;

  vm.loadCommonResultStatuses = function(ids) {
    return $http.get('groupStatus/commonResultStatuses', {params: {"ids": ids.toString()}});
  };

});
