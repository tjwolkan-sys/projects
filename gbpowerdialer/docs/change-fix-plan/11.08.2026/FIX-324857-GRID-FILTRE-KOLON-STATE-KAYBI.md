# FIX-324857: Grid Filtre ve Kolon Görünürlük State'inin Sayfa Değişiminde Sıfırlanması

- İş Etiketi: fix
- Öncelik: normal
- Task: 324857
- Branch: `fix/324857-grid-filtre-kolon-state-kaybi` (lokal `develop` c74c6b9a üzerinden açıldı; VPN dışı olunduğu için `origin/develop` fetch edilemedi, PR öncesi `gbpd-sync-develop-branch` ile sync gerekli)
- Tarih: 11.08.2026
- Durum: **Onaylandı, uygulanıyor.** Açık sorular 11.08.2026'da cevaplandı, plan cevaplara göre revize edildi; geliştirici planı onayladı.

## Bağlam

Kullanıcı bildirimi (iki ayrı belirti, tek kök neden):

1. Campaign list ana ekranında kampanya adına `guvencell` filtresi uygulanıyor. LSC servisi ekranına girip geri dönüldüğünde **filtre tamamen sıfırlanıyor.**
2. Grid menüsünden (üç çizgi) `ID` kolonu görünür yapılıyor. Dialer içinde başka bir sayfaya geçilip dönüldüğünde **ID kolonu tekrar gizleniyor.**

Her iki tercih de aynı `localStorage` kaydında (`pd.campaignsGrid`) tutulduğu için birlikte kaybolur.

Uygulamada grid state persistence'ı `gridStateService` üzerinden çalışır ve **10 ekranda** aynı kalıpla kullanılır: campaign list, LSC, DNC, action log, action log cache, admin logs, server, location, service statuses, telephony RC.

## İş Etiketi

fix

## Öncelik

normal

## Etkilenen Alanlar

Üretim kodu:

- `gbpowerdialer/src/main/webapp/js/services/gridStateService.js` (asıl düzeltme — owner boundary)
- Çağrı sitesi güncellemesi (10 AngularJS controller, toplam 60 çağrı satırı):
  - `js/controllers/campaignCtrl.js`
  - `js/controllers/lscCtrl.js`
  - `js/controllers/dncCtrl.js`
  - `js/controllers/actionLogCtrl.js`
  - `js/controllers/actionLogCacheCtrl.js`
  - `js/controllers/adminLogsCtrl.js`
  - `js/controllers/serverCtrl.js`
  - `js/controllers/locationCtrl.js`
  - `js/controllers/serviceStatusesCtrl.js`
  - `js/controllers/telephonyRcCtrl.js`
- Select filtre template'i düzeltmesi (Açık Soru 1 kapsamı — 12 controller, 29 `ng-init`): yukarıdaki 6 controller'a ek olarak `attachedDataColumnCtrl.js`, `campaignGroupCtrl.js`, `campaignScheduleAutomationCtrl.js`, `dncPhoneCtrl.js`, `filterColumnCtrl.js`, `phoneNumberColumnCtrl.js`

Test:

- Yeni Java contract test: `gbpowerdialer/src/test/java/tests/angular/grid/GridStatePersistenceContractTest.java`
- Yeni Karma spec: `gbpowerdialer/src/test/angular/grids/gridStatePersistence.spec.js`
- Test altyapısı: `gbpowerdialer/karma.conf.js` (`angular-local-storage.min.js`, `gridStateService.js` ve ilgili controller'lar `files` listesine eklenecek)

Etkilenmeyenler (bilinçli): backend DTO/entity/SQL, Redis channel'ları, AMI event akışı, campaign lifecycle, WRO bundle sırası, localization anahtarları, HTML/JSP template'leri, `columnDefs` tanımları.

## Mevcut Davranış (fix öncesi)

`enableFiltering` **açıkken** (grid menüsü > filtre satırı açık — kullanıcının durumu) campaign list her açıldığında şu sıra yaşanır:

1. `onRegisterApi` çalışır (`campaignCtrl.js:563`). İçinde `sortChanged`, `filterChanged`, `columnSizeChanged`, `columnVisibilityChanged` handler'ları kaydedilir; her biri koşulsuz `gridStateService.save(...)` çağırır. Restore ise `$timeout(..., 10)` içine ertelenir (`campaignCtrl.js:610-613`), çünkü `onRegisterApi` anında (`Grid.renderingComplete`) kolonlar henüz kurulmamıştır.
2. İlk digest'te filtre satırı derlenir. `Type` ve `State` kolonlarının `filterHeaderTemplate`'i `ng-init="colFilter.term = ''"` içerir (`campaignCtrl.js:141` ve `:122`). Bu, `col.filters[0].term` değerini `undefined → ''` yapar.
3. Değişiklik ui-grid'in `uiGridHeaderCell` içindeki `col.filters[i].term` watch'ını tetikler ve `grid.api.core.raise.filterChanged()` yayınlanır.
4. Controller'ın `filterChanged` handler'ı (`campaignCtrl.js:591-593`) `gridStateService.save('campaignsGrid', vm.gridApi.saveState.save())` çağırır. **O anda grid henüz varsayılan durumdadır**; kullanıcının `localStorage`'daki kaydı varsayılan state ile ezilir.
5. `$timeout(..., 10)` çalışır: `gridStateService.restore('campaignsGrid', ...)` `localStorage`'ı okur ve **4. adımda kendi yok ettiği** varsayılan state'i geri yükler.

`enableFiltering` kapalıyken 2. adım hiç çalışmaz (`updateFilters(false)` filtre template'ini DOM'a eklemez), state korunur. Belirtinin "bazen oluyor" görünmesinin sebebi budur.

İkincil (aynı kök): ui-grid'in `uiGridSaveStateService.restoreColumns` fonksiyonu, kolonları gezerken **restore'un ortasında** `filterChanged` yayınlar. Bu da yarı restore edilmiş bir state'in kalıcı yazılmasına açık kapı bırakır.

## Beklenen Davranış

1. Campaign list'te uygulanan kolon filtresi (örn. `Name = guvencell`) başka bir ekrana gidip dönüldüğünde korunur.
2. Grid menüsünden görünür yapılan `ID` kolonu (ve tüm kolon görünürlük/genişlik/sıralama tercihleri) sayfa değişiminde korunur.
3. Aynı davranış `gridStateService` kullanan 10 grid'in tamamı için geçerlidir.
4. `Reset Table` menü öğesi eskisi gibi çalışır: varsayılan state'e döner ve bu tercih kalıcıdır.
5. İlk ziyarette (kayıt yokken) varsayılan state `defaultXxxGrid` anahtarına eskisi gibi yazılır.

## Root Cause / Değişiklik Gerekçesi

**Kök neden:** Grid state persistence'ının sıralama sözleşmesi hiçbir yerde sahiplenilmemiş. Save handler'ları grid kurulumunun en başında (`onRegisterApi`) canlıya alınıyor, restore ise sonraki bir tick'e erteleniyor. Bu iki an arasında ui-grid'in kendi kurulum event'leri (burada select filtre template'indeki `ng-init`) save handler'larını tetikliyor ve **kullanıcının kaydını varsayılanla eziyor.**

Bu bir "ng-init hatası" değil: `ng-init` yalnızca tetikleyici. Aynı pencerede yayınlanacak herhangi bir `sortChanged` / `columnVisibilityChanged` / `columnSizeChanged` event'i de aynı kaybı üretir. Bu yüzden düzeltme tetikleyicide değil, **persistence'ın sahibi olan `gridStateService` içinde** yapılır.

**Çözüm (owner boundary'de kalıcı):** `gridStateService`, bir grid instance'ı için restore tamamlanmadan **hiçbir otomatik save'i kalıcı yazmaz.** İşaret, grid nesnesinin (`gridApi.grid`) üzerinde tutulur; her controller instance'ı yeni bir grid nesnesi aldığı için route değişiminde kendiliğinden sıfırlanır. Servis singleton'ına yeni global mutable map eklenmez.

Sözleşmenin unutulamaz olması için `save` artık serileştirilmiş state değil, `gridApi` alır; serileştirmeyi servis kendisi yapar. Böylece controller'ın gate'i atlaması mümkün değildir ve ileride eklenecek yeni grid'ler otomatik korunur.

Hedeflenen servis (uygulama sırasında birebir bu şekilde yazılacak):

```js
app.factory('gridStateService', [ 'localStorageService', function(localStorageService) {

  // ui-grid, grid kurulurken kullanici mudahalesi olmadan filterChanged /
  // columnVisibilityChanged gibi event'ler yayinlar (ornek: select filtre
  // template'indeki ng-init colFilter.term = ''). Bu event'ler controller'lardaki
  // save handler'larini tetikler. Restore bir sonraki tick'e ertelendigi icin,
  // gate olmadan kullanicinin kayitli state'i varsayilan state ile ezilir.
  // Isaret grid nesnesinde tutulur: her controller instance'i yeni grid nesnesi
  // aldigi icin route degisiminde kendiliginden sifirlanir.
  var RESTORED = 'gbpdGridStateRestored';

  function gridOf(gridApi) {
    return gridApi && gridApi.grid ? gridApi.grid : null;
  }

  return {
    restore : function(stateName, gridApi, controllerScope) {
      var gridState = localStorageService.get(stateName);
      if (gridState) {
        gridApi.saveState.restore(controllerScope, gridState);
      }
      var grid = gridOf(gridApi);
      if (grid) {
        grid[RESTORED] = true;
      }
    },

    reset : function(defaultStateName, regularStateName, gridApi, controllerScope) {
      localStorageService.set(regularStateName, localStorageService.get(defaultStateName));
      this.restore(regularStateName, gridApi, controllerScope);
    },

    save : function(stateName, gridApi) {
      var grid = gridOf(gridApi);
      if (!grid || !grid[RESTORED]) {
        return;
      }
      localStorageService.set(stateName, gridApi.saveState.save());
    },

    saveDefaultStateIfNotExist : function(defaultStateName, gridApi) {
      if (!localStorageService.get(defaultStateName)) {
        localStorageService.set(defaultStateName, gridApi.saveState.save());
      }
    }
  };
} ]);
```

Controller tarafındaki tek değişiklik çağrı şeklidir:

```js
// once
gridStateService.save(vm.regularGridStateName, vm.gridApi.saveState.save());
gridStateService.saveDefaultStateIfNotExist(vm.defaultGridStateName, vm.gridApi.saveState.save());

// sonra
gridStateService.save(vm.regularGridStateName, vm.gridApi);
gridStateService.saveDefaultStateIfNotExist(vm.defaultGridStateName, vm.gridApi);
```

`restore` ve `reset` çağrıları hiç değişmez.

Bu, `gbpd-no-workaround-root-cause-engineering` açısından workaround değildir: `ng-init`'i kaldırmak, restore'u geciktirmek veya save'i debounce etmek gibi tetikleyiciye özel yamalar yerine, sıralama sözleşmesi sahibinde tanımlanır ve tüm event kaynaklarını birden kapatır.

## Kod Okuma Bulguları

### ui-grid 3.2.6 `uiGridHeaderCell` filtre watch'ı (minified kaynaktan birebir)

```js
var g = a.filterable;
k.grid.options.enableFiltering && a.col.enableFiltering ? a.filterable = !0 : a.filterable = !1,
g !== a.filterable && (
  "undefined" != typeof a.col.updateFilters && a.col.updateFilters(a.filterable),
  a.filterable ? (
    a.col.filters.forEach(function(b, c) {
      q.push(a.$watch("col.filters[" + c + "].term", function(a, b) {
        a !== b && (k.grid.api.core.raise.filterChanged(), ...)
      }))
    }), ...
```

`updateFilters` (`uiGridFilter` direktifi) filtre template'ini derleyip DOM'a ekler — `ng-init` tam burada çalışır:

```js
b.col.updateFilters = function(d) { if (c.children().remove(), d) { var e = b.col.filterHeaderTemplate; c.append(a(e)(b)) } }
```

`enableFiltering` kapalıyken `updateFilters(false)` template'i eklemez, watch'lar da kurulmaz.

### `restoreColumns` restore ortasında event yayınlıyor

```js
a.options.saveFilter && !angular.equals(e.filters, b.filters) && (
  b.filters.forEach(function(a, b) { angular.extend(e.filters[b], a), ... }),
  a.api.core.raise.filterChanged()
)
```

Kolon döngüsünün içinde yayınlandığı için, henüz restore edilmemiş kolonlar varsayılan değerleriyle kaydedilebilir. Gate bu yolu da kapatır: bayrak `restore` **bittikten sonra** set edilir.

### `onRegisterApi` zamanlaması

`Grid.prototype.renderingComplete = function(){ angular.isFunction(this.options.onRegisterApi) && this.options.onRegisterApi(this.api), ... }` — `uiGrid` direktifinin post-link'inde çağrılır, yani ilk digest'ten (kolon kurulumundan) **önce**. Restore'un `$timeout` içine alınmasının sebebi budur; `$timeout`'u kaldırmak çözüm değildir.

## Kanıt: Headless-Chrome Davranış Harness'ı

Gerçek `campaignCtrl` + gerçek `ui-grid.min.js` (3.2.6) + gerçek `angular-local-storage` ile route değişimi (`$scope.$destroy` + yeni controller instance) birebir replay edildi. Veri, üretimdeki gibi restore'dan sonra (async) gelecek şekilde geciktirildi. `enableFiltering` açık.

Fix öncesi:

```
=== 2) kullanici name=guvencell filtreler ve id kolonunu acar ===
after user actions | id.visible=true  name.term="guvencell" | visibleRows=2
=== 3) baska ekrana gidip donus ===
  >> gridStateService.save  terms[type="" state=""]     <- kullanici kaydini ezen save
  SET campaignsGrid  cols=28 id.visible=false name.term=undefined
  >> gridStateService.save  terms[type="" state=""]
  >> gridStateService.restore reads name.term=undefined  <- az once yok edilen deger okunuyor
after second mount | id.visible=false name.term=undefined | visibleRows=3
```

Save'i tetikleyen tek terim değişikliği `type=""` ve `state=""`, yani `ng-init` çalışan iki görünür select kolonu. `enabled` kolonu varsayılan olarak gizli olduğu için header cell'i render edilmez ve üçüncü bir tetikleme oluşmaz.

Aynı harness'ta gate simüle edildiğinde:

```
=== 3) baska ekrana gidip donus ===
  >> gridStateService.save BLOKLANDI (restore henuz calismadi)
  >> gridStateService.save BLOKLANDI (restore henuz calismadi)
  >> gridStateService.restore reads name.term="guvencell"
after second mount | id.visible=true name.term="guvencell" | visibleRows=2
after third mount  | id.visible=true name.term="guvencell" | visibleRows=2
```

Harness geçici teşhis aracıdır, repo'ya eklenmez; kalıcı testler `Uygulama Kontrol Listesi` altındadır.

## Riskler

- **Gate yüzünden meşru bir save kaybı.** Gate yalnız `restore` çalışmadan önceki pencereyi kapatır. Bu pencerede kullanıcı etkileşimi mümkün değildir (grid henüz render edilip veriyi beklemektedir). `restore` her `onRegisterApi` akışında koşulsuz çağrıldığı için bayrak her mount'ta açılır. Karma spec'i bu iki durumu (öncesi bloklanır / sonrası yazılır) ayrı ayrı kilitleyecek.
- **`restore` hiç çağrılmayan bir grid.** 10 controller'ın tamamında `onRegisterApi` içinde `restore` var (`adminLogsCtrl` iki ayrı yerde çağırıyor). Java contract testi bu değişmezi kilitleyecek; `restore` çağırmayan bir grid state'ini hiç kaydedemez hâle gelir.
- **`reset` davranışı.** `reset` artık `save`'i değil doğrudan `localStorageService.set`'i kullanır (elindeki state zaten serileştirilmiş default state'tir) ve ardından `restore` çağırır. Kullanıcı akışı değişmez.
- **`saveDefaultStateIfNotExist` imza değişimi.** Yalnız `defaultXxxGrid` anahtarına yazar, gate'e tabi değildir; `!exists` koşulu korunur. Davranış aynıdır.
- **Toggle filtering senaryosu (kapsam kararı bekliyor).** Kullanıcı filtre satırını kapatıp tekrar açarsa `ng-init` yeniden çalışır; gate o anda açık olduğu için select kolonlarının (`Type`, `State`) terimleri `''` olarak kaydedilir. Serbest metin filtreleri (`Name` gibi) etkilenmez. Bkz. Açık Soru 1.
- **Dil değişiminde `selectOptions` (mevcut, bu fix dışı).** `saveState.save()` select filtrelerinin `selectOptions` dizisini de kaydeder; restore eski dildeki etiketleri geri yazabilir. Mevcut davranıştır, bu planda değişmez.
- **Genel.** Redis channel, AMI event, result code eşlemesi, campaign state transition, DTO/DB şeması ve WRO bundle sırası değişmez. Yeni kütüphane eklenmez. Yeni global/static mutable state eklenmez.

## Performans Etkisi

- **Hot path durumu:** Hot path **değil**. Değişiklik yalnız AngularJS admin ekranlarındaki grid state persistence'ını etkiler. AMI/AGI originate, hangup/result handling, Redis publish/subscribe, ACD seçimi ve campaign producer/consumer/originator loop'larına temas yoktur.
- **Call/event/tick başına ek maliyet:** Yok (0).
- **IO/scan/lock/serialization/log etkisi:** Pozitif. Grid kurulumu sırasında tetiklenen her event, bugün `saveState.save()` (tüm kolonların serileştirilmesi) + `angular.toJson` + `localStorage.setItem` maliyeti ödüyor. Harness'ta tek mount başına ~48 gereksiz yazma sayıldı; gate bunların kurulum penceresine düşenlerini tamamen ortadan kaldırır.
- **Ölçüm notu:** Serileştirme artık gate geçildikten sonra yapıldığı için, bloklanan çağrılarda `saveState.save()` hiç çalıştırılmaz.

## TDD Stratejisi

Kırmızı → yeşil sırası korunacak. İki katman:

1. **Davranışsal katman (Karma/Jasmine, `gridStatePersistence.spec.js`):** Gerçek `campaignCtrl` + gerçek ui-grid ile mount → filtre/kolon değişikliği → `$destroy` → yeniden mount akışı replay edilir; ikinci mount'ta filtre teriminin ve kolon görünürlüğünün korunduğu doğrulanır. Ayrıca `gridStateService` doğrudan test edilir: restore öncesi `save` yazmaz, restore sonrası yazar, `reset` varsayılana döner.
2. **Contract katmanı (Java/JUnit, offline koşar, `GridStatePersistenceContractTest`):** `gridStateService.js` içinde gate'in varlığını; 10 controller'ın tamamının yeni imzayı kullandığını (`saveState.save()` argümanının kalmadığını); her controller'ın `onRegisterApi` akışında `restore` çağırdığını kilitler.

`npm.cmd run test:angular` VPN/Nexus blocker'ı sürüyorsa (bkz. FIX-324856 kaydı) Karma yerine aynı senaryo headless-Chrome harness'ı ile koşulur ve bu plana sonuç yazılır.

## Açık Sorular

Tümü 11.08.2026'da kullanıcı tarafından cevaplandı ve plan bu cevaplara göre revize edildi.

- [x] **1. Toggle filtering senaryosu kapsama alınsın mı? — CEVAPLANDI: evet.** Filtre satırı kapatılıp tekrar açıldığında `ng-init="colFilter.term = ''"` select kolonlarının terimini sıfırlar; gate o anda açık olduğu için bu kayıp kalıcı yazılır. Kapsama alındı: `ng-init` yalnız değer atanmamışken (`undefined`/`null`) varsayılan `''` atayacak şekilde düzeltilir. İfade `colFilter.term = colFilter.term == null ? '' : colFilter.term` olur; gevşek `== null` hem `undefined` hem `null` yakalar, `0` / `false` / `''` gibi geçerli seçim değerlerine dokunmaz (`enabled` filtresi `true`/`false` değerleri kullandığı için bu önemlidir). 12 controller / 29 template.
- [x] **2. `npm install` yeniden denensin mi? — CEVAPLANDI: hayır.** Doğrulama headless-Chrome harness'ı ile yapılacak; Karma spec'i yazılacak ama `npm.cmd run test:angular` blocker olarak açık kalacak.
- [x] **3. Release backport gerekli mi? — CEVAPLANDI: hayır.** Hedef yalnız `develop`.

## Execution Plan

### Görev 0: Ortam hazırlığı

- [x] **0.1** `npm install` **denenmedi** — Açık Soru 2'nin cevabı "hayır". Doğrulama headless-Chrome harness'ı ile yapıldı. `gbpowerdialer/node_modules` altında `karma` binary'si yok; `package.json` değiştirilmedi.
- [x] **0.2** Baseline contract testleri koşuldu.

### Görev 1: Karma davranış spec'i

- [x] **1.1** `karma.conf.js` `files` listesine `underscore-min.js`, `angular-local-storage.min.js`, `ui-grid.min.js`, `js/services/gridStateService.js` ve `campaignCtrl.js` eklendi.
- [x] **1.2** `src/test/angular/grids/gridStatePersistence.spec.js` yazıldı (17 test):
  - `gridStateService` birim testleri: restore öncesi `save` yazmaz; restore sonrası yazar; bayrak ui-grid restore'u bitmeden açılmaz; bayrak grid instance'ına bağlıdır; `reset` varsayılana döner; `saveDefaultStateIfNotExist` yalnız kayıt yokken yazar ve gate'e tabi değildir.
  - Davranış testleri: gerçek `campaignCtrl` + gerçek ui-grid ile mount → `name` filtresi + `id` görünürlüğü → `$destroy` → yeniden mount; ayrıca art arda 3 sayfa değişimi, select filtresi, toggle filtering ve `Reset Table`.
  - Plan sapması: `campaignCtrl` realtime/poll akışında jQuery kullandığı için spec içinde `window.$` yoksa `$.ajax` stub'ı kuruluyor ve `afterEach` içinde kaldırılıyor.
- [ ] **1.3** `npm.cmd run test:angular` — **koşulamadı** (Görev 0.1). Geliştirici VPN'de `npm install` sonrası koşacak.

### Görev 2: Java contract testi (kırmızı)

- [x] **2.1** `src/test/java/tests/angular/grid/GridStatePersistenceContractTest.java` yazıldı (11 test).
- [x] **2.2** Kırmızı görüldü: `Tests run: 11, Failures: 9`. Yeşil olan iki test (`everyStateGridControllerRestoresItsStateSoTheGateCanOpen` ve `guardedNgInitStillDefaults...`) zaten var olan değişmezleri kilitliyor.
  - Plan sapması: bayrak `gridApi.grid.gbpdGridStateRestored` düz property olarak yazıldığı için test, bracket notation yerine ` = true` ekini arayacak şekilde düzeltildi.

### Görev 3: Üretim kodu değişikliği

- [x] **3.1** `gridStateService.js` yeniden yazıldı (planın hedef kodu, gerekçe yorumuyla).
- [x] **3.2** 10 controller'da 40 `save` ve 10 `saveDefaultStateIfNotExist` çağrısı yeni imzaya geçirildi. `restore`/`reset` çağrıları değişmedi. Değişiklik ASCII arama/değiştirme ile, dosyalar latin1 (bayt-güvenli) round-trip edilerek uygulandı; Türkçe karakter taşıyan satırlar bit bazında korundu (`git diff --numstat`: 117 eklendi / 84 silindi, hepsi beklenen satırlar).
- [x] **3.3** 12 controller'daki 29 `ng-init` korumalı hâle getirildi: `colFilter.term = colFilter.term == null ? '' : colFilter.term`.
- [x] **3.4** Java contract testi yeşile döndü: `Tests run: 11, Failures: 0, Errors: 0`.
- [x] **3.5** Headless-Chrome harness'ı yeşile döndü: `pass=23 fail=0` (fix öncesi aynı harness `pass=12 fail=11`).

### Görev 4: Regresyon ve kapanış

- [x] **4.1** Tüm contract testleri koşuldu: `Tests run: 48, Failures: 1`. Tek failure `tests.util.LogbackAsyncAppenderConfigContractTest.rootLoggerWritesThroughAsyncAppendersOnly` ve **bu fix'le ilgisi yok**; temiz ağaçta (0 değişiklik, `git stash -u`) birebir aynı şekilde düşüyor. `develop` üzerinde mevcut bir durum, dokunulmadı.
- [x] **4.2** `mvn -o -DskipTests package` → **BUILD SUCCESS** (01:22, WRO bundle dâhil).
- [x] **4.3** Manuel doğrulama — **geliştirici tarafından lokal debug ortamında yapıldı ve onaylandı** (11.08.2026). Campaign list'te filtre ve `ID` kolonu sayfa değişiminde korunuyor.
- [x] **4.4** History kaydı `docs/history/11.08.2026.md` dosyasına eklendi; plan adımları işaretlendi.
- [ ] **4.5** Commit `gbpd-commit-discipline` ile hazırlanacak (kullanıcı onayı sonrası); PR öncesi VPN'de `origin/develop` sync (`gbpd-sync-develop-branch`). Hedef dal: `develop`.

## Hedefli Doğrulama Komutları

Komutlardan önce `.codex/TOOL-PATHS.local.properties` okunur; `MAVEN_CMD`, `MAVEN_SETTINGS`, `MAVEN_REPO_LOCAL`, `JAVA_HOME`, `NPM_CMD` oradan alınır. Maven `-o` (offline) koşulur.

| Komut | Amaç |
|---|---|
| `mvn -o -Dtest=GridStatePersistenceContractTest test` | Yeni contract testi (önce kırmızı, sonra yeşil) |
| `mvn -o -Dtest=GridNumericColumnTypeContractTest test` | 324856 regresyonu |
| `mvn -o -Dtest=*ContractTest test` | Tüm angular contract katmanı |
| `mvn -o -DskipTests package` | WRO bundle dâhil WAR derlemesi |
| `npm.cmd run test:angular` | Karma spec'i (blocker durumuna göre) |
| headless-Chrome harness | Fix öncesi/sonrası davranış kanıtı |

## Doğrulama Sonuçları

| Komut | Sonuç |
|---|---|
| `mvn -o -Dtest=GridStatePersistenceContractTest test` (fix öncesi) | **KIRMIZI** — Tests run: 11, Failures: 9 |
| `mvn -o -Dtest=GridStatePersistenceContractTest test` (fix sonrası) | **YEŞİL** — Tests run: 11, Failures: 0, Errors: 0 |
| `mvn -o -Dtest=*ContractTest test` | Tests run: 48, Failures: 1 — tek failure `LogbackAsyncAppenderConfigContractTest`, **pre-existing** (temiz ağaçta da düşüyor), bu fix'le ilgisi yok |
| `mvn -o -DskipTests package` | **BUILD SUCCESS** (01:22, WRO bundle dâhil) |
| headless-Chrome davranış harness'ı (fix öncesi) | **KIRMIZI** — pass=12 fail=11 |
| headless-Chrome davranış harness'ı (fix sonrası) | **YEŞİL** — pass=23 fail=0 |
| `node --check` (19 değişen/yeni JS dosyası) | Hepsi başarılı |
| `npm.cmd run test:angular` | **KOŞULMADI** — Açık Soru 2 kararı; `node_modules` içinde `karma` binary'si yok |

### Davranış doğrulaması (Karma yerine geçici yöntem)

`node_modules` eksik olduğu için Karma koşulamadı. Aynı doğrulamayı bağımlılık indirmeden yapmak üzere scratchpad'de (repo dışında) bir HTML harness kuruldu: `angular.min.js` + `underscore-min.js` + `angular-local-storage.min.js` + `ui-grid.min.js` + `gridStateService.js` + `constants.js` + gerçek `campaignCtrl.js` yüklenir; `angular.injector(['ng','app'])` ile injector kurulur, gerçek `ui-grid` direktifi compile edilir ve route değişimi `$scope.$destroy` + yeni controller instance ile replay edilir. Veri, üretimdeki gibi restore'dan **sonra** (async, 150 ms) gelir ve `enableFiltering` açıktır.

Fix öncesi (aynı harness, üretim kodu `git stash` ile geri alınmış hâlde):

```
PASS  2.3 localStorage filtreyi tuttu  actual="guvencell"
FAIL  3.1 filtre korundu               actual=undefined  expected="guvencell"
FAIL  3.2 id kolonu korundu            actual=false      expected=true
FAIL  3.3 filtre gercekten uygulaniyor actual=3          expected=2
FAIL  5.2 select filtre korundu        actual=""         expected="0"
FAIL  6.1 toggle sonrasi metin filtresi korundu  actual=undefined
SUMMARY pass=12 fail=11
```

Fix sonrası:

```
SUMMARY pass=23 fail=0
```

Kapsanan senaryolar: ilk mount, kullanıcı filtresi + kolon açma, iki ardışık sayfa değişimi, select filtresi, filtre satırı kapat/aç (toggle filtering), `Reset Table` ve reset sonrası sayfa değişimi.

Harness geçici bir teşhis aracıdır, repo'ya eklenmemiştir; kalıcı test `src/test/angular/grids/gridStatePersistence.spec.js` dosyasıdır.

### Blocker: `npm.cmd run test:angular`

`gbpowerdialer/node_modules` altında `karma` binary'si yok ve Açık Soru 2 kararıyla `npm install` denenmedi (internal Nexus'a VPN dışından erişilemiyor). **Geliştirici aksiyonu:** VPN'e bağlanıp `npm install` ve ardından `npm.cmd run test:angular` çalıştırılmalı; yeni spec o koşuda doğrulanacak.

## Kapanış Kriteri

- [x] Açık sorular cevaplandı ve plana işlendi.
- [x] `GridStatePersistenceContractTest` offline Maven ile yeşil (11/11); fix öncesi kırmızı görüldü (9 failure).
- [x] Karma spec'i yazıldı; koşumu blocker olarak açık yazıldı, yerine headless-Chrome davranış doğrulaması koşuldu (23/0).
- [x] Campaign list'te filtre ve kolon görünürlüğü sayfa değişiminde korunuyor; `Reset Table` çalışıyor (harness 3.x, 4.x, 7.x, 8.x).
- [x] `gridStateService` kullanan 10 grid'in tamamı yeni imzaya geçti; eski imzalı çağrı kalmadı.
- [x] WAR paketleme başarılı.
- [x] History kaydı eklendi; plan adımları işaretlendi.
- [x] Manuel UI doğrulaması (Görev 4.3) geliştirici tarafından lokal debug ortamında yapıldı.
- [x] Final yanıtta kısa Test Özeti sunuldu.

## Tamamlanma Notu

Uygulama tamamlandı; kod ve offline testler yeşil, geliştirici lokal debug ortamında davranışı doğruladı ve commit onayı verdi. Plan tek noktada açık kalıyor:

1. `npm.cmd run test:angular` geliştirici VPN'e bağlanıp `npm install` çalıştırdıktan sonra koşulmalı (yeni Karma spec'i o koşuda doğrulanacak).

PR öncesi `gbpd-sync-develop-branch` ile `origin/develop` sync gerekli. Hedef dal: `develop`.
