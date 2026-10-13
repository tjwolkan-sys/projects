/**
 * Created by DPlatonov on 12/07/2016. /
 */

app.factory('gridStateService', [ 'localStorageService', function(localStorageService) {

  /*
   * Task 324857.
   *
   * ui-grid, grid kurulurken kullanici mudahalesi olmadan filterChanged /
   * columnVisibilityChanged gibi event'ler yayinlar. Ornek: select filtre
   * template'indeki ng-init ifadesi colFilter.term degerini degistirir,
   * uiGridHeaderCell'in term watch'i da filterChanged yayinlar.
   *
   * Controller'lar save handler'larini onRegisterApi icinde, yani grid
   * kurulumunun en basinda kaydeder. restore ise kolonlar kurulana kadar
   * beklemek zorunda oldugu icin sonraki tick'e ($timeout) ertelenir. Bu
   * pencerede yayinlanan her event, gate olmadan, kullanicinin localStorage'daki
   * state'ini varsayilan state ile ezer; ardindan restore kendi yok ettigi
   * degeri okur. Bildirilen belirti buydu: filtre ve kolon gorunurlugu sayfa
   * degisiminde sifirlaniyordu.
   *
   * Bu yuzden bir grid instance'i icin restore tamamlanmadan hicbir otomatik
   * save kalici yazilmaz. Isaret grid nesnesinde tutulur: her controller
   * instance'i yeni bir grid nesnesi aldigi icin route degisiminde
   * kendiliginden sifirlanir ve servis singleton'inda paylasilan mutable
   * state olusmaz.
   */

  return {
    //restore from local storage when grid loaded
    restore : function(stateName, gridApi, controllerScope) {
      var gridState = localStorageService.get(stateName);
      if (gridState) {
        gridApi.saveState.restore(controllerScope, gridState);
      }
      // ui-grid restoreColumns, kolon dongusunun icinde filterChanged yayinlar.
      // Bayrak ancak restore tamamlandiktan sonra acilir ki yarim restore edilmis
      // bir state kalici yazilmasin.
      if (gridApi && gridApi.grid) {
        gridApi.grid.gbpdGridStateRestored = true;
      }
    },
    //reset to default
    reset : function(defaultStateName, regularStateName, gridApi, controllerScope) {
      var defaultState = localStorageService.get(defaultStateName);
      localStorageService.set(regularStateName, defaultState);
      this.restore(regularStateName, gridApi, controllerScope);

    },
    //save to local storage
    save : function(stateName, gridApi) {
      if (!gridApi || !gridApi.grid || !gridApi.grid.gbpdGridStateRestored) {
        return;
      }
      localStorageService.set(stateName, gridApi.saveState.save());
    },

    saveDefaultStateIfNotExist : function(defaultStateName, gridApi) {
      var defaultState = localStorageService.get(defaultStateName);
      if (!defaultState) {
        localStorageService.set(defaultStateName, gridApi.saveState.save());
      }
    }
  }
} ]);
