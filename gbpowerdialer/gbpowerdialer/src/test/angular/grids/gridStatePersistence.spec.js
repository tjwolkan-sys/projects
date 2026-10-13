/* global describe, beforeEach, afterEach, inject, it, expect, module, angular */

/**
 * Task 324857: Grid filtre ve kolon gorunurluk tercihleri sayfa degisiminde
 * sifirlaniyordu.
 *
 * Kok neden zinciri (uretimden birebir):
 *   1. Controller, save handler'larini onRegisterApi icinde canliya alir.
 *      onRegisterApi, Grid.renderingComplete icinde -- yani kolonlar kurulmadan
 *      once -- cagrildigi icin restore $timeout(..., 10) ile ertelenmek
 *      zorundadir.
 *   2. Ilk digest'te filtre satiri derlenir. Select filtre template'indeki
 *      ng-init, colFilter.term degerini degistirir.
 *   3. uiGridHeaderCell'in col.filters[i].term watch'i filterChanged yayinlar.
 *   4. Controller'in filterChanged handler'i save cagirir; grid o anda hala
 *      varsayilan durumdadir ve kullanicinin localStorage kaydi ezilir.
 *   5. $timeout tetiklenir, restore az once yok edilen degeri okur.
 *
 * Cozum: gridStateService, bir grid instance'i icin restore tamamlanmadan
 * hicbir otomatik save'i kalici yazmaz. Isaret grid nesnesinde tutulur.
 */
describe('Grid state persistence (task 324857)', function() {

  var STATE_NAME = 'specGrid';
  var DEFAULT_STATE_NAME = 'defaultSpecGrid';

  var gridStateService, localStorageService;

  beforeEach(module('LocalStorageModule'));
  beforeEach(module('app'));

  beforeEach(inject(function(_gridStateService_, _localStorageService_) {
    gridStateService = _gridStateService_;
    localStorageService = _localStorageService_;
    window.localStorage.clear();
  }));

  afterEach(function() {
    window.localStorage.clear();
  });

  // Gercek gridApi yerine, saveState contract'ini tasiyan minimum sahte nesne.
  // grid nesnesi bilerek bostur: bayragin oraya yazildigini dogrulariz.
  function fakeGridApi(stateToSave) {
    var api = {
      grid : {},
      saveState : {
        save : function() {
          return stateToSave;
        },
        restore : function(scope, state) {
          api.restoredWith = state;
          api.flagDuringRestore = api.grid.gbpdGridStateRestored;
        }
      }
    };
    return api;
  }

  describe('gridStateService gate', function() {

    it('restore calismadan once save kalici yazmaz', function() {
      var api = fakeGridApi({ columns : [ { name : 'name' } ] });

      gridStateService.save(STATE_NAME, api);

      expect(localStorageService.get(STATE_NAME)).toBeNull();
    });

    it('restore calistiktan sonra save kalici yazar', function() {
      var api = fakeGridApi({ columns : [ { name : 'name', visible : true } ] });

      gridStateService.restore(STATE_NAME, api, {});
      gridStateService.save(STATE_NAME, api);

      expect(localStorageService.get(STATE_NAME).columns[0].name).toBe('name');
    });

    it('restore bayragi ui-grid restore tamamlanmadan acilmaz', function() {
      // ui-grid restoreColumns, kolon dongusunun icinde filterChanged yayinlar.
      // Bayrak erken acilirsa yarim restore edilmis state kalici yazilir.
      var api = fakeGridApi({});
      localStorageService.set(STATE_NAME, { columns : [ { name : 'name' } ] });

      gridStateService.restore(STATE_NAME, api, {});

      expect(api.flagDuringRestore).toBeFalsy();
      expect(api.grid.gbpdGridStateRestored).toBe(true);
    });

    it('kayitli state yokken de bayrak acilir; ilk ziyarette tercihler kaydedilebilir', function() {
      var api = fakeGridApi({ columns : [ { name : 'name' } ] });

      gridStateService.restore(STATE_NAME, api, {});

      expect(api.restoredWith).toBeUndefined();
      expect(api.grid.gbpdGridStateRestored).toBe(true);
    });

    it('bayrak grid nesnesinde tutulur; her grid instance kendi gate\'ine sahiptir', function() {
      var first = fakeGridApi({ columns : [ { name : 'first' } ] });
      var second = fakeGridApi({ columns : [ { name : 'second' } ] });

      gridStateService.restore(STATE_NAME, first, {});
      gridStateService.save(STATE_NAME, second);

      // second henuz restore edilmedi: first'un restore'u onu acmamali.
      expect(localStorageService.get(STATE_NAME)).toBeNull();
    });
  });

  describe('gridStateService varsayilan state', function() {

    it('saveDefaultStateIfNotExist yalniz kayit yokken yazar', function() {
      var api = fakeGridApi({ columns : [ { name : 'ilk' } ] });
      gridStateService.saveDefaultStateIfNotExist(DEFAULT_STATE_NAME, api);
      expect(localStorageService.get(DEFAULT_STATE_NAME).columns[0].name).toBe('ilk');

      var other = fakeGridApi({ columns : [ { name : 'sonraki' } ] });
      gridStateService.saveDefaultStateIfNotExist(DEFAULT_STATE_NAME, other);
      expect(localStorageService.get(DEFAULT_STATE_NAME).columns[0].name).toBe('ilk');
    });

    it('saveDefaultStateIfNotExist gate\'e tabi degildir; restore oncesi cagrilir', function() {
      var api = fakeGridApi({ columns : [ { name : 'varsayilan' } ] });

      gridStateService.saveDefaultStateIfNotExist(DEFAULT_STATE_NAME, api);

      expect(api.grid.gbpdGridStateRestored).toBeUndefined();
      expect(localStorageService.get(DEFAULT_STATE_NAME)).not.toBeNull();
    });

    it('reset varsayilan state\'i kalici yazar ve geri yukler', function() {
      var api = fakeGridApi({});
      localStorageService.set(DEFAULT_STATE_NAME, { columns : [ { name : 'name' } ] });
      localStorageService.set(STATE_NAME, { columns : [ { name : 'name', filters : [ { term : 'guvencell' } ] } ] });

      gridStateService.reset(DEFAULT_STATE_NAME, STATE_NAME, api, {});

      expect(localStorageService.get(STATE_NAME).columns[0].filters).toBeUndefined();
      expect(api.restoredWith.columns[0].name).toBe('name');
    });
  });

  /**
   * Davranis katmani: gercek campaignCtrl + gercek ui-grid ile route degisimi
   * (scope destroy + yeni controller instance) replay edilir.
   */
  describe('campaign list grid, sayfa degisiminde', function() {

    var $rootScope, $compile, $controller, $timeout, uiGridGridMenuService;
    var jqueryStub, hadJquery;
    var mounted = null;

    var DATA = [
      { id : 11, name : 'GUVENCELL_A', type : 0, state : 0, enabled : true, tenantName : 't1' },
      { id : 22, name : 'OTHER_B', type : 0, state : 0, enabled : true, tenantName : 't1' },
      { id : 33, name : 'GUVENCELL_C', type : 0, state : 0, enabled : true, tenantName : 't1' }
    ];

    beforeEach(module('ui.grid'));
    beforeEach(module('ui.grid.saveState'));
    beforeEach(module('ui.grid.selection'));
    beforeEach(module('ui.grid.resizeColumns'));
    beforeEach(module('ui.grid.autoResize'));
    beforeEach(module('ui.grid.exporter'));

    beforeEach(module(function($provide, $filterProvider) {
      var identity = function() {
        return function(key) {
          return key;
        };
      };
      $filterProvider.register('i18n', identity);
      $filterProvider.register('translate', identity);
      $filterProvider.register('duration', identity);

      $provide.value('locMsg', {});
      $provide.value('$modal', {
        open : function() {
          return { result : { then : angular.noop } };
        }
      });
      $provide.factory('campaignFactory', [ '$q', '$timeout', function($q, $timeout) {
        return {
          // Uretimde veri HTTP ile gelir: restore'dan (t=10ms) SONRA.
          getAll : function() {
            var deferred = $q.defer();
            $timeout(function() {
              deferred.resolve({ data : angular.copy(DATA), totalItems : DATA.length });
            }, 150);
            return deferred.promise;
          },
          hideTenantColumn : function(defs) {
            return defs;
          },
          createNew : function() {
            return {};
          },
          remove : function() {
            return $q.when({});
          },
          action : angular.noop
        };
      } ]);
      $provide.value('adminLogsFactory', {
        check : angular.noop, proccessList : angular.noop, enableDisableFromList : angular.noop
      });
    }));

    beforeEach(inject(function(_$rootScope_, _$compile_, _$controller_, _$timeout_, _uiGridGridMenuService_) {
      $rootScope = _$rootScope_;
      $compile = _$compile_;
      $controller = _$controller_;
      $timeout = _$timeout_;
      uiGridGridMenuService = _uiGridGridMenuService_;

      // campaignCtrl realtime/poll akisi jQuery kullanir; spec'te ag cagrisi yapilmaz.
      hadJquery = typeof window.$ !== 'undefined';
      if (!hadJquery) {
        jqueryStub = function() {};
        jqueryStub.ajax = function() {
          return { done : function() { return this; } };
        };
        window.$ = jqueryStub;
      }

      $rootScope.permitedFieds = {};
      $rootScope.hasAnyRole = function() { return true; };
      $rootScope.hasAuthority = function() { return true; };
      $rootScope.getExportFilename = function(name, ext) { return name + '.' + ext; };
      $rootScope.getExportMenuItemsXLSX = function() { return []; };
      $rootScope.exporterPdfCustomFormatter = function(doc) { return doc; };
      $rootScope.updateAuth = angular.noop;

      window.localStorage.clear();
      // Kullanici filtre satirini acmis durumda (grid menu > Toggle Filtering).
      // Hata yalnizca bu durumda ortaya cikar: filtre satiri kapaliyken select
      // template'i DOM'a hic eklenmez, ng-init calismaz.
      localStorageService.set('enableFiltering_campaign', true);
    }));

    afterEach(function() {
      unmount();
      if (!hadJquery) {
        delete window.$;
      }
    });

    function mount() {
      var scope = $rootScope.$new();
      var vm = $controller('campaignCtrl', { $scope : scope });
      scope.campaign = vm;

      var element = angular.element('<div ui-grid="campaign.campaignGridOptions" ui-grid-save-state'
          + ' ui-grid-resize-columns ui-grid-selection ui-grid-auto-resize class="grid"'
          + ' style="width: 900px; height: 400px;"></div>');
      angular.element(document.body).append(element);
      $compile(element)(scope);

      mounted = { scope : scope, vm : vm, element : element };
      settle();
      return mounted;
    }

    function unmount() {
      if (!mounted) {
        return;
      }
      mounted.scope.$destroy();
      mounted.element.remove();
      mounted = null;
    }

    // ui-grid ve controller birden fazla $timeout zinciri kurar (restore 10ms,
    // veri 150ms, kolon gorunurlugu 50ms, grid refresh debounce). Hepsi bosalana
    // kadar donuyoruz; bosaltilacak is kalmadiginda $timeout.flush() hata atar.
    function settle() {
      $rootScope.$digest();
      for (var i = 0; i < 6; i++) {
        try {
          $timeout.flush();
        } catch (noPendingTask) {
          break;
        }
        $rootScope.$digest();
      }
    }

    function grid() {
      return mounted.vm.gridApi.grid;
    }

    function termOf(columnName) {
      var column = grid().getColumn(columnName);
      return column && column.filters[0] ? column.filters[0].term : 'MISSING';
    }

    function visibleOf(columnName) {
      var column = grid().getColumn(columnName);
      return column ? column.visible : 'MISSING';
    }

    function visibleRowCount() {
      return grid().renderContainers.body.visibleRowCache.length;
    }

    function applyUserPreferences() {
      grid().getColumn('name').filters[0].term = 'guvencell';
      grid().api.core.raise.filterChanged();
      grid().queueGridRefresh();
      settle();
      uiGridGridMenuService.toggleColumnVisibility(grid().getColumn('id'));
      settle();
    }

    it('kullanicinin filtresi ve kolon tercihi ilk ekranda uygulanir', function() {
      mount();
      applyUserPreferences();

      expect(termOf('name')).toBe('guvencell');
      expect(visibleOf('id')).toBe(true);
      expect(visibleRowCount()).toBe(2);
    });

    it('baska ekrana gidip donunce filtre korunur', function() {
      mount();
      applyUserPreferences();
      unmount();

      mount();

      expect(termOf('name')).toBe('guvencell');
      expect(visibleRowCount()).toBe(2);
    });

    it('baska ekrana gidip donunce kolon gorunurlugu korunur', function() {
      mount();
      applyUserPreferences();
      unmount();

      mount();

      expect(visibleOf('id')).toBe(true);
    });

    it('art arda sayfa degisimlerinde tercihler asinmaz', function() {
      mount();
      applyUserPreferences();

      for (var i = 0; i < 3; i++) {
        unmount();
        mount();
      }

      expect(termOf('name')).toBe('guvencell');
      expect(visibleOf('id')).toBe(true);
    });

    it('select filtresi sayfa degisiminde korunur', function() {
      mount();
      grid().getColumn('state').filters[0].term = '0';
      grid().api.core.raise.filterChanged();
      grid().queueGridRefresh();
      settle();
      unmount();

      mount();

      expect(termOf('state')).toBe('0');
    });

    it('filtre satiri kapatilip acilinca ng-init mevcut terimi ezmez', function() {
      mount();
      applyUserPreferences();

      mounted.vm.toggleFiltering();
      settle();
      mounted.vm.toggleFiltering();
      settle();

      expect(termOf('name')).toBe('guvencell');
      expect(visibleOf('id')).toBe(true);
    });

    it('Reset Table varsayilana dondurur ve bu tercih kalicidir', function() {
      mount();
      applyUserPreferences();

      var resetItem = mounted.vm.campaignGridOptions.gridMenuCustomItems.filter(function(item) {
        return item.order === 220 && typeof item.action === 'function';
      })[0];
      resetItem.action();
      settle();

      expect(termOf('name')).toBeUndefined();
      expect(visibleOf('id')).toBe(false);

      unmount();
      mount();

      expect(termOf('name')).toBeUndefined();
      expect(visibleOf('id')).toBe(false);
    });
  });
});
