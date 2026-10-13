/**
 * Define which modules are going to be used in application
 */
var app = angular.module('app', ['blockUI', 'ngRoute', 'ui.grid', 'ui.grid.pagination', 'ui.grid.resizeColumns', 'ui.grid.selection', 'ui.bootstrap',
  'ui.bootstrap.datetimepicker', 'localization', 'pascalprecht.translate', 'app.services', 'ngReallyClickModule', 'fxpicklist', 'ngFormatDateModule', 'ui.grid.pinning',
  'ui.grid.expandable', 'ui.grid.grouping', 'ngMessages', 'ui.grid.autoResize', 'ui.grid.saveState', 'ui.grid.exporter', 'ngCookies', 'LocalStorageModule',
  'angular-duration-format.filter', 'spring-security-csrf-token-interceptor', 'ui.grid.edit', 'ui.grid.rowEdit', 'btorfs.multiselect', "angular-uuid"]);

app.run(function ($rootScope, $templateCache) {
  $rootScope.notDeleted = function (item) {
    return item !== undefined && !item.deleted;
  };
  $templateCache.put('ui-grid/uiGridViewport',
    "<div class=\"ui-grid-viewport\" ng-style=\"colContainer.getViewportStyle()\"><div class=\"ui-grid-canvas\"><div ng-repeat=\"(rowRenderIndex, row) in rowContainer.renderedRows track by $index\" ng-if=\"grid.appScope.notDeleted(row.entity)\" class=\"ui-grid-row\" ng-style=\"Viewport.rowStyle(rowRenderIndex)\"><div ui-grid-row=\"row\" row-render-index=\"rowRenderIndex\"></div></div></div></div>"
  );

  // Gamma Correction Factor yardım içeriği. uib-popover-template ile transclude edildiği
  // için ana scope'a erişir; kapatma butonu campaign controller'daki closeGammaCfHelp()
  // fonksiyonunu çağırır.
  $templateCache.put('pages/help/gammaCorrectionFactorHelp.html',
    '<button type="button" class="gamma-help-close" ng-click="campaign.closeGammaCfHelp()"'
    + ' title="{{\'campaign.help.close\' | i18n}}"'
    + ' aria-label="{{\'campaign.help.close\' | i18n}}">&times;</button>'
    + '<div class="gamma-help-section">{{\'campaign.help.gammaCorrectionFactor.intro\' | i18n}}</div>'
    + '<div class="gamma-help-section">'
    + '<span class="gamma-help-section-title">{{\'campaign.help.gammaCorrectionFactor.balancedTitle\' | i18n}}</span>'
    + '{{\'campaign.help.gammaCorrectionFactor.balancedBody\' | i18n}}</div>'
    + '<div class="gamma-help-section">'
    + '<span class="gamma-help-section-title">{{\'campaign.help.gammaCorrectionFactor.positiveTitle\' | i18n}}</span>'
    + '{{\'campaign.help.gammaCorrectionFactor.positiveBody\' | i18n}}</div>'
    + '<div class="gamma-help-section">{{\'campaign.help.gammaCorrectionFactor.whenBody\' | i18n}}</div>'
    + '<div class="gamma-help-section gamma-help-risk">{{\'campaign.help.gammaCorrectionFactor.riskBody\' | i18n}}</div>'
  );
});

/**
 * HttpRequestInterceptor to handle errors in responces
 */
app.factory('httpRequestInterceptor', function ($q, $location, $rootScope, $log, alertService) {
  return {
    'responseError': function (response) {
      if (response.data != null && response.data.exception != null && response.data.exception == true) {
        alertService.add('danger', response.data.localizedMessage);
      } else {
        if (!response.config.skipErrorRedirect) {
          $location.path('/error/' + response.status);
        }
      }
      return $q.reject(response);
    }
  };
});

/**
 * Configuration to access browser's local storage with cookie
 */
app.config(function (localStorageServiceProvider) {
  localStorageServiceProvider.setPrefix('pd');
});

/**
 * Configuration for UI blocking template
 */
app.config(function (blockUIConfig) {
  blockUIConfig.requestFilter = function (requestConfig) {
    return !!requestConfig.showUIBlock
  };
});

/**
 * Configuration for httpRequestInterceptors
 */
app.config(function ($httpProvider, $interpolateProvider, $routeProvider) {
  $httpProvider.interceptors.push('httpRequestInterceptor');
});

/**
 * Configuration for linking URLs to controllers and views
 */
app.config(function ($routeProvider) {
  $routeProvider
    .when('/login', {
      templateUrl: 'pages/login',
      controller: 'loginCtrl'
    }).when('/campaignList', {
    templateUrl: 'pages/campaignList',
    controller: 'campaignCtrl as campaign'
  }).when('/locationList', {
    templateUrl: 'pages/locationList',
    controller: 'locationCtrl as location'
  }).when('/serverList', {
    templateUrl: 'pages/serverList',
    controller: 'serverCtrl as server'
  }).when('/dncList', {
    templateUrl: 'pages/dncList',
    controller: 'dncCtrl as dnc'
  }).when('/lscList', {
    templateUrl: 'pages/lscList',
    controller: 'lscCtrl as lsc'
  }).when('/telephonyRcList', {
    templateUrl: 'pages/telephonyRcList',
    controller: 'telephonyRcCtrl as telephonyRc'
  }).when('/adminLogs', {
    templateUrl: 'pages/adminLogs',
    controller: 'adminLogsCtrl as admLogs'
  }).when('/actionLog', {
    templateUrl: 'pages/actionLog',
    controller: 'actionLogCtrl as actionLog'
  }).when('/error/:status', {
    templateUrl: 'pages/error',
    controller: 'errorCtrl as error'
  }).when('/testUpload', {
    templateUrl: 'pages/testUpload.html',
    controller: 'multipartUploadCtrl as multipartUpload'
  }).when('/serviceStatusesList', {
    templateUrl: 'pages/statusesList.html',
    controller: 'serviceStatusesCtrl as serviceStatuses'
  }).when('/campaignDataManipulationAndUpdate', {
    templateUrl: 'pages/campaignDataManipulationAndUpdate.jsp', // .jsp olarak kullanacağız
    controller: 'campaignDataManipulationAndUpdateCtrl as campaignDataManipulationAndUpdate'
  }).when('/fctChannelMonitor', {
    templateUrl: 'pages/fctChannelMonitor.html',
    controller: 'fctChannelMonitorCtrl as fctMonitor'
  }).otherwise({
    redirectTo: "/campaignList"
  });
});

/**
 * Directive for confirmation pop-ups
 */
app.directive('ngConfirmClick', [function () {
  return {
    priority: -1,
    restrict: 'A',
    link: function (scope, element, attrs) {
      element.bind('click', function (e) {
        var message = "Are you sure?";
        if (message && !confirm(message)) {
          e.stopImmediatePropagation();
          e.preventDefault();
        }
      });
    }
  };
}]);

/**
 * Directive to gain access to the file object in our controller
 */
app.directive('fileModel', ['$parse', function ($parse) {
  return {
    restrict: 'A',
    link: function (scope, element, attrs) {
      var model = $parse(attrs.fileModel);
      var modelSetter = model.assign;

      element.bind('change', function () {
        scope.$apply(function () {
          modelSetter(scope, element[0].files[0]);
        });
      });
    }
  };
}]);

/**
 * Directive for confirmation pop-ups
 */
angular
  .module('ngReallyClickModule', ['ui.bootstrap', 'localization'])
  .directive(
    'ngReallyClick',
    ['$parse',
      '$modal',
      'locMsg',
      function ($parse, $modal, locMsg) {

        var ModalInstanceCtrl = function ($scope, $modalInstance) {
          $scope.ok = function () {
            $modalInstance.close();
          };

          $scope.cancel = function () {
            $modalInstance.dismiss('cancel');
          };
        };

        return {
          restrict: 'A',
          scope: {
            ngReallyClick: "&",
            ngReallyCancelClick: "&",
            item: "="
          },
          link: function (scope, element, attrs) {
            element
              .bind(
                'click',
                function () {
                  var ngReallyClickDisabledExpression = $parse(attrs.ngReallyClickDisabled);
                  var ngReallyClickDisabled = ngReallyClickDisabledExpression(scope);

                  if (ngReallyClickDisabled === true) {
                    scope.ngReallyClick({
                      item: scope.item
                    });
                    return;
                  }
                  var message = attrs.ngReallyMessage || "Are you sure ?";

                  /*
                   * //This works if (message && confirm(message)) { scope.$apply(attrs.ngReallyClick); } //
                   */

                  // *This doesn't works
                  var modalHtml = '<div class="modal-body">' + message + '</div>';
                  modalHtml += '<div class="modal-footer"><button class="btn btn-primary" ng-click="ok()"><i class="fa fa-check"></i> OK</button><button class="btn btn-warning" ng-click="cancel()"><i class="fa fa-times"></i> '
                    + locMsg['label.cancel'] + '</button></div>';

                  var modalInstance = $modal.open({
                    backdrop: 'static',
                    template: modalHtml,
                    controller: ModalInstanceCtrl
                  });

                  modalInstance.result.then(function () {
                    scope.ngReallyClick({
                      item: scope.item
                    }); // raise an error : $digest already in progress
                  }, function () {
                    scope.ngReallyCancelClick({
                      item: scope.item
                    });
                  });
                });
          }
        };
      }]);

/**
 * Date formatter module
 */
angular.module('ngFormatDateModule', []).directive('ngFormatDate', ['$filter', function ($filter) {
  return {
    require: 'ngModel',
    link: function (scope, element, attrs, ngModelController, filter) {

      // convert data from view format to model format
      ngModelController.$parsers.push(function (data) {
        return data;
      });

      // convert data from model format to view format
      ngModelController.$formatters.push(function (data) {
        if (data !== undefined) {
          return $filter('date')(data, attrs.ngFormatDate);
        }
      });
    }
  };
}]);

/**
 * Configuration for csrf interceptor
 */
angular.module('spring-security-csrf-token-interceptor', []).factory('csrfInterceptor', ['$injector', '$q', function ($injector) {
  var $q = $injector.get('$q'), csrf = $injector.get('csrf'),
    // initialize the csrf provider service which in-turn invokes the injected csrfService
    // to fire the synchronous XHR call to get the CSRF token
    csrfService = csrf.init();

  return {
    request: function (config) {
      // intercept HTTP request only for the configured HTTP types
      if (csrfService.settings.httpTypes.indexOf(config.method.toUpperCase()) > -1) {
        config.headers[csrfService.settings.csrfTokenHeader] = csrfService.token;
      }
      return config || $q.when(config);
    },
    responseError: function (response) {
      var $http, newToken = response.headers(csrfService.settings.csrfTokenHeader);

      if (response.status === 403 && csrfService.numRetries < csrfService.settings.maxRetries) {
        csrfService.getTokenData();
        $http = $injector.get('$http');
        csrfService.numRetries = csrfService.numRetries + 1;
        return $http(response.config);
      } else if (newToken) {
        // update the csrf token in-case of response errors other than 403
        csrfService.token = newToken;
      }
      // Fix for interceptor causing failing requests
      return $q.reject(response);
    },
    response: function (response) {
      // reset number of retries on a successful response
      csrfService.numRetries = 0;
      return response;
    }
  };
}]).factory('csrfService', [function () {
  var defaults = {
    url: 'getCurrentCsrfToken', // the URL to which the CSRF call has to be made to get the token
    csrfHttpType: 'GET', // the HTTP method type which is used for making the CSRF token call
    maxRetries: 3, // number of retires allowed for forbidden requests
    csrfTokenHeader: 'X-CSRF-TOKEN',
    httpTypes: ['GET', 'HEAD', 'PUT', 'POST', 'DELETE']
    // default allowed HTTP types
  };
  return {
    inited: false,
    settings: null,
    numRetries: 0,
    token: '',
    init: function (options) {
      this.settings = angular.extend({}, defaults, options);
      this.getTokenData();
    },
    getTokenData: function () {
      var xhr = new XMLHttpRequest();
      xhr.open(this.settings.csrfHttpType, this.settings.url, false);
      xhr.send();
      response = JSON.parse(xhr.responseText);
      this.token = response.token;
      this.csrfTokenHeader = response.headerName;
      this.inited = true;
    }
  };

}]).provider('csrf', [

  function () {
    var CsrfModel = function CsrfModel(options) {
      return {
        options: options,
        csrfService: null
      };
    };

    return {
      $get: ['csrfService', function (csrfService) {
        var self = this;
        return {
          init: function () {
            self.model = new CsrfModel(self.options);
            self.model.csrfService = csrfService;
            self.model.csrfService.init(self.model.options);
            return self.model.csrfService;
          }
        };
      }],

      model: null,

      options: {},

      config: function (options) {
        this.options = options;
      }
    };
  }]).config(['$httpProvider', function ($httpProvider) {
  $httpProvider.interceptors.push('csrfInterceptor');
}]);

/**
 * Directive for error messages
 */
app.directive('ngErrorMessages', [
  '$compile',
  function ($compile) {
    return function (scope, element, attrs) {
      if (attrs.min === undefined || attrs.max === undefined) {
        updateInputErrorMessages(scope, element, attrs, $compile);
      } else {
        scope.$watch(function () {
          return attrs.min;
        }, function () {
          updateInputErrorMessages(scope, element, attrs, $compile)
        });

        scope.$watch(function () {
          return attrs.max;
        }, function () {
          updateInputErrorMessages(scope, element, attrs, $compile)
        });
      }
    };
  }]);

function updateInputErrorMessages(scope, element, attrs, $compile) {
  $(document.getElementById('error.messages-' + attrs.id)).remove();

  var minMessage = "";
  if (attrs.min != null) {
    minMessage = "<p ng-message='min'>{{'form.validation.notless' | i18n}} " + attrs.min + "</p>";
  }

  var maxMessage = "";
  if (attrs.max != null) {
    maxMessage = "<p ng-message='max'>{{'form.validation.notgreater' | i18n}} " + attrs.max + "</p>";
  }

  var uuidMessage = "";
  if (attrs.ngModel === 'campaign.item.apiToken') {
    uuidMessage = "<p ng-message='pattern'>{{'form.validation.invalidUuidFormat' | i18n}}</p>";
  }

  var fieldName = attrs.name;
  var formName = element[0].form.name;
  var messages = "<div id='error.messages-" + attrs.id + "' style='font-size: 13px;' ng-show='" + formName + ".$submitted || " + formName + "." + fieldName + ".$touched' " +
    "class='help-block' ng-messages='" + formName + "." + fieldName + ".$error'>" + minMessage + maxMessage + uuidMessage + "<div ng-messages-include='pages/errorFormMessages.html'>" + "</div></div>";

  var answersElem = angular.element(messages);
  var compileFn = $compile(answersElem);
  compileFn(scope);
  element.after(answersElem);
}

/**
 * Native autocomplete workaround for ie10
 */
app.directive("ngIe10Autocomplete", function () {
  return function (scope, element, attrs) {
    if (navigator.appVersion.indexOf("MSIE 10") !== -1) {
      var value = attrs["ngIe10Autocomplete"];
      element.attr('autocomplete', value);
    }
  }
});

/**
 * Directive that remembers dragged element position in cookie by specified param lso you can enable/disable it dynamically
 */
app.directive('draggable', function ($document, $compile) {
  return {
    restrict: 'E',
    scope: {
      obj: '@',
      disabled: '=',
      width: '@'
    },
    link: function (scope, element, attr) {
      var startX = 0, startY = 0, x = 0, y = 0, position = 'absolute';

      element.parent().on('mousedown', function (event) {
        if (!scope.disabled) {
          // Prevent default dragging of selected content
          event.preventDefault();

          if (scope.obj && Cookie.find(scope.obj + 'X') && Cookie.find(scope.obj + 'Y')) {
            startX = event.screenX - Cookie.find(scope.obj + 'X');
            startY = event.screenY - Cookie.find(scope.obj + 'Y');
          } else {
            startX = event.screenX - x;
            startY = event.screenY - y;
          }

          $document.on('mousemove', mousemove);
          $document.on('mouseup', mouseup);
        }
      });

      if (!scope.disabled) {
        element.parent().css({
          position: 'absolute',
          display: 'block',
          width: scope.width
        });

        if (scope.obj && Cookie.find(scope.obj + 'X') && Cookie.find(scope.obj + 'Y')) {
          element.parent().css({
            top: Cookie.find(scope.obj + 'Y') + 'px',
            left: Cookie.find(scope.obj + 'X') + 'px'
          });
        }

      }

      scope.$watch("disabled", function (newValue, oldValue) {
        scope.disabled = newValue;
        if (oldValue != newValue) {

          if (!newValue) {

            if (Cookie.find(scope.obj + 'X') == '') {
              var rect = element.parent()[0].getBoundingClientRect();
              Cookie.create(scope.obj + 'Y', rect.top);
              Cookie.create(scope.obj + 'X', rect.left);
            }

            element.parent().css({
              position: 'absolute',
              display: 'block',
              width: scope.width,
              top: Cookie.find(scope.obj + 'Y') + 'px',
              left: Cookie.find(scope.obj + 'X') + 'px'
            });
          } else {
            element.parent().css({
              position: 'relative',
              display: 'block',
              width: '100%',
              top: '-5px',
              left: '0px'
            });
          }

          startX = 0, startY = 0, x = 0, y = 0;
        }
      });

      var mainScreenX;
      var mainScreenY;
      const mainHeaderScreenY = 66; //Header

      function mousemove(event) {

        mainScreenX = document.documentElement.clientWidth - (element.parent()[0].getBoundingClientRect().right - element.parent()[0].getBoundingClientRect().left);
        mainScreenY = document.documentElement.clientHeight - (element.parent()[0].getBoundingClientRect().bottom - element.parent()[0].getBoundingClientRect().top);

        y = event.screenY - startY;
        x = event.screenX - startX;

        if (scope.obj && x >= 0 && x <= mainScreenX) {
          Cookie.create(scope.obj + 'X', x);
          if (y < mainHeaderScreenY)
            y = mainHeaderScreenY + 1;
          if (y > mainScreenY)
            y = mainScreenY - 1;
          element.parent().css({
            top: y + 'px',
            left: x + 'px'
          });
        }
        if (scope.obj && y >= mainHeaderScreenY && y <= mainScreenY) {
          Cookie.create(scope.obj + 'Y', y);
          if (x < 0)
            x = 1;
          if (x > mainScreenX)
            x = mainScreenX - 1;
          element.parent().css({
            top: y + 'px',
            left: x + 'px'
          });
        }

      }

      function mouseup() {
        $document.off('mousemove', mousemove);
        $document.off('mouseup', mouseup);
      }
    }
  };
});

app.directive("databasecolumn", function (constants) {
  var INTEGER_REGEX = /(\d+)(,\s*\d+)*/;
  var DECIMAL_REGEX = /^-?\d+(\.\d{0,})?$/;
  var NULL_REGEX = /^NULL$/i;
  return {
    require: 'ngModel',
    scope: {
      databasecolumn: '='
    },
    link: function (scope, elm, attrs, ctrl) {
      ctrl.$validators.databasecolumn = function (modelValue, viewValue) {

        if ((scope.databasecolumn.condition == 0 || scope.databasecolumn.condition == 1) && (NULL_REGEX.test(viewValue))) {
          return true;
        }

        if (scope.databasecolumn.field && scope.databasecolumn.field.simpleDbType == constants.SIMPLE_DB_TYPES.INTEGER)
          return INTEGER_REGEX.test(viewValue);

        if (scope.databasecolumn.field && scope.databasecolumn.field.simpleDbType == constants.SIMPLE_DB_TYPES.DECIMAL)
          return DECIMAL_REGEX.test(viewValue);

        return true;

      };
    }
  };
});

app.filter('restrictByType', function (constants) {
  return function (collection, columnName, selectedColumnName) {
    var output = [];
    if (columnName == 'ANY_TYPE') {
      angular.forEach(collection, function (item) {
        if (item.isSelected != true || item.fieldName == selectedColumnName)
          output.push(item);
      });
    } else {
      var allowedTypes = constants.RESULT_FIELDS_ALLOWED_TYPES[columnName];
      angular.forEach(collection, function (item) {
        if (((item.fieldType == undefined || allowedTypes.indexOf(item.fieldType) != -1) && item.isSelected != true) || item.fieldName == selectedColumnName)
          output.push(item);
      });
    }
    return output;
  }
});

Array.prototype.uniqueByField = function (field) {
  for (var i = 0; i < this.length; ++i) {
    for (var j = i + 1; j < this.length; ++j) {
      if (this[i][field] == this[j][field])
        this.splice(j--, 1);
    }
  }
  return this;
};

app.directive('validatetablename', function ($parse, dbTableService) {
  var validationTimeout;
  return {
    restrict: 'A',
    require: 'ngModel',
    scope: '=',
    link: function (scope, elem, attr, ctrl) {
      scope.$watch(attr.ngModel, function (tableName) {
        if (validationTimeout)
          clearTimeout(validationTimeout);
        ctrl.$setValidity('validatetablename_' + attr.name, false);
        validationTimeout = setTimeout(function () {
          var campaign = $parse(attr.validatetablename)(scope);
          dbTableService.checkTableExistence(tableName, campaign).then(function (result) {
            ctrl.$setValidity('validatetablename', result.data);
            ctrl.$setValidity('validatetablename_' + attr.name, true);
          }).catch(function (reason) {
            ctrl.$setValidity('validatetablename', false);
            ctrl.$setValidity('validatetablename_' + attr.name, true);
          });
        }, 1000);
      })
    }
  }
});

app.directive('fixdatepicker', function () {
  return {
    restrict: 'A',
    link: function (scope, elem, attr, ctrl) {
      var relativeElementId = attr.fixdatepicker;
      var idSelector = '#' + relativeElementId.replace(/(:|\.|\[|\]|,|=)/g, "\\$1");
      var relativeElement = angular.element(idSelector);
      var w = angular.element(window);
      relativeElement.bind('click', function () {
        var offsets = relativeElement.offset();
        elem.parent().css('position', 'fixed');
        elem.parent().css('top', offsets.top - 75 + 'px');
        elem.parent().css('margin-left', w.width() - Math.max(relativeElement.width(), elem.width()) + 'px');
      });
    }
  };
});

app.directive('fileonload', function (blockUI) {
  return {
    scope: {
      opts: '=',
      fileonload: '&'
    },
    link: function ($scope, $elm, attr) {
      $elm.on('change', function (changeEvent) {
        if (changeEvent.target.files.length > 0) {
          blockUI.start();
          var reader = new FileReader();
          reader.onload = $scope.fileonload;
          reader.readAsBinaryString(changeEvent.target.files[0]);
          reader.onprogress = function (ev) {
            blockUI.message(Math.ceil((ev.loaded / ev.total) * 100) + '%');
          };
          reader.onloadend = function () {
            blockUI.stop();
          }
        }
      });
    }
  };
});

app.directive("dragndrop", function (blockUI) {
  return {
    scope: {
      opts: '=',
      dragndrop: '&'
    },
    link: function ($scope, $elm, attr) {
      $elm.on('drop', function (dropEventJq) {
        if (dropEventJq.originalEvent && dropEventJq.originalEvent.dataTransfer && dropEventJq.originalEvent.dataTransfer.files && dropEventJq.originalEvent.dataTransfer.files.length) {
          var file = dropEventJq.originalEvent.dataTransfer.files[0];
          dropEventJq.stopPropagation();
          dropEventJq.preventDefault();
          blockUI.start();
          var reader = new FileReader();
          reader.onloadstart = function (e) {
            $scope.dragndrop({
              'file': file
            });
          };
          reader.onprogress = function (ev) {
            blockUI.message(Math.ceil((ev.loaded / ev.total) * 100) + '%');
          };
          reader.onloadend = function () {
            blockUI.stop();
          };
          reader.readAsBinaryString(file);
        }
      });
      $elm.on('dragover', function (event) {
        event.preventDefault();
      });
      $elm.on('dragenter', function (event) {
        event.preventDefault();
      });
    }
  };
});

app.filter('actionLogLink', function () {
  var linkReplaceRegex = /"(.*?)":(\w+[-.\/\w]*)/; // "foo":path/to/file
  return function (text) {
    return text.replace(linkReplaceRegex, function (fullString, linkName, linkHref) {
      return '<a target="_blank" href="' + linkHref + '">' + linkName + '<a/>'
    });
  };
});

app.directive('bindhtml', function () {
  return {
    scope: {
      opts: '=',
      bindHtml: '&'
    },
    link: function ($scope, $elm, attr) {
      $elm.html(attr.bindhtml)
    }
  }
});

var localization = angular.module('localization', []);

localization.filter('i18n', ['locMsg', '$rootScope', function (locMsg, $rootScope) {
  return function (text) {
    if (locMsg.hasOwnProperty(text)) {
      return locMsg[text];
    }
    return text;
  };
}]);

localization.factory('localeFactory', function () {
  var locale = 'EN_';
  return {
    setLocale: function (newLocale) {
      locale = newLocale;
    },
    getLocale: function () {
      return locale;
    }
  };
});
