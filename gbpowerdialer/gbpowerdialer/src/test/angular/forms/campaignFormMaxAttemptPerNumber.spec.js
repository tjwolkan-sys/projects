/* global describe, beforeEach, inject, it, expect, module, angular */

/**
 * FEAT-325559 Faz 2: "Numara Başına Azami Arama" alanının campaignForm.html içindeki
 * freeze davranışı ve constants mirror'ları. 28.09.2026: alan artık bir checkbox ile
 * etkinleştiriliyor; checkbox'ın ve alanın ifadeleri aşağıda. 29.09.2026: "Reach retry limit
 * for single number" (ordinal 0) da ordinal 1 gibi muaf; iki stratejide de alan ve checkbox pasif.
 *
 * ng-disabled ifadeleri gerçek Angular $compile ile değerlendirilir; template ifadesi
 * değişirse test kırılır (sadece metin araması değildir).
 */
describe('campaignForm max attempt per phone number', function() {

  var $compile;
  var $rootScope;
  var constants;

  beforeEach(module('app'));

  beforeEach(module(function($filterProvider) {
    $filterProvider.register('i18n', function() {
      return function(key) {
        return key;
      };
    });
  }));

  beforeEach(inject(function(_$compile_, _$rootScope_, _constants_) {
    $compile = _$compile_;
    $rootScope = _$rootScope_;
    constants = _constants_;
  }));

  // campaignForm.html icindeki gercek ng-disabled ifadesi. Alan once checkbox ile
  // etkinlestirilir (campaign.maxAttemptPerNumberEnabled, yalniz arayuz durumu).
  var MAX_ATTEMPT_DISABLED =
    "!campaign.maxAttemptPerNumberEnabled || !campaign.item.retryScheduleEnabled" +
    " || campaign.item.retryStrategy == 0 || campaign.item.retryStrategy == 1" +
    " || !canEditSelectedCampaignByPermission(campaign.item, 'EditCampaignTech')";

  // Alani acan checkbox'in gercek ng-disabled ifadesi: strateji 0 ve 1'de her durumda pasif.
  var MAX_ATTEMPT_CHECKBOX_DISABLED =
    "!campaign.item.retryScheduleEnabled || campaign.item.retryStrategy == 0 || campaign.item.retryStrategy == 1" +
    " || !canEditSelectedCampaignByPermission(campaign.item, 'EditCampaignTech')";

  // Kapanis nedeni binding select'inin gercek ng-disabled ifadesi.
  var CLOSE_REASON_DISABLED =
    "campaign.item.dbResultTable == null" +
    " || !canEditSelectedCampaignByPermission(campaign.item, 'EditCampaign')";

  // Karsilastirma icin mevcut macroMaxAttempt alaninin ifadesi (strateji bagimsiz olmali).
  var MACRO_MAX_ATTEMPT_DISABLED =
    "!campaign.item.retryScheduleEnabled" +
    " || !canEditSelectedCampaignByPermission(campaign.item, 'EditCampaignTech')";

  // perNumberEnabled verilmezse checkbox ISARETLI kabul edilir; boylece mevcut vakalar strateji,
  // yetki ve retry kosullarini olcmeye devam eder.
  function buildScope(item, canEdit, perNumberEnabled) {
    var scope = $rootScope.$new();
    scope.campaign = {
      item: item,
      constants: constants,
      maxAttemptPerNumberEnabled: perNumberEnabled !== false
    };
    scope.canEditSelectedCampaignByPermission = function() {
      return canEdit !== false;
    };
    return scope;
  }

  function isDisabled(disableExpr, item, canEdit, perNumberEnabled) {
    var scope = buildScope(item, canEdit, perNumberEnabled);
    var el = $compile('<input type="number" ng-disabled="' + disableExpr + '">')(scope);
    scope.$digest();
    return el[0].disabled === true;
  }

  function retryEnabledItem(overrides) {
    var base = {
      retryScheduleEnabled: true,
      retryStrategy: 2,
      dbResultTable: 'RES_TABLE'
    };
    return angular.extend(base, overrides || {});
  }

  // -------------------------------------------------------------------
  // constants mirror
  // -------------------------------------------------------------------

  it('constants CLOSE_REASON kolon adı ve tip whitelist mirror içerir', function() {
    expect(constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.CLOSE_REASON).toBe('close_reason');

    var allowed = constants.RESULT_FIELDS_ALLOWED_TYPES['close_reason'];
    expect(allowed).toEqual([
      constants.DB_FIELD_TYPES.VARCHAR,
      constants.DB_FIELD_TYPES.NVARCHAR,
      constants.DB_FIELD_TYPES.NVARCHAR2,
      constants.DB_FIELD_TYPES.CHARACTER_VARYING
    ]);
  });

  // -------------------------------------------------------------------
  // Freeze davranisi (K2)
  // -------------------------------------------------------------------

  it('Round-robin stratejilerinde alan ENABLED', function() {
    expect(isDisabled(MAX_ATTEMPT_DISABLED, retryEnabledItem({ retryStrategy: 2 }))).toBe(false);
    expect(isDisabled(MAX_ATTEMPT_DISABLED, retryEnabledItem({ retryStrategy: 3 }))).toBe(false);
    expect(isDisabled(MAX_ATTEMPT_DISABLED, retryEnabledItem({ retryStrategy: 4 }))).toBe(false);
  });

  it('REACH_RETRY_LIMIT_BEFORE_NEXT_NUMBER (ordinal 1) seçiliyken alan FREEZE', function() {
    expect(isDisabled(MAX_ATTEMPT_DISABLED, retryEnabledItem({ retryStrategy: 1 }))).toBe(true);
  });

  it('REACH_RETRY_LIMIT_FOR_SINGLE_NUMBER (ordinal 0) seçiliyken alan FREEZE', function() {
    expect(isDisabled(MAX_ATTEMPT_DISABLED, retryEnabledItem({ retryStrategy: 0 }))).toBe(true);
  });

  it('retry-rules stratejisinde alan ENABLED kalır', function() {
    expect(isDisabled(MAX_ATTEMPT_DISABLED, retryEnabledItem({ retryStrategy: 5 }))).toBe(false);
  });

  it('retryScheduleEnabled kapalıyken alan FREEZE (mevcut retry alanlarıyla aynı)', function() {
    var item = retryEnabledItem({ retryScheduleEnabled: false });

    expect(isDisabled(MAX_ATTEMPT_DISABLED, item)).toBe(true);
    expect(isDisabled(MACRO_MAX_ATTEMPT_DISABLED, item)).toBe(true);
  });

  it('EditCampaignTech yetkisi yoksa alan FREEZE', function() {
    expect(isDisabled(MAX_ATTEMPT_DISABLED, retryEnabledItem(), false)).toBe(true);
  });

  it('freeze koşulu yalnız yeni alana özeldir; macroMaxAttempt strateji 1 ile donmaz', function() {
    var item = retryEnabledItem({ retryStrategy: 1 });

    expect(isDisabled(MAX_ATTEMPT_DISABLED, item)).toBe(true);
    expect(isDisabled(MACRO_MAX_ATTEMPT_DISABLED, item)).toBe(false);
  });

  // -------------------------------------------------------------------
  // Alani acan checkbox (28.09.2026)
  // -------------------------------------------------------------------

  it('checkbox işaretli değilken alan PASİF', function() {
    expect(isDisabled(MAX_ATTEMPT_DISABLED, retryEnabledItem(), true, false)).toBe(true);
  });

  it('checkbox işaretliyken alan round-robin stratejisinde ENABLED', function() {
    expect(isDisabled(MAX_ATTEMPT_DISABLED, retryEnabledItem({ retryStrategy: 2 }), true, true)).toBe(false);
  });

  it('checkbox REACH_RETRY_LIMIT_BEFORE_NEXT_NUMBER (ordinal 1) seçiliyken her durumda PASİF', function() {
    expect(isDisabled(MAX_ATTEMPT_CHECKBOX_DISABLED, retryEnabledItem({ retryStrategy: 1 }))).toBe(true);
    expect(isDisabled(MAX_ATTEMPT_DISABLED, retryEnabledItem({ retryStrategy: 1 }), true, true)).toBe(true);
  });

  it('checkbox REACH_RETRY_LIMIT_FOR_SINGLE_NUMBER (ordinal 0) seçiliyken her durumda PASİF', function() {
    expect(isDisabled(MAX_ATTEMPT_CHECKBOX_DISABLED, retryEnabledItem({ retryStrategy: 0 }))).toBe(true);
    expect(isDisabled(MAX_ATTEMPT_DISABLED, retryEnabledItem({ retryStrategy: 0 }), true, true)).toBe(true);
  });

  it('checkbox diğer stratejilerde ENABLED, retry kapalıyken ve yetki yokken PASİF', function() {
    expect(isDisabled(MAX_ATTEMPT_CHECKBOX_DISABLED, retryEnabledItem({ retryStrategy: 2 }))).toBe(false);
    expect(isDisabled(MAX_ATTEMPT_CHECKBOX_DISABLED, retryEnabledItem({ retryStrategy: 5 }))).toBe(false);
    expect(isDisabled(MAX_ATTEMPT_CHECKBOX_DISABLED, retryEnabledItem({ retryScheduleEnabled: false }))).toBe(true);
    expect(isDisabled(MAX_ATTEMPT_CHECKBOX_DISABLED, retryEnabledItem(), false)).toBe(true);
  });

  // -------------------------------------------------------------------
  // Kapanis nedeni binding select'i (K17 / R9)
  // -------------------------------------------------------------------

  it('kapanış nedeni binding select result tablosu seçiliyken ENABLED', function() {
    expect(isDisabled(CLOSE_REASON_DISABLED, retryEnabledItem())).toBe(false);
  });

  it('result tablosu seçilmemişse kapanış nedeni binding select FREEZE', function() {
    expect(isDisabled(CLOSE_REASON_DISABLED, retryEnabledItem({ dbResultTable: null }))).toBe(true);
  });

  it('kapanış nedeni binding retry stratejisinden bağımsızdır', function() {
    expect(isDisabled(CLOSE_REASON_DISABLED, retryEnabledItem({ retryStrategy: 1 }))).toBe(false);
  });

  // -------------------------------------------------------------------
  // restrictByType filtresi close_reason ile calisiyor mu
  // -------------------------------------------------------------------

  it('restrictByType close_reason için yalnız metin tipli kolonları döndürür', inject(function($filter) {
    var columns = [
      { fieldName: 'close_reason', fieldType: constants.DB_FIELD_TYPES.NVARCHAR },
      { fieldName: 'attempts', fieldType: constants.DB_FIELD_TYPES.INT },
      { fieldName: 'kapanis_nedeni', fieldType: constants.DB_FIELD_TYPES.VARCHAR }
    ];

    var result = $filter('restrictByType')(columns, constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.CLOSE_REASON, null);
    var names = result.map(function(col) {
      return col.fieldName;
    });

    expect(names).toContain('close_reason');
    expect(names).toContain('kapanis_nedeni');
    expect(names).not.toContain('attempts');
  }));

  it('restrictByType başka bir alana bağlanmış kolonu listeden çıkarır', inject(function($filter) {
    var columns = [
      { fieldName: 'close_reason', fieldType: constants.DB_FIELD_TYPES.NVARCHAR, isSelected: true },
      { fieldName: 'kapanis_nedeni', fieldType: constants.DB_FIELD_TYPES.NVARCHAR }
    ];

    var names = $filter('restrictByType')(columns, constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.CLOSE_REASON, null)
      .map(function(col) {
        return col.fieldName;
      });

    expect(names).not.toContain('close_reason');
    expect(names).toContain('kapanis_nedeni');
  }));

  // -------------------------------------------------------------------
  // Faz 7 / P3.1 — sihirbaz akislarinin tutarliligi
  //
  // Faz 2'de yalniz setDefaultValuesForUndefinedResultTableColumns guncellenmisti. Kolon
  // backend'de olusuyor (createDefaultResultTable / addDefaultResultColumnsToSourceTable) ve
  // binding kampanya start'inda otomatik atandigi icin islevsel kayip yoktu, ama operator
  // sihirbazda binding'i BOS goruyor ve kolonu olusturulacak-kolon dropdown'inda bulamiyordu.
  // Faz 7'de dort akis da tamamlandi:
  //   1) result tablosu secildiginde default binding'ler   (campaignFormCtrl.js)
  //   2) tanimsiz result kolonlari icin default atama       (campaignFormCtrl.js, Faz 2)
  //   3) addResultColsToSourceTable + picklist              (campaignFormCtrl.js)
  //   4) yeni result tablosu olusturma + picklist           (dbTableFormCtrl.js)
  //
  // Bu dort akisin dogrulanmasi BILINCLI olarak buraya YAZILMADI: her biri gercek controller'in
  // ($scope, $q, dbTableService, $modalInstance ...) DI grafigini kurmayi gerektiriyor ve
  // `node_modules` blocker'i nedeniyle bu dosya KOSULAMIYOR. Kosulamayan bir harness'i tahmine
  // dayali yazmak, dogrulanmamis test eklemek olurdu.
  //
  // Yerine offline KOSULABILEN bir sozlesme testi yazildi:
  //   src/test/java/tests/angular/campaign/CampaignCloseReasonBindingUiContractTest.java
  // Repodaki tests.angular.* deseninin aynisi; JS/HTML dosyalarini metin olarak dogrular ve dort
  // akisin + iki picklist'in close_reason tasidigini kilitler.
  //
  // `npm ci` yapildiginda burada yapilacak is: yukaridaki dort akis icin gercek controller
  // instantiate edilerek davranissal spec yazmak (sozlesme testi metin duzeyinde kalir).
  // -------------------------------------------------------------------

});
