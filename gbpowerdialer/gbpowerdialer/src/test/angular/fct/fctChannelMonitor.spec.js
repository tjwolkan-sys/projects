/* global describe, beforeEach, inject, it, expect, module, jasmine */

describe('fctChannelMonitorCtrl', function() {
  var $controller;
  var $rootScope;
  var $interval;
  var $q;
  var factoryMock;
  var snapshotResponse;
  var now;

  beforeEach(module('app'));

  beforeEach(module(function($provide) {
    factoryMock = {
      getSnapshot: jasmine.createSpy('getSnapshot').and.callFake(function() {
        return $q.when({ data: snapshotResponse });
      })
    };
    $provide.value('fctChannelFactory', factoryMock);
  }));

  beforeEach(inject(function(_$controller_, _$rootScope_, _$interval_, _$q_) {
    $controller = _$controller_;
    $rootScope = _$rootScope_;
    $interval = _$interval_;
    $q = _$q_;
    now = new Date().getTime();
    snapshotResponse = buildSnapshot();
  }));

  function buildSnapshot(overrides) {
    var snapshot = {
      serverTimeMs: now,
      ttlMs: 3900000,
      staleWarnMs: 600000,
      cooldownMs: 60000,
      headroomPercent: 0,
      gateEnabled: true,
      entries: [
        entry('05550000001', 'MOBILE', 10, 8, 0),
        entry('05550000002', 'MOBILE', 15, 15, now + 27000),
        entry('05550000003', 'FIXED', 8, 3, 0),
        entry('05550000004', 'MOBILE', 12, 0, 0),
        entry('05550000005', 'FIXED', 20, 19, 0)
      ],
      recentEvents: [
        { id: 5, ts: now, kind: 'MARK_FULL', phoneNumber: '05550000002', callId: null, detail: 'cooldown baslatildi (60sn)' },
        { id: 4, ts: now - 1000, kind: 'RELEASE', phoneNumber: '05550000001', callId: 'dl-abcdef1234567890', detail: null },
        { id: 3, ts: now - 2000, kind: 'SKIP', phoneNumber: '05550000002', callId: 'dl-2', detail: 'cooldown kalan 27sn' },
        { id: 2, ts: now - 3000, kind: 'PRUNE', phoneNumber: '05550000005', callId: 'dl-3', detail: 'TTL asildi' },
        { id: 1, ts: now - 4000, kind: 'REJECT', phoneNumber: '05550000005', callId: 'dl-4', detail: 'kapasite dolu' }
      ],
      kpi: { prunedLast5Min: 12, skippedLast5Min: 34, rejectedLast5Min: 7 }
    };
    return angular.extend(snapshot, overrides || {});
  }

  function entry(phoneNumber, type, capacity, inFlightCount, cooldownUntilMs) {
    var inFlight = [];
    for (var index = 0; index < inFlightCount; index++) {
      inFlight.push({ callId: 'dl-' + phoneNumber + '-' + index, sinceMs: now - ((index + 1) * 5000), campaignId: 1 });
    }
    return {
      phoneNumber: phoneNumber,
      type: type,
      attempts: '100',
      capacity: capacity,
      effectiveCapacity: capacity,
      inFlight: inFlight,
      cooldownUntilMs: cooldownUntilMs,
      lastEventMs: now
    };
  }

  function createController() {
    var scope = $rootScope.$new();
    var controller = $controller('fctChannelMonitorCtrl as fctMonitor', { $scope: scope });
    $rootScope.$digest();
    return controller;
  }

  it('ilk yüklemede snapshot alır ve aggregate değerlerini hesaplar', function() {
    var vm = createController();

    expect(factoryMock.getSnapshot).toHaveBeenCalled();
    expect(vm.loaded).toBe(true);
    expect(vm.entries.length).toBe(5);
    expect(vm.aggregate.totalCapacity).toBe(65);
    expect(vm.aggregate.totalInFlight).toBe(45);
    expect(vm.aggregate.occupancyRate).toBe(69.2);
    expect(vm.aggregate.cooldownCount).toBe(1);
  });

  it('DURUM türetme kurallarını uygular', function() {
    var vm = createController();

    expect(vm.entries[0].status.label).toBe('HIGH USAGE');
    expect(vm.entries[1].status.label).toBe('COOLDOWN');
    expect(vm.entries[1].status.showRemaining).toBe(true);
    expect(vm.entries[2].status.label).toBe('HEALTHY');
    expect(vm.entries[3].status.label).toBe('IDLE');
    expect(vm.entries[4].status.label).toBe('HIGH USAGE');
  });

  it('cooldown geri sayımını mm:ss olarak gösterir', function() {
    var vm = createController();

    expect(vm.entries[1].status.remaining).toMatch(/^00:2[0-9]$/);
  });

  it('kapasite 10 ve altındaysa lamba sayısı kapasiteye eşittir', function() {
    var vm = createController();

    expect(vm.entries[0].lamps.length).toBe(10);
    expect(vm.entries[2].lamps.length).toBe(8);
  });

  it('kapasite 10 üstündeyse lambaları ölçekler', function() {
    var vm = createController();

    // capacity 20 -> her lamba 2 kanal -> 10 lamba
    expect(vm.entries[4].lamps.length).toBe(10);
    // capacity 15 -> her lamba 2 kanal -> 8 lamba
    expect(vm.entries[1].lamps.length).toBe(8);
  });

  it('cooldown satırının tüm lambalarını kırmızı işaretler', function() {
    var vm = createController();

    expect(vm.entries[1].lamps[0].cssClass).toContain('fct-lamp-cooldown');
    expect(vm.entries[1].rowClass).toBe('fct-row-cooldown');
  });

  it('dolu satırın lambalarını full, boş slotları empty gösterir', function() {
    var vm = createController();

    expect(vm.entries[0].lamps[0].cssClass).toContain('fct-lamp-busy');
    expect(vm.entries[0].lamps[9].cssClass).toContain('fct-lamp-empty');
  });

  it('en yaşlı üç rezervasyonu chip olarak verir ve fazlasını sayar', function() {
    var vm = createController();

    expect(vm.entries[0].ageChips.length).toBe(3);
    expect(vm.entries[0].overflowCount).toBe(5);
    expect(vm.entries[0].ageChips[0].ageSeconds).toBeGreaterThan(vm.entries[0].ageChips[1].ageSeconds - 1);
  });

  it('arama ve tip filtresini birlikte uygular', function() {
    var vm = createController();

    vm.typeFilter = 'FIXED';
    vm.applyFilters();
    expect(vm.visibleEntries.length).toBe(2);

    vm.search = '0000003';
    vm.applyFilters();
    expect(vm.visibleEntries.length).toBe(1);
    expect(vm.visibleEntries[0].phoneNumber).toBe('05550000003');

    vm.typeFilter = 'MOBILE';
    vm.applyFilters();
    expect(vm.visibleEntries.length).toBe(0);
  });

  it('tip filtresi seçeneklerini snapshot içeriğinden dinamik kurar', function() {
    var vm = createController();

    expect(vm.typeOptions).toEqual([ 'FIXED', 'MOBILE' ]);
  });

  it('tablo ve kart görünümü arasında geçiş yapar', function() {
    var vm = createController();

    expect(vm.viewMode).toBe('table');
    vm.setViewMode('card');
    expect(vm.viewMode).toBe('card');
  });

  it('feed olaylarını etiketler ve callId son 10 hanesini gösterir', function() {
    var vm = createController();

    expect(vm.visibleEvents[0].label).toBe('MARK FULL (CAUSE 34)');
    expect(vm.visibleEvents[0].cssClass).toBe('fct-event-danger');
    expect(vm.visibleEvents[1].label).toBe('RELEASE');
    expect(vm.visibleEvents[1].shortCallId).toBe('1234567890');
    expect(vm.visibleEvents[2].label).toBe('SKIP (COOLDOWN)');
    expect(vm.visibleEvents[3].label).toBe('PRUNE (TTL EXPIRED)');
    expect(vm.visibleEvents[4].label).toBe('REJECT (CAPACITY FULL)');
  });

  it('KPI şeridini snapshot ve canlı yaşlardan hesaplar', function() {
    var vm = createController();

    expect(vm.kpi.ttlSeconds).toBe(3900);
    expect(vm.kpi.pruned).toBe(12);
    expect(vm.kpi.skipped).toBe(34);
    expect(vm.kpi.avgAgeSeconds).toBeGreaterThan(0);
  });

  it('seçilen aralıkta polling yapar', function() {
    var vm = createController();
    expect(factoryMock.getSnapshot.calls.count()).toBe(1);

    $interval.flush(2000);
    expect(factoryMock.getSnapshot.calls.count()).toBe(2);

    vm.intervalSeconds = 5;
    vm.changeInterval();
    $interval.flush(2000);
    expect(factoryMock.getSnapshot.calls.count()).toBe(2);

    $interval.flush(3000);
    expect(factoryMock.getSnapshot.calls.count()).toBe(3);
  });

  it('duraklatıldığında polling durur, devam edilince sürer', function() {
    var vm = createController();

    vm.togglePause();
    $interval.flush(10000);
    expect(factoryMock.getSnapshot.calls.count()).toBe(1);

    vm.togglePause();
    $interval.flush(2000);
    expect(factoryMock.getSnapshot.calls.count()).toBe(2);
  });

  it('auto refresh kapatıldığında polling durur', function() {
    var vm = createController();

    vm.toggleAutoRefresh();
    $interval.flush(10000);
    expect(factoryMock.getSnapshot.calls.count()).toBe(1);
  });

  it('snapshot hatası ekranı bozmaz, hata bayrağı set edilir', function() {
    factoryMock.getSnapshot.and.callFake(function() {
      return $q.reject({ status: 500 });
    });

    var vm = createController();

    expect(vm.loadError).toBe(true);
    expect(vm.entries.length).toBe(0);
  });

  it('TTL uyarı eşiğini geçen rezervasyonu stale işaretler', function() {
    snapshotResponse.entries = [ entry('05550000009', 'MOBILE', 4, 0, 0) ];
    snapshotResponse.entries[0].inFlight = [
      { callId: 'dl-genc', sinceMs: now - 10000, campaignId: 1 },
      { callId: 'dl-kacan', sinceMs: now - 900000, campaignId: 1 }
    ];

    var vm = createController();

    var chips = vm.entries[0].ageChips;
    expect(chips[0].callId).toBe('dl-kacan');
    expect(chips[0].stale).toBe(true);
    expect(chips[1].stale).toBe(false);
  });

  // QSS getRouteDetails sözleşmesi: type virgülle birleşik çok değerli gelir
  // (CT_MANUAL -> Manual, CT_PD_RETRY -> Retry, CT_PD_CALLBACK -> Callback, CT_PD_FRESH -> Dialer First),
  // attempts de virgüllüdür ve route'ta hiç AT_ attribute'u yoksa 0..99 ile doldurulur,
  // capacity NULL ise 0 döner (limit tanımsız; limiti QSS değil dialer uygular).
  describe('QSS alan sözleşmesi', function() {

    function qssEntry(phoneNumber, type, attempts, capacity) {
      return {
        phoneNumber: phoneNumber,
        type: type,
        attempts: attempts,
        capacity: capacity,
        effectiveCapacity: capacity,
        inFlight: [],
        cooldownUntilMs: 0,
        lastEventMs: now
      };
    }

    function attemptsFallback() {
      var values = [];
      for (var index = 0; index < 100; index++) {
        values.push(index);
      }
      return values.join(',');
    }

    it('virgülle birleşik type değerini token listesine ayırır', function() {
      snapshotResponse.entries = [ qssEntry('05550000021', 'Manual,Retry,Callback', '1', 4) ];

      var vm = createController();

      expect(vm.entries[0].typeTokens).toEqual([ 'Manual', 'Retry', 'Callback' ]);
    });

    it('tip filtresi seçeneklerini token bazında toplar ve tekrar etmez', function() {
      snapshotResponse.entries = [
        qssEntry('05550000021', 'Manual,Retry', '1', 4),
        qssEntry('05550000022', 'Retry,Dialer First', '1', 4)
      ];

      var vm = createController();

      expect(vm.typeOptions).toEqual([ 'Dialer First', 'Manual', 'Retry' ]);
    });

    it('tip filtresi token bazlı eşleşir, tam string eşleşmesi beklemez', function() {
      snapshotResponse.entries = [
        qssEntry('05550000021', 'Manual,Retry', '1', 4),
        qssEntry('05550000022', 'Callback', '1', 4)
      ];

      var vm = createController();

      vm.typeFilter = 'Retry';
      vm.applyFilters();

      expect(vm.visibleEntries.length).toBe(1);
      expect(vm.visibleEntries[0].phoneNumber).toBe('05550000021');
    });

    it('AT_ attribute yokken gelen 0..99 attempts değerini özetler', function() {
      snapshotResponse.entries = [ qssEntry('05550000021', 'Manual', attemptsFallback(), 4) ];

      var vm = createController();

      expect(vm.entries[0].attemptsSummary).toBe('0..99 (100)');
    });

    it('kısa attempts listesini olduğu gibi gösterir', function() {
      snapshotResponse.entries = [ qssEntry('05550000021', 'Manual', '1,2', 4) ];

      var vm = createController();

      expect(vm.entries[0].attemptsSummary).toBe('1, 2');
    });

    it('hiçbir FCT capacity göndermiyorsa gate pasif uyarısını işaretler', function() {
      snapshotResponse.entries = [
        qssEntry('05550000021', 'Manual', '1', 0),
        qssEntry('05550000022', 'Retry', '1', 0)
      ];

      var vm = createController();

      expect(vm.capacityMissing).toBe(true);
      expect(vm.aggregate.totalCapacity).toBe(0);
    });

    it('capacity gelen en az bir FCT varsa uyarıyı işaretlemez', function() {
      snapshotResponse.entries = [
        qssEntry('05550000021', 'Manual', '1', 0),
        qssEntry('05550000022', 'Retry', '1', 6)
      ];

      var vm = createController();

      expect(vm.capacityMissing).toBe(false);
    });

    it('capacity 0 olan numarada lamba çizmez, in-flight kanalları gösterir', function() {
      var entryWithoutCapacity = qssEntry('05550000021', 'Manual', '1', 0);
      entryWithoutCapacity.inFlight = [ { callId: 'dl-1', sinceMs: now - 3000, campaignId: 1 } ];
      snapshotResponse.entries = [ entryWithoutCapacity ];

      var vm = createController();

      expect(vm.entries[0].lamps.length).toBe(1);
      expect(vm.entries[0].lamps[0].cssClass).toContain('fct-lamp-unknown');
      expect(vm.entries[0].occupancyPercent).toBe(0);
    });
  });

  it('feed satır sayısını sınırlar ve tümünü göster ile açar', function() {
    var events = [];
    for (var index = 0; index < 40; index++) {
      events.push({ id: index, ts: now - index * 100, kind: 'ACQUIRE', phoneNumber: '05550000001', callId: 'dl-' + index, detail: null });
    }
    snapshotResponse.recentEvents = events;

    var vm = createController();
    expect(vm.visibleEvents.length).toBe(15);

    vm.toggleShowAllEvents();
    expect(vm.visibleEvents.length).toBe(40);
  });
});
