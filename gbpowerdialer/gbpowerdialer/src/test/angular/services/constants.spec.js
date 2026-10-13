/* global describe, beforeEach, inject, it, expect, module */

describe('constants (customer-scope DNC mapping)', function() {
  var constants;

  beforeEach(module('app'));

  beforeEach(module(function($provide) {
    // constants servisi locMsg'ye bağımlıdır; spec için basit stub yeterlidir.
    $provide.value('locMsg', function(key) {
      return key;
    });
  }));

  beforeEach(inject(function(_constants_) {
    constants = _constants_;
  }));

  it('CUSTOMER_NO ve DATE_ENTERED varsayılan kolon adlarını tanımlar', function() {
    expect(constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.CUSTOMER_NO).toBe('customer_no');
    expect(constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.DATE_ENTERED).toBe('date_entered');
  });

  it('Customer No mapping dropdown\'u için string tip kısıtlarını tanımlar', function() {
    var allowedTypes = constants.RESULT_FIELDS_ALLOWED_TYPES[constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.CUSTOMER_NO];

    expect(allowedTypes).toBeDefined();
    expect(allowedTypes).toContain(constants.DB_FIELD_TYPES.VARCHAR);
    expect(allowedTypes).toContain(constants.DB_FIELD_TYPES.NVARCHAR);
    expect(allowedTypes).toContain(constants.DB_FIELD_TYPES.NVARCHAR2);
    expect(allowedTypes).toContain(constants.DB_FIELD_TYPES.CHARACTER_VARYING);
  });

  it('Date Entered mapping dropdown\'u için tarih tip kısıtlarını tanımlar', function() {
    var allowedTypes = constants.RESULT_FIELDS_ALLOWED_TYPES[constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.DATE_ENTERED];

    expect(allowedTypes).toBeDefined();
    expect(allowedTypes).toContain(constants.DB_FIELD_TYPES.DATETIME);
    expect(allowedTypes).toContain(constants.DB_FIELD_TYPES.DATETIME2);
    expect(allowedTypes).toContain(constants.DB_FIELD_TYPES.SMALLDATETIME);
    expect(allowedTypes).toContain(constants.DB_FIELD_TYPES.TIMESTAMP);
  });

  it('mevcut Called Number tip kısıtlarını değiştirmez (regression)', function() {
    var calledNumberTypes = constants.RESULT_FIELDS_ALLOWED_TYPES[constants.DEFAULT_CAMPAIGN_COLUMN_NAMES.CALLED_NUMBER];

    expect(calledNumberTypes).toEqual([
      constants.DB_FIELD_TYPES.VARCHAR,
      constants.DB_FIELD_TYPES.NVARCHAR,
      constants.DB_FIELD_TYPES.NVARCHAR2,
      constants.DB_FIELD_TYPES.CHARACTER_VARYING
    ]);
  });
});
