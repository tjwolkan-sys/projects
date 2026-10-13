/* global describe, beforeEach, inject, it, expect, module, jasmine */

describe('dncPhoneFormCtrl', function() {
  var $controller;
  var $rootScope;
  var alertService;
  var modalInstance;
  var createdAt;

  beforeEach(module('app'));

  beforeEach(module(function($provide) {
    $provide.value('locMsg', {
      'alert.dnc.phone.duplicate': 'duplicate',
      'alert.dnc.phone.expireDate.beforeCreateDate': 'beforeCreateDate'
    });
  }));

  beforeEach(inject(function(_$controller_, _$rootScope_, _alertService_) {
    $controller = _$controller_;
    $rootScope = _$rootScope_;
    alertService = _alertService_;
    alertService.alerts = [];
    createdAt = new Date('2026-04-27T10:00:00Z');
    modalInstance = {
      close: jasmine.createSpy('close'),
      dismiss: jasmine.createSpy('dismiss')
    };
  }));

  function createController(existingNumbers) {
    var scope = $rootScope.$new();
    scope.dncPhone = {
      $setSubmitted: jasmine.createSpy('$setSubmitted')
    };

    var controller = $controller('dncPhoneFormCtrl as dncphone', {
      $scope: scope,
      $modalInstance: modalInstance,
      existingNumbers: existingNumbers || [],
      createdAt: createdAt
    });

    return {
      scope: scope,
      controller: controller
    };
  }

  it('aynı telefon numarası geldiğinde save yerine duplicate alert verir', function() {
    var setup = createController(['905551112233']);

    setup.controller.item.phoneNumber = '905551112233';

    setup.controller.save();

    expect(setup.scope.dncPhone.$setSubmitted).toHaveBeenCalled();
    expect(alertService.alerts).toEqual([
      { type: 'danger', msg: 'duplicate' }
    ]);
    expect(modalInstance.close).not.toHaveBeenCalled();
  });

  it('expire date add date öncesindeyse save yerine validation alert verir', function() {
    var setup = createController();

    setup.controller.item.phoneNumber = '905551112233';
    setup.controller.item.expiredDate = new Date('2026-04-27T09:59:00Z');

    setup.controller.save();

    expect(setup.scope.dncPhone.$setSubmitted).toHaveBeenCalled();
    expect(alertService.alerts).toEqual([
      { type: 'danger', msg: 'beforeCreateDate' }
    ]);
    expect(modalInstance.close).not.toHaveBeenCalled();
  });

  it('geçerli girişte createdAt bilgisini koruyarak modalı kapatır', function() {
    var setup = createController();

    setup.controller.item.phoneNumber = '905551112233';
    setup.controller.item.expiredDate = new Date('2026-04-27T10:01:00Z');

    setup.controller.save();

    expect(modalInstance.close).toHaveBeenCalledWith(setup.controller.item);
    expect(setup.controller.item.createDate).toEqual(createdAt);
    expect(alertService.alerts).toEqual([]);
  });
});
