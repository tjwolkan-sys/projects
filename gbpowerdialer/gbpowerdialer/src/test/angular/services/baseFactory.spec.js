/* global describe, beforeEach, inject, it, expect, module */

describe('baseFactory', function() {
  var baseFactory;
  var alertService;
  var $httpBackend;
  var $q;
  var $rootScope;

  beforeEach(module('app'));

  beforeEach(inject(function(_baseFactory_, _alertService_, _$httpBackend_, _$q_, _$rootScope_) {
    baseFactory = _baseFactory_;
    alertService = _alertService_;
    $httpBackend = _$httpBackend_;
    $q = _$q_;
    $rootScope = _$rootScope_;
  }));

  function createSubject(overrides) {
    var subject = Object.create(baseFactory.prototype);
    angular.extend(subject, overrides || {});
    return subject;
  }

  it('save başarılı olduğunda kayıt uyarısı ekler ve response döner', function() {
    var response = { data: { id: 42 } };
    var resolved;
    var subject = createSubject({
      _save: function(item, disableAlert) {
        expect(item).toEqual({ id: 42 });
        expect(disableAlert).toBeFalsy();
        return $q.when(response);
      }
    });

    subject.save({ id: 42 }).then(function(result) {
      resolved = result;
    });
    $rootScope.$digest();

    expect(resolved).toBe(response);
    expect(alertService.alerts).toEqual([
      { type: 'success', msg: 'Kaydetme işlemi başarılı' }
    ]);
  });

  it('save disableAlert ile çağrıldığında uyarı eklemez', function() {
    var response = { data: { id: 7 } };
    var subject = createSubject({
      _save: function(item, disableAlert) {
        expect(item).toEqual({ id: 7 });
        expect(disableAlert).toBe(true);
        return $q.when(response);
      }
    });

    subject.save({ id: 7 }, true);
    $rootScope.$digest();

    expect(alertService.alerts).toEqual([]);
  });

  it('remove başarılı olduğunda silme uyarısı ekler ve response döner', function() {
    var response = { data: { removed: true } };
    var resolved;
    var subject = createSubject({
      _remove: function(id) {
        expect(id).toBe(9);
        return $q.when(response);
      }
    });

    subject.remove(9).then(function(result) {
      resolved = result;
    });
    $rootScope.$digest();

    expect(resolved).toBe(response);
    expect(alertService.alerts).toEqual([
      { type: 'success', msg: 'Silme işlemi başarılı' }
    ]);
  });

  it('setEnabled batch endpointine id listesini ve enabled bilgisini gönderir', function() {
    var resolved;
    var subject = createSubject();

    $httpBackend.expectPUT('campaign/batch?enabled=false&ids=10,11', {}).respond(200, {
      updated: 2
    });

    subject.setEnabled([10, 11], false, 'campaign').then(function(result) {
      resolved = result;
    });
    $httpBackend.flush();

    expect(resolved).toEqual({ updated: 2 });
    expect(alertService.alerts).toEqual([
      { type: 'success', msg: 'Kaydetme işlemi başarılı' }
    ]);
  });
});
