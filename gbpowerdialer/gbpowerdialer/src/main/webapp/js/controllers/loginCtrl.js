/**
 * Controller for authentication
 */
app.controller('loginCtrl', [ '$scope', '$http', '$location', '$rootScope', 'blockUI', 'alertService', function($scope, $http, $location, $rootScope, blockUI, alertService) {

  $('#loginModal').modal('show');

  $rootScope.authenticated = false;

  $scope.credentials;

  $scope.loginHandler = function() {

    if($scope.credentials && $scope.credentials.username && $scope.credentials.password){
      $http.post('auth/login', {
        credentials : $scope.credentials,
      }).then(function(response) {
        if (response.data.authenticated) {
          $("#loginModal").modal("hide").on('hidden.bs.modal', function() {
            $location.path('/campaignList');
            $scope.$apply();
          });
          $rootScope.currentUser = response.data;
        } else {
          $("#alertLogin").html('<i class="fa fa-exclamation-triangle"></i> ' + response.data.message).show();
        }
        $rootScope.authenticated = response.data.authenticated;

      });
    }

  };

  // Open oneaccess login window
  $scope.openOneAccessWindow = function() {
    var oneAccessWindow = $scope.openWindowInCenter('auth/oauthLogin', 'Single Sign On', 360, 470);

    var oneAccessInterval = setInterval(function() {
      if (oneAccessWindow.closed) {
        clearInterval(oneAccessInterval);
        $rootScope.authCheck();
      }
    }, 100);
  };

  $scope.openWindowInCenter = function(url, title, w, h) {

    var dualScreenLeft = window.screenLeft != undefined ? window.screenLeft : screen.left;
    var dualScreenTop = window.screenTop != undefined ? window.screenTop : screen.top;

    var width = window.innerWidth ? window.innerWidth : document.documentElement.clientWidth ? document.documentElement.clientWidth : screen.width;
    var height = window.innerHeight ? window.innerHeight : document.documentElement.clientHeight ? document.documentElement.clientHeight : screen.height;

    var left = ((width / 2) - (w / 2)) + dualScreenLeft;
    var top = ((height / 2) - (h / 2)) + dualScreenTop;
    var newWindow = window.open(url, title, 'scrollbars=yes, width=' + w + ', height=' + h + ', top=' + top + ', left=' + left);

    // Puts focus on the newWindow
    if (window.focus) {
      newWindow.focus();
    }

    return newWindow;
  }

} ]);