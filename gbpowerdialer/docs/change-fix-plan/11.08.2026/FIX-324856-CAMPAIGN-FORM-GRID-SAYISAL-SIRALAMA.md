# FIX-324856: Grid'lerde Sayısal Kolonların Alfabetik Sıralanması (Phone Numbers `Sort` + kardeş grid'ler)

- İş Etiketi: fix
- Öncelik: normal
- Task: 324856
- Branch: `fix/324856-phone-numbers-grid-sayisal-siralama` (local `develop` c74c6b9a üzerinden açıldı; VPN dışı olunduğu için `origin/develop` fetch edilemedi, PR öncesi `gbpd-sync-develop-branch` ile sync gerekli)
- Tarih: 11.08.2026
- Durum: **Uygulandı ve doğrulandı.** Java contract testi ve headless-Chrome davranış doğrulaması yeşil; WAR paketleme başarılı. Tek açık kalem: `npm.cmd run test:angular` koşulamadı — `npm install` internal Nexus'a (VPN) erişemedi (bkz. `Doğrulama > Blocker`).

## Bağlam

Campaign Form → **List Source** → **Phone Numbers** → `Phone Number Column List` grid'inde `Sort` kolon başlığına tıklanınca sıralama sayısal değil alfabetik yapılıyordu.

Kullanıcının paylaştığı iki ekran görüntüsündeki kanıt:

- Artan (asc): `0, 1, 10, 11, 12, ...`
- Azalan (desc): `9, 8, 7, 6, 5, ...`

Bu iki dizi tam olarak string karşılaştırmasının (`localeCompare`) çıktısıdır: `"0" < "1" < "10" < "11" < "12" < "2" < ... < "9"`.

İnceleme sırasında aynı hatanın 7 kardeş grid'de daha bulunduğu tespit edildi; kullanıcı kararıyla hepsi kapsama alındı (Açık Soru 1).

## İş Etiketi

fix

## Öncelik

normal

## Etkilenen Alanlar

Üretim kodu (8 AngularJS controller, yalnız `columnDefs` tanımları — 16 satır eklendi):

- `gbpowerdialer/src/main/webapp/js/controllers/phoneNumberColumnCtrl.js`
- `gbpowerdialer/src/main/webapp/js/controllers/campaignGroupCtrl.js`
- `gbpowerdialer/src/main/webapp/js/controllers/resultCodeScheduleCtrl.js`
- `gbpowerdialer/src/main/webapp/js/controllers/resultCodeScheduleAdvancedCtrl.js`
- `gbpowerdialer/src/main/webapp/js/controllers/filterColumnCtrl.js`
- `gbpowerdialer/src/main/webapp/js/controllers/attachedDataColumnCtrl.js`
- `gbpowerdialer/src/main/webapp/js/controllers/campaignScheduleAutomationCtrl.js`
- `gbpowerdialer/src/main/webapp/js/controllers/adminLogsDetailsCtrl.js` (Görev 3.0 taramasında bulundu)

Test:

- Yeni Java contract test: `gbpowerdialer/src/test/java/tests/angular/grid/GridNumericColumnTypeContractTest.java`
- Yeni Karma spec: `gbpowerdialer/src/test/angular/grids/gridNumericSort.spec.js`
- Test altyapısı: `gbpowerdialer/karma.conf.js` (`ui-grid.min.js` + 8 controller `files` listesine eklendi)

Etkilenmeyenler (bilinçli): backend DTO/entity/SQL, Redis channel'ları, AMI event akışı, campaign lifecycle, WRO bundle sırası, localization anahtarları, HTML/JSP template'leri.

## Mevcut Davranış (fix öncesi)

1. `Sort` kolonunun columnDef'i (`phoneNumberColumnCtrl.js:128-137`) `type` alanı **içermiyordu**; aynı dosyadaki gizli `id` kolonu da (`:73-83`) içermiyordu.
2. Veri sunucudan sayı olarak gelir; sıralama tamamen client-side'dır:
   - `PhoneNumberColumn.colSort` ve `PhoneNumberColumnListDTO.colSort` **`int`** (`model/PhoneNumberColumn.java:35,109`, `dto/PhoneNumberColumnListDTO.java:12,55`) → JSON'da number.
   - Kapsamdaki 8 controller'ın hiçbirinde `useExternalSorting` set edilmez (varsayılan `false`) ve `sortChanged` handler'ları yalnız `paginationOptions.sort`'u günceller, refetch yapmaz (Görev 3.0'da tek tek doğrulandı).
   - Grid'lerde `ui-grid-pagination` direktifi yoktur; tüm satırlar tek seferde gelir.
3. Sıralama ui-grid **3.2.6** `rowSorter` servisiyle yapılır (`js/util/ui-grid.min.js`, `WEB-INF/wro.xml:47`).

## Beklenen Davranış

1. `Sort` kolonunda artan sıralama `0, 1, 2, ..., 9, 10, 11, 12`; azalan sıralama bunun tersi.
2. Kapsamdaki tüm sayısal kolonlar (aşağıdaki tablo) sayısal sıralanır; gizli `id` kolonları da (grid menüsünden görünür yapılabiliyorlar) dâhil.
3. Metin kolonlarının alfabetik sıralaması, boolean kolonların sıralaması ve **tüm** kolonların filtre davranışı **değişmez**.

## Kod Okuma Bulguları

### ui-grid 3.2.6 tip tahmini zinciri (minified kaynaktan birebir okundu)

1. **`GridOptions.initialize`** — grid nesnesi yaratılırken controller'ın `gridOptions` nesnesini **yerinde mutate eder**:

```js
angular.module("ui.grid").factory("GridOptions", [..., function(a,b){
  return { initialize: function(c){
    return c.onRegisterApi = c.onRegisterApi || angular.noop(),
           c.data = c.data || [],            // <-- data undefined ise [] olur
           c.columnDefs = c.columnDefs || [], ...
```

2. **`uiGridController` data watch'ı** — `$watchCollection(... .data, dataWatchFunction)`. `[]` **truthy** olduğu için ilk tur `if (newData)` guard'ından geçer ve `columnDefs.length > 0` olduğundan `buildColumns()` + `modifyRows([])` çalışır:

```js
function m(b){ var d=[]; ...; p=b;
  if(b){ var g = n.grid.columns.length > (...);
    !g && (n.grid.options.columnDefs.length>0 || b.length>0) && d.push(n.grid.buildColumns()...);
    e.all(d).then(function(){ n.grid.modifyRows(p).then(...) })
  }
}
```

3. **`Grid.prototype.modifyRows`** — satır listesini kurduktan sonra **koşulsuz** `assignTypes()` çağırır:

```js
p.prototype.modifyRows=function(b){ ...; b.forEach(...); c.assignTypes(); ... }
```

4. **`Grid.prototype.assignTypes`** — tipi bir kez damgalar ve `if(!b.type)` guard'ı yüzünden **bir daha hesaplamaz**:

```js
p.prototype.assignTypes=function(){ var a=this;
  a.options.columnDefs.forEach(function(b,c){
    if(!b.type){
      var e=new g(b,c,a), f = a.rows.length>0 ? a.rows[0] : null;
      f ? b.type = d.guessType(a.getCellValue(f,e))
        : b.type = "string"          // <-- satır yoksa string
    }
  })
}
```

5. **`rowSorter.getSortFn` / `guessSortFn`** — tip adına göre fonksiyon seçer ve uygulama-global cache'e yazar:

```js
d.guessSortFn = function(a){ switch(a){
  case "number": return d.sortNumber;   // (a,b) => a-b
  case "string": return d.sortAlpha;    // a.toString().toLowerCase() + localeCompare
  ... } }

d.getSortFn = function(a,b,c){ var e;
  return d.colSortFnCache[b.colDef.name] ? e = d.colSortFnCache[b.colDef.name]
       : void 0 !== b.sortingAlgorithm ? (...)
       : b.sortCellFiltered && b.cellFilter ? (...)
       : (e = d.guessSortFn(b.colDef.type), e ? d.colSortFnCache[b.colDef.name]=e : e=d.sortAlpha), e }
```

   `d = { colSortFnCache: {} }` servis singleton'ında tutulur; anahtar `colDef.name`'dir (name verilmediğinde `field`'dan türetilir) ve **sayfa ömrü boyunca** kalır.

6. **`rowSorter.sort` external sorting kısa devresi** — `d.sort=function(a,c,e){if(c){if(a.options.useExternalSorting)return c; ...}`. `useExternalSorting` açık grid'ler `getSortFn`'e hiç ulaşmaz: ne yanlış sıralar ne de paylaşılan cache'i kirletir.

### Karşı kanıt: aynı sekmedeki Sort Conditions grid'i neden doğru sıralıyordu

`sortFieldCtrl.js` aynı asenkron akışı kullanır ama columnDef'lerinde tipi açıkça bildirir (`:78` `id`, `:97` `priority`); `sortFieldValueCtrl.js` de aynı. Repo'nun yerleşik ve kanıtlanmış çözüm deseni budur; kapsamdaki 8 controller bu bildirimi atlamıştı.

### Kapsama alınan kolonlar (DTO tipinden doğrulandı)

16 columnDef `type : 'number'` aldı. Tip sütunu ilgili List DTO'dan okunmuştur:

| Dosya | Kolon | DTO tipi |
|---|---|---|
| `phoneNumberColumnCtrl.js` | `id`, `colSort` | `Long`, `int` |
| `campaignGroupCtrl.js` | `id`, `priority` | `Long`, `int` |
| `resultCodeScheduleCtrl.js` | `id`, `retryAmount`, `retryInterval` | `Long`, `int`, `double` |
| `resultCodeScheduleAdvancedCtrl.js` | `id`, `dialAttempt`, `retryInterval`, `numberToTry` | `Long`, `int`, `double`, `int` |
| `filterColumnCtrl.js` | `id`, `ratio` | `Long`, `Integer` |
| `attachedDataColumnCtrl.js` | `id` | `Long` |
| `campaignScheduleAutomationCtrl.js` | `id` | `Long` |
| `adminLogsDetailsCtrl.js` | `id` | `Long` |

### Bilinçli olarak kapsam dışı bırakılan kolonlar

| Kolon | Neden dışarıda |
|---|---|
| `resultCodeScheduleCtrl.applyTo` | DTO'da `int` ama hücre `{{"resultCodeSchedule.applyTo." + row.entity.applyTo \| i18n}}` ile **çevrilmiş etiket** gösteriyor. Sayısal sıralama enum ordinal'ine göre olur; ekranda görünen etiket sırasıyla uyuşmaz. Ayrı karar konusu. |
| `resultCodeScheduleCtrl.minScheduleTime`, `maxScheduleTime` | DTO'da **`Date`**, hücrede `\| date:"HH:mm"` ile yalnız saat gösteriliyor. Doğru çözüm `type : 'number'` değil; tarih/saat semantiği ayrı analiz ister. |
| `resultCodeScheduleCtrl.macroCycle`, tüm `enabled` kolonları | DTO'da **`boolean`**. `sortAlpha` boolean için `sortBool` ile aynı sırayı üretiyor; görünür hata yok. |
| `dncPhoneCtrl.js` `id` | Grid `useExternalSorting : true` kullanıyor; `rowSorter.sort` external sorting'de `getSortFn`'e ulaşmadan dönüyor. Ne yanlış sıralıyor ne de paylaşılan `colSortFnCache['id']`'yi kirletiyor. Bu varsayım `externalSortingGridStaysOutOfClientSideSorting` testiyle kilitlendi. |
| Metin kolonları | Gerçekten string; mevcut `sortAlpha` doğru davranış. |

## Root Cause / Değişiklik Gerekçesi

**Kök neden:** İlgili columnDef'ler ui-grid'e veri tipini bildirmediği için ui-grid tipi kendi tahmin eder; tahmin **grid'in ilk boş veri turunda** yapılır ve kalıcı olarak `string` damgalanır.

Deterministik zincir (her sayfa yüklemesinde tekrarlanır):

1. `<div ui-grid="phoneNumberColumn.phoneNumberColumnGridOptions" ...>` link edilir; `gridClassFactory.createGrid(gridOptions)` → `GridOptions.initialize(gridOptions)` çağrılır ve controller'ın **aynı** nesnesinde `data = []` set edilir.
2. Data watch'ının ilk turu `[]` (truthy) ile çalışır → `buildColumns()` → `Grid.modifyRows([])`.
3. `modifyRows` sonunda `assignTypes()` çalışır; `grid.rows.length === 0` olduğu için `type` bildirmemiş **her** columnDef'e `type = "string"` damgalanır.
4. `vm.getPage()` HTTP cevabı **daha sonra** gelir (`colSort` JSON'da number'dır). İkinci `modifyRows(gerçekVeri)` yine `assignTypes()` çağırır ama `if (!b.type)` guard'ı yüzünden tip **yeniden hesaplanmaz**.
5. Kullanıcı başlığa tıkladığında `rowSorter.getSortFn` → `guessSortFn("string")` → `sortAlpha` döner; sonuç ayrıca uygulama-global `rowSorter.colSortFnCache[colDef.name]`'e yazılır.
6. `sortAlpha` değeri `a.toString().toLowerCase()` ile stringe çevirip `localeCompare` uygular → ekran görüntüsündeki `0,1,10,11,12` / `9,8,7,6,5` sırası.

Veri her zaman asenkron geldiği için 1-3 adımları **istisnasız** çalışır. Bu zincir headless-Chrome doğrulamasında birebir üretildi: fix öncesi 16 kolonun tamamı `asc = [0,1,10,11,12,2,9]` verdi (bkz. `Doğrulama`).

**Çözüm (owner boundary'de kalıcı):** Kolonun veri tipi, kolonu tanımlayan yerde — columnDef'te — açıkça bildirilir. Böylece `assignTypes` bu kolona hiç dokunmaz, tahmin devreden çıkar ve global sort cache'ine yalnızca `sortNumber` yazılabilir.

```js
}, {
  displayName : locMsg['phoneNumberColumn.colSort'],
  field : 'colSort',
  enableHiding : false,
  headerCellClass : vm.highlightFilteredHeader,
  type : 'number',
  filters : [ {
    condition : uiGridConstants.filter.CONTAINS
  } ],
  minWidth : 100
}, {
```

Bu, `gbpd-no-workaround-root-cause-engineering` açısından workaround değildir: veri tipini `sortingAlgorithm` closure'ı, veri post-processing'i veya değerleri stringe/sayıya zorlayan bir dönüştürme ile değil, tipin sahibi olan tanım noktasında düzeltir ve repo'da zaten kanıtlanmış deseni izler.

## Riskler

- **`colDef.type`'ın sıralama dışındaki etkileri.** ui-grid 3.2.6'da `colDef.type`; (a) `rowSorter` fonksiyon seçimi, (b) `ui.grid.edit` editör input tipi, (c) `ui.grid.exporter` biçimlendirmesi için kullanılır. Görev 3.0'da doğrulandı: kapsamdaki 8 grid'in template'lerinde yalnız `ui-grid ui-grid-auto-resize ui-grid-resize-columns ui-grid-selection` var; `ui-grid-edit` uygulamada hiç kullanılmıyor, `ui-grid-exporter` bu grid'lerin hiçbirinde yok. → Sıralama dışı davranış değişmez.
- **Filtreleme.** `rowSearcher` karşılaştırmaları `colDef.type`'a değil, çalışma anındaki değerlerin `typeof`'una bakar. Mevcut `filters`/`filter` tanımları aynen korundu. → Filtre davranışı değişmez.
- **`0` ve `null` değerleri.** `sortNumber` önce `handleNulls(a,b)` çağırır; `handleNulls` `0` ve `false`'u null saymaz (`!a && 0!==a && a!==!1`). `colSort = 0` doğru sıralanır (davranış testinde `0` fixture'da var ve asc'de ilk sırada). `filterColumnCtrl.ratio` nullable (`Integer`); null satırlar `handleNulls` ile sona gider — `sortAlpha`'da da öyleydi.
- **`double` kolonlar.** `retryInterval` (`double`) için `sortNumber` (`a - b`) doğru çalışır.
- **Global sort cache.** `rowSorter.colSortFnCache` anahtarı `colDef.name`'dir ve grid'ler arası paylaşılır; bu yüzden aynı adı taşıyan sayısal kolonlar birlikte tiplendirilmelidir. Görev 3.0 taraması uygulama genelinde yapıldı: `id`, `priority`, `retryInterval`, `colSort`, `retryAmount`, `dialAttempt`, `numberToTry`, `ratio` adlarını taşıyan tüm client-side grid kolonları artık tiplendirilmiş durumda. Geriye yalnız `dncPhoneCtrl.js` `id` kalıyor ve o grid `useExternalSorting : true` ile client-side sıralamaya hiç girmiyor.
- **Genel.** Redis channel, AMI event, result code eşlemesi, campaign state transition, DTO/DB şeması ve WRO bundle sırası değişmedi. Yeni kütüphane eklenmedi.

## TDD Stratejisi

Kırmızı → yeşil sırası korundu. İki katman:

1. **Davranışsal katman:** Gerçek controller'lar örneklenip `columnDefs`'leri alınır, gerçek ui-grid 3.2.6 ile grid kurulur ve üretimdeki sıra birebir replay edilir (`modifyRows([])` → `modifyRows(gerçekVeri)`). Ardından hedef kolon asc/desc sıralanıp satır sırası doğrulanır. Karma spec'i olarak yazıldı; `node_modules` blocker'ı nedeniyle aynı mantık headless Chrome ile koşturuldu (bkz. `Doğrulama`).
2. **Contract katmanı (Java/JUnit, offline koşar):** 16 columnDef'in `type : 'number'` bildirdiğini, kapsam dışı kolonların sayısal tip **almadığını**, `dncPhoneCtrl` external-sorting varsayımının geçerli kaldığını ve `sortFieldCtrl`/`sortFieldValueCtrl` referans deseninin korunduğunu kilitler.

## Açık Sorular

Tümü 11.08.2026'da kullanıcı tarafından cevaplandı ve plan bu cevaplara göre revize edildi.

- [x] **1. Kapsam — CEVAPLANDI.** Cevap: "Phone Numbers + tüm sibling grid'ler". → Kapsam 8 controller / 16 columnDef. Aday kolonlar DTO tiplerinden doğrulandı; `applyTo`, `minScheduleTime`, `maxScheduleTime`, `macroCycle` kapsam dışına alındı.
- [x] **2. Karma koşumu — CEVAPLANDI.** Cevap: "Evet, `npm install` çalıştır". → Denendi, **başarısız**: internal Nexus npm registry'sine VPN dışından erişilemiyor. Ayrıntı `Doğrulama > Blocker`.
- [x] **3. Hedef dal — CEVAPLANDI.** Cevap: "Sadece `develop`". → Release backport'u yok.
- [x] **4. Gizli `id` kolonu — CEVAPLANDI (1. sorunun cevabına dâhil).** Kapsamdaki tüm grid'lerin `id` kolonları tiplendirildi.

## Execution Plan

### Görev 0: Ortam hazırlığı

- [x] **0.1** `npm install` çalıştırıldı — **BAŞARISIZ** (Nexus ETIMEDOUT, VPN dışı). `npm install --offline` de denendi, `ENOTCACHED` (`@types/ws@8.18.1` cache'te yok). 146 paket indi, `karma` binary'si inmedi. `package.json` değişmedi.
- [x] **0.2** Baseline `npm.cmd run test:angular` koşulamadı (aynı blocker).

### Görev 1: Karma davranış spec'i

- [x] **1.1** `karma.conf.js` `files` listesine `ui-grid.min.js` (angular'dan sonra) ve 8 controller dosyası (`angular-app.module.js`'ten sonra) eklendi.
- [x] **1.2** `src/test/angular/grids/gridNumericSort.spec.js` yazıldı. 8 grid × sayısal kolon tablo bazlı gezilir; her kolon için `colDef.type`, `getSortFn` kimliği ve asc/desc satır sırası doğrulanır. Ek olarak metin kolonu regresyon koruması var.
  - Plan sapması: controller'lar `ngRoute` (`$routeParams`) ve angular-translate (`translate` filtresi) bağımlılığı taşıdığı için `$provide.value('$routeParams', {})` ve `$filterProvider.register('translate'/'i18n', identity)` stub'ları eklendi. Bu ihtiyaç headless-Chrome koşusunda tespit edildi (ilk turda 5 controller `translateFilterProvider <- translateFilter` ile patladı).
  - Plan sapması: `adminLogsDetailsCtrl` modal controller'ı olduğu için `item` ve `$modalInstance` local'leri eklendi.
- [ ] **1.3** `npm.cmd run test:angular` — **koşulamadı** (Görev 0 blocker'ı). Geliştirici VPN'de `npm install` sonrası koşacak.

### Görev 2: Java contract testi

- [x] **2.1** `src/test/java/tests/angular/grid/GridNumericColumnTypeContractTest.java` yazıldı (9 test).
  - Plan sapması: kapsam campaign form dışına da çıktığı için sınıf `tests.angular.campaign` yerine `tests.angular.grid` paketine kondu ve adı `GridNumericColumnTypeContractTest` oldu.
- [x] **2.2** Kırmızı görüldü: `Tests run: 9, Failures: 6` — altı grid testi `... -> id columnDef'i type : 'number' bildirmeli` ile düştü; kapsam dışı, external-sorting ve `sortFieldCtrl` referans testleri baştan yeşildi.

### Görev 3: Üretim kodu değişikliği

- [x] **3.0 Ön kontroller:**
  - Template kontrolü: 8 grid'in tamamında yalnız `ui-grid ui-grid-auto-resize ui-grid-resize-columns ui-grid-selection`; `ui-grid-edit`/`ui-grid-exporter`/`ui-grid-pagination` yok.
  - Sorting kontrolü: 8 controller'ın hiçbirinde `useExternalSorting` yok; `sortChanged` handler'larının tamamı yalnız `paginationOptions.sort`'u güncelliyor, refetch yapmıyor.
  - **Uygulama genelinde tarama sonucu (plana eklenen bulgu):** kapsamın dışında iki dosyada daha tiplendirilmemiş sayısal `id` kolonu bulundu.
    - `adminLogsDetailsCtrl.js:39` → client-side sıralıyor, paylaşılan `colSortFnCache['id']`'yi kirletebilir → **kapsama alındı**.
    - `dncPhoneCtrl.js:75` → `useExternalSorting : true`, `rowSorter.sort` kısa devre yapıyor → **kapsam dışı bırakıldı**, varsayım testle kilitlendi.
- [x] **3.1** 16 columnDef'e `headerCellClass` satırından sonra `type : 'number',` eklendi (her dosyanın kendi girinti stiliyle).
- [x] **3.2** Java testi yeşile döndü: `Tests run: 9, Failures: 0, Errors: 0` — BUILD SUCCESS.
- [x] **3.3** Davranış doğrulaması yeşile döndü: headless Chrome harness'ı `pass=66 fail=0` (fix öncesi `pass=2 fail=64`).

### Görev 4: Regresyon ve kapanış

- [x] **4.1** Tüm AngularJS contract testleri: `Tests run: 30, Failures: 0, Errors: 0` (9 sınıf) — BUILD SUCCESS.
- [x] **4.2** WAR derlemesi: `mvn -o -DskipTests package` → BUILD SUCCESS (01:13, WRO bundle dâhil).
- [ ] **4.3** Manuel doğrulama (deploy sonrası): Campaign Form → List Source → Phone Numbers → `Sort` başlığı; asc `0,1,2,...,10,11,12`, desc tersi. Ardından Groups `Priority`, Retry Rules `Retry Amount`/`Retry Interval`/`Dial Attempt`/`Number To Try` ve Filters `Ratio`. Metin kolonlarının sıralaması, checkbox kolonları ve filtre kutuları değişmemiş olmalı.
- [x] **4.4** History kaydı `docs/history/11.08.2026.md` dosyasına eklendi; plan adımları işaretlendi.
- [ ] **4.5** Commit `gbpd-commit-discipline` ile hazırlanacak (kullanıcı onayı sonrası); PR öncesi VPN'de `origin/develop` sync (`gbpd-sync-develop-branch`). Hedef dal: `develop`.

## Performans Etkisi

- **Hot path durumu:** Hot path **değil**. Değişiklik yalnız AngularJS admin ekranlarındaki grid kolonlarının sıralama fonksiyonu seçimini etkiler. AMI/AGI originate, hangup/result handling, Redis publish/subscribe, ACD seçimi ve campaign producer/consumer/originator loop'larına temas yoktur.
- **Call/event/tick başına ek maliyet:** Yok (0).
- **IO/scan/lock/serialization/log etkisi:** Yok. Ek HTTP isteği, DB sorgusu, allocation veya log satırı eklenmedi; DTO/SQL değişmedi.
- **Azaltım veya ölçüm notu:** Sıralama maliyeti karşılaştırma başına `String.toLowerCase()` + `localeCompare`'den `a - b` çıkarmasına düştü; küçük ama pozitif.

## Doğrulama

Komutlardan önce `.codex/TOOL-PATHS.local.properties` okundu; `MAVEN_CMD`, `MAVEN_SETTINGS`, `MAVEN_REPO_LOCAL`, `JAVA_HOME`, `NPM_CMD` oradan alındı. Maven `-o` (offline) koşuldu.

| Komut | Sonuç |
|---|---|
| `mvn -o -Dtest=GridNumericColumnTypeContractTest test` (fix öncesi) | **KIRMIZI** — Tests run: 9, Failures: 6 |
| `mvn -o -Dtest=GridNumericColumnTypeContractTest test` (fix sonrası) | **YEŞİL** — Tests run: 9, Failures: 0, Errors: 0 |
| `mvn -o -Dtest=<9 angular contract sınıfı> test` | **YEŞİL** — Tests run: 30, Failures: 0, Errors: 0 |
| `mvn -o -DskipTests package` | **BUILD SUCCESS** (01:13) |
| headless Chrome davranış harness'ı (fix öncesi) | **KIRMIZI** — pass=2 fail=64 |
| headless Chrome davranış harness'ı (fix sonrası) | **YEŞİL** — pass=66 fail=0 |
| `npm.cmd run test:angular` | **KOŞULAMADI** — blocker, aşağıda |

### Davranış doğrulaması (Karma yerine geçici yöntem)

`node_modules` kurulamadığı için Karma koşulamadı. Aynı doğrulamayı bağımlılık indirmeden yapmak üzere scratchpad'de (repo dışında) bir HTML harness kuruldu: `angular.min.js` + `ui-grid.min.js` + `angular-app.module.js` + `constants.js`/`alertService.js`/`baseFactory.js` + 8 gerçek controller yüklenir, `angular.injector(['ng','ui.grid','app', stubs])` ile injector kurulur ve Karma spec'iyle **aynı** mantık koşturulur. Sonuçlar `console.log` ile Chrome'un stderr log'una yazılır:

```
chrome.exe --headless --disable-gpu --no-sandbox --user-data-dir=<tmp>
           --enable-logging=stderr --v=0 --allow-file-access-from-files
           --virtual-time-budget=15000 file:///<scratchpad>/gridsort-harness.html
```

Fix öncesi çıktı (16 kolonun tamamı, örnek satırlar):

```
FAIL  phoneNumberColumnCtrl.colSort type    actual="string"           expected="number"
FAIL  phoneNumberColumnCtrl.colSort sortFn  actual="sortAlpha"        expected="sortNumber"
FAIL  phoneNumberColumnCtrl.colSort asc     actual=[0,1,10,11,12,2,9] expected=[0,1,2,9,10,11,12]
FAIL  phoneNumberColumnCtrl.colSort desc    actual=[9,2,12,11,10,1,0] expected=[12,11,10,9,2,1,0]
PASS  phoneNumberColumnCtrl.colNumber type
PASS  phoneNumberColumnCtrl.colNumber sortFn
SUMMARY pass=2 fail=64
```

`actual=[0,1,10,11,12,2,9]` kullanıcının ekran görüntüsündeki sırayla birebir aynıdır; kök neden bu çıktıyla kanıtlanmıştır. Fix sonrası aynı harness `SUMMARY pass=66 fail=0` verdi.

Harness geçici bir teşhis aracıdır, repo'ya eklenmemiştir; kalıcı test `src/test/angular/grids/gridNumericSort.spec.js` dosyasıdır.

### Blocker: `npm install`

- `npm install` → tüm paketler `ETIMEDOUT`; registry `.npmrc` üzerinden internal Nexus'a (`npm-public` repository) yönlendiriliyor ve VPN dışında erişilemiyor. npm süreci `Exit handler never called!` ile düştü.
- `npm install --offline` → `ENOTCACHED` (`@types/ws@8.18.1` local cache'te yok; 06.08.2026 kurulumundan bu yana transitive sürüm ilerlemiş).
- Sonuç: `gbpowerdialer/node_modules` altında 146 paket var, `karma` binary'si yok. `package.json` değiştirilmedi; `node_modules` `.gitignore` kapsamındadır.
- **Geliştirici aksiyonu:** VPN'e bağlanıp `npm install` ve ardından `npm.cmd run test:angular` çalıştırılmalı; yeni spec o koşuda doğrulanacak.

## Kapanış Kriteri

- [x] Açık sorular cevaplandı ve plana işlendi.
- [x] `GridNumericColumnTypeContractTest` offline Maven ile yeşil (9/9); fix öncesi kırmızı görüldü (6 failure).
- [ ] `npm.cmd run test:angular` yeşil — **blocker**: `node_modules` kurulamadı (VPN/Nexus). Yerine headless-Chrome davranış doğrulaması koşuldu (66/0).
- [x] Kapsamdaki 16 kolon asc `0,1,2,...,10,11,12`, desc tersi sıralıyor; metin kolonu `sortAlpha` olarak kaldı.
- [x] Görev 3.0 ön kontrolleri yapıldı; paylaşılan `colSortFnCache` anahtarları için tiplendirilmemiş client-side sayısal kolon kalmadı.
- [x] WAR paketleme başarılı.
- [x] History kaydı eklendi; plan adımları işaretlendi.
- [x] Final yanıtta kısa Test Özeti sunuldu.

## Tamamlanma Notu

Uygulama tamamlandı; kod ve testler yeşil. Plan iki noktada açık kalıyor:

1. `npm.cmd run test:angular` geliştirici VPN'e bağlanıp `npm install` çalıştırdıktan sonra koşulmalı (yeni spec o koşuda doğrulanacak).
2. Deploy sonrası manuel UI doğrulaması (Görev 4.3) yapılmalı.

Commit ve PR kullanıcı onayı sonrası hazırlanacak.
