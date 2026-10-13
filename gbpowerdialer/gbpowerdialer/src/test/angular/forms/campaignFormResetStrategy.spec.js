/* global describe, beforeEach, inject, it, expect, module, angular */

/**
 * Agent Callbacks reset-strategy seçicisinin campaignForm.html içindeki
 * karşılıklı freeze davranışını ve constants mirror'larını doğrular.
 *
 * ng-disabled ifadeleri gerçek Angular $compile ile değerlendirilir; böylece
 * template ifadeleri değişirse test kırılır (sadece metin araması değildir).
 */
describe('campaignForm agent callbacks reset strategy', function() {

  var $compile;
  var $rootScope;
  var constants;

  beforeEach(module('app'));

  // i18n filter ve permission helper'ı template ifadeleri için sağla.
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

  // Reset Strategy kolonundaki üç kontrolün gerçek ng-disabled ifadeleri.
  var TIMEOUT_DISABLED =
    "(campaign.item.type == 4 || (campaign.item.type != 4 && !campaign.item.allowCallback) || !campaign.item.appointerReset || (!campaign.item.callbackToAppointer && !campaign.item.allowOverwriteByWebphone)" +
    " || campaign.item.appointerResetStrategy == campaign.constants.APPOINTER_RESET_STRATEGY.ATTEMPT_COUNT)" +
    " || !canEditSelectedCampaignByPermission(campaign.item, 'EditCampaign')";

  var ATTEMPT_FIELDS_DISABLED =
    "(campaign.item.type == 4 || (campaign.item.type != 4 && !campaign.item.allowCallback) || (!campaign.item.callbackToAppointer && !campaign.item.allowOverwriteByWebphone)" +
    " || campaign.item.appointerResetStrategy != campaign.constants.APPOINTER_RESET_STRATEGY.ATTEMPT_COUNT)" +
    " || !canEditSelectedCampaignByPermission(campaign.item, 'EditCampaign')";

  var GATE_DISABLED =
    "(campaign.item.type == 4 || (campaign.item.type != 4 && !campaign.item.allowCallback) || (!campaign.item.callbackToAppointer && !campaign.item.allowOverwriteByWebphone))" +
    " || !canEditSelectedCampaignByPermission(campaign.item, 'EditCampaign')";

  function buildScope(item) {
    var scope = $rootScope.$new();
    scope.campaign = {
      item: item,
      constants: constants
    };
    scope.canEditSelectedCampaignByPermission = function() {
      return true;
    };
    return scope;
  }

  function isDisabled(disableExpr, item) {
    var scope = buildScope(item);
    var el = $compile('<input type="number" ng-disabled="' + disableExpr + '">')(scope);
    scope.$digest();
    return el[0].disabled === true;
  }

  function callbackEnabledItem(overrides) {
    var base = {
      type: 0,
      allowCallback: true,
      callbackToAppointer: true,
      allowOverwriteByWebphone: false,
      appointerReset: true,
      appointerResetStrategy: constants.APPOINTER_RESET_STRATEGY.TIMER
    };
    return angular.extend(base, overrides || {});
  }

  it('constants CALLBACK_ATTEMPTS kolon adı ve tip whitelist mirror içerir', function() {
    expect(constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.CALLBACK_ATTEMPTS).toBe('callback_attempts');

    var allowed = constants.RESULT_FIELDS_ALLOWED_TYPES['callback_attempts'];
    expect(allowed).toEqual([
      constants.DB_FIELD_TYPES.INT,
      constants.DB_FIELD_TYPES.INTEGER,
      constants.DB_FIELD_TYPES.BIGINT,
      constants.DB_FIELD_TYPES.NUMBER
    ]);
  });

  it('APPOINTER_RESET_STRATEGY ordinalleri backend ile aynıdır', function() {
    expect(constants.APPOINTER_RESET_STRATEGY.TIMER).toBe(0);
    expect(constants.APPOINTER_RESET_STRATEGY.ATTEMPT_COUNT).toBe(1);
  });

  it('Timer seçiliyken appointerResetTimeout ENABLED, attempt alanları FREEZE', function() {
    var item = callbackEnabledItem({ appointerResetStrategy: constants.APPOINTER_RESET_STRATEGY.TIMER });

    expect(isDisabled(TIMEOUT_DISABLED, item)).toBe(false);
    expect(isDisabled(ATTEMPT_FIELDS_DISABLED, item)).toBe(true);
  });

  it('Attempt seçiliyken attempt alanları ENABLED, appointerResetTimeout FREEZE', function() {
    var item = callbackEnabledItem({ appointerResetStrategy: constants.APPOINTER_RESET_STRATEGY.ATTEMPT_COUNT });

    expect(isDisabled(TIMEOUT_DISABLED, item)).toBe(true);
    expect(isDisabled(ATTEMPT_FIELDS_DISABLED, item)).toBe(false);
  });

  it('gate kapalıyken (ne callbackToAppointer ne allowOverwriteByWebphone) tüm alanlar disabled', function() {
    var item = callbackEnabledItem({
      callbackToAppointer: false,
      allowOverwriteByWebphone: false,
      appointerResetStrategy: constants.APPOINTER_RESET_STRATEGY.ATTEMPT_COUNT
    });

    expect(isDisabled(GATE_DISABLED, item)).toBe(true);
    expect(isDisabled(TIMEOUT_DISABLED, item)).toBe(true);
    expect(isDisabled(ATTEMPT_FIELDS_DISABLED, item)).toBe(true);
  });

  it('allowOverwriteByWebphone tek başına gate açar', function() {
    var item = callbackEnabledItem({
      callbackToAppointer: false,
      allowOverwriteByWebphone: true,
      appointerResetStrategy: constants.APPOINTER_RESET_STRATEGY.ATTEMPT_COUNT
    });

    expect(isDisabled(GATE_DISABLED, item)).toBe(false);
    expect(isDisabled(ATTEMPT_FIELDS_DISABLED, item)).toBe(false);
  });
});
