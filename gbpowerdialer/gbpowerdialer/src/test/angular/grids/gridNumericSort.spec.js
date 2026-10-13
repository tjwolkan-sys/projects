/* global describe, beforeEach, inject, it, expect, module, angular */

/**
 * Task 324856: Grid'lerde sayisal kolonlar alfabetik siraliyordu.
 * Bildirilen semptom Campaign Form > List Source > Phone Numbers > Sort
 * kolonuydu: asc 0,1,10,11,12 / desc 9,8,7,6,5.
 *
 * Kok neden ui-grid 3.2.6'nin tip tahmini:
 *   1. GridOptions.initialize controller'in gridOptions nesnesini yerinde
 *      mutate edip data = [] yapar.
 *   2. Ilk data watch turu [] (truthy) ile Grid.modifyRows([]) calistirir.
 *   3. modifyRows sonundaki kosulsuz assignTypes(), grid.rows bos oldugu icin
 *      tip bildirmeyen her columnDef'e type = "string" damgalar.
 *   4. if (!colDef.type) guard'i yuzunden gercek veri (HTTP cevabi) geldiginde
 *      tip bir daha hesaplanmaz.
 *   5. rowSorter.getSortFn -> guessSortFn("string") -> sortAlpha; sonuc ayrica
 *      uygulama-global rowSorter.colSortFnCache[colDef.name]'e yazilir.
 *
 * Bu spec uretimdeki sirayi (bos ilk tur -> gercek veri) birebir replay eder;
 * columnDef'ler gercek controller'lardan okunur, sentetik degildir.
 */
describe('Grid sayisal kolon siralamasi', function() {

  // Kapsamdaki grid'ler: controller adi, gridOptions ozelligi, sayisal kolonlar.
  var GRIDS = [
    { ctrl: 'phoneNumberColumnCtrl',          options: 'phoneNumberColumnGridOptions',          numeric: [ 'id', 'colSort' ] },
    { ctrl: 'campaignGroupCtrl',              options: 'campaignGroupGridOptions',              numeric: [ 'id', 'priority' ] },
    { ctrl: 'resultCodeScheduleCtrl',         options: 'resultCodeScheduleGridOptions',         numeric: [ 'id', 'retryAmount', 'retryInterval' ] },
    { ctrl: 'resultCodeScheduleAdvancedCtrl', options: 'resultCodeScheduleAdvancedGridOptions', numeric: [ 'id', 'dialAttempt', 'retryInterval', 'numberToTry' ] },
    { ctrl: 'filterColumnCtrl',               options: 'filterColumnGridOptions',               numeric: [ 'id', 'ratio' ] },
    { ctrl: 'attachedDataColumnCtrl',         options: 'attachedDataColumnGridOptions',         numeric: [ 'id' ] },
    { ctrl: 'campaignScheduleAutomationCtrl', options: 'scheduleAutomationGridOptions',         numeric: [ 'id' ] },
    { ctrl: 'adminLogsDetailsCtrl',           options: 'adminLogsDetailsGridOptions',           numeric: [ 'id' ] }
  ];

  // Tek haneli ve cok haneli degerler karisik: alfabetik siralama 10/11/12'yi
  // 2 ve 9'un onune atar, sayisal siralama atmaz.
  var VALUES = [ 0, 1, 2, 9, 10, 11, 12 ];
  var ASC = [ 0, 1, 2, 9, 10, 11, 12 ];
  var DESC = [ 12, 11, 10, 9, 2, 1, 0 ];

  var FACTORIES = [ 'phoneNumberColumnFactory', 'campaignGroupFactory', 'groupFactory',
                    'resultCodeScheduleFactory', 'resultCodeScheduleAdvancedFactory',
                    'telephonyRcFactory', 'resultCodeFactory', 'filterFactory',
                    'attachedDataFactory', 'campaignScheduleAutomationFactory',
                    'adminLogsDetailsFactory', 'dbTableService', 'commonResultStatusService' ];

  var $controller, $rootScope, gridClassFactory, rowSorter, uiGridConstants;

  beforeEach(module('app'));
  beforeEach(module('ui.grid'));

  // Controller'lar uretimde ngRoute, angular-translate, ui-bootstrap ve kendi
  // factory'lerine baglidir; grid tanimini okumak icin hepsi stub'lanir.
  beforeEach(module(function($provide, $filterProvider) {
    var identity = function() {
      return function(key) {
        return key;
      };
    };
    $filterProvider.register('translate', identity);
    $filterProvider.register('i18n', identity);

    $provide.value('$routeParams', {});
    $provide.value('locMsg', {});
    $provide.value('$modal', {
      open: function() {
        return { result: { then: angular.noop } };
      }
    });

    FACTORIES.forEach(function(name) {
      $provide.factory(name, [ '$q', function($q) {
        var promise = function() {
          return $q.when({ data: [], totalItems: 0 });
        };
        return {
          getAll: promise, get: promise, save: promise, remove: promise,
          addDefaultColumnsForPhoneNumber: promise,
          createNew: function(o) {
            return o || {};
          }
        };
      } ]);
    });
  }));

  beforeEach(inject(function(_$controller_, _$rootScope_, _gridClassFactory_, _rowSorter_, _uiGridConstants_) {
    $controller = _$controller_;
    $rootScope = _$rootScope_;
    gridClassFactory = _gridClassFactory_;
    rowSorter = _rowSorter_;
    uiGridConstants = _uiGridConstants_;
  }));

  // columnDefs gercek uretim controller'indan okunur. Scope hemen destroy edilir
  // ki sonraki digest'lerde controller'in kendi watcher'lari calismasin.
  function columnDefsOf(grid) {
    var scope = $rootScope.$new();
    var vm = $controller(grid.ctrl, {
      $scope: scope,
      item: {},
      $modalInstance: { close: angular.noop, dismiss: angular.noop }
    });
    var defs = angular.copy(vm[grid.options].columnDefs);
    scope.$destroy();
    return defs;
  }

  // Uretimdeki sira: once bos dizi (GridOptions.initialize sonrasi ilk data
  // watch turu), sonra HTTP cevabi.
  function gridWithData(columnDefs, rows) {
    var grid = gridClassFactory.createGrid({ columnDefs: columnDefs });
    grid.buildColumns();
    $rootScope.$digest();
    grid.modifyRows([]);
    $rootScope.$digest();
    grid.modifyRows(rows);
    $rootScope.$digest();
    return grid;
  }

  function rowsFor(field) {
    return VALUES.map(function(value, index) {
      var row = { name: 'row-' + index };
      row[field] = value;
      return row;
    });
  }

  function sortedValues(grid, field, direction) {
    grid.columns.forEach(function(col) {
      col.sort = {};
    });
    grid.getColumn(field).sort = { direction: direction, priority: 0 };
    return rowSorter.sort(grid, grid.rows, grid.columns).map(function(row) {
      return row.entity[field];
    });
  }

  GRIDS.forEach(function(gridSpec) {
    describe(gridSpec.ctrl, function() {
      gridSpec.numeric.forEach(function(field) {

        it(field + ' kolonu bos ilk turdan sonra da sayisal siralanir', function() {
          var grid = gridWithData(columnDefsOf(gridSpec), rowsFor(field));

          expect(grid.getColumn(field).colDef.type).toBe('number');
          expect(rowSorter.getSortFn(grid, grid.getColumn(field))).toBe(rowSorter.sortNumber);
          expect(sortedValues(grid, field, uiGridConstants.ASC)).toEqual(ASC);
          expect(sortedValues(grid, field, uiGridConstants.DESC)).toEqual(DESC);
        });

      });
    });
  });

  it('metin kolonu alfabetik siralamada kalir (regresyon korumasi)', function() {
    var grid = gridWithData(columnDefsOf(GRIDS[0]), rowsFor('colSort'));

    expect(grid.getColumn('colNumber').colDef.type).toBe('string');
    expect(rowSorter.getSortFn(grid, grid.getColumn('colNumber'))).toBe(rowSorter.sortAlpha);
  });
});
