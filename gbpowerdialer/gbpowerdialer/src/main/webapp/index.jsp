<%@ include file="/pages/taglibs.jsp"%>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags"%>
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta http-equiv="X-UA-Compatible" content="IE=edge">
<meta name="viewport" content="width=device-width, initial-scale=1">
<link rel="icon" href="style/favicon.ico">
<title>${appConfig['application.name']}</title>
<link href="style/global.css?v=${appConfig['project.version']}" rel="stylesheet">
<link href="style/font-awesome.min.css" rel="stylesheet">
<link href="style/bootstrap.min.css" rel="stylesheet">
<link href="style/jquery.smartmenus.bootstrap.css" rel="stylesheet">
<link href="style/ui-grid.min.css" rel="stylesheet">
<link href="style/angular-block-ui.css" rel="stylesheet">
<link href="style/datetimepicker.css" rel="stylesheet">
</head>
<body ng-app="app">
  <nav class="navbar navbar-default navbar-fixed-top">
  <div class="container-fluid">
    <div class="navbar-header">
      <button type="button" class="navbar-toggle collapsed" data-toggle="collapse" data-target="#navbar" aria-expanded="false" aria-controls="navbar">
        <span class="icon-bar"></span>
        <span class="icon-bar"></span>
        <span class="icon-bar"></span>
      </button>
      <div class="navbar-brand">
        <img src="style/logo.png" />
      </div>
    </div>
    <div id="navbar" class="navbar-collapse collapse" ng-controller="menuCtrl as menu">
      <ul class="nav navbar-nav">
        <li ng-class="{ active: isActive('/campaignList')}" ng-show="authenticated">
          <a href="#campaignList"> <i class="fa fa-cubes"></i> {{'menu.campaigns' | i18n}}</a>
        </li>
        <li ng-class="{ active: isActive('/serverList') || isActive('/dncList') || isActive('/telephonyRcList') || isActive('/lscList') || isActive('/adminLogs') || isActive('/actionLog') || isActive('/fctChannelMonitor')}"
          class="dropdown" ng-show="hasAnyRole(['ROLE_SUPERADMIN', 'ROLE_TENANT_ADMIN','ROLE_SUPERVISOR']) || hasAuthority({authority:'EditCampaignNonTech'})">
          <a href="javascript:void(0)" onclick="return false" class="dropdown-toggle" data-toggle="dropdown" role="button" aria-haspopup="true"
             aria-expanded="false"> <i class="fa fa-cog"></i> {{'menu.options' | i18n}} <span class="caret"></span>
          </a>
          <ul class="dropdown-menu">
            <li ng-class="{ active: isActive('/dncList')}" ng-show="hasAnyRole(['ROLE_TENANT_ADMIN', 'ROLE_SUPERADMIN', 'ROLE_SUPERVISOR'])"><a href="#dncList">{{ 'menu.dnc.list' | i18n }}</a></li>
            <li ng-class="{ active: isActive('/lscList')}" ng-show="hasAnyRole(['ROLE_SUPERADMIN'])"><a href="#lscList">{{'menu.lsc.list' | i18n}}</a></li>
            <li ng-class="{ active: isActive('/actionLog')}" ng-show="hasAnyRole(['ROLE_SUPERADMIN'])"><a href="#actionLog">{{'action.log' | i18n}}</a></li>
            <li ng-class="{ active: isActive('/adminLogs')}" ng-show="hasAnyRole(['ROLE_SUPERADMIN'])"><a href="#adminLogs">{{'menu.admin.logs' | i18n}}</a></li>
            <li ng-class="{ active: isActive('/locationList')}" ng-show="hasAnyRole(['ROLE_SUPERADMIN'])"><a href="#locationList">{{'menu.location.list' | i18n}}</a></li>
            <li ng-class="{ active: isActive('/serverList')}" ng-show="hasAnyRole(['ROLE_SUPERADMIN'])"><a href="#serverList">{{'menu.server.list' | i18n}}</a></li>
            <li ng-class="{ active: isActive('/telephonyRcList')}" ng-show="hasAnyRole(['ROLE_SUPERADMIN'])"><a href="#telephonyRcList">{{'menu.telephony.rc.list' | i18n}}</a>
            <li ng-class="{ active: isActive('/serviceStatusesList')}" ng-show="hasAnyRole(['ROLE_SUPERADMIN'])"><a href="#serviceStatusesList">{{'menu.service.statuses.list' | i18n}}</a>
            <li ng-class="{ active: isActive('/campaignDataManipulationAndUpdate')}" ng-show="hasAnyRole(['ROLE_SUPERADMIN', 'ROLE_TENANT_ADMIN','ROLE_SUPERVISOR',ROLE_TENANT_ADMIN])"><a href="#campaignDataManipulationAndUpdate">{{'menu.campaign.data.manipulation.and.update' | i18n}}</a></li>
            <li ng-class="{ active: isActive('/fctChannelMonitor')}" ng-show="hasAnyRole(['ROLE_SUPERADMIN'])"><a href="#fctChannelMonitor">{{'menu.fct.channel.monitor' | i18n}}</a></li>
          </ul>
        </li>
        <li class="dropdown" ng-show="authenticated"><a href="javascript:void(0)" onclick="return false" class="dropdown-toggle" data-toggle="dropdown" role="button" aria-haspopup="true"
          aria-expanded="false"> <i class="fa fa-question"></i> {{'menu.help' | i18n}} <span class="caret"></span>
        </a>
          <ul class="dropdown-menu">
            <li><a href ng-click="menu.open('sm', 'pages/feedback.html', 'feedbackFormCtrl as feedback')"> {{'menu.feedback' | i18n}} </a></li>
            <li><a href ng-click="menu.open('sm', 'pages/aboutApplication.jsp', 'aboutApplicationCtrl as aboutApp')">{{'menu.about.app' | i18n}}</a></li>
          </ul></li>
      </ul>
      <ul class="nav navbar-nav navbar-right" ng-show="authenticated">
        <li class="dropdown"><a href="javascript:void(0)" class="dropdown-toggle" data-toggle="dropdown" role="button" aria-haspopup="true" aria-expanded="false" onclick="return false"> <i
            class="fa fa-user"></i> {{currentUser.username}} <span class="caret"></span>
        </a>
          <ul class="dropdown-menu">
            <li><a href="javascript:void(0)"> <i class="fa fa-language"></i> {{'menu.lang' | i18n}} <span class="caret"></span>
            </a>
              <ul class="dropdown-menu">
                <li><a href="javascript:void(0)" ng-click="changeLanguage('en')">{{'menu.en' | i18n}}</a></li>
                <li><a href="javascript:void(0)" ng-click="changeLanguage('ru')">{{'menu.ru' | i18n}}</a></li>
                <li><a href="javascript:void(0)" ng-click="changeLanguage('tr')">{{'menu.tr' | i18n}}</a></li>
                <li><a href="javascript:void(0)" ng-click="changeLanguage('ua')">{{'menu.ua' | i18n}}</a></li>
              </ul></li>
            <li><a id="signoutButton" href="javascript:void(0)" onclick="return false" ng-click="logoutHandler()"> <i class="fa fa-sign-out"></i> {{'menu.signout' | i18n}}
            </a></li>
          </ul></li>
      </ul>
    </div>
  </div>
  </nav>
    <div class="container-fluid">
    <div class="alertContainer" ng-controller="alertCtrl as alert">
      <alert ng-repeat="al in alert.permanentAlerts" type="{{al.type}}" close="alert.closePermanentAlert($index)"> {{al.msg}} </alert>
      <alert ng-repeat="al in alert.alerts" type="{{al.type}}" close="alert.closeAlert($index)" dismiss-on-timeout="5000"> {{al.msg}} </alert>
    </div>
    <div class="jumbotron" ng-view></div>
  </div>
  <c:choose>
    <c:when test="${appConfig['project.stage'] == 'production'}">
      <script type="text/javascript" src="<spring:url value="/wro/global.js?v=${appConfig['project.version']}"/>"></script>
    </c:when>
    <c:otherwise>
      <jsp:include page="pages/sources.jsp" />
    </c:otherwise>
  </c:choose>
  <%-- Localization dosyaları da diğer varlıklar gibi ?v=project.version ile yüklenir (bkz. WRO
       bundle ve pages/sources.jsp). Aksi hâlde tarayıcı eski locMsg kopyasını cache'ten servis eder
       ve yeni eklenen i18n anahtarları ekranda ham anahtar olarak görünür. --%>
  <c:choose>
    <c:when test="${locale == 'ru'}">
      <script src="js/localization/localization_ru.js?v=${appConfig['project.version']}"></script>
    </c:when>
    <c:when test="${locale == 'tr'}">
      <script src="js/localization/localization_tr.js?v=${appConfig['project.version']}"></script>
    </c:when>
    <c:when test="${locale == 'ua'}">
      <script src="js/localization/localization_ua.js?v=${appConfig['project.version']}"></script>
    </c:when>
    <c:otherwise>
      <script src="js/localization/localization_en.js?v=${appConfig['project.version']}"></script>
    </c:otherwise>
  </c:choose>
  <script>
      $(document).ready(function() {
        $("body").click(function(event) {
          if ($("#navbar.navbar-collapse").is(":visible") && $("#navbar .navbar-toggle").is(":visible")) {
            $('#navbar.navbar-collapse').collapse('toggle');
          }
        });
      });
    </script>
</body>
</html>
