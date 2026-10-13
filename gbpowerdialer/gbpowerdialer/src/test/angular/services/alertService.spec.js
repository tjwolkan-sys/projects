/* global describe, beforeEach, inject, it, expect, module */

describe('alertService', function() {
  var alertService;

  beforeEach(module('app'));

  beforeEach(inject(function(_alertService_) {
    alertService = _alertService_;
  }));

  it('geçici uyarıları ekler ve sıra bilgisini korur', function() {
    alertService.add('success', 'Kampanya başlatıldı');
    alertService.add('warning', 'Kampanya duraklatıldı');

    expect(alertService.alerts).toEqual([
      { type: 'success', msg: 'Kampanya başlatıldı' },
      { type: 'warning', msg: 'Kampanya duraklatıldı' }
    ]);
  });

  it('kalıcı uyarıları geçici uyarılardan ayrı tutar', function() {
    alertService.add('info', 'Geçici mesaj');
    alertService.addPermanent('danger', 'Kalıcı mesaj');

    expect(alertService.alerts).toEqual([
      { type: 'info', msg: 'Geçici mesaj' }
    ]);
    expect(alertService.permanentAlerts).toEqual([
      { type: 'danger', msg: 'Kalıcı mesaj' }
    ]);
  });

  it('seçilen geçici ve kalıcı uyarıyı kapatır', function() {
    alertService.add('info', 'İlk geçici mesaj');
    alertService.add('success', 'İkinci geçici mesaj');
    alertService.addPermanent('warning', 'İlk kalıcı mesaj');
    alertService.addPermanent('danger', 'İkinci kalıcı mesaj');

    alertService.closeAlert(0);
    alertService.closePermanentAlert(1);

    expect(alertService.alerts).toEqual([
      { type: 'success', msg: 'İkinci geçici mesaj' }
    ]);
    expect(alertService.permanentAlerts).toEqual([
      { type: 'warning', msg: 'İlk kalıcı mesaj' }
    ]);
  });
});
